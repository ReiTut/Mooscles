package com.mooscles.designsystem.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Overrides the language the Compose resources resolve in. Only the hidden gallery and the
 * tests use it, following JetBrains' documented workaround
 * (kotlinlang.org/docs/multiplatform/compose-resource-environment.html). No component reads it.
 * [tag] is a BCP 47 language tag such as `pt-BR`; null restores the system language.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>

    /**
     * Puts the system language back and returns true when an override was undone (false, a
     * no-op, when none was applied). It does not rebuild anything: call it before navigating
     * away from the overriding screen, so the next screen composes in the system language.
     */
    fun restore(): Boolean
}

@Composable
fun ProvideAppLocale(tag: String?, content: @Composable () -> Unit) {
    // Keyed on Unit: a tag change must not undo the language just applied, only leaving does.
    // Leaving with the override still applied means content beside this one may already have
    // composed in the overridden language, so the theme rebuilds its tree (see LocaleEpoch).
    // Callers that restore before leaving (the gallery) never pay for that rebuild.
    DisposableEffect(Unit) { onDispose { if (LocalAppLocale.restore()) LocaleEpoch.bump() } }
    CompositionLocalProvider(LocalAppLocale provides tag) {
        // A new key rebuilds the content, so every string is read again in the new language.
        key(tag) { content() }
    }
}
