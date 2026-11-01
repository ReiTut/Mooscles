package com.mooscles.screens.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTextInput
import com.mooscles.designsystem.components.tabTag
import com.mooscles.screens.exercise.CustomExerciseFormTags
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.ExerciseInfoScreenTag
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.data.ExerciseCatalog
import com.mooscles.screens.library.LibraryFilters
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.library.LibraryScreenTag
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.routines.PlannedSetMenuTags
import com.mooscles.screens.routines.RoutineEditorTag
import com.mooscles.screens.routines.RoutineEditorTags
import com.mooscles.screens.routines.TrainScreenTag
import com.mooscles.screens.routines.TrainTags
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.data.WorkoutRepository
import com.mooscles.designsystem.components.ExerciseAddSetTag
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.flow.BACK_SQUAT
import com.mooscles.screens.flow.BENCH_PRESS
import com.mooscles.screens.flow.BenchSeed
import com.mooscles.screens.flow.FlowApp
import com.mooscles.screens.flow.ProgressFlowApp
import com.mooscles.screens.flow.ProgressScreens
import com.mooscles.screens.flow.awaitProgressChart
import com.mooscles.screens.flow.recordRowTag
import com.mooscles.screens.flow.scrollProgressTo
import com.mooscles.screens.flow.seedBenchProgress
import com.mooscles.screens.flow.topRecordRow
import com.mooscles.screens.flow.RoutinesFlowApp
import com.mooscles.screens.flow.RoutinesScreens
import com.mooscles.screens.flow.FlowHarness
import com.mooscles.screens.flow.FlowViewModel
import com.mooscles.screens.flow.active
import com.mooscles.screens.flow.await
import com.mooscles.screens.flow.check
import com.mooscles.screens.flow.exists
import com.mooscles.screens.flow.field
import com.mooscles.screens.flow.row
import com.mooscles.screens.flow.scrollToRow
import com.mooscles.screens.flow.set
import com.mooscles.screens.progress.ProgressMetric
import com.mooscles.screens.progress.ProgressTags
import com.mooscles.screens.records.RecordsTags
import com.mooscles.screens.withScreen
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.ExercisePickerAddTag
import com.mooscles.screens.workout.ExercisePickerListTag
import com.mooscles.screens.workout.RenameSheetTags
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.screens.workout.WorkoutHeaderTags
import com.mooscles.screens.workout.exerciseColumnsTag
import com.mooscles.screens.workout.exerciseFooterTag
import com.mooscles.screens.workout.exerciseHeaderTag
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The spec's tap budget ("Orçamento de toques"): each flow of its table on the new workout screen,
 * over the real stack ([FlowHarness]: the common view model, Room and DataStore), counted with the
 * counter of Task 24 ([Taps]: a tap or a gesture counts 1, and so would each typed character;
 * scrolling to a control and opening the workout do not count). Each flow checks what it saved,
 * from the view model's state (which comes from Room), and that its count is within the spec's
 * "Novo" column ([New]) and within today's count on the old screen ([Today]); the one exception is
 * removing a set by its sheet, the accessible alternative to the swipe, held to "Novo" only
 * (spec: "O de remover pela sheet (2) é a alternativa acessível do deslize (1)").
 *
 * Every flow starts from a bench press with two sets and a finished workout a week ago with two
 * sets of 80 kg × 8 on it, the record the routine's sets take as their target ([seed]).
 *
 * Plus the phase 4A library's two flows of its spec table (docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md,
 * "Orçamento de toques"), over the same stack with the whole catalog, the library and the exercise
 * detail hosted as the app does under the tab bar ([LibraryApp]): finding an exercise by its muscle
 * and opening it, from another tab, and creating a custom exercise with a name, a muscle and an
 * equipment, from the library (the typed name does not count). Each is held to the spec's "Novo"
 * ([LibraryNew]) and to today's count on the old library ([LibraryToday]).
 *
 * And the phase 4B routines' five flows of its spec table (same document), over the same stack with the Train tab,
 * the routine editor and the multiple picker hosted as the app does ([RoutinesFlowApp]): building a routine with five
 * exercises from the Train tab, moving the fifth to the top by its menu, adding a planned set like the one above,
 * marking a planned set as a warm-up, and starting a workout from a routine's card. Each is held to the spec's "Novo"
 * ([RoutinesNew]) and to today's count on the old screens ([RoutinesToday]); typed text does not count (the default
 * name is kept), and neither does scrolling to a control.
 *
 * And the phase 5A records' three flows of its spec table (docs/superpowers/specs/2026-10-31-phase5-progress-history-design.md,
 * "Orçamento de toques"), over the same stack with six bench press workouts and the Records tab, the exercise progress
 * and the tab bar hosted as the app does ([ProgressFlowApp]), each from the Train tab: seeing the current record of an
 * exercise, going from a record to the exercise's progress with its chart, and seeing the bench's estimated 1RM. Each
 * is held to [RecordsNew] and to today's count on the old records screens ([RecordsToday]); focusing the search field
 * is a tap, as it was when today's count was measured, and only the typed text is free.
 */
@OptIn(ExperimentalTestApi::class)
class TapBudgetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /**
     * Today's counts, measured on the old workout screen before the swap (Task 24, at `bb3d633`):
     * docs/superpowers/reports/2026-10-29-phase3-verificacao/tap-baseline.md
     */
    private object Today {
        const val PREFILLED = 1
        const val SAME_AS_LAST_WEEK = 6
        const val LOAD_STEP_UP = 8
        const val REP_LESS = 4
        const val ADD_SET = 4
        const val WARMUP = 2
        const val SKIP = 1
        const val PLUS_FIFTEEN = 1
        const val FINISH = 2
    }

    /** The spec's "Novo" column (docs/superpowers/specs/2026-10-29-phase3-active-workout-design.md, "Orçamento de toques"). */
    private object New {
        const val PREFILLED = 1
        const val SAME_AS_LAST_WEEK = 1
        const val LOAD_STEP_UP = 3
        const val REP_LESS = 3
        const val ADD_SET = 2
        const val WARMUP = 2
        const val REMOVE_BY_SWIPE = 1
        const val REMOVE_BY_SHEET = 2
        const val SKIP = 1
        const val PLUS_FIFTEEN = 1
        const val FINISH = 2
    }

    /**
     * Today's counts on the old library (Task 19, at `9112a7d`, before the swap):
     * docs/superpowers/reports/2026-10-29-phase4a-verificacao/tap-baseline-4a.md
     */
    private object LibraryToday {
        const val FIND_BY_MUSCLE_AND_OPEN = 4
        const val CREATE_CUSTOM_EXERCISE = 6
    }

    /** The spec's "Novo" column (docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md, "Orçamento de toques"). */
    private object LibraryNew {
        const val FIND_BY_MUSCLE_AND_OPEN = 3
        const val CREATE_CUSTOM_EXERCISE = 4
    }

    /**
     * Today's counts on the old routine screens (phase 4B Task 15, at `4736ec5`, before the Train tab and the editor
     * were hosted): docs/superpowers/reports/2026-10-30-phase4b-verificacao/tap-baseline-4b.md. Marking a warm-up
     * has no baseline there (the old editor had no planned sets), so it is held to "Novo" only.
     */
    private object RoutinesToday {
        const val BUILD_WITH_FIVE_EXERCISES = 13
        const val MOVE_FIFTH_TO_TOP = 4
        const val ADD_PLANNED_SET = 1
        const val START_FROM_ROUTINE = 1
    }

    /** The spec's "Novo" column (docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md, "Orçamento de toques"). */
    private object RoutinesNew {
        const val BUILD_WITH_FIVE_EXERCISES = 9
        const val MOVE_FIFTH_TO_TOP = 2
        const val ADD_PLANNED_SET = 1
        const val MARK_WARMUP = 2
        const val START_FROM_ROUTINE = 1
    }

    /**
     * Today's counts on the old records screens (phase 5A Task 13, at `2cbca45`, before the Records tab and the
     * progress were hosted): docs/superpowers/reports/2026-10-31-phase5a-verificacao/tap-baseline-5a.md. Focusing the
     * search field counted there as a tap (only the typed name did not). The estimated 1RM has no baseline (the old
     * screens had none), so it is held to [RecordsNew] only.
     */
    private object RecordsToday {
        const val CURRENT_RECORD = 2
        const val RECORD_TO_PROGRESS = 2
    }

    /**
     * The spec's "Novo" column (docs/superpowers/specs/2026-10-31-phase5-progress-history-design.md, "Orçamento de
     * toques"), counted as the baseline was (controller, 2026-10-31): the spec's "1 + typing" for the current record is
     * the tab and the search field's focus, 2, the same as today.
     */
    private object RecordsNew {
        const val CURRENT_RECORD = 2
        const val RECORD_TO_PROGRESS = 2
        const val ESTIMATED_ONE_REP_MAX = 3
    }

    /** The user's touches: a tap or a gesture counts 1. Each waits for the screen to settle. */
    private class Taps(private val test: ComposeUiTest) {
        var count = 0
            private set

        fun tap(node: SemanticsNodeInteraction) {
            node.performClick()
            count++
            test.waitForIdle()
        }

        fun gesture(node: SemanticsNodeInteraction, block: TouchInjectionScope.() -> Unit) {
            node.performTouchInput(block)
            count++
            test.waitForIdle()
        }
    }

    private fun assertWithin(flow: String, taps: Taps, new: Int, today: Int? = null) {
        println("TAP budget $flow: ${taps.count} (new $new, today ${today ?: "-"})")
        assertTrue(taps.count <= new, "$flow took ${taps.count} taps, the spec's budget is $new")
        if (today != null) assertTrue(taps.count <= today, "$flow took ${taps.count} taps, today it takes $today")
    }

    // --- The flows ------------------------------------------------------------------------------

    @Test
    fun prefilledSet() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("prefilledSet", taps, New.PREFILLED, Today.PREFILLED)
    }

    @Test
    fun sameAsLastWeekWithoutTarget() = budget(targets = false) { vm, ids ->
        // No target: the set opens empty, with last week's 80 kg × 8 as its suggestion.
        assertEquals(0 to 0.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        val taps = Taps(this)
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("sameAsLastWeekWithoutTarget", taps, New.SAME_AS_LAST_WEEK, Today.SAME_AS_LAST_WEEK)
    }

    @Test
    fun oneLoadStepUpAndComplete() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(field(ids[0], SetRowLoadTag))
        taps.tap(onNodeWithTag(StepperIncreaseTag))
        await("the load is saved at 82.5") { set(vm, ids[0])?.weightKg == 82.5 }
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(8 to 82.5, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("oneLoadStepUpAndComplete", taps, New.LOAD_STEP_UP, Today.LOAD_STEP_UP)
    }

    @Test
    fun oneRepLessAndComplete() = budget { vm, ids ->
        val taps = Taps(this)
        taps.tap(field(ids[0], SetRowRepsTag))
        taps.tap(onNodeWithTag(StepperDecreaseTag))
        await("the reps are saved at 7") { set(vm, ids[0])?.reps == 7 }
        taps.tap(check(ids[0]))
        await("set 1 is saved done") { set(vm, ids[0])?.completed == true }
        assertEquals(7 to 80.0, set(vm, ids[0])!!.let { it.reps to it.weightKg })
        assertWithin("oneRepLessAndComplete", taps, New.REP_LESS, Today.REP_LESS)
    }

    /**
     * Set 1 is done and set 2 still to do, so the bench is the current exercise and its card is
     * open: "Add set" gives a set 3 with the last set's load and no reps, whose suggestion is the
     * done set above (80 × 8), and its check saves that.
     */
    @Test
    fun addSetLikeAboveAndComplete() = budget(done = 1) { vm, ids ->
        val entryId = active(vm).exercises.single().id
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseFooterTag(entryId)))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNode(hasTestTag(ExerciseAddSetTag) and hasAnyAncestor(hasTestTag(exerciseFooterTag(entryId)))))
        await("set 3 is added") { active(vm).exercises.single().sets.size == 3 }
        val added = active(vm).exercises.single().sets.last().id
        scrollToRow(added)
        taps.tap(check(added))
        await("set 3 is saved done") { set(vm, added)?.completed == true }
        assertEquals(8 to 80.0, set(vm, added)!!.let { it.reps to it.weightKg })
        assertWithin("addSetLikeAboveAndComplete", taps, New.ADD_SET, Today.ADD_SET)
    }

    /**
     * The same, once the bench's last set is done: its card has collapsed (D9), so it opens first.
     * One tap over the spec's "Novo" (2), still within today's (4): measured and reported, not a
     * budget of its own in the spec's table.
     */
    @Test
    fun addSetAfterTheLastSetOpensTheCardFirst() = budget(done = 2) { vm, _ ->
        val entryId = active(vm).exercises.single().id
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseHeaderTag(entryId)))
        waitForIdle()
        assertTrue(!exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true), "the done card is open")
        val taps = Taps(this)
        taps.tap(onNode(hasTestTag(ExerciseHeaderToggleTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId)))))
        await("the card opens") { exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true) }
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseFooterTag(entryId)))
        waitForIdle()
        taps.tap(onNode(hasTestTag(ExerciseAddSetTag) and hasAnyAncestor(hasTestTag(exerciseFooterTag(entryId)))))
        await("set 3 is added") { active(vm).exercises.single().sets.size == 3 }
        val added = active(vm).exercises.single().sets.last().id
        scrollToRow(added)
        taps.tap(check(added))
        await("set 3 is saved done") { set(vm, added)?.completed == true }
        assertEquals(8 to 80.0, set(vm, added)!!.let { it.reps to it.weightKg })
        println("TAP budget addSetAfterTheLastSet: ${taps.count} (new ${New.ADD_SET} with the card open, today ${Today.ADD_SET})")
        assertEquals(New.ADD_SET + 1, taps.count)
        assertTrue(taps.count <= Today.ADD_SET, "adding a set after the last one took ${taps.count} taps, today it takes ${Today.ADD_SET}")
    }

    @Test
    fun markWarmup() = budget { vm, ids ->
        val taps = Taps(this)
        taps.gesture(onNode(row(ids[0]))) { click(Offset(width * NUMBER_X, NUMBER_Y.dp.toPx())) }
        taps.tap(onNode(hasText("Warm-up") and hasAnyAncestor(hasTestTag(SHEET))))
        await("set 1 is saved as a warm-up") { set(vm, ids[0])?.warmup == true }
        assertEquals(false, set(vm, ids[0])!!.completed)
        assertWithin("markWarmup", taps, New.WARMUP, Today.WARMUP)
    }

    @Test
    fun removeSetBySwipe() = budget { vm, ids ->
        val taps = Taps(this)
        // Leftwards from its check, as a thumb does.
        taps.gesture(onNode(row(ids[0]))) {
            swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400)
        }
        await("set 1 is removed") { set(vm, ids[0]) == null }
        assertEquals(listOf(ids[1]), active(vm).exercises.single().sets.map { it.id })
        assertWithin("removeSetBySwipe", taps, New.REMOVE_BY_SWIPE)
    }

    @Test
    fun removeSetBySheet() = budget { vm, ids ->
        val taps = Taps(this)
        taps.gesture(onNode(row(ids[0]))) { click(Offset(width * NUMBER_X, NUMBER_Y.dp.toPx())) }
        taps.tap(onNode(hasText("Remove set") and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))))
        await("set 1 is removed") { set(vm, ids[0]) == null }
        assertEquals(listOf(ids[1]), active(vm).exercises.single().sets.map { it.id })
        // The accessible alternative to the swipe: held to the spec's 2, not to today's 1 (the old X).
        assertWithin("removeSetBySheet", taps, New.REMOVE_BY_SHEET)
    }

    @Test
    fun skipRestAndPlusFifteen() = budget(done = 1) { vm, _ ->
        // A rest running, as after a check (not counted).
        vm.startTimer("Bench press", REST_SECONDS)
        await("the rest runs") { vm.timerUi.value.running }
        await("the dock shows") { exists(hasTestTag(TimerDockTag)) }
        val skip = Taps(this)
        skip.tap(onNode(hasTestTag(TimerSkipTag) and hasAnyAncestor(hasTestTag(TimerDockTag))))
        await("the rest is skipped") { !vm.timerUi.value.running && !vm.timerUi.value.paused }
        assertWithin("skipRest", skip, New.SKIP, Today.SKIP)

        vm.startTimer("Bench press", REST_SECONDS)
        await("the rest runs again") { vm.timerUi.value.running }
        await("the dock shows again") { exists(hasTestTag(TimerPlusTag) and hasAnyAncestor(hasTestTag(TimerDockTag))) }
        val before = vm.timerUi.value.remainingSeconds
        val plus = Taps(this)
        plus.tap(onNode(hasTestTag(TimerPlusTag) and hasAnyAncestor(hasTestTag(TimerDockTag))))
        await("+15 s is saved") { vm.timerUi.value.remainingSeconds >= before + 10 }
        assertWithin("plusFifteen", plus, New.PLUS_FIFTEEN, Today.PLUS_FIFTEEN)
    }

    /** From the header, where it always is: no scrolling, unlike today's button at the end of the list. */
    @Test
    fun finish() = budget(done = 1) { vm, _ ->
        val workoutId = active(vm).id
        val taps = Taps(this)
        taps.tap(onNodeWithTag(WorkoutHeaderTags.FINISH))
        await("the finish dialog opens") { exists(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))) }
        taps.tap(onNode(hasClickAction() and hasText("Finish") and hasAnyAncestor(hasTestTag(DIALOG))))
        await("the workout is saved finished") { vm.contentUi.value.active == null && vm.contentUi.value.history.any { it.id == workoutId } }
        assertWithin("finish", taps, New.FINISH, Today.FINISH)
    }

    /**
     * From the Train tab: the Library tab, the "Back" chip, the first back exercise of the results;
     * its detail opens. Scrolling to the row does not count (it is the first result).
     */
    @Test
    fun findByMuscleAndOpen() = libraryBudget(ShellTabs.TRAIN) { vm ->
        val taps = Taps(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.LIBRARY)))
        await("the library's list is built") { exists(hasTestTag(LibraryTags.COUNT)) }
        taps.tap(onNodeWithTag(LibraryTags.muscleChip("Back")))
        val model = LibraryModel.build(vm.contentUi.value.exercises, portuguese = false)
        val first = model.results("", LibraryFilters(muscles = setOf("Back"))).first()
        await("the back exercises are listed") { exists(hasTestTag(LibraryTags.row(first.id))) }
        onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(LibraryTags.row(first.id)))
        waitForIdle()
        taps.tap(onNodeWithTag(LibraryTags.row(first.id)))
        await("the detail opens") { exists(hasTestTag(ExerciseInfoScreenTag)) }
        assertEquals("Back", first.muscle)
        assertEquals(model.shownName(first), onNodeWithTag(ExerciseInfoTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        assertWithin("findByMuscleAndOpen", taps, LibraryNew.FIND_BY_MUSCLE_AND_OPEN, LibraryToday.FIND_BY_MUSCLE_AND_OPEN)
    }

    /**
     * From the library: "Create exercise", the name typed (not counted, nor the focus it takes),
     * "Chest", "Dumbbell" (the convention takes its default, "Per hand") and "Save"; the new
     * exercise is saved and its detail opens. The form does not focus the name field when it opens,
     * so a person taps it first: 5 touches, against today's 7 by the same count (the baseline's
     * "Contador" leaves that tap out on both sides).
     */
    @Test
    fun createCustomExerciseWithNameMuscleAndEquipment() = libraryBudget(ShellTabs.LIBRARY) { vm ->
        await("the library's list is built") { exists(hasTestTag(LibraryTags.COUNT)) }
        val taps = Taps(this)
        taps.tap(onNodeWithTag(LibraryTags.CREATE))
        await("the form opens") { exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextInput("Machine row")
        waitForIdle()
        taps.tap(onNodeWithTag(CustomExerciseFormTags.muscle("Chest")))
        taps.tap(onNodeWithTag(CustomExerciseFormTags.equipment("Dumbbell")))
        taps.tap(onNodeWithTag(CustomExerciseFormTags.SAVE))
        await("the exercise is saved") { vm.contentUi.value.exercises.any { it.custom && it.name == "Machine row" } }
        val saved = vm.contentUi.value.exercises.single { it.custom && it.name == "Machine row" }
        assertEquals(listOf("Chest", "Dumbbell", "Per hand"), listOf(saved.muscle, saved.equipment, saved.convention))
        await("its detail opens") { exists(hasTestTag(ExerciseInfoScreenTag)) && !exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
        assertEquals("Machine row", onNodeWithTag(ExerciseInfoTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        assertWithin("createCustomExerciseWithNameMuscleAndEquipment", taps, LibraryNew.CREATE_CUSTOM_EXERCISE, LibraryToday.CREATE_CUSTOM_EXERCISE)
    }

    // --- The routines' flows (phase 4B) ----------------------------------------------------------

    /**
     * From the Train tab: "New routine" (the default name kept), "Create", "Add exercises", the five rows marked
     * (scrolling to a row does not count) and "Add 5 exercises": 9 touches, against today's 13.
     */
    @Test
    fun buildARoutineWithFiveExercises() = routinesBudget(RoutinesScreens.TRAIN) { vm, _, screen ->
        val five = ExerciseCatalog.exercises.drop(7).take(5).map { it.id }
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.NEW_ROUTINE))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNodeWithTag(TrainTags.NEW_ROUTINE))
        await("the name sheet opens") { exists(hasTestTag(RenameSheetTags.FIELD)) }
        taps.tap(onNodeWithTag(RenameSheetTags.SAVE))
        await("the editor of the new routine opens") {
            screen.value.startsWith("editor:") && exists(hasTestTag(RoutineEditorTags.EMPTY))
        }
        val created = screen.value.removePrefix("editor:")
        taps.tap(onNodeWithTag(RoutineEditorTags.ADD))
        for (id in five) taps.tap(pickerRow(id))
        taps.tap(onNodeWithTag(ExercisePickerAddTag))
        await("the routine holds the five") { vm.contentUi.value.routines.first { it.id == created }.exercises.map { it.exercise.id } == five }
        assertWithin("buildARoutineWithFiveExercises", taps, RoutinesNew.BUILD_WITH_FIVE_EXERCISES, RoutinesToday.BUILD_WITH_FIVE_EXERCISES)
    }

    /** In the editor of a routine of five: the fifth's menu, "Move to top": 2 touches, against today's 4 (one place a tap). */
    @Test
    fun moveTheFifthExerciseToTheTop() = routinesBudget(editor = true) { vm, routineId, _ ->
        val before = vm.contentUi.value.routines.first { it.id == routineId }.exercises.map { it.id }
        assertEquals(5, before.size)
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.menu(before[4])))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNodeWithTag(RoutineEditorTags.menu(before[4])))
        taps.tap(onNodeWithTag(RoutineEditorTags.EXERCISE_TOP))
        await("the fifth is first") { vm.contentUi.value.routines.first { it.id == routineId }.exercises.map { it.id } == listOf(before[4]) + before.take(4) }
        assertWithin("moveTheFifthExerciseToTheTop", taps, RoutinesNew.MOVE_FIFTH_TO_TOP, RoutinesToday.MOVE_FIFTH_TO_TOP)
    }

    /** "Add set" under the first exercise: one touch, and the new set is the one above (same reps and load). */
    @Test
    fun addAPlannedSetLikeTheOneAbove() = routinesBudget(editor = true) { vm, routineId, _ ->
        fun first() = vm.contentUi.value.routines.first { it.id == routineId }.exercises.first()
        val before = first()
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.addSet(before.id)))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNodeWithTag(RoutineEditorTags.addSet(before.id)))
        await("the set is added") { first().setTargets.size == before.setTargets.size + 1 }
        val sets = first().setTargets
        assertEquals(sets[sets.size - 2].reps to sets[sets.size - 2].weightKg, sets.last().reps to sets.last().weightKg)
        assertWithin("addAPlannedSetLikeTheOneAbove", taps, RoutinesNew.ADD_PLANNED_SET, RoutinesToday.ADD_PLANNED_SET)
    }

    /** The first set's label, then "Warm-up": 2 touches (the old editor had no planned sets). */
    @Test
    fun markAPlannedSetAsWarmup() = routinesBudget(editor = true) { vm, routineId, _ ->
        val entry = vm.contentUi.value.routines.first { it.id == routineId }.exercises.first()
        val set = entry.setTargets.first().id
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.setLabel(set)))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNodeWithTag(RoutineEditorTags.setLabel(set)))
        taps.tap(onNodeWithTag(PlannedSetMenuTags.WARMUP))
        await("the set is saved as a warm-up") {
            vm.contentUi.value.routines.first { it.id == routineId }.exercises.first().setTargets.first().warmup
        }
        assertWithin("markAPlannedSetAsWarmup", taps, RoutinesNew.MARK_WARMUP)
    }

    /** "Start" on the card of the Train tab: one touch, and the workout is the routine's. */
    @Test
    fun startAWorkoutFromARoutine() = routinesBudget(RoutinesScreens.TRAIN) { vm, routineId, screen ->
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.start(routineId)))
        waitForIdle()
        val taps = Taps(this)
        taps.tap(onNodeWithTag(TrainTags.start(routineId)))
        await("the workout starts and opens") { vm.contentUi.value.active != null && screen.value == RoutinesScreens.WORKOUT }
        assertEquals(routineId, active(vm).sourceRoutineId)
        assertWithin("startAWorkoutFromARoutine", taps, RoutinesNew.START_FROM_ROUTINE, RoutinesToday.START_FROM_ROUTINE)
    }

    // --- The records' flows (phase 5A) -----------------------------------------------------------

    /**
     * From the Train tab, with the squat's record the newest of all (on top without a search): the Records tab and
     * the search field (its focus); "bench" is typed (not counted) and the bench's current load record, with
     * "Current", is the timeline's first row. 2 touches, as today.
     */
    @Test
    fun seeTheCurrentRecordOfAnExercise() = progressBudget(squat = true) { vm, seed, _ ->
        val bench = newestRecord(vm, seed, BENCH_PRESS)
        val taps = Taps(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.RECORDS)))
        await("the timeline is built") { topRecordRow() != null }
        assertEquals(recordRowTag(newestRecord(vm, seed, BACK_SQUAT)), topRecordRow(), "without a search the squat is on top")
        val search = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(RecordsTags.SEARCH)))
        taps.tap(search)
        search.performTextInput("bench")
        waitForIdle()
        await("the bench's record is on top") { topRecordRow() == recordRowTag(bench) }
        assertTrue(exists(hasTestTag(RecordsTags.ROW_CURRENT) and hasAnyAncestor(hasTestTag(recordRowTag(bench))), unmerged = true), "the top row says it is current")
        assertWithin("seeTheCurrentRecordOfAnExercise", taps, RecordsNew.CURRENT_RECORD, RecordsToday.CURRENT_RECORD)
    }

    /**
     * From the Train tab, with the bench's load record the newest (the timeline's first row, as the baseline's first
     * card): the Records tab and the row; the progress opens on the load, with its chart. 2 touches, as today.
     */
    @Test
    fun fromARecordToTheExerciseProgress() = progressBudget(squat = false) { vm, seed, screen ->
        val row = recordRowTag(newestRecord(vm, seed, BENCH_PRESS))
        val taps = Taps(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.RECORDS)))
        await("the bench's record is on top") { topRecordRow() == row }
        taps.tap(onNodeWithTag(row))
        await("the progress opens on the load") { screen.value == ProgressScreens.progress(BENCH_PRESS, ProgressMetric.LOAD.route) }
        awaitProgressChart("Load")
        assertWithin("fromARecordToTheExerciseProgress", taps, RecordsNew.RECORD_TO_PROGRESS, RecordsToday.RECORD_TO_PROGRESS)
    }

    /** From the Train tab: the Records tab, the bench's row and the "Estimated 1RM" chip; its chart is drawn. 3 touches (no baseline). */
    @Test
    fun seeTheEstimatedOneRepMaxOfTheBench() = progressBudget(squat = false) { vm, seed, _ ->
        val row = recordRowTag(newestRecord(vm, seed, BENCH_PRESS))
        val taps = Taps(this)
        taps.tap(onNodeWithTag(tabTag(ShellTabs.RECORDS)))
        await("the bench's record is on top") { topRecordRow() == row }
        taps.tap(onNodeWithTag(row))
        val chip = ProgressTags.metric(ProgressMetric.E1RM)
        await("the metrics show") { exists(hasTestTag(chip)) }
        scrollProgressTo(chip)
        taps.tap(onNodeWithTag(chip))
        awaitProgressChart("Estimated 1RM")
        scrollProgressTo(chip)
        onNodeWithTag(chip).assertIsSelected()
        assertWithin("seeTheEstimatedOneRepMaxOfTheBench", taps, RecordsNew.ESTIMATED_ONE_REP_MAX)
    }

    /** The load record of [exerciseId] in the newest of the seeded workouts. */
    private fun newestRecord(vm: FlowViewModel, seed: BenchSeed, exerciseId: String): RecordUi =
        vm.contentUi.value.history.single { it.id == seed.benchWorkouts.last() }.records
            .single { it.exercise.id == exerciseId && it.kind == RecordKind.WEIGHT }

    /** The picker's "All" row of [id], once its list is built (off the main thread) and scrolled to; the scroll is not a touch. */
    private fun ComposeUiTest.pickerRow(id: String): SemanticsNodeInteraction {
        val tag = exercisePickerRowTag(id, recent = false)
        await("the picker lists $id") {
            if (!exists(hasTestTag(tag)) && exists(hasTestTag(ExercisePickerListTag))) {
                runCatching { onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag)) }
            }
            exists(hasTestTag(tag))
        }
        return onNodeWithTag(tag)
    }

    // --- The stack ------------------------------------------------------------------------------

    /**
     * Seeds the stack ([seed]), opens the workout screen as the app hosts it ([FlowApp]) in English
     * at 1.0, waits for the workout, and runs [flow] with the view model and the bench's set ids.
     */
    private fun budget(
        targets: Boolean = true,
        done: Int = 0,
        flow: suspend ComposeUiTest.(vm: FlowViewModel, setIds: List<String>) -> Unit,
    ) = runComposeUiTest(testTimeout = 2.minutes) {
        val harness = FlowHarness()
        try {
            seed(harness.repository, targets, done)
            val vm = harness.createViewModel()
            withScreen(locale = "en") { FlowApp(vm) }
            waitForIdle()
            await("the workout opens") { !vm.contentUi.value.loading && vm.contentUi.value.active != null }
            waitForIdle()
            val ids = active(vm).exercises.single().sets.map { it.id }
            assertEquals(2, ids.size)
            if (done < ids.size) scrollToRow(ids[done])
            flow(vm, ids)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * The routines' stack: the catalog, a routine "Push day" and the app's screens ([RoutinesFlowApp]) in English at
     * 1.0, on the Train tab ([start]) or, with [editor], on that routine's editor; the routine holds five exercises
     * with [editor] (the first with a warm-up-able set), else only the bench. Runs [flow] with the view model, the
     * routine's id and the screen.
     */
    private fun routinesBudget(
        start: String = RoutinesScreens.TRAIN,
        editor: Boolean = false,
        flow: suspend ComposeUiTest.(vm: FlowViewModel, routineId: String, screen: MutableState<String>) -> Unit,
    ) = runComposeUiTest(testTimeout = 3.minutes) {
        val harness = FlowHarness()
        try {
            harness.repository.initialize()
            val routineId = harness.repository.createRoutine("Push day")
            val exercises = if (editor) ExerciseCatalog.exercises.drop(7).take(5).map { it.id } else listOf(BENCH)
            for (id in exercises) harness.repository.addRoutineExercise(routineId, id)
            val vm = harness.createViewModel()
            val screen = mutableStateOf(if (editor) RoutinesScreens.editor(routineId) else start)
            withScreen(locale = "en") { RoutinesFlowApp(vm, screen) }
            waitForIdle()
            await("the routine loads") { !vm.contentUi.value.loading && vm.contentUi.value.routines.any { it.id == routineId } }
            waitForIdle()
            flow(vm, routineId, screen)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * The records' stack: six bench press workouts, with the back squat in the last two when [squat]
     * ([seedBenchProgress]), and the app's screens ([ProgressFlowApp]) in English at 1.0 on the Train tab. Runs [flow]
     * with the view model, what was seeded and the screen.
     */
    private fun progressBudget(
        squat: Boolean,
        flow: suspend ComposeUiTest.(vm: FlowViewModel, seed: BenchSeed, screen: MutableState<String>) -> Unit,
    ) = runComposeUiTest(testTimeout = 3.minutes) {
        val harness = FlowHarness()
        try {
            val seed = seedBenchProgress(harness.repository, squat)
            val vm = harness.createViewModel()
            val screen = mutableStateOf(ProgressScreens.TRAIN)
            withScreen(locale = "en") { ProgressFlowApp(vm, screen) }
            waitForIdle()
            await("the history loads") { !vm.contentUi.value.loading && vm.contentUi.value.history.size == seed.benchWorkouts.size }
            waitForIdle()
            flow(vm, seed, screen)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * Seeds the catalog, opens the library as the app hosts it ([LibraryApp]) in English at 1.0 on
     * [tab], waits for the state, and runs [flow] with the view model.
     */
    private fun libraryBudget(tab: String, flow: suspend ComposeUiTest.(vm: FlowViewModel) -> Unit) = runComposeUiTest(testTimeout = 2.minutes) {
        val harness = FlowHarness()
        try {
            harness.repository.initialize()
            val vm = harness.createViewModel()
            withScreen(locale = "en") { LibraryApp(vm, tab) }
            waitForIdle()
            await("the state loads") { !vm.contentUi.value.loading && vm.contentUi.value.exercises.isNotEmpty() }
            waitForIdle()
            flow(vm)
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    /**
     * The library and the exercise detail as `:app` hosts them (`MoosclesApp`), without its NavHost:
     * the tab's content over the shell's bottom (the tab bar), starting on [startTab]; the Library
     * tab shows [LibraryRoute], a row opens [ExerciseInfoRoute] in its place, and choosing a tab
     * leaves the detail. Clear of the system bars and the keyboard, as the app's window is.
     */
    @Composable
    private fun LibraryApp(viewModel: FlowViewModel, startTab: String) {
        val state by viewModel.contentUi.collectAsState()
        val timer = viewModel.timerUi.collectAsState()
        var tab by rememberSaveable { mutableStateOf(startTab) }
        var detail by rememberSaveable { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val open = detail
                when {
                    open != null -> ExerciseInfoRoute(
                        exerciseId = open,
                        state = state,
                        actions = viewModel,
                        onBack = { detail = null },
                        onOpenHistory = {},
                        onOpenRoutine = {},
                        onOpenWorkout = {},
                    )
                    tab == ShellTabs.LIBRARY -> LibraryRoute(state, viewModel, onOpenExercise = { detail = it }, onOpenSettings = {})
                    else -> Box(Modifier.fillMaxSize())
                }
            }
            AppShellBottom(
                state = state,
                timer = timer,
                selectedTab = tab,
                onSelectTab = {
                    tab = it
                    detail = null
                },
                onReselectTab = { detail = null },
                onOpenWorkout = {},
                actions = viewModel,
                imeInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }

    /**
     * A finished workout a week ago with two sets of 80 kg × 8 on the bench, then the workout in
     * progress: from the routine "Push day" (the bench, whose two sets take that record as their
     * target, so they open at 80 × 8) with [targets], else an empty workout with the bench added
     * (two empty sets). The first [done] sets are already done, at 80 × 8.
     */
    private suspend fun seed(repository: WorkoutRepository, targets: Boolean, done: Int) {
        repository.initialize()
        val past = currentTimeMillis() - WEEK_MS
        val old = repository.startEmpty("Push", now = past)
        repository.addWorkoutExercise(old, BENCH)
        repository.workoutSets(old).forEach { repository.updateSet(it.copy(reps = 8, weightKg = 80.0, completed = true)) }
        check(repository.finishWorkout(old, now = past + HOUR_MS))
        val workout = if (targets) {
            val routine = repository.createRoutine("Push day")
            repository.addRoutineExercise(routine, BENCH)
            repository.startRoutine(routine)
        } else {
            repository.startEmpty("Push day").also { repository.addWorkoutExercise(it, BENCH) }
        }
        repository.workoutSets(workout).sortedBy { it.position }.take(done).forEach {
            repository.updateSet(it.copy(reps = 8, weightKg = 80.0, completed = true))
        }
    }

    private companion object {
        const val BENCH = "catalog_barbell_bench_flat"
        const val HOUR_MS = 3_600_000L
        const val WEEK_MS = 7 * 24 * HOUR_MS
        const val REST_SECONDS = 90

        /**
         * Where the set's number is: near the row's start, in its first 56 dp (the row may have
         * two lines, the number on the first). A tap there opens the set type sheet.
         */
        const val NUMBER_X = 0.05f
        const val NUMBER_Y = 28

        // The design system's internal tags (DialogTag, SheetTag).
        const val DIALOG = "dialog"
        const val SHEET = "sheet"
    }
}
