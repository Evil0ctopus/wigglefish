package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** A stylized signal mix, not a geographic position or bearing estimate. */
class SignalRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val comicTypeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
    private val sweepOval = RectF(-88f, -88f, 88f, 88f)
    private var theme = AppTheme.KOHOLINT_TOYBOX
    private var signalCount = 0
    private var wifiCount = 0
    private var bleCount = 0
    private var riskCount = 0
    private var targetLabel = "NO TARGET"
    private var targetRssi: Int? = null
    private var channelHeat: Map<Int, Int> = emptyMap()
    private var sweepAngle = 0f
    private var pulseRadius = 0f
    private val animationTicker = object : Runnable {
        override fun run() {
            if (!isShown || windowVisibility != VISIBLE || !ComicMotion.enabled(context)) return
            sweepAngle = (sweepAngle + 3f) % 360f
            pulseRadius = (pulseRadius + 0.018f) % 1f
            invalidate()
            postDelayed(this, 33L)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setPalette(value: AppTheme) {
        theme = value
        invalidate()
    }

    fun refreshMotion() {
        removeCallbacks(animationTicker)
        if (isShown && windowVisibility == VISIBLE && ComicMotion.enabled(context)) post(animationTicker)
        invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        refreshMotion()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshMotion()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animationTicker)
        super.onDetachedFromWindow()
    }

    fun setSignalCount(count: Int) {
        signalCount = count
        invalidate()
    }

    fun setTelemetry(
        wifiCount: Int,
        bleCount: Int,
        riskCount: Int,
        targetLabel: String?,
        targetRssi: Int?,
        channelHeat: Map<Int, Int>,
    ) {
        this.wifiCount = wifiCount
        this.bleCount = bleCount
        this.riskCount = riskCount
        signalCount = wifiCount + bleCount
        this.targetLabel = targetLabel?.takeIf { it.isNotBlank() } ?: "NO TARGET"
        this.targetRssi = targetRssi
        this.channelHeat = channelHeat.toMap()
        contentDescription = "Signal mix: $wifiCount Wi-Fi, $bleCount Bluetooth, $riskCount flagged. " +
            "Target ${this.targetLabel}. ${targetRssi?.let { "$it dBm" }.orEmpty()} Not a location map."
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 320f, height / 240f)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = ComicInk.black
        canvas.drawCircle(5f, 5f, 94f, paint)
        paint.color = theme.primaryAccent
        canvas.drawCircle(0f, 0f, 94f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = ComicInk.black
        canvas.drawCircle(0f, 0f, 94f, paint)
        paint.strokeWidth = 1f
        paint.alpha = 80
        for (ring in 1..3) canvas.drawCircle(0f, 0f, ring * 29f, paint)
        canvas.drawLine(-88f, 0f, 88f, 0f, paint)
        canvas.drawLine(0f, -88f, 0f, 88f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 30
        for (row in -5..5) for (column in -5..5) {
            val x = column * 14f
            val y = row * 14f
            if (x * x + y * y < 7300f) canvas.drawCircle(x, y, 1.2f, paint)
        }
        paint.alpha = 255
        paint.color = Color.argb(110, 255, 255, 255)
        canvas.drawArc(sweepOval, sweepAngle - 35f, 35f, true, paint)
        val angle = Math.toRadians(sweepAngle.toDouble())
        paint.color = ComicInk.black
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawLine(0f, 0f, cos(angle).toFloat() * 88f, sin(angle).toFloat() * 88f, paint)
        if (targetRssi != null) {
            paint.color = ComicInk.black
            paint.alpha = ((1f - pulseRadius) * 150).toInt()
            canvas.drawCircle(0f, 0f, 12f + pulseRadius * 70f, paint)
            paint.alpha = 255
        }
        for (index in 0 until signalCount.coerceAtMost(16)) {
            val theta = Math.toRadians((index * 53 + 22).toDouble())
            val distance = 24f + (index % 4) * 17f
            val x = cos(theta).toFloat() * distance
            val y = sin(theta).toFloat() * distance
            paint.style = Paint.Style.FILL
            paint.color = when {
                index < riskCount -> theme.secondaryAccent
                index >= wifiCount -> theme.tertiaryAccent
                else -> ComicInk.paper
            }
            if (index >= wifiCount) canvas.drawRect(x - 4f, y - 4f, x + 4f, y + 4f, paint)
            else canvas.drawCircle(x, y, 4.5f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = ComicInk.black
            paint.strokeWidth = 1.5f
            if (index >= wifiCount) canvas.drawRect(x - 4f, y - 4f, x + 4f, y + 4f, paint)
            else canvas.drawCircle(x, y, 4.5f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 4f
        canvas.drawArc(-84f, -84f, 84f, 84f, 212f, 45f, false, paint)
        paint.color = ComicInk.black
        paint.strokeWidth = 2f
        canvas.drawLine(-127f, -49f, -109f, -43f, paint)
        canvas.drawLine(-139f, -31f, -112f, -28f, paint)
        canvas.drawLine(110f, 32f, 136f, 37f, paint)
        canvas.drawLine(108f, 45f, 123f, 51f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = comicTypeface
        paint.textSize = 11f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(if (ComicMotion.enabled(context)) "PING!" else "PAUSED", -143f, -65f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${channelHeat.size} CHANNELS", 146f, -96f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        canvas.drawText(ComicMotion.disabledReason(context) ?:
            targetRssi?.let { "LOCKED / $it dBm" } ?: "SIGNAL MIX / NOT A MAP", 0f, 114f, paint)
        canvas.restore()
    }
}
