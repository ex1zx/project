package com.project.handtracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: HandOverlayView
    private lateinit var countView: TextView
    private lateinit var connectionView: TextView
    private lateinit var permissionPanel: LinearLayout
    private lateinit var usbController: UsbSerialController
    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private var lastCount = 0

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionPanel.visibility = if (granted) View.GONE else View.VISIBLE
        if (granted) startCamera()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbSerialController.ACTION_USB_PERMISSION) return
            val device = intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null && usbController.connect(device)) {
                connectionView.text = "USB • متصل"
                connectionView.setTextColor(getColor(R.color.project_mint))
            } else {
                connectionView.text = "USB • يحتاج إذن"
                connectionView.setTextColor(getColor(R.color.project_dim))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.project_ink)
        window.navigationBarColor = getColor(R.color.project_ink)
        usbController = UsbSerialController(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        registerReceiver(usbReceiver, IntentFilter(UsbSerialController.ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        prepareUsb()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionPanel.visibility = View.GONE
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.project_ink))
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        overlayView = HandOverlayView(this)
        root.addView(overlayView, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 20, 24, 0)
        }
        val brand = TextView(this).apply {
            text = "PROJECT"
            textColor = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        connectionView = TextView(this).apply {
            text = "USB • غير متصل"
            textColor = getColor(R.color.project_dim)
            textSize = 12f
            setPadding(16, 0, 0, 0)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(connectionView)
        root.addView(top, FrameLayout.LayoutParams(-1, 74, Gravity.TOP))

        countView = TextView(this).apply {
            text = "0"
            textColor = getColor(R.color.project_ink)
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.project_backdrop)
            setTextColor(getColor(R.color.project_mint))
        }
        val countParams = FrameLayout.LayoutParams(56, 56, Gravity.TOP or Gravity.END).apply {
            topMargin = 92
            rightMargin = 24
        }
        root.addView(countView, countParams)

        permissionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 24, 36, 24)
            setBackgroundColor(Color.argb(224, 7, 18, 24))
        }
        val permissionTitle = TextView(this).apply {
            text = "نحتاج إلى الكاميرا"
            textColor = Color.WHITE
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val permissionBody = TextView(this).apply {
            text = "تعمل المعالجة على الهاتف فقط. اسمح بالكاميرا لبدء المرآة وتتبع الكف."
            textColor = getColor(R.color.project_dim)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 18)
        }
        val permissionButton = Button(this).apply {
            text = "السماح بالكاميرا"
            setOnClickListener {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        permissionPanel.addView(permissionTitle)
        permissionPanel.addView(permissionBody)
        permissionPanel.addView(permissionButton, LinearLayout.LayoutParams(-2, 54))
        root.addView(permissionPanel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        setContentView(root)
    }

    private fun prepareUsb() {
        val device = usbController.firstDevice() ?: return
        if (usbController.hasPermission(device)) {
            if (usbController.connect(device)) {
                connectionView.text = "USB • متصل"
                connectionView.setTextColor(getColor(R.color.project_mint))
            }
        } else {
            connectionView.text = "USB • اسمح بالوصول"
            usbController.requestPermission(device)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { image -> analyze(image) } }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            setupLandmarker()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupLandmarker() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setMinHandDetectionConfidence(0.62f)
            .setMinHandPresenceConfidence(0.62f)
            .setMinTrackingConfidence(0.62f)
            .setNumHands(1)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        try {
            val bitmap = YuvToRgbConverter.toBitmap(image)?.let {
                rotateBitmap(it, image.imageInfo.rotationDegrees)
            } ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker?.detect(mpImage)
            val landmarks = result?.landmarks()?.firstOrNull()
            val normalized = landmarks?.map { NormalizedPoint(it.x(), it.y()) }
            val count = normalized?.let { countRaisedFingers(it) } ?: 0
            runOnUiThread {
                overlayView.setLandmarks(normalized, mirror = true)
                countView.text = count.toString()
                if (count in 1..5 && count != lastCount) usbController.sendFingerCount(count)
                lastCount = count
            }
        } finally {
            image.close()
        }
    }

    private fun countRaisedFingers(points: List<NormalizedPoint>): Int {
        if (points.size < 21) return 0
        var count = 0
        val pairs = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)
        pairs.forEach { (tip, pip) ->
            if (points[tip].y < points[pip].y - 0.018f) count++
        }
        val thumbTip = points[4]
        val thumbIp = points[3]
        val thumbMcp = points[2]
        val wrist = points[0]
        val thumbLength = distance(thumbTip, wrist)
        val foldedLength = distance(thumbIp, wrist)
        if (thumbLength > foldedLength * 1.12f && distance(thumbTip, thumbMcp) > 0.08f) count++
        return count.coerceIn(0, 5)
    }

    private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
        return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }

    private fun rotateBitmap(bitmap: android.graphics.Bitmap, degrees: Int): android.graphics.Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return android.graphics.Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbController.close()
        handLandmarker?.close()
        cameraExecutor.shutdown()
    }
}