package com.wigglefish.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.sin

class ComicHeroView(context: Context) : View(context) {
    private val art = ComicMantaArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val comicTypeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD_ITALIC)
    private val chapterCaptions = listOf("LET'S GO!", "PING!", "ZAP!", "AHA!", "PACK IT!")
    var companionShortcut = false
    private var theme = AppTheme.KOHOLINT_TOYBOX
    private var progress = 1f
    private var animator: ValueAnimator? = null
    private var chapter = 0

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPalette(value: AppTheme) {
        theme = value
        invalidate()
    }

    fun celebrate(index: Int) {
        chapter = index
        animator?.cancel()
        progress = 1f
        if (!ComicMotion.enabled(context) || !isShown) { invalidate(); return }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) {
            animator?.cancel()
            progress = 1f
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 160f, height / 132f)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        paint.color = ComicInk.black
        paint.alpha = 35
        for (row in 0..10) for (column in 0..10) {
            canvas.drawCircle(-70f + column * 14f + (row % 2) * 7f, -66f + row * 13f, 1.4f, paint)
        }
        paint.alpha = 255
        art.burst(canvas, 0f, -8f, 63f + sin(progress * Math.PI).toFloat() * 6f, theme.tertiaryAccent)
        canvas.save()
        canvas.translate(0f, -15f - sin(progress * Math.PI).toFloat() * 12f)
        canvas.rotate(-8f + sin(progress * Math.PI).toFloat() * 12f)
        canvas.scale(0.8f, 0.8f)
        art.draw(canvas, theme.primaryAccent, theme.secondaryAccent, progress < 1f)
        canvas.restore()
        paint.color = ComicInk.black
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawLine(-73f, -31f, -57f, -26f, paint)
        canvas.drawLine(58f, 27f, 73f, 37f, paint)
        canvas.drawLine(54f, 37f, 65f, 47f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = if (companionShortcut) 15f else 10f
        paint.typeface = comicTypeface
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (companionShortcut) "LUMI" else chapterCaptions[chapter], 0f, 58f, paint)
        canvas.restore()
    }
}
