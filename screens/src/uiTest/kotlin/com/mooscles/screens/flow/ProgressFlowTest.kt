package com.mooscles.screens.flow

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.charts.LineChartTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.progress.ExerciseProgressTag
import com.mooscles.screens.progress.ProgressMetric
import com.mooscles.screens.progress.ProgressTags
import com.mooscles.screens.records.RecordsTags
import com.mooscles.screens.withScreen
import com.mooscles.ui.RecordKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The progress from the Records tab to the exercise detail over the real stack (spec D6): the common
 * [com.mooscles.MoosclesViewModel], Room (v6) through the platform's own driver and DataStore, each in a fresh directory
 * ([FlowHarness]), with the Records tab, the exercise progress and the detail as the app hosts them
 * ([ProgressFlowApp]: a plain screen switch under the tab bar, no NavHost). It runs on the emulator and on the iOS
 * simulator.
 *
 * Twice: Portuguese, light, at 1.0, and English, dark, at 2.0 (2.01 on every platform, the linear 200% iOS draws:
 * [LINEAR_TWO_X_FONT_SCALE]). Each run seeds, through the repository, six finished bench press workouts with the load
 * going up and, in the newest, a reps record, the back squat in the last two ([seedBenchProgress]), then: the timeline
 * opens with the squat's record on top; the search ("supino" or "bench") puts the bench's current load record on top,
 * with "Current"; a tap on it opens the progress on the load, the chart drawn with the record mark (one image node with
 * its summary); "Estimated 1RM" swaps the chart, without the mark; "View data" lists one row per session; and the menu's
 * "About the exercise" opens the detail, whose history card has the sparkline. In each state ([sweep]): every
 * clickable node has a label and a 48 dp target, every number is shown whole and a capture is taken (a PNG on iOS,
 * `screens/build/ios-captures/progress-<run>-<state>.png`).
 */
@OptIn(ExperimentalTestApi::class)
class ProgressFlowTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun progressFlowPortugueseLightAtOneX() = runComposeUiTest(testTimeout = 6.minutes) { flow(PORTUGUESE) }

    @Test
    fun progressFlowEnglishDarkAtTwoX() = runComposeUiTest(testTimeout = 6.minutes) { flow(ENGLISH) }

    /** One run's language, look and the texts it types and expects. */
    private class Run(
        val name: String,
        val locale: String,
        val dark: Boolean,
        val fontScale: Float,
        val query: String,
        val load: String,
        val estimate: String,
    ) {
        val portuguese get() = locale == "pt-BR"
    }

    private suspend fun ComposeUiTest.flow(run: Run) {
        val harness = FlowHarness()
        try {
            val seed = seedBenchProgress(harness.repository, squat = true)
            val vm = harness.createViewModel()
            val screen = mutableStateOf(ProgressScreens.RECORDS)
            withScreen(dark = run.dark, fontScale = run.fontScale, locale = run.locale) { ProgressFlowApp(vm, screen) }
            waitForIdle()
            await("the saved data loads") { !vm.contentUi.value.loading && vm.contentUi.value.history.size == seed.benchWorkouts.size }

            // The records the repository found in the newest workout: the squat's and the bench's load records, and the
            // bench's reps record (8 at 80 kg, beating 5), all beaten ones and all current.
            val newest = vm.contentUi.value.history.single { it.id == seed.benchWorkouts.last() }
            val squatLoad = newest.records.single { it.exercise.id == BACK_SQUAT && it.kind == RecordKind.WEIGHT }
            val benchLoad = newest.records.single { it.exercise.id == BENCH_PRESS && it.kind == RecordKind.WEIGHT }
            val benchReps = newest.records.single { it.exercise.id == BENCH_PRESS && it.kind == RecordKind.REPS && it.weightKg == 80.0 }
            assertEquals(105.0 to 100.0, squatLoad.weightKg to squatLoad.previousKg)
            assertEquals(85.0 to 80.0, benchLoad.weightKg to benchLoad.previousKg)
            assertEquals(8 to 5, benchReps.reps to benchReps.previousReps)
            val squatRow = recordRowTag(squatLoad)
            val benchRow = recordRowTag(benchLoad)

            // 1. The timeline: the newest workout's first record, the squat's, is on top.
            await("the timeline is built") { topRecordRow() != null && !exists(hasTestTag(RecordsTags.SKELETON), unmerged = true) }
            waitForIdle()
            assertEquals(squatRow, topRecordRow(), "the top of the timeline")
            sweep(run, "records")

            // 2. The search: the bench's current load record on top, with "Current".
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(RecordsTags.SEARCH))).performTextReplacement(run.query)
            await("the search puts the bench on top") { topRecordRow() == benchRow }
            waitForIdle()
            assertTrue(exists(hasTestTag(RecordsTags.ROW_CURRENT) and hasAnyAncestor(hasTestTag(benchRow)), unmerged = true), "the top row says it is current")
            assertEquals(benchName(run), textOf(onNode(hasTestTag(RecordsTags.ROW_NAME) and hasAnyAncestor(hasTestTag(benchRow)), useUnmergedTree = true).fetchSemanticsNode().config))
            assertTrue(!exists(hasTestTag(squatRow)), "the squat is not found")
            sweep(run, "search")

            // 3. The row opens the progress on the load: the chart with the record mark.
            onNodeWithTag(benchRow).performClick()
            await("the progress opens on the load") { screen.value == ProgressScreens.progress(BENCH_PRESS, ProgressMetric.LOAD.route) }
            await("the metrics show") { exists(hasTestTag(ProgressTags.metric(ProgressMetric.LOAD))) }
            waitForIdle()
            onNodeWithTag(ProgressTags.metric(ProgressMetric.LOAD)).assertIsSelected()
            awaitProgressChart(run.load)
            scrollTo(ProgressTags.LEGEND_RECORD)
            assertTrue(exists(hasTestTag(ProgressTags.LEGEND_RECORD)), "the load chart has the record legend")
            toTop()
            sweep(run, "load", numbers = 1)

            // 4. "Estimated 1RM": the chart swaps, and has no record mark (D6).
            scrollTo(ProgressTags.metric(ProgressMetric.E1RM))
            onNodeWithTag(ProgressTags.metric(ProgressMetric.E1RM)).performClick()
            waitForIdle()
            awaitProgressChart(run.estimate)
            assertTrue(!exists(hasTestTag(ProgressTags.LEGEND_RECORD)), "the 1RM chart has no record legend")
            toTop()
            onNodeWithTag(ProgressTags.metric(ProgressMetric.E1RM)).assertIsSelected()
            sweep(run, "e1rm", numbers = 1)

            // 5. "View data": one row per session, the newest first.
            scrollTo(ProgressTags.DATA_TOGGLE)
            onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
            await("the table shows") { runCatching { scrollTo(ProgressTags.tableRow(0)) }.isSuccess }
            assertTrue(!exists(hasTestTag(LineChartTag)), "the table replaces the chart")
            for (i in seed.benchWorkouts.indices) scrollTo(ProgressTags.tableRow(i))
            assertTrue(
                runCatching { scrollTo(ProgressTags.tableRow(seed.benchWorkouts.size)) }.isFailure,
                "the table has a row more than the ${seed.benchWorkouts.size} sessions",
            )
            waitForIdle()
            sweep(run, "table", numbers = 1)

            // 6. The menu's "About the exercise" opens the detail, whose history card has the sparkline.
            onNodeWithTag(ProgressTags.MENU).performClick()
            await("the menu opens") { exists(hasTestTag(ProgressTags.MENU_ABOUT)) }
            waitForIdle()
            sweep(run, "menu")
            onNodeWithTag(ProgressTags.MENU_ABOUT).performClick()
            await("the detail opens") { screen.value == ProgressScreens.info(BENCH_PRESS) && exists(hasTestTag(ExerciseInfoTags.TITLE)) }
            await("the menu closes") { !exists(hasTestTag(ProgressTags.MENU_ABOUT)) }
            await("the history card has the sparkline") { exists(hasTestTag(ExerciseInfoTags.HISTORY_SPARKLINE), unmerged = true) }
            waitForIdle()
            assertEquals(benchName(run), textOf(onNodeWithTag(ExerciseInfoTags.TITLE).fetchSemanticsNode().config))
            sweep(run, "detail", numbers = 1)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    // --- Finding and acting ---------------------------------------------------------------------

    private fun benchName(run: Run) =
        ExerciseNames.display(BENCH_PRESS, ExerciseCatalog.exercises.single { it.id == BENCH_PRESS }.name, custom = false, portuguese = run.portuguese)

    private fun textOf(config: androidx.compose.ui.semantics.SemanticsConfiguration): String =
        config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun ComposeUiTest.scrollTo(tag: String) = scrollProgressTo(tag)

    private fun ComposeUiTest.toTop() {
        onNodeWithTag(ExerciseProgressTag).performScrollToIndex(0)
        waitForIdle()
    }

    // --- The checks of every state ----------------------------------------------------------------

    /**
     * The checks of a state: a label and a 48 dp target on every clickable node (every root: a sheet too), every
     * number text whole ([assertNumbersWhole]), and a capture, `progress-<run>-<state>`. [numbers] is the floor of
     * number-only texts the state shows: 1 where the highlight, a table row or the detail's best set is on screen; 0
     * on the timeline (its rows write "before" and dates around their numbers) and the menu.
     */
    private fun ComposeUiTest.sweep(run: Run, state: String, numbers: Int = 0) {
        waitForIdle()
        assertClickablesLabeledAndSized("${run.name} $state")
        assertNumbersWhole(atLeast = numbers)
        capture("progress-${run.name}-$state")
    }

    private companion object {
        val PORTUGUESE = Run(
            name = "pt-light-1x",
            locale = "pt-BR",
            dark = false,
            fontScale = 1f,
            query = "supino",
            load = "Carga",
            estimate = "1RM estimado",
        )

        val ENGLISH = Run(
            name = "en-dark-2x",
            locale = "en",
            dark = true,
            fontScale = LINEAR_TWO_X_FONT_SCALE,
            query = "bench",
            load = "Load",
            estimate = "Estimated 1RM",
        )
    }
}
