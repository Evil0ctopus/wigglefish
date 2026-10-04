package com.wigglefish.android

import android.view.View
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.core.graphics.ColorUtils
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventUiTest {
    private var savedMotion = true

    @Before
    fun enableMotionForTests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        savedMotion = context.getSharedPreferences("wigglefish_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("comic_motion", true)
        ComicMotion.setEnabled(context, true)
    }

    @After
    fun restoreMotionPreference() {
        ComicMotion.setEnabled(InstrumentationRegistry.getInstrumentation().targetContext, savedMotion)
    }

    @Test
    fun navigationKeepsAllPagesReachableAndRestoresSelection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tabs = listOf(
                    R.id.tabScannerBtn, R.id.tabRadarBtn, R.id.tabToolsBtn,
                    R.id.tabAnalyticsBtn, R.id.tabExportsBtn,
                )
                val sections = listOf(
                    R.id.sectionScanner, R.id.sectionRadar, R.id.sectionTools,
                    R.id.sectionAnalytics, R.id.sectionExports,
                )
                repeat(3) {
                    tabs.forEachIndexed { index, id ->
                        val button = activity.findViewById<Button>(id)
                        button.performClick()
                        assertTrue(button.isSelected)
                        sections.forEachIndexed { sectionIndex, sectionId ->
                            assertEquals(
                                if (index == sectionIndex) View.VISIBLE else View.GONE,
                                activity.findViewById<View>(sectionId).visibility,
                            )
                        }
                        assertTrue(button.height >= 48 * activity.resources.displayMetrics.density)
                        assertTrue("Navigation label must fit without clipping: ${button.text}",
                            button.paint.measureText(button.text.toString()) <=
                                button.width - button.paddingLeft - button.paddingRight)
                        if (index == 3) {
                            val report = activity.findViewById<TextView>(R.id.analyticsContentText)
                            val page = report.parent as LinearLayout
                            val toggle = (0 until page.childCount).map { page.getChildAt(it) }
                                .filterIsInstance<Button>().single { it.text == "Show full survey report" }
                            assertEquals(View.GONE, report.visibility)
                            toggle.performClick()
                            assertEquals(View.VISIBLE, report.visibility)
                            toggle.performClick()
                            assertEquals(View.GONE, report.visibility)
                        }
                    }
                }
                activity.findViewById<Button>(R.id.tabToolsBtn).performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Button>(R.id.tabToolsBtn).isSelected)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.sectionTools).visibility)
            }
        }
    }

    @Test
    fun toolCardsExpandWithoutStartingDeviceActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.tabToolsBtn).performClick()
                val tools = activity.findViewById<ScrollView>(R.id.sectionTools).getChildAt(0) as ViewGroup
                val cards = (0 until tools.childCount).map { tools.getChildAt(it) }
                    .filterIsInstance<LinearLayout>().drop(1)
                assertTrue(cards.size >= 6)
                cards.forEach { card ->
                    val heading = card.getChildAt(0) as LinearLayout
                    val toggle = heading.getChildAt(1) as Button
                    val body = card.getChildAt(1)
                    assertEquals(View.GONE, body.visibility)
                    toggle.performClick()
                    assertEquals(View.VISIBLE, body.visibility)
                    toggle.performClick()
                    assertEquals(View.GONE, body.visibility)
                }
                assertTrue(activity.findViewById<TextView>(R.id.toolLiveOpsText).text.contains("SAFE"))
                assertTrue(activity.findViewById<TextView>(R.id.deviceBadge).text.contains("not connected"))
            }
        }
    }

    @Test
    fun signalListRecyclesRowsAndPreservesAllThousandResults() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ListView>(R.id.signalList)
                val adapter = list.adapter as SignalListAdapter
                val rows = (0 until 1000).map { index ->
                    SignalRow("test-$index", "Signal $index", "Wi-Fi / Synthetic observation\n00:11:22:33:44:55",
                        "-50 dBm", "LOW", 0xFF66E2CB.toInt())
                }
                adapter.submit(rows, AppTheme.KOHOLINT_TOYBOX)
                assertEquals(1000, adapter.count)
                val first = adapter.getView(0, null, list)
                val last = adapter.getView(999, first, list)
                assertSame(first, last)
                assertEquals("test-999", adapter.getItem(999).key)
                assertTrue(last.contentDescription.contains("Signal 999"))
                list.setSelection(999)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<ListView>(R.id.signalList)
                assertEquals(999, list.lastVisiblePosition)
                assertTrue("Only visible rows should be attached", list.childCount in 1..20)
                val cardHeight = list.getChildAt(0).height
                assertTrue("Complete signal card needs $cardHeight px; list has ${list.height} px",
                    list.height >= cardHeight)
                list.setSelection(0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<ListView>(R.id.signalList)
                val adapter = list.adapter as SignalListAdapter
                assertEquals(0, list.firstVisiblePosition)
                adapter.submit(emptyList(), AppTheme.KOHOLINT_TOYBOX)
                assertEquals(0, adapter.count)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultsText).visibility)
                assertEquals(View.GONE, list.visibility)
            }
        }
    }

    @Test
    fun observationsFilterAndOpenDetailsWithoutUsbConnection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val handleLine = MainActivity::class.java.getDeclaredMethod("handleLine", String::class.java)
                    .apply { isAccessible = true }
                handleLine.invoke(activity, """{"type":"wifi","ssid":"Event test network","bssid":"02:00:00:00:00:01","channel":6,"rssi":-62,"security":"WPA2"}""")
                handleLine.invoke(activity, """{"type":"bluetooth","name":"Event test beacon","address":"02:00:00:00:00:02","rssi":-71}""")
                activity.findViewById<Button>(R.id.filterBleBtn).performClick()
                val list = activity.findViewById<ListView>(R.id.signalList)
                val adapter = list.adapter as SignalListAdapter
                assertTrue((0 until adapter.count).any { adapter.getItem(it).name == "Event test beacon" })
                assertFalse((0 until adapter.count).any { adapter.getItem(it).name == "Event test network" })
                activity.findViewById<Button>(R.id.filterWifiBtn).performClick()
                val position = (0 until adapter.count).first { adapter.getItem(it).name == "Event test network" }
                list.performItemClick(adapter.getView(position, null, list), position, adapter.getItemId(position))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.deviceBadge).text.contains("not connected"))
            }
        }

    }

    @Test
    fun radarAnimationStopsOffscreenAndRestartsWhenVisible() {
        val angle = SignalRadarView::class.java.getDeclaredField("sweepAngle").apply {
            isAccessible = true
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var radar: SignalRadarView? = null
            var before = 0f
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.tabRadarBtn).performClick()
                radar = activity.findViewById(R.id.radarView)
                before = angle.getFloat(radar)
            }
            Thread.sleep(180)
            scenario.onActivity { activity ->
                assertNotEquals(before, angle.getFloat(radar))
                activity.findViewById<Button>(R.id.tabScannerBtn).performClick()
                before = angle.getFloat(radar)
            }
            Thread.sleep(180)
            scenario.onActivity { activity ->
                assertEquals(before, angle.getFloat(radar), 0f)
                activity.findViewById<Button>(R.id.tabRadarBtn).performClick()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { before = angle.getFloat(radar) }
            Thread.sleep(180)
            scenario.onActivity { assertEquals(before, angle.getFloat(radar), 0f) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(180)
            scenario.onActivity { assertNotEquals(before, angle.getFloat(radar)) }
        }
    }

    @Test
    fun companionOpensSeparatelyAndEveryPaletteKeepsNavigationUsable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var pet: LumiPetView
            lateinit var dialog: androidx.appcompat.app.AlertDialog
            scenario.onActivity { activity ->
                val shellField = MainActivity::class.java.getDeclaredField("eventUi").apply { isAccessible = true }
                val shell = shellField.get(activity) as EventUiShell
                val companionButton = EventUiShell::class.java.getDeclaredField("companionButton")
                    .apply { isAccessible = true }.get(shell) as Button
                val dialogField = EventUiShell::class.java.getDeclaredField("companionDialog")
                    .apply { isAccessible = true }
                pet = MainActivity::class.java.getDeclaredField("wigglePetView")
                    .apply { isAccessible = true }.get(activity) as LumiPetView
                assertFalse(pet.isAttachedToWindow)
                companionButton.performClick()
                dialog = dialogField.get(shell) as androidx.appcompat.app.AlertDialog
                assertTrue(dialog.isShowing)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(pet.isAttachedToWindow)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(pet.isAttachedToWindow)
                val applyTheme = MainActivity::class.java.getDeclaredMethod("applyTheme", AppTheme::class.java)
                    .apply { isAccessible = true }
                AppTheme.values().forEach { palette ->
                    applyTheme.invoke(activity, palette)
                    activity.findViewById<Button>(R.id.tabExportsBtn).performClick()
                    assertTrue(activity.findViewById<Button>(R.id.tabExportsBtn).isSelected)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.sectionExports).visibility)
                }
            }
        }
    }

    @Test
    fun comicPaintHasInkOutlinesAndReadableContrastInAllPalettes() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            AppTheme.values().forEach { palette ->
                listOf(palette.cardBackground, palette.surface, palette.primaryAccent,
                    palette.secondaryAccent, palette.tertiaryAccent).forEach { fill ->
                    assertTrue("Ink contrast must exceed 4.5:1",
                        ColorUtils.calculateContrast(ComicInk.black, fill) >= 4.5)
                }
                val paintwork = ComicPanelDrawable(palette.primaryAccent, 1f)
                paintwork.setBounds(0, 0, 200, 100)
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                paintwork.draw(Canvas(bitmap))
                assertEquals(palette.primaryAccent, bitmap.getPixel(50, 40))
                assertEquals(ComicInk.black, bitmap.getPixel(80, 2))
                assertEquals(ComicInk.black, bitmap.getPixel(80, 97))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                paintwork.alpha = 128
                paintwork.draw(Canvas(bitmap))
                assertEquals(128, android.graphics.Color.alpha(bitmap.getPixel(50, 40)))
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                paintwork.alpha = 0
                paintwork.draw(Canvas(bitmap))
                assertEquals(0, bitmap.getPixel(50, 40))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun reducedMotionStopsRadarAndHeroAndPersistsAcrossRecreation() {
        val angle = SignalRadarView::class.java.getDeclaredField("sweepAngle").apply { isAccessible = true }
        val tick = LumiPetView::class.java.getDeclaredField("tick").apply { isAccessible = true }
        var before = 0f
        var petBefore = 0f
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var radar: SignalRadarView
            lateinit var pet: LumiPetView
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.tabRadarBtn).performClick()
                radar = activity.findViewById(R.id.radarView)
                ComicMotion.setEnabled(activity, false)
                val shell = MainActivity::class.java.getDeclaredField("eventUi")
                    .apply { isAccessible = true }.get(activity) as EventUiShell
                shell.refreshMotion()
                before = angle.getFloat(radar)
                val hero = EventUiShell::class.java.getDeclaredField("heroArt")
                    .apply { isAccessible = true }.get(shell) as ComicHeroView
                val animation = ComicHeroView::class.java.getDeclaredField("animator")
                    .apply { isAccessible = true }.get(hero) as? ValueAnimator
                assertFalse(animation?.isRunning == true)
                (EventUiShell::class.java.getDeclaredField("companionButton")
                    .apply { isAccessible = true }.get(shell) as Button).performClick()
                pet = MainActivity::class.java.getDeclaredField("wigglePetView")
                    .apply { isAccessible = true }.get(activity) as LumiPetView
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(pet.isShown)
                pet.triggerPokeBounce()
                petBefore = tick.getFloat(pet)
            }
            Thread.sleep(200)
            scenario.onActivity {
                assertEquals(before, angle.getFloat(radar), 0f)
                assertEquals(petBefore, tick.getFloat(pet), 0f)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(ComicMotion.enabled(activity))
                assertTrue(activity.findViewById<Button>(R.id.tabRadarBtn).isSelected)
                assertEquals(1f, activity.findViewById<Button>(R.id.tabRadarBtn).scaleX, 0f)
                ComicMotion.setEnabled(activity, true)
            }
        }
    }

    @Test
    fun heroCelebrationEndsAndUnchangedButtonsReusePaintwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var hero: ComicHeroView
            scenario.onActivity { activity ->
                val shell = MainActivity::class.java.getDeclaredField("eventUi")
                    .apply { isAccessible = true }.get(activity) as EventUiShell
                hero = EventUiShell::class.java.getDeclaredField("heroArt")
                    .apply { isAccessible = true }.get(shell) as ComicHeroView
                hero.celebrate(0)
                val button = activity.findViewById<Button>(R.id.filterAllBtn)
                ComicInk.style(button, AppTheme.KOHOLINT_TOYBOX.primaryAccent)
                val paintwork = button.background
                repeat(100) { ComicInk.style(button, AppTheme.KOHOLINT_TOYBOX.primaryAccent) }
                assertSame(paintwork, button.background)
            }
            Thread.sleep(950)
            scenario.onActivity {
                val animation = ComicHeroView::class.java.getDeclaredField("animator")
                    .apply { isAccessible = true }.get(hero) as? ValueAnimator
                assertFalse(animation?.isRunning == true)
            }
        }

    }

    @Test
    fun findingsCardGrowsInsteadOfClippingLargeText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.tabAnalyticsBtn).performClick()
                activity.findViewById<TextView>(R.id.intelTopFindingsText).text =
                    "Bands: 2.4G / 5G\nWeak/Open: 1\nClients: 0\nSkimmers: 0\nHandshakes: 0\nSurvey ready"
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val findings = activity.findViewById<TextView>(R.id.intelTopFindingsText)
                assertTrue("All findings text must fit",
                    findings.layout.height <= findings.height - findings.compoundPaddingTop - findings.compoundPaddingBottom)
            }
        }
    }
}
