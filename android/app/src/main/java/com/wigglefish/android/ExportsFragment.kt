package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ExportsFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()
    private var historyContainer: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_exports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sessionText = view.findViewById<TextView>(R.id.sessionText)
        val sourceText = view.findViewById<TextView>(R.id.sourceText)
        val coverageText = view.findViewById<TextView>(R.id.coverageText)
        historyContainer = view.findViewById(R.id.sessionHistoryContainer)
        val host = requireActivity() as SurveyHost

        view.findViewById<Button>(R.id.exportButton).setOnClickListener { host.shareSessionJson() }
        view.findViewById<Button>(R.id.csvButton).setOnClickListener { host.shareSessionCsv() }
        view.findViewById<Button>(R.id.wardriveGoButton).setOnClickListener { host.shareWardriveGoCsv() }
        view.findViewById<Button>(R.id.wigleButton).setOnClickListener { host.shareWigleCsv() }
        view.findViewById<Button>(R.id.geoJsonButton).setOnClickListener { host.shareGeoJson() }
        view.findViewById<Button>(R.id.clearButton).setOnClickListener {
            // clearSession archives a non-empty session first, then resets live state
            host.clearSession()
            refreshHistory(host)
        }
        view.findViewById<Button>(R.id.stopAllButton).setOnClickListener { host.stopAllCollection() }
        view.findViewById<Button>(R.id.saveSessionButton).setOnClickListener {
            host.saveCurrentSession()
            refreshHistory(host)
        }
        view.findViewById<Button>(R.id.refreshHistoryButton).setOnClickListener {
            refreshHistory(host)
        }

        session.ui.observe(viewLifecycleOwner) { state ->
            sessionText.text = state.session
            sourceText.text = state.source
            coverageText.text = state.coverageHint
        }
        refreshHistory(host)
    }

    override fun onResume() {
        super.onResume()
        (activity as? SurveyHost)?.let { refreshHistory(it) }
    }

    private fun refreshHistory(host: SurveyHost) {
        val container = historyContainer ?: return
        container.removeAllViews()
        val sessions = host.listArchivedSessions()
        if (sessions.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "No archived sessions yet. SAVE SESSION or CLEAR (auto-saves non-empty)."
                setTextColor(0xFF5D958D.toInt())
                textSize = 12f
                setPadding(0, 8, 0, 8)
                typeface = android.graphics.Typeface.MONOSPACE
            })
            return
        }
        sessions.forEach { meta ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF10142B.toInt())
                setPadding(28, 24, 28, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 16
                layoutParams = lp
            }
            val s = meta.summary
            card.addView(TextView(requireContext()).apply {
                text = meta.label
                setTextColor(0xFFFF4FD8.toInt())
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(TextView(requireContext()).apply {
                text = s.formatLine()
                setTextColor(0xFF8FF7FF.toInt())
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            })
            card.addView(TextView(requireContext()).apply {
                text = s.formatCoverageDetail()
                setTextColor(0xFFF0B35A.toInt())
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
            })
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 12
                layoutParams = lp
            }
            fun miniButton(label: String, tint: Int, onClick: () -> Unit): Button {
                return Button(requireContext()).apply {
                    text = label
                    textSize = 10f
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(16, 8, 16, 8)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(tint)
                    setTextColor(0xFF050611.toInt())
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.marginEnd = 8
                    layoutParams = lp
                    setOnClickListener { onClick() }
                }
            }
            row.addView(miniButton("OPEN", 0xFF8FF7FF.toInt()) {
                host.loadArchivedSession(meta.fileName)
                Toast.makeText(requireContext(), "Loaded ${meta.label}", Toast.LENGTH_SHORT).show()
            })
            row.addView(miniButton("JSON", 0xFFF0B35A.toInt()) {
                host.exportArchivedSession(meta.fileName, "json")
            })
            row.addView(miniButton("WIGLE", 0xFFFF4FD8.toInt()) {
                host.exportArchivedSession(meta.fileName, "wigle")
            })
            row.addView(miniButton("DEL", 0xFF5A1A2E.toInt()) {
                host.deleteArchivedSession(meta.fileName)
                refreshHistory(host)
            }.also { it.setTextColor(0xFFF4F7FF.toInt()) })
            card.addView(row)
            val row2 = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 8
                layoutParams = lp
            }
            row2.addView(miniButton("GEOJSON", 0xFF8FF7FF.toInt()) {
                host.exportArchivedSession(meta.fileName, "geojson")
            })
            row2.addView(miniButton("CSV", 0xFFF0B35A.toInt()) {
                host.exportArchivedSession(meta.fileName, "csv")
            })
            row2.addView(miniButton("GO CSV", 0xFFFF4FD8.toInt()) {
                host.exportArchivedSession(meta.fileName, "wardrive")
            })
            card.addView(row2)
            container.addView(card)
        }
    }
}
