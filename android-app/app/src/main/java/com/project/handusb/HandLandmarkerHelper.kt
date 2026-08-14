package com.project.handusb

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * كشف اليد في الزمن الحقيقي (LIVE_STREAM) باستخدام MediaPipe.
 * - يعمل على GPU مع تراجع تلقائي إلى CPU.
 * - لا يحجب خيط الكاميرا: النتائج تصل عبر callback فور جهوزها.
 */
class HandLandmarkerHelper(
    context: Context,
    private val onResult: (HandLandmarkerResult, Long) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {

    private var landmarker: HandLandmarker

    init {
        landmarker = build(context, Delegate.GPU) ?: build(context, Delegate.CPU)
            ?: throw IllegalStateException("تعذّر تهيئة نموذج اليد")
    }

    private fun build(context: Context, delegate: Delegate): HandLandmarker? = runCatching {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(delegate)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setNumHands(1)
            // عتبات أعلى قليلاً = نقاط أثبت وأدق، مع تتبّع سريع بين الإطارات
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onResult(result, System.nanoTime()) }
            .setErrorListener { e -> onError(e) }
            .build()
        HandLandmarker.createFromOptions(context, options)
    }.getOrNull()

    /** يدفع إطاراً للمعالجة غير المتزامنة. لا ينسخ ولا يدوّر الصورة (الدوران يتم داخل المكتبة). */
    fun detectAsync(bitmap: Bitmap, rotationDegrees: Int, timestampMs: Long) {
        runCatching {
            val image: MPImage = BitmapImageBuilder(bitmap).build()
            val opts = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()
            landmarker.detectAsync(image, opts, timestampMs)
        }
    }

    fun close() = runCatching { landmarker.close() }

    companion object {

        /**
         * عدّ الأصابع بدقة عالية ومستقلة تماماً عن دوران اليد أو اتجاهها:
         * يعتمد على زوايا المفاصل (MCP/PIP/DIP) + إسقاط الإصبع على محور الكف.
         * pts: 42 قيمة (x,y) مطبّعة.
         */
        fun countFingers(pts: FloatArray): Int {
            var count = 0
            for (f in FINGERS) if (isExtended(pts, f)) count++
            if (isThumbExtended(pts)) count++
            return count.coerceIn(0, 5)
        }

        /** يعيد حالة كل إصبع: [إبهام, سبابة, وسطى, بنصر, خنصر] */
        fun fingerStates(pts: FloatArray): BooleanArray = booleanArrayOf(
            isThumbExtended(pts),
            isExtended(pts, FINGERS[0]),
            isExtended(pts, FINGERS[1]),
            isExtended(pts, FINGERS[2]),
            isExtended(pts, FINGERS[3])
        )

        // (mcp, pip, dip, tip)
        private val FINGERS = arrayOf(
            intArrayOf(5, 6, 7, 8),
            intArrayOf(9, 10, 11, 12),
            intArrayOf(13, 14, 15, 16),
            intArrayOf(17, 18, 19, 20)
        )

        private fun px(p: FloatArray, i: Int) = p[i * 2]
        private fun py(p: FloatArray, i: Int) = p[i * 2 + 1]

        private fun dist(p: FloatArray, a: Int, b: Int): Float =
            hypot(px(p, a) - px(p, b), py(p, a) - py(p, b))

        /** الزاوية عند b بين المتجهين b->a و b->c بالدرجات */
        private fun angle(p: FloatArray, a: Int, b: Int, c: Int): Float {
            val ax = px(p, a) - px(p, b); val ay = py(p, a) - py(p, b)
            val cx = px(p, c) - px(p, b); val cy = py(p, c) - py(p, b)
            val na = sqrt(ax * ax + ay * ay); val nc = sqrt(cx * cx + cy * cy)
            if (na < 1e-6f || nc < 1e-6f) return 180f
            val cos = ((ax * cx + ay * cy) / (na * nc)).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cos).toDouble()).toFloat()
        }

        private fun isExtended(p: FloatArray, f: IntArray): Boolean {
            val mcp = f[0]; val pip = f[1]; val dip = f[2]; val tip = f[3]
            val palm = dist(p, 0, 9).coerceAtLeast(1e-4f)

            // 1) استقامة المفاصل
            val pipAngle = angle(p, mcp, pip, dip)
            val dipAngle = angle(p, pip, dip, tip)
            val straight = pipAngle > 155f && dipAngle > 145f

            // 2) امتداد الطرف بعيداً عن الرسغ مقارنة بالمفصل الأوسط
            val reach = dist(p, tip, 0) > dist(p, pip, 0) * 1.02f

            // 3) طول الإصبع الظاهر منسوباً لحجم الكف (يمنع اعتبار إصبع مطويّ نحو الكاميرا ممدوداً)
            val length = dist(p, mcp, tip) / palm > 0.62f

            // قرار مركّب: الاستقامة القوية وحدها تكفي، أو دليلان من الثلاثة
            val votes = (if (straight) 1 else 0) + (if (reach) 1 else 0) + (if (length) 1 else 0)
            return (straight && reach) || votes >= 2 && pipAngle > 130f
        }

        private fun isThumbExtended(p: FloatArray): Boolean {
            val palm = dist(p, 0, 9).coerceAtLeast(1e-4f)
            val ipAngle = angle(p, 2, 3, 4)
            val mcpAngle = angle(p, 1, 2, 3)
            val straight = ipAngle > 150f && mcpAngle > 135f
            // ابتعاد الإبهام جانبياً عن مفصل السبابة والخنصر
            val spreadIndex = dist(p, 4, 5) / palm > 0.72f
            val spreadPinky = dist(p, 4, 17) > dist(p, 2, 17) * 1.10f
            val awayFromPalm = dist(p, 4, 9) / palm > 0.95f
            val votes = (if (straight) 1 else 0) + (if (spreadIndex) 1 else 0) +
                (if (spreadPinky) 1 else 0) + (if (awayFromPalm) 1 else 0)
            return votes >= 3
        }

        /** مقياس ثقة بسيط لجودة الوضعية (لتفادي القرارات على يد شبه مخفية). */
        fun poseQuality(pts: FloatArray): Float {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until 21) {
                val x = px(pts, i); val y = py(pts, i)
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            val span = min(maxX - minX, maxY - minY)
            val palm = dist(pts, 0, 9)
            return min(1f, palm * 6f + abs(span) * 2f)
        }
    }
}