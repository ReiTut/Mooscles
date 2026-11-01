package com.mooscles.screens.format

import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek

/** The own CLDR table against the JVM's WeekFields. */
class WeekStartJvmEquivalenceTest {
    /** Regions where the JVM's CLDR version differs from the table, with the reason. */
    private val exceptions: Set<String> = emptySet()

    @Test
    fun everyLocaleWithACountryMatchesWeekFields() {
        val mismatches = mutableListOf<String>()
        for (locale in Locale.getAvailableLocales()) {
            if (locale.country.isEmpty() || locale.country in exceptions) continue
            val expected = DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
            val actual = WeekStart.of(locale.toLanguageTag())
            if (expected != actual) mismatches += "${locale.toLanguageTag()}: jvm=$expected table=$actual"
        }
        assertEquals(emptyList<String>(), mismatches)
    }
}
