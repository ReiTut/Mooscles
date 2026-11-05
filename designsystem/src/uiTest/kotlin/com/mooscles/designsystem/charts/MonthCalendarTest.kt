package com.mooscles.designsystem.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.INJECTS_DRAG
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.saveChartsCapture
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 5B spike S2: useful day buttons, four/six-week grids, and whole dates at the narrow/large-text limit. */
@OptIn(ExperimentalTestApi::class)
class MonthCalendarTest {
    private val labels = listOf("S", "M", "T", "W", "T", "F", "S")
    private val marks = listOf(CalendarDayMark(1, 1, "October 1, one workout: Upper"), CalendarDayMark(12, 2, "October 12, two workouts"))
    private val summary = "October 2026: three workouts, on days 1 and 12"

    @AfterTest
    fun restoreLanguage() { LocalAppLocale.restore() }

    @Test
    fun onlyWorkoutDaysAreButtonsAndExposeFullLabels() = runComposeUiTest {
        var tapped = 0
        withTheme {
            MonthCalendar("October 2026 · 3 workouts", summary, labels, 31, 4, marks, 3, 12, { tapped = it }, {}, {}, false, {})
        }
        waitForIdle()
        onNodeWithTag(CalendarSummaryTag).assertContentDescriptionEquals(summary)
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(5)
        onNodeWithTag(calendarDayTag(12)).assertContentDescriptionEquals(marks[1].label).performClick()
        assertEquals(12, tapped)
        val emptyDay = onNodeWithTag(calendarDayTag(3), useUnmergedTree = true).fetchSemanticsNode().config
        assertNull(emptyDay.getOrNull(SemanticsActions.OnClick))
        assertNull(emptyDay.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(true, onNodeWithTag(calendarDayTag(12)).fetchSemanticsNode().config[SemanticsProperties.Selected])
    }

    @Test
    fun collapseRetainsSummaryAndCanExpandWithoutChangingTheDay() = runComposeUiTest {
        var collapsed by mutableStateOf(false)
        var tapped = 0
        withTheme {
            MonthCalendar("October 2026", summary, labels, 31, 4, marks, null, 12, { tapped = it }, {}, {},
                collapsed, { collapsed = !collapsed })
        }
        waitForIdle()
        onNodeWithTag(CalendarCollapseTag).performClick()
        onNodeWithTag(calendarDayTag(12)).assertDoesNotExist()
        onNodeWithTag(CalendarSummaryTag).assertContentDescriptionEquals(summary)
        onNodeWithTag(CalendarCollapseTag).performClick()
        onNodeWithTag(calendarDayTag(12)).assertExists()
        assertEquals(0, tapped)
    }

    @Test
    fun boundaryMonthsHideUnavailableNavigation() = runComposeUiTest {
        withTheme {
            MonthCalendar("October 2026", summary, labels, 31, 4, marks, null, null, {}, null, null, false, {})
        }
        waitForIdle()
        onNodeWithTag(CalendarPreviousTag).assertDoesNotExist()
        onNodeWithTag(CalendarNextTag).assertDoesNotExist()
        onNodeWithTag(CalendarCollapseTag).assertExists()
    }

    @Test
    fun monthControlsAreLocalizedAndInvokeTheRightDirection() {
        for ((locale, previous, next) in listOf(Triple("en", "Previous month", "Next month"), Triple("pt-BR", "Mês anterior", "Próximo mês"))) {
            runComposeUiTest {
                var position = 0
                withTheme {
                    ProvideAppLocale(locale) {
                        MonthCalendar("October 2026", summary, labels, 31, 4, marks, null, null, {}, { position-- }, { position++ }, false, {})
                    }
                }
                waitForIdle()
                onNodeWithTag(CalendarPreviousTag).assertContentDescriptionEquals(previous).performClick()
                assertEquals(-1, position)
                onNodeWithTag(CalendarNextTag).assertContentDescriptionEquals(next).performClick()
                assertEquals(0, position)
            }
        }
    }

    @Test
    fun horizontalGestureChangesOnlyOneMonthAndButtonsRemainTheAlternative() = runComposeUiTest {
        var position = 0
        withTheme(reducedMotion = true) {
            MonthCalendar("October 2026", summary, labels, 31, 4, marks, null, null, {}, { position-- }, { position++ }, false, {})
        }
        waitForIdle()
        onNodeWithTag(CalendarNextTag).performClick()
        assertEquals(1, position)
        if (INJECTS_DRAG) {
            onNodeWithTag(MonthCalendarTag).performTouchInput {
                swipe(Offset(width * 0.9f, height * 0.6f), Offset(width * 0.1f, height * 0.6f))
            }
            assertEquals(2, position)
            onNodeWithTag(MonthCalendarTag).performTouchInput {
                swipe(Offset(width * 0.1f, height * 0.6f), Offset(width * 0.9f, height * 0.6f))
            }
            assertEquals(1, position)
        }
    }

    @Test
    fun visualOnlyFallbackKeepsSummaryButAddsNoDayActions() = runComposeUiTest {
        withTheme {
            MonthCalendar("October 2026", summary, labels, 31, 4, marks, null, null, {}, {}, {}, false, {}, interactive = false)
        }
        waitForIdle()
        onNodeWithTag(CalendarSummaryTag).assertContentDescriptionEquals(summary)
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(3)
        assertNull(onNodeWithTag(calendarDayTag(12), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun fourAndSixWeekMonthsFitBothThemesAndLargeFontsAt320And412() {
        for (dark in listOf(false, true)) for (scale in listOf(1f, LINEAR_TWO_X_FONT_SCALE)) {
            for (width in listOf(320, 412)) for ((days, offset) in listOf(28 to 0, 31 to 6)) {
                runComposeUiTest {
                    var widestNumber = 0
                    var numberHeight = 0
                    var minCell = 0f
                    withTheme(dark = dark, fontScale = scale) {
                        ProvideAppLocale(if (dark) "pt-BR" else "en") {
                            val measurer = rememberTextMeasurer()
                            val style = MTheme.type.numSm.copy(fontWeight = FontWeight.SemiBold)
                            val sizes = (1..days).map { measurer.measure(it.toString(), style) }
                            widestNumber = sizes.maxOf { it.size.width }
                            numberHeight = sizes.maxOf { it.size.height }
                            minCell = with(LocalDensity.current) { MTheme.sizes.calendarCellMin.toPx() }
                            Box(Modifier.width(width.dp).padding(horizontal = 16.dp)) {
                                MonthCalendar(if (dark) "Outubro de 2026 · 3 treinos" else "October 2026 · 3 workouts",
                                    summary, labels, days, offset, marks, 3, 12, {}, {}, {}, false, {})
                            }
                        }
                    }
                    waitForIdle()
                    for (day in 1..days) {
                        val bounds = onNodeWithTag(calendarDayTag(day), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.width + 1 >= minCell, "$width/$scale/day$day target ${bounds.width} < $minCell")
                        assertTrue(bounds.height + 1 >= minCell, "$width/$scale/day$day short target")
                        assertTrue(bounds.width >= widestNumber, "$width/$scale clips a number: ${bounds.width} < $widestNumber")
                        assertTrue(bounds.height >= numberHeight, "$width/$scale clips number height")
                    }
                    val name = "calendar-${if (dark) "dark" else "light"}-$scale-$width-$days"
                    val image = try { onRoot().captureToImage() } catch (unsupported: NotImplementedError) {
                        println("S2 capture unavailable: $name: ${unsupported.message}")
                        null
                    }
                    if (image != null) saveChartsCapture(name, image)
                }
            }
        }
    }
}
