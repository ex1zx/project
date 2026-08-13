package com.project.handusb

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/** كشف كف يد واحد باستخدام MediaPipe (يعمل محلياً بالكامل بدون إنترنت) */
class HandLandmarkerHelper(context: Context) {

    private val landmarker: HandLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.35f)
            .setMinTrackingConfidence(0.35f)
            .setMinHandPresenceConfidence(0.35f)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): HandLandmarkerResult? =
        runCatching { landmarker.detect(BitmapImageBuilder(bitmap).build()) }.getOrNull()

    fun close() = landmarker.close()

    companion object {
        /**
         * عدّ الأصابع المرفوعة بطريقة لا تتأثر بدوران اليد:
         * الإصبع مرفوع إذا كان طرفه أبعد عن الرسغ من مفصله الأوسط.
         */
        fun countFingers(pts: FloatArray, mirrored: Boolean): Int {
            fun x(i: Int) = pts[i * 2]
            fun y(i: Int) = pts[i * 2 + 1]
            fun dist(a: Int, b: Int): Float {
                val dx = x(a) - x(b); val dy = y(a) - y(b)
                return kotlin.math.sqrt(dx * dx + dy * dy)
            }
            // مقياس اليد لتطبيع القرارات
            val palm = dist(0, 9).coerceAtLeast(1e-4f)
            var count = 0
            for ((tip, pip) in listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)) {
                if (dist(tip, 0) > dist(pip, 0) * 1.08f) count++
            }
            // الإبهام: بعد الطرف عن مفصل الخنصر مقارنة بالمفصل السفلي للإبهام
            if (dist(4, 17) > dist(2, 17) * 1.12f && dist(4, 0) > palm * 0.9f) count++
            return count.coerceIn(0, 5)
        }
    }
}
