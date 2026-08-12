package com.handsignal.arduino

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handsignal.arduino.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analysisExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private lateinit var usb: UsbSerialManager

    private var lastSentValue = -1
    private var stableValue = -1
    private var stableFrames = 0

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else binding.statusText.text = "لا توجد صلاحية للكاميرا"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analysisExecutor = Executors.newSingleThreadExecutor()
        usb = UsbSerialManager(this) { msg ->
            runOnUiThread { binding.statusText.text = msg }
        }
        setupLandmarker()
        usb.connect()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun setupLandmarker() {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result, input -> onResults(result, input.width, input.height) }
                .setErrorListener { e -> Log.e(TAG, "MediaPipe error", e) }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "failed to create landmarker", e)
            binding.statusText.text = "تعذر تحميل نموذج اليد"
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) { proxy.close(); return }
        try {
            val bitmap = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(proxy.planes[0].buffer)
            val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            landmarker.detectAsync(BitmapImageBuilder(rotated).build(), System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "analyze failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun onResults(result: HandLandmarkerResult, inputWidth: Int, inputHeight: Int) {
        val hand = result.landmarks().firstOrNull()
        val points = hand?.map { floatArrayOf(it.x(), it.y()) }
        val fingers = if (hand == null) 0 else countFingers(result)

        runOnUiThread {
            binding.overlay.setHand(points, inputWidth, inputHeight)
            binding.fingerCount.text = fingers.toString()
        }
        debounceAndSend(fingers)
    }

    /** Keeps the serial line quiet until a value has been stable for a few frames. */
    private fun debounceAndSend(value: Int) {
        if (value == stableValue) stableFrames++ else { stableValue = value; stableFrames = 1 }
        if (stableFrames == 3 && value != lastSentValue) {
            lastSentValue = value
            if (value in 1..5) usb.send("$value\n")
        }
    }

    private fun countFingers(result: HandLandmarkerResult): Int {
        val lm = result.landmarks().first()
        var count = 0
        // index, middle, ring, pinky: tip above (smaller y than) the PIP joint
        val tips = intArrayOf(8, 12, 16, 20)
        val pips = intArrayOf(6, 10, 14, 18)
        for (i in tips.indices) {
            if (lm[tips[i]].y() < lm[pips[i]].y()) count++
        }
        // thumb: compare horizontally, direction depends on which hand it is
        val isRight = result.handedness().firstOrNull()?.firstOrNull()?.categoryName() == "Right"
        val thumbTip = lm[4].x()
        val thumbIp = lm[3].x()
        if (if (isRight) thumbTip < thumbIp else thumbTip > thumbIp) count++
        return count.coerceIn(0, 5)
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        handLandmarker?.close()
        usb.close()
    }

    companion object { private const val TAG = "HandSignal" }
}
