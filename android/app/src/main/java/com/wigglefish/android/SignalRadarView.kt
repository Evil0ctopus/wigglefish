package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SignalRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 104, 106)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 79, 216)
        strokeWidth = 3f
        setShadowLayer(18f, 0f, 0f, Color.rgb(143, 247, 255))
    }
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(114, 230, 197)
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 0f, Color.rgb(255, 79, 216))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 247, 255)
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(240, 179, 90)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val glitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var signalCount = 0
    private var sweep = 0f
    private var pulse = 0f
    private var glitch = 0

    init {
        setBackgroundColor(Color.rgb(7, 27, 32))
        post(object : Runnable {
            override fun run() {
                sweep = (sweep + 2f) % 360f
                pulse = (pulse + 0.035f) % 1f
                glitch = (glitch + 1) % 24
                invalidate()
                postDelayed(this, 40L)
            }
        })
    }

    fun setSignalCount(count: Int) {
        signalCount = count
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        glitchPaint.color = Color.rgb(7, 10, 29)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glitchPaint)
        for (line in 0 until height step 9) {
            glitchPaint.color = if ((line / 9 + glitch) % 7 == 0) Color.argb(55, 255, 79, 216) else Color.argb(28, 143, 247, 255)
            canvas.drawRect(0f, line.toFloat(), width.toFloat(), line + 1f, glitchPaint)
        }
        for (band in 0..2) {
            val y = ((glitch * 13 + band * 71) % (height + 30)) - 15
            glitchPaint.color = Color.argb(35, if (band % 2 == 0) 255 else 143, if (band % 2 == 0) 79 else 247, if (band % 2 == 0) 216 else 255)
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + 4).toFloat(), glitchPaint)
        }
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.39f
        gridPaint.color = Color.rgb(26, 117, 143)
        for (scale in 1..4) canvas.drawCircle(centerX, centerY, radius * scale / 4f, gridPaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint)
        canvas.drawLine(centerX - radius * 0.7f, centerY - radius * 0.7f, centerX + radius * 0.7f, centerY + radius * 0.7f, gridPaint)
        canvas.drawLine(centerX + radius * 0.7f, centerY - radius * 0.7f, centerX - radius * 0.7f, centerY + radius * 0.7f, gridPaint)
        val angle = Math.toRadians(sweep.toDouble())
        canvas.drawLine(centerX, centerY, centerX + radius * cos(angle).toFloat(), centerY + radius * sin(angle).toFloat(), sweepPaint)
        pulsePaint.alpha = (180 - pulse * 150).toInt()
        canvas.drawCircle(centerX, centerY, radius * (0.2f + pulse * 0.8f), pulsePaint)
        val visibleBlips = signalCount.coerceAtMost(18)
        for (index in 0 until visibleBlips) {
            val blipAngle = Math.toRadians((index * 47 + 18).toDouble())
            val distance = radius * (0.25f + (index % 5) * 0.14f)
            val x = centerX + distance * cos(blipAngle).toFloat()
            val y = centerY + distance * sin(blipAngle).toFloat()
            canvas.drawCircle(x, y, 5f, blipPaint)
            if (index < 3) {
                canvas.drawLine(x - 11f, y - 11f, x - 3f, y - 11f, pulsePaint)
                canvas.drawLine(x - 11f, y - 11f, x - 11f, y - 3f, pulsePaint)
                canvas.drawText("SIG-%02d".format(index + 1), x + 9f, y + 4f, textPaint)
            }
        }
        canvas.drawText("PASSIVE // RADAR", 14f, 20f, textPaint)
        canvas.drawText("LIVE %02d".format(signalCount.coerceAtMost(99)), width - 78f, 20f, textPaint)
        canvas.drawText("360° FIELD", 14f, height - 12f, textPaint)
        canvas.drawText("RX ONLINE", width - 82f, height - 12f, textPaint)
    }
}