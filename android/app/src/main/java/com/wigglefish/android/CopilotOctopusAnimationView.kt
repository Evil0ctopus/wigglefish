package com.wigglefish.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CopilotOctopusAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    private var mood = WigglePetCompanion.Mood.HAPPY
    private var stage = WigglePetCompanion.EvolutionStage.BABY_GUPPY
    private var tick = 0f
    private var squirm = 0f
    private val sparks = mutableListOf<Spark>()
    private var attachedAt = 0L
    private val frameBitmaps: List<Bitmap> by lazy {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        listOf(
            R.drawable.copilot_pet_frame_01,
            R.drawable.copilot_pet_frame_02,
            R.drawable.copilot_pet_frame_03,
            R.drawable.copilot_pet_frame_04,
        ).map { BitmapFactory.decodeResource(resources, it, options) }
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val animationTicker = object : Runnable {
        override fun run() {
            tick += 0.055f
            squirm = (squirm * 0.94f).let { if (it < 0.01f) 0f else it }
            val iterator = sparks.iterator()
            while (iterator.hasNext()) {
                val spark = iterator.next()
                spark.x += spark.vx
                spark.y += spark.vy
                spark.vy -= 0.025f
                spark.life -= 0.035f
                if (spark.life <= 0f) iterator.remove()
            }
            invalidate()
            postDelayed(this, 32L)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setMood(newMood: WigglePetCompanion.Mood) {
        if (mood != newMood && (newMood == WigglePetCompanion.Mood.EXCITED || newMood == WigglePetCompanion.Mood.ANGRY || newMood == WigglePetCompanion.Mood.HUNTING)) {
            squirm = 1f
            emitSparks(18)
        }
        mood = newMood
        invalidate()
    }

    fun setEvolutionStage(newStage: WigglePetCompanion.EvolutionStage) {
        if (stage != newStage) emitSparks(24)
        stage = newStage
        invalidate()
    }

    fun triggerPokeBounce() {
        squirm = 1f
        emitSparks(16)
        invalidate()
    }

    private fun emitSparks(count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * 6.283f
            val speed = 0.8f + Random.nextFloat() * 2.3f
            sparks.add(Spark(width * 0.5f, height * 0.56f, cos(angle) * speed, sin(angle) * speed, 0.6f + Random.nextFloat() * 0.4f))
        }
        while (sparks.size > 48) sparks.removeAt(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedAt = SystemClock.uptimeMillis()
        removeCallbacks(animationTicker)
        post(animationTicker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animationTicker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return

        paint.shader = LinearGradient(0f, 0f, widthPx, heightPx, Color.rgb(7, 9, 14), Color.rgb(16, 18, 27), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, widthPx, heightPx, paint)
        paint.shader = null
        drawCircuitHalo(canvas, widthPx, heightPx)

        val bitmaps = frameBitmaps
        if (bitmaps.isEmpty()) return
        val aspect = bitmaps[0].width.toFloat() / bitmaps[0].height.toFloat()
        val drawHeight = minOf(heightPx * 0.98f, widthPx * 1.42f / aspect)
        val drawWidth = drawHeight * aspect
        val centerX = widthPx * 0.5f
        val centerY = heightPx * 0.51f
        val baseFrameDuration = when (mood) {
            WigglePetCompanion.Mood.SLEEPY -> 1550L
            WigglePetCompanion.Mood.HUNTING, WigglePetCompanion.Mood.EXCITED, WigglePetCompanion.Mood.ANGRY -> 540L
            WigglePetCompanion.Mood.SASSY -> 760L
            WigglePetCompanion.Mood.NOM_NOM -> 650L
            else -> 1120L
        }
        val frameDuration = (baseFrameDuration * (1f - squirm * 0.34f)).toLong().coerceAtLeast(360L)
        val frameOrder = when (mood) {
            WigglePetCompanion.Mood.SLEEPY -> intArrayOf(0, 2)
            WigglePetCompanion.Mood.NOM_NOM -> intArrayOf(2, 3, 1, 0)
            WigglePetCompanion.Mood.HUNTING, WigglePetCompanion.Mood.ANGRY -> intArrayOf(2, 0, 3, 1)
            WigglePetCompanion.Mood.EXCITED -> intArrayOf(3, 1, 2, 0)
            WigglePetCompanion.Mood.SASSY -> intArrayOf(1, 3, 0, 2)
            else -> intArrayOf(0, 1, 2, 3)
        }
        val elapsed = (SystemClock.uptimeMillis() - attachedAt).coerceAtLeast(0L)
        val framePosition = elapsed / frameDuration
        val orderIndex = (framePosition % frameOrder.size).toInt()
        val currentIndex = frameOrder[orderIndex]
        val nextIndex = frameOrder[(orderIndex + 1) % frameOrder.size]
        val progress = ((elapsed % frameDuration).toFloat() / frameDuration)
        val fadeStart = 0.72f
        val fade = if (progress > fadeStart) ((progress - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f) else 0f
        val frameRect = RectF(centerX - drawWidth * 0.5f, centerY - drawHeight * 0.5f, centerX + drawWidth * 0.5f, centerY + drawHeight * 0.5f)
        drawImageFrame(canvas, bitmaps[currentIndex], frameRect, 255)
        if (fade > 0f) drawImageFrame(canvas, bitmaps[nextIndex], frameRect, (fade * 255f).toInt())

        if (mood == WigglePetCompanion.Mood.ANGRY || mood == WigglePetCompanion.Mood.HUNTING) {
            linePaint.color = Color.argb((100 + 100 * (0.5f + 0.5f * sin(tick * 4f))).toInt(), 53, 198, 255)
            linePaint.strokeWidth = maxOf(1f, height * 0.009f)
            canvas.drawRoundRect(frameRect, height * 0.04f, height * 0.04f, linePaint)
        }
        drawSparks(canvas)
    }

    private fun drawImageFrame(canvas: Canvas, bitmap: Bitmap, bounds: RectF, alpha: Int) {
        imagePaint.alpha = alpha
        canvas.drawBitmap(bitmap, null, bounds, imagePaint)
        imagePaint.alpha = 255
    }

    private fun drawCircuitHalo(canvas: Canvas, widthPx: Float, heightPx: Float) {
        val radius = minOf(heightPx * 0.43f, widthPx * 0.25f)
        val cx = widthPx * 0.5f
        val cy = heightPx * 0.51f
        val hex = Path()
        for (index in 0..5) {
            val angle = Math.toRadians((index * 60 - 90).toDouble())
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (index == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        linePaint.color = Color.argb(125, 58, 124, 137)
        linePaint.strokeWidth = radius * 0.025f
        canvas.drawPath(hex, linePaint)
        linePaint.color = Color.argb(170, 64, 218, 222)
        linePaint.strokeWidth = radius * 0.009f
        canvas.drawPath(hex, linePaint)
    }

    private fun drawSparks(canvas: Canvas) {
        for (spark in sparks) {
            paint.color = if (spark.life > 0.45f) Color.rgb(66, 214, 255) else Color.rgb(202, 129, 226)
            paint.alpha = (spark.life * 230f).toInt().coerceIn(0, 230)
            canvas.drawCircle(spark.x, spark.y, height * 0.009f * spark.life, paint)
        }
    }
}
