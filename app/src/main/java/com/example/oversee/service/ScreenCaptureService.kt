package com.example.oversee.service

// --- ANDROID CORE ---
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

// --- ML KIT ---
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// --- PROJECT IMPORTS ---
import com.example.oversee.R
import com.example.oversee.data.DeviceRepository
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.model.Incident
import com.example.oversee.domain.TextAnalysisEngine
import com.example.oversee.domain.ToxicityScorer
import com.example.oversee.utils.sendConsoleUpdate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
        private const val BURST_TIME_WINDOW_MS = 300_000L // 5 minutes
    }

    private lateinit var projection: MediaProjection
    private lateinit var imageReader: ImageReader
    private val handler = Handler(Looper.getMainLooper())

    private val attemptCount = AtomicInteger(0)
    private val successCaptureCount = AtomicInteger(0)

    // --- ML KIT & ANALYSIS ---
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var textAnalysisEngine: TextAnalysisEngine

    private val debounceMap = ConcurrentHashMap<String, Long>()
    private val ocrExecutor = Executors.newSingleThreadExecutor()

    private val recentIncidents = mutableListOf<Long>()
    private var syncRequestListener: ListenerRegistration? = null
    @Volatile private var lastHandledSyncRequest = 0L


    private var topInset = 0
    private var bottomInset = 0
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

    // --- CAPTURE LOOP ---
    private val captureRunnable = object : Runnable {
        override fun run() {
            if (ScreenState.isAppOpen) { // Changed from isFacebookOpen
                if (overlayView == null) handler.post { showMonitoringOverlay() }

                val currentId = attemptCount.incrementAndGet()
                Log.d(TAG, "System: Capture Cycle #$currentId started")

                val image = try { imageReader.acquireLatestImage() } catch (e: Exception) { null }
                if (image != null) {
                    successCaptureCount.incrementAndGet()
                    val bitmap = imageToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        ocrExecutor.execute { runOcr(bitmap, currentId) }
                    }
                }
            } else {
                if (overlayView != null) handler.post { removeOverlay() }
                imageReader.acquireLatestImage()?.close()
            }
            handler.postDelayed(this, CAPTURE_INTERVAL)
        }
    }

    // --- ML KIT OCR LOGIC ---
    private fun runOcr(bitmap: Bitmap, id: Int) {
        if (ScreenState.isKeyboardVisible) {
            bitmap.recycle()
            return
        }

        val startTime = System.currentTimeMillis()

        // Fetch preferences dynamically inside the scope
        val blockMins = try { AppPreferenceManager.getLong(applicationContext, "block_duration_mins", 5L) } catch (e: Exception) { 5L }
        val burstThreshold = try { AppPreferenceManager.getLong(applicationContext, "burst_threshold", 50L) } catch (e: Exception) { 50L }

        val cropped = if (topInset + bottomInset > 0) {
            val cropHeight = bitmap.height - topInset - bottomInset
            if (cropHeight > 0)
                Bitmap.createBitmap(bitmap, 0, topInset, bitmap.width, cropHeight)
            else bitmap
        } else bitmap

        // --- FRAME SKIPPING LOGIC ---
        val currentHash = generateFastHash(cropped)
        if (lastHash != null && java.lang.Long.bitCount(currentHash xor lastHash!!) < 5) {
            Log.d(TAG, "Frame #$id skipped (Identical to previous screen)")
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            return
        }
        lastHash = currentHash

        val image = InputImage.fromBitmap(cropped, 0)

        // Process ML Kit Asynchronously
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "ML Kit OCR #$id (${duration}ms) - Length: ${visionText.text.length}")

                if (visionText.text.isNotBlank()) {
                    val analysisResult = textAnalysisEngine.analyze(visionText.text)
                    val scoredWords = ToxicityScorer.score(analysisResult)

                    if (scoredWords.isNotEmpty()) {
                        scoredWords.forEach { scored ->
                            val lastSeen = debounceMap[scored.matchedWord] ?: 0L
                            val now = System.currentTimeMillis()

                            if (now - lastSeen > DEBOUNCE_TIME_MS) {
                                debounceMap[scored.matchedWord] = now
                                var severity = mapSeverity(scored.severity)

                                // --- BURST DETECTION LOGIC ---
                                recentIncidents.removeAll { now - it > BURST_TIME_WINDOW_MS }
                                recentIncidents.add(now)

                                if (recentIncidents.size >= burstThreshold) {
                                    severity = "HIGH"
                                    recentIncidents.clear() // Prevent loop
                                    sendConsoleUpdate("🚨 BURST THRESHOLD REACHED: $burstThreshold alerts in 5 mins! Escalating to HIGH risk. 🚨")
                                }

                                IncidentRepository.saveIncident(
                                    applicationContext,
                                    Incident(
                                        rawWord = scored.originalText,
                                        matchedWord = scored.matchedWord,
                                        severity = severity,
                                        appName = ScreenState.currentAppName // Use the dynamic name
                                    )
                                )

                                // --- PENALTY OVERLAY LOGIC ---
                                if (severity == "HIGH") {
                                    val unlockTime = System.currentTimeMillis() + (blockMins * 60 * 1000L)
                                    try { AppPreferenceManager.saveLong(applicationContext, "app_unlock_time", unlockTime) } catch (e: Exception) {}

                                    val overlayIntent = Intent(applicationContext, OverlayService::class.java).apply {
                                        putExtra(OverlayService.EXTRA_OVERLAY_MODE, OverlayService.MODE_SEVERE_WARNING)
                                    }
                                    startService(overlayIntent)
                                }

                                Log.d("OCR_TRACE", "[$id] 🚨 FLAG CAUGHT: Saw='${scored.originalText}' -> Cleaned='${scored.rawToken}' -> Matched='${scored.matchedWord}' (Severity: $severity) in ${duration}ms 🚨")
                                sendConsoleUpdate("🚨 FLAG CAUGHT: OCR Saw='${scored.originalText}' -> Cleaned='${scored.rawToken}' -> Matched='${scored.matchedWord}' (Severity: $severity) in ${duration}ms 🚨")
                            } else {
                                Log.d("OCR_TRACE", "[$id] Debounced matched word: ${scored.matchedWord}")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR_TRACE", "[$id] ML Kit Processing Error", e)
                sendConsoleUpdate("Error: ML Kit Failed - ${e.message}")
            }
            .addOnCompleteListener {
                // Critical memory leak fix: Recycle after the async task finishes
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            }
    }

    // --- NATIVE ARGB PERCEPTUAL HASH ---
    // Replaces the old ALPHA_8 OcrPreprocessor hash logic
    private fun generateFastHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)

        var sum = 0
        val grays = IntArray(64)
        for (i in 0..63) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            // Standard luma weighting
            val gray = (r * 77 + g * 150 + b * 29) shr 8
            grays[i] = gray
            sum += gray
        }
        val mean = sum / 64
        var hash = 0L
        for (i in 0..63) {
            if (grays[i] >= mean) {
                hash = hash or (1L shl i)
            }
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

    // --- CORRECT ARGB_8888 EXTRACTION ---
    // Replaced the ALPHA_8 luma cruncher with a standard row padding crop
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val paddedWidth = image.width + rowPadding / pixelStride

        // Create standard ML Kit compliant ARGB bitmap
        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop the invisible hardware stride padding off the right side
        val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)

        // Don't leak the padded bitmap
        if (bitmap !== cleanBitmap) {
            bitmap.recycle()
        }

        return cleanBitmap
    }

    private fun createNotification(): Notification {
        val channelId = "monitor_svc"
        val channel = NotificationChannel(channelId, "Active Monitor", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("SafeMonitor Running")
            .setSmallIcon(R.mipmap.ic_launcher)
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
        @Volatile var isAppOpen = false // Generic flag
        @Volatile var currentAppName = "Unknown" // Dynamic name
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