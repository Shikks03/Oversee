package com.example.oversee.service

// --- ANDROID CORE ---
import android.app.*
import com.example.oversee.R
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.*
import android.view.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import androidx.core.graphics.scale

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// --- PROJECT IMPORTS ---
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.model.Incident
import com.example.oversee.domain.TextAnalysisEngine
import com.example.oversee.domain.ToxicityScorer
import com.example.oversee.utils.sendConsoleUpdate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val SYNC_INTERVAL = 28_800_000L
        private const val CAPTURE_INTERVAL = 3000L
        private const val DEBOUNCE_TIME_MS = 10_000L
    }

    private lateinit var projection: MediaProjection
    private lateinit var imageReader: ImageReader
    private val handler = Handler(Looper.getMainLooper())

    private val attemptCount = AtomicInteger(0)
    private val successCaptureCount = AtomicInteger(0)
    private val ocrFinishedCount = AtomicInteger(0)
//    private var tess: TessBaseAPI? = null
//    private var tessBaseline: TessBaseAPI? = null

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var textAnalysisEngine: TextAnalysisEngine

    private val debounceMap = ConcurrentHashMap<String, Long>()
    private val ocrExecutor = Executors.newSingleThreadExecutor()

    private var syncRequestListener: ListenerRegistration? = null
    @Volatile private var lastHandledSyncRequest = 0L

    // Step 6: cached system-bar insets computed once in setupVirtualDisplay
    private var topInset = 0
    private var bottomInset = 0

    // Step 10: last pHash for frame-diff gate
    @Volatile private var lastHash: Long? = null


    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        textAnalysisEngine = TextAnalysisEngine.fromAssets(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data) ?: return START_NOT_STICKY

        val broadcastIntent = Intent("com.example.oversee.CAPTURE_STARTED").apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        CaptureState.isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupVirtualDisplay()

        handler.post(captureRunnable)
        handler.postDelayed(syncRunnable, SYNC_INTERVAL)
        listenForParentSyncRequests()

        return START_STICKY
    }

//    private fun prepareTesseract() {
//        val tessDir = File(filesDir, "tessdata")
//        if (!tessDir.exists()) tessDir.mkdirs()
//        val jsonData = File(tessDir, "eng.traineddata")
//        if (!jsonData.exists()) {
//            assets.open("tessdata/eng.traineddata").use { input ->
//                jsonData.outputStream().use { output -> input.copyTo(output) }
//                Log.d("OCR", "prepareTesseract DONE")
//            }
//        }
//    }

    // --- CAPTURE LOOP ---
    private val captureRunnable = object : Runnable {
        override fun run() {
            if (ScreenState.isFacebookOpen) {
                if (overlayView == null) handler.post { showMonitoringOverlay() }

                val currentId = attemptCount.incrementAndGet()
                Log.d(TAG, "System: Capture Cycle #$currentId started")
                sendConsoleUpdate("System: Capture Cycle #$currentId started")

                val image = try { imageReader.acquireLatestImage() } catch (e: Exception) { null }
                if (image != null) {
                    successCaptureCount.incrementAndGet()
                    val bitmap = imageToBitmap(image)
                    image.close()
                    ocrExecutor.execute {
                        runOcr(bitmap, currentId)
                    }
                }
            } else {
                if (overlayView != null) handler.post { removeOverlay() }
                imageReader.acquireLatestImage()?.close()
            }
            handler.postDelayed(this, CAPTURE_INTERVAL)
        }
    }

    // --- OCR LOGIC ---
    private fun runOcr(bitmap: Bitmap, id: Int) {
        if (ScreenState.isKeyboardVisible) {
            bitmap.recycle()
            return
        }

        val startTime = System.currentTimeMillis()

        val cropped = if (topInset + bottomInset > 0) {
            val cropHeight = bitmap.height - topInset - bottomInset
            if (cropHeight > 0)
                Bitmap.createBitmap(bitmap, 0, topInset, bitmap.width, cropHeight)
            else bitmap
        } else bitmap

        // --- NEW: FRAME SKIPPING LOGIC ---
        val currentHash = generateFastHash(cropped)
        if (lastHash != null && java.lang.Long.bitCount(currentHash xor lastHash!!) < 5) {
            Log.d(TAG, "Frame #$id skipped (Identical to previous screen)")
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            return
        }
        lastHash = currentHash
        // ---------------------------------

        val image = InputImage.fromBitmap(cropped, 0)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "ML Kit OCR #$id (${duration}ms) - Text:\n${visionText.text}")

                if (visionText.text.isNotBlank()) {
                    val analysisResult = textAnalysisEngine.analyze(visionText.text)
                    val scoredWords = ToxicityScorer.score(analysisResult)

                    if (scoredWords.isNotEmpty()) {
                        scoredWords.forEach { scored ->
                            val lastSeen = debounceMap[scored.matchedWord] ?: 0L
                            val now = System.currentTimeMillis()

                            if (now - lastSeen > DEBOUNCE_TIME_MS) {
                                debounceMap[scored.matchedWord] = now
                                val severity = mapSeverity(scored.severity)

                                // --- NEW: RESTORED LOGCAT PRINT ---
                                Log.d(TAG, "🚨 FLAG CAUGHT: OCR Saw='${scored.originalText}' -> Cleaned='${scored.rawToken}' -> Matched='${scored.matchedWord}' (Severity: $severity) in ${duration}ms 🚨")


                                IncidentRepository.saveIncident(
                                    applicationContext,
                                    Incident(
                                        rawWord = scored.originalText,
                                        matchedWord = scored.matchedWord,
                                        severity = severity,
                                        appName = "Facebook"
                                    )
                                )
                                sendConsoleUpdate("FLAG: '${scored.rawToken}' -> '${scored.matchedWord}' ($severity) in ${duration}ms")
                            } else {
                                Log.d(TAG, "⏳ Ignoring '${scored.matchedWord}' (Debounce active)")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit Processing Error", e)
            }
            .addOnCompleteListener {
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            }
    }

    private fun generateFastHash(bitmap: Bitmap): Long {
        // Shrink the image to 8x8 pixels to instantly blur out minor video noise
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)

        var sum = 0
        val grays = IntArray(64)
        for (i in 0..63) {
            val p = pixels[i]

            // Extract RGB directly from the Int to avoid Color import clashes
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            // Calculate brightness
            val gray = (r + g + b) / 3
            grays[i] = gray
            sum += gray
        }
        val mean = sum / 64
        var hash = 0L
        for (i in 0..63) {
            if (grays[i] >= mean) hash = hash or (1L shl i)
        }
        scaled.recycle()
        return hash
    }

    // --- COMPOSE OVERLAY ---
    private fun showMonitoringOverlay() {
        if (overlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40; y = 100
        }
        overlayView = ComposeView(this).apply {
            val lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.attachToView(this)
            setContent { MonitoringIndicator() }
        }
        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    // --- STANDARD HELPERS ---
    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        projection.createVirtualDisplay("capture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null)

        // Step 6 (A1): cache system-bar insets once; adapts to gesture-nav vs 3-button-nav
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        topInset = insets.top
        bottomInset = insets.bottom
    }

    private fun listenForParentSyncRequests() {
        DeviceRepository.getFid { fid ->
            if (fid == null) return@getFid
            syncRequestListener = FirebaseFirestore.getInstance()
                .collection("monitor_sessions").document(fid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val requestedAt = snapshot.getLong("sync_requested_at") ?: return@addSnapshotListener
                    if (requestedAt > lastHandledSyncRequest) {
                        lastHandledSyncRequest = requestedAt
                        Log.d(TAG, "📲 Parent requested sync")
                        sendConsoleUpdate("System: Parent requested sync")
                        IncidentRepository.syncData(applicationContext)
                    }
                }
        }
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            Thread { IncidentRepository.syncData(applicationContext) }.start()
            handler.postDelayed(this, SYNC_INTERVAL)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val width = image.width
        val height = image.height

        // Hardware screens often add invisible "padding" bytes to the edge of rows.
        // We have to account for that padding so the image doesn't look skewed.
        val rowPadding = rowStride - pixelStride * width

        // 1. Create a full-color ARGB_8888 bitmap
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )

        // 2. Copy the raw screen pixels directly into it
        bitmap.copyPixelsFromBuffer(buffer)

        // 3. Slice off the invisible padding on the right edge (if any)
        return if (rowPadding > 0) {
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle() // Free the uncropped version from memory
            croppedBitmap
        } else {
            bitmap
        }
    }

    private fun createNotification(): Notification {
        val channelId = "monitor_svc"
        val channel = NotificationChannel(channelId, "Active Monitor", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("SafeMonitor Running")
            .setSmallIcon(R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        Thread { IncidentRepository.syncData(applicationContext) }.start()
        syncRequestListener?.remove()
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        CaptureState.isRunning = false
        if (::projection.isInitialized) projection.stop()


        ocrExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun mapSeverity(scorerSeverity: String): String = when (scorerSeverity) {
        "Severe"   -> "HIGH"
        "Moderate" -> "MEDIUM"
        "Mild"     -> "LOW"
        else       -> "LOW"
    }

    object CaptureState { @Volatile var isRunning = false }
    object ScreenState {
        @Volatile var isFacebookOpen = false
        // Step 5: set by FacebookAccessibilityService on every window-list change
        @Volatile var isKeyboardVisible = false
    }

}



// --- COMPOSE UI ---
@Composable
fun MonitoringIndicator() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0x662196F3))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, "Monitoring", tint = Color.White)
    }
}

// --- LIFECYCLE HELPER ---
class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    fun attachToView(view: ComposeView) {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
