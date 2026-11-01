package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseAddSetTag
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.SetRowTypeTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The set type sheet, removing a set with its undo toast and adding a set (spec "Tipos de
 * série", D13, "Adicionar série"). The rows sit in a lazy list as the screen lays them out, with
 * the sheet, the toast and the footer the screen puts beside them.
 */
@OptIn(ExperimentalTestApi::class)
class SetTypeAndRemovalTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", convention = "Total load")
    private val assistedPullUp = ExerciseUi("apu", "Assisted pull-up", "Back", "Machine", convention = "Assistance")

    /**
     * Records every call and changes [entry] the way the database emission would: always for
     * `removeSet`, `restoreSet` and `addSet` (which, like the repository, copies the last set's
     * load and assistance), and for `updateSet` only with [echo].
     */
    private class Actions(
        val entry: MutableState<WorkoutExerciseUi>,
        val echo: Boolean = true,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls
        private var added = 0

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            if (echo) {
                entry.value = entry.value.copy(
                    sets = entry.value.sets.map {
                        if (it.id == setId) it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else it
                    },
                )
            }
        }

        override fun removeSet(setId: String) {
            recording.removeSet(setId)
            entry.value = entry.value.copy(sets = entry.value.sets.filter { it.id != setId })
        }

        override fun restoreSet(entryId: String, position: Int, set: SetUi) {
            recording.restoreSet(entryId, position, set)
            val sets = entry.value.sets.toMutableList()
            sets.add(position.coerceIn(0, sets.size), set.copy(record = null))
            entry.value = entry.value.copy(sets = sets)
        }

        override fun addSet(entryId: String) {
            recording.addSet(entryId)
            val last = entry.value.sets.lastOrNull()
            added++
            entry.value = entry.value.copy(
                sets = entry.value.sets + SetUi("n$added", weightKg = last?.weightKg ?: 0.0, assistance = last?.assistance ?: false),
            )
        }
    }

    private class Shown {
        var screen: ActiveWorkoutScreenState? = null
    }

    /**
     * The sets of [entry] as [WorkoutSetRow]s in a lazy list, with the footer under them, and the
     * type sheet and the undo toast the screen puts beside the list. A tap on a set's number opens
     * its sheet; a removal from the row goes the screen's way, with its [RemovedSet]. Everything
     * keeps clear of the system bars and the keyboard ([WindowInsets.safeDrawing]), so a test that
     * types sees the same layout whether or not a soft keyboard opens.
     */
    private fun ComposeUiTest.showList(
        entry: MutableState<WorkoutExerciseUi>,
        actions: MoosclesActions,
        locale: String? = "pt-BR",
        restoration: ScreenRestoration? = null,
    ): Shown {
        val shown = Shown()
        withScreen(locale = locale, restoration = restoration) {
            val screen = rememberActiveWorkoutScreenState()
            shown.screen = screen
            val host = remember { MToastHostState() }
            val current = entry.value
            val suggestions = SetSuggestions.forExercise(current.sets, current.previousSets, current.exercise.convention, screen.cleared)
            val previous = SetSuggestions.pairPrevious(current.sets, current.previousSets)
            // Clear of the system bars and the keyboard, as the screen is: a focused field under the
            // status bar makes the emulator pan the window down when the soft keyboard opens, and a
            // toast at the very bottom then falls off the screen.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                LazyColumn(Modifier.width(412.dp)) {
                    itemsIndexed(current.sets, key = { _, set -> "row-${set.id}" }) { i, set ->
                        WorkoutSetRow(
                            set = set, number = i + 1, total = current.sets.size, entry = current,
                            suggestion = suggestions[i], previous = previous[i], pounds = false,
                            screen = screen, actions = actions,
                            onChangeType = { screen.openTypeSheet(it.id) },
                            onRemove = { removed, position -> screen.removeSet(RemovedSet(current.id, position, removed, position + 1), actions) },
                            modifier = setRowItemMotion(),
                        )
                    }
                    item(key = "footer-${current.id}") { AddSetFooter(current, actions) }
                }
                MToastHost(host, Modifier.fillMaxSize())
            }
            SetTypeSheetHost(listOf(entry.value), pounds = false, screen = screen, actions = actions)
            UndoRemovalToast(screen, host, actions)
        }
        waitForIdle()
        return shown
    }

    private fun sets(count: Int = 4, assistance: Boolean = false) =
        (1..count).map { SetUi("s$it", reps = 6, weightKg = 85.0, assistance = assistance) }

    private fun entryOf(sets: List<SetUi>, exercise: ExerciseUi = bench) =
        mutableStateOf(WorkoutExerciseUi("e1", exercise, sets = sets))

    private fun ComposeUiTest.row(id: String): SemanticsNodeInteraction = onNodeWithTag(workoutSetRowTag(id))

    /** Taps the number of the row of [id], which opens its type sheet. */
    private fun ComposeUiTest.tapNumber(id: String) {
        row(id).performTouchInput { click(Offset(width * 0.05f, centerY)) }
        waitForIdle()
    }

    /** Swipes the row of [id] to the left, from the check, so the drag cannot start on a field. */
    private fun ComposeUiTest.swipeLeft(id: String) {
        row(id).performTouchInput { swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400) }
        waitForIdle()
    }

    /** The editable text field in the [cell] (load or reps tag) of the row of [id]. */
    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id))), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun typeSheetOffersNormalWarmupAssisted() {
        for ((locale, texts) in listOf(
            "pt-BR" to listOf("Tipo da série", "Normal", "Aquecimento", "não conta para recordes", "Assistida", "recordes de repetições à parte", "Remover série"),
            "en" to listOf("Set type", "Normal", "Warm-up", "doesn't count for records", "Assisted", "separate rep records", "Remove set"),
        )) {
            runComposeUiTest {
                val entry = entryOf(sets())
                val actions = Actions(entry)
                showList(entry, actions, locale = locale)
                tapNumber("s4")
                for (text in texts) onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                // The current type is the one selected.
                onNodeWithText(texts[1]).assertIsSelected()
                onNodeWithText(texts[2]).assertIsNotSelected()
                onNodeWithText(texts[4]).assertIsNotSelected()
                assertEquals(emptyList(), actions.calls, "opening the sheet writes nothing")
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun assistedIsHiddenForTheAssistanceConvention() = runComposeUiTest {
        // In the Assistance convention every set is assisted: none is marked, and Normal is selected.
        val entry = entryOf(sets(assistance = true), exercise = assistedPullUp)
        val actions = Actions(entry)
        showList(entry, actions)
        tapNumber("s4")
        onNodeWithText("Tipo da série").assertIsDisplayed()
        onNodeWithText("Aquecimento").assertIsDisplayed()
        onNodeWithText("Assistida").assertDoesNotExist()
        onNodeWithText("recordes de repetições à parte").assertDoesNotExist()
        onNodeWithText("Normal").assertIsSelected()
    }

    @Test
    fun choosingWarmupSendsWarmupAndClosesInTwoTaps() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry)
        showList(entry, actions)
        onNode(hasTestTag(SetRowTypeTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("s4"))), useUnmergedTree = true).assertDoesNotExist()
        tapNumber("s4")
        onNodeWithText("Aquecimento").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s4,6,85.0,false,true,false"), actions.calls)
        onNodeWithText("Tipo da série").assertDoesNotExist()
        // The echo marks the row as a warm-up.
        onNode(hasTestTag(SetRowTypeTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("s4"))), useUnmergedTree = true).assertExists()
        // Choosing the type already shown closes and writes nothing.
        tapNumber("s4")
        onNodeWithText("Aquecimento").assertIsSelected()
        onNodeWithText("Aquecimento").performClick()
        waitForIdle()
        onNodeWithText("Tipo da série").assertDoesNotExist()
        assertEquals(1, actions.calls.size, actions.calls.toString())
    }

    @Test
    fun normalInAssistanceConventionKeepsAssistance() = runComposeUiTest {
        // F29: a warm-up of an assisted exercise goes back to Normal still assisted.
        val entry = entryOf(sets(assistance = true).map { if (it.id == "s4") it.copy(warmup = true) else it }, exercise = assistedPullUp)
        val actions = Actions(entry)
        showList(entry, actions)
        tapNumber("s4")
        onNodeWithText("Aquecimento").assertIsSelected()
        onNodeWithText("Normal").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s4,6,85.0,false,false,true"), actions.calls)
        // And Warm-up there stays assisted too.
        tapNumber("s3")
        onNodeWithText("Aquecimento").performClick()
        waitForIdle()
        assertEquals("updateSet:s3,6,85.0,false,true,true", actions.calls.last())
    }

    @Test
    fun typeChangeUsesTheCurrentValues() = runComposeUiTest {
        // Without echoes: the load typed is written and still in flight when the type changes.
        val entry = entryOf(sets())
        val actions = Actions(entry, echo = false)
        showList(entry, actions)
        field("s4", SetRowLoadTag).performTextReplacement("90")
        waitForIdle()
        assertEquals(listOf("updateSet:s4,6,90.0,false,false,false"), actions.calls)
        tapNumber("s4")
        // The sheet looks the set up when the choice is made: a change from elsewhere meanwhile counts.
        entry.value = entry.value.copy(sets = entry.value.sets.map { if (it.id == "s4") it.copy(completed = true) else it })
        waitForIdle()
        onNodeWithText("Aquecimento").performClick()
        waitForIdle()
        assertEquals("updateSet:s4,6,90.0,true,true,false", actions.calls.last(), "not the 85 kg the database still shows")
        assertEquals(2, actions.calls.size, actions.calls.toString())
    }

    @Test
    fun removeFromTheSheetShowsUndo() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry)
        val shown = showList(entry, actions)
        tapNumber("s4")
        onNodeWithText("Remover série").performClick()
        waitForIdle()
        assertEquals(listOf("removeSet:s4"), actions.calls)
        onNodeWithText("Tipo da série").assertDoesNotExist()
        row("s4").assertDoesNotExist()
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Desfazer", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(RemovedSet("e1", 3, SetUi("s4", reps = 6, weightKg = 85.0), 4), shown.screen?.lastRemoved)
    }

    @Test
    fun undoRestoresTheSnapshot() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry)
        val shown = showList(entry, actions)
        tapNumber("s4")
        onNodeWithText("Remover série").performClick()
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        val last = actions.calls.last()
        assertTrue(last.startsWith("restoreSet:e1,3,") && "id=s4" in last, last)
        assertTrue("reps=6" in last && "weightKg=85.0" in last, last)
        row("s4").assertExists()
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertDoesNotExist()
        assertNull(shown.screen?.lastRemoved)
    }

    @Test
    fun swipeLeftRemovesWithUndo() = runComposeUiTest {
        val entry = entryOf(sets().map { if (it.id == "s4") it.copy(completed = true) else it })
        val actions = Actions(entry)
        showList(entry, actions)
        swipeLeft("s4")
        assertEquals(listOf("removeSet:s4"), actions.calls)
        row("s4").assertDoesNotExist()
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        // A completed set comes back completed.
        assertTrue(actions.calls.last().startsWith("restoreSet:e1,3,SetUi(id=s4, reps=6, weightKg=85.0, completed=true"), actions.calls.last())
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("s4")))).assertIsOn()
    }

    @Test
    fun anotherRemovalReplacesTheUndo() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry)
        val shown = showList(entry, actions)
        swipeLeft("s4")
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertIsDisplayed()
        swipeLeft("s2")
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Série 2 removida", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(2, shown.screen?.lastRemoved?.number)
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertEquals(listOf("removeSet:s4", "removeSet:s2"), actions.calls.dropLast(1))
        assertTrue(actions.calls.last().startsWith("restoreSet:e1,1,SetUi(id=s2"), actions.calls.last())
        // Only the last removal can be undone: s4 stays removed.
        row("s4").assertDoesNotExist()
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun undoSurvivesRecreation() = runComposeUiTest {
        // Review Focus 1, with a row in edit holding a draft. No forced language: the restoration
        // tester loses the saved state when the language override rebuilds the tree.
        val entry = entryOf(sets())
        val actions = Actions(entry, echo = false)
        val restoration = screenRestoration()
        val shown = showList(entry, actions, locale = null, restoration = restoration)
        field("s1", SetRowRepsTag).performTextReplacement("8")
        waitForIdle()
        swipeLeft("s4")
        onNodeWithText("Set 4 removed", useUnmergedTree = true).assertIsDisplayed()
        val editing = shown.screen?.editing
        assertEquals("s1", editing?.setId, "a row is in edit before the recreation")
        val removed = shown.screen?.lastRemoved

        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals(removed, shown.screen?.lastRemoved)
        assertEquals(editing, shown.screen?.editing)
        assertEquals("8", field("s1", SetRowRepsTag).editableText())
        onNodeWithText("Set 4 removed", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertTrue(actions.calls.last().startsWith("restoreSet:e1,3,SetUi(id=s4"), actions.calls.toString())
        assertEquals(1, actions.calls.count { it.startsWith("restoreSet:") }, actions.calls.toString())
        row("s4").assertExists()
    }

    @Test
    fun undoStaysForSixSeconds() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry)
        val shown = showList(entry, actions)
        mainClock.autoAdvance = false
        // The row's "Remove set" action, the screen reader's way to the same removal.
        val remove = row("s4").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Remover série" }
        runOnUiThread { remove.action() }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(5_900)
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertExists()
        onNodeWithText("Desfazer", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(MoosclesTiming().toastAction - 5_900 + MoosclesMotion().fast + 200L)
        onNodeWithText("Série 4 removida", useUnmergedTree = true).assertDoesNotExist()
        assertNull(shown.screen?.lastRemoved)
        assertEquals(listOf("removeSet:s4"), actions.calls, "no undo without the tap")
    }

    @Test
    fun undoDoesNotBringBackAStaleDraft() = runComposeUiTest {
        val entry = entryOf(sets())
        val actions = Actions(entry, echo = false)
        val shown = showList(entry, actions)
        // An invalid draft is held, not sent; a cleared reps field is marked.
        field("s4", SetRowLoadTag).performTextReplacement("82,")
        field("s4", SetRowRepsTag).performTextReplacement("")
        waitForIdle()
        assertEquals("82,", field("s4", SetRowLoadTag).editableText())
        swipeLeft("s4")
        val screen = shown.screen!!
        assertTrue(screen.drafts.keys.none { it.first == "s4" }, screen.drafts.toString())
        assertTrue(screen.cleared.none { it.first == "s4" }, screen.cleared.toString())
        assertNull(screen.editing)
        // The snapshot has the last valid values: the load as saved, the reps as written.
        assertEquals(SetUi("s4", reps = 0, weightKg = 85.0), screen.lastRemoved?.set)
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertEquals("85", field("s4", SetRowLoadTag).editableText())
        assertEquals(null, screen.drafts["s4" to SetField.LOAD])
    }

    @Test
    fun addSetShowsTheSuggestionFromAbove() = runComposeUiTest {
        val entry = entryOf(listOf(SetUi("s1", reps = 6, weightKg = 85.0, completed = true)))
        val actions = Actions(entry)
        showList(entry, actions)
        onNodeWithTag(ExerciseAddSetTag).performClick()
        waitForIdle()
        assertEquals(listOf("addSet:e1"), actions.calls)
        row("n1").assertIsDisplayed()
        // The new set has the load copied and no reps: the reps above are its suggestion.
        assertEquals("85", field("n1", SetRowLoadTag).editableText())
        assertEquals("", field("n1", SetRowRepsTag).editableText())
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("n1")))).performClick()
        waitForIdle()
        assertEquals("updateSet:n1,6,85.0,true,false,false", actions.calls.last())
    }

    @Test
    fun addSetIsDisabledAtOneHundred() {
        runComposeUiTest {
            val entry = entryOf(sets(count = MAX_SETS))
            val actions = Actions(entry)
            withScreen { AddSetFooter(entry.value, actions) }
            waitForIdle()
            onNodeWithTag(ExerciseAddSetTag).assertIsNotEnabled()
            onNodeWithTag(ExerciseAddSetTag).performClick()
            waitForIdle()
            assertEquals(emptyList(), actions.calls)
        }
        runComposeUiTest {
            val entry = entryOf(sets(count = MAX_SETS - 1))
            val actions = Actions(entry)
            withScreen { AddSetFooter(entry.value, actions) }
            waitForIdle()
            onNodeWithTag(ExerciseAddSetTag).assertIsEnabled()
            onNodeWithTag(ExerciseAddSetTag).performClick()
            waitForIdle()
            assertEquals(listOf("addSet:e1"), actions.calls)
        }
    }
}
