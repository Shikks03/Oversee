package com.example.prototype.service

// --- ANDROID CORE ---
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.example.prototype.R
import com.example.prototype.data.IncidentRepository
import com.example.prototype.data.model.Incident
import com.example.prototype.domain.ContentAnalyzer // Using your TextAnalyzer
import com.example.prototype.utils.sendConsoleUpdate // Import the extension we made earlier
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

    // Debounce Map: Stores "badword" -> Last Time Seen
    private val debounceMap = ConcurrentHashMap<String, Long>()

    // Frame skip: tracks previous frame brightness to avoid re-OCR-ing unchanged screens
    private var previousBrightness: Double = -1.0

    private val ocrExecutor = Executors.newSingleThreadExecutor()

    // UI Overlay (Compose)
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        prepareTesseract() // Copy assets first
        ContentAnalyzer.init(this)
        tess = TessBaseAPI().apply {
            init(filesDir.absolutePath, "eng", TessBaseAPI.OEM_DEFAULT)
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

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
        val broadcastIntent = Intent("com.example.prototype.CAPTURE_STARTED").apply {
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

        val destFile = File(tessDir, "eng.traineddata")
        if (!destFile.exists() || destFile.length() == 0L) {
            assets.open("tessdata/eng.traineddata").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("OCR", "prepareTesseract DONE — copied ${destFile.length() / 1024}KB")
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

                val currentId = attemptCount.incrementAndGet()
                Log.d(TAG, "System: Capture Cycle #$currentId started")

                val image = try {
                    imageReader.acquireLatestImage()
                } catch (e: Exception) { null }

                if (image != null) {
                    successCaptureCount.incrementAndGet()
                    val bitmap = imageToBitmap(image)
                    image.close()

                    ocrExecutor.execute {
                        runOcr(bitmap, currentId)
                        bitmap.recycle()
                    }
                } else {
                    Log.d(TAG, "Capture Cycle #$currentId — no image available")
                }
            } else {
                Log.d(TAG, "Capture skipped — isFacebookOpen=false")
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
            val t0 = System.currentTimeMillis()

            // 1. Crop status bar (top 5%) and nav bar (bottom 8%) — no useful text there
            val cropTop = (bitmap.height * 0.05).toInt()
            val cropBottom = (bitmap.height * 0.08).toInt()
            val cropped = Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, bitmap.height - cropTop - cropBottom)

            // 2. Scale down to 50%
            val scaled = cropped.scale(cropped.width / 2, cropped.height / 2, false)
            cropped.recycle()

            // 3. Frame skip BEFORE grayscale — avoids paying for conversion on unchanged frames
            val currentBrightness = computeAverageBrightness(scaled)
            if (kotlin.math.abs(currentBrightness - previousBrightness) < 5.0) {
                scaled.recycle()
                Log.d(TAG, "OCR Cycle #$id — Skipped (unchanged frame, brightness=${"%.1f".format(currentBrightness)})")
                return
            }
            previousBrightness = currentBrightness

            // 4. Grayscale + moderate contrast boost
            val grayscale = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also { bw ->
                val canvas = android.graphics.Canvas(bw)
                // Fill white first — screen captures on some devices have alpha=0,
                // which makes drawBitmap a no-op and leaves Tesseract with a blank image
                canvas.drawColor(android.graphics.Color.WHITE)
                // Desaturate then boost contrast
                val desaturate = android.graphics.ColorMatrix().also { it.setSaturation(0f) }
                val contrastBoost = android.graphics.ColorMatrix(floatArrayOf(
                    3f, 0f, 0f, 0f, -256f,
                    0f, 3f, 0f, 0f, -256f,
                    0f, 0f, 3f, 0f, -256f,
                    0f, 0f, 0f, 1f,    0f
                ))
                desaturate.postConcat(contrastBoost)
                val paint = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(desaturate)
                }
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            }
            scaled.recycle()

            val tPreprocess = System.currentTimeMillis()

            // 4. Run OCR
            Log.d(TAG, "OCR Processing Cycle #$id")
            api.setImage(grayscale)
            val text = api.utF8Text
            api.clear()
            grayscale.recycle()

            val tOcr = System.currentTimeMillis()
            val duration = tOcr - t0
            Log.d(TAG, "OCR Cycle #$id — Prep: ${tPreprocess - t0}ms, OCR: ${tOcr - tPreprocess}ms, Total: ${duration}ms")
            sendConsoleUpdate("OCR Cycle #$id — Prep: ${tPreprocess - t0}ms, OCR: ${tOcr - tPreprocess}ms, Total: ${duration}ms")
            Log.d(TAG, "OCR Text #$id: ${text?.trim() ?: "(empty)"}")

            if (!text.isNullOrBlank()) {
                // 3. Analyze text for inappropriate words
                val result = ContentAnalyzer.analyze(text)
                if (!result.isClean) {
                    result.incidents.forEach { incident ->

                        // 4. Debounce: Check if we've seen this word in the last 10s
                        val lastSeen = debounceMap[incident.word] ?: 0L
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastSeen > 10_000L) { // 10s Debounce
                            debounceMap[incident.word] = currentTime

                            // 5. Save and Sync
                            // High severity logic is handled inside saveIncident
                            IncidentRepository.saveIncident(
                                applicationContext,
                                Incident(incident.word, incident.severity, "Facebook")
                            )

                            sendConsoleUpdate("FLAG: '${incident.word}' (${incident.severity}) detected in ${duration}ms")
                            Log.d(TAG,"FLAG: '${incident.word}' (${incident.severity}) detected in ${duration}ms" )
                        }else{
                            Log.d(TAG, "Debounced ${incident.word}")
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

    /**
     * Returns the average luminance (0–255) of a bitmap by sampling every 4th pixel.
     * Works on both ARGB and grayscale bitmaps — uses standard luminance weights.
     * Sampling keeps this well under 1ms on the 33%-scaled image size.
     */
    private fun computeAverageBrightness(bitmap: Bitmap): Double {
        var total = 0L
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var i = 0
        while (i < pixels.size) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            total += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
            count++
            i += 4 // sample every 4th pixel
        }
        return if (count == 0) 0.0 else total.toDouble() / count
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
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
        tess?.end() // tess-two cleanup
        ocrExecutor.shutdownNow()
        super.onDestroy()
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