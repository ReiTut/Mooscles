package com.mooscles.screens.routines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.ReorderAnnouncerTag
import com.mooscles.screens.INJECTS_DRAG
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.RenameSheetTags
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The folder manager sheet (Task 12), opened from the Train tab's "Folders" chip. */
@OptIn(ExperimentalTestApi::class)
class FolderManagerSheetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val folders = listOf(
        RoutineFolderUi("default", "My routines"), RoutineFolderUi("hyper", "Hipertrofia"), RoutineFolderUi("empty", "Força"),
    )
    private val routines = listOf(
        RoutineUi("a", "Treino A", folderId = "default"),
        RoutineUi("b", "Treino B", folderId = "default"),
        RoutineUi("c", "Treino C", folderId = "default"),
        RoutineUi("legs", "Pernas", folderId = "hyper"),
    )

    private fun stateOf(
        folders: List<RoutineFolderUi> = this.folders,
        routines: List<RoutineUi> = this.routines,
        selected: String = "default",
    ) = mutableStateOf(
        UiState(loading = false, routines = routines, routineFolders = folders, preferences = PreferencesUi(selectedRoutineFolderId = selected)),
    )

    /** The view model of the folders: it records the calls and applies them as the repository does. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        /** False: a reorder is recorded but the state keeps its order. */
        var applyReorder = true

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null, errorSubject = null)
        }

        override fun reorderRoutineFolders(ids: List<String>) {
            recording.reorderRoutineFolders(ids)
            if (!applyReorder) return
            val all = state.value.routineFolders
            state.value = state.value.copy(routineFolders = ids.mapNotNull { id -> all.firstOrNull { it.id == id } })
        }

        override fun createRoutineFolder(name: String) {
            recording.createRoutineFolder(name)
            state.value = state.value.copy(routineFolders = state.value.routineFolders + RoutineFolderUi("new${state.value.routineFolders.size}", name))
        }

        override fun renameRoutineFolder(id: String, name: String) {
            recording.renameRoutineFolder(id, name)
            state.value = state.value.copy(routineFolders = state.value.routineFolders.map { if (it.id == id) it.copy(name = name) else it })
        }

        override fun selectRoutineFolder(id: String) {
            recording.selectRoutineFolder(id)
            state.value = state.value.copy(preferences = state.value.preferences.copy(selectedRoutineFolderId = id))
        }

        /** Task 3's repository: the routines go to the default folder, and a deleted selection falls back to it. */
        override fun deleteRoutineFolder(id: String) {
            recording.deleteRoutineFolder(id)
            val now = state.value
            state.value = now.copy(
                routineFolders = now.routineFolders.filter { it.id != id },
                routines = now.routines.map { if (it.folderId == id) it.copy(folderId = "default") else it },
                preferences = if (now.preferences.selectedRoutineFolderId == id) now.preferences.copy(selectedRoutineFolderId = "default") else now.preferences,
            )
        }
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = stateOf(),
        fake: Fake = Fake(state),
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        width: androidx.compose.ui.unit.Dp? = null,
        height: androidx.compose.ui.unit.Dp? = null,
        restoration: ScreenRestoration? = null,
    ) {
        withScreen(locale = locale, fontScale = fontScale, restoration = restoration) {
            val sized = when {
                width != null && height != null -> Modifier.requiredSize(width, height)
                else -> Modifier.fillMaxSize()
            }
            Box(sized) {
                TrainRoute(state.value, fake, onOpenRoutine = {}, onOpenWorkout = {}, onOpenSettings = {})
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.open() {
        // In a short window the chip can be past the row's edge.
        onNodeWithTag(TrainTags.FOLDER_ROW).performScrollToNode(hasTestTag(TrainTags.MANAGE_FOLDERS))
        waitForIdle()
        onNodeWithTag(TrainTags.MANAGE_FOLDERS).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pick(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.inSheet(text: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag("sheet")), useUnmergedTree = true)

    private fun ComposeUiTest.inDialog(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag("dialog")), useUnmergedTree = true)

    private fun ComposeUiTest.rowIds(): List<String> =
        onAllNodes(SemanticsMatcher("is a folder row") { it.config.getOrNull(SemanticsProperties.TestTag)?.let { t -> t.startsWith("folder-") && t.endsWith("-row") } == true })
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag].removePrefix("folder-").removeSuffix("-row") }

    private fun ComposeUiTest.fieldText(): String =
        onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun rowsInSavedOrderWithCountsAndHandles() = runComposeUiTest {
        show()
        open()
        assertEquals(listOf("default", "hyper", "empty"), rowIds())
        onNodeWithTag(FolderTags.count("default")).assertTextEquals("3 rotinas")
        onNodeWithTag(FolderTags.count("hyper")).assertTextEquals("1 rotina")
        onNodeWithTag(FolderTags.count("empty")).assertTextEquals("0 rotinas")
        // The default folder reads in the screen's language; the others as written.
        onNode(hasText("Minhas rotinas") and hasAnyAncestor(hasTestTag(FolderTags.row("default"))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Hipertrofia") and hasAnyAncestor(hasTestTag(FolderTags.row("hyper"))), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(FolderTags.handle("hyper"), useUnmergedTree = true).assertContentDescriptionEquals("Reordenar Hipertrofia")
        onNodeWithTag(FolderTags.menu("hyper")).assertContentDescriptionEquals("Opções de Hipertrofia")
        // "New folder" ends the list.
        assertTrue(onNodeWithTag(FolderTags.NEW).getUnclippedBoundsInRoot().top >= onNodeWithTag(FolderTags.row("empty")).getUnclippedBoundsInRoot().bottom - 0.5.dp)
        inSheet("Pastas").assertIsDisplayed()
    }

    @Test
    fun defaultHasNoDelete() = runComposeUiTest {
        show()
        open()
        pick(FolderTags.menu("default"))
        onNodeWithTag(FolderTags.MENU_RENAME).assertIsDisplayed()
        onNodeWithTag(FolderTags.MENU_DELETE).assertDoesNotExist()
    }

    @Test
    fun deleteDialogNamesWhereTheRoutinesGo() = runComposeUiTest {
        // Three routines in the default folder are not what moves: the folder deleted is the one that has them.
        val many = routines.map { if (it.id == "legs" || it.id == "b" || it.id == "c") it.copy(folderId = "hyper") else it }
        val fake = Fake(stateOf(routines = many + RoutineUi("d", "Treino D", folderId = "empty")))
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_DELETE)
        inDialog("Excluir a pasta Hipertrofia?").assertIsDisplayed()
        inDialog("As 3 rotinas dela vão para Minhas rotinas.").assertIsDisplayed()
        inDialog("Cancelar").performClick()
        waitForIdle()
        pick(FolderTags.menu("empty"))
        pick(FolderTags.MENU_DELETE)
        inDialog("Excluir a pasta Força?").assertIsDisplayed()
        inDialog("A rotina dela vai para Minhas rotinas.").assertIsDisplayed()
        inDialog("Cancelar").performClick()
        waitForIdle()
        assertTrue(fake.calls.none { it.startsWith("deleteRoutineFolder") })
    }

    @Test
    fun deleteDialogWithNoRoutinesHasOnlyTheTitleAndButtons() {
        for ((locale, title, cancel, delete) in listOf(
            listOf("en", "Delete the Força folder?", "Cancel", "Delete"),
            listOf("pt-BR", "Excluir a pasta Força?", "Cancelar", "Excluir"),
        )) {
            runComposeUiTest {
                show(locale = locale)
                open()
                pick(FolderTags.menu("empty"))
                pick(FolderTags.MENU_DELETE)
                val texts = onAllNodes(hasAnyAncestor(hasTestTag("dialog")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes().map { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text } }
                assertEquals(listOf(title, cancel, delete), texts, locale)
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun menuMovesReorderAndAnnounce() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { applyReorder = false }
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.menu("default"))
        onNodeWithTag(FolderTags.MENU_UP).assertIsNotEnabled()
        onNodeWithTag(FolderTags.MENU_TOP).assertIsNotEnabled()
        pick(FolderTags.MENU_BOTTOM)
        assertEquals(listOf("reorderRoutineFolders:hyper|empty|default"), fake.calls)
        // Held: the list shows the new order though the write has not come back.
        assertEquals(listOf("hyper", "empty", "default"), rowIds())
        onNode(hasTestTag(ReorderAnnouncerTag) and hasAnyAncestor(hasTestTag("sheet")), useUnmergedTree = true)
            .assertContentDescriptionEquals("Minhas rotinas agora na posição 3 de 3")
        pick(FolderTags.menu("default"))
        onNodeWithTag(FolderTags.MENU_DOWN).assertIsNotEnabled()
        onNodeWithTag(FolderTags.MENU_BOTTOM).assertIsNotEnabled()
        pick(FolderTags.MENU_UP)
        assertEquals(2, fake.calls.size)
        assertEquals("reorderRoutineFolders:hyper|default|empty", fake.calls.last())
    }

    @Test
    fun errorWhileManagingShowsInTheSheet() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        fake.state.value = fake.state.value.copy(errorCode = com.mooscles.ui.UiError.SAVE_FAILED)
        waitForIdle()
        onNode(
            hasText("Não foi possível salvar a última mudança", substring = true) and hasAnyAncestor(hasTestTag("toast")) and hasAnyAncestor(hasTestTag("sheet")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        assertEquals(null, fake.state.value.errorCode)
    }

    @Test
    fun deleteDialogReadsInEnglish() = runComposeUiTest {
        show(locale = "en", state = stateOf(routines = routines.map { if (it.id == "legs") it else it.copy(folderId = "hyper") }))
        open()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_DELETE)
        inDialog("Delete the Hipertrofia folder?").assertIsDisplayed()
        inDialog("Its 4 routines move to My routines.").assertIsDisplayed()
    }

    @Test
    fun confirmDeleteCallsDeleteOnce() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_DELETE)
        inDialog("Excluir").performClick()
        waitForIdle()
        assertEquals(listOf("deleteRoutineFolder:hyper"), fake.calls)
        assertEquals(listOf("default", "empty"), rowIds())
        onNodeWithTag("dialog").assertDoesNotExist()
    }

    @Test
    fun deletingTheSelectedFolderFallsBackToTheDefault() = runComposeUiTest {
        val fake = Fake(stateOf(selected = "hyper"))
        show(state = fake.state, fake = fake)
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.start("legs")))
        open()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_DELETE)
        inDialog("Excluir").performClick()
        waitForIdle()
        // The sheet stays open; the tab behind it shows the default folder's routines, "legs" among them now.
        onNodeWithTag(FolderTags.LIST).assertIsDisplayed()
        assertEquals("default", fake.state.value.preferences.selectedRoutineFolderId)
        onNodeWithTag(TrainTags.folder("default"), useUnmergedTree = true).assertExists()
        onNodeWithTag(TrainScreenTag).performScrollToNode(hasTestTag(TrainTags.card("legs")))
        onNodeWithTag(TrainTags.card("a")).assertExists()
    }

    @Test
    fun renameIncludingTheDefault() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_RENAME)
        assertEquals("Hipertrofia", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Volume")
        onNodeWithText("Salvar").performClick()
        waitForIdle()
        assertEquals(listOf("renameRoutineFolder:hyper,Volume"), fake.calls)
        // The default folder starts with its translated name; the stored name is never what the field shows.
        pick(FolderTags.menu("default"))
        pick(FolderTags.MENU_RENAME)
        assertEquals("Minhas rotinas", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Principal")
        onNodeWithText("Salvar").performClick()
        waitForIdle()
        assertEquals("renameRoutineFolder:default,Principal", fake.calls.last())
        // Unchanged writes nothing.
        pick(FolderTags.menu("empty"))
        pick(FolderTags.MENU_RENAME)
        onNodeWithText("Salvar").performClick()
        waitForIdle()
        assertEquals(2, fake.calls.size)
    }

    @Test
    fun newFolderCreates() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.NEW)
        assertEquals("Nova pasta", fieldText())
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Braços")
        onNodeWithText("Criar").performClick()
        waitForIdle()
        assertEquals(listOf("createRoutineFolder:Braços"), fake.calls)
        assertEquals(listOf("default", "hyper", "empty", "new3"), rowIds())
    }

    @Test
    fun newFolderWithTheDefaultNameCreates() = runComposeUiTest {
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        pick(FolderTags.NEW)
        assertEquals(
            androidx.compose.ui.text.TextRange(0, "Nova pasta".length),
            onNodeWithTag(RenameSheetTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
        onNodeWithText("Criar").performClick()
        waitForIdle()
        assertEquals(listOf("createRoutineFolder:Nova pasta"), fake.calls)
    }

    @Test
    fun dragReordersTheFolders() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val fake = Fake(stateOf())
        show(state = fake.state, fake = fake)
        open()
        var longPress = 0L
        onNodeWithTag(FolderTags.handle("default"), useUnmergedTree = true).performTouchInput {
            longPress = viewConfiguration.longPressTimeoutMillis
            down(center)
        }
        mainClock.advanceTimeBy(longPress + 100)
        waitForIdle()
        val rowHeight = onNodeWithTag(FolderTags.row("default")).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val step = with(density) { (rowHeight * 1.2f).toPx() } / 12
        repeat(12) {
            onNodeWithTag(FolderTags.LIST).performTouchInput { moveBy(Offset(0f, step)) }
            waitForIdle()
        }
        // Nothing is written until the finger lifts.
        assertEquals(emptyList(), fake.calls)
        onNodeWithTag(FolderTags.LIST).performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf("reorderRoutineFolders:hyper|default|empty"), fake.calls)
        assertEquals(listOf("hyper", "default", "empty"), rowIds())
    }

    @Test
    fun accessibilityActionsReorder() = runComposeUiTest {
        val fake = Fake(stateOf()).apply { applyReorder = false }
        show(state = fake.state, fake = fake)
        open()
        val actions = onNodeWithTag(FolderTags.handle("hyper"), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Mover para cima", "Mover para baixo", "Mover para o topo", "Mover para o fim"), actions.map { it.label })
        mainClock.autoAdvance = false
        runOnUiThread { actions.first { it.label == "Mover para baixo" }.action() }
        mainClock.advanceTimeBy(200)
        assertEquals("reorderRoutineFolders:default|empty|hyper", fake.calls.last())
        // Held: the list already shows the new order though the write has not come back.
        assertEquals(listOf("default", "empty", "hyper"), rowIds())
        onNode(hasTestTag(ReorderAnnouncerTag) and hasAnyAncestor(hasTestTag("sheet")), useUnmergedTree = true).assertContentDescriptionEquals("Hipertrofia agora na posição 3 de 3")
    }

    @Test
    fun sheetStateSurvivesRecreation() = runComposeUiTest {
        val restoration = screenRestoration()
        val fake = Fake(stateOf())
        // No forced language: the restoration tester loses the saved state when the override rebuilds the tree.
        show(state = fake.state, fake = fake, locale = null, restoration = restoration)
        open()
        pick(FolderTags.menu("hyper"))
        restoration.emulateSaveAndRestore()
        waitForIdle()
        // The sheet and the folder's menu are still open.
        waitUntil(timeoutMillis = 5_000L) { onAllNodesWithTag(FolderTags.MENU_RENAME).fetchSemanticsNodes().isNotEmpty() }
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(FolderTags.MENU_RENAME).assertIsDisplayed()
        pick(FolderTags.MENU_DELETE)
        restoration.emulateSaveAndRestore()
        waitForIdle()
        onNode(hasText("Hipertrofia", substring = true) and hasAnyAncestor(hasTestTag("dialog")), useUnmergedTree = true).assertExists()
        pressCancel()
        pick(FolderTags.menu("hyper"))
        pick(FolderTags.MENU_RENAME)
        onNodeWithTag(RenameSheetTags.FIELD).performTextReplacement("Vol")
        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals("Vol", fieldText())
        assertTrue(fake.calls.isEmpty())
    }

    private fun ComposeUiTest.pressCancel() {
        onNode(hasText("Cancel") or hasText("Cancelar"), useUnmergedTree = true).performClick()
        waitForIdle()
    }

    @Test
    fun manyFoldersScrollAtTwoX() = runComposeUiTest {
        val many = folders + List(30) { RoutineFolderUi("extra$it", "Pasta extra $it") }
        val fake = Fake(stateOf(folders = many))
        show(state = fake.state, fake = fake, fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 640.dp)
        open()
        onNodeWithTag(FolderTags.LIST).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        onNodeWithTag(FolderTags.row("extra29")).assertDoesNotExist()
        onNodeWithTag(FolderTags.LIST).performScrollToNode(hasTestTag(FolderTags.row("extra29")))
        waitForIdle()
        onNodeWithTag(FolderTags.row("extra29")).assertIsDisplayed()
        // The announcer below the list keeps a height of its own inside the sheet.
        val announcer = onNode(hasTestTag(ReorderAnnouncerTag) and hasAnyAncestor(hasTestTag("sheet")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(announcer.bottom - announcer.top > 0.dp, "announcer height")
        onNodeWithTag(FolderTags.LIST).performScrollToNode(hasTestTag(FolderTags.NEW))
        waitForIdle()
        onNodeWithTag(FolderTags.NEW).assertIsDisplayed()
    }

    @Test
    fun textsWholeAtTwoX() = runComposeUiTest {
        show(fontScale = LINEAR_TWO_X_FONT_SCALE, width = 360.dp, height = 640.dp)
        open()
        onNodeWithTag(FolderTags.handle("default"), useUnmergedTree = true).assertExists()
        assertClickablesLabeledAndSized("Folders at 2x")
        assertButtonTextsWhole("Folders at 2x")
        pick(FolderTags.menu("hyper"))
        assertClickablesLabeledAndSized("Folder menu at 2x")
        assertButtonTextsWhole("Folder menu at 2x")
    }
}
