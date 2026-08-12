package com.project.handusb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** يرسم هيكل كف اليد بخطوط ناعمة تمتد حتى أطراف الأصابع */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val connections = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 17 to 18, 18 to 19, 19 to 20,
        0 to 17
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#67F5C4")
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3367F5C4")
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // إحداثيات مطبّعة 0..1 (21 نقطة)
    private var points: FloatArray? = null
    private var smoothed: FloatArray? = null
    private var imgW = 1
    private var imgH = 1

    fun clear() {
        points = null
        smoothed = null
        invalidate()
    }

    fun setResults(normalized: FloatArray, imageWidth: Int, imageHeight: Int) {
        imgW = imageWidth
        imgH = imageHeight
        val prev = smoothed
        smoothed = if (prev == null || prev.size != normalized.size) {
            normalized.copyOf()
        } else {
            FloatArray(normalized.size) { i -> prev[i] * 0.55f + normalized[i] * 0.45f }
        }
        points = smoothed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = points ?: return
        // scale-to-fill مثل PreviewView FILL_CENTER
        val scale = max(width.toFloat() / imgW, height.toFloat() / imgH)
        val dx = (width - imgW * scale) / 2f
        val dy = (height - imgH * scale) / 2f
        fun x(i: Int) = p[i * 2] * imgW * scale + dx
        fun y(i: Int) = p[i * 2 + 1] * imgH * scale + dy

        for ((a, b) in connections) {
            canvas.drawLine(x(a), y(a), x(b), y(b), glowPaint)
        }
        for ((a, b) in connections) {
            canvas.drawLine(x(a), y(a), x(b), y(b), linePaint)
        }
        for (i in 0 until p.size / 2) {
            val r = if (i % 4 == 0) 9f else 6f
            canvas.drawCircle(x(i), y(i), r, dotPaint)
        }
    }
}
