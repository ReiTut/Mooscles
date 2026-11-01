package com.mooscles.screens.flow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.data.WorkoutRepository
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.TimerCompactTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerExpandedTag
import com.mooscles.designsystem.components.TimerOpenTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.assertWorkoutTargets
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.summary.SummaryRecordsTag
import com.mooscles.screens.summary.SummaryStat
import com.mooscles.screens.summary.SummaryStatsTag
import com.mooscles.screens.summary.summaryStatTag
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.ExerciseMenuTags
import com.mooscles.screens.workout.ExercisePickerListTag
import com.mooscles.screens.workout.RecordToastTag
import com.mooscles.screens.workout.WorkoutHeaderTags
import com.mooscles.screens.workout.exerciseHeaderTag
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.ui.RecordKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/**
 * The active workout from start to summary over the real stack (spec D6): the common
 * [com.mooscles.MoosclesViewModel], Room through the platform's own driver and DataStore, each in
 * a fresh directory ([FlowHarness]), with the screens as the app hosts them ([FlowApp]). It runs on
 * the emulator and on the iOS simulator, where it is the proof that the workout works on iOS.
 *
 * Twice (plan decision 27): light at 1.0 and dark at 2.0 (2.01 on every platform, the linear 200%
 * iOS draws: [LINEAR_TWO_X_FONT_SCALE]). Each run seeds a routine of three exercises and a past
 * workout with 85 kg × 6 on the bench, starts the routine, then: a pre-filled set done; the load
 * stepped up to 87.5 and done, which beats the seeded record (the toast and the badge); a set done
 * from its suggestion; the rest timer docked (compact at 2.0), +15 s, expanded, skipped; a set
 * removed and put back; an exercise added and swapped; the workout finished and its summary, with
 * the numbers and the record. Each step is checked in the view model's state, which comes from the
 * database, not from the screen. In each main state ([sweep]): every clickable node has a label,
 * every target is 48 dp and the workout's actions 56, every number is shown whole, and a capture
 * is taken (written as a PNG on iOS, for the CI to upload).
 */
@OptIn(ExperimentalTestApi::class)
class ActiveWorkoutFlowTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun flowLightAtOneX() = runComposeUiTest(testTimeout = 5.minutes) { flow("light-1x", dark = false, fontScale = 1f) }

    @Test
    fun flowDarkAtTwoX() = runComposeUiTest(testTimeout = 5.minutes) {
        flow("dark-2x", dark = true, fontScale = LINEAR_TWO_X_FONT_SCALE)
    }

    /** What [seed] made. */
    private class Seeded(val routineId: String, val pastWorkoutId: String)

    /**
     * A finished workout a week ago with two sets of 85 kg × 6 on the bench (its record), then the
     * routine "Push day": the bench with three sets, the first two taking that record as their
     * target (so they open pre-filled with 85 × 6) and the third with no target (so it opens empty,
     * to be filled by its suggestion), then the row and the overhead press.
     */
    private suspend fun seed(repository: WorkoutRepository): Seeded {
        repository.initialize()
        val past = currentTimeMillis() - WEEK_MS
        val old = repository.startEmpty("Push", now = past)
        repository.addWorkoutExercise(old, BENCH)
        repository.workoutSets(old).forEach { repository.updateSet(it.copy(reps = 6, weightKg = 85.0, completed = true)) }
        check(repository.finishWorkout(old, now = past + HOUR_MS))
        val routine = repository.createRoutine("Push day")
        val bench = repository.addRoutineExercise(routine, BENCH)
        val third = repository.addRoutineSet(bench)
        repository.updateRoutineSet(third, reps = 0, weightKg = null)
        repository.addRoutineExercise(routine, ROW)
        repository.addRoutineExercise(routine, PRESS)
        return Seeded(routine, old)
    }

    private suspend fun ComposeUiTest.flow(name: String, dark: Boolean, fontScale: Float) {
        val harness = FlowHarness()
        try {
            val seeded = seed(harness.repository)
            val vm = harness.createViewModel()
            withScreen(dark = dark, fontScale = fontScale, locale = "en") { FlowApp(vm) }
            waitForIdle()
            await("the saved data loads") { !vm.contentUi.value.loading && vm.contentUi.value.routines.isNotEmpty() }
            val compact = fontScale >= 1.5f

            // Start the routine, as Train does.
            vm.startWorkout(seeded.routineId)
            await("the workout opens") { vm.contentUi.value.active != null }
            waitForIdle()
            val started = active(vm)
            assertEquals(listOf(BENCH, ROW, PRESS), started.exercises.map { it.exercise.id })
            val benchId = started.exercises[0].id
            val (s1, s2, s3) = started.exercises[0].sets.map { it.id }
            assertEquals(listOf(6 to 85.0, 6 to 85.0, 0 to 0.0), started.exercises[0].sets.map { it.reps to it.weightKg })
            onNodeWithTag(WorkoutHeaderTags.TITLE, useUnmergedTree = true).assertTextEquals("Push day")
            sweep(name, "started")

            // 1. A pre-filled set: one tap on its check. 85 × 6 ties the record, beats nothing.
            check(s1).performClick()
            await("set 1 is saved done") { set(vm, s1)?.completed == true }
            assertEquals(6 to 85.0, set(vm, s1)!!.let { it.reps to it.weightKg })
            assertNull(set(vm, s1)!!.record)
            await("the rest starts") { vm.timerUi.value.running }

            // 2. The load one step up (2.5 kg) by the stepper, then done: 87.5 × 6 beats 85.
            scrollToRow(s2)
            field(s2, SetRowLoadTag).performClick()
            waitForIdle()
            onNodeWithTag(StepperIncreaseTag).assertIsDisplayed()
            sweep(name, "editing")
            onNodeWithTag(StepperIncreaseTag).performClick()
            await("set 2's load is saved at 87.5") { set(vm, s2)?.weightKg == 87.5 }
            assertEquals(false, set(vm, s2)!!.completed)
            // The toast holds for `timing.prToast` on the effects' clock, which waits advance: freeze the
            // clock from the check until the toast is swept and dismissed, and step it frame by frame.
            mainClock.autoAdvance = false
            try {
                check(s2).performClick()
                await("set 2 is saved done with the record") { set(vm, s2)?.completed == true && set(vm, s2)?.record != null }
                val record = set(vm, s2)!!.record!!
                assertEquals(RecordKind.WEIGHT, record.kind)
                assertEquals(85.0, record.previousKg)
                // A few frames bring the emission's toast in, far within its hold (4 s, 250 frames).
                var frames = 0
                while (!exists(hasTestTag(RecordToastTag)) && frames < RECORD_FRAMES) {
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    frames++
                }
                onNode(hasText("New weight record") and hasAnyAncestor(hasTestTag(RecordToastTag)), useUnmergedTree = true).assertIsDisplayed()
                onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row(s2)), useUnmergedTree = true).assertExists()
                sweep(name, "record")
                // A tap on the toast dismisses it (it would otherwise cover the top of the list for its hold).
                onNodeWithTag(RecordToastTag).performClick()
                mainClock.advanceTimeByFrame()
            } finally {
                mainClock.autoAdvance = true
            }
            await("the record toast leaves") { !exists(hasTestTag(RecordToastTag)) }

            // 3. The empty set, done from its suggestion: the set above, 87.5 × 6.
            scrollToRow(s3)
            check(s3).performClick()
            await("set 3 is saved done from its suggestion") { set(vm, s3)?.completed == true }
            assertEquals(6 to 87.5, set(vm, s3)!!.let { it.reps to it.weightKg })
            // One record per exercise: it stays on one of the two 87.5 × 6 sets, and its row shows the badge.
            val holders = listOf(s1, s2, s3).filter { set(vm, it)?.record != null }
            assertEquals(1, holders.size, "sets holding the record: $holders")
            // The bench is all done: after `timing.cardAdvance` its card collapses for the next one.
            openCard(benchId)
            scrollToRow(holders.single())
            onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row(holders.single())), useUnmergedTree = true).assertExists()

            // 4. The rest: docked (compact at 2.0), +15 s, expanded, skipped.
            await("the rest runs") { vm.timerUi.value.running }
            waitForIdle()
            onNodeWithTag(if (compact) TimerCompactTag else TimerDockTag).assertIsDisplayed()
            sweep(name, "dock")
            if (!compact) plusFifteen(vm, onNodeWithTag(TimerPlusTag))
            onNodeWithTag(TimerOpenTag).performClick()
            await("the expanded timer opens") { exists(hasTestTag(TimerExpandedTag)) }
            waitForIdle()
            if (compact) plusFifteen(vm, onNode(hasTestTag(TimerPlusTag) and hasAnyAncestor(hasTestTag(TimerExpandedTag))))
            sweep(name, "expanded")
            onNode(hasTestTag(TimerSkipTag) and hasAnyAncestor(hasTestTag(TimerExpandedTag))).performClick()
            await("the rest is skipped") { !vm.timerUi.value.running && !vm.timerUi.value.paused }
            await("the expanded timer closes") { !exists(hasTestTag(TimerExpandedTag)) }
            await("the dock leaves") { !exists(hasTestTag(TimerOpenTag)) }

            // 5. Set 3 removed by a swipe, then put back by Undo, with its values and its place.
            openCard(benchId)
            scrollToRow(s3)
            // Leftwards from its check, as a thumb does.
            onNode(row(s3)).performTouchInput {
                swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400)
            }
            await("set 3 is removed") { set(vm, s3) == null }
            await("the undo toast shows") { exists(hasText("Set 3 removed") and hasAnyAncestor(hasTestTag(TOAST)), unmerged = true) }
            sweep(name, "removed")
            onNode(hasText("Undo") and hasClickAction()).performClick()
            await("set 3 is back") { set(vm, s3) != null }
            val back = entry(vm, benchId).sets
            assertEquals(listOf(s1, s2, s3), back.map { it.id })
            assertEquals(Triple(6, 87.5, true), back[2].let { Triple(it.reps, it.weightKg, it.completed) })

            // 6. An exercise added from the picker, then swapped from its card's menu.
            onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.ADD_EXERCISE))
            waitForIdle()
            onNodeWithTag(ActiveWorkoutTags.ADD_EXERCISE).performClick()
            waitForIdle()
            pick(name, "picker", query = "Pendlay", exerciseId = PENDLAY)
            await("the exercise is added") { active(vm).exercises.size == 4 }
            val added = active(vm).exercises.last()
            assertEquals(PENDLAY, added.exercise.id)
            assertEquals(2, added.sets.size)
            onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseHeaderTag(added.id)))
            waitForIdle()
            onNode(hasTestTag(ExerciseMenuTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(added.id)))).performClick()
            waitForIdle()
            onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
            waitForIdle()
            pick(name, "swap", query = "shrug", exerciseId = SHRUG)
            await("the exercise is swapped") { active(vm).exercises.firstOrNull { it.id == added.id }?.exercise?.id == SHRUG }
            assertEquals(listOf(BENCH, ROW, PRESS, SHRUG), active(vm).exercises.map { it.exercise.id })

            // 7. Finish: the header's button, then the dialog's confirmation.
            onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
            waitForIdle()
            await("the finish dialog opens") { exists(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))) }
            sweep(name, "finish")
            onNode(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))).performClick()
            val workoutId = started.id
            await("the workout is saved finished") { vm.contentUi.value.active == null && vm.contentUi.value.history.any { it.id == workoutId } }
            await("the summary opens") { exists(hasTestTag(SummaryStatsTag)) }
            waitForIdle()

            // 8. The summary: 3 sets (1 exercise) of 85 × 6, 87.5 × 6 and 87.5 × 6, 1,560 kg, and the record.
            onNodeWithTag(summaryStatTag(SummaryStat.SETS)).assertTextEquals("Sets", "3")
            onNodeWithTag(summaryStatTag(SummaryStat.EXERCISES)).assertTextEquals("Exercises", "1")
            onNode(hasTestTag(summaryStatTag(SummaryStat.VOLUME)) and hasText("1,560", substring = true)).assertExists()
            val records = hasAnyAncestor(hasTestTag(SummaryRecordsTag))
            onNode(hasText("87.5${NBSP}kg${NBSP}×${NBSP}6", substring = true) and records).assertExists()
            onNode(hasText("before 85${NBSP}kg", substring = true) and records).assertExists()
            assertEquals(1, onAllNodes(hasTestTag(PrBadgeTag) and records, useUnmergedTree = true).fetchSemanticsNodes().size)
            sweep(name, "summary", workout = false)

            // And on disk: the history kept both workouts, the new one with its three sets done.
            val saved = harness.repository.workoutSets(workoutId)
            assertEquals(listOf(85.0, 87.5, 87.5), saved.filter { it.completed }.map { it.weightKg })
            assertEquals(2, harness.repository.snapshot().workouts.size)
            assertNotNull(harness.repository.workout(seeded.pastWorkoutId))
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /** Taps [plus] and waits for the saved rest to grow by about 15 s. */
    private fun ComposeUiTest.plusFifteen(vm: FlowViewModel, plus: SemanticsNodeInteraction) {
        val before = vm.timerUi.value.remainingSeconds
        plus.performClick()
        await("+15 s is saved") { vm.timerUi.value.remainingSeconds >= before + 10 }
    }

    /** In the open exercise picker: sweeps it, searches [query] and picks [exerciseId]. */
    private fun ComposeUiTest.pick(name: String, state: String, query: String, exerciseId: String) {
        await("the exercise picker opens") { exists(hasTestTag(ExercisePickerListTag)) }
        // Its list is the library's model, built off the main thread: skeleton rows until then.
        await("the exercise picker's list is built") { !exists(hasTestTag(LibraryTags.SKELETON_ROW)) }
        waitForIdle()
        sweep(name, state)
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SHEET))).performTextReplacement(query)
        waitForIdle()
        val tag = exercisePickerRowTag(exerciseId, recent = false)
        onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag))
        waitForIdle()
        onNodeWithTag(tag).performClick()
        await("the exercise picker closes") { !exists(hasTestTag(ExercisePickerListTag)) }
    }

    // --- The checks of every main state -----------------------------------------------------------

    /**
     * The checks of a main state: a label on every clickable node, the targets, the numbers whole
     * ([assertNumbersWhole]) and a capture. [workout] false is the summary, which has no workout actions.
     */
    private fun ComposeUiTest.sweep(name: String, state: String, workout: Boolean = true) {
        waitForIdle()
        assertClickablesLabeledAndSized(state)
        if (workout) assertWorkoutTargets(state)
        assertNumbersWhole()
        capture("$name-$state")
    }

    private companion object {
        const val BENCH = "catalog_barbell_bench_flat"
        const val ROW = "catalog_barbell_row_overhand"
        const val PRESS = "catalog_barbell_overhead_press"
        const val PENDLAY = "catalog_barbell_row_pendlay"
        const val SHRUG = "catalog_barbell_shrug"

        const val HOUR_MS = 3_600_000L
        const val WEEK_MS = 7 * 24 * HOUR_MS
        /** At most this many frames for the record toast to come in (a second of the effects' clock). */
        const val RECORD_FRAMES = 60

        const val NBSP = " "

        // The design system's internal tags (ToastTag, DialogTag, SheetTag).
        const val TOAST = "toast"
        const val DIALOG = "dialog"
        const val SHEET = "sheet"
    }
}
