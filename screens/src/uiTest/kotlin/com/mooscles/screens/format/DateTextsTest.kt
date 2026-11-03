package com.mooscles.screens.format

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DateTextsTest {
    private val utc = TimeZone.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun shortDateBothLanguages() {
        val ms = at(2026, 10, 21, 9, 0)
        for ((locale, expected) in listOf("en" to "Oct 21", "pt-BR" to "21 out")) {
            runComposeUiTest {
                var text = ""
                withScreen(locale = locale) { text = shortDate(ms, utc) }
                waitForIdle()
                assertEquals(expected, text, locale)
            }
            LocalAppLocale.restore()
        }
    }

    // 2024-10-29 is a Tuesday (the plan's example date).
    @Test
    fun summaryRangePortuguese() = runComposeUiTest {
        var text = ""
        withScreen(locale = "pt-BR") { text = summaryDateRange(at(2024, 10, 29, 18, 2), at(2024, 10, 29, 18, 44), utc) }
        waitForIdle()
        assertEquals("ter., 29 out · 18:02–18:44", text)
    }

    @Test
    fun summaryRangeEnglish() = runComposeUiTest {
        var text = ""
        withScreen(locale = "en") { text = summaryDateRange(at(2024, 10, 29, 18, 2), at(2024, 10, 29, 18, 44), utc) }
        waitForIdle()
        assertEquals("Tue, Oct 29 · 18:02–18:44", text)
    }

    @Test
    fun durations() = runComposeUiTest {
        val texts = mutableListOf<String>()
        withScreen(locale = "en") { texts += durationText(2520); texts += durationText(4320) }
        waitForIdle()
        assertEquals(listOf("42 min", "1 h 12 min"), texts)
    }

    @Test
    fun wholeHoursAndSubMinute() = runComposeUiTest {
        val texts = mutableListOf<String>()
        withScreen(locale = "en") {
            texts += durationText(40); texts += durationText(3600); texts += durationText(7200); texts += durationText(3660)
        }
        waitForIdle()
        assertEquals(listOf("< 1 min", "1 h", "2 h", "1 h 1 min"), texts)
    }
}
