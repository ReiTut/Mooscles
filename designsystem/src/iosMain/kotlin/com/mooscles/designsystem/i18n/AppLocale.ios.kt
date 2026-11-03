package com.mooscles.designsystem.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"
    private val defaults get() = NSUserDefaults.standardUserDefaults

    // Captured when the first override is applied and cleared when it is undone.
    private var overriding = false
    private var previousOverride: Any? = null
    private var systemTag: String? = null
    private val local = staticCompositionLocalOf { "" }

    private fun systemLanguage(): String = systemTag ?: (NSLocale.preferredLanguages.first() as String)

    actual val current: String
        @Composable get() = local.current.ifEmpty { systemLanguage() }

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (value == null) {
            restore()
            return local.provides(systemLanguage())
        }
        if (!overriding) {
            systemTag = NSLocale.preferredLanguages.first() as String
            previousOverride = defaults.objectForKey(LANGUAGES_KEY)
            overriding = true
        }
        defaults.setObject(listOf(value), LANGUAGES_KEY)
        return local.provides(value)
    }

    actual fun restore(): Boolean {
        if (!overriding) return false
        val previous = previousOverride
        if (previous == null) defaults.removeObjectForKey(LANGUAGES_KEY) else defaults.setObject(previous, LANGUAGES_KEY)
        overriding = false
        previousOverride = null
        systemTag = null
        return true
    }
}
