package com.mooscles.screens.flow

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.data.WorkoutRepository
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.exercise.AddToRoutineSheetTags
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.exercise.FakeExercisePhotos
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.routines.PlannedSetMenuTags
import com.mooscles.screens.routines.RoutineEditorTag
import com.mooscles.screens.routines.RoutineEditorTags
import com.mooscles.screens.routines.TrainScreenTag
import com.mooscles.screens.routines.TrainTags
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.ExercisePickerAddTag
import com.mooscles.screens.workout.ExercisePickerListTag
import com.mooscles.screens.workout.RenameSheetTags
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The routines from the library to the workout over the real stack (spec D6): the common
 * [com.mooscles.MoosclesViewModel], Room through the platform's own driver and DataStore, each in a fresh
 * directory ([FlowHarness]), with the library, the exercise detail, the Train tab, the routine editor and the
 * workout as the app hosts them ([RoutinesFlowApp]: a plain screen switch, no NavHost). It runs on the emulator and
 * on the iOS simulator.
 *
 * Twice: Portuguese, light, at 1.0, and English, dark, at 2.0 (2.01 on every platform, the linear 200% iOS draws:
 * [LINEAR_TWO_X_FONT_SCALE]). Each run seeds the empty routine "Treino A", then: searches the bench press ("supino"
 * or "bench") in the library and opens it; "Add to routine" → "Treino A" (checked in the state, which comes from the
 * database); the Train tab, where "New routine" is created with its default name and opens its editor; "Add
 * exercises" with three marked in the multiple picker, which the routine holds in the order of the taps; the third
 * card to the top by the finger (its handle scrolled to: at 2x the card is then the first one shown,
 * and lifting collapses the cards until the list's end comes into view) and the first down one place by its menu; the first set of the
 * first exercise marked as a warm-up through its menu; and "Start workout", whose first card shows the target line
 * with "+ 1 warm-up". In each main state ([sweep]): every clickable node has a label and a 48 dp target, every number
 * is shown whole and a capture is taken (a PNG on iOS, `screens/build/ios-captures/routines-<state>.png`).
 */
@OptIn(ExperimentalTestApi::class)
class RoutinesFlowTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun routinesFlowPortugueseLightAtOneX() = runComposeUiTest(testTimeout = 6.minutes) { flow(PORTUGUESE) }

    @Test
    fun routinesFlowEnglishDarkAtTwoX() = runComposeUiTest(testTimeout = 6.minutes) { flow(ENGLISH) }

    /** One run's language, look and the texts it types and expects. */
    private class Run(
        val name: String,
        val locale: String,
        val dark: Boolean,
        val fontScale: Float,
        val query: String,
        val newName: String,
        val warmupLine: String,
    )

    private suspend fun ComposeUiTest.flow(run: Run) {
        val harness = FlowHarness()
        try {
            harness.repository.initialize()
            val routineA = harness.repository.createRoutine(ROUTINE)
            val vm = harness.createViewModel()
            val screen = mutableStateOf(RoutinesScreens.LIBRARY)
            val photos = FakeExercisePhotos()
            withScreen(dark = run.dark, fontScale = run.fontScale, locale = run.locale) { photos.Provide { RoutinesFlowApp(vm, screen) } }
            waitForIdle()
            await("the saved data loads") { !vm.contentUi.value.loading && vm.contentUi.value.routines.isNotEmpty() }
            awaitList()
            sweep(run, "library")

            // 1. Search the bench press and open it.
            onNode(hasSetTextAction()).performTextReplacement(run.query)
            waitForIdle()
            val bench = LibraryTags.row(BENCH)
            await("the search finds it") { exists(hasTestTag(LibraryTags.COUNT), unmerged = true) && rowShown(bench) }
            sweep(run, "search")
            onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(bench))
            waitForIdle()
            onNodeWithTag(bench).performClick()
            await("its detail opens") { screen.value == RoutinesScreens.info(BENCH) }
            await("its title shows") { exists(hasTestTag(ExerciseInfoTags.TITLE)) }
            waitForIdle()
            sweep(run, "detail", numbers = 1)

            // 2. "Add to routine" → "Treino A".
            onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE).performScrollTo().performClick()
            await("the routine sheet opens") { exists(hasTestTag(AddToRoutineSheetTags.routine(routineA))) }
            waitForIdle()
            sweep(run, "add-to-routine")
            onNodeWithTag(AddToRoutineSheetTags.routine(routineA)).performClick()
            await("the routine is saved with the exercise") { routine(vm, routineA).exercises.map { it.exercise.id } == listOf(BENCH) }
            await("the sheet closes") { !exists(hasTestTag(AddToRoutineSheetTags.ROOT)) }
            waitForIdle()

            // 3. The Train tab: "Treino A" has its card; "New routine" is created with the default name and opens.
            screen.value = RoutinesScreens.TRAIN
            await("the card shows") { exists(hasTestTag(TrainTags.card(routineA))) }
            waitForIdle()
            sweep(run, "train")
            onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.NEW_ROUTINE))
            waitForIdle()
            onNodeWithTag(TrainTags.NEW_ROUTINE).performClick()
            await("the name sheet opens") { exists(hasTestTag(RenameSheetTags.FIELD)) }
            waitForIdle()
            assertEquals(run.newName, onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            sweep(run, "new-routine")
            onNodeWithTag(RenameSheetTags.SAVE).performClick()
            await("the routine is saved") { vm.contentUi.value.routines.size == 2 }
            val created = vm.contentUi.value.routines.single { it.id != routineA }
            assertEquals(run.newName, created.name)
            await("its editor opens") { screen.value == RoutinesScreens.editor(created.id) && exists(hasTestTag(RoutineEditorTags.EMPTY)) }
            waitForIdle()
            sweep(run, "editor-empty")

            // 4. "Add exercises" with three marked: the routine holds them in the order of the taps.
            onNodeWithTag(RoutineEditorTags.ADD).performClick()
            for (id in PICKS) {
                pickerRow(id).performClick()
                waitForIdle()
            }
            sweep(run, "picker")
            onNodeWithTag(ExercisePickerAddTag).performClick()
            await("the routine holds the three") { routine(vm, created.id).exercises.map { it.exercise.id } == PICKS }
            await("the picker closes") { !exists(hasTestTag(ExercisePickerAddTag)) }
            waitForIdle()
            sweep(run, "editor", numbers = 1)

            // 5. The third card to the top by the finger, and the first down one place by its menu.
            val entries = routine(vm, created.id).exercises.map { it.id }
            val third = entries[2]
            lift(third)
            try {
                // The finger up to just inside the list's top: there the list scrolls back to its top, and the card goes first.
                // Bounds in px.
                val handle = onNodeWithTag(RoutineEditorTags.handle(third), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val list = onNodeWithTag(RoutineEditorTag).fetchSemanticsNode().boundsInRoot
                dragBy(list.top + with(density) { TOP_INSET.toPx() } - handle.center.y)
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
            } finally {
                release()
            }
            await("the drag is saved") { routine(vm, created.id).exercises.map { it.id } == listOf(third, entries[0], entries[1]) }
            waitForIdle()
            openExerciseMenu(entries[0])
            onNodeWithTag(RoutineEditorTags.EXERCISE_DOWN).performClick()
            waitForIdle()
            await("the order is saved") { routine(vm, created.id).exercises.map { it.id } == listOf(third, entries[1], entries[0]) }
            waitForIdle()
            assertEquals(listOf(SQUAT, INCLINE, BENCH), routine(vm, created.id).exercises.map { it.exercise.id })
            sweep(run, "reordered", numbers = 1)

            // 6. The first set of the first exercise as a warm-up, through its menu.
            val first = routine(vm, created.id).exercises.first()
            val set = first.setTargets.first().id
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.setLabel(set)))
            waitForIdle()
            onNodeWithTag(RoutineEditorTags.setLabel(set)).performClick()
            await("the set menu opens") { exists(hasTestTag(PlannedSetMenuTags.WARMUP)) }
            waitForIdle()
            sweep(run, "set-menu", numbers = 1)
            onNodeWithTag(PlannedSetMenuTags.WARMUP).performClick()
            await("the set is a warm-up") { routine(vm, created.id).exercises.first().setTargets.first().warmup }
            await("the menu closes") { !exists(hasTestTag(PlannedSetMenuTags.WARMUP)) }
            waitForIdle()
            sweep(run, "editor-warmup", numbers = 1)

            // 7. "Start workout": the first card's target line says "+ 1 warm-up".
            onNodeWithTag(RoutineEditorTags.START).performClick()
            await("the workout is started") { vm.contentUi.value.active != null && screen.value == RoutinesScreens.WORKOUT }
            val warmupText = hasText(run.warmupLine, substring = true)
            await("the target line shows the warm-up") { exists(warmupText, unmerged = true) }
            waitForIdle()
            sweep(run, "workout", numbers = 1)
            val active = active(vm)
            assertEquals(created.id, active.sourceRoutineId)
            assertEquals(listOf(SQUAT, INCLINE, BENCH), active.exercises.map { it.exercise.id })
            assertTrue(active.exercises.first().sets.first().warmup, "the first set of the workout is the warm-up")
            assertEquals(1, active.exercises.first().target?.warmups)
            // And on disk: "Treino A" holds the bench only, the new routine the three in their new order.
            assertEquals(listOf(BENCH), harness.repository.snapshot().routineItems(routineA).map { it.exerciseId })
            assertEquals(listOf(SQUAT, INCLINE, BENCH), harness.repository.snapshot().routineItems(created.id).map { it.exerciseId })
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    // --- Finding and acting ---------------------------------------------------------------------

    private fun routine(vm: FlowViewModel, id: String): RoutineUi = vm.contentUi.value.routines.first { it.id == id }

    /** The library's model is built off the main thread, which `waitForIdle` does not wait for. */
    private fun ComposeUiTest.awaitList() {
        await("the library's list is built") {
            exists(hasTestTag(LibraryTags.COUNT), unmerged = true) && !exists(hasTestTag(LibraryTags.SKELETON_ROW), unmerged = true)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.rowShown(tag: String): Boolean {
        if (!exists(hasTestTag(tag))) {
            if (!exists(hasTestTag(LibraryTags.LIST))) return false
            runCatching { onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(tag)) }
        }
        return exists(hasTestTag(tag))
    }

    /** The picker's "All" row of [id], once its list is built (off the main thread) and scrolled to. */
    private fun ComposeUiTest.pickerRow(id: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        val tag = exercisePickerRowTag(id, recent = false)
        await("the picker lists $id") {
            if (!exists(hasTestTag(tag)) && exists(hasTestTag(ExercisePickerListTag))) {
                runCatching { onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag)) }
            }
            exists(hasTestTag(tag))
        }
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.openExerciseMenu(entryId: String) {
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.menu(entryId)))
        waitForIdle()
        onNodeWithTag(RoutineEditorTags.menu(entryId)).performClick()
        waitForIdle()
    }

    /** Puts a finger on [entryId]'s handle and holds it past the long-press timeout: the card lifts. */
    private fun ComposeUiTest.lift(entryId: String) {
        // Only when the handle is not in the list yet: scrolling to a handle in sight would make its card the first one shown.
        if (!exists(hasTestTag(RoutineEditorTags.handle(entryId)), unmerged = true)) {
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.handle(entryId)))
            waitForIdle()
        }
        var longPress = 0L
        onNodeWithTag(RoutineEditorTags.handle(entryId), useUnmergedTree = true).performTouchInput {
            longPress = viewConfiguration.longPressTimeoutMillis
            down(center)
        }
        mainClock.advanceTimeBy(longPress + LONG_PRESS_MARGIN)
        waitForIdle()
    }

    private fun ComposeUiTest.dragBy(dy: Float, steps: Int = DRAG_STEPS) {
        val step = dy / steps
        repeat(steps) {
            onNodeWithTag(RoutineEditorTag).performTouchInput { moveBy(Offset(0f, step)) }
            waitForIdle()
        }
    }

    private fun ComposeUiTest.release() {
        onNodeWithTag(RoutineEditorTag).performTouchInput { up() }
        waitForIdle()
    }

    // --- The checks of every main state -----------------------------------------------------------

    /**
     * The checks of a main state: a label and a 48 dp target on every clickable node (every root: a sheet too),
     * every number text whole ([assertNumbersWhole]), and a capture, `routines-<run>-<state>`.
     */
    private fun ComposeUiTest.sweep(run: Run, state: String, numbers: Int = 0) {
        waitForIdle()
        assertClickablesLabeledAndSized("${run.name} $state")
        // [numbers] is the floor of number-only texts the state shows: 1 where a set's number label or the
        // detail's history is on screen. 0 only where there is none (lists and sheets write counts as words).
        assertNumbersWhole(atLeast = numbers)
        capture("routines-${run.name}-$state")
    }

    private companion object {
        const val ROUTINE = "Treino A"
        const val BENCH = "catalog_barbell_bench_flat"
        const val INCLINE = "catalog_dumbbell_bench_incline"
        const val SQUAT = "catalog_barbell_front_squat"

        /** The three marked in the picker, in the order of the taps. */
        val PICKS = listOf(BENCH, INCLINE, SQUAT)

        const val LONG_PRESS_MARGIN = 100L
        const val DRAG_STEPS = 20

        /** Where the finger stops, below the list's top: inside the edge zone, where the list scrolls back. */
        val TOP_INSET = 8.dp
        const val SETTLE_MS = 1_000L

        val PORTUGUESE = Run(
            name = "pt-light-1x",
            locale = "pt-BR",
            dark = false,
            fontScale = 1f,
            query = "supino",
            newName = "Nova rotina",
            warmupLine = "+\u00A01\u00A0aquec.",
        )

        val ENGLISH = Run(
            name = "en-dark-2x",
            locale = "en",
            dark = true,
            fontScale = LINEAR_TWO_X_FONT_SCALE,
            query = "bench",
            newName = "New routine",
            warmupLine = "+\u00A01\u00A0warm-up",
        )
    }
}
