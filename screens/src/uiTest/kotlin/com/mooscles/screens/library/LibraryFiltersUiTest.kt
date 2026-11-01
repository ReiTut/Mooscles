package com.mooscles.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.MuscleGroup
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.restoreTouchMode
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.UiState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The library's filters (spec "Biblioteca", Filtros): muscle chips, the equipment sheet, "My exercises" and "Clear filters". */
@OptIn(ExperimentalTestApi::class)
class LibraryFiltersUiTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private val bench = catalog("barbell_bench_flat")
    private val dumbbellIncline = catalog("dumbbell_bench_incline")
    private val pendlay = catalog("barbell_row_pendlay")
    private val pushUp = catalog("bodyweight_push_up")
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", custom = true)
    private val exercises = listOf(bench, dumbbellIncline, pendlay, pushUp, mine)

    private class Modes {
        var manager: InputModeManager? = null
    }

    private fun ComposeUiTest.showLibrary(
        locale: String? = "pt-BR",
        restoration: ScreenRestoration? = null,
        modes: Modes? = null,
    ) {
        val state = UiState(loading = false, exercises = exercises)
        val seams = LibrarySeams(cache = LibraryModelCache(), build = { list, portuguese -> LibraryModel.build(list, portuguese) })
        withScreen(locale = locale, restoration = restoration) {
            modes?.manager = LocalInputModeManager.current
            TopAnchored(TALL) {
                LibraryRoute(state, RecordingActions(), onOpenExercise = {}, onOpenSettings = {}, modifier = Modifier, seams = seams)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /** The exercises the list shows as rows (the screen is tall enough for all of them). */
    private fun ComposeUiTest.shownRows(): Set<String> =
        exercises.map { it.id }.filter { onAllNodesWithTag(LibraryTags.row(it)).fetchSemanticsNodes().isNotEmpty() }.toSet()

    private fun ComposeUiTest.chipLabel(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    /**
     * Closes the open sheet by its scrim (the design system's `SheetScrimTag`): a tap, or its
     * semantic click ([semantic]), as a screen reader or a keyboard user does, which keeps keyboard mode.
     */
    private fun ComposeUiTest.closeSheet(semantic: Boolean = false) {
        if (semantic) onNodeWithTag(SHEET_SCRIM).performSemanticsAction(SemanticsActions.OnClick) else onNodeWithTag(SHEET_SCRIM).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertDoesNotExist()
    }

    @Test
    fun muscleChipsAreCheckboxesInEnumOrder() = runComposeUiTest {
        var selected by mutableStateOf(setOf<String>())
        val toggled = mutableListOf<String>()
        withScreen(locale = "pt-BR") {
            MuscleFilterRow(selected, onToggle = { label ->
                toggled += label
                selected = if (label in selected) selected - label else selected + label
            })
        }
        waitForIdle()
        val lefts = MuscleGroup.entries.map { group ->
            val chip = onNodeWithTag(LibraryTags.muscleChip(group.label))
            chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff()
            chip.getUnclippedBoundsInRoot().left
        }
        assertEquals(12, lefts.size)
        assertEquals(lefts.sorted(), lefts, "chips in the order of MuscleGroup")
        assertEquals(lefts.size, lefts.distinct().size)
        // Translated, from the English label.
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertTextEquals("Peito")
        onNodeWithTag(LibraryTags.muscleChip("Full body")).assertTextEquals("Corpo inteiro")
        // Several at once ("or"): each toggles on its own.
        onNodeWithTag(LibraryTags.muscleChip("Chest")).performClick()
        onNodeWithTag(LibraryTags.muscleChip("Back")).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOn()
        onNodeWithTag(LibraryTags.muscleChip("Back")).assertIsOn()
        onNodeWithTag(LibraryTags.muscleChip("Chest")).performClick()
        waitForIdle()
        onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOff()
        assertEquals(listOf("Chest", "Back", "Chest"), toggled)
        assertEquals(setOf("Back"), selected)
    }

    @Test
    fun equipmentSheetSelectsSeveralAndTheChipCounts() = runComposeUiTest {
        showLibrary()
        val chip = onNodeWithTag(LibraryTags.EQUIPMENT_CHIP)
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("Equipamento", chipLabel(LibraryTags.EQUIPMENT_CHIP))
        assertEquals(null, chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        click(LibraryTags.EQUIPMENT_CHIP)
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertIsDisplayed()
        // The 9 equipment, as checkboxes, in the order of the enum.
        val places = Equipment.entries.map { equipment ->
            val option = onNodeWithTag(LibraryTags.equipmentOption(equipment.label))
            option.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff()
            option.getUnclippedBoundsInRoot().let { it.top.value to it.left.value }
        }
        assertEquals(9, places.size)
        // Reading order (line by line, then left to right) is the order of the enum.
        assertEquals(places.sortedWith(compareBy({ it.first }, { it.second })), places)
        onNodeWithTag(LibraryTags.equipmentOption("Barbell")).assertTextEquals("Barra")
        click(LibraryTags.equipmentOption("Barbell"))
        click(LibraryTags.equipmentOption("Dumbbell"))
        onNodeWithTag(LibraryTags.equipmentOption("Barbell")).assertIsOn()
        onNodeWithTag(LibraryTags.equipmentOption("Dumbbell")).assertIsOn()
        // The chip says the count while the sheet is open, and the list follows at once.
        assertEquals("Equipamento · 2", chipLabel(LibraryTags.EQUIPMENT_CHIP))
        closeSheet()
        assertEquals("Equipamento · 2", chipLabel(LibraryTags.EQUIPMENT_CHIP))
        // Not by color alone: the chosen equipment is its state, in words.
        onNodeWithTag(LibraryTags.EQUIPMENT_CHIP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Barra, Halter"))
        assertEquals(setOf(bench.id, dumbbellIncline.id, pendlay.id), shownRows())
        // Turning one off again.
        click(LibraryTags.EQUIPMENT_CHIP)
        click(LibraryTags.equipmentOption("Barbell"))
        closeSheet()
        assertEquals("Equipamento · 1", chipLabel(LibraryTags.EQUIPMENT_CHIP))
        assertEquals(setOf(dumbbellIncline.id), shownRows())
    }

    @Test
    fun equipmentSheetSelectionSurvivesRecreation() = runComposeUiTest {
        // No forced language: the restoration tester loses the saved state when the language override rebuilds the tree.
        val restoration = screenRestoration()
        showLibrary(locale = null, restoration = restoration)
        click(LibraryTags.EQUIPMENT_CHIP)
        click(LibraryTags.equipmentOption("Cable"))
        click(LibraryTags.equipmentOption("Bodyweight"))

        restoration.emulateSaveAndRestore()
        waitForIdle()
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(LibraryTags.equipmentOption("Cable")).assertIsOn()
        onNodeWithTag(LibraryTags.equipmentOption("Bodyweight")).assertIsOn()
        onNodeWithTag(LibraryTags.equipmentOption("Barbell")).assertIsOff()
        assertTrue(chipLabel(LibraryTags.EQUIPMENT_CHIP).endsWith(" · 2"), chipLabel(LibraryTags.EQUIPMENT_CHIP))
        closeSheet()
        assertEquals(setOf(pushUp.id), shownRows())
    }

    /**
     * Focus is the keyboard's (and a switch's): in touch mode a chip takes no focus on Android, so
     * the test works in keyboard mode and opens the sheet by the chip's semantic click.
     */
    @Test
    fun equipmentSheetReturnsFocusToTheChip() = try {
        returnsFocus()
    } finally {
        restoreTouchMode()
    }

    private fun returnsFocus() = runComposeUiTest {
        val modes = Modes()
        showLibrary(modes = modes)
        runOnUiThread { modes.manager?.requestInputMode(InputMode.Keyboard) }
        waitForIdle()
        assertEquals(InputMode.Keyboard, modes.manager?.inputMode, "keyboard mode")
        onNodeWithTag(LibraryTags.EQUIPMENT_CHIP).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(LibraryTags.EQUIPMENT_SHEET).assertIsDisplayed()
        onNodeWithTag(LibraryTags.equipmentOption("Kettlebell")).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        closeSheet(semantic = true)
        onNodeWithTag(LibraryTags.EQUIPMENT_CHIP).assertIsFocused()
    }

    @Test
    fun mineOnlyToggles() = runComposeUiTest {
        showLibrary()
        val chip = onNodeWithTag(LibraryTags.MINE_CHIP)
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff()
        assertEquals("Meus exercícios", chipLabel(LibraryTags.MINE_CHIP))
        click(LibraryTags.MINE_CHIP)
        onNodeWithTag(LibraryTags.MINE_CHIP).assertIsOn()
        assertEquals(setOf(mine.id), shownRows())
        // With the search too ("and").
        onNode(hasSetTextAction()).performTextReplacement("remada")
        waitForIdle()
        assertEquals(emptySet(), shownRows())
        onNode(hasSetTextAction()).performTextReplacement("")
        waitForIdle()
        click(LibraryTags.MINE_CHIP)
        onNodeWithTag(LibraryTags.MINE_CHIP).assertIsOff()
        assertEquals(exercises.map { it.id }.toSet(), shownRows())
    }

    @Test
    fun clearFiltersShowsOnlyWithAFilter() = runComposeUiTest {
        showLibrary()
        onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertDoesNotExist()
        onNode(hasSetTextAction()).performTextReplacement("supino")
        waitForIdle()
        // A search is not a filter.
        onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertDoesNotExist()
        for (turnOn in listOf<ComposeUiTest.() -> Unit>(
            { click(LibraryTags.muscleChip("Back")) },
            { click(LibraryTags.MINE_CHIP) },
            {
                click(LibraryTags.EQUIPMENT_CHIP)
                click(LibraryTags.equipmentOption("Smith machine"))
                closeSheet()
            },
        )) {
            turnOn()
            onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertTextEquals("Limpar filtros").assertIsDisplayed()
            click(LibraryTags.CLEAR_FILTERS)
            onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertDoesNotExist()
            onNodeWithTag(LibraryTags.muscleChip("Back")).assertIsOff()
            onNodeWithTag(LibraryTags.MINE_CHIP).assertIsOff()
            assertEquals("Equipamento", chipLabel(LibraryTags.EQUIPMENT_CHIP))
            // The search stays.
            assertEquals("supino", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertEquals(setOf(bench.id, dumbbellIncline.id, mine.id), shownRows())
        }
    }

    private companion object {
        /** The design system's scrim tag of a sheet (internal there). */
        const val SHEET_SCRIM = "sheet-scrim"
    }
}
