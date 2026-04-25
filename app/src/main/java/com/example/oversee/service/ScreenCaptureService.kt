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

// --- PROJECT IMPORTS ---
import com.example.oversee.data.IncidentRepository
import com.example.oversee.data.local.AppPreferenceManager
import com.example.oversee.data.model.Incident
import com.example.oversee.domain.TextAnalysisEngine
import com.example.oversee.domain.ToxicityScorer
import com.example.oversee.utils.sendConsoleUpdate
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
    private var tess: TessBaseAPI? = null
    private var tessBaseline: TessBaseAPI? = null
    private lateinit var textAnalysisEngine: TextAnalysisEngine

    private val debounceMap = ConcurrentHashMap<String, Long>()
    private val ocrExecutor = Executors.newSingleThreadExecutor()

    // Step 6: cached system-bar insets computed once in setupVirtualDisplay
    private var topInset = 0
    private var bottomInset = 0

    // Step 10: last pHash for frame-diff gate
    @Volatile private var lastHash: Long? = null

    // Step 8: one-slot bitmap pool for the ALPHA_8 source bitmap
    @Volatile private var reusableBitmap: Bitmap? = null

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        prepareTesseract()
        textAnalysisEngine = TextAnalysisEngine.fromAssets(this)
        tess = TessBaseAPI().apply {
            init(filesDir.absolutePath, "eng")
            // Step 1 (A7): DPI hint so Tesseract skips internal scale inference
            setVariable("user_defined_dpi", resources.displayMetrics.densityDpi.toString())
            // Step 2 (B3): PSM_SPARSE_TEXT matches Facebook's scattered-card layout
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            // Step 3 (C2): conservative whitelist — keeps digits for leet normalisation,
            // drops emoji / private-use Unicode that bloats the token set
            setVariable(
                "tessedit_char_whitelist",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,!?'\""
            )
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

        val broadcastIntent = Intent("com.example.oversee.CAPTURE_STARTED").apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        CaptureState.isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupVirtualDisplay()

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
                jsonData.outputStream().use { output -> input.copyTo(output) }
                Log.d("OCR", "prepareTesseract DONE")
            }
        }
    }

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
                        // Step 8: return to pool instead of recycling
                        reusableBitmap = bitmap
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
        // Step 5: skip OCR while keyboard is up — user is typing, not reading feed
        if (ScreenState.isKeyboardVisible) return

        val api = tess ?: return
        try {
            val startTime = System.currentTimeMillis()
            val debugMode = AppPreferenceManager.getBoolean(applicationContext, "ocr_debug", false)

            Log.d("OCR_TRACE", "[$id] --- Pipeline Started --- | Source: ${bitmap.width}x${bitmap.height}")

            // Step 6 (A1): crop status-bar and nav-bar strips
            val cropStart = System.currentTimeMillis()
            val cropped = if (topInset + bottomInset > 0) {
                val cropHeight = bitmap.height - topInset - bottomInset
                if (cropHeight > 0)
                    Bitmap.createBitmap(bitmap, 0, topInset, bitmap.width, cropHeight)
                else bitmap
            } else bitmap
            Log.d("OCR_TRACE", "[$id] Crop: ${System.currentTimeMillis() - cropStart}ms | New Size: ${cropped.width}x${cropped.height}")

            if (debugMode) {
                runAbTest(bitmap, cropped, api, id, startTime)
                if (cropped !== bitmap) cropped.recycle()
                return
            }

            // Step 10 (A2): pHash frame-diff gate — skip if frame looks identical
            val hashStart = System.currentTimeMillis()
            val hash = OcrPreprocessor.perceptualHash(cropped)
            val hashDiff = if (lastHash != null) java.lang.Long.bitCount(hash xor lastHash!!) else -1
            Log.d("OCR_TRACE", "[$id] pHash: ${System.currentTimeMillis() - hashStart}ms | Diff from last: $hashDiff bits")

            if (lastHash != null && hashDiff < 5) {
                if (cropped !== bitmap) cropped.recycle()
                Log.d("OCR_TRACE", "[$id] ABORT: Duplicate frame skipped (hash gate)")
                return
            }
            lastHash = hash

            // Step 9 (B1): Otsu binarisation (skipped automatically on photo-backed posts)
            val otsuStart = System.currentTimeMillis()
            val processed = OcrPreprocessor.applyOtsu(cropped)
            Log.d("OCR_TRACE", "[$id] Otsu: ${System.currentTimeMillis() - otsuStart}ms")

            // Feed grayscale bytes directly — 1 byte/pixel, 1/4 the RGBA upload size
            val tessStart = System.currentTimeMillis()
            val bytes = OcrPreprocessor.extractBytes(processed)
            api.setImage(bytes, processed.width, processed.height, 1, processed.width)
            val text = api.utF8Text
            Log.d("OCR_TRACE", "[$id] Tesseract: ${System.currentTimeMillis() - tessStart}ms | Chars found: ${text?.length ?: 0}")

            if (processed !== cropped) processed.recycle()
            if (cropped !== bitmap) cropped.recycle()

            val duration = System.currentTimeMillis() - startTime
            Log.d("OCR_TRACE", "[$id] --- Pipeline Complete --- | Total: ${duration}ms")

            if (!text.isNullOrBlank()) {
                val analysisResult = textAnalysisEngine.analyze(text)
                val scoredWords = ToxicityScorer.score(analysisResult)
                Log.d(TAG, "OCR Text: $text")
                if (scoredWords.isNotEmpty()) {
                    scoredWords.forEach { scored ->
                        val lastSeen = debounceMap[scored.matchedWord] ?: 0L
                        val now = System.currentTimeMillis()
                        if (now - lastSeen > DEBOUNCE_TIME_MS) {
                            debounceMap[scored.matchedWord] = now
                            val severity = mapSeverity(scored.severity)
                            IncidentRepository.saveIncident(
                                applicationContext,
                                Incident(
                                    rawWord = scored.rawToken,
                                    matchedWord = scored.matchedWord,
                                    severity = severity,
                                    appName = "Facebook"
                                )
                            )
                            Log.d("OCR_TRACE", "[$id] 🚨 FLAG CAUGHT: Saw='${scored.originalText}' -> Cleaned='${scored.rawToken}' -> Matched='${scored.matchedWord}' (Severity: $severity) in ${duration}ms 🚨")
                            sendConsoleUpdate("🚨 FLAG CAUGHT: OCR Saw='${scored.originalText}' -> Cleaned='${scored.rawToken}' -> Matched='${scored.matchedWord}' (Severity: $severity) in ${duration}ms 🚨")
                        } else {
                            Log.d("OCR_TRACE", "[$id] Debounced matched word: ${scored.matchedWord}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OCR_TRACE", "[$id] OCR Processing Error", e)
            sendConsoleUpdate("Error: OCR Failed - ${e.message}")
        }
    }

    // A/B harness: runs baseline (no crop, no Otsu, default Tesseract) and candidate
    // (full pipeline) on the same ALPHA_8 source; logs token diff to ocr_ab.log.
    // Only baseline's incident saves persist — candidate output is metric-only.
    private fun runAbTest(sourceBitmap: Bitmap, candidateBitmap: Bitmap, api: TessBaseAPI, id: Int, startTime: Long) {
        try {
            val bl = tessBaseline ?: TessBaseAPI().apply {
                init(filesDir.absolutePath, "eng")
            }.also { tessBaseline = it }

            // Pipeline A: no scale, no Otsu, default Tesseract config
            val bytesA = OcrPreprocessor.extractBytes(sourceBitmap)
            val timeAStart = System.nanoTime()
            bl.setImage(bytesA, sourceBitmap.width, sourceBitmap.height, 1, sourceBitmap.width)
            val textA = bl.utF8Text ?: ""
            val latA = System.nanoTime() - timeAStart

            // Pipeline B: Otsu on already-cropped bitmap, optimised Tesseract config
            val processedB = OcrPreprocessor.applyOtsu(candidateBitmap)
            val bytesB = OcrPreprocessor.extractBytes(processedB)
            val timeBStart = System.nanoTime()
            api.setImage(bytesB, processedB.width, processedB.height, 1, processedB.width)
            val textB = api.utF8Text ?: ""
            val latB = System.nanoTime() - timeBStart
            if (processedB !== candidateBitmap) processedB.recycle()

            // Token-set diff (ground-truth metric: matched flagged words)
            val tA = textA.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val tB = textB.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val onlyA = (tA - tB).take(3)
            val onlyB = (tB - tA).take(3)
            val matchedA = textAnalysisEngine.analyze(textA).detectedWords.size
            val matchedB = textAnalysisEngine.analyze(textB).detectedWords.size

            val entry = """{"ts":${System.currentTimeMillis()},"frame":$id,"latA_ns":$latA,"latB_ns":$latB,"onlyA":$onlyA,"onlyB":$onlyB,"matchedA":$matchedA,"matchedB":$matchedB}"""
            try {
                FileWriter(File(filesDir, "ocr_ab.log"), true).use { it.appendLine(entry) }
            } catch (e: Exception) {
                Log.w(TAG, "A/B log write failed: ${e.message}")
            }
            Log.d(TAG, "A/B #$id: latA=${latA / 1_000_000}ms latB=${latB / 1_000_000}ms onlyA=$onlyA onlyB=$onlyB")
            sendConsoleUpdate("A/B #$id: baseline=${latA / 1_000_000}ms candidate=${latB / 1_000_000}ms matched A=$matchedA B=$matchedB")

            // Only baseline's incidents are saved
            val scoredWords = ToxicityScorer.score(textAnalysisEngine.analyze(textA))
            scoredWords.forEach { scored ->
                val lastSeen = debounceMap[scored.matchedWord] ?: 0L
                val now = System.currentTimeMillis()
                if (now - lastSeen > DEBOUNCE_TIME_MS) {
                    debounceMap[scored.matchedWord] = now
                    IncidentRepository.saveIncident(
                        applicationContext,
                        Incident(
                            rawWord = scored.rawToken,
                            matchedWord = scored.matchedWord,
                            severity = mapSeverity(scored.severity),
                            appName = "Facebook"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "A/B test error", e)
        }
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

    private val syncRunnable = object : Runnable {
        override fun run() {
            Thread { IncidentRepository.syncData(applicationContext) }.start()
            handler.postDelayed(this, SYNC_INTERVAL)
        }
    }

    // Step 7 (A3) + Step 8 (A6): direct-luminance grayscale → ALPHA_8 + bitmap pooling.
    // Avoids Canvas/ColorMatrix overhead and halves GC churn on repeated captures.
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buf = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val out = ByteArray(w * h)
        var di = 0
        for (y in 0 until h) {
            var si = y * rowStride
            for (x in 0 until w) {
                val r = buf[si].toInt() and 0xFF
                val g = buf[si + 1].toInt() and 0xFF
                val b = buf[si + 2].toInt() and 0xFF
                out[di++] = ((r * 77 + g * 150 + b * 29) ushr 8).toByte()
                si += pixelStride
            }
        }
        val existing = reusableBitmap
        val bitmap = if (existing != null && !existing.isRecycled && existing.width == w && existing.height == h) {
            existing
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(out))
        return bitmap
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
        tessBaseline?.end()
        reusableBitmap?.recycle()
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
