package com.mooscles.screens.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.charts.calendarDayTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.tabTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.flow.BENCH_PRESS
import com.mooscles.screens.flow.FlowHarness
import com.mooscles.screens.flow.FlowViewModel
import com.mooscles.screens.flow.await
import com.mooscles.screens.flow.exists
import com.mooscles.screens.history.HistoryContent
import com.mooscles.screens.history.HistoryScreenTag
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.share.SharePreviewContent
import com.mooscles.screens.share.SharePreviewState
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.screens.workoutdetail.WorkoutDetailRoute
import com.mooscles.screens.workoutdetail.WorkoutDetailTags
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Phase 5B's actual touch paths over Room and the common view model, including the shell tab tap.
 * Scrolling is setup, as in TapBudgetTest. A share stops at the preview: no recipient/app is opened.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryTapBudgetTest {
    @AfterTest fun restoreLanguage() { LocalAppLocale.restore() }

    private class Touches(private val test: ComposeUiTest) {
        var count = 0
        fun tap(node: SemanticsNodeInteraction) { node.performClick(); count++; test.waitForIdle() }
        fun within(name: String, budget: Int) {
            println("TAP budget $name: $count (phase 5B $budget)")
            assertTrue(count <= budget, "$name needs $count taps; budget $budget")
        }
    }

    @Test fun historyCalendarRecordFilterAndProgressStayWithinTheirBudgets() = budget { vm, screen, selected, seed ->
        var taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        historyScroll("history-workout-${seed.workout}")
        taps.tap(onNodeWithTag("history-workout-${seed.workout}"))
        await("the latest workout opens") { selected.value == seed.workout && screen.value == DETAIL }
        taps.within("openLatestWorkout", 2)

        runOnIdle { screen.value = ShellTabs.TRAIN }
        taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        await("calendar day 3 loads") { exists(hasTestTag(calendarDayTag(3))) }
        historyScroll(calendarDayTag(3)); taps.tap(onNodeWithTag(calendarDayTag(3)))
        await("the calendar opens its only workout") { selected.value == seed.workout && screen.value == DETAIL }
        taps.within("openSingleWorkoutDay", 2)

        runOnIdle { screen.value = ShellTabs.TRAIN }
        taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        historyScroll("history-filter-pr"); onNodeWithTag("history-filter-pr").performScrollTo().assertIsDisplayed()
        taps.tap(onNodeWithTag("history-filter-pr"))
        onNodeWithTag("history-filter-pr").assertIsOn()
        taps.within("showOnlyBeatenRecords", 2)

        runOnIdle { screen.value = ShellTabs.TRAIN }
        taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        taps.tap(onNodeWithTag("history-segment-progress"))
        await("overall progress is usable") { exists(hasTestTag("history-period-stats")) }
        taps.within("viewWeeklyVolume", 2)
        assertEquals(3, vm.contentUi.value.history.size)
    }

    @Test fun savedSetEditingSharingAndDeletionStayWithinTheirBudgets() = budget { vm, screen, selected, seed ->
        var taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        historyScroll("history-workout-${seed.workout}"); taps.tap(onNodeWithTag("history-workout-${seed.workout}"))
        detailScroll(WorkoutDetailTags.EDIT); taps.tap(onNodeWithTag(WorkoutDetailTags.EDIT))
        taps.tap(onNodeWithTag("dialog-confirm"))
        await("saved editing opens") { exists(hasTestTag(WorkoutDetailTags.DONE)) }
        detailScroll(workoutSetRowTag(seed.set))
        taps.tap(onNode(hasTestTag(SetRowLoadTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag(seed.set))), useUnmergedTree = true))
        onNodeWithTag("stepper-value").assertExists()
        taps.within("editSavedSet", 5)

        runOnIdle { screen.value = ShellTabs.TRAIN }
        taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        historyScroll("history-workout-${seed.workout}"); taps.tap(onNodeWithTag("history-workout-${seed.workout}"))
        detailScroll("share-workout"); taps.tap(onNodeWithTag("share-workout"))
        onNodeWithTag("share-preview-close").assertIsDisplayed()
        taps.within("shareSavedWorkout", 3)
        // Closing is outside the measured share flow; no destination has been selected.
        onNodeWithTag("share-preview-close").performClick()
        runOnIdle { screen.value = ShellTabs.TRAIN }
        taps = Touches(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.HISTORY)))
        historyScroll("history-workout-${seed.workout}"); taps.tap(onNodeWithTag("history-workout-${seed.workout}"))
        detailScroll(WorkoutDetailTags.DELETE); taps.tap(onNodeWithTag(WorkoutDetailTags.DELETE))
        taps.tap(onNodeWithTag("dialog-confirm-danger"))
        await("the saved workout is deleted") { vm.contentUi.value.history.none { it.id == seed.workout } }
        await("deletion returns to History") { screen.value == ShellTabs.HISTORY }
        taps.within("deleteSavedWorkout", 4)
        assertEquals(2, vm.contentUi.value.history.size)
        assertEquals(seed.workout, selected.value)
    }

    private class Seed(val workout: String, val set: String)
    private fun budget(flow: suspend ComposeUiTest.(FlowViewModel, MutableState<String>, MutableState<String?>, Seed) -> Unit) =
        runComposeUiTest(testTimeout = 3.minutes) {
            val harness = FlowHarness()
            try {
                val repo = harness.repository
                repo.initialize()
                var seed: Seed? = null
                for (day in 1..3) {
                    val start = LocalDateTime(2026, 11, day, 10, 0).toInstant(UTC).toEpochMilliseconds()
                    val id = repo.startEmpty("Workout $day", now = start)
                    repo.addWorkoutExercise(id, BENCH_PRESS)
                    val set = repo.workoutSets(id).first()
                    repo.updateSet(set.copy(reps = 6, weightKg = 20.0 + day, completed = true))
                    check(repo.finishWorkout(id, now = start + 3_600_000))
                    seed = Seed(id, set.id)
                }
                val vm = harness.createViewModel()
                val screen = mutableStateOf(ShellTabs.TRAIN)
                val selected = mutableStateOf<String?>(null)
                withScreen(locale = "en", reducedMotion = true) { BudgetApp(vm, screen, selected) }
                await("the synthetic history loads") { !vm.contentUi.value.loading && vm.contentUi.value.history.size == 3 }
                flow(vm, screen, selected, requireNotNull(seed))
            } finally { runOnUiThread { harness.close() } }
        }

    private fun ComposeUiTest.historyScroll(tag: String) {
        await("History has its list") { exists(hasTestTag(HistoryScreenTag)) }
        await("History has $tag") {
            runCatching { onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag(tag)) }.isSuccess && exists(hasTestTag(tag))
        }
        waitForIdle()
    }
    private fun ComposeUiTest.detailScroll(tag: String) {
        await("the detail has its list") { exists(hasTestTag(WorkoutDetailTags.LIST)) }
        onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(tag)); waitForIdle()
    }

    @Composable private fun BudgetApp(vm: FlowViewModel, screen: MutableState<String>, selected: MutableState<String?>) {
        val ui by vm.contentUi.collectAsState()
        val timer = vm.timerUi.collectAsState()
        val share = androidx.compose.runtime.remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (screen.value) {
                    ShellTabs.HISTORY -> HistoryContent(ui, vm, { selected.value = it; screen.value = DETAIL }, {}, {}, {}, seams = SEAMS)
                    DETAIL -> WorkoutDetailRoute(requireNotNull(selected.value), ui, vm,
                        { screen.value = ShellTabs.HISTORY }, {}, {}, { share.value = true })
                    else -> MText("Train", MTheme.type.titleLg)
                }
            }
            AppShellBottom(ui, timer, if (screen.value == DETAIL) ShellTabs.HISTORY else screen.value,
                { screen.value = it }, { screen.value = it }, {}, vm, imeInsets = WindowInsets(0, 0, 0, 0))
        }
        if (share.value) SharePreviewContent(SharePreviewState.Ready(1), emptySet(), false, {}, { share.value = false },
            { error("The tap-budget test must never share externally") }, page = { _, _, ready, _ -> SideEffect { ready() } })
    }

    private companion object {
        const val DETAIL = "history-budget-detail"
        val UTC = TimeZone.UTC
        val SEAMS = ProgressSeams({ LocalDateTime(2026, 11, 4, 12, 0).toInstant(UTC).toEpochMilliseconds() }, { UTC }, { DayOfWeek.SUNDAY })
    }
}
