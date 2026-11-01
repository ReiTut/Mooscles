package com.mooscles.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.components.ExerciseMediaPlaceholderTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.illustration.FigureJoint
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureProp
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.assertTextNotCut
import com.mooscles.screens.exercise.CustomExerciseFormTags
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.FakeExercisePhotos
import com.mooscles.screens.exercise.photo.PhotoRowTags
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The library tab (spec "Biblioteca (aba)"): top bar, search, count, rows and their states. */
@OptIn(ExperimentalTestApi::class)
class LibraryScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    // pt-BR names, in their alphabetical order: Agachamento frontal com barra, Remada Pendlay,
    // Rosca direta com halteres, Supino articulado do Zé (custom), Supino inclinado com barra,
    // Supino inclinado com halteres, Supino reto com barra.
    private val bench = catalog("barbell_bench_flat")
    private val incline = catalog("barbell_bench_incline")
    private val dumbbellIncline = catalog("dumbbell_bench_incline")
    private val squat = catalog("barbell_front_squat")
    private val pendlay = catalog("barbell_row_pendlay")
    private val curl = catalog("dumbbell_curl_standing")
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", custom = true)
    private val exercises = listOf(bench, incline, dumbbellIncline, squat, pendlay, curl, mine)

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private fun workout(id: String, startedAt: Long, vararg exercises: ExerciseUi) =
        WorkoutUi(id, "Treino $id", startedAt, endedAt = startedAt + 1, exercises = exercises.mapIndexed { i, e -> WorkoutExerciseUi("$id-$i", e) })

    /** Newest first: Remada Pendlay, Supino reto; then the front squat (Supino reto once). */
    private val history = listOf(workout("w2", 200, pendlay, bench), workout("w1", 100, squat, bench))

    /** A still figure for the media seam; the real poses are Task 8's. */
    private val pose = FigurePose(
        hipX = 50f, hipY = 50f, trunk = 0f, head = 0f,
        upperArmNear = 180f, forearmNear = 180f, upperArmFar = 180f, forearmFar = 180f,
        thighNear = 180f, shinNear = 180f, footNear = 90f, thighFar = 180f, shinFar = 180f, footFar = 90f,
        floorY = 91f,
        props = listOf(FigureProp.Barbell(FigureJoint.HAND_NEAR), FigureProp.Floor(91f)),
    )

    /** The view model: records every call and clears the error as the real one does. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null)
        }
    }

    /** What the route asked of the app. */
    private class Requests {
        val opened = mutableListOf<String>()
        var settings = 0
    }

    /** A build that waits for [hold] while one is set. */
    private class Builds {
        var hold: CompletableDeferred<Unit>? = null
        var count = 0
        val build: suspend (List<ExerciseUi>, Boolean) -> LibraryModel = { list, portuguese ->
            count++
            hold?.await()
            LibraryModel.build(list, portuguese)
        }
    }

    private fun stateOf(list: List<ExerciseUi> = exercises, history: List<WorkoutUi> = emptyList()) =
        mutableStateOf(UiState(loading = false, exercises = list, history = history))

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = stateOf(),
        locale: String? = "pt-BR",
        builds: Builds = Builds(),
        media: (ExerciseUi) -> ExerciseMedia = { ExerciseMedia.None },
        fontScale: Float = 1f,
        size: DpSize? = null,
        tall: Boolean = false,
        restoration: ScreenRestoration? = null,
        fake: Fake = Fake(state),
        photos: FakeExercisePhotos? = null,
    ): Requests {
        val requests = Requests()
        // A cache of the test's own (pre-flight F19), so no other test's model is reused.
        val seams = LibrarySeams(cache = LibraryModelCache(), build = builds.build, media = media)
        withScreen(locale = locale, fontScale = fontScale, size = size, restoration = restoration) {
            val route: @androidx.compose.runtime.Composable () -> Unit = {
                TopAnchored(if (tall) TALL else null) {
                    LibraryRoute(
                        state = state.value,
                        actions = fake,
                        onOpenExercise = { requests.opened += it },
                        onOpenSettings = { requests.settings++ },
                        modifier = Modifier,
                        seams = seams,
                    )
                }
            }
            if (photos != null) photos.Provide(route) else route()
        }
        waitForIdle()
        return requests
    }

    private fun ComposeUiTest.row(exercise: ExerciseUi, recent: Boolean = false): SemanticsNodeInteraction {
        val tag = if (recent) LibraryTags.recentRow(exercise.id) else LibraryTags.row(exercise.id)
        onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasTestTag(tag))
        waitForIdle()
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.search(query: String) {
        onNode(hasSetTextAction()).performTextReplacement(query)
        waitForIdle()
    }

    private fun ComposeUiTest.formName(): String =
        onNodeWithTag(CustomExerciseFormTags.NAME).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.cancelForm() {
        onNodeWithTag(CustomExerciseFormTags.CANCEL).performClick()
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
    }

    /** Fills the open form with a new exercise and saves it; the id the screen gave it. */
    private fun ComposeUiTest.createInTheForm(fake: Fake, name: String): String {
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextReplacement(name)
        onNodeWithTag(CustomExerciseFormTags.muscle("Back")).performScrollTo().performClick()
        onNodeWithTag(CustomExerciseFormTags.equipment("Plate-loaded machine")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        val call = fake.calls.last { it.startsWith("createCustomExercise:") }
        assertTrue(call.endsWith(",$name,Back,Plate-loaded machine,Total load"), call)
        return call.removePrefix("createCustomExercise:").substringBefore(",")
    }

    private fun ComposeUiTest.countText(): String =
        onNodeWithTag(LibraryTags.COUNT).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun ComposeUiTest.top(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().top

    /** The ids of the result rows, top to bottom (all of them composed: the screen is tall enough). */
    private fun ComposeUiTest.rowIds(): List<String> =
        onAllNodes(rowTag).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag].removePrefix("library-row-") }

    private val rowTag = SemanticsMatcher("is a library row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-row-") == true
    }

    @Test
    fun topHasTitleSettingsAndCreateAt48() = runComposeUiTest {
        val requests = show()
        onNodeWithText("Biblioteca").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithTag(LibraryTags.SETTINGS).assertContentDescriptionEquals("Configurações").assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(LibraryTags.CREATE).assertContentDescriptionEquals("Criar exercício").assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        // 8 dp between the two targets.
        val settings = onNodeWithTag(LibraryTags.SETTINGS).getUnclippedBoundsInRoot()
        val create = onNodeWithTag(LibraryTags.CREATE).getUnclippedBoundsInRoot()
        assertTrue(create.left - settings.right >= 8.dp - 0.5.dp, "gap ${create.left - settings.right}")
        // The search field sits under the top bar, with its placeholder as its name.
        onNodeWithText("Buscar por nome, músculo ou equipamento", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(top(onNodeWithTag(LibraryTags.SEARCH)) >= onNodeWithTag(LibraryTags.CREATE).getUnclippedBoundsInRoot().bottom - 0.5.dp)
        // "Create exercise" opens the form, with no name yet.
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertEquals("", formName())
        cancelForm()
        assertEquals(0, requests.settings)
    }

    @Test
    fun rowIsOneNodeWithNameMuscleEquipmentAndCustom() = runComposeUiTest {
        show()
        row(bench).assertContentDescriptionEquals("Supino reto com barra, Peito, Barra").assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row(mine).assertContentDescriptionEquals("Supino articulado do Zé, Peito, Máquina de anilhas, personalizado")
        for (exercise in listOf(bench, mine)) {
            val tag = LibraryTags.row(exercise.id)
            // One node a screen reader stops on: nothing inside the row is clickable on its own.
            onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertCountEquals(0)
        }
        onNode(hasText("Peito · Barra") and hasAnyAncestor(hasTestTag(LibraryTags.row(bench.id))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag(LibraryTags.ROW_CUSTOM) and hasAnyAncestor(hasTestTag(LibraryTags.row(mine.id))), useUnmergedTree = true)
            .assertIsDisplayed()
        onNode(hasText("Personalizado") and hasAnyAncestor(hasTestTag(LibraryTags.ROW_CUSTOM)), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag(LibraryTags.ROW_CUSTOM) and hasAnyAncestor(hasTestTag(LibraryTags.row(bench.id))), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun rowsAreAtLeast72WithFigureOrEquipmentIcon() = runComposeUiTest {
        show(media = { if (it.id == bench.id) ExerciseMedia.Figure(pose, setOf(FigureSegment.TORSO_FRONT)) else ExerciseMedia.None })
        for (exercise in exercises) row(exercise).assertHeightIsAtLeast(72.dp)
        row(bench)
        val benchRow = hasAnyAncestor(hasTestTag(LibraryTags.row(bench.id)))
        onNode(hasTestTag(ExerciseMediaFigureTag) and benchRow, useUnmergedTree = true).assertWidthIsEqualTo(56.dp)
        onNode(hasTestTag(ExerciseEquipmentIconTag) and benchRow, useUnmergedTree = true).assertDoesNotExist()
        // Without a figure, the 56 dp tile shows the equipment's icon.
        val squatRow = hasAnyAncestor(hasTestTag(LibraryTags.row(squat.id)))
        row(squat)
        onNode(hasTestTag(ExerciseEquipmentIconTag) and squatRow, useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag(ExerciseMediaFigureTag) and squatRow, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun recentThenAllWithoutSearchOrFilter() = runComposeUiTest {
        show(state = stateOf(history = history), tall = true)
        val recentHeader = onNodeWithTag(LibraryTags.RECENT_HEADER).assertTextEquals("Usados recentemente")
        val allHeader = onNodeWithTag(LibraryTags.ALL_HEADER).assertTextEquals("Todos")
        // Newest workout first, each exercise once.
        val recents = listOf(pendlay, bench, squat).map { top(onNodeWithTag(LibraryTags.recentRow(it.id))) }
        assertTrue(top(recentHeader) < recents.first(), "\"Usados recentemente\" heads the list")
        assertEquals(recents.sorted(), recents)
        onAllNodes(SemanticsMatcher("recent row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-recent-") == true })
            .assertCountEquals(3)
        assertTrue(recents.last() < top(allHeader), "\"Todos\" after the recent ones")
        // Then every exercise, the recent ones too, alphabetically by the shown name.
        assertEquals(listOf(squat, pendlay, curl, mine, incline, dumbbellIncline, bench).map { it.id }, rowIds())
        assertTrue(top(allHeader) < top(onNodeWithTag(LibraryTags.row(squat.id))))
        assertEquals("7 exercícios", countText())
    }

    @Test
    fun searchShowsOnlyResultsAlphabetically() = runComposeUiTest {
        show(state = stateOf(history = history), tall = true)
        search("supino")
        onNodeWithTag(LibraryTags.RECENT_HEADER).assertDoesNotExist()
        onNodeWithTag(LibraryTags.ALL_HEADER).assertDoesNotExist()
        assertEquals(listOf(mine, incline, dumbbellIncline, bench).map { it.id }, rowIds())
        // Bilingual: the English name finds the exercise on a Portuguese screen.
        search("incline dumbbell")
        assertEquals(listOf(dumbbellIncline.id), rowIds())
        search("")
        onNodeWithTag(LibraryTags.RECENT_HEADER).assertIsDisplayed()
        assertEquals(listOf(squat, pendlay, curl, mine, incline, dumbbellIncline, bench).map { it.id }, rowIds())
    }

    @Test
    fun countIsAPoliteLiveRegionAndFollowsResults() = runComposeUiTest {
        show(tall = true)
        onNodeWithTag(LibraryTags.COUNT).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals("7 exercícios", countText())
        search("supino")
        assertEquals("4 exercícios", countText())
        onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
        waitForIdle()
        // Zero takes the plural.
        assertEquals("0 exercícios", countText())
        search("")
        assertEquals("1 exercício", countText())
        onNodeWithTag(LibraryTags.COUNT).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun rowTapOpensTheExercise() = runComposeUiTest {
        val requests = show(state = stateOf(history = history))
        row(curl).performClick()
        waitForIdle()
        row(pendlay, recent = true).performClick()
        waitForIdle()
        assertEquals(listOf(curl.id, pendlay.id), requests.opened)
        assertEquals(0, requests.settings)
    }

    @Test
    fun settingsOpensSettings() = runComposeUiTest {
        val fake = Fake(stateOf())
        val requests = show(state = fake.state, fake = fake)
        onNodeWithTag(LibraryTags.SETTINGS).performClick()
        waitForIdle()
        assertEquals(1, requests.settings)
        assertEquals(emptyList(), requests.opened)
        assertEquals(emptyList(), fake.calls)
    }

    @Test
    fun emptyResultOffersClearFiltersAndCreateFromTheQuery() = runComposeUiTest {
        val requests = show()
        search("remada articulada")
        val empty = onNodeWithTag(LibraryTags.EMPTY)
        empty.assertIsDisplayed()
        onNodeWithText("Nenhum exercício encontrado").assertIsDisplayed()
        onNodeWithText("Tente outras palavras ou menos filtros.").assertIsDisplayed()
        // No filter: only "Create" with the search as the name.
        onNode(hasText("Limpar filtros") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).assertDoesNotExist()
        onNode(hasText("Criar “remada articulada”") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).performClick()
        waitForIdle()
        // The form opens with the search as the name; cancelling it comes back to the same search.
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertEquals("remada articulada", formName())
        cancelForm()
        // With a filter, "Clear filters" too; it keeps the search.
        onNodeWithTag(LibraryTags.MINE_CHIP).performClick()
        waitForIdle()
        onNode(hasText("Criar “remada articulada”") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).assertIsDisplayed()
        onNode(hasText("Limpar filtros") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.MINE_CHIP).assertIsOff()
        assertEquals("remada articulada", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        // A filter alone with nothing found: "Clear filters" is the only action, and no "Create".
        search("")
        onNodeWithTag(LibraryTags.MINE_CHIP).performClick()
        onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.EMPTY).assertIsDisplayed()
        onNode(hasText("Criar", substring = true) and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).assertDoesNotExist()
        onNode(hasText("Limpar filtros") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
        assertEquals("7 exercícios", countText())
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun loadingStateShowsSkeletonNeverTheEmptyCatalog() = runComposeUiTest {
        // The app starts the route while the view model is still loading: an empty list, loading.
        val state = mutableStateOf(UiState())
        val builds = Builds()
        show(state = state, builds = builds)
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(6)
        onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
        onNodeWithTag(LibraryTags.COUNT).assertDoesNotExist()
        // Loading ends; the list's model is still being built, and the empty one stands for nothing.
        builds.hold = CompletableDeferred()
        runOnIdle { state.value = UiState(loading = false, exercises = exercises) }
        waitForIdle()
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(6)
        onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
        onNodeWithText("Nenhum exercício ainda").assertDoesNotExist()
        builds.hold?.complete(Unit)
        waitForIdle()
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(0)
        onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
        assertEquals("7 exercícios", countText())
        row(bench).assertIsDisplayed()
    }

    @Test
    fun loadingShowsSkeletonRows() = runComposeUiTest {
        val builds = Builds().apply { hold = CompletableDeferred() }
        show(builds = builds)
        // Before the model: skeleton rows, no count, no rows; the search and the filters are there.
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(6)
        onNodeWithTag(LibraryTags.COUNT).assertDoesNotExist()
        onAllNodes(rowTag).assertCountEquals(0)
        onNode(hasSetTextAction()).assertIsDisplayed()
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsDisplayed()
        builds.hold?.complete(Unit)
        waitForIdle()
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(0)
        assertEquals("7 exercícios", countText())
        row(bench).assertIsDisplayed()
        assertEquals(1, builds.count)
    }

    @Test
    fun searchFiltersAndScrollSurviveRecreation() = runComposeUiTest {
        // The whole catalog, so the results scroll. No forced language: the restoration tester loses
        // the saved state when the language override rebuilds the tree; the search is bilingual.
        val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
        val restoration = screenRestoration()
        show(state = stateOf(catalog + mine), locale = null, restoration = restoration)
        search("supino")
        onNodeWithTag(LibraryTags.muscleChip("Chest")).performClick()
        waitForIdle()
        val count = countText()
        onNodeWithTag(LibraryTags.LIST).performScrollToIndex(8)
        waitForIdle()
        val topRow = topVisibleRow()
        onNodeWithTag(LibraryTags.MINE_CHIP).assertDoesNotExist()

        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals("supino", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(topRow, topVisibleRow())
        onNodeWithTag(LibraryTags.LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOn()
        assertEquals(count, countText())

        // A new query starts the list from the top again, so the count (a live region) is on screen when it changes.
        onNodeWithTag(LibraryTags.LIST).performScrollToIndex(8)
        waitForIdle()
        onNodeWithTag(LibraryTags.COUNT).assertDoesNotExist()
        search("supino inclinado")
        onNodeWithTag(LibraryTags.COUNT).assertIsDisplayed()
        onNodeWithTag(LibraryTags.MINE_CHIP).assertIsDisplayed()
        assertTrue(countText() != count, "the count follows the new query")
    }

    /** The tag of the highest row the list shows (partly or whole). */
    private fun ComposeUiTest.topVisibleRow(): String =
        onAllNodes(rowTag).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f }
            .minBy { it.boundsInRoot.top }
            .config[SemanticsProperties.TestTag]

    @Test
    fun listChangeWhileSearchingKeepsTheQueryAndUpdatesResults() = runComposeUiTest {
        val state = stateOf()
        val builds = Builds()
        val requests = show(state = state, builds = builds, tall = true)
        search("supino")
        assertEquals(listOf(mine, incline, dumbbellIncline, bench).map { it.id }, rowIds())

        // A custom exercise is created and the bench press goes (a backup import): a new list, built while held.
        val smith = ExerciseUi("custom-smith", "Supino no Smith do Zé", "Chest", "Smith machine", custom = true)
        builds.hold = CompletableDeferred()
        runOnIdle { state.value = state.value.copy(exercises = exercises - bench + smith) }
        waitForIdle()
        // Nothing flashes empty: the previous results stay, with the query, until the new list is built.
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(0)
        onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
        assertEquals(listOf(mine, incline, dumbbellIncline, bench).map { it.id }, rowIds())
        // A tap on the row of the exercise that is gone opens nothing.
        onNodeWithTag(LibraryTags.row(bench.id)).performClick()
        waitForIdle()
        assertEquals(emptyList(), requests.opened)

        builds.hold?.complete(Unit)
        waitForIdle()
        assertEquals("supino", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(listOf(mine, incline, dumbbellIncline, smith).map { it.id }, rowIds())
        onNodeWithTag(LibraryTags.row(bench.id)).assertDoesNotExist()
        assertEquals("4 exercícios", countText())
        onNodeWithTag(LibraryTags.row(smith.id)).performClick()
        waitForIdle()
        assertEquals(listOf(smith.id), requests.opened)
    }

    @Test
    fun queryTypedBeforeTheIndexIsReadyIsAppliedWhenItArrives() = runComposeUiTest {
        val builds = Builds().apply { hold = CompletableDeferred() }
        show(builds = builds, tall = true)
        search("remada")
        onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
        waitForIdle()
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(6)
        assertEquals("remada", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)

        builds.hold?.complete(Unit)
        waitForIdle()
        onAllNodesWithTag(LibraryTags.SKELETON_ROW).assertCountEquals(0)
        assertEquals(listOf(pendlay.id), rowIds())
        onNodeWithTag(LibraryTags.ALL_HEADER).assertDoesNotExist()
        assertEquals("1 exercício", countText())
        onNodeWithTag(LibraryTags.muscleChip("Back")).assertIsOn()
    }

    @Test
    fun languageChangeNeverShowsTheOtherLanguagesNames() = runComposeUiTest {
        // Task 14's hook: a model of the other language is dropped at once, not kept while rebuilding.
        val builds = Builds()
        val cache = LibraryModelCache()
        val portuguese = mutableStateOf(false)
        var model: LibraryModel? = null
        withScreen { model = rememberLibraryModel(exercises, portuguese.value, cache, builds.build) }
        waitForIdle()
        assertEquals(false, model?.portuguese)
        builds.hold = CompletableDeferred()
        runOnIdle { portuguese.value = true }
        waitForIdle()
        assertNull(model, "the English model is not shown while the Portuguese one builds")
        builds.hold?.complete(Unit)
        waitForIdle()
        assertEquals(true, assertNotNull(model).portuguese)
    }

    @Test
    fun nameWrapsToThreeLinesAtTwoX() {
        val long = ExerciseUi(
            "custom-long",
            "Supino inclinado articulado unilateral com pegada neutra, pausa isométrica e descida lenta controlada",
            "Chest", "Plate-loaded machine", custom = true,
        )
        for ((scale, lines) in listOf(1f to 2, 2f to 3, LINEAR_TWO_X_FONT_SCALE to 3)) {
            runComposeUiTest {
                show(state = stateOf(listOf(long, bench)), fontScale = scale, size = DpSize(360.dp, 800.dp))
                val rowNode = row(long).assertHeightIsAtLeast(72.dp)
                val inRow = hasAnyAncestor(hasTestTag(LibraryTags.row(long.id)))
                val name = layoutOf(onNode(hasTestTag(LibraryTags.ROW_NAME) and inRow, useUnmergedTree = true))
                assertEquals(lines, name.layoutInput.maxLines, "at $scale")
                assertEquals(lines, name.lineCount, "at $scale")
                val meta = layoutOf(onNode(hasText("Peito · Máquina de anilhas") and inRow, useUnmergedTree = true))
                assertTextNotCut(meta, "meta at $scale")
                val custom = layoutOf(onNode(hasText("Personalizado") and inRow, useUnmergedTree = true))
                assertShownWhole(custom, "custom tag at $scale")
                // The row grows with its text: the name and the meta stay inside it.
                val rowBottom = rowNode.getUnclippedBoundsInRoot().bottom
                val metaBottom = onNode(hasText("Peito · Máquina de anilhas") and inRow, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
                assertTrue(metaBottom <= rowBottom, "at $scale the meta ends at $metaBottom, the row at $rowBottom")
                // A short name stays on one line. At the iOS linear 2x the long row can push the bench
                // row out of what the list composes in the simulator's window, so scroll to it first
                // (with the clock running).
                row(bench)
                val short = layoutOf(onNode(hasTestTag(LibraryTags.ROW_NAME) and hasAnyAncestor(hasTestTag(LibraryTags.row(bench.id))), useUnmergedTree = true))
                assertTrue(short.lineCount <= lines)
            }
            LocalAppLocale.restore()
        }
    }

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "no text layout")
        return results.first()
    }

    @Test
    fun errorShowsTheLocalizedToastAndClears() = runComposeUiTest {
        val state = stateOf()
        val fake = Fake(state)
        show(state = state, fake = fake)
        runOnIdle { state.value = state.value.copy(error = "raw message", errorCode = UiError.SAVE_FAILED) }
        waitForIdle()
        onNode(
            hasText("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.") and hasAnyAncestor(hasTestTag(TOAST)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("raw message", substring = true).assertDoesNotExist()
        assertEquals(listOf("clearError:"), fake.calls)
        assertNull(state.value.errorCode)
    }

    @Test
    fun creatingOpensTheNewExerciseWhenItArrives() = runComposeUiTest {
        // No forced language (the restoration tester loses the saved state under the override); by tag.
        val state = stateOf()
        val fake = Fake(state)
        val restoration = screenRestoration()
        val requests = show(state = state, locale = null, restoration = restoration, fake = fake)
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        val id = createInTheForm(fake, "Remada articulada")
        // The form stays (busy) until the state brings the exercise, so the library never shows between the form and
        // the detail; nothing opens yet.
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertEquals(emptyList(), requests.opened)
        // Unrelated changes while waiting open nothing either.
        runOnIdle { state.value = state.value.copy(history = history) }
        waitForIdle()
        assertEquals(emptyList(), requests.opened)

        val created = ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true)
        runOnIdle { state.value = state.value.copy(exercises = exercises + created) }
        waitUntil(timeoutMillis = 5_000) { requests.opened.isNotEmpty() }
        waitForIdle()
        assertEquals(listOf(id), requests.opened)
        // This host stays on the library: the handed-over form goes after the cap.
        mainClock.advanceTimeBy(MoosclesTiming().formHandoff + 100)
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
        // Once: later states do not open it again.
        runOnIdle { state.value = state.value.copy(exercises = exercises + created.copy(name = "Remada articulada II")) }
        waitForIdle()
        assertEquals(listOf(id), requests.opened)
        assertEquals(1, fake.calls.count { it.startsWith("createCustomExercise:") })
    }

    /** The saved form is handed over: the detail opens with the form still over the library, which leaves with it. */
    @Test
    fun savedFormStaysUntilTheDetailTakesOver() = runComposeUiTest {
        val state = stateOf()
        val fake = Fake(state)
        val requests = show(state = state, fake = fake)
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        val id = createInTheForm(fake, "Remada articulada")
        mainClock.autoAdvance = false
        val created = ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true)
        runOnIdle { state.value = state.value.copy(exercises = exercises + created) }
        var frames = 0
        while (requests.opened.isEmpty() && frames < 120) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        assertEquals(listOf(id), requests.opened)
        // The host is moving to the detail (a few frames): the form still covers the library, so the library is not seen
        // between them.
        repeat(3) { mainClock.advanceTimeByFrame() }
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        // A host that stays on the library gets it back after the hand-over's cap.
        mainClock.advanceTimeBy(MoosclesTiming().formHandoff + 100)
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
        assertEquals(listOf(id), requests.opened)
        mainClock.autoAdvance = true
    }

    /**
     * The handed-over form leaves with the library, as a NavHost takes the library out of composition (each entry's
     * saved state in a [rememberSaveableStateHolder], under the entry's key) while the form is still shown. What the
     * form had saved is not brought back by the next "Create": that form starts with its own prefill and a new id, and
     * the previous exercise's photo is left alone (final review C1).
     */
    @Test
    fun nextFormAfterTheHandOverStartsAfresh() = runComposeUiTest {
        val photos = FakeExercisePhotos()
        val state = stateOf()
        val fake = Fake(state)
        val onLibrary = mutableStateOf(true)
        val opened = mutableListOf<String>()
        val seams = LibrarySeams(cache = LibraryModelCache(), build = { list, portuguese -> LibraryModel.build(list, portuguese) })
        withScreen(locale = "pt-BR") {
            photos.Provide {
                val holder = rememberSaveableStateHolder()
                val entry = if (onLibrary.value) "library" else "detail"
                holder.SaveableStateProvider(entry) {
                    if (entry == "library") {
                        LibraryRoute(
                            state.value, fake,
                            onOpenExercise = {
                                opened += it
                                onLibrary.value = false
                            },
                            onOpenSettings = {}, modifier = Modifier, seams = seams,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().testTag("detail"))
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        onNodeWithTag(PhotoRowTags.GALLERY).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(PhotoRowTags.PREVIEW, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val first = createInTheForm(fake, "Remada articulada")
        runOnIdle { state.value = state.value.copy(exercises = exercises + ExerciseUi(first, "Remada articulada", "Back", "Plate-loaded machine", custom = true)) }
        waitUntil(timeoutMillis = 5_000) { opened.isNotEmpty() }
        waitForIdle()
        onNodeWithTag("detail").assertExists()
        onNodeWithTag(LibraryScreenTag).assertDoesNotExist()
        assertTrue(photos.store.hasPhoto(first))

        // Back on the library, a second exercise from the search.
        runOnIdle { onLibrary.value = true }
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
        search("puxada do zé")
        onNode(hasText("Criar “puxada do zé”") and hasAnyAncestor(hasTestTag(LibraryTags.EMPTY))).performClick()
        waitForIdle()
        assertEquals("puxada do zé", formName())
        onNodeWithTag(PhotoRowTags.EMPTY, useUnmergedTree = true).assertExists()
        val creations = fake.calls.count { it.startsWith("createCustomExercise:") }
        val second = createInTheForm(fake, "Puxada do Zé")
        assertEquals(creations + 1, fake.calls.count { it.startsWith("createCustomExercise:") })
        assertTrue(second != first, "a new id")
        runOnIdle {
            state.value = state.value.copy(exercises = state.value.exercises + ExerciseUi(second, "Puxada do Zé", "Back", "Plate-loaded machine", custom = true))
        }
        waitUntil(timeoutMillis = 5_000) { opened.size == 2 }
        waitForIdle()
        assertEquals(listOf(first, second), opened)
        assertEquals(1, photos.store.log.count { it == "commit:$first:$first" })
        assertTrue(photos.store.log.none { it.startsWith("commit:$second") }, photos.store.log.toString())
        assertEquals(emptySet(), photos.photos.failedCommits.value)
    }

    @Test
    fun creationErrorDropsThePendingOpen() = runComposeUiTest {
        val state = stateOf()
        val fake = Fake(state)
        val requests = show(state = state, fake = fake)
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        val failed = createInTheForm(fake, "Remada articulada")
        // The save fails before the exercise arrives: the form stays open, the error shows over it and is handed back.
        runOnIdle {
            state.value = state.value.copy(error = "raw", errorCode = UiError.SAVE_FAILED, errorSubject = failed, subjectFailures = mapOf(failed to 1))
        }
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        onNode(
            hasText("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.") and hasAnyAncestor(hasTestTag(TOAST)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        assertNull(state.value.errorCode)
        assertEquals(emptyList(), requests.opened)
        cancelForm()
        // The wait is over: a list that later shows the exercise is not this save's echo.
        runOnIdle { state.value = state.value.copy(exercises = exercises + ExerciseUi(failed, "Remada articulada", "Back", "Plate-loaded machine", custom = true)) }
        waitForIdle()
        assertEquals(emptyList(), requests.opened)

        // An error that comes with the exercise already in the list (a repeated creation of the
        // same id is refused) is no failure of this one: the exercise is there, so it opens.
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        val second = createInTheForm(fake, "Remada cavalinho do Zé")
        runOnIdle {
            state.value = state.value.copy(
                exercises = state.value.exercises + ExerciseUi(second, "Remada cavalinho do Zé", "Back", "Plate-loaded machine", custom = true),
                error = "raw", errorCode = UiError.INVALID_VALUE, errorSubject = second, subjectFailures = mapOf(second to 1),
            )
        }
        waitUntil(timeoutMillis = 5_000) { requests.opened.isNotEmpty() }
        assertEquals(listOf(second), requests.opened)
    }

    @Test
    fun rowShowsTheCustomPhotoThumbnail() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        show(photos = photos, builds = Builds(), tall = true)
        val inMine = hasAnyAncestor(hasTestTag(LibraryTags.row(mine.id)))
        // The image's placeholder (the equipment icon under it) is drawn only for a photo: ExerciseMedia.Image.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(ExerciseMediaPlaceholderTag) and inMine, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag(ExerciseEquipmentIconTag) and inMine, useUnmergedTree = true).assertDoesNotExist()
        // A catalog row has no photo to read: its icon stays.
        val inSquat = hasAnyAncestor(hasTestTag(LibraryTags.row(squat.id)))
        onNode(hasTestTag(ExerciseEquipmentIconTag) and inSquat, useUnmergedTree = true).assertExists()
        onNode(hasTestTag(ExerciseMediaPlaceholderTag) and inSquat, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The real navigation path: the library gives way to the detail as the form closes, so the toast of a photo that
     * could not be kept has to come from the detail.
     */
    @Test
    fun photoThatCannotBeKeptToastsOnTheDetailAfterTheNavigation() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.commitWorks = false }
        val state = stateOf()
        val fake = Fake(state)
        val opened = mutableStateOf<String?>(null)
        val seams = LibrarySeams(cache = LibraryModelCache(), build = { list, portuguese -> LibraryModel.build(list, portuguese) })
        withScreen(locale = "pt-BR") {
            photos.Provide {
                val id = opened.value
                if (id == null) {
                    LibraryRoute(state.value, fake, onOpenExercise = { opened.value = it }, onOpenSettings = {}, modifier = Modifier, seams = seams)
                } else {
                    ExerciseInfoRoute(id, state.value, fake, onBack = {}, onOpenHistory = {}, onOpenRoutine = {}, onOpenWorkout = {}, modifier = Modifier)
                }
            }
        }
        waitForIdle()
        onNodeWithTag(LibraryTags.CREATE).performClick()
        waitForIdle()
        onNodeWithTag(PhotoRowTags.GALLERY).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(PhotoRowTags.PREVIEW, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val id = createInTheForm(fake, "Remada articulada")
        runOnIdle { state.value = state.value.copy(exercises = exercises + ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true)) }
        waitUntil(timeoutMillis = 5_000) { opened.value != null }
        waitForIdle()
        // The library is gone; the detail of the new exercise shows the toast.
        assertEquals(id, opened.value)
        onNodeWithTag(LibraryScreenTag).assertDoesNotExist()
        onNode(hasText("Não foi possível salvar a foto.") and hasAnyAncestor(hasTestTag(TOAST)), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(emptySet(), photos.photos.failedCommits.value)
    }

    @Test
    fun withoutPhotosTheCustomRowKeepsItsIcon() = runComposeUiTest {
        show(tall = true)
        val inMine = hasAnyAncestor(hasTestTag(LibraryTags.row(mine.id)))
        onNode(hasTestTag(ExerciseEquipmentIconTag) and inMine, useUnmergedTree = true).assertExists()
        onNode(hasTestTag(ExerciseMediaPlaceholderTag) and inMine, useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        /** The design system's toast tag (internal there). */
        const val TOAST = "toast"
    }
}
