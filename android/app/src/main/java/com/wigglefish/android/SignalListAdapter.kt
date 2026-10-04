package com.wigglefish.android

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

data class SignalRow(
    val key: String,
    val name: String,
    val description: String,
    val signal: String,
    val risk: String,
    val color: Int,
)

class SignalListAdapter(private val context: Context) : BaseAdapter() {
    private var rows: List<SignalRow> = emptyList()
    private var theme = AppTheme.KOHOLINT_TOYBOX
    private val density = context.resources.displayMetrics.density
    private val compact = context.resources.configuration.screenHeightDp < 700

    fun submit(value: List<SignalRow>, palette: AppTheme) {
        if (rows == value && theme == palette) return
        rows = value
        theme = palette
        notifyDataSetChanged()
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    private class Holder(val name: TextView, val description: TextView, val signal: TextView, val badge: TextView)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(if (compact) 8 else 16), dp(20), dp(if (compact) 10 else 18))
            val badge = label(9f).apply {
                letterSpacing = 0.12f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
            }
            val name = label(18f).apply {
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val description = label(12f).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(if (compact) 3 else 5), 0, 0)
            }
            val signal = label(12f).apply {
                setPadding(dp(10), dp(if (compact) 4 else 7), dp(10), dp(if (compact) 6 else 9))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            addView(badge, LinearLayout.LayoutParams(-2, -2))
            addView(name)
            addView(description)
            addView(signal, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(if (compact) 4 else 8) })
            tag = Holder(name, description, signal, badge)
        }
        val holder = view.tag as Holder
        val item = getItem(position)
        holder.name.text = item.name
        holder.name.setTextColor(theme.textPrimary)
        holder.description.text = item.description
        holder.description.setTextColor(theme.textSecondary)
        holder.signal.text = "${item.signal}  /  ${item.risk}"
        holder.signal.setTextColor(item.color)
        val wifi = item.description.startsWith("Wi-Fi")
        holder.badge.text = if (wifi) "WI-FI / OBSERVED" else "BLUETOOTH / OBSERVED"
        holder.badge.setTextColor(ComicInk.black)
        holder.badge.setPadding(dp(8), dp(4), dp(8), dp(6))
        holder.badge.background = ComicPanelDrawable(
            if (wifi) theme.primaryAccent else theme.secondaryAccent, density, decorated = false, shadow = false,
        )
        // Keep risk text on light paper for reliable contrast across every paint palette.
        holder.signal.background = ComicPanelDrawable(theme.surface, density, decorated = false, shadow = false)
        view.background = ComicPanelDrawable(theme.cardBackground, density)
        view.contentDescription = "${item.name}, ${item.description}, ${holder.signal.text}. Tap for details."
        return view
    }

    private fun label(size: Float) = TextView(context).apply { textSize = size }
    private fun dp(value: Int) = (value * density).toInt()
}
