package com.handsignal.arduino

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Draws a smoothed hand skeleton (palm lines extending to the fingertips)
 * over the mirrored front-camera preview.
 */
class HandOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val connections = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
        intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
        intArrayOf(13, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
        intArrayOf(0, 17)
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(70, 120, 220, 255)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 120, 220, 255)
    }

    private var smoothed: Array<FloatArray>? = null
    private var alphaLevel = 0f
    private var srcW = 1
    private var srcH = 1

    /** points are normalized (0..1) in the analyzed (already rotated) image. */
    fun setHand(points: List<FloatArray>?, imageWidth: Int, imageHeight: Int) {
        srcW = max(1, imageWidth)
        srcH = max(1, imageHeight)
        if (points == null || points.size < 21) {
            alphaLevel = max(0f, alphaLevel - 0.15f)
            if (alphaLevel <= 0f) smoothed = null
        } else {
            val current = smoothed
            if (current == null) {
                smoothed = Array(21) { floatArrayOf(points[it][0], points[it][1]) }
            } else {
                val k = 0.45f // exponential smoothing for fluid motion
                for (i in 0 until 21) {
                    current[i][0] += (points[i][0] - current[i][0]) * k
                    current[i][1] += (points[i][1] - current[i][1]) * k
                }
            }
            alphaLevel = minOf(1f, alphaLevel + 0.25f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = smoothed ?: return
        if (alphaLevel <= 0.01f) return

        // FILL_CENTER mapping of the source image onto this view
        val scale = max(width.toFloat() / srcW, height.toFloat() / srcH)
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f

        val xs = FloatArray(21)
        val ys = FloatArray(21)
        for (i in 0 until 21) {
            // mirror X because the front preview is mirrored
            xs[i] = (1f - pts[i][0]) * srcW * scale + dx
            ys[i] = pts[i][1] * srcH * scale + dy
        }

        val a = (alphaLevel * 255).toInt()
        glowPaint.alpha = (a * 0.4f).toInt()
        linePaint.alpha = a
        dotPaint.alpha = (a * 0.92f).toInt()

        for (c in connections) {
            canvas.drawLine(xs[c[0]], ys[c[0]], xs[c[1]], ys[c[1]], glowPaint)
        }
        for (c in connections) {
            canvas.drawLine(xs[c[0]], ys[c[0]], xs[c[1]], ys[c[1]], linePaint)
        }
        for (i in 0 until 21) {
            val r = if (i == 4 || i == 8 || i == 12 || i == 16 || i == 20) 9f else 6f
            canvas.drawCircle(xs[i], ys[i], r, dotPaint)
        }
    }
}
