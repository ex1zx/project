package com.project.handusb

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.max

/**
 * رسم هيكل اليد بجودة عالية وبدون تأخير محسوس:
 * - مرشّح One-Euro تكيّفي: ثبات عند السكون وسرعة فورية عند الحركة.
 * - تنبؤ بالسرعة (velocity lead) لتعويض زمن المعالجة، فتبدو الرسمة ملتصقة باليد.
 * - إعادة رسم متزامنة مع تحديث الشاشة عبر Choreographer (سلاسة 60/120fps).
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val connections = intArrayOf(
        0, 1, 1, 2, 2, 3, 3, 4,
        0, 5, 5, 6, 6, 7, 7, 8,
        5, 9, 9, 10, 10, 11, 11, 12,
        9, 13, 13, 14, 14, 15, 15, 16,
        13, 17, 17, 18, 18, 19, 19, 20,
        0, 17
    )

    private val fingerChains = arrayOf(
        intArrayOf(0, 1, 2, 3, 4),
        intArrayOf(0, 5, 6, 7, 8),
        intArrayOf(0, 9, 10, 11, 12),
        intArrayOf(0, 13, 14, 15, 16),
        intArrayOf(0, 17, 18, 19, 20)
    )

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#3300E5A0")
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val palmFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1A00E5A0")
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val jointRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#CCFFFFFF")
    }
    private val tipActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF2BE8B0")
    }
    private val tipIdle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#66FFFFFF")
    }

    private val path = Path()

    private var raw: FloatArray? = null
    private var filtered: FloatArray? = null
    private var velocity = FloatArray(42)
    private var states = BooleanArray(5)
    private var lastUpdateNs = 0L
    private var lastFrameNs = 0L
    private var visible = false
    private var alphaFade = 0f
    private var imgW = 1
    private var imgH = 1
    private var mirrored = true

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            step(frameTimeNanos)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    fun clear() {
        visible = false
    }

    /** يُستدعى من خيط الاستدلال مباشرة (بدون انتظار الواجهة). */
    fun setResults(
        normalized: FloatArray,
        fingerStates: BooleanArray,
        imageWidth: Int,
        imageHeight: Int,
        isMirrored: Boolean
    ) {
        synchronized(this) {
            raw = normalized
            states = fingerStates
            imgW = imageWidth
            imgH = imageHeight
            mirrored = isMirrored
            lastUpdateNs = System.nanoTime()
            visible = true
        }
    }

    /** مرشّح One-Euro مبسّط + تنبؤ بالحركة، يعمل كل إطار عرض. */
    private fun step(nowNs: Long) {
        val dt = if (lastFrameNs == 0L) 0.016f else
            ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
        lastFrameNs = nowNs

        alphaFade += ((if (visible) 1f else 0f) - alphaFade) * (dt * 12f).coerceAtMost(1f)

        val target = synchronized(this) { raw } ?: return
        var f = filtered
        if (f == null || f.size != target.size) {
            f = target.copyOf()
            filtered = f
            velocity = FloatArray(target.size)
            return
        }
        for (i in target.indices) {
            val diff = target[i] - f[i]
            val speed = kotlin.math.abs(diff) / dt
            // كلما زادت السرعة زاد القطع الترددي => استجابة فورية بلا تأخير
            val cutoff = 2.0f + 45f * speed
            val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
            val alpha = (dt / (tau + dt)).coerceIn(0.15f, 1f)
            val next = f[i] + diff * alpha
            velocity[i] = (next - f[i]) / dt
            f[i] = next
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = filtered ?: return
        if (alphaFade <= 0.01f) return

        val scale = max(width.toFloat() / imgW, height.toFloat() / imgH)
        val dx = (width - imgW * scale) / 2f
        val dy = (height - imgH * scale) / 2f
        // تعويض زمن الاستدلال بتقديم بسيط جداً على مسار الحركة
        val lead = 0.012f

        fun x(i: Int): Float {
            val nx = (f[i * 2] + velocity[i * 2] * lead).coerceIn(-0.2f, 1.2f)
            val v = if (mirrored) 1f - nx else nx
            return v * imgW * scale + dx
        }
        fun y(i: Int): Float {
            val ny = (f[i * 2 + 1] + velocity[i * 2 + 1] * lead).coerceIn(-0.2f, 1.2f)
            return ny * imgH * scale + dy
        }

        val a = (alphaFade * 255f).toInt().coerceIn(0, 255)

        // كف شفاف
        path.reset()
        val palmIdx = intArrayOf(0, 1, 5, 9, 13, 17)
        path.moveTo(x(palmIdx[0]), y(palmIdx[0]))
        for (i in 1 until palmIdx.size) path.lineTo(x(palmIdx[i]), y(palmIdx[i]))
        path.close()
        palmFill.alpha = (a * 0.55f).toInt()
        canvas.drawPath(path, palmFill)

        // توهج
        glowPaint.alpha = (a * 0.55f).toInt()
        var k = 0
        while (k < connections.size) {
            val s = connections[k]; val e = connections[k + 1]
            canvas.drawLine(x(s), y(s), x(e), y(e), glowPaint)
            k += 2
        }

        // عظام بتدرّج لوني لكل إصبع (منحنيات ناعمة)
        for ((idx, chain) in fingerChains.withIndex()) {
            val active = states.getOrElse(idx) { false }
            linePaint.shader = LinearGradient(
                x(chain[0]), y(chain[0]), x(chain[4]), y(chain[4]),
                if (active) Color.parseColor("#00E5A0") else Color.parseColor("#7A8CA0"),
                if (active) Color.parseColor("#7DF9FF") else Color.parseColor("#AFC0CE"),
                Shader.TileMode.CLAMP
            )
            linePaint.alpha = if (active) a else (a * 0.75f).toInt()
            linePaint.strokeWidth = if (active) 9f else 6.5f

            path.reset()
            path.moveTo(x(chain[0]), y(chain[0]))
            for (i in 1 until chain.size) {
                val px0 = x(chain[i - 1]); val py0 = y(chain[i - 1])
                val px1 = x(chain[i]); val py1 = y(chain[i])
                path.quadTo(px0, py0, (px0 + px1) / 2f, (py0 + py1) / 2f)
            }
            path.lineTo(x(chain[4]), y(chain[4]))
            canvas.drawPath(path, linePaint)
        }
        linePaint.shader = null

        // مفاصل
        for (i in 0 until 21) {
            val isTip = i == 4 || i == 8 || i == 12 || i == 16 || i == 20
            if (isTip) {
                val fingerIdx = when (i) { 4 -> 0; 8 -> 1; 12 -> 2; 16 -> 3; else -> 4 }
                val paint = if (states.getOrElse(fingerIdx) { false }) tipActive else tipIdle
                paint.alpha = a
                canvas.drawCircle(x(i), y(i), 12f, paint)
                jointRing.alpha = (a * 0.8f).toInt()
                canvas.drawCircle(x(i), y(i), 16f, jointRing)
            } else {
                jointPaint.color = Color.WHITE
                jointPaint.alpha = (a * 0.9f).toInt()
                canvas.drawCircle(x(i), y(i), if (i == 0) 10f else 6f, jointPaint)
            }
        }
    }

    /** آخر لحظة وصلت فيها نتيجة (للتحكم بالإخفاء). */
    fun lastResultAgeMs(): Long =
        if (lastUpdateNs == 0L) Long.MAX_VALUE
        else (System.nanoTime() - lastUpdateNs) / 1_000_000

    @Suppress("unused")
    private fun nowMs() = SystemClock.elapsedRealtime()
}
