package com.mooscles.backup

import com.mooscles.domain.RestDuration

/**
 * Allowed preference values, the single source for the settings setters and the backup import. Pure
 * Kotlin, so the import can refuse a bad file before its transaction writes anything.
 */
object PreferenceRules {
    val THEMES = listOf("light", "dark", "system")
    val REST_SECONDS = 0..RestDuration.MAX_SECONDS

    fun validTheme(value: String) = value in THEMES
    fun validRest(seconds: Int) = seconds in REST_SECONDS
}
