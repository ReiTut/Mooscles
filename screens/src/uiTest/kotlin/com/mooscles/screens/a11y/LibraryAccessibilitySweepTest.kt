package com.mooscles.screens.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineFolder
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.illustration.prepareBodyMap
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenFrame
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.exercise.AddToRoutineSheetTags
import com.mooscles.screens.exercise.CustomExerciseForm
import com.mooscles.screens.exercise.CustomExerciseFormTags
import com.mooscles.screens.exercise.ExerciseCues
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.isPlaced
import com.mooscles.screens.library.LibraryFilters
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.library.TALL
import com.mooscles.screens.library.TopAnchored
import com.mooscles.screens.screenReaderOrder
import com.mooscles.screens.spokenLabel
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The accessibility and large text sweep of the phase 4A screens (spec "Acessibilidade", plan Task
 * 21): the library (no search, filtered by a muscle, nothing found, the equipment sheet), the
 * exercise detail (with the figure, without one, without cues, without history, not found, its
 * menu, "Add to routine") and the custom exercise form (new, refused, editing with both warnings),
 * each in light and dark × at 1.0, 1.3 and 2.0 ([SWEEP_FONT_SCALES]: 2.01 too on Android) × on 320
 * and 412 dp wide phones, in Portuguese (the longer texts), and at the top, the middle and the end
 * of its scroll ([Pass]). One test per state: it reaches the state once, then changes the theme,
 * the scale and the width within the same composition ([ScreenFrame] over [Combo] state) and checks
 * each combination: every clickable has a label and a 48 dp target ([assertClickablesLabeledAndSized]),
 * every number is whole ([assertNumbersWhole]; at least as many as the state shows), no button
 * text is cut ([assertButtonTextsWhole]), and, in a window of its own (a sheet, the form), the text
 * is laid out at the combination's scale ([assertDrawnAt]). All the failures of a state are
 * reported together.
 *
 * The form and the sheets are windows of their own, which take the frame's density and font scale
 * (the form, `MBottomSheet` and `MDialog` pass the host's in). The form also takes the frame's
 * width ([CustomExerciseForm]'s `pageSize`); a sheet is as wide as the screen (412 dp on the
 * emulator's Pixel 8, the simulator's on iOS), so its "320 dp" combinations check it at the
 * screen's width.
 *
 * Plus the screen reader's order over the library ([libraryReadingOrder]: the top, the search, the
 * filters, the count, the list) and over the whole detail, laid out in a window tall enough that
 * nothing scrolls ([detailReadingOrder]: the bar, the name, the media, the chips, the muscles with
 * their text, the cues, the history, the actions).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryAccessibilitySweepTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    // --- The data -------------------------------------------------------------------------------

    private val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    private val bench = catalog.single { it.id == "catalog_barbell_bench_flat" }

    /** A catalog exercise without a figure (not in the pilot), with cues. */
    private val frontSquat = catalog.single { it.id == "catalog_barbell_front_squat" }

    /** A custom exercise: no figure, no cues, a convention with a caption. */
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Dumbbell", LoadConvention.PER_HAND.label, custom = true)

    /** A custom exercise named as a catalog one (the duplicate warning), with logged sets. */
    private val twin = ExerciseUi("custom-twin", "Supino reto com barra", "Chest", "Barbell", LoadConvention.TOTAL_LOAD.label, custom = true)

    private val exercises = catalog + mine + twin

    private fun workout(id: String, startedAt: Long, vararg entries: Pair<ExerciseUi, List<SetUi>>) =
        WorkoutUi(id, "Treino $id", startedAt, endedAt = startedAt + HOUR_MS,
            exercises = entries.mapIndexed { i, (exercise, sets) -> WorkoutExerciseUi("$id-$i", exercise, sets) })

    private fun sets(prefix: String, kg: Double, reps: Int) = List(3) { SetUi("$prefix-$it", reps, kg, completed = true) }

    /** Two sessions of each exercise the detail shows with a history; newest first. */
    private val history = listOf(
        workout("w2", OCT_21, bench to sets("w2b", 82.5, 8), frontSquat to sets("w2f", 70.0, 6), mine to sets("w2m", 22.5, 12), twin to sets("w2t", 60.0, 10)),
        workout("w1", OCT_14, bench to sets("w1b", 100.0, 3), frontSquat to sets("w1f", 65.0, 8), mine to sets("w1m", 20.0, 12)),
    )
    private val records = listOf(RecordUi(bench, weightKg = 100.0, reps = 3, workoutId = "w1", date = OCT_14))
    private val active = WorkoutUi("w9", "Treino de hoje", startedAt = OCT_21 + 7 * DAY_MS)
    private val folders = listOf(RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME), RoutineFolderUi("f2", "Hipertrofia para o verão inteiro"))
    private val routines = listOf(
        RoutineUi("r1", "Treino A", folderId = RoutineFolder.DEFAULT_ID),
        RoutineUi("r2", "Treino B: peito, ombros e tríceps", folderId = RoutineFolder.DEFAULT_ID),
        RoutineUi("r3", "Pernas", folderId = "f2"),
    )

    private fun stateOf(withHistory: Boolean = true) = UiState(
        loading = false,
        exercises = exercises,
        history = if (withHistory) history else emptyList(),
        records = if (withHistory) records else emptyList(),
        active = active,
        routines = routines,
        routineFolders = folders,
    )

    /** The library's "All", in its order (pt-BR names), for the scroll's middle and end. */
    private val model = LibraryModel.build(exercises, portuguese = true)

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
     * One look at a state: [scroll] brings part of it into view (null: as it is), and the tags in
     * [required] must then be shown.
     */
    private class Pass(val name: String, val required: List<String> = emptyList(), val scroll: (ComposeUiTest.() -> Unit)? = null)

    /**
     * Sets [content] once, in the frame of the current [Combo] (light, 1.0, 412 dp first; [content]
     * gets the phone's size), lets [reach] bring it to the state, then checks each combination (see
     * the class comment): at least [numbers] number texts, and the text under [scaled] (a window's)
     * laid out at the combination's scale.
     */
    private fun ComposeUiTest.sweep(
        state: String,
        numbers: Int,
        passes: List<Pass>,
        scaled: String? = null,
        reach: ComposeUiTest.() -> Unit = {},
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
                    assertNumbersWhole(atLeast = numbers)
                    buttonTexts += assertButtonTextsWhole(where)
                    if (scaled != null) assertDrawnAt(scaled, each.fontScale, where)
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

    private fun ComposeUiTest.exists(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.await(what: String, condition: () -> Boolean) = waitUntil(what, timeoutMillis = WAIT_MS, condition = condition)

    // --- The library ----------------------------------------------------------------------------

    @Composable
    private fun Library(state: UiState) {
        LibraryRoute(state, RecordingActions(), onOpenExercise = {}, onOpenSettings = {})
    }

    private fun ComposeUiTest.awaitList() = await("the library's list is built") { exists(LibraryTags.COUNT) }

    /**
     * Scrolls the list to the row of [exercise], the [index]th item of the list's (the filters and
     * the count come first, then "Recently used" and the "All" header without a search): by its
     * index, since searching for it from the top for each combination takes long; the row must then
     * be there.
     */
    private fun listTo(name: String, exercise: ExerciseUi, index: Int) = Pass(name, listOf(LibraryTags.row(exercise.id))) {
        onNodeWithTag(LibraryTags.LIST).performScrollToIndex(index)
    }

    /** The index in the list of the [i]th row of "All", after the filters, the count and "Recently used". */
    private fun allIndex(i: Int): Int = 2 + model.recents(history).size.let { if (it > 0) it + 1 else 0 } + 1 + i

    /** Recently used and the start of "All"; the middle of "All"; its longest name; its end. */
    @Test
    fun libraryWithoutSearch() = runComposeUiTest(testTimeout = 8.minutes) {
        val all = model.exercises
        val longest = all.maxBy { model.shownName(it).length }
        sweep(
            "library", numbers = 0, reach = { awaitList() },
            passes = listOf(
                Pass("top", listOf(LibraryTags.COUNT, LibraryTags.RECENT_HEADER)) { onNodeWithTag(LibraryTags.LIST).performScrollToIndex(0) },
                listTo("middle", all[all.size / 2], allIndex(all.size / 2)),
                listTo("longest name", longest, allIndex(all.indexOf(longest))),
                listTo("end", all.last(), allIndex(all.lastIndex)),
            ),
        ) { Library(stateOf()) }
    }

    /** "Back" on: "Clear filters" shows; the results' top and end. */
    @Test
    fun libraryFiltered() = runComposeUiTest(testTimeout = 5.minutes) {
        val back = model.results("", LibraryFilters(muscles = setOf("Back")))
        sweep(
            "library filtered", numbers = 0,
            reach = {
                awaitList()
                onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
                waitForIdle()
            },
            passes = listOf(
                Pass("top", listOf(LibraryTags.COUNT, LibraryTags.CLEAR_FILTERS)) { onNodeWithTag(LibraryTags.LIST).performScrollToIndex(0) },
                // The filters and the count, then the results.
                listTo("end", back.last(), 2 + back.lastIndex),
            ),
        ) { Library(stateOf()) }
    }

    /** "Supino articulado" with "Back" on: nothing found, with "Create “Supino articulado”" and "Clear filters". */
    @Test
    fun libraryNothingFound() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "library empty", numbers = 0,
            reach = {
                awaitList()
                onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
                onNode(hasSetTextAction()).performTextReplacement("Supino articulado")
                waitForIdle()
                await("nothing is found") { exists(LibraryTags.EMPTY) }
            },
            passes = listOf(
                Pass("top", listOf(LibraryTags.EMPTY)) { onNodeWithTag(LibraryTags.LIST).performScrollToIndex(0) },
                Pass("actions", listOf(LibraryTags.EMPTY)) {
                    onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).fetchSemanticsNodes().also {
                        assertEquals(2, it.size, "the empty state's actions")
                    }
                    onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY)))[1].performScrollTo()
                },
            ),
        ) { Library(stateOf()) }
    }

    @Test
    fun libraryEquipmentSheet() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "equipment sheet", numbers = 0, scaled = LibraryTags.EQUIPMENT_SHEET,
            reach = {
                awaitList()
                onNodeWithTag(LibraryTags.EQUIPMENT_CHIP).performClick()
                await("the sheet opens") { exists(LibraryTags.EQUIPMENT_SHEET) }
            },
            passes = listOf(
                Pass("top", listOf(LibraryTags.EQUIPMENT_SHEET, LibraryTags.equipmentOption("Barbell"))),
                Pass("end", listOf(LibraryTags.equipmentOption("Other"))) { onNodeWithTag(LibraryTags.equipmentOption("Other")).performScrollTo() },
            ),
        ) { Library(stateOf()) }
    }

    // --- The detail -----------------------------------------------------------------------------

    @Composable
    private fun Detail(exerciseId: String, state: UiState) {
        ExerciseInfoRoute(
            exerciseId = exerciseId,
            state = state,
            actions = RecordingActions(),
            onBack = {},
            onOpenHistory = {},
            onOpenRoutine = {},
            onOpenWorkout = {},
        )
    }

    /** The cues and the body map in memory, as after the library opened: both draw in their first frame. */
    private fun preload() = runBlocking {
        ExerciseCues.load(portuguese = true)
        prepareBodyMap()
    }

    private fun scrollTo(name: String, tag: String, vararg required: String) = Pass(name, listOf(tag) + required) {
        onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    }

    /** The detail's top (the name and the media), its middle (the muscle text) and its end (the actions). */
    private fun detailPasses() = listOf(
        scrollTo("top", ExerciseInfoTags.TITLE, ExerciseInfoTags.MEDIA),
        scrollTo("middle", ExerciseInfoTags.MUSCLE_TEXT),
        scrollTo("end", ExerciseInfoTags.ADD_WORKOUT, ExerciseInfoTags.ADD_ROUTINE),
    )

    private fun ComposeUiTest.awaitCues() = await("the cues show") { exists(ExerciseInfoTags.CUES) }

    /** The bench: the figure, cues, a history with its record, "Add to workout". */
    @Test
    fun detailWithFigure() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        // The cues' numbers (four) and the best set ("Melhor"; the last time holds its date, not a number only).
        sweep("detail figure", numbers = 5, reach = { awaitCues() }, passes = detailPasses()) { Detail(bench.id, stateOf()) }
    }

    @Test
    fun detailWithoutFigure() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        sweep("detail no figure", numbers = 3, reach = { awaitCues() }, passes = detailPasses()) { Detail(frontSquat.id, stateOf()) }
    }

    /** A custom exercise: no cues, the convention's caption, a history (per hand): its best set is the one number. */
    @Test
    fun detailWithoutCues() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        sweep("detail no cues", numbers = 1, passes = detailPasses()) { Detail(mine.id, stateOf()) }
    }

    @Test
    fun detailWithoutHistory() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        val passes = detailPasses().toMutableList()
        passes[2] = scrollTo("end", ExerciseInfoTags.ADD_WORKOUT, ExerciseInfoTags.HISTORY_NONE, ExerciseInfoTags.ADD_ROUTINE)
        sweep("detail no history", numbers = 3, reach = { awaitCues() }, passes = passes) { Detail(bench.id, stateOf(withHistory = false)) }
    }

    @Test
    fun detailNotFound() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("detail not found", numbers = 0, passes = listOf(Pass("as shown", listOf(ExerciseInfoTags.NOT_FOUND)))) { Detail("gone", stateOf()) }
    }

    /** The menu of a custom exercise: its name, "Edit exercise" and "View full history". */
    @Test
    fun detailMenu() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "detail menu", numbers = 0, scaled = ExerciseInfoTags.MENU_EDIT,
            reach = {
                onNodeWithTag(ExerciseInfoTags.MENU).performClick()
                await("the menu opens") { exists(ExerciseInfoTags.MENU_EDIT) }
            },
            passes = listOf(Pass("as shown", listOf(ExerciseInfoTags.MENU_EDIT, ExerciseInfoTags.MENU_HISTORY))),
        ) { Detail(mine.id, stateOf()) }
    }

    /** "Add to routine": the two folders and their three routines. */
    @Test
    fun detailAddToRoutine() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        sweep(
            "add to routine", numbers = 0, scaled = AddToRoutineSheetTags.ROOT,
            reach = {
                onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE).performScrollTo().performClick()
                await("the sheet opens") { exists(AddToRoutineSheetTags.ROOT) }
            },
            passes = listOf(
                Pass("top", listOf(AddToRoutineSheetTags.routine("r1"))),
                Pass("end", listOf(AddToRoutineSheetTags.routine("r3"))) { onNodeWithTag(AddToRoutineSheetTags.routine("r3")).performScrollTo() },
            ),
        ) { Detail(bench.id, stateOf()) }
    }

    // --- The form -------------------------------------------------------------------------------

    @Composable
    private fun Form(initial: ExerciseUi?, size: DpSize) {
        CustomExerciseForm(
            initial = initial,
            prefillName = "",
            state = stateOf(),
            actions = RecordingActions(),
            onSaved = {},
            onCancel = {},
            pageSize = size,
        )
    }

    private val lastConvention = CustomExerciseFormTags.convention(LoadConvention.entries.last().label)

    /** The title and the name; the muscle and equipment chips; the conventions' end. */
    private fun formPasses() = listOf(
        scrollTo("top", CustomExerciseFormTags.TITLE, CustomExerciseFormTags.NAME, CustomExerciseFormTags.SAVE),
        scrollTo("middle", CustomExerciseFormTags.equipment("Other"), CustomExerciseFormTags.muscle("Full body")),
        scrollTo("end", lastConvention),
    )

    @Test
    fun formNew() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "form new", numbers = 0, scaled = CustomExerciseFormTags.TITLE, passes = formPasses(),
            reach = { await("the form opens") { exists(CustomExerciseFormTags.ROOT) } },
        ) { Form(null, it) }
    }

    /** "Save" with nothing filled in: the name's error and both sections' errors. */
    @Test
    fun formRefused() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "form refused", numbers = 0, scaled = CustomExerciseFormTags.TITLE,
            reach = {
                await("the form opens") { exists(CustomExerciseFormTags.ROOT) }
                onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
                waitForIdle()
            },
            passes = listOf(
                scrollTo("top", CustomExerciseFormTags.TITLE, CustomExerciseFormTags.NAME),
                scrollTo("muscle error", CustomExerciseFormTags.MUSCLE_ERROR),
                scrollTo("equipment error", CustomExerciseFormTags.EQUIPMENT_ERROR),
                scrollTo("end", lastConvention),
            ),
        ) { Form(null, it) }
    }

    /**
     * Editing a custom exercise named as a catalog one ("Já existe …", under the name) with logged
     * sets, its convention changed ("As séries já registradas …", at the end).
     */
    @Test
    fun formEditWithWarnings() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "form edit", numbers = 0, scaled = CustomExerciseFormTags.TITLE,
            reach = {
                await("the form opens") { exists(CustomExerciseFormTags.ROOT) }
                onNodeWithTag(CustomExerciseFormTags.convention(LoadConvention.PER_HAND.label)).performScrollTo().performClick()
                await("the convention warning shows") { exists(CustomExerciseFormTags.CONVENTION_CHANGE) }
            },
            passes = listOf(
                scrollTo("top", CustomExerciseFormTags.TITLE, CustomExerciseFormTags.NAME),
                scrollTo("middle", CustomExerciseFormTags.equipment("Other")),
                scrollTo("end", CustomExerciseFormTags.CONVENTION_CHANGE),
            ),
        ) { Form(twin, it) }
    }

    // --- The screen reader ----------------------------------------------------------------------

    /**
     * The region of [node], by the first of its tags (or its ancestors') that [regions] maps, else by
     * [byLabel] over what it says.
     */
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
        val regions = order.map { node -> assertTrueNotNull(region(node), "a node of no region: '${spokenLabel(node)}' ${node.config}\n$read") }
        return regions.fold(mutableListOf()) { acc, it -> acc.apply { if (lastOrNull() != it) add(it) } }
    }

    private fun <T : Any> assertTrueNotNull(value: T?, message: String): T {
        assertTrue(value != null, message)
        return value
    }

    /**
     * The library's order ([screenReaderOrder]) on a 412 dp phone: the top bar ("Biblioteca",
     * Settings, Create), the search, the filters (the muscle chips, "Equipamento", "Meus
     * exercícios"), the count, then the list. The muscle chips scrolled out of the row (a node with
     * no bounds on the screen) are left out: a reader reaches them by scrolling the row.
     */
    @Test
    fun libraryReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        setContent { ScreenFrame(locale = "pt-BR", size = sizeOf(412.dp)) { Library(stateOf()) } }
        waitForIdle()
        awaitList()
        waitForIdle()
        val regions: (String) -> String? = { tag ->
            when {
                tag == LibraryTags.SETTINGS || tag == LibraryTags.CREATE -> "top"
                tag == LibraryTags.SEARCH -> "search"
                tag.startsWith("library-chip-") || tag == LibraryTags.EQUIPMENT_CHIP || tag == LibraryTags.MINE_CHIP -> "filters"
                tag == LibraryTags.COUNT -> "count"
                tag == LibraryTags.LIST -> "list"
                else -> null
            }
        }
        val order = screenReaderOrder().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        val sequence = sequenceOf(order) { regionOf(it, regions) { label -> if (label == "Biblioteca") "top" else null } }
        assertEquals(listOf("top", "search", "filters", "count", "list"), sequence, "the screen reader's order")
    }

    /**
     * The detail's whole order ([screenReaderOrder]), in a window tall enough that its content does
     * not scroll (a scrolled column clips what it hides, and a clipped node has no place in the
     * order): the bar (back, the menu), the name, the media, the chips, the muscles (the title, the
     * legend, the text; the map says nothing), the cues, the history and the actions.
     */
    @Test
    fun detailReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        preload()
        setContent { ScreenFrame(locale = "pt-BR") { TopAnchored(TALL) { Detail(bench.id, stateOf()) } } }
        waitForIdle()
        awaitCues()
        waitForIdle()
        // Nothing scrolls: the last action ends inside the column.
        val body = onNodeWithTag(ExerciseInfoTags.BODY).fetchSemanticsNode().boundsInRoot
        val last = onNodeWithTag(ExerciseInfoTags.ADD_WORKOUT).fetchSemanticsNode().boundsInRoot
        assertTrue(last.bottom <= body.bottom && last.height > 0f, "the detail does not fit: body $body, last action $last")
        val regions: (String) -> String? = { tag ->
            when (tag) {
                ExerciseInfoTags.MENU -> "bar"
                ExerciseInfoTags.TITLE -> "name"
                ExerciseInfoTags.MEDIA -> "media"
                ExerciseInfoTags.META -> "meta"
                ExerciseInfoTags.MUSCLES -> "muscles"
                ExerciseInfoTags.CUES -> "cues"
                ExerciseInfoTags.HISTORY -> "history"
                ExerciseInfoTags.ADD_ROUTINE, ExerciseInfoTags.ADD_WORKOUT -> "actions"
                else -> null
            }
        }
        val order = screenReaderOrder()
        val sequence = sequenceOf(order) { regionOf(it, regions) { label -> if (label == "Voltar") "bar" else null } }
        assertEquals(listOf("bar", "name", "media", "meta", "muscles", "cues", "history", "actions"), sequence, "the screen reader's order")
        // The muscles' text is read, last in its section; "Add to routine" before "Add to workout".
        val muscles = order.filter { regionOf(it, regions) == "muscles" }
        assertEquals(
            onNodeWithTag(ExerciseInfoTags.MUSCLE_TEXT).fetchSemanticsNode().id, muscles.last().id,
            "the muscle text is not the section's last: ${muscles.map { spokenLabel(it) }}",
        )
        val actions = order.filter { regionOf(it, regions) == "actions" }.map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(listOf(ExerciseInfoTags.ADD_ROUTINE, ExerciseInfoTags.ADD_WORKOUT), actions)
    }

    private companion object {
        /** Noon UTC, the same day in every time zone a test device runs in. */
        const val OCT_21 = 1_792_584_000_000L
        const val OCT_14 = 1_791_979_200_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
        const val WAIT_MS = 10_000L
    }
}
