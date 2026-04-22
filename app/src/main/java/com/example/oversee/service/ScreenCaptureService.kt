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

// --- PROJECT IMPORTS ---
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.model.Incident
import com.example.oversee.domain.TextAnalysisEngine
import com.example.oversee.domain.ToxicityScorer
import com.example.oversee.utils.sendConsoleUpdate // Import the extension we made earlier
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.graphics.scale

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val SYNC_INTERVAL = 28_800_000L // 8 Hours
        private const val CAPTURE_INTERVAL = 3000L    // 3 Seconds
        private const val DEBOUNCE_TIME_MS = 10_000L  // 10 Seconds Debounce
    }

    private lateinit var projection: MediaProjection
    private lateinit var imageReader: ImageReader
    private val handler = Handler(Looper.getMainLooper())

    // Atomic Counters (From your provided code)
    private val attemptCount = AtomicInteger(0)
    private val successCaptureCount = AtomicInteger(0)
    private val ocrFinishedCount = AtomicInteger(0)
    private var tess: TessBaseAPI? = null // Persistent instance
    private lateinit var textAnalysisEngine: TextAnalysisEngine

    // Debounce Map: Stores "badword" -> Last Time Seen
    private val debounceMap = ConcurrentHashMap<String, Long>()

    private val ocrExecutor = Executors.newSingleThreadExecutor()

    // UI Overlay (Compose)
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        prepareTesseract() // Copy assets first
        textAnalysisEngine = TextAnalysisEngine.fromAssets(this)
        tess = TessBaseAPI().apply {
            init(filesDir.absolutePath, "eng")
        }
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

        // Broadcast to dismiss the "Enable" Blocker
        val broadcastIntent = Intent("com.example.oversee.CAPTURE_STARTED").apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        CaptureState.isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupVirtualDisplay()

        // Start Loops
        handler.post(captureRunnable)
        handler.postDelayed(syncRunnable, SYNC_INTERVAL)

        return START_STICKY
    }


    private fun prepareTesseract() {
        val tessDir = File(filesDir, "tessdata")
        if (!tessDir.exists()) tessDir.mkdirs()

        val jsonData = File(tessDir, "eng.traineddata")
        if (!jsonData.exists()) {
            assets.open("tessdata/eng.traineddata").use { input ->
                jsonData.outputStream().use { output ->
                    input.copyTo(output)
                }
                Log.d("OCR","prepareTesseract DONE")
            }
        }
    }

    // --- CAPTURE LOOP ---
    private val captureRunnable = object : Runnable {
        override fun run() {
            // Hard Gate: Only process if Facebook is confirmed open
            if (ScreenState.isFacebookOpen) {

                // Ensure Overlay is Visible (Main Thread)
                if (overlayView == null) {
                    handler.post { showMonitoringOverlay() }
                }

                // Log Attempt
                val currentId = attemptCount.incrementAndGet()
                Log.d(TAG,"System: Capture Cycle #$currentId started")
                sendConsoleUpdate("System: Capture Cycle #$currentId started")

                val image = try {
                    imageReader.acquireLatestImage()
                } catch (e: Exception) { null }

                if (image != null) {
                    successCaptureCount.incrementAndGet()
                    val bitmap = imageToBitmap(image)
                    image.close()

                    // 🟢 FIX: Submit to the queue instead of spawning a raw Thread
                    ocrExecutor.execute {
                        runOcr(bitmap, currentId)
                        bitmap.recycle() // Recycle AFTER OCR is done
                    }
                }
            } else {
                // Not in Facebook: Hide Overlay & Flush Buffer
                if (overlayView != null) {
                    handler.post { removeOverlay() }
                }
                imageReader.acquireLatestImage()?.close()
            }

            handler.postDelayed(this, CAPTURE_INTERVAL)
        }
    }

    // --- OCR LOGIC (With Timing & Debounce) ---
    private fun runOcr(bitmap: Bitmap, id: Int) {
        val api = tess ?: return // Ensure Tesseract is ready

        try {
            val startTime = System.currentTimeMillis()

            // 1. Optimization: Scale down to improve speed
            val scaled = bitmap.scale(bitmap.width / 2, bitmap.height / 2, false)
            Log.d(TAG, "OCR Processing Cycle #$id")
            // 2. Process Image
            api.setImage(scaled)
            val text = api.utF8Text
            scaled.recycle() // Free memory immediately

            val duration = System.currentTimeMillis() - startTime

            if (!text.isNullOrBlank()) {
                val analysisResult = textAnalysisEngine.analyze(text)
                val scoredWords = ToxicityScorer.score(analysisResult)
                Log.d(TAG, "OCR Text: $text")
                if (scoredWords.isNotEmpty()) {
                    scoredWords.forEach { scored ->
                        val lastSeen = debounceMap[scored.matchedWord] ?: 0L
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSeen > 10_000L) {
                            debounceMap[scored.matchedWord] = currentTime
                            val severity = mapSeverity(scored.severity)
                            IncidentRepository.saveIncident(
                                applicationContext,
                                Incident(scored.matchedWord, severity, "Facebook")
                            )
                            sendConsoleUpdate("FLAG: '${scored.matchedWord}' ($severity, score=${scored.toxicityScore}) detected in ${duration}ms")
                            Log.d(TAG, "FLAG: '${scored.matchedWord}' ($severity, score=${scored.toxicityScore}) detected in ${duration}ms")
                        } else {
                            Log.d(TAG, "Debounced ${scored.matchedWord}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenCapture", "OCR Processing Error", e)
            sendConsoleUpdate("Error: OCR Failed - ${e.message}")
        }
    }

    // --- COMPOSE OVERLAY (Click-Through) ---
    private fun showMonitoringOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 🟢 CLICK THROUGH FLAGS
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40
            y = 100
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
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            Thread { IncidentRepository.syncData(applicationContext) }.start()
            handler.postDelayed(this, SYNC_INTERVAL)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
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
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        CaptureState.isRunning = false
        if (::projection.isInitialized) projection.stop()
        tess?.end()
        ocrExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun mapSeverity(scorerSeverity: String): String = when (scorerSeverity) {
        "Severe"   -> "HIGH"
        "Moderate" -> "MEDIUM"
        "Mild"     -> "LOW"
        else       -> "LOW"
    }

    // Global States
    object CaptureState { @Volatile var isRunning = false }
    object ScreenState { @Volatile var isFacebookOpen = false }
}

// --- COMPOSE UI ---
@Composable
fun MonitoringIndicator() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            // Semi-transparent blue
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