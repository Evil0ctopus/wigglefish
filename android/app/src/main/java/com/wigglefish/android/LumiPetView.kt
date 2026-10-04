package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class LumiPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private data class Spark(var x: Float, var y: Float, val vx: Float, var vy: Float, var life: Float)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val comicTypeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD_ITALIC)
    private val path = Path()
    private val comicArt = ComicMantaArt()
    private val sparks = mutableListOf<Spark>()
    private var palette = AppTheme.KOHOLINT_TOYBOX
    private var mood = WigglePetCompanion.Mood.HAPPY
    private var stage = WigglePetCompanion.EvolutionStage.BABY_GUPPY
    private var stageCaption = "LUMI / ${stage.title.uppercase()}"
    private var tick = 0f
    private var bounce = 0f
    private var ripple = 0f
    private val animationTicker = object : Runnable {
        override fun run() {
            if (!isShown || windowVisibility != VISIBLE || !ComicMotion.enabled(context)) return
            tick += 0.055f
            bounce *= 0.92f
            ripple = (ripple - 0.025f).coerceAtLeast(0f)
            val iterator = sparks.iterator()
            while (iterator.hasNext()) {
                val spark = iterator.next()
                spark.x += spark.vx
                spark.y += spark.vy
                spark.vy += 0.025f
                spark.life -= 0.035f
                if (spark.life <= 0f) iterator.remove()
            }
            invalidate()
            postDelayed(this, 33L)
        }
    }

    fun setPalette(value: AppTheme) {
        palette = value
        invalidate()
    }

    fun refreshMotion() {
        removeCallbacks(animationTicker)
        if (isShown && windowVisibility == VISIBLE && ComicMotion.enabled(context)) post(animationTicker)
        else { sparks.clear(); bounce = 0f; ripple = 0f }
        invalidate()
    }

    fun setMood(value: WigglePetCompanion.Mood) {
        if (mood == value) return
        mood = value
        if (value == WigglePetCompanion.Mood.EXCITED || value == WigglePetCompanion.Mood.ANGRY ||
            value == WigglePetCompanion.Mood.HUNTING) triggerPokeBounce()
        invalidate()
    }

    fun setEvolutionStage(value: WigglePetCompanion.EvolutionStage) {
        if (stage == value) return
        stage = value
        stageCaption = "LUMI / ${stage.title.uppercase()}"
        emitSparks(22)
        invalidate()
    }

    fun triggerPokeBounce() {
        if (ComicMotion.enabled(context)) {
            bounce = 1f
            ripple = 1f
            emitSparks(16)
        }
        invalidate()
    }

    private fun emitSparks(count: Int) {
        if (!isShown || !ComicMotion.enabled(context)) return
        repeat(count) {
            val angle = Random.nextFloat() * 6.283f
            val speed = 0.7f + Random.nextFloat() * 1.8f
            sparks.add(Spark(180f, 69f, cos(angle) * speed, sin(angle) * speed, 1f))
        }
        while (sparks.size > 48) sparks.removeAt(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMotion()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        refreshMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshMotion()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animationTicker)
        sparks.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 360f, height / 144f)
        canvas.save()
        canvas.translate((width - 360f * scale) / 2f, (height - 144f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = palette.tertiaryAccent
        canvas.drawRect(0f, 0f, 360f, 144f, paint)
        paint.color = Color.argb(35, 24, 24, 33)
        for (row in 0..11) for (column in 0..29) {
            canvas.drawCircle(column * 13f + (row % 2) * 6f, row * 13f, 1f, paint)
        }
        paint.color = ComicInk.black
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        for (index in 0..4) {
            val x = 10f + index * 80f
            canvas.drawLine(x, 13f, x + 17f, 22f, paint)
            canvas.drawLine(x + 4f, 25f, x + 20f, 30f, paint)
        }
        comicArt.burst(canvas, 304f, 36f, 25f, palette.secondaryAccent)
        paint.style = Paint.Style.FILL
        paint.color = ComicInk.black
        paint.textSize = 10f
        paint.typeface = comicTypeface
        canvas.drawText(if (mood == WigglePetCompanion.Mood.SLEEPY) "ZZZ" else "HEY!", 291f, 39f, paint)
        val swimX = 180f + sin(tick * 0.32f) * 36f
        val swimY = 62f + sin(tick * 0.57f) * 9f
        canvas.save()
        canvas.translate(swimX, swimY)
        canvas.rotate(sin(tick * 0.45f) * 4f)
        val growth = 0.85f + stage.stageIndex * 0.035f
        canvas.scale(growth * (1f + bounce * 0.08f), growth * (1f - bounce * 0.11f))
        comicArt.draw(canvas, palette.primaryAccent, palette.secondaryAccent,
            mood == WigglePetCompanion.Mood.EXCITED || bounce > 0.1f)
        if (stage.stageIndex >= 2) {
            paint.color = ComicInk.black
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawOval(-18f, -39f, 18f, -30f, paint)
        }
        if (stage.stageIndex >= 4) {
            path.reset()
            path.moveTo(-14f, -27f); path.lineTo(-17f, -42f); path.lineTo(-6f, -35f)
            path.lineTo(0f, -48f); path.lineTo(6f, -35f); path.lineTo(17f, -42f)
            path.lineTo(14f, -27f); path.close()
            paint.style = Paint.Style.FILL
            paint.color = palette.tertiaryAccent
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.color = ComicInk.black
            canvas.drawPath(path, paint)
        }
        if (mood == WigglePetCompanion.Mood.COOL) {
            paint.style = Paint.Style.FILL
            paint.color = ComicInk.black
            canvas.drawRect(-28f, -18f, -5f, -4f, paint)
            canvas.drawRect(5f, -18f, 28f, -4f, paint)
            canvas.drawRect(-5f, -14f, 5f, -10f, paint)
        }
        canvas.restore()
        if (ripple > 0f) {
            paint.style = Paint.Style.STROKE
            paint.color = ComicInk.black
            paint.alpha = (ripple * 160).toInt()
            paint.strokeWidth = 2f
            canvas.drawCircle(swimX, swimY, 32f + (1f - ripple) * 40f, paint)
            paint.alpha = 255
        }
        paint.style = Paint.Style.FILL
        for ((index, spark) in sparks.withIndex()) {
            paint.color = if (index % 2 == 0) palette.secondaryAccent else palette.primaryAccent
            paint.alpha = (spark.life * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(spark.x - 2f, spark.y - 2f, spark.x + 2f, spark.y + 2f, paint)
        }
        paint.alpha = 255
        paint.color = ComicInk.black
        paint.textSize = 10f
        canvas.drawText(stageCaption, 14f, 132f, paint)
        canvas.restore()
    }
}
