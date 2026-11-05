package com.mooscles.screens.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.charts.BarChartTag
import com.mooscles.designsystem.charts.CalendarCollapseTag
import com.mooscles.designsystem.charts.ChartDataButtonTag
import com.mooscles.designsystem.charts.calendarDayTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertAtLeast
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.assertWorkoutTargets
import com.mooscles.screens.flow.await
import com.mooscles.screens.flow.capture
import com.mooscles.screens.flow.exists
import com.mooscles.screens.history.HistoryContent
import com.mooscles.screens.history.HistoryScreenTag
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.share.SharePreviewContent
import com.mooscles.screens.share.SharePreviewDestination
import com.mooscles.screens.share.SharePreviewState
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.workoutSetRowTag
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
 * Every new screen is checked in both themes and languages, at320/412dp and1/1.3/2/2.01font scales.
 * History and progress visit top/middle/end; saved detail covers reading and editing, including the
 * keyboard focus/IME action path. The iOS simulator does not display its real keyboard: these tests
 * prove focus and IME semantics, not physical keyboard geometry or screen-reader speech.
 * They check semantics/layout, not actual TalkBack or VoiceOver speech.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryAccessibilityTest {
    @AfterTest fun restoreLanguage() { LocalAppLocale.restore() }

    private data class Look(val locale: String, val dark: Boolean, val scale: Float, val size: DpSize)
    private val looks = listOf("en", "pt-BR").flatMap { locale ->
        listOf(false, true).flatMap { dark ->
            listOf(1f, 1.3f, 2f, LINEAR_TWO_X_FONT_SCALE).flatMap { scale ->
                listOf(320, 412).map { width -> Look(locale, dark, scale, DpSize(width.dp, 800.dp)) }
            }
        }
    }
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
            val unmarked = onNodeWithTag(calendarDayTag(2)).fetchSemanticsNode()
            assertTrue(unmarked.config.getOrNull(SemanticsProperties.Role) == null,
                "Unmarked calendar days must not become screen-reader buttons")
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
            progressScroll("history-frequent-${exercise.id}")
            onNodeWithTag("history-frequent-${exercise.id}").assertIsDisplayed()
            assertClickablesLabeledAndSized("overall end $look")
            assertButtonTextsWhole("overall end $look")
        }
    }

    @Test fun savedDetailAndPreviewKeepStatisticsAndSharingActionsAccessible() {
        for (look in looks) {
            println("HISTORY_A11Y saved editor/preview start: $look")
            runComposeUiTest {
                val screen = WorkoutDetailScreenState()
                val actions = RecordingActions()
                withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                    WorkoutDetailRoute("saved", state, actions, {}, {}, {}, {}, screen = screen)
                }
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.PRS))
                val prs = onNodeWithTag(WorkoutDetailTags.PRS).fetchSemanticsNode()
                assertTrue(spokenLabel(prs).contains("1"))
                assertNumbersWhole(atLeast = 2)
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.readingSet("set")))
                onNodeWithTag(WorkoutDetailTags.readingSet("set")).assertIsDisplayed()
                assertClickablesLabeledAndSized("saved reading middle $look")
                assertButtonTextsWhole("saved reading middle $look")
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag("share-workout"))
                onNodeWithTag("share-workout").assertIsDisplayed()
                assertClickablesLabeledAndSized("saved detail $look")
                assertButtonTextsWhole("saved detail $look")

                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.EDIT))
                onNodeWithTag(WorkoutDetailTags.EDIT).performClick()
                onNodeWithTag("dialog-confirm").performClick(); waitForIdle()
                onNodeWithTag(WorkoutDetailTags.DONE).assertIsDisplayed()
                // A wrapping title must not consume the entire screen above the lazy editor.
                assertAtLeast(onNodeWithTag(WorkoutDetailTags.LIST).fetchSemanticsNode(), 0f, 56f,
                    "saved editing viewport $look")
                assertClickablesLabeledAndSized("saved editing top $look")
                assertButtonTextsWhole("saved editing top $look")
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(workoutSetRowTag("set")))
                val load = hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and
                    hasAnyAncestor(hasTestTag(workoutSetRowTag("set")))
                val reps = hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowRepsTag)) and
                    hasAnyAncestor(hasTestTag(workoutSetRowTag("set")))
                val check = hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("set")))
                assertTrue(spokenLabel(onNode(check).fetchSemanticsNode()).isNotBlank())
                onNode(load, useUnmergedTree = true).performClick(); waitForIdle()
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(StepperIncreaseTag))
                onNodeWithTag(StepperIncreaseTag).assertIsDisplayed()
                assertClickablesLabeledAndSized("saved editing stepper $look")
                assertWorkoutTargets("saved editing stepper $look")
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(workoutSetRowTag("set")))
                onNode(load, useUnmergedTree = true).performClick(); waitForIdle()
                onNode(load, useUnmergedTree = true).assertIsFocused().assertIsDisplayed()
                assertTrue(spokenLabel(onNode(load, useUnmergedTree = true).fetchSemanticsNode()).isNotBlank())
                onNode(load, useUnmergedTree = true).performTextReplacement("22,"); waitForIdle()
                onNode(load, useUnmergedTree = true).assertTextEquals("22,")
                // Next reaches the same row's repetitions; Done releases focus instead of leaving a stale target.
                onNode(load, useUnmergedTree = true).performImeAction(); waitForIdle()
                onNode(reps, useUnmergedTree = true).assertIsFocused()
                if (look.locale == "pt-BR" && look.size.width == 320.dp && look.scale == LINEAR_TWO_X_FONT_SCALE) {
                    capture("phase5b-saved-editor-pt-320-2x-${if (look.dark) "dark" else "light"}")
                }
                onNode(reps, useUnmergedTree = true).performImeAction(); waitForIdle()
                onNode(reps, useUnmergedTree = true).assertIsNotFocused()
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.ADD_EXERCISE))
                onNodeWithTag(WorkoutDetailTags.ADD_EXERCISE).assertIsDisplayed()
                assertClickablesLabeledAndSized("saved editing end $look")
                assertButtonTextsWhole("saved editing end $look")
                onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(workoutSetRowTag("set")))
                onNode(load, useUnmergedTree = true).assertTextEquals("22,")
                assertEquals(22.5, state.history.single().exercises.single().sets.single().weightKg)
                assertTrue(actions.calls.none { it.startsWith("updateSet:") || it.startsWith("startTimer:") },
                    "The invalid draft must not overwrite saved values or start a timer")
                onNodeWithTag(WorkoutDetailTags.DONE).performClick(); waitForIdle()
                onNodeWithTag(WorkoutDetailTags.DONE).assertDoesNotExist()
            }
            runComposeUiTest {
                withScreen(locale = look.locale, dark = look.dark, fontScale = look.scale, size = look.size, reducedMotion = true) {
                    SharePreviewContent(SharePreviewState.Ready(2, 1), setOf(SharePreviewDestination.WhatsApp), false, {}, {}, {},
                        page = { index, description, ready, _ ->
                            SideEffect { ready() }
                            Box(Modifier.fillMaxWidth().height(100.dp).testTag("a11y-preview-$index")
                                .semantics { contentDescription = description })
                        })
                }
                waitForIdle()
                onNodeWithTag("a11y-preview-0").assertIsDisplayed()
                onNodeWithTag("share-preview-pages").performScrollToIndex(1)
                onNodeWithTag("a11y-preview-1").assertIsDisplayed()
                assertTrue(spokenLabel(onNodeWithTag("a11y-preview-1").fetchSemanticsNode()).contains("1"),
                    "Each preview page must retain the workout-wide spoken PR total")
                onNodeWithTag("share-whatsapp").performScrollTo().assertIsDisplayed()
                onNodeWithTag("share-other-apps").performScrollTo().assertIsDisplayed()
                assertClickablesLabeledAndSized("sharing preview $look")
                assertButtonTextsWhole("sharing preview $look")
            }
            println("HISTORY_A11Y saved editor/preview passed: $look")
        }
    }

    private fun ComposeUiTest.historyScroll(tag: String) {
        onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
    private fun ComposeUiTest.progressScroll(tag: String) {
        onNodeWithTag("history-progress-list").performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
}
