package com.project.handtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

data class NormalizedPoint(val x: Float, val y: Float)

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.project_mint)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(10f, 0f, 0f, context.getColor(R.color.project_blue))
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.project_white)
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, context.getColor(R.color.project_mint))
    }

    private var current = emptyList<NormalizedPoint>()
    private var target = emptyList<NormalizedPoint>()
    private var mirrored = true

    private val connections = arrayOf(
        intArrayOf(0, 1, 2, 3, 4),
        intArrayOf(0, 5, 6, 7, 8),
        intArrayOf(5, 9, 10, 11, 12),
        intArrayOf(9, 13, 14, 15, 16),
        intArrayOf(13, 17, 18, 19, 20),
        intArrayOf(0, 17),
        intArrayOf(5, 9, 13, 17),
    )

    fun setLandmarks(points: List<NormalizedPoint>?, mirror: Boolean) {
        mirrored = mirror
        target = points ?: emptyList()
        if (current.isEmpty() || current.size != target.size) current = target
        invalidate()
    }

    fun clear() {
        target = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (current.isEmpty() && target.isEmpty()) return
        val next = if (current.size == target.size) {
            current.mapIndexed { index, point ->
                val destination = target[index]
                NormalizedPoint(
                    point.x + (destination.x - point.x) * 0.32f,
                    point.y + (destination.y - point.y) * 0.32f,
                )
            }
        } else {
            target
        }
        current = next

        fun screenPoint(point: NormalizedPoint): Pair<Float, Float> {
            val x = (if (mirrored) 1f - point.x else point.x) * width
            val y = point.y * height
            return x to y
        }

        connections.forEach { chain ->
            val path = Path()
            chain.forEachIndexed { index, landmarkIndex ->
                if (landmarkIndex >= current.size) return@forEachIndexed
                val (x, y) = screenPoint(current[landmarkIndex])
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }
        current.forEach {
            val (x, y) = screenPoint(it)
            canvas.drawCircle(x, y, max(4f, width * 0.008f), dotPaint)
        }
        if (current != target) postInvalidateOnAnimation()
    }
}