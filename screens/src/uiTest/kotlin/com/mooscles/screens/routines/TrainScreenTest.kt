package com.mooscles.screens.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.ReorderAnnouncerTag
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.INJECTS_DRAG
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.RenameSheet
import com.mooscles.screens.workout.RenameSheetTags
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/** The Train tab (spec "Treinar (aba)"): folders, routine cards, starting, the card menu, reordering and creating. */
@OptIn(ExperimentalTestApi::class)
class TrainScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private fun exercise(id: String, muscle: String) = ExerciseUi(id, "Exercício $id", muscle, "Barbell")
    private fun planned(id: String, muscle: String, sets: Int) = PlannedExerciseUi("p-$id", exercise(id, muscle), sets = sets)

    private val zone = TimeZone.currentSystemDefault()
    private val now = LocalDateTime(2026, 10, 30, 12, 0).toInstant(zone).toEpochMilliseconds()
    private fun daysBefore(n: Int): Long =
        LocalDate(2026, 10, 30).minus(DatePeriod(days = n)).atTime(8, 0).toInstant(zone).toEpochMilliseconds()

    private val a = RoutineUi(
        "a", "Treino A",
        listOf(planned("e1", "Chest", 4), planned("e2", "Chest", 3), planned("e3", "Shoulders", 3), planned("e4", "Triceps", 4), planned("e5", "Triceps", 3)),
        folderId = "default", lastFinishedAt = daysBefore(3),
    )
    private val b = RoutineUi("b", "Treino B", listOf(planned("e6", "Back", 3), planned("e7", "Back", 3)), folderId = "default")
    private val c = RoutineUi("c", "Treino C", emptyList(), folderId = "default")
    private val legs = RoutineUi("legs", "Pernas", listOf(planned("e8", "Quadriceps", 4)), folderId = "hyper")
    private val folders = listOf(
        RoutineFolderUi("default", "My routines"), RoutineFolderUi("hyper", "Hipertrofia"), RoutineFolderUi("empty", "Força"),
    )

    private fun stateOf(
        routines: List<RoutineUi> = listOf(a, b, c, legs),
        selected: String = "default",
        active: WorkoutUi? = null,
        loading: Boolean = false,
    ) = mutableStateOf(
        UiState(
            loading = loading, routines = routines, routineFolders = folders, active = active,
            preferences = PreferencesUi(selectedRoutineFolderId = selected),
        ),
    )

    private fun activeFrom(routineId: String?, name: String = "Treino em andamento") =
        WorkoutUi("saved-session", name, 1_000, sourceRoutineId = routineId)

    /** The view model: records every call and applies the ones whose echo a test wants. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        /** False: a created or duplicated routine does not come back until [deliver]. */
        var echo = true

        /** False: a reorder is recorded but the state keeps its order (a write whose echo has not come back). */
        var applyReorder = true
        private val undelivered = mutableListOf<RoutineUi>()

        fun deliver() {
            state.value = state.value.copy(routines = state.value.routines + undelivered)
            undelivered.clear()
        }

        private fun arrive(routine: RoutineUi) {
            if (echo) state.value = state.value.copy(routines = state.value.routines + routine) else undelivered += routine
        }

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null, errorSubject = null)
        }

        override fun selectRoutineFolder(id: String) {
            recording.selectRoutineFolder(id)
            state.value = state.value.copy(preferences = state.value.preferences.copy(selectedRoutineFolderId = id))
        }

        override fun moveRoutineToFolder(routineId: String, folderId: String) {
            recording.moveRoutineToFolder(routineId, folderId)
            state.value = state.value.copy(routines = state.value.routines.map { if (it.id == routineId) it.copy(folderId = folderId) else it })
        }

        override fun reorderRoutines(folderId: String, ids: List<String>) {
            recording.reorderRoutines(folderId, ids)
            if (!applyReorder) return
            val all = state.value.routines
            val inFolder = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
            var next = 0
            state.value = state.value.copy(routines = all.map { if (it.folderId == folderId) inFolder[next++] else it })
        }

        override fun createRoutine(name: String, folderId: String, id: String) {
            recording.createRoutine(name, folderId, id)
            arrive(RoutineUi(id, name, emptyList(), folderId))
        }

        override fun duplicateRoutine(id: String, copyName: String, newId: String) {
            recording.duplicateRoutine(id, copyName, newId)
            val source = state.value.routines.first { it.id == id }
            arrive(source.copy(id = newId, name = copyName, lastFinishedAt = null))
        }
    }

    /** What the route asked of the app. */
    private class Requests {
        val routines = mutableListOf<String>()
        var workout = 0
        var settings = 0
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
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        width: Dp? = null,
        tall: Boolean = true,
        height: Dp? = null,
        restoration: ScreenRestoration? = null,
        density: Float? = null,
    ): Requests {
        val requests = Requests()
        withScreen(locale = locale, fontScale = fontScale, restoration = restoration, density = density) {
            TopAnchored(height ?: if (tall) 2400.dp else null, width) {
                TrainRoute(
                    state = state.value,
                    actions = fake,
                    onOpenRoutine = { requests.routines += it },
                    onOpenWorkout = { requests.workout++ },
                    onOpenSettings = { requests.settings++ },
                    now = { now },
                )
            }
        }
        waitForIdle()
        return requests
    }

    private fun ComposeUiTest.start(id: String): SemanticsNodeInteraction {
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.start(id)))
        waitForIdle()
        return onNodeWithTag(TrainTags.start(id))
    }

    private val isStart = SemanticsMatcher("is a start button") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> tag.startsWith("routine-") && tag.endsWith("-start") } == true
    }

    private fun ComposeUiTest.openMenu(id: String) {
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.menu(id)))
        waitForIdle()
        onNodeWithTag(TrainTags.menu(id)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pick(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /** How far the scrolling node under [tag] can scroll down (its range's maximum). */
    private fun ComposeUiTest.scrollRangeOf(tag: String): Float =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** The node under [tag] is composed and in sight (a clipped or absent one is not). */
    private fun ComposeUiTest.isShown(tag: String): Boolean =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() && runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    private fun ComposeUiTest.textsOf(tag: String): List<String> =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun ComposeUiTest.inDialog(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag("dialog")), useUnmergedTree = true)

    private fun ComposeUiTest.toastWith(text: String) =
        onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)

    private fun ComposeUiTest.cardIds(): List<String> =
        onAllNodes(SemanticsMatcher("is a routine card") { it.config.getOrNull(SemanticsProperties.TestTag)?.let { t -> t.startsWith("routine-") && t.endsWith("-card") } == true })
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag].removePrefix("routine-").removeSuffix("-card") }

    // --- Folders -----------------------------------------------------------------------------

    @Test
    fun foldersAreChipsInSavedOrderWithTheSelectedHighlighted() = runComposeUiTest {
        show(state = stateOf(selected = "hyper"))
        val chips = folders.map { it.id to onNodeWithTag(TrainTags.folder(it.id)).getUnclippedBoundsInRoot() }
        // Saved order, left to right, on one row.
        assertEquals(folders.map { it.id }, chips.sortedBy { it.second.left }.map { it.first })
        onNodeWithTag(TrainTags.folder("default")).assertIsNotSelected()
        onNodeWithTag(TrainTags.folder("hyper")).assertIsSelected()
        onNodeWithTag(TrainTags.folder("empty")).assertIsNotSelected()
        onNodeWithTag(TrainTags.folder("hyper")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        // "Pastas" ends the row.
        onNodeWithTag(TrainTags.MANAGE_FOLDERS).assertTextEquals("Pastas")
        assertTrue(onNodeWithTag(TrainTags.MANAGE_FOLDERS).getUnclippedBoundsInRoot().left >= chips.maxOf { it.second.right } - 0.5.dp)
        onNodeWithTag(TrainTags.FOLDER_ROW).assertIsDisplayed()
        // Only the selected folder's routines.
        assertEquals(listOf("legs"), cardIds())
    }

    @Test
    fun selectingAFolderCallsSelect() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        pick(TrainTags.folder("hyper"))
        assertEquals(listOf("selectRoutineFolder:hyper"), fake.calls)
        assertEquals(listOf("legs"), cardIds())
        // The folder already selected asks for nothing.
        pick(TrainTags.folder("hyper"))
        assertEquals(listOf("selectRoutineFolder:hyper"), fake.calls)
    }

    @Test
    fun defaultFolderIsTranslated() {
        runComposeUiTest {
            show()
            onNode(hasText("Minhas rotinas") and hasAnyAncestor(hasTestTag(TrainTags.folder("default"))), useUnmergedTree = true).assertIsDisplayed()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            show(locale = "en")
            onNode(hasText("My routines") and hasAnyAncestor(hasTestTag(TrainTags.folder("default"))), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Train").assertIsDisplayed()
        }
    }

    // --- The card ----------------------------------------------------------------------------

    @Test
    fun cardIsOneNodeWithNameAndSummaryPlusStartAndMenu() = runComposeUiTest {
        val requests = show()
        // Name and summary are in one node, which opens the editor and is the only one with that name.
        val info = onNodeWithTag(TrainTags.info("a")).assertHasClickAction()
        val texts = textsOf(TrainTags.info("a"))
        assertTrue("Treino A" in texts && "5 exercícios · 17 séries" in texts, "$texts")
        onAllNodesWithText("Treino A").assertCountEquals(1)
        onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TrainTags.info("a"))), useUnmergedTree = true).assertCountEquals(0)
        info.performClick()
        // "Começar" and the menu are buttons of their own, outside that node.
        onNodeWithTag(TrainTags.start("a")).assertHasClickAction().assertTextEquals("Começar")
        onNodeWithTag(TrainTags.menu("a")).assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Opções de Treino A")))
        assertTrue(onNodeWithTag(TrainTags.start("a")).getUnclippedBoundsInRoot().top >= info.getUnclippedBoundsInRoot().bottom - 0.5.dp)
        assertEquals(listOf("a"), requests.routines)
    }

    @Test
    fun cardShowsCountsMusclesLastDoneAndFourThumbnails() = runComposeUiTest {
        show()
        assertEquals(listOf("Treino A", "5 exercícios · 17 séries", "Peito, Tríceps, Ombros", "Última vez: há 3 dias"), textsOf(TrainTags.info("a")))
        // Four thumbnails of the five exercises, 56 dp each.
        val thumbs = onAllNodes(hasTestTag(TrainTags.THUMB) and hasAnyAncestor(hasTestTag(TrainTags.card("a"))), useUnmergedTree = true)
        thumbs.assertCountEquals(4)
        for (i in 0 until 4) thumbs[i].assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        // Two exercises, two thumbnails; none, no strip. Never done.
        onAllNodes(hasTestTag(TrainTags.THUMB) and hasAnyAncestor(hasTestTag(TrainTags.card("b"))), useUnmergedTree = true).assertCountEquals(2)
        onAllNodes(hasTestTag(TrainTags.THUMB) and hasAnyAncestor(hasTestTag(TrainTags.card("c"))), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag(TrainTags.strip("c")).assertDoesNotExist()
        assertEquals(listOf("Treino B", "2 exercícios · 6 séries", "Costas", "Nunca feita"), textsOf(TrainTags.info("b")))
        assertEquals(listOf("Treino C", "0 exercícios · 0 séries", "Nunca feita"), textsOf(TrainTags.info("c")))
    }

    @Test
    fun lastDoneWordsTheDays() = runComposeUiTest {
        val today = a.copy(lastFinishedAt = daysBefore(0))
        val yesterday = b.copy(lastFinishedAt = daysBefore(1))
        val old = c.copy(lastFinishedAt = daysBefore(31))
        show(state = stateOf(routines = listOf(today, yesterday, old)))
        assertTrue("Última vez: hoje" in textsOf(TrainTags.info("a")))
        assertTrue("Última vez: ontem" in textsOf(TrainTags.info("b")))
        // A date after 30 days: day and month, in the screen's language (31 days before 30 October is 29 Sep, "29 set").
        assertTrue(textsOf(TrainTags.info("c")).any { it.startsWith("Última vez: ") && it.endsWith(" set") }, "${textsOf(TrainTags.info("c"))}")
    }

    // --- Starting ----------------------------------------------------------------------------

    @Test
    fun startStartsAndOpensTheWorkout() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        start("a").performClick()
        waitForIdle()
        assertEquals(listOf("startWorkout:a"), fake.calls)
        assertEquals(1, requests.workout)
    }

    @Test
    fun startWhileAnotherWorkoutIsActiveOpensTheDialog() = runComposeUiTest {
        val fake = Fake(stateOf(active = activeFrom("b", "Treino B")))
        val requests = show(state = fake.state, fake = fake)
        start("a").performClick()
        waitForIdle()
        onNodeWithText("Já existe um treino em andamento").assertIsDisplayed()
        onNodeWithText("Termine ou descarte “Treino B” antes de começar outro.").assertIsDisplayed()
        assertEquals(emptyList(), fake.calls)
        assertEquals(0, requests.workout)
        // "Cancelar" leaves everything as it was.
        inDialog("Cancelar").performClick()
        waitForIdle()
        onNodeWithText("Já existe um treino em andamento").assertDoesNotExist()
        // "Retomar treino" opens the workout that is there and starts nothing.
        start("a").performClick()
        waitForIdle()
        inDialog("Retomar treino").performClick()
        waitForIdle()
        assertEquals(1, requests.workout)
        assertEquals(emptyList(), fake.calls)
        onNodeWithText("Já existe um treino em andamento").assertDoesNotExist()
    }

    @Test
    fun sourceRoutineCardResumes() = runComposeUiTest {
        val fake = Fake(stateOf(active = activeFrom("b", "Treino B")))
        val requests = show(state = fake.state, fake = fake)
        start("b").assertTextEquals("Retomar treino").performClick()
        waitForIdle()
        assertEquals(1, requests.workout)
        assertEquals(emptyList(), fake.calls)
        start("a").assertTextEquals("Começar")
    }

    @Test
    fun emptyWorkoutStartsAnEmptyWorkout() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.EMPTY_WORKOUT))
        onNodeWithTag(TrainTags.EMPTY_WORKOUT).assertTextEquals("Treino vazio").performClick()
        waitForIdle()
        assertEquals(listOf("startWorkout:null"), fake.calls)
        assertEquals(1, requests.workout)
    }

    // --- The menu ----------------------------------------------------------------------------

    @Test
    fun menuRenameDuplicateMoveDelete() = runComposeUiTest {
        val fake = Fake(stateOf())
        // The window's own height: a toast sits at the bottom of the screen.
        val requests = show(state = fake.state, fake = fake, tall = false)
        // The menu has every item as a button of its own, "Editar" first and "Excluir" last.
        openMenu("a")
        for (tag in TrainTags.MENU_ITEMS) onNodeWithTag(tag).assertHasClickAction()
        // Edit opens the editor.
        pick(TrainTags.MENU_EDIT)
        assertEquals(listOf("a"), requests.routines)
        onNodeWithTag(TrainTags.MENU_EDIT).assertDoesNotExist()
        // Rename: the sheet starts with the name.
        openMenu("a")
        pick(TrainTags.MENU_RENAME)
        onNodeWithText("Renomear").assertIsDisplayed()
        assertEquals("Treino A", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Costas e bíceps")
        pick(RenameSheetTags.SAVE)
        assertEquals(listOf("renameRoutine:a,Costas e bíceps"), fake.calls)
        // Duplicate: the copy's name carries "(cópia)" and its id is the screen's.
        fake.echo = false
        openMenu("b")
        pick(TrainTags.MENU_DUPLICATE)
        val call = fake.calls.last()
        assertTrue(call.startsWith("duplicateRoutine:b,Treino B (cópia),"), call)
        val copyId = call.substringAfterLast(",")
        // The toast comes on the echo, never on the tap, with "Abrir".
        toastWith("Rotina duplicada").assertDoesNotExist()
        fake.deliver()
        waitForIdle()
        toastWith("Rotina duplicada").assertIsDisplayed()
        onNode(hasText("Abrir") and hasAnyAncestor(hasTestTag("toast-action")), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("toast-action", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(listOf("a", copyId), requests.routines)
        // Move to folder: the folders as buttons, the current one marked.
        openMenu("c")
        pick(TrainTags.MENU_MOVE)
        onNodeWithText("Mover para pasta").assertIsDisplayed()
        onAllNodes(hasContentDescription("Pasta atual"), useUnmergedTree = true).assertCountEquals(1)
        onNode(hasContentDescription("Pasta atual") and hasAnyAncestor(hasTestTag(TrainTags.moveTo("default"))), useUnmergedTree = true).assertExists()
        pick(TrainTags.moveTo("hyper"))
        assertEquals("moveRoutineToFolder:c,hyper", fake.calls.last())
        onNodeWithTag(TrainTags.moveTo("hyper")).assertDoesNotExist()
        // Delete asks first.
        openMenu("b")
        pick(TrainTags.MENU_DELETE)
        inDialog("Excluir Treino B?").assertIsDisplayed()
        assertTrue(fake.calls.none { it.startsWith("deleteRoutine") })
        inDialog("Excluir").performClick()
        waitForIdle()
        assertEquals("deleteRoutine:b", fake.calls.last())
    }

    @Test
    fun deleteDialogSaysHistoryStays() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        openMenu("a")
        pick(TrainTags.MENU_DELETE)
        inDialog("Excluir Treino A?").assertIsDisplayed()
        inDialog("Os treinos já feitos com ela continuam no histórico.").assertIsDisplayed()
        inDialog("Cancelar").performClick()
        waitForIdle()
        onNodeWithText("Excluir Treino A?").assertDoesNotExist()
        assertTrue(fake.calls.none { it.startsWith("deleteRoutine") })
    }

    @Test
    fun moveActionsReorderInsideTheFolder() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        // The folder's routines are a, b, c; "legs" is in another folder and never in the write.
        openMenu("a")
        onNodeWithTag(TrainTags.MENU_UP).assertIsNotEnabled()
        onNodeWithTag(TrainTags.MENU_TOP).assertIsNotEnabled()
        pick(TrainTags.MENU_DOWN)
        assertEquals("reorderRoutines:default,b|a|c", fake.calls.last())
        assertEquals(listOf("b", "a", "c"), cardIds())
        openMenu("c")
        onNodeWithTag(TrainTags.MENU_DOWN).assertIsNotEnabled()
        onNodeWithTag(TrainTags.MENU_BOTTOM).assertIsNotEnabled()
        pick(TrainTags.MENU_TOP)
        assertEquals("reorderRoutines:default,c|b|a", fake.calls.last())
        openMenu("c")
        pick(TrainTags.MENU_BOTTOM)
        assertEquals("reorderRoutines:default,b|a|c", fake.calls.last())
        openMenu("c")
        pick(TrainTags.MENU_UP)
        assertEquals("reorderRoutines:default,b|c|a", fake.calls.last())
    }

    @Test
    fun dragReordersInsideTheFolder() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        val handle = onNodeWithTag(TrainTags.handle("a"), useUnmergedTree = true)
        var longPress = 0L
        handle.performTouchInput {
            longPress = viewConfiguration.longPressTimeoutMillis
            down(center)
        }
        mainClock.advanceTimeBy(longPress + 100)
        waitForIdle()
        val step = with(density) { 330.dp.toPx() } / 20
        repeat(20) {
            onNodeWithTag(TrainScreenTag).performTouchInput { moveBy(Offset(0f, step)) }
            waitForIdle()
        }
        // Nothing is written until the finger lifts.
        assertEquals(emptyList(), fake.calls)
        onNodeWithTag(TrainScreenTag).performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf("reorderRoutines:default,b|a|c"), fake.calls)
        assertEquals(listOf("b", "a", "c"), cardIds())
    }

    // --- Creating ----------------------------------------------------------------------------

    private fun ComposeUiTest.fieldText(): String =
        onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.newRoutine() {
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.NEW_ROUTINE))
        pick(TrainTags.NEW_ROUTINE)
    }

    @Test
    fun newRoutineCreatesInTheSelectedFolderAndOpensOnTheEcho() = runComposeUiTest {
        val fake = Fake(stateOf(selected = "hyper")).apply { echo = false }
        val requests = show(state = fake.state, fake = fake)
        newRoutine()
        // The default name is there, selected, with the keyboard open (the field focused).
        assertEquals("Nova rotina", fieldText())
        assertEquals(TextRange(0, "Nova rotina".length), onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        onNodeWithTag(RenameSheetTags.FIELD).assertIsFocused()
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Empurrar")
        onNodeWithText("Criar").performClick()
        waitForIdle()
        val call = fake.calls.single()
        assertTrue(call.startsWith("createRoutine:Empurrar,hyper,"), call)
        val id = call.substringAfterLast(",")
        // The editor opens on the echo, not on the tap.
        assertEquals(emptyList(), requests.routines)
        fake.deliver()
        waitForIdle()
        assertEquals(listOf(id), requests.routines)
    }

    /** The name sheet (new routine, rename, folders) stands on the keyboard: its field and button are never under it. */
    @Test
    fun nameSheetStandsAboveTheKeyboard() = runComposeUiTest {
        val keyboard = 300.dp
        withScreen {
            RenameSheet("Nova rotina", onRename = {}, onDismiss = {}, selectAll = true, submitUnchanged = true, bottomInsets = WindowInsets(bottom = keyboard))
        }
        waitForIdle()
        val sheet = onNode(hasTestTag("sheet"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val save = onNodeWithTag(RenameSheetTags.SAVE).getUnclippedBoundsInRoot()
        val field = onNodeWithTag(RenameSheetTags.FIELD).getUnclippedBoundsInRoot()
        assertTrue(sheet.bottom - save.bottom >= keyboard, "the button ends ${sheet.bottom - save.bottom} above the sheet's bottom, under a $keyboard keyboard")
        assertTrue(field.bottom < save.top, "the field is above the button")
    }

    @Test
    fun newRoutineWithTheDefaultNameCreates() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        newRoutine()
        onNodeWithText("Criar").performClick()
        waitForIdle()
        val call = fake.calls.single()
        assertTrue(call.startsWith("createRoutine:Nova rotina,default,"), call)
        assertEquals(listOf(call.substringAfterLast(",")), requests.routines)
        onNodeWithTag(RenameSheetTags.FIELD).assertDoesNotExist()
    }

    @Test
    fun newRoutineErrorDropsTheWait() {
        // A failure about another id changes nothing: the editor still opens on the echo.
        runComposeUiTest {
            val fake = Fake(stateOf()).apply { echo = false }
            val requests = show(state = fake.state, fake = fake)
            newRoutine()
            onNodeWithText("Criar").performClick()
            waitForIdle()
            val id = fake.calls.single().substringAfterLast(",")
            fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = "other", subjectFailures = mapOf("other" to 1))
            waitForIdle()
            assertEquals(emptyList(), requests.routines)
            fake.deliver()
            waitForIdle()
            assertEquals(listOf(id), requests.routines)
        }
        LocalAppLocale.restore()
        // The failure of this very command releases the wait: the error shows, nothing opens, even on a late echo,
        // and a new routine can be asked for again.
        runComposeUiTest {
            val fake = Fake(stateOf()).apply { echo = false }
            val requests = show(state = fake.state, fake = fake, tall = false)
            newRoutine()
            onNodeWithText("Criar").performClick()
            waitForIdle()
            val id = fake.calls.single().substringAfterLast(",")
            fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = id, subjectFailures = mapOf(id to 1))
            waitForIdle()
            toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
            assertTrue("clearError:" in fake.calls)
            fake.deliver()
            waitForIdle()
            assertEquals(emptyList(), requests.routines)
            newRoutine()
            onNodeWithTag(RenameSheetTags.FIELD).assertIsDisplayed()
            onNodeWithText("Criar").performClick()
            waitForIdle()
            assertEquals(2, fake.calls.count { it.startsWith("createRoutine:") })
        }
    }

    @Test
    fun newRoutineWaitEndsAtTheCap() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { echo = false }
        val requests = show(state = fake.state, fake = fake, tall = false)
        newRoutine()
        onNodeWithText("Criar").performClick()
        waitForIdle()
        // The command was lost: after the cap the screen is free again, with the error, and opens nothing.
        mainClock.advanceTimeBy(MoosclesTiming().saveEchoWait + 1_000)
        waitForIdle()
        toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
        fake.deliver()
        waitForIdle()
        assertEquals(emptyList(), requests.routines)
        newRoutine()
        onNodeWithTag(RenameSheetTags.FIELD).assertIsDisplayed()
    }

    @Test
    fun duplicateFailureReleasesTheWait() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { echo = false }
        show(state = fake.state, fake = fake, tall = false)
        openMenu("b")
        pick(TrainTags.MENU_DUPLICATE)
        val copyId = fake.calls.last().substringAfterLast(",")
        fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = copyId, subjectFailures = mapOf(copyId to 1))
        waitForIdle()
        toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
        // A late echo shows no "duplicated" toast, and Duplicate is there again.
        fake.deliver()
        waitForIdle()
        toastWith("Rotina duplicada").assertDoesNotExist()
        openMenu("a")
        onNodeWithTag(TrainTags.MENU_DUPLICATE).assertIsEnabled()
    }

    @Test
    fun creationAndCopyAreBusyWhileAnEchoIsPending() {
        runComposeUiTest {
            val fake = Fake(stateOf()).apply { echo = false }
            show(state = fake.state, fake = fake)
            openMenu("b")
            pick(TrainTags.MENU_DUPLICATE)
            assertEquals(1, fake.calls.count { it.startsWith("duplicateRoutine:") })
            // Another copy cannot start, and "New routine" does not open its sheet.
            openMenu("a")
            onNodeWithTag(TrainTags.MENU_DUPLICATE).assertIsNotEnabled()
            pick(TrainTags.MENU_EDIT)
            onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.NEW_ROUTINE))
            pick(TrainTags.NEW_ROUTINE)
            onNodeWithTag(RenameSheetTags.FIELD).assertDoesNotExist()
            // The copy arrives: both are free again.
            fake.deliver()
            waitForIdle()
            pick(TrainTags.NEW_ROUTINE)
            onNodeWithTag(RenameSheetTags.FIELD).assertIsDisplayed()
        }
        LocalAppLocale.restore()
        // An empty folder: its empty state has no button while a creation is on its way; "New routine" shows it.
        runComposeUiTest {
            val fake = Fake(stateOf(selected = "empty")).apply { echo = false }
            show(state = fake.state, fake = fake)
            onNode(hasText("Nova rotina") and hasAnyAncestor(hasTestTag(TrainTags.EMPTY)), useUnmergedTree = true).performClick()
            waitForIdle()
            onNodeWithText("Criar").performClick()
            waitForIdle()
            onNode(hasText("Nova rotina") and hasAnyAncestor(hasTestTag(TrainTags.EMPTY)), useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag(TrainTags.NEW_ROUTINE).assertExists()
            onNodeWithTag(TrainTags.NEW_ROUTINE).performClick()
            waitForIdle()
            onNodeWithTag(RenameSheetTags.FIELD).assertDoesNotExist()
        }
    }

    @Test
    fun menuMoveHoldsTheOrderAndAnnounces() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { applyReorder = false }
        show(state = fake.state, fake = fake)
        openMenu("a")
        mainClock.autoAdvance = false
        onNodeWithTag(TrainTags.MENU_DOWN).performClick()
        mainClock.advanceTimeBy(200)
        assertEquals("reorderRoutines:default,b|a|c", fake.calls.last())
        // The write has not come back, and the list already shows the new order, as after a drag.
        assertEquals(listOf("b", "a", "c"), cardIds())
        onNodeWithTag(ReorderAnnouncerTag).assertContentDescriptionEquals("Treino A agora na posição 2 de 3")
    }

    @Test
    fun inProgressDialogSurvivesRecreationAndClosesWithItsWorkout() = runComposeUiTest {
        val restoration = screenRestoration()
        val fake = Fake(stateOf(active = activeFrom("b", "Treino B")))
        show(state = fake.state, fake = fake, locale = null, restoration = restoration)
        start("a").performClick()
        waitForIdle()
        onNodeWithTag("dialog").assertExists()
        restoration.emulateSaveAndRestore()
        waitForIdle()
        onNodeWithTag("dialog").assertExists()
        // Another workout is under way now: the dialog was about the old one.
        fake.state.value = fake.state.value.copy(active = activeFrom("a").copy(id = "other-session"))
        waitForIdle()
        onNodeWithTag("dialog").assertDoesNotExist()
    }

    @Test
    fun routineMenuScrollsAtTwoXInAShortWindow() = runComposeUiTest {
        val fake = Fake(stateOf())
        // The menu is a sheet, a window of its own: a screen 1.5 times as dense is what makes it short enough to overflow.
        show(state = fake.state, fake = fake, fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 640.dp, density = density.density * 1.5f)
        openMenu("a")
        onNodeWithTag(TrainTags.MENU_LIST).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        // A real overflow: the list scrolls, its last item is out of sight until it does, and in sight after.
        assertTrue(scrollRangeOf(TrainTags.MENU_LIST) > 0f, "the card menu does not overflow")
        assertTrue(!isShown(TrainTags.MENU_DELETE), "the last item of the card menu is shown before scrolling")
        onNodeWithTag(TrainTags.MENU_LIST).performScrollToNode(hasTestTag(TrainTags.MENU_DELETE))
        waitForIdle()
        assertTrue(isShown(TrainTags.MENU_DELETE), "the last item of the card menu is not shown after scrolling")
        onNodeWithTag(TrainTags.MENU_LIST).performScrollToNode(hasTestTag(TrainTags.MENU_EDIT))
        waitForIdle()
        assertClickablesLabeledAndSized("Train menu at 2x")
        onNodeWithTag(TrainTags.MENU_LIST).performScrollToNode(hasTestTag(TrainTags.MENU_BOTTOM))
        pick(TrainTags.MENU_BOTTOM)
        assertEquals("reorderRoutines:default,b|c|a", fake.calls.last())
        openMenu("b")
        onNodeWithTag(TrainTags.MENU_LIST).performScrollToNode(hasTestTag(TrainTags.MENU_DELETE))
        pick(TrainTags.MENU_DELETE)
        inDialog("Excluir Treino B?").assertIsDisplayed()
    }

    @Test
    fun moveSheetScrollsWithManyFolders() = runComposeUiTest {
        val many = folders + List(12) { RoutineFolderUi("extra$it", "Pasta extra $it") }
        val fake = Fake(stateOf().apply { value = value.copy(routineFolders = many) })
        show(state = fake.state, fake = fake, fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 640.dp, density = density.density * 1.5f)
        openMenu("a")
        onNodeWithTag(TrainTags.MENU_LIST).performScrollToNode(hasTestTag(TrainTags.MENU_MOVE))
        pick(TrainTags.MENU_MOVE)
        onNodeWithTag(TrainTags.MOVE_LIST).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        // A real overflow: the last folder is out of sight until the list scrolls, and in sight after.
        assertTrue(scrollRangeOf(TrainTags.MOVE_LIST) > 0f, "the move sheet does not overflow")
        assertTrue(!isShown(TrainTags.moveTo("extra11")), "the last folder is shown before scrolling")
        onNodeWithTag(TrainTags.MOVE_LIST).performScrollToNode(hasTestTag(TrainTags.moveTo("extra11")))
        waitForIdle()
        assertTrue(isShown(TrainTags.moveTo("extra11")), "the last folder is not shown after scrolling")
        pick(TrainTags.moveTo("extra11"))
        assertEquals("moveRoutineToFolder:a,extra11", fake.calls.last())
    }

    // --- States ------------------------------------------------------------------------------

    @Test
    fun emptyFolderOffersNewRoutine() = runComposeUiTest {
        val fake = Fake(stateOf(selected = "empty"))
        show(state = fake.state, fake = fake)
        onNodeWithText("Nenhuma rotina nesta pasta").assertIsDisplayed()
        onNodeWithTag(TrainTags.EMPTY).assertIsDisplayed()
        onNode(hasText("Nova rotina") and hasAnyAncestor(hasTestTag(TrainTags.EMPTY)), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(RenameSheetTags.FIELD).assertIsDisplayed()
        onNodeWithText("Criar").performClick()
        waitForIdle()
        assertTrue(fake.calls.single().startsWith("createRoutine:Nova rotina,empty,"))
    }

    @Test
    fun loadingShowsSkeletonCards() = runComposeUiTest {
        val state = stateOf(loading = true)
        show(state = state)
        assertTrue(onAllNodesWithTag(TrainTags.SKELETON).fetchSemanticsNodes().size >= 2)
        onNodeWithTag(TrainTags.start("a")).assertDoesNotExist()
        onNodeWithTag(TrainTags.NEW_ROUTINE).assertDoesNotExist()
        state.value = state.value.copy(loading = false)
        waitForIdle()
        onAllNodesWithTag(TrainTags.SKELETON).assertCountEquals(0)
        onNodeWithTag(TrainTags.start("a")).assertIsDisplayed()
    }

    @Test
    fun topHasTitleAndSettings() = runComposeUiTest {
        val requests = show()
        onNodeWithText("Treinar").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithTag(TrainTags.SETTINGS).assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Configurações")))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, requests.settings)
    }

    @Test
    fun startIsFiftySixAndTextsWholeAtTwoX() = runComposeUiTest {
        show(fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp)
        start("a").assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(56.dp)
        onNodeWithTag(TrainTags.menu("a")).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        onNodeWithTag(TrainTags.handle("a"), useUnmergedTree = true).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertClickablesLabeledAndSized("Train at 2x")
        assertButtonTextsWhole("Train at 2x")
    }

    @Test
    fun errorShowsTheLocalizedToast() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake, tall = false)
        fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED)
        waitForIdle()
        toastWith("Não foi possível salvar a última mudança").assertIsDisplayed()
        assertEquals(listOf("clearError:"), fake.calls)
        assertEquals(null, fake.state.value.errorCode)
    }

    // --- Ported from the old routine screen (RoutineFoldersHostTest, RoutineResumeHostTest) ---

    private val first = RoutineUi("first", "Upper day")
    private val second = RoutineUi("second", "Upper day")

    @Test
    fun switchingFoldersFiltersCardsWithoutChangingActiveRoutineAssociation() = runComposeUiTest {
        val upper = RoutineUi("upper", "Upper", folderId = "default")
        val full = RoutineUi("full", "Full", folderId = "hyper")
        val active = activeFrom("upper")
        val fake = Fake(stateOf(routines = listOf(upper, full), active = active))
        show(state = fake.state, fake = fake)
        start("upper").assertTextEquals("Retomar treino")
        onNodeWithTag(TrainTags.start("full")).assertDoesNotExist()
        pick(TrainTags.folder("hyper"))
        onNodeWithTag(TrainTags.folder("hyper")).assertIsSelected()
        start("full").assertTextEquals("Começar")
        onNodeWithTag(TrainTags.start("upper")).assertDoesNotExist()
        pick(TrainTags.folder("default"))
        start("upper").assertTextEquals("Retomar treino")
        assertEquals(active, fake.state.value.active)
        assertEquals(listOf("selectRoutineFolder:hyper", "selectRoutineFolder:default"), fake.calls)
    }

    @Test
    fun movingAnActiveRoutinesCardUsesItsIdAndKeepsTheSession() = runComposeUiTest {
        val upper = RoutineUi("upper", "Upper", folderId = "default")
        val active = activeFrom("upper")
        val fake = Fake(stateOf(routines = listOf(upper), active = active))
        show(state = fake.state, fake = fake)
        openMenu("upper")
        pick(TrainTags.MENU_MOVE)
        pick(TrainTags.moveTo("hyper"))
        onNodeWithTag(TrainTags.start("upper")).assertDoesNotExist()
        pick(TrainTags.folder("hyper"))
        start("upper").assertTextEquals("Retomar treino")
        assertEquals(active, fake.state.value.active)
        assertEquals("hyper", fake.state.value.routines.first { it.id == "upper" }.folderId)
        assertEquals(listOf("moveRoutineToFolder:upper,hyper", "selectRoutineFolder:hyper"), fake.calls)
    }

    private fun twinsState(active: WorkoutUi? = activeFrom("second", "Upper day")) = stateOf(routines = listOf(first, second), active = active)

    @Test
    fun identicalNamesUseOnlyTheMatchingPersistentIdAndResumeWithoutStarting() = runComposeUiTest {
        val fake = Fake(twinsState())
        val requests = show(state = fake.state, fake = fake)
        start("first").assertTextEquals("Começar")
        start("second").assertTextEquals("Retomar treino").performClick()
        waitForIdle()
        // Only the matching card resumes: the other keeps "Começar".
        assertEquals(1, onAllNodes(hasText("Retomar treino") and isStart).fetchSemanticsNodes().size)
        assertEquals(1, requests.workout)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun renamingAndReorderingDoNotMoveTheResumeAssociation() = runComposeUiTest {
        val fake = Fake(twinsState())
        show(state = fake.state, fake = fake)
        fake.state.value = fake.state.value.copy(routines = listOf(second.copy(name = "Renamed routine"), first.copy(name = "Renamed routine")))
        waitForIdle()
        start("second").assertTextEquals("Retomar treino")
        start("first").assertTextEquals("Começar")
        assertEquals(listOf("second", "first"), cardIds())
    }

    @Test
    fun startingAnotherRoutineProtectsTheExistingSessionAndOffersExplicitResume() = runComposeUiTest {
        val fake = Fake(twinsState())
        val requests = show(state = fake.state, fake = fake)
        start("first").assertTextEquals("Começar").performClick()
        waitForIdle()
        onNodeWithText("Já existe um treino em andamento").assertIsDisplayed()
        assertEquals(0, requests.workout)
        assertTrue(fake.calls.isEmpty())
        inDialog("Cancelar").performClick()
        waitForIdle()
        start("first").performClick()
        waitForIdle()
        inDialog("Retomar treino").performClick()
        waitForIdle()
        assertEquals(1, requests.workout)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun freeOrLegacyUnassociatedSessionNeverMarksACardAsResumable() = runComposeUiTest {
        val fake = Fake(twinsState(active = activeFrom(null, "Free session")))
        val requests = show(state = fake.state, fake = fake)
        start("first").assertTextEquals("Começar")
        start("second").assertTextEquals("Começar")
        // No card resumes it; the empty-workout button does, without starting anything.
        onAllNodesWithText("Retomar treino").assertCountEquals(1)
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.EMPTY_WORKOUT))
        onNodeWithTag(TrainTags.EMPTY_WORKOUT).assertTextEquals("Retomar treino").performClick()
        waitForIdle()
        assertEquals(1, requests.workout)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun finishingAndDiscardingRefreshBothCardsImmediately() = runComposeUiTest {
        val fake = Fake(twinsState())
        show(state = fake.state, fake = fake)
        start("second").assertTextEquals("Retomar treino")
        fake.state.value = fake.state.value.copy(active = null)
        waitForIdle()
        start("second").assertTextEquals("Começar")
        start("first").assertTextEquals("Começar")
        fake.state.value = fake.state.value.copy(active = activeFrom("first", "Upper day"))
        waitForIdle()
        start("first").assertTextEquals("Retomar treino")
        start("second").assertTextEquals("Começar")
        fake.state.value = fake.state.value.copy(active = null)
        waitForIdle()
        start("first").assertTextEquals("Começar")
        start("second").assertTextEquals("Começar")
    }

    @Test
    fun noActiveSessionStartsTheSelectedRoutine() = runComposeUiTest {
        val fake = Fake(twinsState(active = null))
        val requests = show(state = fake.state, fake = fake)
        start("first").performClick()
        waitForIdle()
        assertEquals(listOf("startWorkout:first"), fake.calls)
        assertEquals(1, requests.workout)
    }
}
