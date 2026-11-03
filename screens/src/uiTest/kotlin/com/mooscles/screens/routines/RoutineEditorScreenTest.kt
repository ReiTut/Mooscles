package com.mooscles.screens.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.ReorderAnnouncerTag
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.INJECTS_DRAG
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.ExercisePickerAddTag
import com.mooscles.screens.workout.RenameSheetTags
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.screens.workout.restShortcutTag
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The `zIndex` modifier of the node's layout, read from its modifier info (the layout info has no accessor for it). */
private fun androidx.compose.ui.semantics.SemanticsNode.zIndexOf(): Float =
    layoutInfo.getModifierInfo().firstNotNullOfOrNull { Regex("zIndex=(-?[0-9.]+)").find(it.modifier.toString())?.groupValues?.get(1)?.toFloat() } ?: 0f

private fun androidx.compose.ui.semantics.SemanticsNode.tag(): String? = config.getOrNull(SemanticsProperties.TestTag)

/** The routine editor (spec "Editor de rotina"): name, menus, cards, rest chip, target, drag, adding, replacing, starting. */
@OptIn(ExperimentalTestApi::class)
class RoutineEditorScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell")
    private val fly = ExerciseUi("fly", "Crucifixo na polia", "Chest", "Cable")
    private val curl = ExerciseUi("curl", "Rosca direta", "Biceps", "Dumbbell")
    private val squat = ExerciseUi("squat", "Agachamento livre", "Quadriceps", "Barbell")
    private val catalog = listOf(bench, row, fly, curl, squat)

    private fun set(id: String, reps: Int, kg: Double?, inherited: Boolean = true, warmup: Boolean = false) =
        RoutineSetUi(id, reps, kg, inherited = inherited && !warmup, warmup = warmup)

    /** Manual targets: a warm-up and two working sets of 8 × 80 kg. */
    private val e1 = PlannedExerciseUi(
        "e1", bench, sets = 3, reps = 8, weightKg = 80.0, restSeconds = 135,
        setTargets = listOf(
            set("e1w", 10, 40.0, warmup = true),
            set("e1a", 8, 80.0, inherited = false),
            set("e1b", 8, 80.0, inherited = false),
        ),
    )

    /** Its working sets follow the record, but the warm-up has values, which a replacement empties. */
    private val e2 = PlannedExerciseUi(
        "e2", row, sets = 3, reps = 10, weightKg = 60.0, restSeconds = 120,
        setTargets = listOf(set("e2w", 10, 30.0, warmup = true), set("e2a", 10, 60.0), set("e2b", 10, 60.0)),
    )

    private val e3 = PlannedExerciseUi(
        "e3", fly, sets = 1, reps = 12, weightKg = 25.0, restSeconds = 300,
        setTargets = listOf(set("e3a", 12, 25.0)),
    )

    private val routine = RoutineUi("r", "Treino A", listOf(e1, e2, e3), folderId = "default")
    private val other = RoutineUi("o", "Treino B", listOf(e3.copy(id = "o1")), folderId = "default")
    private val folders = listOf(RoutineFolderUi("default", "My routines"), RoutineFolderUi("hyper", "Hipertrofia"))

    private fun stateOf(
        r: RoutineUi? = routine,
        active: WorkoutUi? = null,
        loading: Boolean = false,
    ) = mutableStateOf(
        UiState(
            loading = loading, exercises = catalog, routines = listOfNotNull(r, other), routineFolders = folders, active = active,
        ),
    )

    private fun activeFrom(routineId: String?, name: String = "Treino em andamento") =
        WorkoutUi("saved-session", name, 1_000, sourceRoutineId = routineId)

    /** The view model: records every call and applies the ones whose echo a test wants. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        /** False: a copy or an addition does not come back until [deliver]. */
        var echo = true

        /** False: a reorder is recorded but the state keeps its order. */
        var applyReorder = true

        /** False: a delete is recorded but the routine stays until [removeRoutine]. */
        var applyDelete = true
        private val undelivered = mutableListOf<() -> Unit>()

        fun deliver() {
            undelivered.forEach { it() }
            undelivered.clear()
        }

        fun edit(id: String = "r", change: (RoutineUi) -> RoutineUi) {
            state.value = state.value.copy(routines = state.value.routines.map { if (it.id == id) change(it) else it })
        }

        fun removeRoutine(id: String = "r") {
            state.value = state.value.copy(routines = state.value.routines.filter { it.id != id })
        }

        private fun arrive(apply: () -> Unit) {
            if (echo) apply() else undelivered += apply
        }

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null, errorSubject = null)
        }

        override fun renameRoutine(id: String, name: String) {
            recording.renameRoutine(id, name)
            edit(id) { it.copy(name = name) }
        }

        override fun moveRoutineToFolder(routineId: String, folderId: String) {
            recording.moveRoutineToFolder(routineId, folderId)
            edit(routineId) { it.copy(folderId = folderId) }
        }

        override fun deleteRoutine(id: String) {
            recording.deleteRoutine(id)
            if (applyDelete) removeRoutine(id)
        }

        override fun duplicateRoutine(id: String, copyName: String, newId: String) {
            recording.duplicateRoutine(id, copyName, newId)
            val source = state.value.routines.first { it.id == id }
            arrive { state.value = state.value.copy(routines = state.value.routines + source.copy(id = newId, name = copyName)) }
        }

        override fun reorderRoutineExercises(routineId: String, ids: List<String>) {
            recording.reorderRoutineExercises(routineId, ids)
            if (!applyReorder) return
            edit(routineId) { r -> r.copy(exercises = ids.mapNotNull { id -> r.exercises.firstOrNull { it.id == id } }) }
        }

        override fun removeRoutineExercise(entryId: String) {
            recording.removeRoutineExercise(entryId)
            edit { r -> r.copy(exercises = r.exercises.filter { it.id != entryId }) }
        }

        override fun addRoutineExercises(routineId: String, exerciseIds: List<String>) {
            recording.addRoutineExercises(routineId, exerciseIds)
            val added = exerciseIds.map { id ->
                val exercise = state.value.exercises.first { it.id == id }
                PlannedExerciseUi("new-$id", exercise, sets = 1, setTargets = listOf(RoutineSetUi("new-$id-s", 0, null)))
            }
            arrive { edit(routineId) { it.copy(exercises = it.exercises + added) } }
        }

        override fun updateRoutineExerciseRest(entryId: String, seconds: Int) {
            recording.updateRoutineExerciseRest(entryId, seconds)
            edit { r -> r.copy(exercises = r.exercises.map { if (it.id == entryId) it.copy(restSeconds = seconds) else it }) }
        }
    }

    /** What the route asked of the app. */
    private class Requests {
        var back = 0
        var gone = 0
        var workout = 0
        val info = mutableListOf<String>()
        val opened = mutableListOf<String>()
    }

    /** [content] from the window's top down, [height] tall and [width] wide when given. */
    @Composable
    private fun TopAnchored(height: Dp?, width: Dp?, content: @Composable () -> Unit) {
        if (height == null && width == null) {
            content()
            return
        }
        Box(Modifier.fillMaxSize()) {
            val sized = Modifier
                .align(Alignment.TopStart)
                .let { if (width != null) it.requiredWidth(width) else it.fillMaxWidth() }
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .let { if (height != null) it.requiredHeight(height) else it }
            Box(sized) { content() }
        }
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = stateOf(),
        fake: Fake = Fake(state),
        routineId: String = "r",
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        width: Dp? = null,
        tall: Boolean = true,
        height: Dp? = null,
        canOpenRoutines: Boolean = false,
    ): Requests {
        val requests = Requests()
        withScreen(locale = locale, fontScale = fontScale) {
            TopAnchored(height ?: if (tall) 2400.dp else null, width) {
                RoutineEditorRoute(
                    routineId = routineId,
                    state = state.value,
                    actions = fake,
                    onBack = { requests.back++ },
                    onGone = { requests.gone++ },
                    onOpenWorkout = { requests.workout++ },
                    onExerciseInfo = { requests.info += it },
                    onOpenRoutine = if (canOpenRoutines) ({ requests.opened += it }) else null,
                )
            }
        }
        waitForIdle()
        return requests
    }

    private fun ComposeUiTest.pick(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.inDialog(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag("dialog")), useUnmergedTree = true)

    private fun ComposeUiTest.toastWith(text: String) =
        onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)

    private fun ComposeUiTest.fieldText(): String =
        onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.openExerciseMenu(entryId: String) {
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.menu(entryId)))
        waitForIdle()
        onNodeWithTag(RoutineEditorTags.menu(entryId)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openTopMenu() {
        pick(RoutineEditorTags.MENU)
    }

    /** The entry ids of the headers on screen, top to bottom. */
    private fun ComposeUiTest.headerIds(): List<String> =
        onAllNodes(SemanticsMatcher("is an exercise header") { it.tag()?.let { t -> t.startsWith("routine-exercise-") && t.endsWith("-header") } == true })
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag].removePrefix("routine-exercise-").removeSuffix("-header") }

    /** The "All" row of [exerciseId] in the open picker, once its list is built (off the main thread). */
    private fun ComposeUiTest.pickerRow(exerciseId: String): SemanticsNodeInteraction {
        val tag = exercisePickerRowTag(exerciseId, recent = false)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        return onNode(hasTestTag(tag))
    }

    // --- The name and the bar ------------------------------------------------------------------

    @Test
    fun nameIsTheFirstContentLineAndRenames() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        // The name is the list's first line, not part of the bar; "Voltar" is the bar's.
        val name = onNodeWithTag(RoutineEditorTags.NAME)
        name.assertTextEquals("Treino A")
        onNode(hasTestTag(RoutineEditorTags.NAME) and hasAnyAncestor(hasTestTag(RoutineEditorTag))).assertExists()
        val nameTop = name.fetchSemanticsNode().boundsInRoot.top
        val firstHeaderTop = onNodeWithTag(RoutineEditorTags.header("e1")).fetchSemanticsNode().boundsInRoot.top
        assertTrue(nameTop < firstHeaderTop)
        onNode(hasContentDescription("Voltar")).performClick()
        assertEquals(1, requests.back)
        // Tapping it renames: the sheet starts with the name.
        name.performClick()
        waitForIdle()
        assertEquals("Treino A", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Costas e bíceps")
        pick(RenameSheetTags.SAVE)
        assertEquals(listOf("renameRoutine:r,Costas e bíceps"), fake.calls)
        onNodeWithTag(RoutineEditorTags.NAME).assertTextEquals("Costas e bíceps")
        // At the default size too, the name is a 48 dp target and the whole screen's clickables are labelled and sized.
        onNodeWithTag(RoutineEditorTags.NAME).assertHeightIsAtLeast(48.dp)
        assertClickablesLabeledAndSized("Routine editor at 1x")
    }

    @Test
    fun topMenuRenameDuplicateMoveDelete() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake, tall = false)
        openTopMenu()
        for (tag in RoutineEditorTags.MENU_ITEMS) onNodeWithTag(tag).assertHasClickAction()
        // Rename.
        pick(RoutineEditorTags.MENU_RENAME)
        assertEquals("Treino A", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Push")
        pick(RenameSheetTags.SAVE)
        assertEquals("renameRoutine:r,Push", fake.calls.last())
        // Duplicate: the copy's name carries "(cópia)" and its id is the screen's; the toast comes on the echo.
        fake.echo = false
        openTopMenu()
        pick(RoutineEditorTags.MENU_DUPLICATE)
        val call = fake.calls.last()
        assertTrue(call.startsWith("duplicateRoutine:r,Push (cópia),"), call)
        toastWith("Rotina duplicada").assertDoesNotExist()
        // A second copy waits for the first.
        openTopMenu()
        onNodeWithTag(RoutineEditorTags.MENU_DUPLICATE).assertIsNotEnabled()
        onNodeWithTag(RoutineEditorTags.MENU_DUPLICATE).assertHasClickAction()
        onNodeWithTag(RoutineEditorTags.MENU_RENAME).performClick() // closes the menu
        waitForIdle()
        onNodeWithTag(RenameSheetTags.FIELD).assertExists()
        pick(RenameSheetTags.SAVE)
        fake.deliver()
        waitForIdle()
        toastWith("Rotina duplicada").assertIsDisplayed()
        // Move to folder: the current one is marked.
        openTopMenu()
        pick(RoutineEditorTags.MENU_MOVE)
        onNodeWithText("Mover para pasta").assertIsDisplayed()
        onNode(hasContentDescription("Pasta atual") and hasAnyAncestor(hasTestTag(TrainTags.moveTo("default"))), useUnmergedTree = true).assertExists()
        pick(TrainTags.moveTo("hyper"))
        assertEquals("moveRoutineToFolder:r,hyper", fake.calls.last())
        // Delete asks first, and says the history stays.
        openTopMenu()
        pick(RoutineEditorTags.MENU_DELETE)
        inDialog("Excluir Push?").assertIsDisplayed()
        inDialog("Os treinos já feitos com ela continuam no histórico.").assertIsDisplayed()
        assertTrue(fake.calls.none { it.startsWith("deleteRoutine") })
        inDialog("Cancelar").performClick()
        waitForIdle()
        assertTrue(fake.calls.none { it.startsWith("deleteRoutine") })
        openTopMenu()
        pick(RoutineEditorTags.MENU_DELETE)
        inDialog("Excluir").performClick()
        waitForIdle()
        assertEquals("deleteRoutine:r", fake.calls.last())
    }

    @Test
    fun deletingReturnsToTrainOnTheEcho() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { applyDelete = false }
        val requests = show(state = fake.state, fake = fake, tall = false)
        openTopMenu()
        pick(RoutineEditorTags.MENU_DELETE)
        inDialog("Excluir").performClick()
        waitForIdle()
        // The command is sent, but the routine is still in the state: the screen stays.
        assertEquals("deleteRoutine:r", fake.calls.last())
        assertEquals(0, requests.gone)
        fake.removeRoutine()
        waitForIdle()
        assertEquals(1, requests.gone)
        // A later emission changes nothing.
        fake.state.value = fake.state.value.copy(preferences = fake.state.value.preferences.copy(pounds = true))
        waitForIdle()
        assertEquals(1, requests.gone)
    }

    @Test
    fun goneRoutineCallsOnGoneOnce() = runComposeUiTest {
        val state = stateOf()
        val requests = show(state = state, routineId = "missing")
        assertEquals(1, requests.gone)
        state.value = state.value.copy(preferences = state.value.preferences.copy(pounds = true))
        waitForIdle()
        state.value = state.value.copy(routines = emptyList())
        waitForIdle()
        assertEquals(1, requests.gone)
    }

    @Test
    fun loadingDoesNotCallOnGone() = runComposeUiTest {
        // After process death the route is back with the state loading and no routines: nothing is gone yet.
        val state = stateOf(r = null, loading = true).apply { value = value.copy(routines = emptyList()) }
        val requests = show(state = state)
        onNodeWithTag(RoutineEditorTags.SKELETON).assertExists()
        assertEquals(0, requests.gone)
        // The routines arrive with the routine in them: still nothing.
        state.value = state.value.copy(loading = false, routines = listOf(routine))
        waitForIdle()
        onNodeWithTag(RoutineEditorTags.NAME).assertTextEquals("Treino A")
        assertEquals(0, requests.gone)
        // Loaded and the routine absent is gone.
        state.value = state.value.copy(routines = emptyList())
        waitForIdle()
        assertEquals(1, requests.gone)
    }

    // --- The cards -----------------------------------------------------------------------------

    @Test
    fun headerHasHandleMediaNameMetaAndMenu() = runComposeUiTest {
        show()
        val header = hasTestTag(RoutineEditorTags.header("e1"))
        onNodeWithTag(RoutineEditorTags.handle("e1"), useUnmergedTree = true).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        onNodeWithTag(RoutineEditorTags.handle("e1"), useUnmergedTree = true)
            .assertContentDescriptionEquals("Reordenar Supino reto")
        onNode(hasText("Supino reto") and hasAnyAncestor(header), useUnmergedTree = true).assertExists()
        onNode(hasText("Peito · Barra") and hasAnyAncestor(header), useUnmergedTree = true).assertExists()
        // The media tile is in the header: this exercise has no figure or photo, so it is the equipment's icon.
        onNode(hasTestTag(ExerciseEquipmentIconTag) and hasAnyAncestor(header), useUnmergedTree = true).assertExists()
        onNodeWithTag(RoutineEditorTags.menu("e1"))
            .assertContentDescriptionEquals("Opções de Supino reto")
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        // The three cards are in the saved order, each with its planned sets and footer.
        assertEquals(listOf("e1", "e2", "e3"), headerIds())
        onNodeWithTag(RoutineEditorTags.set("e1w")).assertExists()
        onNodeWithTag(RoutineEditorTags.set("e1a")).assertExists()
        onNodeWithTag(RoutineEditorTags.footer("e1")).assertExists()
        // The warm-up is lettered, the working sets are numbered from 1.
        onNode(hasText("A") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e1w"))), useUnmergedTree = true).assertExists()
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e1a"))), useUnmergedTree = true).assertExists()
        onNode(hasText("2") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e1b"))), useUnmergedTree = true).assertExists()
    }

    /** A card's loads and reps stand in one column, whether a set follows the record (its icon) or not (a warm-up). */
    @Test
    fun loadsAndRepsLineUpWithAndWithoutTheRecordIcon() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTags.setRecord("e2a"), useUnmergedTree = true).assertExists()
        for (field in listOf(RoutineEditorTags::setLoad, RoutineEditorTags::setReps)) {
            val lefts = listOf("e2w", "e2a", "e2b").map { onNodeWithTag(field(it)).fetchSemanticsNode().boundsInRoot.left }
            assertEquals(lefts.first(), lefts.max(), 0.5f, "lefts $lefts")
            assertEquals(lefts.first(), lefts.min(), 0.5f, "lefts $lefts")
        }
    }

    @Test
    fun restChipOpensTheRestSheetAndSavesOnlyThatEntry() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake, tall = false)
        onNodeWithTag(RoutineEditorTags.rest("e1")).assertTextEquals("Descanso 2:15")
        pick(RoutineEditorTags.rest("e1"))
        // The sheet of the phase 3 minus "Start rest now", which has nothing to start here.
        onNodeWithTag(StepperValueTag).assertTextEquals("2:15")
        onNodeWithText("Iniciar descanso agora").assertDoesNotExist()
        // The note is the routine's, not the workout's.
        onNodeWithText("Vale para os treinos que você começar com esta rotina.").assertIsDisplayed()
        onNodeWithText("Vale para os próximos descansos deste exercício", substring = true).assertDoesNotExist()
        pick(StepperIncreaseTag)
        onNodeWithTag(StepperValueTag).assertTextEquals("2:30")
        pick(restShortcutTag(180))
        onNodeWithTag(StepperValueTag).assertTextEquals("3:00")
        assertEquals(listOf("updateRoutineExerciseRest:e1,150", "updateRoutineExerciseRest:e1,180"), fake.calls)
        // Only that entry changed.
        onNodeWithTag(RoutineEditorTags.rest("e2")).assertTextEquals("Descanso 2:00")
        // The sheet closes with the entry: removed elsewhere, it goes away by itself.
        fake.edit { r -> r.copy(exercises = r.exercises.filter { it.id != "e1" }) }
        waitForIdle()
        onNodeWithTag(StepperValueTag).assertDoesNotExist()
        // Five minutes is the top: no more steps.
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.rest("e3")))
        pick(RoutineEditorTags.rest("e3"))
        onNodeWithTag(StepperValueTag).assertTextEquals("5:00")
        onNodeWithTag(StepperIncreaseTag).assertIsNotEnabled()
    }

    @Test
    fun targetLineInTheSupportRow() = runComposeUiTest {
        show()
        val header = hasTestTag(RoutineEditorTags.header("e1"))
        // The chip and the target line are in the same header: working sets only, the warm-up counted apart.
        onNode(hasTestTag(RoutineEditorTags.rest("e1")) and hasAnyAncestor(header), useUnmergedTree = true).assertExists()
        onNode(hasTestTag("card-subline") and hasAnyAncestor(header), useUnmergedTree = true)
            .assertTextContains("Alvo 2×8 · 80 kg + 1 aquec.")
        // The row's inherited sets: a warm-up too, and the working ones at 10 × 60.
        onNode(hasTestTag("card-subline") and hasAnyAncestor(hasTestTag(RoutineEditorTags.header("e2"))), useUnmergedTree = true)
            .assertTextContains("Alvo 2×10 · 60 kg + 1 aquec.")
    }

    // --- Dragging and moving -------------------------------------------------------------------

    private fun ComposeUiTest.lift(entryId: String) {
        val handle = onNodeWithTag(RoutineEditorTags.handle(entryId), useUnmergedTree = true)
        var longPress = 0L
        handle.performTouchInput {
            longPress = viewConfiguration.longPressTimeoutMillis
            down(center)
        }
        mainClock.advanceTimeBy(longPress + 100)
        waitForIdle()
    }

    private fun ComposeUiTest.dragBy(dy: Float) {
        val step = dy / 20
        repeat(20) {
            onNodeWithTag(RoutineEditorTag).performTouchInput { moveBy(Offset(0f, step)) }
            waitForIdle()
        }
    }

    private fun ComposeUiTest.release() {
        onNodeWithTag(RoutineEditorTag).performTouchInput { up() }
        waitForIdle()
    }

    /** The distance between the tops of the first two collapsed headers, with the list as it is now. */
    private fun ComposeUiTest.headerPitch(): Float {
        val a = onNodeWithTag(RoutineEditorTags.header("e1")).fetchSemanticsNode().boundsInRoot.top
        val b = onNodeWithTag(RoutineEditorTags.header("e2")).fetchSemanticsNode().boundsInRoot.top
        return b - a
    }

    @Test
    fun dragCollapsesCardsAndReordersOnDrop() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTags.set("e1a")).assertExists()
        lift("e1")
        // Decision 14: while one is dragged, every card shows only its header.
        onAllNodesWithTag(RoutineEditorTags.set("e1a")).assertCountEquals(0)
        onAllNodesWithTag(RoutineEditorTags.set("e2a")).assertCountEquals(0)
        onAllNodesWithTag(RoutineEditorTags.footer("e1")).assertCountEquals(0)
        assertEquals(listOf("e1", "e2", "e3"), headerIds())
        dragBy(headerPitch() * 1.1f)
        // Nothing is written until the finger lifts.
        assertEquals(emptyList(), fake.calls)
        release()
        assertEquals(listOf("reorderRoutineExercises:r,e2|e1|e3"), fake.calls)
        assertEquals(listOf("e2", "e1", "e3"), headerIds())
        // The cards open again.
        onNodeWithTag(RoutineEditorTags.set("e1a")).assertExists()
        onNodeWithTag(RoutineEditorTags.footer("e1")).assertExists()
    }

    @Test
    fun liftedCardIsRaisedAmongTheListsItems() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        lift("e1")
        dragBy(headerPitch() * 0.5f)
        // Dragged down over the next card: the header is a direct item of the list (a sibling of the other headers, not
        // inside a wrapper) and drawn above them, so it is not hidden under the next one.
        val lifted = onNodeWithTag(RoutineEditorTags.header("e1")).fetchSemanticsNode()
        val next = onNodeWithTag(RoutineEditorTags.header("e2")).fetchSemanticsNode()
        assertTrue(lifted.zIndexOf() > next.zIndexOf(), "z ${lifted.zIndexOf()} vs ${next.zIndexOf()}")
        assertTrue(lifted.layoutInfo.parentInfo === next.layoutInfo.parentInfo)
        assertTrue(lifted.boundsInRoot.bottom > next.boundsInRoot.top, "the lifted card overlaps the next one")
        release()
    }

    @Test
    fun exerciseRemovedElsewhereDuringADragDropsWithoutAnError() {
        if (!INJECTS_DRAG) return
        // Another exercise leaves while one is dragged: the drop carries exactly the ones that are left.
        runComposeUiTest {
            val fake = Fake(stateOf())
            show(state = fake.state, fake = fake)
            lift("e1")
            dragBy(headerPitch() * 1.1f)
            fake.edit { r -> r.copy(exercises = r.exercises.filter { it.id != "e3" }) }
            waitForIdle()
            release()
            assertEquals(listOf("reorderRoutineExercises:r,e2|e1"), fake.calls)
            assertEquals(null, fake.state.value.errorCode)
            assertEquals(listOf("e2", "e1"), headerIds())
        }
        LocalAppLocale.restore()
        // The dragged one leaves: the drag ends and nothing is written.
        runComposeUiTest {
            val fake = Fake(stateOf())
            show(state = fake.state, fake = fake)
            lift("e1")
            fake.edit { r -> r.copy(exercises = r.exercises.filter { it.id != "e1" }) }
            waitForIdle()
            release()
            assertEquals(emptyList(), fake.calls)
            assertEquals(null, fake.state.value.errorCode)
            assertEquals(listOf("e2", "e3"), headerIds())
            onNodeWithTag(RoutineEditorTags.set("e2a")).assertExists()
        }
    }

    @Test
    fun menuMovesUpDownTopBottom() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        openExerciseMenu("e1")
        for (tag in RoutineEditorTags.EXERCISE_ITEMS) onNodeWithTag(tag).assertHasClickAction()
        onNodeWithTag(RoutineEditorTags.EXERCISE_UP).assertIsNotEnabled()
        onNodeWithTag(RoutineEditorTags.EXERCISE_TOP).assertIsNotEnabled()
        pick(RoutineEditorTags.EXERCISE_DOWN)
        assertEquals("reorderRoutineExercises:r,e2|e1|e3", fake.calls.last())
        assertEquals(listOf("e2", "e1", "e3"), headerIds())
        openExerciseMenu("e3")
        onNodeWithTag(RoutineEditorTags.EXERCISE_DOWN).assertIsNotEnabled()
        onNodeWithTag(RoutineEditorTags.EXERCISE_BOTTOM).assertIsNotEnabled()
        pick(RoutineEditorTags.EXERCISE_TOP)
        assertEquals("reorderRoutineExercises:r,e3|e2|e1", fake.calls.last())
        openExerciseMenu("e3")
        pick(RoutineEditorTags.EXERCISE_BOTTOM)
        assertEquals("reorderRoutineExercises:r,e2|e1|e3", fake.calls.last())
        openExerciseMenu("e3")
        pick(RoutineEditorTags.EXERCISE_UP)
        assertEquals("reorderRoutineExercises:r,e2|e3|e1", fake.calls.last())
        // The write has not come back: the list already shows the new order, and the move is announced.
        fake.applyReorder = false
        openExerciseMenu("e2")
        mainClock.autoAdvance = false
        onNodeWithTag(RoutineEditorTags.EXERCISE_BOTTOM).performClick()
        mainClock.advanceTimeBy(200)
        assertEquals("reorderRoutineExercises:r,e3|e1|e2", fake.calls.last())
        onNodeWithTag(ReorderAnnouncerTag).assertContentDescriptionEquals("Remada curvada agora na posição 3 de 3")
    }

    @Test
    fun duplicatedToastOpensTheCopyWhenTheHostCanOpenIt() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake, tall = false, canOpenRoutines = true)
        openTopMenu()
        pick(RoutineEditorTags.MENU_DUPLICATE)
        val copyId = fake.calls.last().substringAfterLast(",")
        toastWith("Rotina duplicada").assertIsDisplayed()
        onNode(hasText("Abrir") and hasAnyAncestor(hasTestTag("toast-action")), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("toast-action", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(listOf(copyId), requests.opened)
    }

    // --- Adding, replacing, removing -----------------------------------------------------------

    @Test
    fun addExercisesUsesTheMultiPickerInTapOrder() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { echo = false }
        show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
        pick(RoutineEditorTags.ADD)
        // Nothing selected: the button is off and nothing is ever sent for an empty selection.
        pickerRow("curl")
        onNodeWithTag(ExercisePickerAddTag).assertTextEquals("Adicionar 0 exercícios").assertIsNotEnabled()
        // The taps are the order: Rosca first, Agachamento second (alphabetical would be the other way).
        pickerRow("curl").performClick()
        waitForIdle()
        pickerRow("squat").performClick()
        waitForIdle()
        onNodeWithTag(ExercisePickerAddTag).assertTextEquals("Adicionar 2 exercícios")
        pick(ExercisePickerAddTag)
        assertEquals(listOf("addRoutineExercises:r,curl|squat"), fake.calls)
        // Busy until the routine holds them: "Add exercises" opens no second picker.
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
        pick(RoutineEditorTags.ADD)
        onAllNodesWithTag(ExercisePickerAddTag).assertCountEquals(0)
        // The addition lands in the same emission as a removal elsewhere: the new ids are the echo, not the count.
        fake.edit { r -> r.copy(exercises = r.exercises.filter { it.id != "e3" }) }
        fake.deliver()
        waitForIdle()
        assertEquals(listOf("e1", "e2", "new-curl", "new-squat"), headerIds())
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
        pick(RoutineEditorTags.ADD)
        onNodeWithTag(ExercisePickerAddTag).assertExists()
    }

    @Test
    fun addWaitReleasesOnItsOwnFailureAndAtTheCap() {
        // The failure of the command about this routine releases the wait, with the error shown.
        runComposeUiTest {
            val fake = Fake(stateOf()).apply { echo = false }
            show(state = fake.state, fake = fake, tall = false)
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
            pick(RoutineEditorTags.ADD)
            pickerRow("curl").performClick()
            waitForIdle()
            pick(ExercisePickerAddTag)
            // A failure about another id changes nothing.
            fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = "other", subjectFailures = mapOf("other" to 1))
            waitForIdle()
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
            pick(RoutineEditorTags.ADD)
            onAllNodesWithTag(ExercisePickerAddTag).assertCountEquals(0)
            fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = "r", subjectFailures = mapOf("other" to 1, "r" to 1))
            waitForIdle()
            toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
            pick(RoutineEditorTags.ADD)
            onNodeWithTag(ExercisePickerAddTag).assertExists()
        }
        LocalAppLocale.restore()
        // A lost command: after the cap the button is free again, with the error.
        runComposeUiTest {
            val fake = Fake(stateOf()).apply { echo = false }
            show(state = fake.state, fake = fake, tall = false)
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
            pick(RoutineEditorTags.ADD)
            pickerRow("curl").performClick()
            waitForIdle()
            pick(ExercisePickerAddTag)
            mainClock.advanceTimeBy(MoosclesTiming().saveEchoWait + 1_000)
            waitForIdle()
            toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
            onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.ADD))
            pick(RoutineEditorTags.ADD)
            onNodeWithTag(ExercisePickerAddTag).assertExists()
        }
    }

    @Test
    fun replaceAsksOnlyWithManualTargets() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake, tall = false)
        // The fly follows the record in its only set and has no warm-up: nothing to lose, no question.
        openExerciseMenu("e3")
        pick(RoutineEditorTags.EXERCISE_REPLACE)
        onNodeWithText("Trocar o exercício?").assertDoesNotExist()
        pickerRow("curl").performClick()
        waitForIdle()
        assertEquals("replaceRoutineExercise:e3,curl", fake.calls.last())
        // A warm-up with no values has nothing to lose either.
        fake.edit { r -> r.copy(exercises = r.exercises.map { if (it.id == "e3") it.copy(setTargets = it.setTargets + set("e3w", 0, null, warmup = true)) else it }) }
        waitForIdle()
        openExerciseMenu("e3")
        pick(RoutineEditorTags.EXERCISE_REPLACE)
        onNodeWithText("Trocar o exercício?").assertDoesNotExist()
        pickerRow("squat").performClick()
        waitForIdle()
        assertEquals("replaceRoutineExercise:e3,squat", fake.calls.last())
        // The row's working sets follow the record, but its warm-up (10 x 30 kg) would be emptied: the question says so.
        openExerciseMenu("e2")
        pick(RoutineEditorTags.EXERCISE_REPLACE)
        inDialog("Trocar o exercício?").assertIsDisplayed()
        inDialog("Os alvos manuais dele voltam a seguir o recorde e os aquecimentos ficam vazios.").assertIsDisplayed()
        inDialog("Cancelar").performClick()
        waitForIdle()
        onAllNodesWithTag(ExercisePickerAddTag).assertCountEquals(0)
        assertEquals(2, fake.calls.size)
        // The bench has manual working sets: it asks too, and "Trocar" goes on to the picker.
        openExerciseMenu("e1")
        pick(RoutineEditorTags.EXERCISE_REPLACE)
        inDialog("Trocar o exercício?").assertIsDisplayed()
        inDialog("Trocar").performClick()
        waitForIdle()
        pickerRow("squat").performClick()
        waitForIdle()
        assertEquals("replaceRoutineExercise:e1,squat", fake.calls.last())
        assertEquals(3, fake.calls.size)
    }

    @Test
    fun removeAsksAndNamesThePlannedSets() {
        runComposeUiTest {
            val fake = Fake(stateOf())
            show(state = fake.state, fake = fake, tall = false)
            openExerciseMenu("e1")
            pick(RoutineEditorTags.EXERCISE_REMOVE)
            inDialog("Remover Supino reto da rotina?").assertIsDisplayed()
            inDialog("As 3 séries planejadas saem.").assertIsDisplayed()
            inDialog("Cancelar").performClick()
            waitForIdle()
            assertEquals(emptyList(), fake.calls)
            openExerciseMenu("e1")
            pick(RoutineEditorTags.EXERCISE_REMOVE)
            inDialog("Remover").performClick()
            waitForIdle()
            assertEquals(listOf("removeRoutineExercise:e1"), fake.calls)
            assertEquals(listOf("e2", "e3"), headerIds())
        }
        LocalAppLocale.restore()
        // One planned set is said in the singular.
        runComposeUiTest {
            val fake = Fake(stateOf())
            show(state = fake.state, fake = fake, tall = false)
            openExerciseMenu("e3")
            pick(RoutineEditorTags.EXERCISE_REMOVE)
            inDialog("A série planejada sai.").assertIsDisplayed()
        }
    }

    @Test
    fun aboutOpensTheExerciseInfo() = runComposeUiTest {
        val requests = show(tall = false)
        openExerciseMenu("e2")
        pick(RoutineEditorTags.EXERCISE_ABOUT)
        assertEquals(listOf("row"), requests.info)
        onNodeWithTag(RoutineEditorTags.EXERCISE_ABOUT).assertDoesNotExist()
    }

    // --- Starting ------------------------------------------------------------------------------

    @Test
    fun startStartsAndOpensTheWorkout() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTags.START).assertTextEquals("Começar treino")
        pick(RoutineEditorTags.START)
        assertEquals(listOf("startWorkout:r"), fake.calls)
        assertEquals(1, requests.workout)
    }

    @Test
    fun startOfTheSourceRoutineResumes() = runComposeUiTest {
        val fake = Fake(stateOf(active = activeFrom("r")))
        val requests = show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTags.START).assertTextEquals("Retomar treino")
        pick(RoutineEditorTags.START)
        assertEquals(emptyList(), fake.calls)
        assertEquals(1, requests.workout)
    }

    @Test
    fun editorAlsoProtectsAnUnrelatedActiveSession() = runComposeUiTest {
        val fake = Fake(stateOf(active = activeFrom("o", "Treino B")))
        val requests = show(state = fake.state, fake = fake)
        pick(RoutineEditorTags.START)
        onNodeWithText("Já existe um treino em andamento").assertIsDisplayed()
        assertEquals(emptyList(), fake.calls)
        assertEquals(0, requests.workout)
        // "Retomar treino" opens the workout that is there and starts nothing.
        inDialog("Retomar treino").performClick()
        waitForIdle()
        assertEquals(1, requests.workout)
        assertEquals(emptyList(), fake.calls)
        onNodeWithText("Já existe um treino em andamento").assertDoesNotExist()
    }

    // --- States and sizes ----------------------------------------------------------------------

    @Test
    fun emptyRoutineOffersAddExercises() = runComposeUiTest {
        val empty = routine.copy(exercises = emptyList())
        val fake = Fake(stateOf(r = empty))
        show(state = fake.state, fake = fake)
        onNodeWithTag(RoutineEditorTags.EMPTY).assertIsDisplayed()
        onNodeWithText("Rotina sem exercícios").assertIsDisplayed()
        onNodeWithTag(RoutineEditorTags.ADD).assertTextEquals("Adicionar exercícios")
        pick(RoutineEditorTags.ADD)
        pickerRow("bench")
        onNodeWithTag(ExercisePickerAddTag).assertExists()
        assertEquals(emptyList(), fake.calls)
    }

    @Test
    fun startIsFiftySixAndTextsWholeAtTwoX() = runComposeUiTest {
        show(fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 1200.dp)
        onNodeWithTag(RoutineEditorTags.START).assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(56.dp)
        onNodeWithTag(RoutineEditorTags.MENU).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        onNodeWithTag(RoutineEditorTags.handle("e1"), useUnmergedTree = true).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        onNodeWithTag(RoutineEditorTags.menu("e1")).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertClickablesLabeledAndSized("Routine editor at 2x")
        assertButtonTextsWhole("Routine editor at 2x")
    }

    @Test
    fun menusScrollAtTwoXInAShortWindow() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake, fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 640.dp)
        openTopMenu()
        onNodeWithTag(RoutineEditorTags.MENU_LIST).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        onNodeWithTag(RoutineEditorTags.MENU_LIST).performScrollToNode(hasTestTag(RoutineEditorTags.MENU_DELETE))
        pick(RoutineEditorTags.MENU_DELETE)
        inDialog("Excluir Treino A?").assertIsDisplayed()
        inDialog("Cancelar").performClick()
        waitForIdle()
        openExerciseMenu("e1")
        onNodeWithTag(RoutineEditorTags.EXERCISE_MENU_LIST).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        assertClickablesLabeledAndSized("Editor menu at 2x")
        onNodeWithTag(RoutineEditorTags.EXERCISE_MENU_LIST).performScrollToNode(hasTestTag(RoutineEditorTags.EXERCISE_REMOVE))
        pick(RoutineEditorTags.EXERCISE_REMOVE)
        inDialog("Remover Supino reto da rotina?").assertIsDisplayed()
    }
}
