package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rest sheet of an exercise (spec D14): the stepper, the shortcuts, the note and "Start rest now". */
@OptIn(ExperimentalTestApi::class)
class RestSheetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")

    private fun entry(rest: Int = 120, id: String = "e1", exercise: ExerciseUi = bench) =
        WorkoutExerciseUi(id, exercise, sets = listOf(SetUi("$id-s1", reps = 8, weightKg = 80.0)), restSeconds = rest)

    /** Records every call; with [echo], `updateWorkoutExerciseRest` changes [exercises] as the database emission would. */
    private class Actions(
        val exercises: MutableState<List<WorkoutExerciseUi>>,
        var echo: Boolean = true,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) {
            recording.updateWorkoutExerciseRest(entryId, seconds)
            if (echo) setRest(exercises, entryId, seconds)
        }
    }

    /** The rest sheet host over [exercises], open for [open] until it closes itself (it then sets [open] to null). */
    private fun ComposeUiTest.showSheet(
        exercises: MutableState<List<WorkoutExerciseUi>>,
        actions: MoosclesActions,
        open: MutableState<String?> = mutableStateOf("e1"),
        locale: String = "pt-BR",
    ): MutableState<String?> {
        withScreen(locale = locale) {
            RestSheetHost(exercises.value, open.value, actions, onClose = { open.value = null })
        }
        waitForIdle()
        return open
    }

    private fun ComposeUiTest.value() = onNodeWithTag(StepperValueTag)

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun stepperMovesInFifteenSecondsFromZeroToFive() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 15)))
        val actions = Actions(exercises)
        showSheet(exercises, actions)
        value().assertTextEquals("0:15")
        onNodeWithTag(StepperDecreaseTag).assertIsEnabled()
        tap(StepperDecreaseTag)
        value().assertTextEquals("0:00")
        onNodeWithTag(StepperDecreaseTag).assertIsNotEnabled()
        tap(StepperIncreaseTag)
        value().assertTextEquals("0:15")
        // A change from elsewhere, with nothing in flight, shows.
        runOnIdle { setRest(exercises, "e1", 285) }
        waitForIdle()
        value().assertTextEquals("4:45")
        onNodeWithTag(StepperIncreaseTag).assertIsEnabled()
        tap(StepperIncreaseTag)
        value().assertTextEquals("5:00")
        onNodeWithTag(StepperIncreaseTag).assertIsNotEnabled()
        onNodeWithTag(StepperDecreaseTag).assertIsEnabled()
        assertEquals(
            listOf("updateWorkoutExerciseRest:e1,0", "updateWorkoutExerciseRest:e1,15", "updateWorkoutExerciseRest:e1,300"),
            actions.calls,
        )
    }

    @Test
    fun shortcutsSetTheRest() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises)
        showSheet(exercises, actions)
        assertEquals(listOf(60, 90, 120, 180), RestShortcuts)
        for ((seconds, text) in listOf(60 to "1:00", 90 to "1:30", 120 to "2:00", 180 to "3:00")) {
            onNodeWithTag(restShortcutTag(seconds)).assertIsDisplayed().assertTextEquals(text)
        }
        // The shortcut of the current rest is the one selected.
        onNodeWithTag(restShortcutTag(120)).assertIsSelected()
        onNodeWithTag(restShortcutTag(90)).assertIsNotSelected()
        tap(restShortcutTag(90))
        assertEquals(listOf("updateWorkoutExerciseRest:e1,90"), actions.calls)
        value().assertTextEquals("1:30")
        onNodeWithTag(restShortcutTag(90)).assertIsSelected()
        onNodeWithTag(restShortcutTag(120)).assertIsNotSelected()
        // The rest it already has writes nothing.
        tap(restShortcutTag(90))
        assertEquals(1, actions.calls.size, actions.calls.toString())
        // A step off every shortcut leaves none selected.
        tap(StepperIncreaseTag)
        for (seconds in RestShortcuts) onNodeWithTag(restShortcutTag(seconds)).assertIsNotSelected()
    }

    @Test
    fun zeroTurnsTheRestOff() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises)
        showSheet(exercises, actions)
        onNodeWithText("Iniciar descanso agora").assertIsEnabled()
        // Every step down is saved as it ends, down to 0:00.
        repeat(8) { tap(StepperDecreaseTag) }
        value().assertTextEquals("0:00")
        assertEquals((7 downTo 0).map { "updateWorkoutExerciseRest:e1,${it * 15}" }, actions.calls)
        assertEquals(0, exercises.value.single().restSeconds)
        // No rest to start (F19).
        onNodeWithText("Iniciar descanso agora").assertIsNotEnabled()
        tap(restShortcutTag(60))
        onNodeWithText("Iniciar descanso agora").assertIsEnabled()
    }

    @Test
    fun noteIsShown() {
        for ((locale, texts) in listOf(
            "pt-BR" to listOf(
                "Descanso", "Supino reto",
                "Vale para os próximos descansos deste exercício; o descanso atual mantém o próprio tempo.",
                "Iniciar descanso agora",
            ),
            "en" to listOf(
                "Rest", "Supino reto",
                "Applies to this exercise's next rests; the current rest keeps its own time.",
                "Start rest now",
            ),
        )) {
            runComposeUiTest {
                val exercises = mutableStateOf(listOf(entry()))
                val actions = Actions(exercises)
                showSheet(exercises, actions, locale = locale)
                for (text in texts) onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                assertEquals(emptyList(), actions.calls, "opening the sheet writes nothing")
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun startNowStartsTheTimer() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises, echo = false)
        val open = showSheet(exercises, actions)
        onNodeWithText("Iniciar descanso agora").performClick()
        waitForIdle()
        assertEquals(listOf("startTimer:Supino reto,120,e1"), actions.calls)
        // Starting the rest closes the sheet.
        assertNull(open.value)
        value().assertDoesNotExist()
        // It starts with the rest as the sheet shows it, before the database has it.
        runOnIdle { open.value = "e1" }
        waitForIdle()
        tap(restShortcutTag(90))
        onNodeWithText("Iniciar descanso agora").performClick()
        waitForIdle()
        assertEquals(
            listOf("startTimer:Supino reto,120,e1", "updateWorkoutExerciseRest:e1,90", "startTimer:Supino reto,90,e1"),
            actions.calls,
        )
    }

    @Test
    fun returningToTheSavedValueBeforeTheEcho() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises, echo = false)
        showSheet(exercises, actions)
        tap(StepperIncreaseTag)
        value().assertTextEquals("2:15")
        tap(StepperDecreaseTag)
        // Back to the saved value before any echo: it shows, and it is written, since 2:15 was.
        value().assertTextEquals("2:00")
        assertEquals(listOf("updateWorkoutExerciseRest:e1,135", "updateWorkoutExerciseRest:e1,120"), actions.calls)
        // The late echo of 2:15 does not bring it back; the echo of 2:00 settles it.
        runOnIdle { setRest(exercises, "e1", 135) }
        waitForIdle()
        value().assertTextEquals("2:00")
        runOnIdle { setRest(exercises, "e1", 120) }
        waitForIdle()
        value().assertTextEquals("2:00")
        // With nothing in flight, a change from elsewhere shows.
        runOnIdle { setRest(exercises, "e1", 150) }
        waitForIdle()
        value().assertTextEquals("2:30")
        assertEquals(2, actions.calls.size, actions.calls.toString())
    }

    @Test
    fun openingAndChangingDoesNotTouchTheRunningTimer() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises)
        val open = mutableStateOf<String?>(null)
        showSheet(exercises, actions, open)
        runOnIdle { open.value = "e1" }
        waitForIdle()
        value().assertTextEquals("2:00")
        assertEquals(emptyList(), actions.calls, "opening writes nothing")
        tap(StepperIncreaseTag)
        tap(restShortcutTag(60))
        tap(StepperDecreaseTag)
        // Only the exercise's rest is written: the rest running now keeps its own time (D14).
        assertEquals(
            listOf("updateWorkoutExerciseRest:e1,135", "updateWorkoutExerciseRest:e1,60", "updateWorkoutExerciseRest:e1,45"),
            actions.calls,
        )
        val timerMethods = listOf("startTimer", "pauseTimer", "resumeTimer", "resetTimer", "skipTimer", "adjustTimer")
        assertTrue(actions.calls.none { call -> timerMethods.any { call.startsWith("$it:") } }, actions.calls.toString())
    }

    @Test
    fun sheetSurvivesCardDisposalAndClosesWhenItsExerciseIsRemoved() = runComposeUiTest {
        // Ported from InlineRestEditorHostTest.activeWorkoutDialogSurvivesLazyCardDisposalAndClosesIfItsExerciseIsRemoved.
        val first = entry(rest = 165, id = "bench")
        val others = List(10) { i -> entry(rest = 75, id = "other-$i", exercise = bench.copy(id = "x$i", name = "Other movement $i")) }
        val exercises = mutableStateOf(listOf(first) + others)
        val actions = Actions(exercises)
        val open = mutableStateOf<String?>(null)
        withScreen(locale = "pt-BR") {
            Column {
                // The cards of a long workout, as the screen lays them out: the chip opens the sheet.
                LazyColumn(Modifier.height(350.dp).testTag("list")) {
                    items(exercises.value, key = { it.id }) { e ->
                        Box(Modifier.fillMaxWidth().height(200.dp).testTag("card-${e.id}")) {
                            MButton("Descanso", onClick = { open.value = e.id }, modifier = Modifier.testTag("chip-${e.id}"))
                        }
                    }
                }
            }
            RestSheetHost(exercises.value, open.value, actions, onClose = { open.value = null })
        }
        waitForIdle()
        tap("chip-bench")
        value().assertTextEquals("2:45")
        tap(StepperDecreaseTag)
        value().assertTextEquals("2:30")
        // Scroll the list until the first card is disposed, as an IME resize of the viewport also can.
        onNodeWithTag("list").performScrollToIndex(others.size)
        waitForIdle()
        onNodeWithTag("card-bench").assertDoesNotExist()
        value().assertTextEquals("2:30")
        tap(StepperDecreaseTag)
        value().assertTextEquals("2:15")
        assertEquals(listOf("updateWorkoutExerciseRest:bench,150", "updateWorkoutExerciseRest:bench,135"), actions.calls)
        assertEquals(135, exercises.value.first().restSeconds)
        // The exercise leaves the workout (removed here or elsewhere): the sheet closes by itself.
        runOnIdle { exercises.value = exercises.value.drop(1) }
        waitForIdle()
        value().assertDoesNotExist()
        assertNull(open.value)
        assertEquals(2, actions.calls.size, actions.calls.toString())
    }

    @Test
    fun recreationKeepsOnlyTheSettledRest() = runComposeUiTest {
        // No forced language: the restoration tester loses the saved state when the language override rebuilds the tree.
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises)
        val open = mutableStateOf<String?>("e1")
        val restoration = screenRestoration()
        withScreen(restoration = restoration) {
            RestSheetHost(exercises.value, open.value, actions, onClose = { open.value = null })
        }
        waitForIdle()
        tap(StepperIncreaseTag)
        value().assertTextEquals("2:15")
        assertEquals(135, exercises.value.single().restSeconds, "2:15 reached the database")
        // 2:30 is written, but the process dies before it lands.
        actions.echo = false
        tap(StepperIncreaseTag)
        value().assertTextEquals("2:30")

        restoration.emulateSaveAndRestore()
        waitForIdle()
        // The sheet is back with the rest the database holds; the lost write is not awaited.
        value().assertTextEquals("2:15")
        tap(StepperIncreaseTag)
        value().assertTextEquals("2:30")
        assertEquals(
            listOf("updateWorkoutExerciseRest:e1,135", "updateWorkoutExerciseRest:e1,150", "updateWorkoutExerciseRest:e1,150"),
            actions.calls,
            "asked again after the recreation, the sheet writes 2:30 again",
        )
    }

    @Test
    fun withoutStartNowTheSheetHasNoButtonAndStillSaves() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(entry(rest = 120)))
        val actions = Actions(exercises)
        withScreen(locale = "pt-BR") {
            RestSheet(exercises.value.single(), actions, onDismiss = {}, showStartNow = false)
        }
        waitForIdle()
        // The routine editor's form: the stepper and the shortcuts, no "Start rest now", and the timer is never touched.
        value().assertTextEquals("2:00")
        onNodeWithText("Iniciar descanso agora").assertDoesNotExist()
        tap(StepperIncreaseTag)
        value().assertTextEquals("2:15")
        assertEquals(listOf("updateWorkoutExerciseRest:e1,135"), actions.calls)
    }

    private companion object {
        fun setRest(exercises: MutableState<List<WorkoutExerciseUi>>, entryId: String, seconds: Int) {
            exercises.value = exercises.value.map { if (it.id == entryId) it.copy(restSeconds = seconds) else it }
        }
    }
}
