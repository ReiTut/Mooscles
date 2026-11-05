package com.mooscles.screens.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.charts.BarChartTag
import com.mooscles.designsystem.charts.CalendarCollapseTag
import com.mooscles.designsystem.charts.ChartDataButtonTag
import com.mooscles.designsystem.charts.calendarDayTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertAtLeast
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.flow.await
import com.mooscles.screens.flow.exists
import com.mooscles.screens.history.HistoryContent
import com.mooscles.screens.history.HistoryScreenTag
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.share.SharePreviewContent
import com.mooscles.screens.share.SharePreviewDestination
import com.mooscles.screens.share.SharePreviewState
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.withScreen
import com.mooscles.screens.workoutdetail.WorkoutDetailRoute
import com.mooscles.screens.workoutdetail.WorkoutDetailScreenState
import com.mooscles.screens.workoutdetail.WorkoutDetailTags
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Focused Phase5B accessibility additions. Existing sweeps cover charts, inputs and the shell.
 * These checks exercise their new History/detail/preview composition at default and narrow2x sizes.
 * They check semantics/layout, not actual TalkBack or VoiceOver speech.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryAccessibilityTest {
    @AfterTest fun restoreLanguage() { LocalAppLocale.restore() }

    private data class Look(val locale: String, val dark: Boolean, val scale: Float, val size: DpSize)
    private val looks = listOf(
        Look("en", false, 1f, DpSize(412.dp, 800.dp)),
        Look("en", true, 1f, DpSize(412.dp, 800.dp)),
        Look("pt-BR", false, LINEAR_TWO_X_FONT_SCALE, DpSize(320.dp, 800.dp)),
        Look("pt-BR", true, LINEAR_TWO_X_FONT_SCALE, DpSize(320.dp, 800.dp)),
    )
    private val utc = TimeZone.UTC
    private val at = LocalDateTime(2026, 11, 3, 10, 0).toInstant(utc).toEpochMilliseconds()
    private val exercise = ExerciseUi("custom", "Remada articulada com apoio e pegada neutra", "Back", "Dumbbell", "Per hand", custom = true)
    private val workout = WorkoutUi("saved", "Meu treino completo de terça-feira", at, at + 3_600_000,
        listOf(WorkoutExerciseUi("entry", exercise, listOf(SetUi("set", 8, 22.5, true)))),
        listOf(RecordUi(exercise, 22.5, 8, workoutId = "saved", date = at + 3_600_000)))
    private val state = UiState(loading = false, exercises = listOf(exercise), history = listOf(workout))
    private val seams = ProgressSeams({ at + 86_400_000 }, { utc }, { DayOfWeek.SUNDAY })

    @Test fun calendarFiltersAndCardsRetainLabelsTargetsAndWholeText() {
        for (look in looks) runComposeUiTest {
            withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                HistoryContent(state, RecordingActions(), {}, {}, {}, {}, seams = seams)
            }
            await("the marked calendar day is ready") { exists(hasTestTag(calendarDayTag(3))) }
            historyScroll(calendarDayTag(3))
            val marked = onNodeWithTag(calendarDayTag(3)).assertIsDisplayed().fetchSemanticsNode()
            assertEquals(Role.Button, marked.config.getOrNull(SemanticsProperties.Role))
            assertTrue(spokenLabel(marked).contains(workout.name), "The calendar must say its workout name")
            // The approved calendar exception is40dp; ordinary controls remain48dp.
            assertAtLeast(marked, 40f, 40f, "calendar ${look.locale} ${look.scale}")
            onNodeWithTag(CalendarCollapseTag).performClick(); waitForIdle()
            historyScroll("history-filter-pr")
            assertClickablesLabeledAndSized("history filters $look")
            assertButtonTextsWhole("history filters $look")
            historyScroll("history-workout-saved")
            val card = onNodeWithTag("history-workout-saved").assertIsDisplayed().fetchSemanticsNode()
            assertTrue(spokenLabel(card).contains(workout.name))
            assertTrue(spokenLabel(card).contains("1"), "The spoken card must retain its workout statistics")
            assertClickablesLabeledAndSized("history card $look")
            assertButtonTextsWhole("history card $look")
        }
    }

    @Test fun overallProgressChartsSpeakAndExposeAnAccessibleDataTable() {
        for (look in looks) runComposeUiTest {
            withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                HistoryContent(state, RecordingActions(), {}, {}, {}, {}, seams = seams)
            }
            onNodeWithTag("history-segment-progress").performClick()
            await("the period totals are ready") { exists(hasTestTag("history-period-stats")) }
            progressScroll("history-period-stats")
            assertNumbersWhole(atLeast = 4)
            assertClickablesLabeledAndSized("overall totals $look")
            progressScroll("history-chart-workouts")
            val chart = onNode(hasTestTag(BarChartTag) and hasAnyAncestor(hasTestTag("history-chart-workouts"))).fetchSemanticsNode()
            assertEquals(Role.Image, chart.config.getOrNull(SemanticsProperties.Role))
            assertTrue(spokenLabel(chart).isNotBlank(), "The overall chart needs a localized summary")
            val dataButton = hasTestTag(ChartDataButtonTag) and hasAnyAncestor(hasTestTag("history-chart-workouts"))
            onNodeWithTag("history-progress-list").performScrollToNode(dataButton)
            onNode(dataButton).performClick(); waitForIdle()
            // The table has one row per weekly bucket; finding any actual row avoids a screenshot-only assertion.
            val rows = onAllNodes(SemanticsMatcher("history data row") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("chart-table-row-history-false-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(rows.isNotEmpty(), "View data must expose the actual weekly values")
            assertClickablesLabeledAndSized("overall data $look")
            assertButtonTextsWhole("overall data $look")
        }
    }

    @Test fun savedDetailAndPreviewKeepStatisticsAndSharingActionsAccessible() {
        for (look in looks) {
            runComposeUiTest {
                withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                    WorkoutDetailRoute("saved", state, RecordingActions(), {}, {}, {}, {}, screen = WorkoutDetailScreenState())
                }
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.PRS))
                val prs = onNodeWithTag(WorkoutDetailTags.PRS).fetchSemanticsNode()
                assertTrue(spokenLabel(prs).contains("1"))
                assertNumbersWhole(atLeast = 2)
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag("share-workout"))
                onNodeWithTag("share-workout").assertIsDisplayed()
                assertClickablesLabeledAndSized("saved detail $look")
                assertButtonTextsWhole("saved detail $look")
            }
            runComposeUiTest {
                withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                    SharePreviewContent(SharePreviewState.Ready(2, 1), setOf(SharePreviewDestination.WhatsApp), false, {}, {}, {},
                        page = { index, _, ready, _ ->
                            SideEffect { ready() }
                            Box(Modifier.fillMaxWidth().height(100.dp).testTag("a11y-preview-$index"))
                        })
                }
                waitForIdle()
                onNodeWithTag("a11y-preview-0").assertIsDisplayed()
                onNodeWithTag("share-other-apps").performScrollTo().assertIsDisplayed()
                assertClickablesLabeledAndSized("sharing preview $look")
                assertButtonTextsWhole("sharing preview $look")
            }
        }
    }

    private fun ComposeUiTest.historyScroll(tag: String) {
        onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
    private fun ComposeUiTest.progressScroll(tag: String) {
        onNodeWithTag("history-progress-list").performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
}
