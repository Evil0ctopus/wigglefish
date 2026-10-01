package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D Toy Diorama Signal Radar.
 * Styled after the charming, glossy aesthetic of The Legend of Zelda: Link's Awakening (Switch).
 */
class SignalRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 255, 255, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var signalCount = 0
    private var wifiCount = 0
    private var bleCount = 0
    private var riskCount = 0
    private var targetLabel = "NO LOCK"
    private var targetRssi: Int? = null
    private var channelHeat: Map<Int, Int> = emptyMap()
    private var sweepAngle = 0f
    private var pulseRadius = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        post(object : Runnable {
            override fun run() {
                sweepAngle = (sweepAngle + 3.0f) % 360f
                pulseRadius = (pulseRadius + 0.025f) % 1.0f
                invalidate()
                postDelayed(this, 30L)
            }
        })
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
        this.signalCount = wifiCount + bleCount
        this.targetLabel = targetLabel?.takeIf { it.isNotBlank() } ?: "NO LOCK"
        this.targetRssi = targetRssi
        this.channelHeat = channelHeat.toMap()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.42f

        // 1. 3D Bevel Shadow behind radar dish
        bgPaint.color = Color.parseColor("#0B0E1A")
        canvas.drawCircle(cx, cy + 6f, radius, bgPaint)

        // 2. Main Radar Dish (Deep Sea / Diorama gradient)
        val dishGradient = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.3f,
            Color.parseColor("#17243B"), Color.parseColor("#0F1726"), Shader.TileMode.CLAMP
        )
        bgPaint.shader = dishGradient
        canvas.drawCircle(cx, cy, radius, bgPaint)
        bgPaint.shader = null

        // 3. Glowing Sweep Beam (Smooth 3D Fan)
        val sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(0, 0, 229, 255),
                Color.argb(0, 0, 229, 255),
                Color.argb(120, 0, 229, 255),
                Color.argb(0, 0, 229, 255)
            ),
            floatArrayOf(0f, (sweepAngle - 60f + 360f) % 360f / 360f, sweepAngle / 360f, 1f)
        )
        sweepPaint.shader = sweepShader
        canvas.drawCircle(cx, cy, radius, sweepPaint)
        sweepPaint.shader = null

        // 4. Concentric Toy Radar Rings
        ringPaint.color = Color.argb(70, 0, 229, 255)
        for (i in 1..3) {
            canvas.drawCircle(cx, cy, radius * (i / 3f), ringPaint)
        }

        // Crosshairs
        canvas.drawLine(cx - radius, cy, cx + radius, cy, ringPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, ringPaint)

        drawChannelHeat(canvas, cx, cy, radius)

        // Expanding Energy Pulse Ring
        val targetStrength = targetRssi?.let { ((it + 100).coerceIn(0, 70) / 70f) } ?: 0f
        ringPaint.color = Color.argb(((1f - pulseRadius) * (110 + targetStrength * 120)).toInt(), 255, 214, 0)
        ringPaint.strokeWidth = 3.5f
        canvas.drawCircle(cx, cy, radius * (0.2f + pulseRadius * 0.78f), ringPaint)
        ringPaint.strokeWidth = 3f

        if (targetRssi != null) {
            drawTargetLock(canvas, cx, cy, radius, targetStrength)
        }

        // 5. 3D Candy Blip Signals (Rupee / Gem Pins)
        val blips = signalCount.coerceAtMost(16)
        for (i in 0 until blips) {
            val angle = Math.toRadians((i * 53 + 22).toDouble())
            val dist = radius * (0.28f + (i % 4) * 0.18f)
            val bx = cx + dist * cos(angle).toFloat()
            val by = cy + dist * sin(angle).toFloat()

            val gemColor = when {
                i < riskCount -> Color.parseColor("#FF3366")
                i < riskCount + wifiCount -> Color.parseColor("#00F5D4")
                else -> Color.parseColor("#FFE600")
            }

            // Blip drop shadow
            gemPaint.color = Color.argb(120, 0, 0, 0)
            canvas.drawCircle(bx, by + 2.5f, 6.5f, gemPaint)

            // Blip body
            gemPaint.color = gemColor
            if (i >= riskCount + wifiCount) {
                canvas.drawRoundRect(RectF(bx - 5.5f, by - 5.5f, bx + 5.5f, by + 5.5f), 3f, 3f, gemPaint)
            } else {
                canvas.drawCircle(bx, by, 6.5f, gemPaint)
            }

            // Gloss shine spot
            canvas.drawCircle(bx - 2f, by - 2f, 2.2f, highlightPaint)
        }

        // 6. Chunky Toy Outer Rim (Glossy Enamel Bezel)
        rimPaint.color = Color.parseColor("#00E5FF")
        canvas.drawCircle(cx, cy, radius, rimPaint)

        // Top Gloss Sheen Reflection Arc
        highlightPaint.alpha = 180
        val oval = RectF(cx - radius + 4f, cy - radius + 4f, cx + radius - 4f, cy - radius * 0.1f)
        canvas.drawArc(oval, 200f, 140f, false, ringPaint.apply {
            color = Color.argb(160, 255, 255, 255)
            strokeWidth = 4f
        })

        // 7. Playful Badges in Corners
        textPaint.color = Color.parseColor("#00E5FF")
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("360 AIR RADAR", 16f, 22f, textPaint)

        textPaint.color = Color.parseColor("#FFE600")
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("WIFI $wifiCount  BLE $bleCount  RISK $riskCount", width - 16f, 22f, textPaint)

        drawBottomHud(canvas, cx, cy, radius)
    }

    private fun drawChannelHeat(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        if (channelHeat.isEmpty()) return
        val maxCount = channelHeat.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val sortedChannels = channelHeat.entries.sortedBy { it.key }.take(18)
        sortedChannels.forEachIndexed { index, entry ->
            val angle = Math.toRadians((index * (360.0 / sortedChannels.size)) - 90.0)
            val strength = entry.value / maxCount.toFloat()
            val inner = radius * 0.72f
            val outer = radius * (0.78f + strength * 0.18f)
            val sx = cx + inner * cos(angle).toFloat()
            val sy = cy + inner * sin(angle).toFloat()
            val ex = cx + outer * cos(angle).toFloat()
            val ey = cy + outer * sin(angle).toFloat()
            ringPaint.color = if (entry.key >= 36) Color.argb(150, 255, 214, 0) else Color.argb(150, 0, 245, 212)
            ringPaint.strokeWidth = 4f + strength * 5f
            canvas.drawLine(sx, sy, ex, ey, ringPaint)
        }
        ringPaint.strokeWidth = 3f
    }

    private fun drawTargetLock(canvas: Canvas, cx: Float, cy: Float, radius: Float, strength: Float) {
        val angle = Math.toRadians((sweepAngle * 1.7f - 90f).toDouble())
        val lockRadius = radius * (0.68f - strength * 0.34f)
        val x = cx + lockRadius * cos(angle).toFloat()
        val y = cy + lockRadius * sin(angle).toFloat()
        val box = 18f + strength * 12f
        rimPaint.color = Color.parseColor("#FFE600")
        rimPaint.strokeWidth = 4f
        canvas.drawLine(x - box, y - box, x - box * 0.35f, y - box, rimPaint)
        canvas.drawLine(x - box, y - box, x - box, y - box * 0.35f, rimPaint)
        canvas.drawLine(x + box, y - box, x + box * 0.35f, y - box, rimPaint)
        canvas.drawLine(x + box, y - box, x + box, y - box * 0.35f, rimPaint)
        canvas.drawLine(x - box, y + box, x - box * 0.35f, y + box, rimPaint)
        canvas.drawLine(x - box, y + box, x - box, y + box * 0.35f, rimPaint)
        canvas.drawLine(x + box, y + box, x + box * 0.35f, y + box, rimPaint)
        canvas.drawLine(x + box, y + box, x + box, y + box * 0.35f, rimPaint)
        gemPaint.color = Color.argb(190, 255, 214, 0)
        canvas.drawCircle(x, y, 5f + strength * 7f, gemPaint)
        rimPaint.strokeWidth = 6f
    }

    private fun drawBottomHud(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val panel = RectF(cx - radius * 0.92f, cy + radius * 0.63f, cx + radius * 0.92f, cy + radius * 0.91f)
        barPaint.color = Color.argb(180, 4, 12, 24)
        canvas.drawRoundRect(panel, 12f, 12f, barPaint)
        rimPaint.color = Color.argb(170, 0, 229, 255)
        rimPaint.strokeWidth = 2.5f
        canvas.drawRoundRect(panel, 12f, 12f, rimPaint)
        val rssi = targetRssi
        val hotCold = when {
            rssi == null -> "TARGET: $targetLabel"
            rssi >= -45 -> "TARGET HOT  $rssi dBm"
            rssi >= -65 -> "TARGET NEAR $rssi dBm"
            else -> "TARGET FAR  $rssi dBm"
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (rssi == null) Color.parseColor("#B3E5FC") else Color.parseColor("#FFE600")
        canvas.drawText(hotCold.take(32), cx, panel.centerY() + 4f, textPaint)
    }
}