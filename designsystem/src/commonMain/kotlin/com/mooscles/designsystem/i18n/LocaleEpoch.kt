package com.mooscles.designsystem.i18n

import androidx.compose.runtime.mutableIntStateOf

/**
 * Counts how many times an applied language override was undone. The platform reads the default
 * locale at composition time, which is not observable, so nothing would recompose after a
 * restore. [MoosclesDesignTheme][com.mooscles.designsystem.theme.MoosclesDesignTheme] keys its
 * content on this value, so the tree re-resolves its strings. Only
 * [ProvideAppLocale] bumps it, when it leaves composition with its override still applied, so
 * normal app use never rebuilds. A screen inside a navigation host must restore the language
 * before it leaves (as the gallery does), because the rebuild would also reset the navigation
 * state remembered inside the theme.
 */
internal object LocaleEpoch {
    val value = mutableIntStateOf(0)

    fun bump() {
        value.intValue += 1
    }
}
