package com.mooscles.screens.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.RoutineFolder
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenFrame
import com.mooscles.screens.WORKOUT_TARGET_DP
import com.mooscles.screens.assertAtLeast
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.isPlaced
import com.mooscles.screens.library.LibraryFilters
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.TALL
import com.mooscles.screens.library.TopAnchored
import com.mooscles.screens.routines.FolderManagerSheet
import com.mooscles.screens.routines.FolderTags
import com.mooscles.screens.routines.RoutineEditorRoute
import com.mooscles.screens.routines.RoutineEditorTag
import com.mooscles.screens.routines.RoutineEditorTags
import com.mooscles.screens.routines.TrainRoute
import com.mooscles.screens.routines.TrainScreenTag
import com.mooscles.screens.routines.TrainTags
import com.mooscles.screens.screenReaderOrder
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.workout.ExercisePickerAddTag
import com.mooscles.screens.workout.ExercisePickerListTag
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The accessibility and large text sweep of the phase 4B screens (spec "Acessibilidade", plan Task 17): the Train tab
 * (with routines, one of them resumable; an empty folder; loading), the folder manager and its delete dialog, the
 * routine editor (with a warm-up; with a set in edit; empty) and the multiple picker, each in light and dark × at 1.0,
 * 1.3 and 2.0 ([SWEEP_FONT_SCALES]: 2.01 too on Android) × on 320 and 412 dp wide phones, in Portuguese (the longer
 * texts), and at the top, the middle and the end of its scroll ([Pass]). One test per state: it reaches the state
 * once, then changes the theme, the scale and the width within the same composition ([ScreenFrame] over [Combo]
 * state) and checks each combination: every clickable has a label and a 48 dp target
 * ([assertClickablesLabeledAndSized]), "Start" and "Start workout" are 56 dp, every number is whole
 * ([assertNumbersWhole]; at least as many as the state shows), no button text is cut ([assertButtonTextsWhole]), every
 * item that can be dragged carries the screen reader's moves (the four, less the ones its place rules out), and, in a
 * window of its own (a sheet, a dialog), the text is laid out at the combination's scale ([assertDrawnAt]). All the
 * failures of a state are reported together.
 *
 * Plus the screen reader's order over the Train tab ([trainReadingOrder]: the top, the folders, the cards each with
 * its name and summary, then "Start" and its menu, the actions) and over the whole editor ([editorReadingOrder]: the
 * bar, the name, each exercise with its sets, "Add exercises", "Start workout").
 */
@OptIn(ExperimentalTestApi::class)
class RoutinesAccessibilitySweepTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    // --- The data -------------------------------------------------------------------------------

    private val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    private fun catalogExercise(id: String) = catalog.single { it.id == id }

    private val bench = catalogExercise("catalog_barbell_bench_flat")
    private val incline = catalogExercise("catalog_dumbbell_bench_incline")
    private val squat = catalogExercise("catalog_barbell_front_squat")

    private fun set(id: String, reps: Int, kg: Double?, inherited: Boolean = true, warmup: Boolean = false) =
        RoutineSetUi(id, reps, kg, inherited = inherited && !warmup, warmup = warmup)

    private fun planned(id: String, exercise: ExerciseUi, sets: List<RoutineSetUi>, rest: Int = 90) =
        PlannedExerciseUi(id, exercise, sets = sets.size, reps = sets.last().reps, weightKg = sets.last().weightKg, restSeconds = rest, setTargets = sets)

    /** The editor's routine: a warm-up and manual sets, sets that follow the record, one set, and two more to scroll. */
    private val editorEntries = listOf(
        planned("e1", bench, listOf(set("e1w", 10, 40.0, warmup = true), set("e1a", 8, 82.5, inherited = false), set("e1b", 8, 82.5, inherited = false)), rest = 135),
        planned("e2", incline, listOf(set("e2a", 10, 30.0), set("e2b", 10, 30.0), set("e2c", 10, 30.0))),
        planned("e3", squat, listOf(set("e3a", 12, 100.0, inherited = false)), rest = 300),
        planned("e4", catalogExercise("catalog_barbell_back_squat_high_bar"), listOf(set("e4a", 8, 120.0), set("e4b", 8, 120.0))),
        planned("e5", catalogExercise("catalog_barbell_bench_flat"), listOf(set("e5a", 5, 1_187.5), set("e5b", 5, 1_187.5))),
    )
    private val editorRoutine = RoutineUi("r", "Treino A: peito, ombros e tríceps do verão", editorEntries, folderId = RoutineFolder.DEFAULT_ID)
    private val folders = listOf(
        RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME),
        RoutineFolderUi("hyper", "Hipertrofia para o verão inteiro"),
        RoutineFolderUi("empty", "Força"),
    )

    /** Nine routines in the default folder (so the list scrolls), the first of them with a long name and a past session. */
    private val trainRoutines = List(9) { i ->
        RoutineUi(
            "t$i",
            if (i == 1) "Treino B: peito e tríceps" else "Treino ${'A' + i}",
            editorEntries.take(1 + i % 5).mapIndexed { j, e -> e.copy(id = "t$i-$j") },
            folderId = RoutineFolder.DEFAULT_ID,
            lastFinishedAt = if (i % 2 == 0) LAST_DONE else null,
        ) } + RoutineUi("hy", "Pernas", editorEntries.take(2), folderId = "hyper")

    private fun trainState(selected: String = RoutineFolder.DEFAULT_ID, loading: Boolean = false, active: WorkoutUi? = null) = UiState(
        loading = loading,
        exercises = catalog,
        routines = trainRoutines,
        routineFolders = folders,
        active = active,
        preferences = PreferencesUi(selectedRoutineFolderId = selected),
    )

    private fun editorState(routine: RoutineUi = editorRoutine) = UiState(
        loading = false,
        exercises = catalog,
        routines = listOf(routine),
        routineFolders = folders,
    )

    /** The picker's "All", in its order (pt-BR names). */
    private val model = LibraryModel.build(catalog, portuguese = true)

    // --- The combinations -----------------------------------------------------------------------

    /** One combination of the sweep: the theme, the font scale and the phone's width. */
    private data class Combo(val dark: Boolean, val fontScale: Float, val width: Dp) {
        override fun toString() = "${if (dark) "dark" else "light"} ${fontScale}x ${width.value.toInt()}dp"
    }

    private val combos: List<Combo> = listOf(false, true).flatMap { dark ->
        SWEEP_FONT_SCALES.flatMap { scale -> listOf(320.dp, 412.dp).map { Combo(dark, scale, it) } }
    }

    /** A phone of [width]: 640 dp tall at 320, 800 at 412. */
    private fun sizeOf(width: Dp) = DpSize(width, if (width < 400.dp) 640.dp else 800.dp)

    /**
     * One look at a state: [scroll] brings part of it into view (null: as it is), and the tags in [required] must
     * then be shown.
     */
    private class Pass(val name: String, val required: List<String> = emptyList(), val numbers: Int? = null, val scroll: (ComposeUiTest.() -> Unit)? = null)

    /**
     * Sets [content] once, in the frame of the current [Combo] (light, 1.0, 412 dp first; [content] gets the phone's
     * size), lets [reach] bring it to the state, then checks each combination (see the class comment): at least
     * [numbers] number texts, the text under [scaled] (a window's) laid out at the combination's scale, and [extra]
     * (the state's own checks, which fail with their message). The floor is 1 where a planned set's number label is
     * on screen (the editor); 0 only where the state truly has no number-only text: the Train cards, the folder rows
     * and the dialogs write their counts as words around the digits ("3 exercises"), the picker rows have none, and
     * the empty editor and the loading Train tab have no sets.
     */
    private fun ComposeUiTest.sweep(
        state: String,
        numbers: Int,
        passes: List<Pass>,
        scaled: String? = null,
        reach: ComposeUiTest.() -> Unit = {},
        extra: (ComposeUiTest.(where: String) -> Unit)? = null,
        content: @Composable (DpSize) -> Unit,
    ) {
        var combo by mutableStateOf(Combo(dark = false, fontScale = 1f, width = 412.dp))
        setContent {
            val size = sizeOf(combo.width)
            ScreenFrame(dark = combo.dark, fontScale = combo.fontScale, locale = "pt-BR", size = size) { content(size) }
        }
        waitForIdle()
        reach()
        waitForIdle()
        val failures = mutableListOf<String>()
        var buttonTexts = 0
        for (each in combos) {
            combo = each
            waitForIdle()
            for (pass in passes) {
                val where = "$state, $each, ${pass.name}"
                try {
                    pass.scroll?.invoke(this)
                    waitForIdle()
                    // What this pass is for must be there: a check of nothing passes in the void.
                    for (tag in pass.required) {
                        assertTrue(onAllNodes(hasTestTag(tag) and isPlaced, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "$where: no $tag shown")
                    }
                    assertClickablesLabeledAndSized(where)
                    assertNumbersWhole(atLeast = pass.numbers ?: numbers)
                    buttonTexts += assertButtonTextsWhole(where)
                    assertStartsFiftySix(where)
                    if (scaled != null) assertDrawnAt(scaled, each.fontScale, where)
                    extra?.invoke(this, where)
                } catch (failure: AssertionError) {
                    val message = failure.message.orEmpty()
                    failures += if (message.startsWith(where)) message else "$where: $message"
                }
            }
        }
        println("SWEEP $state: ${combos.size} combinations x ${passes.size} passes, $buttonTexts button texts measured, ${failures.size} failing")
        assertTrue(failures.isEmpty(), "${failures.size} checks of $state fail:\n" + failures.joinToString("\n"))
    }

    /** A text under [tag] (or [tag]'s own) is laid out at [fontScale]: the combination reaches its window. */
    private fun ComposeUiTest.assertDrawnAt(tag: String, fontScale: Float, where: String) {
        val text = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val node = onAllNodes((hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and text, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        assertTrue(node != null, "$where: no text under $tag")
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "$where: $tag has no text layout")
        val drawn = results.first().layoutInput.density.fontScale
        assertTrue(abs(drawn - fontScale) < 0.001f, "$where: the text under $tag is laid out at ${drawn}x")
    }

    /** "Start" (a card's) and "Start workout" (the editor's) are 56 dp targets (spec "Acessibilidade"). */
    private fun ComposeUiTest.assertStartsFiftySix(where: String) {
        val start = SemanticsMatcher("is a start button") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                tag == RoutineEditorTags.START || (tag.startsWith("routine-") && tag.endsWith("-start"))
            } == true
        }
        for (node in onAllNodes(start and isPlaced, useUnmergedTree = true).fetchSemanticsNodes()) {
            assertAtLeast(node, WORKOUT_TARGET_DP, WORKOUT_TARGET_DP, where)
        }
    }

    /**
     * Every composed drag handle of [ids] (in their order, of which only some are composed) carries the screen
     * reader's moves in place of the drag: "up" and "to top" unless it is the first, "down" and "to bottom" unless it
     * is the last, each named.
     */
    private fun ComposeUiTest.assertMovesOfEveryHandle(
        where: String,
        ids: List<String>,
        handleTag: (String) -> String,
        // The end of a scroll may show no handle at all (the last card's footer and "Add exercises").
        mustShow: Boolean = !where.endsWith(", end"),
    ) {
        var seen = 0
        for ((index, id) in ids.withIndex()) {
            val nodes = onAllNodes(hasTestTag(handleTag(id)) and isPlaced, useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) continue
            seen++
            val actions = nodes.first().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
            val expected = (if (index > 0) 2 else 0) + (if (index < ids.lastIndex) 2 else 0)
            assertEquals(expected, actions.size, "$where: the handle of $id (${index + 1} of ${ids.size}) has ${actions.map { it.label }}")
            assertTrue(actions.all { it.label.isNotBlank() }, "$where: a move of $id has no name: ${actions.map { it.label }}")
        }
        assertTrue(seen > 0 || !mustShow, "$where: no drag handle shown")
    }

    private fun ComposeUiTest.exists(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.await(what: String, condition: () -> Boolean) = waitUntil(what, timeoutMillis = WAIT_MS, condition = condition)

    // --- The Train tab --------------------------------------------------------------------------

    @Composable
    private fun Train(state: UiState) {
        TrainRoute(state, RecordingActions(), onOpenRoutine = {}, onOpenWorkout = {}, onOpenSettings = {}, now = { NOW })
    }

    private val defaultFolderIds = trainRoutines.filter { it.folderId == RoutineFolder.DEFAULT_ID }.map { it.id }

    private fun trainScroll(name: String, tag: String, vararg required: String) = Pass(name, listOf(tag) + required) {
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(tag))
    }

    private fun trainPasses() = listOf(
        Pass("top", listOf(TrainTags.FOLDER_ROW, TrainTags.card("t0"), TrainTags.start("t0"))) { onNodeWithTag(TrainScreenTag).performScrollToIndex(0) },
        trainScroll("middle", TrainTags.card("t4"), TrainTags.start("t4"), TrainTags.menu("t4")),
        trainScroll("end", TrainTags.NEW_ROUTINE, TrainTags.EMPTY_WORKOUT),
    )

    /** Nine cards, one of them to resume ("Retomar treino"), a long name; the top, the middle and the end. */
    @Test
    fun trainWithRoutines() = runComposeUiTest(testTimeout = 8.minutes) {
        val resumable = WorkoutUi("w", "Treino em andamento", startedAt = LAST_DONE, sourceRoutineId = "t2")
        sweep(
            "train", numbers = 0, passes = trainPasses(),
            extra = { where -> assertMovesOfEveryHandle(where, defaultFolderIds, TrainTags::handle) },
        ) { Train(trainState(active = resumable)) }
    }

    @Test
    fun trainEmptyFolder() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "train empty", numbers = 0,
            passes = listOf(
                Pass("top", listOf(TrainTags.FOLDER_ROW, TrainTags.EMPTY)) { onNodeWithTag(TrainScreenTag).performScrollToIndex(0) },
                trainScroll("end", TrainTags.EMPTY_WORKOUT),
            ),
        ) { Train(trainState(selected = "empty")) }
    }

    @Test
    fun trainLoading() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "train loading", numbers = 0,
            passes = listOf(Pass("as shown", listOf(TrainTags.FOLDER_ROW, TrainTags.SETTINGS, TrainTags.SKELETON))),
        ) { Train(trainState(loading = true)) }
    }

    // --- The folders ------------------------------------------------------------------------------

    private val manyFolders = folders + List(10) { RoutineFolderUi("extra$it", "Pasta extra $it") }

    @Composable
    private fun Folders() {
        FolderManagerSheet(trainState().copy(routineFolders = manyFolders), RecordingActions(), onDismiss = {})
    }

    /** The folder manager: the first folder, a long name, the last of twelve and "New folder" at the end. */
    @Test
    fun folderManager() = runComposeUiTest(testTimeout = 8.minutes) {
        sweep(
            "folders", numbers = 0, scaled = FolderTags.LIST,
            reach = { await("the sheet opens") { exists(FolderTags.LIST) } },
            passes = listOf(
                Pass("top", listOf(FolderTags.row(RoutineFolder.DEFAULT_ID), FolderTags.handle(RoutineFolder.DEFAULT_ID))) {
                    onNodeWithTag(FolderTags.LIST).performScrollToNode(hasTestTag(FolderTags.row(RoutineFolder.DEFAULT_ID)))
                },
                Pass("middle", listOf(FolderTags.row("hyper"), FolderTags.menu("hyper"))) {
                    onNodeWithTag(FolderTags.LIST).performScrollToNode(hasTestTag(FolderTags.row("hyper")))
                },
                Pass("end", listOf(FolderTags.row("extra9"), FolderTags.NEW)) {
                    onNodeWithTag(FolderTags.LIST).performScrollToNode(hasTestTag(FolderTags.NEW))
                },
            ),
            extra = { where -> assertMovesOfEveryHandle(where, manyFolders.map { it.id }, FolderTags::handle) },
        ) { Folders() }
    }

    /** "Delete Hipertrofia…?", with where its routines go. */
    @Test
    fun folderDeleteDialog() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "delete folder", numbers = 0, scaled = DIALOG,
            reach = {
                await("the sheet opens") { exists(FolderTags.menu("hyper")) }
                onNodeWithTag(FolderTags.menu("hyper")).performClick()
                await("the menu opens") { exists(FolderTags.MENU_DELETE) }
                onNodeWithTag(FolderTags.MENU_DELETE).performClick()
                await("the dialog opens") { exists(DIALOG) }
            },
            passes = listOf(Pass("as shown", listOf(DIALOG))),
        ) { Folders() }
    }

    // --- The editor -------------------------------------------------------------------------------

    @Composable
    private fun Editor(state: UiState, routineId: String = "r") {
        RoutineEditorRoute(
            routineId = routineId,
            state = state,
            actions = RecordingActions(),
            onBack = {},
            onGone = {},
            onOpenWorkout = {},
            onExerciseInfo = {},
        )
    }

    private fun editorScroll(name: String, tag: String, vararg required: String) = Pass(name, listOf(tag) + required) {
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(tag))
    }

    /** The top (name and the first cards), the middle (a card of its own) and the end ("Add exercises"; "Start workout" is always there). */
    private fun editorPasses() = listOf(
        // The top shows the name and the first card, whose first set is the warm-up ("A"): at 320 dp and at 2x no set number fits yet.
        Pass("top", listOf(RoutineEditorTags.NAME, RoutineEditorTags.header("e1"), RoutineEditorTags.START), numbers = 0) { onNodeWithTag(RoutineEditorTag).performScrollToIndex(0) },
        editorScroll("middle", RoutineEditorTags.set("e3a"), RoutineEditorTags.header("e3"), RoutineEditorTags.START),
        editorScroll("end", RoutineEditorTags.ADD, RoutineEditorTags.START),
    )

    private val entryIds = editorEntries.map { it.id }

    /** A warm-up, manual sets and sets that follow the record: five cards, a routine name that wraps. */
    @Test
    fun editorWithWarmup() = runComposeUiTest(testTimeout = 8.minutes) {
        sweep(
            "editor", numbers = 1, passes = editorPasses(),
            extra = { where -> assertMovesOfEveryHandle(where, entryIds, RoutineEditorTags::handle) },
        ) { Editor(editorState()) }
    }

    /** The first working set's load in edit (the stepper under the row), then the cards around it. */
    @Test
    fun editorWithASetInEdit() = runComposeUiTest(testTimeout = 8.minutes) {
        sweep(
            "editor editing", numbers = 1,
            reach = {
                onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.set("e1a")))
                waitForIdle()
                onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(RoutineEditorTags.setLoad("e1a"))), useUnmergedTree = true).performClick()
                await("the stepper shows") { exists(StepperIncreaseTag) }
            },
            passes = listOf(
                Pass("editing", listOf(StepperIncreaseTag, RoutineEditorTags.START)) {
                    onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.set("e1a")))
                },
                editorScroll("end", RoutineEditorTags.ADD, RoutineEditorTags.START),
            ),
            // At 2x the set in edit fills the view and its card's handle is scrolled out: a handle shown is checked, none is not a failure.
            extra = { where -> assertMovesOfEveryHandle(where, entryIds, RoutineEditorTags::handle, mustShow = false) },
        ) { Editor(editorState()) }
    }

    @Test
    fun editorEmpty() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "editor empty", numbers = 0,
            passes = listOf(
                Pass("as shown", listOf(RoutineEditorTags.EMPTY, RoutineEditorTags.START)) { onNodeWithTag(RoutineEditorTag).performScrollToIndex(0) },
                editorScroll("end", RoutineEditorTags.ADD, RoutineEditorTags.START),
            ),
        ) { Editor(editorState(editorRoutine.copy(exercises = emptyList()))) }
    }

    // --- The picker -------------------------------------------------------------------------------

    /** "Add exercises": the multiple picker with two rows marked; the top, and the end of the catalog. */
    @Test
    fun multiplePicker() = runComposeUiTest(testTimeout = 8.minutes) {
        val all = model.results("", LibraryFilters())
        val first = all.first()
        val last = all.last()
        sweep(
            "multiple picker", numbers = 0, scaled = ExercisePickerAddTag,
            reach = {
                onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
                waitForIdle()
                onNodeWithTag(RoutineEditorTags.ADD).performClick()
                for (exercise in listOf(first, last)) {
                    val tag = exercisePickerRowTag(exercise.id, recent = false)
                    await("the picker lists ${exercise.id}") {
                        if (!exists(tag) && exists(ExercisePickerListTag)) runCatching { onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag)) }
                        exists(tag)
                    }
                    onNodeWithTag(tag).performClick()
                    waitForIdle()
                }
            },
            passes = listOf(
                Pass("top", listOf(ExercisePickerAddTag, exercisePickerRowTag(first.id, recent = false))) {
                    onNodeWithTag(ExercisePickerListTag).performScrollToIndex(0)
                },
                // The list's items: the filters, the "All" header, then the rows.
                Pass("end", listOf(ExercisePickerAddTag, exercisePickerRowTag(last.id, recent = false))) {
                    onNodeWithTag(ExercisePickerListTag).performScrollToIndex(1 + all.size)
                },
            ),
        ) { Editor(editorState()) }
    }

    // --- The screen reader ----------------------------------------------------------------------

    /** The tag of [node], or of the nearest ancestor that has one [regions] maps. */
    private fun regionOf(node: SemanticsNode, regions: (String) -> String?, byLabel: (String) -> String? = { null }): String? {
        var current: SemanticsNode? = node
        while (current != null) {
            current.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> regions(tag)?.let { return it } }
            current = current.parent
        }
        return byLabel(spokenLabel(node))
    }

    /** The regions [order] goes through, each once in a row; fails on a node of no region. */
    private fun sequenceOf(order: List<SemanticsNode>, region: (SemanticsNode) -> String?): List<String> {
        val read = order.joinToString("\n") { "${region(it)} '${spokenLabel(it)}' ${it.boundsInRoot}" }
        val regions = order.map { node ->
            val found = region(node)
            assertTrue(found != null, "a node of no region: '${spokenLabel(node)}' ${node.config}\n$read")
            found
        }
        return regions.fold(mutableListOf()) { acc, it -> acc.apply { if (lastOrNull() != it) add(it) } }
    }

    /**
     * The Train tab's order ([screenReaderOrder]) on a window tall enough that the list does not scroll: the top
     * ("Treinar", Settings), the folders ("Pastas" last), then each card (its name and summary in one node, "Start",
     * its menu: the handle is a stop of its own, read first; the menu comes before "Start", which is on the row
     * below), then "New routine" and "Empty workout".
     */
    @Test
    fun trainReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        val three = trainState().copy(routines = trainRoutines.take(3))
        setContent { ScreenFrame(locale = "pt-BR") { TopAnchored(TALL) { Train(three) } } }
        waitForIdle()
        await("the cards show") { exists(TrainTags.card("t2")) }
        waitForIdle()
        val ids = listOf("t0", "t1", "t2")
        val regions: (String) -> String? = { tag ->
            when {
                tag == TrainTags.SETTINGS -> "top"
                tag == TrainTags.FOLDER_ROW || tag == TrainTags.MANAGE_FOLDERS || tag.startsWith("train-folder-") -> "folders"
                tag == TrainTags.NEW_ROUTINE -> "new"
                tag == TrainTags.EMPTY_WORKOUT -> "empty"
                else -> ids.firstNotNullOfOrNull { id ->
                    when (tag) {
                        TrainTags.info(id) -> "$id name"
                        TrainTags.start(id) -> "$id start"
                        TrainTags.menu(id) -> "$id menu"
                        TrainTags.handle(id) -> "$id handle"
                        else -> null
                    }
                }
            }
        }
        val order = screenReaderOrder().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        val sequence = sequenceOf(order) { regionOf(it, regions) { label -> if (label == "Treinar") "top" else null } }
        val seen = sequence.filter { it != "top" && it != "folders" }
        // Per card: its name and summary, then "Start" and the menu (the handle, where it is, is its own stop); the cards in order.
        for (id in ids) {
            val name = sequence.indexOf("$id name")
            val start = sequence.indexOf("$id start")
            val menu = sequence.indexOf("$id menu")
            val handle = sequence.indexOf("$id handle")
            assertTrue(name >= 0 && start > name && menu > name, "the card $id is not read as name, then start and menu: $sequence")
            // The card is read as a block: its handle just before its name, its start and menu just after, in the layout's order.
            assertEquals(listOf("$id handle", "$id name", "$id menu", "$id start"), sequence.subList(handle, handle + 4), "the card $id: $sequence")
        }
        assertEquals(listOf("top", "folders"), sequence.take(2), "the top and the folders come first: $sequence")
        assertTrue(sequence.indexOf("t0 name") < sequence.indexOf("t1 name") && sequence.indexOf("t1 name") < sequence.indexOf("t2 name"), "$sequence")
        assertEquals(listOf("new", "empty"), seen.takeLast(2), "the actions come last: $sequence")
        // The card's name and summary are one node, with both.
        val info = onNodeWithTag(TrainTags.info("t0")).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue(info.contains("Treino A") && info.contains("exercício"), "name and summary in one node: $info")
    }

    /**
     * The editor's whole order ([screenReaderOrder]), in a window tall enough that its list does not scroll: the bar
     * (back, the options), the name, each exercise (its header, then its sets, then "Add set"), "Add exercises" and
     * "Start workout".
     */
    @Test
    fun editorReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        val three = editorRoutine.copy(exercises = editorEntries.take(3))
        setContent { ScreenFrame(locale = "pt-BR") { TopAnchored(TALL) { Editor(editorState(three)) } } }
        waitForIdle()
        await("the cards show") { exists(RoutineEditorTags.footer("e3")) }
        waitForIdle()
        val setOwner = three.exercises.flatMap { e -> e.setTargets.map { it.id to e.id } }.toMap()
        val regions: (String) -> String? = { tag ->
            when {
                tag == RoutineEditorTags.MENU -> "bar"
                tag == RoutineEditorTags.NAME -> "name"
                tag == RoutineEditorTags.ADD -> "add"
                tag == RoutineEditorTags.START -> "start"
                tag.startsWith("routine-exercise-") -> three.exercises.firstOrNull { tag.startsWith("routine-exercise-${it.id}-") }?.id
                tag.startsWith("routine-set-") -> setOwner.entries.firstOrNull { tag.startsWith("routine-set-${it.key}") }?.value
                else -> null
            }
        }
        val order = screenReaderOrder()
        val sequence = sequenceOf(order) { regionOf(it, regions) { label -> if (label == "Voltar") "bar" else null } }
        assertEquals(listOf("bar", "name", "e1", "e2", "e3", "add", "start"), sequence, "the screen reader's order")
        // Inside a card: the header's handle and name come before its sets, the sets before "Add set".
        for (entry in three.exercises) {
            val ofEntry = order.filter { regionOf(it, regions) == entry.id }
            val tags = ofEntry.map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "" }
            val firstSet = tags.indexOfFirst { it.startsWith("routine-set-") }
            val add = tags.indexOf(RoutineEditorTags.addSet(entry.id))
            assertTrue(firstSet < 0 || add < 0 || firstSet < add, "${entry.id}: the sets come before \"Add set\": $tags")
        }
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val DIALOG = "dialog"

        /** Noon UTC, the same day in every time zone a test device runs in. */
        const val NOW = 1_792_584_000_000L
        const val LAST_DONE = 1_791_979_200_000L
    }
}
