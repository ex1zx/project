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
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): HandLandmarkerResult? =
        runCatching { landmarker.detect(BitmapImageBuilder(bitmap).build()) }.getOrNull()

    fun close() = landmarker.close()

    companion object {
        /** عدّ الأصابع المرفوعة من النقاط المطبّعة */
        fun countFingers(pts: FloatArray, mirrored: Boolean): Int {
            fun x(i: Int) = pts[i * 2]
            fun y(i: Int) = pts[i * 2 + 1]
            var count = 0
            // الأصابع الأربعة: الطرف أعلى من المفصل الأوسط
            for ((tip, pip) in listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)) {
                if (y(tip) < y(pip)) count++
            }
            // الإبهام: مقارنة أفقية
            val thumbOut = if (mirrored) x(4) > x(3) else x(4) < x(3)
            if (thumbOut && kotlin.math.abs(x(4) - x(17)) > kotlin.math.abs(x(3) - x(17))) count++
            return count.coerceIn(0, 5)
        }
    }
}
