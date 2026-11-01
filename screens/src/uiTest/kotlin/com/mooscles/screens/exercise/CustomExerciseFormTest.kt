package com.mooscles.screens.exercise

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertTextNotCut
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The custom exercise form (spec "Formulário de exercício personalizado"), without the photo (D2). */
@OptIn(ExperimentalTestApi::class)
class CustomExerciseFormTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseCatalog.exercises.single { it.id == "catalog_barbell_bench_flat" }
        .let { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", "Total load", custom = true)
    private val exercises = listOf(bench, mine)

    /** What the form reported to its host. */
    private class Outcome(val actions: RecordingActions) {
        val saved = mutableListOf<String>()
        var cancelled = 0
        val calls: List<String> get() = actions.calls
    }

    private fun ComposeUiTest.showForm(
        initial: ExerciseUi? = null,
        prefill: String = "",
        state: UiState = UiState(loading = false, exercises = exercises),
        holder: MutableState<UiState> = mutableStateOf(state),
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        size: DpSize? = null,
        restoration: ScreenRestoration? = null,
        open: MutableState<Boolean> = mutableStateOf(true),
    ): Outcome {
        val outcome = Outcome(RecordingActions())
        withScreen(locale = locale, fontScale = fontScale, size = size, restoration = restoration) {
            // The host keeps the form while [open]: a host that does not close it on "Save" (a slow
            // one) still gets one creation.
            if (open.value) {
                CustomExerciseForm(
                    initial = initial,
                    prefillName = prefill,
                    state = holder.value,
                    actions = outcome.actions,
                    onSaved = { outcome.saved += it },
                    onCancel = { outcome.cancelled++ },
                    // The form's window takes the whole screen: the page itself gets the phone's size.
                    pageSize = size,
                )
            }
        }
        waitForIdle()
        return outcome
    }

    private fun ComposeUiTest.name(): SemanticsNodeInteraction = onNodeWithTag(CustomExerciseFormTags.NAME)

    private fun ComposeUiTest.nameText(): String =
        name().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.type(text: String) {
        name().performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.choose(muscle: MuscleGroup, equipment: Equipment) {
        tap(CustomExerciseFormTags.muscle(muscle.label))
        tap(CustomExerciseFormTags.equipment(equipment.label))
    }

    private fun ComposeUiTest.save() {
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.conventionNode(convention: LoadConvention): SemanticsNodeInteraction =
        onNodeWithTag(CustomExerciseFormTags.convention(convention.label)).performScrollTo()

    private fun ComposeUiTest.hasTextInForm(text: String, substring: Boolean = false) =
        onNode(hasText(text, substring = substring) and hasAnyAncestor(hasTestTag(CustomExerciseFormTags.ROOT)), useUnmergedTree = true)

    private val creations = { calls: List<String> -> calls.filter { it.startsWith("createCustomExercise:") } }

    /** The id the form gave its creation. */
    private fun createdId(outcome: Outcome): String =
        creations(outcome.calls).last().removePrefix("createCustomExercise:").substringBefore(",")

    @Test
    fun blankNameShowsTheErrorAndStays() = runComposeUiTest {
        val outcome = showForm()
        choose(MuscleGroup.CHEST, Equipment.BARBELL)
        type("   ")
        save()
        name().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        hasTextInForm("Digite um nome.").assertIsDisplayed()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertEquals(emptyList(), outcome.saved)
        assertEquals(emptyList(), creations(outcome.calls))
        // Typing a name clears the error.
        type("Remada")
        name().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        hasTextInForm("Digite um nome.").assertDoesNotExist()
    }

    @Test
    fun nameOver120ShowsTheError() = runComposeUiTest {
        val outcome = showForm()
        choose(MuscleGroup.BACK, Equipment.CABLE)
        type("a".repeat(121))
        save()
        hasTextInForm("Use até 120 caracteres.").assertIsDisplayed()
        name().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        assertEquals(emptyList(), creations(outcome.calls))
        // 120, with spaces around it (trimmed), is accepted.
        type(" ${"a".repeat(120)} ")
        hasTextInForm("Use até 120 caracteres.").assertDoesNotExist()
        save()
        assertEquals(1, creations(outcome.calls).size)
        assertTrue(creations(outcome.calls).single().contains(",${"a".repeat(120)},Back,Cable,Total load"))
    }

    @Test
    fun missingMuscleOrEquipmentShowsTheErrorAndStays() = runComposeUiTest {
        val outcome = showForm()
        type("Remada articulada")
        save()
        hasTextInForm("Escolha um grupo muscular.").assertIsDisplayed()
        hasTextInForm("Escolha o equipamento.").assertExists()
        assertEquals(emptyList(), creations(outcome.calls))
        tap(CustomExerciseFormTags.muscle(MuscleGroup.BACK.label))
        hasTextInForm("Escolha um grupo muscular.").assertDoesNotExist()
        save()
        assertEquals(emptyList(), creations(outcome.calls))
        tap(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label))
        hasTextInForm("Escolha o equipamento.").assertDoesNotExist()
        save()
        assertEquals(1, creations(outcome.calls).size)
    }

    @Test
    fun duplicateWarningDoesNotBlock() = runComposeUiTest {
        val outcome = showForm()
        type("supino reto com barra")
        // The existing exercise, by its name in the screen's language; not an error.
        hasTextInForm("Já existe “Supino reto com barra”. Você pode salvar mesmo assim.").assertIsDisplayed()
        name().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        // Also by the English catalog name.
        type("Barbell Bench Press")
        hasTextInForm("Já existe “Supino reto com barra”. Você pode salvar mesmo assim.").assertIsDisplayed()
        choose(MuscleGroup.CHEST, Equipment.BARBELL)
        save()
        // A create is reported saved once the state brings the exercise, so only the call is seen here.
        assertEquals(1, creations(outcome.calls).size)
        assertTrue(creations(outcome.calls).single().endsWith(",Barbell Bench Press,Chest,Barbell,Total load"))
        type("Supino reto inédito")
        hasTextInForm("Já existe", substring = true).assertDoesNotExist()
    }

    @Test
    fun equipmentSetsTheDefaultConventionWhichCanChange() = runComposeUiTest {
        val outcome = showForm()
        type("Rosca martelo")
        tap(CustomExerciseFormTags.muscle(MuscleGroup.BICEPS.label))
        tap(CustomExerciseFormTags.equipment(Equipment.DUMBBELL.label))
        conventionNode(LoadConvention.PER_HAND).assertIsSelected()
        tap(CustomExerciseFormTags.equipment(Equipment.BODYWEIGHT.label))
        conventionNode(LoadConvention.BODYWEIGHT).assertIsSelected()
        conventionNode(LoadConvention.PER_HAND).assertIsNotSelected()
        tap(CustomExerciseFormTags.equipment(Equipment.CABLE.label))
        conventionNode(LoadConvention.TOTAL_LOAD).assertIsSelected()
        // The person's own choice; another equipment no longer replaces it.
        tap(CustomExerciseFormTags.convention(LoadConvention.ONE_SIDE.label))
        conventionNode(LoadConvention.ONE_SIDE).assertIsSelected()
        tap(CustomExerciseFormTags.equipment(Equipment.DUMBBELL.label))
        conventionNode(LoadConvention.ONE_SIDE).assertIsSelected()
        // Each option says what it means, in the screen's language.
        hasTextInForm("Por mão").assertExists()
        save()
        assertTrue(creations(outcome.calls).single().endsWith(",Rosca martelo,Biceps,Dumbbell,One side"))
    }

    @Test
    fun saveCreatesWithTheScreenId() = runComposeUiTest {
        val holder = mutableStateOf(UiState(loading = false, exercises = exercises))
        val outcome = showForm(holder = holder)
        type("Supino articulado")
        choose(MuscleGroup.CHEST, Equipment.PLATE_LOADED_MACHINE)
        save()
        val id = createdId(outcome)
        assertTrue(id.isNotBlank() && !id.startsWith("catalog_"), id)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id), id)
        assertEquals("createCustomExercise:$id,Supino articulado,Chest,Plate-loaded machine,Total load", outcome.calls.last())
        // A stale error from before is cleared first, so the host only sees this creation's.
        assertEquals(listOf("clearError:", outcome.calls.last()), outcome.calls)
        // The form stays (busy) until the state brings the exercise, then reports the same id.
        assertEquals(emptyList(), outcome.saved)
        holder.value = holder.value.copy(exercises = exercises + ExerciseUi(id, "Supino articulado", "Chest", "Plate-loaded machine", custom = true))
        waitForIdle()
        assertEquals(listOf(id), outcome.saved)
    }

    @Test
    fun saveTwiceCreatesOnce() = runComposeUiTest {
        val holder = mutableStateOf(UiState(loading = false, exercises = exercises))
        val outcome = showForm(holder = holder)
        type("Supino articulado")
        choose(MuscleGroup.CHEST, Equipment.PLATE_LOADED_MACHINE)
        // Two taps in one frame, then a third once the first went through.
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick().performClick()
        waitForIdle()
        save()
        assertEquals(1, creations(outcome.calls).size, "${outcome.calls}")
        // The form is busy until the exercise arrives; then the host hears once.
        assertEquals(emptyList(), outcome.saved)
        val id = createdId(outcome)
        holder.value = holder.value.copy(exercises = exercises + ExerciseUi(id, "Supino articulado", "Chest", "Plate-loaded machine", custom = true))
        waitForIdle()
        assertEquals(listOf(id), outcome.saved)
        assertTrue(creations(outcome.calls).single().startsWith("createCustomExercise:$id,"))
    }

    @Test
    fun editPrefillsAndSaves() = runComposeUiTest {
        val holder = mutableStateOf(UiState(loading = false, exercises = exercises))
        val outcome = showForm(initial = mine, holder = holder)
        onNodeWithTag(CustomExerciseFormTags.TITLE).assertTextEquals("Editar exercício")
        assertEquals("Supino articulado do Zé", nameText())
        onNodeWithTag(CustomExerciseFormTags.muscle(MuscleGroup.CHEST.label)).performScrollTo().assertIsSelected()
        onNodeWithTag(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label)).performScrollTo().assertIsSelected()
        conventionNode(LoadConvention.TOTAL_LOAD).assertIsSelected()
        // Its own name is no duplicate.
        hasTextInForm("Já existe", substring = true).assertDoesNotExist()
        type("Supino articulado do Zé II")
        tap(CustomExerciseFormTags.muscle(MuscleGroup.SHOULDERS.label))
        // Editing keeps the convention when the equipment changes.
        tap(CustomExerciseFormTags.equipment(Equipment.DUMBBELL.label))
        conventionNode(LoadConvention.TOTAL_LOAD).assertIsSelected()
        save()
        assertEquals("saveCustomExercise:custom-ze,Supino articulado do Zé II,Shoulders,Dumbbell,Total load", outcome.calls.last())
        // An edit waits for the state to show the saved fields, as a creation waits for the exercise.
        assertEquals(emptyList(), outcome.saved)
        holder.value = holder.value.copy(exercises = listOf(bench, mine.copy(name = "Supino articulado do Zé II", muscle = "Shoulders", equipment = "Dumbbell")))
        waitForIdle()
        assertEquals(listOf(mine.id), outcome.saved)
        assertEquals(emptyList(), creations(outcome.calls))
    }

    @Test
    fun conventionChangeWarnsWhenSetsExist() = runComposeUiTest {
        val logged = WorkoutUi(
            "w1", "Treino", startedAt = 1, endedAt = 2,
            exercises = listOf(WorkoutExerciseUi("w1-0", mine, sets = listOf(SetUi("s1", reps = 8, weightKg = 60.0, completed = true)))),
        )
        val history = mutableStateOf(listOf(logged))
        val warning = "As séries já registradas continuam com a convenção de quando foram feitas."
        withScreen(locale = "pt-BR") {
            CustomExerciseForm(mine, "", UiState(loading = false, exercises = exercises, history = history.value), RecordingActions(), {}, {})
        }
        waitForIdle()
        hasTextInForm(warning).assertDoesNotExist()
        tap(CustomExerciseFormTags.convention(LoadConvention.PER_HAND.label))
        hasTextInForm(warning).assertExists()
        onNodeWithTag(CustomExerciseFormTags.CONVENTION_CHANGE).performScrollTo().assertIsDisplayed()
        tap(CustomExerciseFormTags.convention(LoadConvention.TOTAL_LOAD.label))
        hasTextInForm(warning).assertDoesNotExist()
        // Without logged sets, a change says nothing.
        runOnIdle { history.value = emptyList() }
        waitForIdle()
        tap(CustomExerciseFormTags.convention(LoadConvention.PER_HAND.label))
        hasTextInForm(warning).assertDoesNotExist()
    }

    @Test
    fun cancelCallsNothing() = runComposeUiTest {
        val outcome = showForm()
        type("Remada articulada")
        choose(MuscleGroup.BACK, Equipment.PLATE_LOADED_MACHINE)
        onNodeWithTag(CustomExerciseFormTags.CANCEL).assertTextEquals("Cancelar").performClick()
        waitForIdle()
        assertEquals(1, outcome.cancelled)
        assertEquals(emptyList(), outcome.saved)
        assertEquals(emptyList(), outcome.calls)
    }

    @Test
    fun draftSurvivesRecreation() = runComposeUiTest {
        // No forced language: the restoration tester loses the saved state when the language
        // override rebuilds the tree. Everything here is found by tag.
        val restoration = screenRestoration()
        val outcome = showForm(prefill = "Remada", locale = null, restoration = restoration)
        type("Remada articulada")
        choose(MuscleGroup.BACK, Equipment.PLATE_LOADED_MACHINE)
        tap(CustomExerciseFormTags.convention(LoadConvention.ONE_SIDE.label))

        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals("Remada articulada", nameText())
        onNodeWithTag(CustomExerciseFormTags.muscle(MuscleGroup.BACK.label)).performScrollTo().assertIsSelected()
        onNodeWithTag(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label)).performScrollTo().assertIsSelected()
        conventionNode(LoadConvention.ONE_SIDE).assertIsSelected()
        // The choice of the convention was the person's: a new equipment still keeps it.
        tap(CustomExerciseFormTags.equipment(Equipment.DUMBBELL.label))
        conventionNode(LoadConvention.ONE_SIDE).assertIsSelected()
        save()
        val id = createdId(outcome)
        assertEquals("createCustomExercise:$id,Remada articulada,Back,Dumbbell,One side", outcome.calls.last())

        // Saved, then recreated: the form is the draft's, open and free (a restored wait could never end after
        // process death), with the same id; saving again sends the creation again for that id.
        restoration.emulateSaveAndRestore()
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertEquals(emptyList(), outcome.saved)
        save()
        assertEquals(2, creations(outcome.calls).size, "${outcome.calls}")
        assertEquals(id, createdId(outcome))
    }

    @Test
    fun prefillComesFromTheSearch() = runComposeUiTest {
        showForm(prefill = "supino articulado")
        onNodeWithTag(CustomExerciseFormTags.TITLE).assertTextEquals("Novo exercício")
        assertEquals("supino articulado", nameText())
        // A new exercise starts with no muscle group and no equipment.
        for (group in MuscleGroup.entries) onNodeWithTag(CustomExerciseFormTags.muscle(group.label)).assertIsNotSelected()
        for (equipment in Equipment.entries) onNodeWithTag(CustomExerciseFormTags.equipment(equipment.label)).assertIsNotSelected()
        hasTextInForm("Peito").assertExists()
        hasTextInForm("Máquina de anilhas").assertExists()
        // What is required is said before any save.
        onNodeWithTag(CustomExerciseFormTags.REQUIRED_HINT).assertTextEquals("Escolha um grupo muscular e o equipamento.")
    }

    @Test
    fun refusedSaveBringsTheFirstProblemIntoView() = runComposeUiTest {
        // A small phone with large text: most of the form is below the fold.
        val outcome = showForm(fontScale = LINEAR_TWO_X_FONT_SCALE, size = DpSize(320.dp, 640.dp))
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        type("Remada articulada")
        // No muscle group: its error comes into view, announced, without scrolling by hand.
        save()
        onNodeWithTag(CustomExerciseFormTags.MUSCLE_ERROR).assertIsDisplayed().assert(polite)
        // With the muscle chosen and the page back at the top, the equipment error comes into view.
        tap(CustomExerciseFormTags.muscle(MuscleGroup.BACK.label))
        onNodeWithTag(CustomExerciseFormTags.TITLE).performScrollTo()
        waitForIdle()
        save()
        onNodeWithTag(CustomExerciseFormTags.EQUIPMENT_ERROR).assertIsDisplayed().assert(polite)
        onNodeWithTag(CustomExerciseFormTags.MUSCLE_ERROR).assertDoesNotExist()
        // An invalid name, with the page scrolled to its end: the name field comes back into view, focused.
        tap(CustomExerciseFormTags.equipment(Equipment.CABLE.label))
        conventionNode(LoadConvention.ASSISTANCE)
        type("")
        save()
        name().assertIsFocused().assertIsDisplayed()
        assertEquals(emptyList(), creations(outcome.calls))
    }

    /** "Save", the filled button, ends where the page's content ends (16 dp from the edge), not 8 dp from it. */
    @Test
    fun saveEndsWhereTheContentEnds() {
        for (width in listOf(400.dp, 320.dp)) {
            runComposeUiTest {
                showForm(size = DpSize(width, 800.dp))
                val save = onNodeWithTag(CustomExerciseFormTags.SAVE).getUnclippedBoundsInRoot()
                val field = name().getUnclippedBoundsInRoot()
                assertTrue((save.right - field.right).value in -0.5f..0.5f, "at $width: save ends at ${save.right}, the field at ${field.right}")
                val page = onNodeWithTag(CustomExerciseFormTags.ROOT).getUnclippedBoundsInRoot()
                assertTrue((page.right - field.right).value in 15.5f..16.5f, "at $width: the field ends at ${field.right}, the page at ${page.right}")
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun escapeCancelsAndTheWindowHasItsTitle() = runComposeUiTest {
        val outcome = showForm()
        onNodeWithTag(CustomExerciseFormTags.ROOT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Novo exercício"))
        type("Remada articulada")
        // The key goes to the focused field; the window sees it first.
        name().assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, outcome.cancelled)
        assertEquals(emptyList(), outcome.calls)
    }

    @Test
    fun largeFontKeepsEveryLabelWhole() {
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            runComposeUiTest {
                val logged = WorkoutUi(
                    "w1", "Treino", startedAt = 1, endedAt = 2,
                    exercises = listOf(WorkoutExerciseUi("w1-0", mine, sets = listOf(SetUi("s1", reps = 8, weightKg = 60.0, completed = true)))),
                )
                showForm(
                    initial = mine,
                    state = UiState(loading = false, exercises = exercises, history = listOf(logged)),
                    fontScale = scale,
                    size = DpSize(320.dp, 640.dp),
                )
                type("supino reto com barra")
                tap(CustomExerciseFormTags.convention(LoadConvention.ASSISTANCE.label))
                val texts = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                        hasAnyAncestor(hasTestTag(CustomExerciseFormTags.ROOT)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                var checked = 0
                for (node in texts) {
                    val results = mutableListOf<TextLayoutResult>()
                    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
                    if (!action(results) || results.isEmpty()) continue
                    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    // The test's font scale reaches the form's own window.
                    assertEquals(scale, results.first().layoutInput.density.fontScale, "\"$text\"")
                    assertTextNotCut(results.first(), "\"$text\" at $scale")
                    checked++
                }
                // Title, the two buttons, the field's label and warning, 3 headings, 12 + 9 chips,
                // 5 options with their explanations, the convention warning.
                assertTrue(checked >= 39, "only $checked texts at $scale")
            }
            LocalAppLocale.restore()
        }
    }
}
