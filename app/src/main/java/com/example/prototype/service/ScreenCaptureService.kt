package com.example.prototype.service

// --- ANDROID CORE ---
import android.app.*
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
        private const val CROP_TOP_RATIO = 0.03f      // Status bar only (~72px on 2400px screen)
        private const val CROP_BOTTOM_RATIO = 0.055f  // Nav bar only (~132px on 2400px screen)
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
            init(filesDir.absolutePath, "eng")
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            // Only recognize letters and space — eliminates garbage from icons,
            // emoji, reaction counts, and other Facebook UI elements.
            setVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ ")
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
        val assetSize = assets.open("tessdata/eng.traineddata").use { it.available().toLong() }

        if (!destFile.exists() || destFile.length() != assetSize) {
            assets.open("tessdata/eng.traineddata").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("OCR", "prepareTesseract: copied eng.traineddata ($assetSize bytes)")
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
            val t0 = System.currentTimeMillis()

            // 1. Crop system bars (status bar top, nav bar bottom) — zero-copy
            val cropped = cropSystemBars(bitmap)
            val t1 = System.currentTimeMillis()

            // 2. Scale down 50% (crop-before-scale reduces pixels for the scale op)
            val scaled = cropped.scale(cropped.width / 2, cropped.height / 2, false)
            cropped.recycle()
            val t2 = System.currentTimeMillis()

            // 3. OCR
            api.setImage(scaled)
            val text = api.utF8Text
            scaled.recycle()
            val t3 = System.currentTimeMillis()

            Log.d(TAG, "OCR #$id: crop=${t1-t0}ms scale=${t2-t1}ms ocr=${t3-t2}ms total=${t3-t0}ms")
            sendConsoleUpdate("OCR #$id: prep=${t2-t0}ms ocr=${t3-t2}ms total=${t3-t0}ms")

            if (!text.isNullOrBlank()) {
                // 4. Analyze text for inappropriate words
                val result = ContentAnalyzer.analyze(text)

                Log.d(TAG, "OCR Text: $text")
                if (!result.isClean) {
                    result.incidents.forEach { incident ->

                        // 5. Debounce: Check if we've seen this word in the last 10s
                        val lastSeen = debounceMap[incident.word] ?: 0L
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastSeen > 10_000L) {
                            debounceMap[incident.word] = currentTime

                            // 6. Save and Sync
                            IncidentRepository.saveIncident(
                                applicationContext,
                                Incident(incident.word, incident.severity, "Facebook")
                            )

                            sendConsoleUpdate("FLAG: '${incident.word}' (${incident.severity}) in ${t3-t0}ms")
                            Log.d(TAG, "FLAG: '${incident.word}' (${incident.severity}) in ${t3-t0}ms")
                        } else {
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

    private fun cropSystemBars(bitmap: Bitmap): Bitmap {
        val topCrop = (bitmap.height * CROP_TOP_RATIO).toInt()
        val bottomCrop = (bitmap.height * CROP_BOTTOM_RATIO).toInt()
        return Bitmap.createBitmap(
            bitmap, 0, topCrop, bitmap.width, bitmap.height - topCrop - bottomCrop
        )
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
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        // When rowPadding == 0, createBitmap returns padded itself (Android optimization).
        // Recycling padded in that case would immediately invalidate the returned bitmap.
        return if (rowPadding == 0) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
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
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        CaptureState.isRunning = false
        if (::projection.isInitialized) projection.stop()
        tess?.end()
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