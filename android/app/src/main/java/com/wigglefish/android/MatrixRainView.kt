package com.wigglefish.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Low-contrast circuit glyphs with teal, amethyst, and copper signal highlights.
 */
class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Stream(
        var column: Int = 0,
        var headY: Float = 0f,
        var speed: Float = 5f,
        var length: Int = 14,
        var chars: CharArray = CharArray(24),
        var stepCounter: Int = 0,
        var colorTheme: Int = 0 // 0 = Teal, 1 = Copper, 2 = Amethyst
    )

    // Geometric circuit marks, hex symbols, and matrix digits.
    private val glyphPool = charArrayOf(
        '✦', '◆', '▲', '★', '✧', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'X', 'Z', 'W', 'K', 'V', 'T', 'Y', 'O', 'S',
        'Ω', 'Ψ', 'Δ', '§', '¶', '※', 'Φ', 'Λ', 'Ξ', 'Π', 'Σ', 'Θ'
    )

    private val tealColors = intArrayOf(
        Color.parseColor("#FFFFFF"), Color.parseColor("#D5FFF7"), Color.parseColor("#40E8D0"),
        Color.parseColor("#25BFAF"), Color.parseColor("#188D83"), Color.parseColor("#12665F"),
        Color.parseColor("#0B3B3B"), Color.parseColor("#071F25")
    )
    private val copperColors = intArrayOf(
        Color.parseColor("#FFFFFF"), Color.parseColor("#F4E1D1"), Color.parseColor("#D39A72"),
        Color.parseColor("#BB805D"), Color.parseColor("#9D654F"), Color.parseColor("#754A43"),
        Color.parseColor("#503336"), Color.parseColor("#2A202B")
    )
    private val amethystColors = intArrayOf(
        Color.parseColor("#FFFFFF"), Color.parseColor("#F0D9F7"), Color.parseColor("#C77BDB"),
        Color.parseColor("#A85FC4"), Color.parseColor("#85469F"), Color.parseColor("#663777"),
        Color.parseColor("#44264F"), Color.parseColor("#25152F")
    )

    // Paints
    private val textShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 4, 7, 14) // Clay shadow underneath
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textClayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255) // Top gloss shine
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val headGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var numCols = 24
    private var colWidth = 40f
    private val rowHeight = 28f
    private var streams: Array<Stream>? = null
    private var animRunning = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun initStreams(w: Int, h: Int) {
        val random = Random(1337)
        colWidth = 36f * resources.displayMetrics.density.coerceIn(1f, 3f)
        numCols = (w / colWidth).toInt().coerceAtLeast(16)
        colWidth = w.toFloat() / numCols

        val fontSize = (rowHeight * 0.72f) * resources.displayMetrics.density.coerceIn(1f, 2f)
        textShadowPaint.textSize = fontSize
        textClayPaint.textSize = fontSize
        textHighlightPaint.textSize = fontSize * 0.88f

        // 2-3 overlapping streams per column for dense Matrix rain depth
        val totalStreams = (numCols * 1.5).toInt()
        streams = Array(totalStreams) { i ->
            val col = i % numCols
            val streamLen = 10 + random.nextInt(12)
            val chars = CharArray(streamLen) { glyphPool[random.nextInt(glyphPool.size)] }
            Stream(
                column = col,
                headY = random.nextFloat() * (h + 300f) - 150f,
                speed = 3.5f + random.nextFloat() * 5.5f,
                length = streamLen,
                chars = chars,
                colorTheme = if (random.nextInt(10) == 0) (if (random.nextBoolean()) 1 else 2) else 0
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initStreams(w, h)
            if (!animRunning) {
                animRunning = true
                startLoop()
            }
        }
    }

    private fun startLoop() {
        post(object : Runnable {
            override fun run() {
                val streamArray = streams
                if (streamArray != null && height > 0) {
                    val random = Random
                    for (stream in streamArray) {
                        stream.headY += stream.speed
                        stream.stepCounter++

                        // Randomly mutate characters inside stream like authentic Matrix rain
                        if (stream.stepCounter % 4 == 0) {
                            val mutateIdx = random.nextInt(stream.length)
                            stream.chars[mutateIdx] = glyphPool[random.nextInt(glyphPool.size)]
                        }

                        // Reset stream when it exits screen
                        val totalHeight = stream.length * rowHeight
                        if (stream.headY - totalHeight > height) {
                            stream.headY = -rowHeight * 2f
                            stream.speed = 3.5f + random.nextFloat() * 5.5f
                            stream.length = 10 + random.nextInt(12)
                            stream.chars = CharArray(stream.length) { glyphPool[random.nextInt(glyphPool.size)] }
                            stream.colorTheme = if (random.nextInt(10) == 0) (if (random.nextBoolean()) 1 else 2) else 0
                        }
                    }
                    invalidate()
                }
                postDelayed(this, 28L)
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val streamArray = streams ?: return

        for (stream in streamArray) {
            val cx = (stream.column * colWidth) + (colWidth * 0.5f)
            val palette = when (stream.colorTheme) {
                1 -> copperColors
                2 -> amethystColors
                else -> tealColors
            }

            for (i in 0 until stream.length) {
                val y = stream.headY - (i * rowHeight)
                if (y < -rowHeight || y > height + rowHeight) continue

                val glyph = stream.chars[i % stream.chars.size].toString()

                val colorIdx = (i.toFloat() / stream.length * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)
                val baseColor = palette[colorIdx]

                if (i == 0) {
                    // --- 3D CLAY HEAD (Bright glowing tip) ---
                    headGlowPaint.color = palette[2]
                    headGlowPaint.alpha = 110
                    canvas.drawCircle(cx, y - (rowHeight * 0.28f), rowHeight * 0.65f, headGlowPaint)

                    textShadowPaint.color = Color.argb(180, 0, 0, 0)
                    canvas.drawText(glyph, cx + 1.5f, y + 2.5f, textShadowPaint)

                    textClayPaint.color = Color.WHITE
                    canvas.drawText(glyph, cx, y, textClayPaint)

                    textHighlightPaint.color = Color.argb(220, 255, 255, 255)
                    canvas.drawText(glyph, cx - 1f, y - 1f, textHighlightPaint)
                } else {
                    // --- 3D CLAY TRAIL RUNES ---
                    val alpha = (255 - (i.toFloat() / stream.length * 200)).toInt().coerceIn(30, 240)

                    textShadowPaint.color = Color.argb((alpha * 0.7f).toInt(), 0, 0, 0)
                    canvas.drawText(glyph, cx + 1.2f, y + 2f, textShadowPaint)

                    textClayPaint.color = baseColor
                    textClayPaint.alpha = alpha
                    canvas.drawText(glyph, cx, y, textClayPaint)

                    if (i < 4) {
                        textHighlightPaint.color = Color.argb((alpha * 0.5f).toInt(), 255, 255, 255)
                        canvas.drawText(glyph, cx - 0.8f, y - 0.8f, textHighlightPaint)
                    }
                }
            }
        }
    }
}