package com.mooscles.screens.workoutdetail

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import com.mooscles.screens.flow.capture
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.ExercisePickerListTag
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.UiError
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Saved-workout parity, separate from active-workout effects and Android navigation hosting. */
@OptIn(ExperimentalTestApi::class)
class WorkoutDetailScreenTest {
    @AfterTest fun resetLocale() { LocalAppLocale.restore() }

    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", "Total load", custom = true)
    private val row = ExerciseUi("row", "Row", "Back", "Dumbbell", "Per hand", custom = true)
    private val pullup = ExerciseUi("pullup", "Pull-up", "Back", "Bodyweight", "Added weight", custom = true)
    private val record = RecordUi(bench, 22.0, 6)
    private fun workout() = WorkoutUi("saved", "Upper A", 1_730_224_920_000, 1_730_227_440_000,
        listOf(
            WorkoutExerciseUi("e1", bench, listOf(SetUi("s1", 6, 22.0, true), SetUi("s2", 0)), restSeconds = 120),
            WorkoutExerciseUi("e2", row, listOf(SetUi("s3", 8, 12.0, true)), restSeconds = 180),
        ), listOf(record), sourceRoutineId = "routine")

    private fun state() = mutableStateOf(UiState(loading = false, exercises = listOf(bench, row, pullup),
        routines = listOf(RoutineUi("routine", "Upper")), history = listOf(workout()),
        // A different active session must never become this screen's editing target.
        active = WorkoutUi("active", "Active", 0, exercises = listOf(WorkoutExerciseUi("active-e", bench, listOf(SetUi("active-s", 5, 30.0)))))))

    private class Actions(val state: MutableState<UiState>, val recording: RecordingActions = RecordingActions()) : MoosclesActions by recording {
        val calls get() = recording.calls
        private fun change(block: (WorkoutUi) -> WorkoutUi) { state.value = state.value.copy(history = state.value.history.map(block)) }
        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            change { w -> w.copy(exercises = w.exercises.map { entry -> entry.copy(sets = entry.sets.map { set ->
                if (set.id == setId) set.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else set
            }) }) }
        }
        override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) {
            recording.updateWorkoutExerciseRest(entryId, seconds)
            change { w -> w.copy(exercises = w.exercises.map { if (it.id == entryId) it.copy(restSeconds = seconds) else it }) }
        }
        override fun removeSet(setId: String) {
            recording.removeSet(setId)
            change { w -> w.copy(exercises = w.exercises.map { it.copy(sets = it.sets.filterNot { it.id == setId }) }) }
        }
        override fun removeWorkoutExercise(entryId: String) {
            recording.removeWorkoutExercise(entryId)
            change { it.copy(exercises = it.exercises.filterNot { e -> e.id == entryId }) }
        }
        override fun renameWorkout(id: String, name: String) {
            recording.renameWorkout(id, name)
            change { if (it.id == id) it.copy(name = name) else it }
        }
        override fun deleteWorkout(id: String) {
            recording.deleteWorkout(id)
            state.value = state.value.copy(history = state.value.history.filterNot { it.id == id })
        }
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = state(),
        actions: MoosclesActions = Actions(state),
        editing: Boolean = false,
        locale: String = "en",
        fontScale: Float = 1f,
        calls: MutableList<String> = mutableListOf(),
        haptics: RecordingHaptics = RecordingHaptics(),
    ): WorkoutDetailScreenState {
        val screen = WorkoutDetailScreenState(editing)
        withScreen(locale = locale, fontScale = fontScale, size = DpSize(360.dp, 640.dp), haptics = haptics, reducedMotion = true) {
            WorkoutDetailRoute("saved", state.value, actions, onBack = { calls += "back" },
                onExerciseProgress = { calls += "progress:$it" }, onRoutine = { calls += "routine:$it" },
                onShare = { calls += "share:$it" }, screen = screen)
        }
        waitForIdle()
        return screen
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
    private fun ComposeUiTest.rowControl(id: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id))))
    private fun ComposeUiTest.field(id: String, tag: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id))), useUnmergedTree = true)
    private fun ComposeUiTest.openType(id: String) {
        val action = onNodeWithTag(workoutSetRowTag(id)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Change type" }
        runOnIdle { assertTrue(action.action()) }; waitForIdle()
    }

    @Test fun readOnlyHeaderIncludesFirstRecordsBesideSetsAndSeparateSection() = runComposeUiTest {
        show()
        onNodeWithTag(WorkoutDetailTags.PRS).assertContentDescriptionEquals("1 exercise with a personal record")
        scrollTo(WorkoutDetailTags.FIRST)
        onNodeWithTag(WorkoutDetailTags.FIRST).assertIsDisplayed()
        onNodeWithTag(WorkoutDetailTags.RECORDS).assertDoesNotExist()
    }

    @Test fun editRequiresConfirmationAndDoneLeavesTheMode() = runComposeUiTest {
        val screen = show()
        scrollTo(WorkoutDetailTags.EDIT)
        onNodeWithTag(WorkoutDetailTags.EDIT).performClick()
        assertFalse(screen.editing)
        onNodeWithText("Edit saved workout?").assertIsDisplayed()
        onAllNodesWithText("Edit workout").onLast().performClick(); waitForIdle()
        assertTrue(screen.editing)
        onNodeWithTag(WorkoutDetailTags.DONE).performClick(); waitForIdle()
        assertFalse(screen.editing)
    }

    @Test fun hostedBackLeavingEditModeDoesNotReopenThePreviousKeyboard() = runComposeUiTest {
        val state = state(); val screen = show(state, Actions(state), editing = true)
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).performClick(); field("s1", SetRowLoadTag).performClick()
        runOnIdle { screen.finishEditing() }; waitForIdle()
        runOnIdle { screen.beginEditing() }; waitForIdle()
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).assertIsNotFocused()
    }

    @Test fun savedRowsEditLoadAndRepsWithoutTouchingAnotherActiveWorkout() = runComposeUiTest {
        val state = state(); val active = state.value.active; val actions = Actions(state)
        show(state, actions, editing = true)
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).performClick(); field("s1", SetRowLoadTag).performClick()
        field("s1", SetRowLoadTag).performTextReplacement("22.5")
        field("s1", SetRowRepsTag).performClick(); field("s1", SetRowRepsTag).performTextReplacement("7")
        waitForIdle()
        assertEquals(22.5, state.value.history.single().exercises.first().sets.first().weightKg)
        assertEquals(7, state.value.history.single().exercises.first().sets.first().reps)
        assertEquals(active, state.value.active)
        assertTrue(actions.calls.none { it.startsWith("startTimer:") || it.startsWith("finishWorkout:") || it.startsWith("discardWorkout:") })
    }

    @Test fun completionAndUncompletionKeepSavedValuesAndHaveNoTimerCommand() = runComposeUiTest {
        val state = state(); val actions = Actions(state); val haptics = RecordingHaptics()
        show(state, actions, editing = true, haptics = haptics)
        scrollTo(workoutSetRowTag("s1"))
        rowControl("s1", SetRowCheckTag).performClick(); waitForIdle()
        assertFalse(state.value.history.single().exercises.first().sets.first().completed)
        rowControl("s1", SetRowCheckTag).performClick(); waitForIdle()
        assertTrue(state.value.history.single().exercises.first().sets.first().completed)
        assertTrue(actions.calls.filter { it.startsWith("updateSet:") }.all { it.startsWith("updateSet:s1,6,22.0,") })
        assertTrue(actions.calls.none { it.startsWith("startTimer:") })
        assertTrue(haptics.events.none { it == HapticEvent.PersonalRecord || it == HapticEvent.RestFinished })
    }

    @Test fun editingOnlyRepsInPoundsKeepsExactKilogramsAndBlankFieldsStayBlank() = runComposeUiTest {
        val state = state()
        val exact = 22.123456789
        state.value = state.value.copy(preferences = state.value.preferences.copy(pounds = true), history = listOf(
            workout().copy(exercises = workout().exercises.map { entry ->
                if (entry.id == "e1") entry.copy(sets = entry.sets.map { if (it.id == "s1") it.copy(weightKg = exact) else it }) else entry
            }),
        ))
        val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowRepsTag).performClick(); field("s1", SetRowRepsTag).performClick()
        field("s1", SetRowRepsTag).performTextReplacement("7"); waitForIdle()
        assertEquals(exact, state.value.history.single().exercises.first().sets.first().weightKg)
        scrollTo(workoutSetRowTag("s2"))
        field("s2", SetRowLoadTag).assertTextEquals("")
        field("s2", SetRowRepsTag).assertTextEquals("")
    }

    @Test fun typeSheetChangesWarmupAssistedAndNormalWithoutChangingLoad() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(workoutSetRowTag("s1"))
        openType("s1"); onNodeWithText("Warm-up").performClick(); waitForIdle()
        assertTrue(state.value.history.single().exercises.first().sets.first().warmup)
        openType("s1"); onNodeWithText("Assisted").performClick(); waitForIdle()
        val assisted = state.value.history.single().exercises.first().sets.first()
        assertFalse(assisted.warmup); assertTrue(assisted.assistance)
        openType("s1"); onNodeWithText("Normal").performClick(); waitForIdle()
        val normal = state.value.history.single().exercises.first().sets.first()
        assertFalse(normal.assistance); assertEquals(22.0, normal.weightKg)
    }

    @Test fun setRemovalRequiresConfirmationAndOffersNoUndo() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(workoutSetRowTag("s1"))
        openType("s1"); onNodeWithText("Remove set").performClick(); waitForIdle()
        assertTrue(actions.calls.none { it.startsWith("removeSet:") })
        onNodeWithText("Cancel").performClick(); waitForIdle()
        assertEquals(2, state.value.history.single().exercises.first().sets.size)
        openType("s1"); onNodeWithText("Remove set").performClick(); waitForIdle()
        onNodeWithText("Remove").performClick(); waitForIdle()
        assertEquals(listOf("removeSet:s1"), actions.calls.filter { it.startsWith("removeSet:") })
        assertEquals(listOf("s2"), state.value.history.single().exercises.first().sets.map { it.id })
        onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test fun failedSetRemovalPreservesItsUnsavedDraft() = runComposeUiTest {
        val state = state(); val recording = RecordingActions(); show(state, recording, editing = true)
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).performClick(); field("s1", SetRowLoadTag).performClick()
        field("s1", SetRowLoadTag).performTextReplacement("22,")
        openType("s1"); onNodeWithText("Remove set").performClick(); waitForIdle()
        onNodeWithText("Remove").performClick(); waitForIdle()
        runOnIdle { state.value = state.value.copy(errorCode = UiError.SAVE_FAILED) }; waitForIdle()
        assertEquals(listOf("removeSet:s1"), recording.calls.filter { it.startsWith("removeSet:") })
        field("s1", SetRowLoadTag).assertTextEquals("22,")
        assertEquals(22.0, state.value.history.single().exercises.first().sets.first().weightKg)
    }

    @Test fun restEditsOnlyThatExerciseAndNeverOffersStartRestNow() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(WorkoutDetailTags.rest("e1")); onNodeWithTag(WorkoutDetailTags.rest("e1")).performClick(); waitForIdle()
        onNodeWithTag(StepperValueTag).assertTextEquals("2:00")
        onNodeWithText("Start rest now").assertDoesNotExist()
        onNodeWithTag(StepperIncreaseTag).performClick(); waitForIdle()
        assertEquals(120, state.value.history.single().exercises.first().restSeconds)
        onNodeWithTag("detail-rest-save").performClick(); waitForIdle()
        assertEquals(135, state.value.history.single().exercises.first().restSeconds)
        assertEquals(180, state.value.history.single().exercises.last().restSeconds)
        assertTrue(actions.calls.none { it.startsWith("startTimer:") })
    }

    @Test fun preciseRestRetainsArbitrarySecondsAndCancelDiscardsTheDraft() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(WorkoutDetailTags.rest("e1")); onNodeWithTag(WorkoutDetailTags.rest("e1")).performClick(); waitForIdle()
        onNodeWithTag("detail-rest-input").performTextReplacement("3:05"); waitForIdle()
        onNodeWithTag(StepperValueTag).assertTextEquals("3:05")
        assertEquals(120, state.value.history.single().exercises.first().restSeconds)
        onNodeWithTag("detail-rest-cancel").performClick(); waitForIdle()
        assertTrue(actions.calls.none { it.startsWith("updateWorkoutExerciseRest:") })
        onNodeWithTag(WorkoutDetailTags.rest("e1")).performClick(); waitForIdle()
        onNodeWithTag("detail-rest-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("2:00")))
        onNodeWithTag("detail-rest-input").performTextReplacement("3:05")
        onNodeWithTag("detail-rest-save").performClick(); waitForIdle()
        assertEquals(listOf("updateWorkoutExerciseRest:e1,185"), actions.calls.filter { it.startsWith("updateWorkoutExerciseRest:") })
        assertEquals(185, state.value.history.single().exercises.first().restSeconds)
    }

    @Test fun preciseRestRejectsTimesAboveFiveMinutesWithoutWriting() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(WorkoutDetailTags.rest("e1")); onNodeWithTag(WorkoutDetailTags.rest("e1")).performClick(); waitForIdle()
        onNodeWithTag("detail-rest-input").performTextReplacement("5:00"); waitForIdle()
        onNodeWithTag(StepperValueTag).assertTextEquals("5:00")
        onNodeWithTag("detail-rest-input").performTextReplacement("5:01"); waitForIdle()
        onNodeWithTag("detail-rest-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("5:00")))
        assertTrue(actions.calls.none { it.startsWith("updateWorkoutExerciseRest:") })
        onNodeWithTag("detail-rest-input").performTextReplacement("3:05")
        onNodeWithTag("detail-rest-save").performClick(); waitForIdle()
        assertEquals(185, state.value.history.single().exercises.first().restSeconds)
    }

    @Test fun exerciseMenuMovesAndRemovesOnlyAfterConfirmation() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo("detail-exercise-menu-e1"); onNodeWithTag("detail-exercise-menu-e1").performClick()
        onNodeWithTag("detail-exercise-down").performClick(); waitForIdle()
        assertTrue("moveWorkoutExercise:e1,1" in actions.calls)
        onNodeWithTag("detail-exercise-menu-e1").performClick(); onNodeWithTag("detail-exercise-remove").performClick(); waitForIdle()
        assertTrue(actions.calls.none { it.startsWith("removeWorkoutExercise:") })
        onNodeWithText("Remove").performClick(); waitForIdle()
        assertTrue("removeWorkoutExercise:e1" in actions.calls)
        assertEquals(listOf("e2"), state.value.history.single().exercises.map { it.id })
    }

    @Test fun addingASetTargetsTheSavedEntry() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo("detail-add-set-e1")
        onNodeWithTag("detail-add-set-e1").performClick(); waitForIdle()
        assertTrue("addSet:e1" in actions.calls)
        assertTrue(actions.calls.none { it == "addSet:active-e" })
    }

    @Test fun addingAnExerciseUsesTheSavedWorkoutId() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo(WorkoutDetailTags.ADD_EXERCISE); onNodeWithTag(WorkoutDetailTags.ADD_EXERCISE).performClick(); waitForIdle()
        val tag = exercisePickerRowTag("pullup", recent = false)
        onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).performClick(); waitForIdle()
        assertTrue("addWorkoutExercise:saved,pullup" in actions.calls)
        assertTrue(actions.calls.none { it.startsWith("addWorkoutExercise:active,") })
    }

    @Test fun replacingAnExerciseRequiresConfirmationAndUsesItsEntryId() = runComposeUiTest {
        val state = state(); val actions = Actions(state); show(state, actions, editing = true)
        scrollTo("detail-exercise-menu-e1"); onNodeWithTag("detail-exercise-menu-e1").performClick()
        onNodeWithTag("detail-exercise-replace").performClick(); waitForIdle()
        assertTrue(actions.calls.none { it.startsWith("replaceWorkoutExercise:") })
        onNodeWithText("Replace exercise").performClick(); waitForIdle()
        val tag = exercisePickerRowTag("pullup", recent = false)
        onNodeWithTag(ExercisePickerListTag).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).performClick(); waitForIdle()
        assertTrue("replaceWorkoutExercise:e1,pullup" in actions.calls)
    }

    @Test fun renameAndShareKeepTheSavedWorkoutIdentity() = runComposeUiTest {
        val state = state(); val actions = Actions(state); val navigation = mutableListOf<String>()
        show(state, actions, calls = navigation)
        onNodeWithTag(WorkoutDetailTags.MENU).performClick(); onNodeWithTag(WorkoutDetailTags.RENAME).performClick(); waitForIdle()
        onNodeWithTag("detail-name").performTextReplacement("My renamed workout")
        onNodeWithText("Save").performClick(); waitForIdle()
        assertTrue("renameWorkout:saved,My renamed workout" in actions.calls)
        scrollTo("share-workout"); onNodeWithTag("share-workout").performClick(); waitForIdle()
        assertEquals(listOf("share:saved"), navigation)
    }

    @Test fun deletingWaitsForTheActualRemovalBeforeReturning() = runComposeUiTest {
        val state = state(); val actions = Actions(state); val navigation = mutableListOf<String>()
        show(state, actions, calls = navigation)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick(); waitForIdle()
        assertTrue(navigation.isEmpty()); assertEquals(1, state.value.history.size)
        onAllNodesWithText("Delete workout").onLast().performClick(); waitForIdle()
        assertEquals(listOf("back"), navigation)
        assertTrue(state.value.history.isEmpty())
    }

    @Test fun repeatedDeleteConfirmationDispatchesOnlyOneCommandBeforeItsEcho() = runComposeUiTest {
        val state = state(); val recording = RecordingActions()
        show(state, recording)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick(); waitForIdle()
        val confirm = onNodeWithTag("dialog-confirm-danger").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        runOnIdle { assertTrue(confirm()); assertTrue(confirm()) }; waitForIdle()
        assertEquals(listOf("deleteWorkout:saved"), recording.calls.filter { it.startsWith("deleteWorkout:") })
        assertEquals(1, state.value.history.size)
    }

    @Test fun missingWorkoutShowsTheHistoryReturnAction() = runComposeUiTest {
        val state = state().also { it.value = it.value.copy(history = emptyList()) }
        val navigation = mutableListOf<String>(); show(state, calls = navigation)
        onNodeWithTag(WorkoutDetailTags.MISSING).assertIsDisplayed()
        onNodeWithText("Back to History").performClick(); assertEquals(listOf("back"), navigation)
    }

    @Test fun failedDeleteKeepsTheScreenAndReleasesItsControl() = runComposeUiTest {
        val state = state(); val recording = RecordingActions(); val navigation = mutableListOf<String>()
        show(state, recording, calls = navigation)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick()
        onAllNodesWithText("Delete workout").onLast().performClick(); waitForIdle()
        runOnIdle { state.value = state.value.copy(subjectFailures = mapOf("saved" to 1), errorCode = UiError.SAVE_FAILED) }
        waitForIdle()
        assertTrue(navigation.isEmpty())
        assertEquals(1, state.value.history.size)
        onNodeWithTag(WorkoutDetailTags.DELETE).performClick()
        onNodeWithText("Delete this workout?").assertIsDisplayed()
    }

    @Test fun deleteCanBeRetriedAfterItsEchoWaitExpires() = runComposeUiTest {
        val state = state(); val recording = RecordingActions()
        show(state, recording)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick()
        onNodeWithTag("dialog-confirm-danger").performClick(); waitForIdle()
        mainClock.advanceTimeBy(MoosclesTiming().saveEchoWait + 100)
        waitForIdle()
        onNodeWithTag(WorkoutDetailTags.DELETE).assertIsEnabled().performClick()
        onNodeWithTag("dialog-confirm-danger").performClick(); waitForIdle()
        assertEquals(listOf("deleteWorkout:saved", "deleteWorkout:saved"), recording.calls.filter { it.startsWith("deleteWorkout:") })
    }

    @Test fun aLateDeleteEchoStillReturnsAfterItsEchoWaitExpires() = runComposeUiTest {
        val state = state(); val recording = RecordingActions(); val navigation = mutableListOf<String>()
        show(state, recording, calls = navigation)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick()
        onNodeWithTag("dialog-confirm-danger").performClick(); waitForIdle()
        mainClock.advanceTimeBy(MoosclesTiming().saveEchoWait + 100)
        waitForIdle()
        assertTrue(navigation.isEmpty())
        runOnIdle { state.value = state.value.copy(history = emptyList()) }; waitForIdle()
        assertEquals(listOf("back"), navigation)
        assertEquals(listOf("deleteWorkout:saved"), recording.calls.filter { it.startsWith("deleteWorkout:") })
    }

    @Test fun anUnrelatedFailureDoesNotReleaseDeleteProtectionOrLoseItsRemovalEcho() = runComposeUiTest {
        val state = state(); val recording = RecordingActions(); val navigation = mutableListOf<String>()
        show(state, recording, calls = navigation)
        scrollTo(WorkoutDetailTags.DELETE); onNodeWithTag(WorkoutDetailTags.DELETE).performClick()
        onNodeWithTag("dialog-confirm-danger").performClick(); waitForIdle()
        onNodeWithTag(WorkoutDetailTags.DELETE).assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = "active-s",
            subjectFailures = mapOf("active-s" to 1)) }; waitForIdle()
        onNodeWithTag(WorkoutDetailTags.DELETE).assertIsNotEnabled()
        assertTrue(navigation.isEmpty())
        runOnIdle { state.value = state.value.copy(history = emptyList()) }; waitForIdle()
        assertEquals(listOf("back"), navigation)
        assertEquals(listOf("deleteWorkout:saved"), recording.calls.filter { it.startsWith("deleteWorkout:") })
    }

    @Test fun oneFailedSetDoesNotDiscardAnotherSetsPendingCompletion() = runComposeUiTest {
        val state = state(); val recording = RecordingActions()
        show(state, recording, editing = true)
        scrollTo(workoutSetRowTag("s1")); rowControl("s1", SetRowCheckTag).performClick(); waitForIdle()
        rowControl("s1", SetRowCheckTag).assertContentDescriptionEquals("Complete set 1")
        scrollTo(workoutSetRowTag("s3")); rowControl("s3", SetRowCheckTag).performClick(); waitForIdle()
        rowControl("s3", SetRowCheckTag).assertContentDescriptionEquals("Complete set 1")
        runOnIdle { state.value = state.value.copy(errorCode = UiError.SAVE_FAILED, errorSubject = "s1",
            subjectFailures = mapOf("s1" to 1)) }; waitForIdle()
        rowControl("s3", SetRowCheckTag).assertContentDescriptionEquals("Complete set 1")
        scrollTo(workoutSetRowTag("s1")); rowControl("s1", SetRowCheckTag).assertContentDescriptionEquals("Set 1 completed")
    }

    @Test fun historicalFailureCountersDoNotDiscardRestoredPendingValues() = runComposeUiTest {
        val state = state().also { it.value = it.value.copy(subjectFailures = mapOf("s1" to 1)) }
        val recording = RecordingActions(); val restoration = screenRestoration()
        // Match the shared restoration fixture: a forced locale rebuilds its root and discards saved state.
        withScreen(locale = null, restoration = restoration, size = DpSize(360.dp, 640.dp), reducedMotion = true) {
            WorkoutDetailRoute("saved", state.value, recording, {}, {}, {}, {}, screen = WorkoutDetailScreenState(true))
        }
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).performClick(); field("s1", SetRowLoadTag).performClick()
        field("s1", SetRowLoadTag).performTextReplacement("22.5")
        field("s1", SetRowLoadTag).performTextReplacement("22,"); waitForIdle()
        onNodeWithTag(StepperValueTag).assertTextEquals("22.5\u00A0kg")
        restoration.emulateSaveAndRestore(); waitForIdle()
        scrollTo(workoutSetRowTag("s1")); field("s1", SetRowLoadTag).assertTextEquals("22,")
        onNodeWithTag(StepperValueTag).assertTextEquals("22.5\u00A0kg")
        assertEquals(listOf("updateSet:s1,6,22.5,true,false,false"), recording.calls.filter { it.startsWith("updateSet:") })
    }

    @Test fun editModeAndInvalidDraftSurviveRecreation() = runComposeUiTest {
        val state = state(); val actions = Actions(state); val restoration = screenRestoration()
        // The platform's current English locale avoids rebuilding the restoration root just for a test override.
        withScreen(locale = null, restoration = restoration, size = DpSize(360.dp, 640.dp), reducedMotion = true) {
            WorkoutDetailRoute("saved", state.value, actions, {}, {}, {}, {})
        }
        scrollTo(WorkoutDetailTags.EDIT); onNodeWithTag(WorkoutDetailTags.EDIT).performClick()
        onAllNodesWithText("Edit workout").onLast().performClick(); waitForIdle()
        onNodeWithTag(WorkoutDetailTags.DONE).assertIsDisplayed()
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).performClick(); field("s1", SetRowLoadTag).performClick()
        field("s1", SetRowLoadTag).performTextReplacement("22,")
        restoration.emulateSaveAndRestore(); waitForIdle()
        capture("phase5b-detail-restored-edit")
        onNodeWithTag(WorkoutDetailTags.DONE).assertIsDisplayed()
        scrollTo(workoutSetRowTag("s1"))
        field("s1", SetRowLoadTag).assertTextEquals("22,")
        assertEquals(22.0, state.value.history.single().exercises.first().sets.first().weightKg)
    }

    @Test fun portugueseLargeFontKeepsTheInclusivePrDescriptionAndCustomName() = runComposeUiTest {
        show(locale = "pt-BR", fontScale = 2f)
        scrollTo(WorkoutDetailTags.PRS)
        onNodeWithTag(WorkoutDetailTags.PRS).assertContentDescriptionEquals("1 exercício com recorde pessoal")
        scrollTo(WorkoutDetailTags.exercise("e1"))
        onNodeWithText("Bench press").assertIsDisplayed()
    }
}
