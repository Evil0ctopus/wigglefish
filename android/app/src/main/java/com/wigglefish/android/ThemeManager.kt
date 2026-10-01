package com.wigglefish.android

import android.content.Context
import android.graphics.Color

/** Visual palettes based on Wigglefish's dark metal, teal, amethyst, and copper identity. */
enum class AppTheme(
    val title: String,
    val background: Int,
    val surface: Int,
    val cardBackground: Int,
    val cardBorder: Int,
    val primaryAccent: Int,
    val secondaryAccent: Int,
    val tertiaryAccent: Int,
    val textPrimary: Int,
    val textSecondary: Int
) {
    KOHOLINT_TOYBOX(
        title = "🐙 EVIL OCTOPUS",
        background = Color.parseColor("#080A0F"),
        surface = Color.parseColor("#11151D"),
        cardBackground = Color.parseColor("#191D27"),
        cardBorder = Color.parseColor("#3B5360"),
        primaryAccent = Color.parseColor("#40E8D0"),
        secondaryAccent = Color.parseColor("#C77BDB"),
        tertiaryAccent = Color.parseColor("#D39A72"),
        textPrimary = Color.parseColor("#F3F1F7"),
        textSecondary = Color.parseColor("#A9B1BF")
    ),
    MARIN_SUNSET(
        title = "💜 AMETHYST CIRCUIT",
        background = Color.parseColor("#0D0A12"),
        surface = Color.parseColor("#19131F"),
        cardBackground = Color.parseColor("#211A2A"),
        cardBorder = Color.parseColor("#654C72"),
        primaryAccent = Color.parseColor("#D079E4"),
        secondaryAccent = Color.parseColor("#40D6C2"),
        tertiaryAccent = Color.parseColor("#D8A37A"),
        textPrimary = Color.parseColor("#F6F0F8"),
        textSecondary = Color.parseColor("#BEB0C4")
    ),
    WIND_FISH_DREAM(
        title = "🟢 TEAL SIGNAL",
        background = Color.parseColor("#070D10"),
        surface = Color.parseColor("#101B1D"),
        cardBackground = Color.parseColor("#172426"),
        cardBorder = Color.parseColor("#356C67"),
        primaryAccent = Color.parseColor("#53E4CE"),
        secondaryAccent = Color.parseColor("#B870D0"),
        tertiaryAccent = Color.parseColor("#D4A26F"),
        textPrimary = Color.parseColor("#F0F6F5"),
        textSecondary = Color.parseColor("#A7BFBC")
    ),
    DUNGEON_GUARDIAN(
        title = "🟤 COPPER CORE",
        background = Color.parseColor("#0D0B0A"),
        surface = Color.parseColor("#1B1714"),
        cardBackground = Color.parseColor("#25201C"),
        cardBorder = Color.parseColor("#795B46"),
        primaryAccent = Color.parseColor("#D7A078"),
        secondaryAccent = Color.parseColor("#56D9C3"),
        tertiaryAccent = Color.parseColor("#C276D4"),
        textPrimary = Color.parseColor("#F4F0EC"),
        textSecondary = Color.parseColor("#B8AAA0")
    );

    companion object {
        private const val PREF_KEY_THEME = "wigglefish_current_theme"

        fun getSavedTheme(context: Context): AppTheme {
            val prefs = context.getSharedPreferences("wigglefish_prefs", Context.MODE_PRIVATE)
            val name = prefs.getString(PREF_KEY_THEME, KOHOLINT_TOYBOX.name)
            return try {
                valueOf(name ?: KOHOLINT_TOYBOX.name)
            } catch (_: Exception) {
                KOHOLINT_TOYBOX
            }
        }

        fun saveTheme(context: Context, theme: AppTheme) {
            val prefs = context.getSharedPreferences("wigglefish_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_KEY_THEME, theme.name).apply()
        }
    }
}

