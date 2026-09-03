package com.noam.gate

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * A horizontal line that spans the screen and sits at a height set by [progress]:
 * 0 puts it at the bottom, 1 at the top. The gate animates it up and then back
 * down while the ten seconds run out.
 */
class SweepLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lineColor = ContextCompat.getColor(context, R.color.gate_accent)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14f)
        strokeCap = Paint.Cap.ROUND
        alpha = 38
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = ContextCompat.getColor(context, R.color.gate_track)
    }

    private val inset = dp(24f)

    /** 0 = bottom of the view, 1 = top. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Fade the line out towards both edges so it reads as a sweep, not a divider.
        val shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0x00FFFFFF and lineColor, lineColor, lineColor, 0x00FFFFFF and lineColor),
            floatArrayOf(0f, 0.18f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        linePaint.shader = shader
        glowPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val top = inset
        val bottom = height - inset

        // The rails the line travels between.
        canvas.drawLine(inset, top, width - inset, top, trackPaint)
        canvas.drawLine(inset, bottom, width - inset, bottom, trackPaint)

        val y = bottom - progress * (bottom - top)
        canvas.drawLine(0f, y, width.toFloat(), y, glowPaint)
        canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
