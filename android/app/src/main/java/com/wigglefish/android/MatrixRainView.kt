package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 255, 205)
        textSize = 18f
        alpha = 220
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val columns = 32
    private val drops = IntArray(columns) { Random.nextInt(0, 140) }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        post(object : Runnable {
            override fun run() {
                val rowCount = (height / 22f).toInt().coerceAtLeast(1) + 8
                for (index in drops.indices) drops[index] = (drops[index] + 1) % rowCount
                invalidate()
                postDelayed(this, 90L)
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width.toFloat() / columns
        val cellHeight = 22f
        val rows = (height / cellHeight).toInt() + 8
        for (column in 0 until columns) {
            val row = drops[column]
            for (tail in 0..5) {
                val y = ((row - tail + rows * 2) % rows) * cellHeight
                if (y >= 0f && y <= height) {
                    paint.alpha = 225 - tail * 34
                    canvas.drawText(if ((column + row + tail) % 3 == 0) "1" else "0", column * cellWidth, y, paint)
                }
            }
        }
    }
}