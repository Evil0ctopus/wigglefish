package com.wigglefish.android

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat

/** Reuses the existing controller views so all device actions keep their original wiring. */
class EventUiShell(private val activity: AppCompatActivity) {
    private val density = activity.resources.displayMetrics.density
    private val compact = activity.resources.configuration.screenHeightDp < 700
    private val tabs = listOf(
        R.id.tabScannerBtn, R.id.tabRadarBtn, R.id.tabToolsBtn,
        R.id.tabAnalyticsBtn, R.id.tabExportsBtn,
    ).map { activity.findViewById<Button>(it) }
    private val pageTitle = TextView(activity)
    private val pageDescription = TextView(activity)
    private val issueLabel = TextView(activity)
    private val heroArt = ComicHeroView(activity)
    private lateinit var heroPanel: LinearLayout
    private lateinit var contentPanel: FrameLayout
    private val companionButton = Button(activity)
    private val companion = activity.findViewById<LinearLayout>(R.id.petDockContainer)
    private var companionDialog: AlertDialog? = null
    private var theme = AppTheme.KOHOLINT_TOYBOX
    private val disclosureButtons = mutableListOf<Button>()
    private val cards = mutableListOf<View>()
    private val texts = mutableListOf<TextView>()
    private val buttons = mutableListOf<Button>()
    private val toolTitles = mutableListOf<TextView>()
    private var currentPage = -1
    private lateinit var navigation: LinearLayout

    fun install() {
        val root = activity.findViewById<FrameLayout>(R.id.rootContainer)
        val main = root.getChildAt(0) as LinearLayout
        val header = activity.findViewById<LinearLayout>(R.id.headerContainer)
        navigation = tabs.first().parent as LinearLayout
        val content = activity.findViewById<View>(R.id.sectionScanner).parent as FrameLayout
        contentPanel = content
        detach(companion)
        detach(navigation)
        detach(content)

        val title = activity.findViewById<TextView>(R.id.appTitleText)
        val style = activity.findViewById<Button>(R.id.themeButton)
        val connect = activity.findViewById<Button>(R.id.connectButton)
        val usb = activity.findViewById<TextView>(R.id.deviceBadge)
        val gps = activity.findViewById<TextView>(R.id.gpsBadge)
        val status = activity.findViewById<TextView>(R.id.appStatusText)
        val alert = activity.findViewById<TextView>(R.id.threatAlertText)
        listOf(title, style, connect, usb, gps, status, alert).forEach(::detach)
        header.removeAllViews()
        main.removeAllViews()
        main.addView(header)
        header.setPadding(dp(16), dp(if (compact) 4 else 10), dp(16), dp(if (compact) 4 else 10))

        val top = row()
        title.text = "WIGGLEFISH"
        title.textSize = if (compact) 17f else 21f
        title.typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        style.text = "Paint"
        style.setBackgroundResource(R.drawable.btn_3d_dark)
        style.setTextColor(Color.WHITE)
        style.contentDescription = "Choose comic paint palette and animation settings"
        top.addView(style, LinearLayout.LayoutParams(dp(64), dp(48)))
        connect.text = "Connect"
        connect.setBackgroundResource(R.drawable.btn_3d_cyan)
        top.addView(connect, LinearLayout.LayoutParams(dp(86), dp(48)).apply {
            marginStart = dp(6)
        })
        header.addView(top)

        val badges = row()
        listOf(usb, gps).forEach { badge ->
            badge.textSize = 11f
            badge.maxLines = 2
            badge.ellipsize = TextUtils.TruncateAt.END
            badges.addView(badge, LinearLayout.LayoutParams(0, -2, 1f))
        }
        header.addView(badges, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(if (compact) 4 else 8)
        })
        status.textSize = 12f
        status.maxLines = if (compact) 1 else 2
        status.ellipsize = TextUtils.TruncateAt.END
        status.background = null
        status.setPadding(0, dp(if (compact) 2 else 6), 0, 0)
        status.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        header.addView(status)
        header.addView(alert)

        heroPanel = row().apply { setPadding(dp(16), dp(if (compact) 6 else 13), dp(8), dp(if (compact) 10 else 13)) }
        val headingText = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        issueLabel.textSize = 9f
        issueLabel.letterSpacing = 0.13f
        issueLabel.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        issueLabel.setPadding(0, 0, 0, dp(5))
        pageTitle.textSize = if (compact) 20f else 30f
        pageTitle.typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
        pageDescription.textSize = 12f
        pageDescription.setPadding(0, dp(5), dp(8), 0)
        pageDescription.visibility = if (compact) View.GONE else View.VISIBLE
        headingText.addView(issueLabel)
        headingText.addView(pageTitle)
        headingText.addView(pageDescription)
        heroPanel.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
        companionButton.text = "Lumi"
        companionButton.contentDescription = "Open Lumi companion"
        companionButton.setOnClickListener { heroArt.celebrate(currentPage.coerceAtLeast(0)); showCompanion() }
        val mascot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        mascot.addView(heroArt, LinearLayout.LayoutParams(dp(if (compact) 88 else 108), dp(if (compact) 64 else 90)))
        mascot.addView(companionButton, LinearLayout.LayoutParams(dp(88), dp(48)))
        if (compact) {
            companionButton.visibility = View.GONE
            heroArt.companionShortcut = true
            heroArt.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            heroArt.contentDescription = "Open Lumi companion"
            heroArt.setOnClickListener { companionButton.performClick() }
        }
        heroPanel.addView(mascot, LinearLayout.LayoutParams(dp(if (compact) 92 else 112), -2))
        main.addView(heroPanel, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(8), dp(12), dp(4))
        })
        main.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        navigation.setPadding(dp(8), dp(8), dp(8), dp(8))
        tabs.forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            button.textSize = 11f
            button.isAllCaps = false
            button.setPadding(dp(2), dp(5), dp(2), dp(6))
            button.setCompoundDrawablesWithIntrinsicBounds(null, ComicGlyphDrawable(tabs.indexOf(button), density), null, null)
            button.compoundDrawablePadding = dp(2)
            button.maxLines = 1
            button.setHorizontallyScrolling(false)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                button, 9, 11, 1, TypedValue.COMPLEX_UNIT_SP,
            )
            button.stateListAnimator = null
        }
        main.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        polish(content)
        if (compact) {
            activity.findViewById<View>(R.id.sectionScanner).setPadding(dp(12), dp(8), dp(12), dp(8))
            listOf(R.id.countWifiText, R.id.countBleText, R.id.countThreatText).forEach { id ->
                activity.findViewById<TextView>(id).apply {
                    textSize = 12f
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                }
            }
        }
        polish(companion)
        val name = companion.findViewById<TextView>(R.id.petNameTitleText)
        val experience = companion.findViewById<TextView>(R.id.petExpText)
        listOf(name.parent, experience.parent).forEach {
            (it as LinearLayout).orientation = LinearLayout.VERTICAL
        }
        listOf(R.id.petNameTitleText, R.id.petPwnedBadgeText, R.id.petExpText, R.id.petMoodTagText).forEach { id ->
            companion.findViewById<TextView>(id).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                textSize = if (id == R.id.petNameTitleText) 15f else 12f
                setPadding(0, dp(3), 0, dp(3))
            }
        }
        companion.findViewById<TextView>(R.id.petSpeechText).typeface =
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        cards.remove(companion)
        companion.background = null
        listOf(usb, gps, status, alert).forEach {
            it.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            texts.add(it)
        }
        val resultPanel = activity.findViewById<View>(R.id.signalList).parent as View
        cards.remove(resultPanel)
        resultPanel.background = null
        resultPanel.setPadding(0, 0, 0, 0)
        listOf(style, connect, companionButton).forEach(::polish)
        focusSecondaryPages()
        buildToolDisclosures()
        selectTab(0)
    }

    private fun focusSecondaryPages() {
        listOf(R.id.sectionRadar, R.id.sectionAnalytics, R.id.sectionExports).forEach { id ->
            val page = activity.findViewById<ScrollView>(id).getChildAt(0) as LinearLayout
            page.getChildAt(0).visibility = View.GONE
        }
        val radarPage = activity.findViewById<ScrollView>(R.id.sectionRadar).getChildAt(0) as LinearLayout
        val chooseTarget = activity.findViewById<Button>(R.id.geigerSelectBtn)
        detach(chooseTarget)
        chooseTarget.text = "Choose a signal to follow"
        radarPage.addView(chooseTarget, 1, LinearLayout.LayoutParams(-1, dp(48)))
        val decoder = activity.findViewById<TextView>(R.id.decodeText)
        decoder.text = "LAST HEARD / Waiting for a signal"
        decoder.textSize = 12f
        (decoder.parent as View).layoutParams.height = dp(44)
        val report = activity.findViewById<TextView>(R.id.analyticsContentText)
        listOf(R.id.intelCountsText, R.id.intelTopFindingsText).forEach { id ->
            activity.findViewById<TextView>(id).apply {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                minHeight = dp(96)
            }
        }
        val reportPage = report.parent as LinearLayout
        val reportToggle = Button(activity).apply {
            text = "Show full survey report"
            textSize = 13f
            isAllCaps = false
            setOnClickListener {
                val expanded = report.visibility != View.VISIBLE
                report.visibility = if (expanded) View.VISIBLE else View.GONE
                text = if (expanded) "Hide full survey report" else "Show full survey report"
                ComicMotion.pop(this)
                if (expanded) ComicMotion.enter(report)
            }
        }
        report.visibility = View.GONE
        reportPage.addView(reportToggle, reportPage.indexOfChild(report), LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
        })
        disclosureButtons.add(reportToggle)
        mapOf(
            R.id.exportPcapBtn to "Wireshark capture (.pcap)",
            R.id.exportHashcatBtn to "Captured hashes (.22000)",
            R.id.exportCsvBtn to "Survey spreadsheet (.csv)",
            R.id.exportGpxBtn to "GPS route (.gpx)",
            R.id.exportJsonBtn to "Complete session (.jsonl)",
            R.id.clearSessionBtn to "Clear session observations",
        ).forEach { (id, label) -> activity.findViewById<Button>(id).text = label }
    }

    private fun buildToolDisclosures() {
        val scroll = activity.findViewById<ScrollView>(R.id.sectionTools)
        val tools = scroll.getChildAt(0) as LinearLayout
        tools.getChildAt(0).visibility = View.GONE
        val operations = tools.getChildAt(1) as LinearLayout
        val diagnostics = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        listOf(R.id.toolTrafficText, R.id.toolLastEventText, R.id.toolEventFeedText).forEach { id ->
            val detail = activity.findViewById<View>(id)
            detach(detail)
            diagnostics.addView(detail)
        }
        val activityToggle = Button(activity).apply {
            text = "Show session activity"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                val expanded = diagnostics.visibility != View.VISIBLE
                diagnostics.visibility = if (expanded) View.VISIBLE else View.GONE
                text = if (expanded) "Hide session activity" else "Show session activity"
                ComicMotion.pop(this)
                if (expanded) ComicMotion.enter(diagnostics)
            }
        }
        operations.addView(activityToggle, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
        })
        operations.addView(diagnostics)
        disclosureButtons.add(activityToggle)
        // Keep the operations/stop panel visible; reveal individual tools only on demand.
        val toolCards = (0 until tools.childCount).map { tools.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .drop(1)
        toolCards.forEachIndexed { index, card ->
            val title = card.getChildAt(0) as? TextView ?: return@forEachIndexed
            if (title.id == View.NO_ID) {
                title.text = when {
                    title.text.contains("HANDSHAKE") -> "Handshake capture"
                    title.text.contains("LIGHT") -> "Smart lights"
                    title.text.contains("LED") -> "Board indicator"
                    else -> title.text
                }
            }
            val children = (0 until card.childCount).map { card.getChildAt(it) }
            card.removeAllViews()
            val toggle = Button(activity).apply {
                text = "Open"
                textSize = 12f
                isAllCaps = false
                contentDescription = "Expand ${title.text}"
            }
            val heading = row()
            title.textSize = 15f
            title.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            toolTitles.add(title)
            val titleColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            titleColumn.addView(TextView(activity).apply {
                text = "MODULE %02d".format(index + 1)
                textSize = 9f
                letterSpacing = 0.12f
                setTextColor(ComicInk.muted)
                setPadding(0, 0, 0, dp(5))
            })
            titleColumn.addView(title)
            heading.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(toggle, LinearLayout.LayoutParams(dp(72), dp(48)))
            card.addView(heading)
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            children.drop(1).forEach { body.addView(it) }
            card.addView(body)
            toggle.setOnClickListener {
                val expanded = body.visibility != View.VISIBLE
                body.visibility = if (expanded) View.VISIBLE else View.GONE
                toggle.text = if (expanded) "Close" else "Open"
                toggle.contentDescription = "${if (expanded) "Collapse" else "Expand"} ${title.text}"
                ComicMotion.pop(toggle)
                if (expanded) ComicMotion.enter(body)
            }
            disclosureButtons.add(toggle)
        }
    }

    private fun polish(view: View) {
        if (view is ViewGroup) {
            if (view.background != null) {
                cards.add(view)
                view.elevation = 0f
            }
            for (index in 0 until view.childCount) polish(view.getChildAt(index))
        }
        if (view is TextView && view.background != null && view !is Button) {
            cards.add(view)
        }
        if (view is Button) {
            buttons.add(view)
            view.isAllCaps = false
            view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            view.textSize = maxOf(view.textSize / activity.resources.displayMetrics.scaledDensity, 12f)
            view.minimumHeight = dp(48)
            if (view.layoutParams != null && view.layoutParams.height in 1 until dp(48)) {
                view.layoutParams.height = dp(48)
            }
            view.setPadding(dp(6), dp(4), dp(6), dp(4))
            view.stateListAnimator = null
        } else if (view is TextView) {
            texts.add(view)
            view.textSize = maxOf(view.textSize / activity.resources.displayMetrics.scaledDensity, 13f)
            if (view.id == View.NO_ID) {
                view.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
        }
    }

    fun applyTheme(value: AppTheme) {
        theme = value
        pageTitle.setTextColor(value.textPrimary)
        pageDescription.setTextColor(value.textSecondary)
        issueLabel.setTextColor(ComicInk.black)
        navigation.setBackgroundColor(value.surface)
        texts.forEach { it.setTextColor(ComicInk.black) }
        buttons.forEach { button ->
            val fill = when (button.id) {
                R.id.connectButton, R.id.geigerSelectBtn, R.id.exportPcapBtn, R.id.exportGpxBtn -> value.primaryAccent
                R.id.toolStopAllBtn, R.id.exportCsvBtn -> value.secondaryAccent
                R.id.exportHashcatBtn, R.id.exportJsonBtn -> value.tertiaryAccent
                else -> value.surface
            }
            button.background = ComicInk.button(fill, density)
            button.setTextColor(ComicInk.black)
        }
        cards.forEach { view ->
            val fill = when (view.id) {
                R.id.countWifiText -> value.primaryAccent
                R.id.countBleText -> value.secondaryAccent
                R.id.countThreatText, R.id.intelPostureText -> value.tertiaryAccent
                else -> value.cardBackground
            }
            view.background = ComicPanelDrawable(fill, density)
        }
        disclosureButtons.forEach {
            it.background = ComicInk.button(value.tertiaryAccent, density)
            it.setTextColor(ComicInk.black)
        }
        companionButton.background = ComicInk.button(value.secondaryAccent, density)
        companionButton.setTextColor(ComicInk.black)
        heroPanel.background = ComicPanelDrawable(value.primaryAccent, density)
        heroArt.setPalette(value)
        if (compact) heroArt.background = ComicInk.button(value.secondaryAccent, density)
        activity.findViewById<TextView>(R.id.appTitleText).setTextColor(ComicInk.black)
        activity.findViewById<SignalRadarView>(R.id.radarView).setPalette(value)
        companion.findViewById<LumiPetView>(R.id.wigglePetView).setPalette(value)
    }

    fun selectTab(index: Int) {
        val changed = currentPage != index
        currentPage = index
        val titles = listOf("SIGNAL\nSCOUT", "RADAR\nRIDER", "POWER\nTOOLS", "THE\nLOWDOWN", "PACK\n& GO")
        val descriptions = listOf(
            "Real signals. Fresh discoveries.",
            "Lock a signal. Follow the pulse.",
            "Lab tools. You're in control.",
            "Your survey, decoded.",
            "Your findings. Ready to travel.",
        )
        pageTitle.text = if (compact) titles[index].replace('\n', ' ') else titles[index]
        pageDescription.text = descriptions[index]
        issueLabel.text = "ISSUE %02d / %s".format(index + 1,
            listOf("PASSIVE SURVEY", "SIGNAL TRACKING", "AUTHORIZED LAB", "SURVEY INSIGHTS", "SESSION EXPORT")[index])
        val labels = listOf("Discover", "Radar", "Tools", "Insights", "Save")
        tabs.forEachIndexed { position, button ->
            button.text = labels[position]
            button.isSelected = position == index
            button.contentDescription = "${labels[position]}${if (position == index) ", selected" else ""}"
            button.background = if (position == index) ComicInk.button(theme.tertiaryAccent, density)
                else ComicInk.button(theme.surface, density)
            button.setTextColor(ComicInk.black)
        }
        if (changed) {
            ComicMotion.enter(contentPanel)
            ComicMotion.pop(tabs[index])
            heroArt.celebrate(index)
        }
    }

    private fun showCompanion() {
        detach(companion)
        val scroll = ScrollView(activity).apply {
            addView(companion, ViewGroup.LayoutParams(-1, -2))
        }
        companionDialog = AlertDialog.Builder(activity)
            .setTitle("LUMI'S PIT STOP")
            .setView(scroll)
            .setPositiveButton("Back to exploring", null)
            .create().also { dialog ->
                dialog.setOnDismissListener {
                    detach(companion)
                    companionDialog = null
                }
                dialog.show()
                dialog.window?.setBackgroundDrawable(ComicPanelDrawable(theme.surface, density))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    textSize = 12f
                    ComicInk.style(this, theme.primaryAccent)
                }
            }
    }

    fun refreshMotion() {
        heroArt.celebrate(currentPage.coerceAtLeast(0))
        activity.findViewById<SignalRadarView>(R.id.radarView).refreshMotion()
        companion.findViewById<LumiPetView>(R.id.wigglePetView).refreshMotion()
        if (!ComicMotion.enabled(activity)) {
            listOf(contentPanel, heroPanel, companionButton).plus(tabs).forEach {
                it.animate().cancel()
                it.alpha = 1f
                it.translationY = 0f
                it.scaleX = 1f
                it.scaleY = 1f
            }
        }
    }

    fun close() {
        contentPanel.animate().cancel()
        tabs.forEach { it.animate().cancel() }
        companionDialog?.dismiss()
    }

    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
