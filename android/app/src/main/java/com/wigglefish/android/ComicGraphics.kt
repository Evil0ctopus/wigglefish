package com.wigglefish.android

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import kotlin.math.cos
import kotlin.math.sin

object ComicInk {
    val black = Color.rgb(24, 24, 33)
    val paper = Color.rgb(255, 250, 237)
    val muted = Color.rgb(76, 76, 89)

    fun button(fill: Int, density: Float): Drawable = RippleDrawable(
        ColorStateList.valueOf(Color.argb(42, 24, 24, 33)),
        ComicPanelDrawable(fill, density, decorated = false),
        null,
    )

    fun style(button: Button, fill: Int) {
        val current = (button.background as? RippleDrawable)?.getDrawable(0) as? ComicPanelDrawable
        if (current?.fill != fill) button.background = button(fill, button.resources.displayMetrics.density)
        button.setTextColor(black)
    }
}

/** Static, original paintwork: ink keyline, offset shadow, halftone and cut highlights. */
class ComicPanelDrawable(
    val fill: Int,
    private val density: Float,
    private val decorated: Boolean = true,
    private val shadow: Boolean = true,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Path()
    private val shade = Path()
    private val shine = Path()
    private val edge = 2f * density
    private var right = 0f
    private var bottom = 0f
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left + edge
        val top = bounds.top + edge
        right = bounds.right - edge - if (shadow) 4f * density else 0f
        bottom = bounds.bottom - edge - if (shadow) 5f * density else 0f
        val cut = 9f * density
        face.reset()
        face.moveTo(left + cut, top)
        face.lineTo(right - cut, top)
        face.lineTo(right, top + cut)
        face.lineTo(right, bottom - cut)
        face.lineTo(right - cut, bottom)
        face.lineTo(left + cut, bottom)
        face.lineTo(left, bottom - cut)
        face.lineTo(left, top + cut)
        face.close()
        shade.reset()
        shade.moveTo(right - 48f * density, bottom)
        shade.lineTo(right, bottom - 40f * density)
        shade.lineTo(right, bottom)
        shade.close()
        shine.reset()
        shine.moveTo(left + 12f * density, top + 6f * density)
        shine.lineTo(left + 53f * density, top + 6f * density)
        shine.lineTo(left + 45f * density, top + 10f * density)
        shine.lineTo(left + 8f * density, top + 10f * density)
        shine.close()
    }

    override fun draw(canvas: Canvas) {
        if (opacity == 0) return
        val layer = if (opacity < 255) canvas.saveLayerAlpha(null, opacity) else -1
        paint.style = Paint.Style.FILL
        if (shadow) {
            canvas.save()
            canvas.translate(4f * density, 5f * density)
            paint.color = ComicInk.black
            canvas.drawPath(face, paint)
            canvas.restore()
        }
        paint.color = fill
        canvas.drawPath(face, paint)
        canvas.save()
        canvas.clipPath(face)
        paint.color = Color.argb(25, 24, 24, 33)
        canvas.drawPath(shade, paint)
        if (decorated) {
            paint.color = Color.argb(30, 24, 24, 33)
            for (row in 0..5) {
                for (column in 0..7) {
                    canvas.drawCircle(
                        right - (column * 7f + 5f + (row % 2) * 3f) * density,
                        bottom - (row * 7f + 5f) * density,
                        0.85f * density,
                        paint,
                    )
                }
            }
        }
        paint.color = Color.argb(190, 255, 255, 255)
        canvas.drawPath(shine, paint)
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.color = ComicInk.black
        paint.strokeWidth = edge
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(face, paint)
        if (layer >= 0) canvas.restoreToCount(layer)
    }

    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class ComicGlyphDrawable(private val kind: Int, density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ComicInk.black
        style = Paint.Style.STROKE
        strokeWidth = 2.3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val size = (22 * density).toInt()
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        when (kind) {
            0 -> {
                canvas.drawArc(3f, 4f, 21f, 22f, 220f, 100f, false, paint)
                canvas.drawArc(7f, 9f, 17f, 19f, 220f, 100f, false, paint)
                canvas.drawCircle(12f, 18f, 1.2f, paint)
            }
            1 -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(12f, 12f, 4f, paint)
                canvas.drawLine(12f, 12f, 18f, 5f, paint)
            }
            2 -> {
                path.reset()
                path.moveTo(13f, 2f); path.lineTo(5f, 13f); path.lineTo(11f, 13f)
                path.lineTo(10f, 22f); path.lineTo(20f, 9f); path.lineTo(13f, 9f); path.close()
                canvas.drawPath(path, paint)
            }
            3 -> {
                canvas.drawLine(4f, 20f, 20f, 20f, paint)
                canvas.drawLine(6f, 17f, 6f, 12f, paint)
                canvas.drawLine(12f, 17f, 12f, 5f, paint)
                canvas.drawLine(18f, 17f, 18f, 9f, paint)
            }
            else -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 2f, 2f, paint)
                canvas.drawRect(7f, 3f, 17f, 9f, paint)
                canvas.drawRect(7f, 14f, 17f, 21f, paint)
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Shared original mascot silhouette, drawn as painted flat planes rather than gradients. */
class ComicMantaArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val burstPath = Path()
    private val body = Path().apply {
        moveTo(0f, -22f)
        cubicTo(-18f, -31f, -34f, -9f, -64f, -27f)
        cubicTo(-54f, -1f, -38f, 29f, -17f, 21f)
        quadTo(0f, 38f, 17f, 21f)
        cubicTo(38f, 29f, 54f, -1f, 64f, -27f)
        cubicTo(34f, -9f, 18f, -31f, 0f, -22f)
        close()
    }
    private val shadow = Path().apply {
        moveTo(-64f, -27f); quadTo(-30f, 18f, 0f, 17f)
        quadTo(36f, 20f, 64f, -27f)
        cubicTo(54f, -1f, 38f, 29f, 17f, 21f)
        quadTo(0f, 38f, -17f, 21f)
        cubicTo(-38f, 29f, -54f, -1f, -64f, -27f); close()
    }
    private val tail = Path().apply {
        moveTo(0f, 24f); cubicTo(0f, 56f, 38f, 34f, 25f, 70f)
    }

    fun draw(canvas: Canvas, primary: Int, secondary: Int, excited: Boolean = false) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 5f
        paint.color = ComicInk.black
        canvas.drawPath(tail, paint)
        paint.strokeWidth = 2f
        paint.color = secondary
        canvas.drawPath(tail, paint)
        paint.style = Paint.Style.FILL
        paint.color = primary
        canvas.drawPath(body, paint)
        paint.color = secondary
        canvas.drawPath(shadow, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = ComicInk.black
        canvas.drawPath(body, paint)
        canvas.drawLine(-52f, -15f, -31f, 5f, paint)
        canvas.drawLine(52f, -15f, 31f, 5f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawOval(-26f, -17f, -7f, 3f, paint)
        canvas.drawOval(7f, -17f, 26f, 3f, paint)
        paint.color = ComicInk.black
        canvas.drawOval(-18f, -13f, -10f, 1f, paint)
        canvas.drawOval(10f, -13f, 18f, 1f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawArc(-7f, -1f, 7f, if (excited) 13f else 7f, 0f, 180f, false, paint)
        paint.color = Color.WHITE
        paint.strokeWidth = 3f
        canvas.drawLine(-6f, -21f, 6f, -21f, paint)
        canvas.drawLine(-47f, -14f, -37f, -8f, paint)
        canvas.drawLine(40f, -8f, 47f, -14f, paint)
    }

    fun burst(canvas: Canvas, x: Float, y: Float, radius: Float, fill: Int) {
        val path = burstPath
        path.reset()
        repeat(24) { index ->
            val angle = index * Math.PI / 12
            val r = radius * if (index % 2 == 0) 1f else 0.7f
            val px = x + cos(angle).toFloat() * r
            val py = y + sin(angle).toFloat() * r
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.color = ComicInk.black
        paint.strokeWidth = 1.8f
        canvas.drawPath(path, paint)
    }
}
