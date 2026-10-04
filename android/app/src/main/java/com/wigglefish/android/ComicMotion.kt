package com.wigglefish.android

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.OvershootInterpolator

object ComicMotion {
    private const val KEY = "comic_motion"
    fun requested(context: Context): Boolean =
        context.getSharedPreferences("wigglefish_prefs", Context.MODE_PRIVATE)
            .getBoolean(KEY, true)

    fun enabled(context: Context): Boolean = requested(context) && ValueAnimator.areAnimatorsEnabled()

    fun disabledReason(context: Context): String? = when {
        !requested(context) -> "REDUCED MOTION / PAINT > MOTION"
        !ValueAnimator.areAnimatorsEnabled() -> "ANDROID ANIMATIONS OFF"
        else -> null
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("wigglefish_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }

    fun enter(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.translationY = 0f
        if (!enabled(view.context) || !view.isShown) return
        view.alpha = 0.4f
        view.translationY = 10f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(OvershootInterpolator(0.7f)).start()
    }

    fun pop(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        if (!enabled(view.context) || !view.isShown) return
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.animate().scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }
}
