package com.wigglefish.android

import android.content.Context
import android.graphics.Color

/** Stable preference names with original comic custom-paint palettes. */
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
        title = "TURBO POP / cyan + hot pink",
        background = Color.parseColor("#FFF5DE"),
        surface = Color.parseColor("#FFFAED"),
        cardBackground = Color.parseColor("#FFFEF8"),
        cardBorder = ComicInk.black,
        primaryAccent = Color.parseColor("#42DFE8"),
        secondaryAccent = Color.parseColor("#FF83B5"),
        tertiaryAccent = Color.parseColor("#FFD447"),
        textPrimary = ComicInk.black,
        textSecondary = ComicInk.muted
    ),
    MARIN_SUNSET(
        title = "CANDY DRIFT / lilac + mint",
        background = Color.parseColor("#F5EDFF"),
        surface = Color.parseColor("#FFF8FF"),
        cardBackground = Color.parseColor("#FFFAFF"),
        cardBorder = ComicInk.black,
        primaryAccent = Color.parseColor("#C5ABFF"),
        secondaryAccent = Color.parseColor("#66E3C2"),
        tertiaryAccent = Color.parseColor("#FFCE72"),
        textPrimary = ComicInk.black,
        textSecondary = ComicInk.muted
    ),
    WIND_FISH_DREAM(
        title = "REEF RACER / lime + blue",
        background = Color.parseColor("#F0F8DF"),
        surface = Color.parseColor("#FCFFEE"),
        cardBackground = Color.parseColor("#FDFFF7"),
        cardBorder = ComicInk.black,
        primaryAccent = Color.parseColor("#B6ED57"),
        secondaryAccent = Color.parseColor("#86C8FF"),
        tertiaryAccent = Color.parseColor("#FFCF66"),
        textPrimary = ComicInk.black,
        textSecondary = ComicInk.muted
    ),
    DUNGEON_GUARDIAN(
        title = "SUNSET STRIPE / orange + sky",
        background = Color.parseColor("#FFF0E5"),
        surface = Color.parseColor("#FFF9EF"),
        cardBackground = Color.parseColor("#FFFCF4"),
        cardBorder = ComicInk.black,
        primaryAccent = Color.parseColor("#FFB06B"),
        secondaryAccent = Color.parseColor("#80D8ED"),
        tertiaryAccent = Color.parseColor("#FFE26A"),
        textPrimary = ComicInk.black,
        textSecondary = ComicInk.muted
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
