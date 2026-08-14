package com.project.handusb

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var counterText: TextView
    private lateinit var usbText: TextView
    private lateinit var fpsText: TextView

    private lateinit var analysisExecutor: ExecutorService
    private var helper: HandLandmarkerHelper? = null
    private lateinit var usb: UsbSerialManager
    private lateinit var sender: SignalSender

    /** مخزن Bitmap مُعاد الاستخدام لتجنّب أي تخصيص ذاكرة لكل إطار */
    private var buffer: Bitmap? = null

    private var frameW = 1
    private var frameH = 1
    private var frameRotation = 0
    private var isFront = true

    /** تصويت على آخر 3 قراءات: يمنع الرفرفة بدون إضافة تأخير محسوس */
    private val history = IntArray(3) { -1 }
    private var historyIndex = 0
    private var historyFilled = 0

    /** مهلة قصيرة جداً قبل اعتبار اليد مفقودة */
    private val handLostGraceMs = 220L
    @Volatile private var lastHandSeenMs = 0L

    private var lastFpsTick = 0L
    private var framesSinceTick = 0

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else usbText.text = "الكاميرا: الصلاحية مرفوضة" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)
        counterText = findViewById(R.id.fingerCount)
        usbText = findViewById(R.id.usbStatus)
        fpsText = findViewById(R.id.fpsStatus)

        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        analysisExecutor = Executors.newSingleThreadExecutor()
        usb = UsbSerialManager(this) { s -> runOnUiThread { usbText.text = s } }
        usb.register()
        sender = SignalSender(usb) { v ->
            runOnUiThread { counterText.text = v.toString() }
        }

        analysisExecutor.execute {
            try {
                helper = HandLandmarkerHelper(
                    context = this,
                    onResult = { result, _ -> handleResult(result) },
                    onError = { }
                )
            } catch (t: Throwable) {
                runOnUiThread { counterText.text = "!" }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)

        usb.requestAndConnect()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    val h = helper ?: return@setAnalyzer
                    val w = image.width
                    val hh = image.height
                    val plane = image.planes[0]
                    val bmp: Bitmap
                    if (plane.rowStride == w * 4) {
                        // مسار سريع: نسخ مباشر إلى مخزن مُعاد الاستخدام (بدون تخصيص لكل إطار)
                        var reused = buffer
                        if (reused == null || reused.width != w || reused.height != hh) {
                            reused = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
                            buffer = reused
                        }
                        plane.buffer.rewind()
                        reused.copyPixelsFromBuffer(plane.buffer)
                        bmp = reused
                    } else {
                        bmp = image.toBitmap()
                    }

                    frameRotation = image.imageInfo.rotationDegrees
                    if (frameRotation == 90 || frameRotation == 270) {
                        frameW = hh; frameH = w
                    } else {
                        frameW = w; frameH = hh
                    }
                    h.detectAsync(bmp, frameRotation, SystemClock.uptimeMillis())
                } catch (_: Throwable) {
                } finally {
                    image.close()
                }
            }

            provider.unbindAll()
            isFront = true
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /** يصل من خيط MediaPipe فور جهوز النتيجة — لا انتظار للواجهة. */
    private fun handleResult(result: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult) {
        val lms = result.landmarks().firstOrNull()
        val now = SystemClock.elapsedRealtime()

        if (lms != null && lms.size == 21) {
            val pts = FloatArray(42)
            for (i in 0 until 21) {
                pts[i * 2] = lms[i].x()
                pts[i * 2 + 1] = lms[i].y()
            }
            val states = HandLandmarkerHelper.fingerStates(pts)
            var fingers = 0
            for (s in states) if (s) fingers++

            lastHandSeenMs = now
            overlay.setResults(pts, states, frameW, frameH, isFront)
            onFingers(fingers)
        } else {
            if (now - lastHandSeenMs > handLostGraceMs) {
                overlay.clear()
                resetHistory()
                sender.setValue(0)
            }
        }
        tickFps(now)
    }

    private fun resetHistory() {
        historyFilled = 0
        historyIndex = 0
        for (i in history.indices) history[i] = -1
    }

    /**
     * قرار لحظي: القيمة تُعتمد فوراً إذا تكرّرت مرتين ضمن آخر 3 قراءات
     * (نافذة ~30 مللي ثانية عند 60fps) — لا تأخير محسوس، وبلا رفرفة.
     */
    private fun onFingers(fingers: Int) {
        history[historyIndex] = fingers
        historyIndex = (historyIndex + 1) % history.size
        if (historyFilled < history.size) historyFilled++

        if (historyFilled < 2) {
            sender.setValue(fingers)
            return
        }
        var votes = 0
        for (i in 0 until historyFilled) if (history[i] == fingers) votes++
        if (votes >= 2 || fingers == sender.currentValue()) sender.setValue(fingers)
    }

    private fun tickFps(now: Long) {
        framesSinceTick++
        if (now - lastFpsTick >= 1000L) {
            val fps = framesSinceTick
            framesSinceTick = 0
            lastFpsTick = now
            runOnUiThread { fpsText.text = "$fps FPS" }
        }
    }

    override fun onResume() {
        super.onResume()
        usb.requestAndConnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        sender.setValue(0)
        sender.stop()
        usb.unregister()
        analysisExecutor.execute { helper?.close() }
        analysisExecutor.shutdown()
    }
}
