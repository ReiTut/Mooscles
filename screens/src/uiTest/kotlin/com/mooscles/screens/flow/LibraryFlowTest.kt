package com.mooscles.screens.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import com.mooscles.data.Equipment
import com.mooscles.data.MuscleGroup
import com.mooscles.data.WorkoutRepository
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.illustration.AnimatedFigureTag
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.exercise.AddToRoutineSheetTags
import com.mooscles.screens.exercise.CustomExerciseFormTags
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.withScreen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The library from search to a new exercise over the real stack (spec D6): the common
 * [com.mooscles.MoosclesViewModel], Room through the platform's own driver and DataStore, each in a
 * fresh directory ([FlowHarness]), with [LibraryRoute] and [ExerciseInfoRoute] as the app hosts
 * them ([LibraryFlowApp]: a plain screen switch, no NavHost, whose saved state survives a visit to
 * the detail as a back stack entry's does). It runs on the emulator and on the iOS simulator.
 *
 * Twice: Portuguese, light, at 1.0, and English, dark, at 2.0 (2.01 on every platform, the linear
 * 200% iOS draws: [LINEAR_TWO_X_FONT_SCALE]). Each run seeds the routine "Treino A" and a finished
 * workout with the incline dumbbell bench press, then: searches it ("supino inclinado halter" or
 * "incline dumbbell") and opens it; sees its figure or icon, the muscle sentence, the cues and the
 * history card with the seeded best set; "Add to routine" → "Treino A", whose toast comes only
 * with the saved echo and lies inside the window (pre-flight F24); back, the search cleared, the
 * "Back" muscle chip, and a result opened; back again and, with "Back" still on, "supino
 * articulado", which finds nothing there (in Portuguese, without a filter, it would find the
 * plate-loaded chest presses), so the empty state offers "Create “supino articulado”"; the form,
 * prefilled, with a muscle and an equipment chosen, saved; and the new exercise's detail. Each step
 * is checked in the view model's state, which comes from the database. In each main state
 * ([sweep]): every clickable node has a label and a 48 dp target, every number is shown whole and
 * a capture is taken (a PNG on iOS, `screens/build/ios-captures/library-<state>.png`).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryFlowTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun libraryFlowPortugueseLightAtOneX() = runComposeUiTest(testTimeout = 5.minutes) { flow(PORTUGUESE) }

    @Test
    fun libraryFlowEnglishDarkAtTwoX() = runComposeUiTest(testTimeout = 5.minutes) { flow(ENGLISH) }

    /** One run's language, look and the texts it types and expects. */
    private class Run(
        val name: String,
        val locale: String,
        val dark: Boolean,
        val fontScale: Float,
        val query: String,
        val inclineName: String,
        val musclesStart: String,
        val best: String,
        val added: String,
        val back: String,
        val create: String,
    )

    /** What [seed] made. */
    private class Seeded(val routineId: String, val pastWorkoutId: String)

    /**
     * The routine "Treino A" with the flat bench, and a workout finished a week ago with the incline
     * dumbbell bench press, two sets of 30 kg × 10 (its best set and its history).
     */
    private suspend fun seed(repository: WorkoutRepository): Seeded {
        repository.initialize()
        val routine = repository.createRoutine(ROUTINE)
        repository.addRoutineExercise(routine, BENCH)
        val past = currentTimeMillis() - WEEK_MS
        val old = repository.startEmpty("Peito", now = past)
        repository.addWorkoutExercise(old, INCLINE)
        repository.workoutSets(old).forEach { repository.updateSet(it.copy(reps = 10, weightKg = 30.0, completed = true)) }
        check(repository.finishWorkout(old, now = past + HOUR_MS))
        return Seeded(routine, old)
    }

    private suspend fun ComposeUiTest.flow(run: Run) {
        val harness = FlowHarness()
        try {
            val seeded = seed(harness.repository)
            val vm = harness.createViewModel()
            val screen = mutableStateOf<String?>(null)
            val window = mutableStateOf(IntSize.Zero)
            withScreen(dark = run.dark, fontScale = run.fontScale, locale = run.locale) { LibraryFlowApp(vm, screen, window) }
            waitForIdle()
            await("the saved data loads") { !vm.contentUi.value.loading && vm.contentUi.value.routines.isNotEmpty() }
            awaitList()
            sweep(run, "list")

            // 1. Search the incline dumbbell bench press and open it.
            search(run.query)
            val incline = LibraryTags.row(INCLINE)
            await("the search finds it") { exists(hasTestTag(LibraryTags.COUNT), unmerged = true) && rowShown(incline) }
            sweep(run, "search")
            onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(incline))
            waitForIdle()
            onNodeWithTag(incline).performClick()
            await("its detail opens") { screen.value == INCLINE }
            waitForIdle()

            // 2. The detail: the name, the figure or the icon, the muscles, the cues and the history.
            onNodeWithTag(ExerciseInfoTags.TITLE).assertTextEquals(run.inclineName)
            assertMedia()
            val muscles = textOf(onNodeWithTag(ExerciseInfoTags.MUSCLE_TEXT, useUnmergedTree = true))
            assertTrue(muscles.startsWith(run.musclesStart), "muscle sentence: $muscles")
            await("the cues show") { exists(hasTestTag(ExerciseInfoTags.cue(0)), unmerged = true) }
            onNodeWithTag(ExerciseInfoTags.HISTORY_BEST).performScrollTo()
            waitForIdle()
            val best = mergedText(onNodeWithTag(ExerciseInfoTags.HISTORY_BEST))
            assertEquals(run.best, best)
            onNodeWithTag(ExerciseInfoTags.HISTORY_SESSIONS, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            sweep(run, "detail", numbers = 1)

            // 3. "Add to routine" → "Treino A": the toast comes with the saved echo, inside the window.
            onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE).performScrollTo().performClick()
            await("the routine sheet opens") { exists(hasTestTag(AddToRoutineSheetTags.routine(seeded.routineId))) }
            waitForIdle()
            sweep(run, "routine-sheet", numbers = 0)
            // The toast holds for `timing.toastAction` on the effects' clock: freeze it from the tap
            // until the toast is checked, and step it frame by frame (nothing scrolls meanwhile).
            mainClock.autoAdvance = false
            try {
                onNodeWithTag(AddToRoutineSheetTags.routine(seeded.routineId)).performClick()
                await("the routine is saved with the exercise") {
                    vm.contentUi.value.routines.first { it.id == seeded.routineId }.exercises.any { it.exercise.id == INCLINE }
                }
                val toast = onNode(hasText(run.added) and hasAnyAncestor(hasTestTag(TOAST)), useUnmergedTree = true)
                var frames = 0
                while (!exists(hasText(run.added) and hasAnyAncestor(hasTestTag(TOAST)), unmerged = true) && frames < TOAST_FRAMES) {
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    frames++
                }
                // Its entrance (`motion.base`), far within its hold (6 s).
                repeat(ENTRANCE_FRAMES) { mainClock.advanceTimeByFrame() }
                waitForIdle()
                toast.assertIsDisplayed()
                assertInsideWindow(onNodeWithTag(TOAST, useUnmergedTree = true), window.value, "the \"${run.added}\" toast")
                onNodeWithTag(AddToRoutineSheetTags.ROOT).assertDoesNotExist()
                sweep(run, "added", numbers = 1)
            } finally {
                mainClock.autoAdvance = true
            }
            val items = harness.repository.snapshot().routineItems(seeded.routineId).map { it.exerciseId }
            assertEquals(listOf(BENCH, INCLINE), items)

            // 4. Back; the search cleared; the "Back" chip; a result opened.
            goBack(run, screen)
            awaitList()
            onNode(hasSetTextAction()).performTextReplacement("")
            waitForIdle()
            onNodeWithTag(LibraryTags.muscleChip(MuscleGroup.BACK.label)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(LibraryTags.muscleChip(MuscleGroup.BACK.label)).assertIsOn()
            await("the back exercises show") { shownRowIds().isNotEmpty() }
            val backIds = shownRowIds()
            val exercises = vm.contentUi.value.exercises.associateBy { it.id }
            for (id in backIds) assertEquals(MuscleGroup.BACK.label, exercises.getValue(id).muscle, "row $id")
            sweep(run, "filtered", numbers = 0)
            val first = backIds.first()
            onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(LibraryTags.row(first)))
            waitForIdle()
            onNodeWithTag(LibraryTags.row(first)).performClick()
            await("the back exercise's detail opens") { screen.value == first }
            waitForIdle()
            onNodeWithTag(ExerciseInfoTags.TITLE).assertExists()
            assertMedia()
            onNodeWithTag(ExerciseInfoTags.HISTORY_NONE).performScrollTo().assertIsDisplayed()
            sweep(run, "filtered-detail", numbers = 0)

            // 5. Back, "Back" still on: "supino articulado" finds nothing, and the empty state offers to create it.
            goBack(run, screen)
            awaitList()
            onNodeWithTag(LibraryTags.muscleChip(MuscleGroup.BACK.label)).assertIsOn()
            search(NEW_NAME)
            await("nothing is found") { exists(hasTestTag(LibraryTags.EMPTY)) }
            val offer = hasText(run.create) and hasClickAction() and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))
            onNodeWithTag(LibraryTags.LIST).performScrollToNode(offer)
            waitForIdle()
            val create = onNode(offer)
            create.assertIsDisplayed()
            sweep(run, "no-results", numbers = 0)
            create.performClick()
            await("the form opens") { exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
            waitForIdle()
            val prefilled = onNodeWithTag(CustomExerciseFormTags.NAME).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            assertEquals(NEW_NAME, prefilled)
            tap(CustomExerciseFormTags.muscle(MuscleGroup.CHEST.label))
            tap(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label))
            sweep(run, "form", numbers = 0)

            // 6. Save: the form closes, and the new exercise's detail opens once the state brings it.
            val before = vm.contentUi.value.exercises.map { it.id }.toSet()
            onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
            await("the exercise is saved") { vm.contentUi.value.exercises.any { it.id !in before } }
            val created = vm.contentUi.value.exercises.single { it.id !in before }
            await("the new exercise's detail opens") { screen.value == created.id }
            await("the form closes") { !exists(hasTestTag(CustomExerciseFormTags.ROOT)) }
            waitForIdle()
            onNodeWithTag(ExerciseInfoTags.TITLE).assertTextEquals(NEW_NAME)
            onNode(hasTestTag(ExerciseEquipmentIconTag) and hasAnyAncestor(hasTestTag(ExerciseInfoTags.MEDIA)), useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag(ExerciseInfoTags.HISTORY_NONE).performScrollTo().assertIsDisplayed()
            sweep(run, "created", numbers = 0)

            // And on disk: the custom exercise as chosen, and the routine and the history untouched.
            val saved = checkNotNull(harness.repository.exercise(created.id)) { "the new exercise is not saved" }
            assertEquals(NEW_NAME, saved.name)
            assertEquals(MuscleGroup.CHEST, saved.muscle)
            assertEquals(Equipment.PLATE_LOADED_MACHINE, saved.equipment)
            assertTrue(saved.custom)
            assertEquals(listOf(BENCH, INCLINE), harness.repository.snapshot().routineItems(seeded.routineId).map { it.exerciseId })
            assertEquals(1, harness.repository.snapshot().workouts.size)
            checkNotNull(harness.repository.workout(seeded.pastWorkoutId))
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    // --- Finding and acting ---------------------------------------------------------------------

    /** The library's model is built off the main thread (`Dispatchers.Default`), which `waitForIdle` does not wait for. */
    private fun ComposeUiTest.awaitList() {
        await("the library's list is built") {
            exists(hasTestTag(LibraryTags.COUNT), unmerged = true) && !exists(hasTestTag(LibraryTags.SKELETON_ROW), unmerged = true)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.search(query: String) {
        onNode(hasSetTextAction()).performTextReplacement(query)
        waitForIdle()
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** The top bar's back, then the library again. */
    private fun ComposeUiTest.goBack(run: Run, screen: MutableState<String?>) {
        onNode(hasContentDescription(run.back) and hasClickAction()).performClick()
        await("the library is back") { screen.value == null && exists(hasTestTag(LibraryTags.LIST)) }
    }

    private fun ComposeUiTest.rowShown(tag: String): Boolean {
        if (!exists(hasTestTag(tag))) {
            if (!exists(hasTestTag(LibraryTags.LIST))) return false
            runCatching { onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(tag)) }
        }
        return exists(hasTestTag(tag))
    }

    /** The ids of the result rows composed now, top to bottom. */
    private fun ComposeUiTest.shownRowIds(): List<String> =
        onAllNodes(rowTag).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag].removePrefix(ROW_PREFIX) }

    private val rowTag = SemanticsMatcher("is a library row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ROW_PREFIX) == true
    }

    /** The detail's media frame holds the figure or, without one, the equipment's icon. */
    private fun ComposeUiTest.assertMedia() {
        val inMedia = hasAnyAncestor(hasTestTag(ExerciseInfoTags.MEDIA))
        val figure = exists(hasTestTag(AnimatedFigureTag) and inMedia, unmerged = true)
        val icon = exists(hasTestTag(ExerciseEquipmentIconTag) and inMedia, unmerged = true)
        assertTrue(figure || icon, "the media frame holds neither the figure nor the icon")
    }

    private fun textOf(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty().replace(NBSP, ' ')

    private fun mergedText(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty().replace(NBSP, ' ')

    // --- The checks of every main state -----------------------------------------------------------

    /**
     * The checks of a main state: a label and a 48 dp target on every clickable node (every root:
     * a sheet or the form too), at least [numbers] number texts, all whole ([assertNumbersWhole]),
     * and a capture, `library-<run>-<state>`.
     */
    private fun ComposeUiTest.sweep(run: Run, state: String, numbers: Int = 0) {
        waitForIdle()
        assertClickablesLabeledAndSized("${run.name} $state")
        assertNumbersWhole(atLeast = numbers)
        capture("library-${run.name}-$state")
    }

    private companion object {
        const val ROUTINE = "Treino A"
        const val BENCH = "catalog_barbell_bench_flat"
        const val INCLINE = "catalog_dumbbell_bench_incline"
        const val NEW_NAME = "supino articulado"
        const val ROW_PREFIX = "library-row-"

        const val HOUR_MS = 3_600_000L
        const val WEEK_MS = 7 * 24 * HOUR_MS
        /** At most this many frames for the toast to come in once the echo is in the state. */
        const val TOAST_FRAMES = 60
        /** About `motion.base` and more: the toast's entrance, done. */
        const val ENTRANCE_FRAMES = 30

        const val NBSP = '\u00A0'

        // The design system's internal tag (ToastTag).
        const val TOAST = "toast"

        val PORTUGUESE = Run(
            name = "pt-light-1x",
            locale = "pt-BR",
            dark = false,
            fontScale = 1f,
            query = "supino inclinado halter",
            inclineName = "Supino inclinado com halteres",
            musclesStart = "Principais: peito.",
            best = "Melhor 30 kg × 10",
            added = "Adicionado a $ROUTINE",
            back = "Voltar",
            create = "Criar “$NEW_NAME”",
        )

        val ENGLISH = Run(
            name = "en-dark-2x",
            locale = "en",
            dark = true,
            fontScale = LINEAR_TWO_X_FONT_SCALE,
            query = "incline dumbbell",
            inclineName = "Incline dumbbell bench press",
            musclesStart = "Primary: chest.",
            best = "Best 30 kg × 10",
            added = "Added to $ROUTINE",
            back = "Back",
            create = "Create “$NEW_NAME”",
        )
    }
}

/**
 * The library and the exercise detail as `:app` hosts them (`MoosclesApp`'s `library` and
 * `exerciseInfo/{id}` routes) over the view model's `contentUi`, switched by [screen] (null is the
 * library, an id that exercise's detail) instead of a NavHost. Each screen's saved state is kept in
 * a [rememberSaveableStateHolder] while the other shows, as a back stack entry's is: the library
 * keeps its search, filters and scroll under the detail, and a detail that is left is forgotten.
 * [window] receives the window's size, for the checks that a toast is in sight.
 */
@Composable
internal fun LibraryFlowApp(viewModel: FlowViewModel, screen: MutableState<String?>, window: MutableState<IntSize>) {
    val state by viewModel.contentUi.collectAsState()
    val holder = rememberSaveableStateHolder()
    val size = LocalWindowInfo.current.containerSize
    SideEffect { window.value = size }
    Box(Modifier.fillMaxSize()) {
        when (val id = screen.value) {
            null -> holder.SaveableStateProvider(LIBRARY_KEY) {
                LibraryRoute(state, viewModel, onOpenExercise = { screen.value = it }, onOpenSettings = {})
            }
            else -> holder.SaveableStateProvider("$INFO_KEY/$id") {
                ExerciseInfoRoute(
                    exerciseId = id,
                    state = state,
                    actions = viewModel,
                    onBack = {
                        screen.value = null
                        holder.removeState("$INFO_KEY/$id")
                    },
                    onOpenHistory = {},
                    onOpenRoutine = {},
                    onOpenWorkout = {},
                )
            }
        }
    }
}

private const val LIBRARY_KEY = "library"
private const val INFO_KEY = "exerciseInfo"
