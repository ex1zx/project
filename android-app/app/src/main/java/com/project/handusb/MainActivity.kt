package com.project.handusb

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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

    private lateinit var analysisExecutor: ExecutorService
    private var helper: HandLandmarkerHelper? = null
    private lateinit var usb: UsbSerialManager

    private var lastSent = -1
    private var lastSentAt = 0L

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else usbText.text = "الكاميرا: الصلاحية مرفوضة" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)
        counterText = findViewById(R.id.fingerCount)
        usbText = findViewById(R.id.usbStatus)

        analysisExecutor = Executors.newSingleThreadExecutor()
        usb = UsbSerialManager(this) { s -> runOnUiThread { usbText.text = s } }
        usb.register()

        analysisExecutor.execute { helper = HandLandmarkerHelper(this) }

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
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    val h = helper
                    if (h == null) { image.close(); return@setAnalyzer }
                    val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(image.planes[0].buffer)
                    val m = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                        postScale(-1f, 1f) // مرآة للكاميرا الأمامية
                    }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    val result = h.detect(rotated)
                    val lms = result?.landmarks()?.firstOrNull()
                    if (lms != null && lms.size == 21) {
                        val pts = FloatArray(42)
                        lms.forEachIndexed { i, lm ->
                            pts[i * 2] = lm.x()
                            pts[i * 2 + 1] = lm.y()
                        }
                        val fingers = HandLandmarkerHelper.countFingers(pts, true)
                        runOnUiThread {
                            overlay.setResults(pts, rotated.width, rotated.height)
                            counterText.text = fingers.toString()
                        }
                        maybeSend(fingers)
                    } else {
                        runOnUiThread {
                            overlay.clear()
                            counterText.text = "0"
                        }
                        maybeSend(0)
                    }
                    rotated.recycle(); bmp.recycle()
                } catch (_: Throwable) {
                } finally {
                    image.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /** يرسل الرقم عند تغيّره فقط (مع حد أدنى زمني) */
    private fun maybeSend(value: Int) {
        val now = System.currentTimeMillis()
        if (value != lastSent && now - lastSentAt > 150) {
            lastSent = value
            lastSentAt = now
            usb.send(value)
        }
    }

    override fun onResume() {
        super.onResume()
        usb.requestAndConnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        usb.unregister()
        analysisExecutor.execute { helper?.close() }
        analysisExecutor.shutdown()
    }
}
