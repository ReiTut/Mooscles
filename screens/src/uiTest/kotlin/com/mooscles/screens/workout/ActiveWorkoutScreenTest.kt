package com.mooscles.screens.workout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseCardSublineTag
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerOpenTag
import com.mooscles.designsystem.components.TimerTitleTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.ScreenFrame
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RoutineTargetUi
import com.mooscles.ui.TargetSetUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The active workout screen as the `workout` route shows it (spec "Tela do treino ativo",
 * "Finalizar, descartar e resumo", Review Focus 4): the header and its clock, the end of the list,
 * finishing, discarding, renaming, no workout, the placeholders while it opens and the errors.
 */
@OptIn(ExperimentalTestApi::class)
class ActiveWorkoutScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val exercises = listOf(
        ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load"),
        ExerciseUi("row", "Remada curvada", "Back", "Barbell", convention = "Total load"),
        ExerciseUi("press", "Desenvolvimento", "Shoulders", "Dumbbell", convention = "Per hand"),
        ExerciseUi("curl", "Rosca direta", "Biceps", "Barbell", convention = "Total load"),
    )

    /** A workout of four exercises with [sets] sets each, the first [done] of each done. */
    private fun workout(sets: List<Int>, done: List<Int>, name: String = "Treino A") = WorkoutUi(
        id = "w1",
        name = name,
        startedAt = START,
        exercises = sets.indices.map { i ->
            WorkoutExerciseUi(
                id = "e${i + 1}",
                exercise = exercises[i],
                sets = (1..sets[i]).map { n -> SetUi("e${i + 1}s$n", reps = 8, weightKg = 60.0, completed = n <= done[i]) },
            )
        },
    )

    /** 9 of 15 sets done. */
    private val nineOfFifteen get() = workout(sets = listOf(4, 4, 4, 3), done = listOf(3, 3, 2, 1))

    /** 9 of 12 sets done, across the 4 exercises: 3 left out. */
    private val nineOfTwelve get() = workout(sets = listOf(3, 3, 3, 3), done = listOf(3, 2, 2, 2))

    /** One of four sets done in the first exercise, which stays the open one after a removal. */
    private val oneDoneFirst get() = workout(sets = listOf(4, 4, 4, 3), done = listOf(1, 3, 2, 1))

    /** Nothing done yet. */
    private val nothingDone get() = workout(sets = listOf(3, 3, 3, 3), done = listOf(0, 0, 0, 0))

    /**
     * Records every call and changes [state] the way the view model would: `updateSet` (with
     * [echo]) and `removeSet` change the active workout, and `clearError` clears the error.
     * Finishing changes nothing by itself: the tests decide what the state does then.
     */
    private class Fake(
        initial: UiState,
        val echo: Boolean = true,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val state: MutableState<UiState> = mutableStateOf(initial)
        val calls: List<String> get() = recording.calls

        private fun changeSets(change: (List<SetUi>) -> List<SetUi>) {
            val active = state.value.active ?: return
            state.value = state.value.copy(active = active.copy(exercises = active.exercises.map { it.copy(sets = change(it.sets)) }))
        }

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            if (echo) {
                changeSets { sets ->
                    sets.map { if (it.id == setId) it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else it }
                }
            }
        }

        override fun removeSet(setId: String) {
            recording.removeSet(setId)
            changeSets { sets -> sets.filter { it.id != setId } }
        }

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null)
        }

        /** The view model reporting a failure. */
        fun fail(code: UiError, message: String = "raw message") {
            state.value = state.value.copy(error = message, errorCode = code)
        }
    }

    /** What the route asked of the app. */
    private class Events {
        val list = mutableListOf<String>()
    }

    private fun ComposeUiTest.show(
        fake: Fake,
        events: Events = Events(),
        haptics: RecordingHaptics = RecordingHaptics(),
        locale: String? = "pt-BR",
        reducedMotion: Boolean = false,
        size: DpSize = DpSize(412.dp, 800.dp),
        fontScale: Float = 1f,
        density: Float? = null,
        skeletonCards: Int = 1,
        probe: ((String) -> Unit)? = null,
        restoration: ScreenRestoration? = null,
        now: () -> Long = { START + ELAPSED_MS },
        timer: TimerUi = TimerUi(),
    ) {
        withScreen(
            locale = locale, reducedMotion = reducedMotion, size = size, fontScale = fontScale, density = density,
            haptics = haptics, probe = probe, restoration = restoration,
        ) {
            ActiveWorkoutRoute(
                state = fake.state.value,
                timer = remember { mutableStateOf(timer) },
                actions = fake,
                onMinimize = { events.list += "minimize" },
                onOpenSummary = { events.list += "summary:$it" },
                onExerciseHistory = { events.list += "history:$it" },
                onChooseRoutine = { events.list += "chooseRoutine" },
                onExerciseInfo = { events.list += "info:$it" },
                skeletonCards = skeletonCards,
                nowMillis = now,
            )
        }
        waitForIdle()
    }

    private fun stateOf(workout: WorkoutUi?) = UiState(loading = false, active = workout, exercises = exercises)

    private fun ComposeUiTest.tag(tag: String): SemanticsNodeInteraction = onNodeWithTag(tag, useUnmergedTree = true)

    /** A text shown in the toast (the design system's `ToastTag`). */
    private fun ComposeUiTest.toast(text: String): SemanticsNodeInteraction =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(TOAST)), useUnmergedTree = true)

    /** The buttons of the open dialog (the design system's `DialogTag`), in their order. */
    private fun ComposeUiTest.dialogButtons(): List<String> =
        onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(DIALOG))).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        }

    private fun ComposeUiTest.dialogButton(text: String): SemanticsNodeInteraction =
        onNode(hasClickAction() and hasText(text) and hasAnyAncestor(hasTestTag(DIALOG)))

    private fun ComposeUiTest.layoutOf(tag: String): TextLayoutResult {
        val node = tag(tag).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "$tag has no text layout")
        return results.first()
    }

    private fun ComposeUiTest.scrollToEnd() {
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.END))
        waitForIdle()
    }

    private fun ComposeUiTest.openMenuItem(tag: String) {
        onNodeWithTag(WorkoutHeaderTags.MORE).performClick()
        waitForIdle()
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.row(id: String): SemanticsNodeInteraction = onNodeWithTag(workoutSetRowTag(id))

    /**
     * The "All" row of [exerciseId] in the open picker, once its list is built: the route's picker
     * builds the library's model off the main thread, which the test's idle does not wait for.
     */
    private fun ComposeUiTest.pickerAllRow(exerciseId: String): SemanticsNodeInteraction {
        val tag = exercisePickerRowTag(exerciseId, recent = false)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        return onNode(hasTestTag(tag))
    }

    private fun ComposeUiTest.check(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag(id))))

    /** Swipes the row of [id] to the left from its check, which removes it with an undo. */
    private fun ComposeUiTest.swipeLeft(id: String) {
        row(id).performTouchInput { swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400) }
        waitForIdle()
    }

    @Test
    fun headerHasMinimizeTitleClockAndFinish() = runComposeUiTest {
        show(Fake(stateOf(nineOfFifteen)))
        onNodeWithTag(WorkoutHeaderTags.HEADER).assertHeightIsAtLeast(64.dp)
        onNodeWithContentDescription("Minimizar treino").assertIsDisplayed()
        tag(WorkoutHeaderTags.TITLE).assertTextEquals("Treino A")
        // 2538 s on the clock, then the sets: "42:18 · 9 de 15 séries".
        tag(WorkoutClockTag).assertTextEquals("42:18")
        tag(WorkoutHeaderTags.SETS).assertTextEquals("9 de 15 séries")
        val clock = tag(WorkoutClockTag).getBoundsInRoot()
        val dot = tag(WorkoutHeaderTags.SEPARATOR).assertIsDisplayed().getBoundsInRoot()
        val sets = tag(WorkoutHeaderTags.SETS).getBoundsInRoot()
        assertTrue(dot.left >= clock.right && sets.left >= dot.right && abs((sets.bottom - clock.bottom).value) < 4f, "the sets follow the clock on its line: $clock, $dot, $sets")
        // The separator is drawn, not read.
        tag(WorkoutHeaderTags.SEPARATOR).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        // Read in whole minutes, and never announced on its own.
        tag(WorkoutClockTag).assertContentDescriptionEquals("Tempo de treino, 42 minutos")
        tag(WorkoutClockTag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        onNodeWithTag(WorkoutHeaderTags.FINISH).assert(hasText("Finalizar")).assertIsDisplayed()
        onNodeWithContentDescription("Mais opções").assertIsDisplayed()
        // The header's "Finish" is tonal S: a 48 dp target. The end of the list's is 56 dp (F22).
        onNodeWithTag(WorkoutHeaderTags.FINISH).assertHeightIsAtLeast(48.dp)
        scrollToEnd()
        onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun clockRecomposesOnlyItselfOnceASecond() = runComposeUiTest {
        val keys = mutableListOf<String>()
        val base = mainClock.currentTime
        show(Fake(stateOf(nineOfFifteen)), probe = { keys += it }, now = { START + ELAPSED_MS + (mainClock.currentTime - base) })
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        val clockBefore = keys.count { it == "clock" }
        val cardsBefore = keys.count { it.startsWith("card-") || it.startsWith("row-") }
        assertTrue(clockBefore >= 1 && cardsBefore >= 1, "the probes did not report: $keys")
        mainClock.advanceTimeBy(5_000L)
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
        // The positive control: the clock did tick five times, so the probe counts.
        assertEquals(clockBefore + 5, keys.count { it == "clock" }, "clock recompositions: $keys")
        assertEquals(cardsBefore, keys.count { it.startsWith("card-") || it.startsWith("row-") }, "cards or rows recomposed with the clock")
        tag(WorkoutClockTag).assertTextEquals("42:23")
    }

    @Test
    fun minimizeCallsBack() = runComposeUiTest {
        val events = Events()
        show(Fake(stateOf(nineOfFifteen)), events)
        onNodeWithContentDescription("Minimizar treino").performClick()
        waitForIdle()
        assertEquals(listOf("minimize"), events.list)
    }

    @Test
    fun endOfListHasAddExerciseAndFinishWithTheNote() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone))
        show(fake)
        scrollToEnd()
        onNodeWithTag(ActiveWorkoutTags.ADD_EXERCISE).assert(hasText("Adicionar exercício")).assertIsDisplayed()
        onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).assert(hasText("Finalizar treino")).assertIsDisplayed()
        tag(ActiveWorkoutTags.FINISH_NOTE).assertTextEquals("Conclua pelo menos uma série para finalizar.")
        // "Add exercise" over "Finish workout", the note under it.
        val add = onNodeWithTag(ActiveWorkoutTags.ADD_EXERCISE).getBoundsInRoot()
        val finish = onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).getBoundsInRoot()
        val note = tag(ActiveWorkoutTags.FINISH_NOTE).getBoundsInRoot()
        assertTrue(add.bottom <= finish.top && finish.bottom <= note.top, "order: $add, $finish, $note")
        // A done set takes the note away.
        fake.state.value = stateOf(nineOfFifteen)
        waitForIdle()
        scrollToEnd()
        tag(ActiveWorkoutTags.FINISH_NOTE).assertDoesNotExist()
        // "Add exercise" opens the picker; a pick adds to this workout.
        onNodeWithTag(ActiveWorkoutTags.ADD_EXERCISE).performClick()
        waitForIdle()
        pickerAllRow("curl").performClick()
        waitForIdle()
        assertEquals("addWorkoutExercise:w1,curl", fake.calls.last())
    }

    @Test
    fun finishWithoutACompletedSetRejectsWithTheToast() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(nothingDone))
        show(fake, haptics = haptics)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.Rejected), haptics.events)
        toast("Conclua pelo menos uma série para finalizar.").assertIsDisplayed()
        onNodeWithText("Finalizar treino?").assertDoesNotExist()
        assertFalse(fake.calls.any { it.startsWith("finishWorkout") })
        // The end of the list refuses the same way.
        scrollToEnd()
        onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.Rejected, HapticEvent.Rejected), haptics.events)
        onNodeWithText("Finalizar treino?").assertDoesNotExist()
    }

    @Test
    fun finishDialogTextsAndOrder() = runComposeUiTest {
        show(Fake(stateOf(nineOfTwelve)))
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        onNodeWithText("Finalizar treino?").assertIsDisplayed()
        onNodeWithText("9 séries em 4 exercícios · 42 min.", substring = true).assertIsDisplayed()
        onNodeWithText("9 séries em 4 exercícios · 42 min.\n3 séries não concluídas ficam de fora.").assertIsDisplayed()
        assertEquals(listOf("Continuar treinando", "Finalizar"), dialogButtons())
        dialogButton("Continuar treinando").performClick()
        waitForIdle()
        onNodeWithText("Finalizar treino?").assertDoesNotExist()
    }

    @Test
    fun finishDialogUsesTheSingular() = runComposeUiTest {
        show(Fake(stateOf(workout(sets = listOf(1, 1, 1, 1), done = listOf(1, 1, 1, 0)))))
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        onNodeWithText("3 séries em 3 exercícios · 42 min.\n1 série não concluída fica de fora.").assertIsDisplayed()
    }

    @Test
    fun confirmingShowsLoadingAndOpensTheSummaryWhenHistoryHasIt() = runComposeUiTest {
        val events = Events()
        val fake = Fake(stateOf(nineOfTwelve))
        show(fake, events)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finalizar").performClick()
        waitForIdle()
        assertEquals("finishWorkout:", fake.calls.last())
        // Saving: the button shows its loading state and the summary waits for the history.
        onNodeWithTag(WorkoutHeaderTags.FINISH).assertContentDescriptionEquals("Carregando")
        assertTrue(events.list.isEmpty(), "opened too soon: ${events.list}")
        val finished = fake.state.value.active!!.copy(endedAt = START + ELAPSED_MS)
        fake.state.value = fake.state.value.copy(active = null, history = listOf(finished))
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
        // While the app moves to the summary, nothing says there is no workout, and a later
        // emission does not open it again.
        onNodeWithText("Nenhum treino em andamento").assertDoesNotExist()
        fake.state.value = fake.state.value.copy(history = listOf(finished, finished.copy(id = "w0")))
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
    }

    @Test
    fun finishErrorRestoresTheButton() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfTwelve))
        show(fake)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finalizar").performClick()
        waitForIdle()
        onNodeWithTag(WorkoutHeaderTags.FINISH).assertContentDescriptionEquals("Carregando")
        fake.fail(UiError.SAVE_FAILED)
        waitForIdle()
        onNodeWithTag(WorkoutHeaderTags.FINISH)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .assert(hasText("Finalizar"))
        toast("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.").assertIsDisplayed()
        assertTrue("clearError:" in fake.calls)
    }

    @Test
    fun anErrorAfterTheFinishCommittedStillOpensTheSummary() = runComposeUiTest {
        val events = Events()
        val fake = Fake(stateOf(nineOfTwelve))
        show(fake, events)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finalizar").performClick()
        waitForIdle()
        // The finish committed, then clearing the rest timer failed: its error comes before the
        // emission that moves the workout to the history.
        fake.fail(UiError.TIMER_STORAGE)
        waitForIdle()
        assertTrue("clearError:" in fake.calls)
        fake.state.value = fake.state.value.copy(active = null, history = listOf(nineOfTwelve.copy(endedAt = START + ELAPSED_MS)))
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
        onNodeWithText("Nenhum treino em andamento").assertDoesNotExist()
    }

    @Test
    fun discardDialogWithAndWithoutSets() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfFifteen))
        show(fake)
        openMenuItem(WorkoutHeaderTags.MENU_DISCARD)
        onNodeWithText("Descartar treino?").assertIsDisplayed()
        onNodeWithText("As 9 séries registradas serão apagadas.").assertIsDisplayed()
        assertEquals(listOf("Continuar treinando", "Descartar"), dialogButtons())
        dialogButton("Continuar treinando").performClick()
        waitForIdle()
        onNodeWithText("Descartar treino?").assertDoesNotExist()
        fake.state.value = stateOf(nothingDone)
        waitForIdle()
        openMenuItem(WorkoutHeaderTags.MENU_DISCARD)
        onNodeWithText("Nada foi registrado ainda.").assertIsDisplayed()
        assertFalse(fake.calls.any { it.startsWith("discardWorkout") })
    }

    @Test
    fun discardCallsDiscard() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfFifteen))
        show(fake)
        openMenuItem(WorkoutHeaderTags.MENU_DISCARD)
        dialogButton("Descartar").performClick()
        waitForIdle()
        assertEquals("discardWorkout:", fake.calls.last())
    }

    @Test
    fun renameSheetRenames() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfFifteen))
        show(fake)
        openMenuItem(WorkoutHeaderTags.MENU_RENAME)
        val field = onNode(hasSetTextAction() and hasTestTag(RenameSheetTags.FIELD), useUnmergedTree = true)
        field.performTextReplacement("   ")
        waitForIdle()
        // A blank name cannot be saved.
        onNodeWithTag(RenameSheetTags.SAVE).performClick()
        waitForIdle()
        assertFalse(fake.calls.any { it.startsWith("renameWorkout") })
        field.performTextReplacement(" Treino B ")
        waitForIdle()
        onNodeWithTag(RenameSheetTags.SAVE).performClick()
        waitForIdle()
        assertEquals("renameWorkout:w1,Treino B", fake.calls.last())
        onNodeWithTag(RenameSheetTags.SAVE).assertDoesNotExist()
    }

    @Test
    fun noWorkoutShowsTheEmptyStateWithTwoActions() = runComposeUiTest {
        val events = Events()
        val fake = Fake(stateOf(null))
        show(fake, events)
        onNodeWithText("Nenhum treino em andamento").assertIsDisplayed()
        onNodeWithText("Comece por uma rotina ou por um treino vazio.").assertIsDisplayed()
        onNodeWithText("Escolher rotina").performClick()
        waitForIdle()
        assertEquals(listOf("chooseRoutine"), events.list)
        onNodeWithText("Treino vazio").performClick()
        waitForIdle()
        assertEquals("startWorkout:null", fake.calls.last())
    }

    @Test
    fun startingShowsOneSkeletonPerCard() = runComposeUiTest {
        val fake = Fake(UiState(loading = false, startingWorkout = true))
        // Still placeholders (reduced motion), so the pulse cannot keep the test from idling.
        show(fake, skeletonCards = 3, reducedMotion = true)
        onAllNodesWithTag(ActiveWorkoutTags.SKELETON_CARD, useUnmergedTree = true).assertCountEquals(3)
        onNodeWithContentDescription("Abrindo o treino").assertExists()
        onNodeWithText("Nenhum treino em andamento").assertDoesNotExist()
        // The workout arrives: the cards take the placeholders' place.
        fake.state.value = stateOf(nineOfFifteen)
        waitForIdle()
        onAllNodesWithTag(ActiveWorkoutTags.SKELETON_CARD, useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag(WorkoutHeaderTags.HEADER).assertIsDisplayed()
    }

    @Test
    fun errorsShowLocalizedTextAndNeverTheRawError() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfFifteen).copy(error = "Set unavailable", errorCode = UiError.SET_GONE))
        show(fake)
        toast("Esta série não existe mais.").assertIsDisplayed()
        onNodeWithText("Set unavailable", substring = true).assertDoesNotExist()
        assertTrue("clearError:" in fake.calls)
    }

    @Test
    fun failedCheckClearsTheDisc() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone), echo = false)
        show(fake)
        check("e1s1").performClick()
        waitForIdle()
        // Drawn done at once, by the optimistic state, with no echo yet.
        check("e1s1").assertIsOn()
        assertEquals("updateSet:e1s1,8,60.0,true,false,false", fake.calls.last())
        // The write failed: the disc goes back to what the database holds.
        fake.fail(UiError.SAVE_FAILED)
        waitForIdle()
        check("e1s1").assertIsOff()
        toast("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.").assertIsDisplayed()
    }

    @Test
    fun refusedRestoreShowsSetNotRestored() = runComposeUiTest {
        val fake = Fake(stateOf(oneDoneFirst))
        show(fake)
        swipeLeft("e1s4")
        assertEquals("removeSet:e1s4", fake.calls.last())
        toast("Série 4 removida").assertIsDisplayed()
        onNode(hasText("Desfazer") and hasClickAction()).performClick()
        waitForIdle()
        assertTrue(fake.calls.last().startsWith("restoreSet:e1,3,"), "undo: ${fake.calls.last()}")
        // The repository refused to put it back (the exercise left, the workout ended).
        fake.fail(UiError.SET_NOT_RESTORED)
        waitForIdle()
        toast("A série não pôde voltar.").assertIsDisplayed()
    }

    @Test
    fun contentIsAtMostSixHundredWide() = runComposeUiTest {
        show(Fake(stateOf(nineOfFifteen)), size = DpSize(900.dp, 800.dp))
        val window = onNodeWithTag(ActiveWorkoutTags.ROUTE).getBoundsInRoot()
        assertEquals(900f, (window.right - window.left).value, 0.5f)
        val list = onNodeWithTag(ActiveWorkoutTags.LIST).getBoundsInRoot()
        assertEquals(600f, (list.right - list.left).value, 0.5f)
        assertEquals(window.left.value + 150f, list.left.value, 0.5f)
        // The cards keep the 16 dp margin inside it.
        val card = onNodeWithTag(exerciseHeaderTag("e1")).getBoundsInRoot()
        assertEquals(list.left.value + 16f, card.left.value, 0.5f)
        assertEquals(list.right.value - 16f, card.right.value, 0.5f)
        val header = onNodeWithTag(WorkoutHeaderTags.HEADER).getBoundsInRoot()
        assertTrue((header.right - header.left).value <= 600.5f, "header $header")
    }

    @Test
    fun emptyActiveWorkoutShowsOnlyAddExerciseAndBlocksFinish() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val fake = Fake(stateOf(WorkoutUi("w1", "Treino vazio", startedAt = START)))
        show(fake, haptics = haptics)
        tag(WorkoutHeaderTags.SETS).assertTextEquals("0 de 0 séries")
        onNodeWithTag(ActiveWorkoutTags.ADD_EXERCISE).assertIsDisplayed()
        onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).assertDoesNotExist()
        tag(ActiveWorkoutTags.FINISH_NOTE).assertDoesNotExist()
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.Rejected), haptics.events)
        toast("Conclua pelo menos uma série para finalizar.").assertIsDisplayed()
        onNodeWithText("Finalizar treino?").assertDoesNotExist()
    }

    @Test
    fun finishingClosesTheUndoToastAndSendsNoInvalidDraft() = runComposeUiTest {
        val fake = Fake(stateOf(oneDoneFirst))
        show(fake)
        swipeLeft("e1s4")
        toast("Série 4 removida").assertIsDisplayed()
        // An invalid draft in a row: it is never written.
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and hasAnyAncestor(hasTestTag(workoutSetRowTag("e1s3"))), useUnmergedTree = true)
            .performTextReplacement("82,,")
        waitForIdle()
        val before = fake.calls.size
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finalizar").performClick()
        waitForIdle()
        assertEquals(listOf("finishWorkout:"), fake.calls.drop(before))
        toast("Série 4 removida").assertDoesNotExist()
        assertFalse(fake.calls.any { it.startsWith("restoreSet") })
    }

    @Test
    fun headerTitleTakesTwoLinesAtTwoHundredWithTheClockWhole() {
        // 2.01 with density 1 is the text iOS draws at 200% (see LINEAR_TWO_X_FONT_SCALE).
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                val long = "Treino de pernas e glúteos com foco em força máxima"
                show(Fake(stateOf(workout(listOf(4, 4, 4, 3), listOf(3, 3, 2, 1), name = long))), size = DpSize(320.dp, 800.dp), fontScale = scale, density = density)
                val title = layoutOf(WorkoutHeaderTags.TITLE)
                assertEquals(2, title.lineCount, "at $scale the title takes two lines")
                assertShownWhole(layoutOf(WorkoutClockTag), "clock at $scale")
                val header = onNodeWithTag(WorkoutHeaderTags.HEADER).getBoundsInRoot()
                // The name keeps the row beside the two icon buttons: "Finish" and the clock go under it.
                val name = tag(WorkoutHeaderTags.TITLE).getBoundsInRoot()
                assertTrue((name.right - name.left) >= (header.right - header.left) / 2, "at $scale the title is squeezed: $name in $header")
                for (part in listOf(WorkoutHeaderTags.MINIMIZE, WorkoutHeaderTags.TITLE, WorkoutClockTag, WorkoutHeaderTags.SETS, WorkoutHeaderTags.FINISH, WorkoutHeaderTags.MORE)) {
                    val bounds = tag(part).getBoundsInRoot()
                    assertTrue(bounds.left >= header.left - 0.5.dp && bounds.right <= header.right + 0.5.dp && bounds.bottom <= header.bottom + 0.5.dp, "$part at $scale: $bounds in $header")
                }
                // The sets go under the clock there, without the separator.
                val setsBounds = tag(WorkoutHeaderTags.SETS).getBoundsInRoot()
                assertTrue(setsBounds.top >= tag(WorkoutClockTag).getBoundsInRoot().bottom - 0.5.dp, "at $scale the sets wrap: $setsBounds")
                tag(WorkoutHeaderTags.SEPARATOR).assertIsNotDisplayed()
                // Nothing overlaps: the clock is not under "Finish".
                val clock = tag(WorkoutClockTag).getBoundsInRoot()
                val finish = onNodeWithTag(WorkoutHeaderTags.FINISH).getBoundsInRoot()
                assertTrue(clock.right <= finish.left || clock.bottom <= finish.top || clock.top >= finish.bottom, "clock $clock, finish $finish")
            }
        }
    }

    @Test
    fun finishedWorkoutThatNeverReachesTheHistoryShowsNoWorkout() = runComposeUiTest {
        val events = Events()
        val fake = Fake(stateOf(nineOfTwelve))
        show(fake, events)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finalizar").performClick()
        waitForIdle()
        // The workout left, but no emission brought it to the history: not a blank dead end.
        fake.state.value = fake.state.value.copy(active = null)
        waitForIdle()
        onNodeWithText("Nenhum treino em andamento").assertIsDisplayed()
        assertTrue(events.list.isEmpty(), "summary opened: ${events.list}")
        // Should the history bring it later, the summary still opens.
        fake.state.value = fake.state.value.copy(history = listOf(nineOfTwelve.copy(endedAt = START + ELAPSED_MS)))
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
    }

    @Test
    fun headerReadOrderFollowsTheSpecInTwoRows() = runComposeUiTest {
        show(Fake(stateOf(nineOfFifteen)), size = DpSize(320.dp, 800.dp), fontScale = 2f)
        // Two rows: "More options" sits above the clock and "Finish"...
        val more = onNodeWithTag(WorkoutHeaderTags.MORE).getBoundsInRoot()
        val finish = onNodeWithTag(WorkoutHeaderTags.FINISH).getBoundsInRoot()
        assertTrue(more.bottom <= finish.top + 0.5.dp, "more $more, finish $finish")
        // ...but the header is read Minimize, title, time, sets, Finish, More options.
        onNodeWithTag(WorkoutHeaderTags.HEADER).assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
        val order = listOf(
            WorkoutHeaderTags.MINIMIZE, WorkoutHeaderTags.TITLE, WorkoutClockTag,
            WorkoutHeaderTags.SETS, WorkoutHeaderTags.FINISH, WorkoutHeaderTags.MORE,
        ).map { part -> tag(part).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex) }
        assertTrue(order.all { it != null }, "every part has a reading index: $order")
        assertEquals(order.map { it!! }.sorted(), order.map { it!! }, "reading order")
        assertEquals(order.size, order.toSet().size, "distinct indices: $order")
    }

    @Test
    fun swapFromTheCardMenuReplacesTheExercise() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone))
        show(fake)
        onNode(hasTestTag(ExerciseMenuTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        // No set done: the picker opens directly, and the pick replaces this entry.
        pickerAllRow("curl").performClick()
        waitForIdle()
        assertEquals("replaceWorkoutExercise:e1,curl", fake.calls.last())
        assertFalse(fake.calls.any { it.startsWith("addWorkoutExercise") })
    }

    @Test
    fun menuAboutOpensTheExerciseInfo() = runComposeUiTest {
        val events = Events()
        show(Fake(stateOf(nothingDone)), events)
        openCardMenuItem("e2", ExerciseMenuTags.ABOUT)
        // The menu closed and the app was asked for the detail of the catalog exercise, not the entry.
        assertEquals(listOf("info:row"), events.list)
        onNodeWithTag(ExerciseMenuTags.ABOUT).assertDoesNotExist()
        // "Exercise history" stays, after it, with its own callback.
        openCardMenuItem("e2", ExerciseMenuTags.HISTORY)
        assertEquals(listOf("info:row", "history:row"), events.list)
    }

    private fun ComposeUiTest.openCardMenuItem(entryId: String, tag: String) {
        onNode(hasTestTag(ExerciseMenuTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId))), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun swappingTheExerciseClosesTheUndoOfItsSet() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone))
        show(fake)
        swipeLeft("e1s3")
        toast("Série 3 removida").assertIsDisplayed()
        // Swapping another exercise leaves the undo up: the set still has its own entry to go back to.
        openCardMenuItem("e2", ExerciseMenuTags.SWAP)
        pickerAllRow("curl").performClick()
        waitForIdle()
        assertEquals("replaceWorkoutExercise:e2,curl", fake.calls.last())
        toast("Série 3 removida").assertExists()
        // Swapping its own exercise closes it: "Undo" would put the bench set into the new exercise.
        openCardMenuItem("e1", ExerciseMenuTags.SWAP)
        pickerAllRow("curl").performClick()
        waitForIdle()
        assertEquals("replaceWorkoutExercise:e1,curl", fake.calls.last())
        toast("Série 3 removida").assertDoesNotExist()
        assertFalse(fake.calls.any { it.startsWith("restoreSet") })
    }

    @Test
    fun removingTheExerciseClosesTheUndoOfItsSet() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone))
        show(fake)
        swipeLeft("e1s3")
        toast("Série 3 removida").assertIsDisplayed()
        openCardMenuItem("e1", ExerciseMenuTags.REMOVE)
        dialogButton("Remover").performClick()
        waitForIdle()
        assertEquals("removeWorkoutExercise:e1", fake.calls.last())
        toast("Série 3 removida").assertDoesNotExist()
        assertFalse(fake.calls.any { it.startsWith("restoreSet") })
    }

    @Test
    fun restChipOpensTheRestSheet() = runComposeUiTest {
        show(Fake(stateOf(nothingDone)))
        onNodeWithText(REST_NOTE).assertDoesNotExist()
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        onNodeWithText(REST_NOTE).assertIsDisplayed()
    }

    @Test
    fun howToLogOpensItsSheet() = runComposeUiTest {
        show(Fake(stateOf(nothingDone)))
        onNode(hasTestTag(ExerciseMenuTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).performClick()
        waitForIdle()
        // The menu closed and the sheet opened with its title.
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).assertDoesNotExist()
        onNodeWithText("Como registrar").assertIsDisplayed()
    }

    @Test
    fun discardClosesTheUndoToast() = runComposeUiTest {
        val fake = Fake(stateOf(oneDoneFirst))
        show(fake)
        swipeLeft("e1s4")
        toast("Série 4 removida").assertIsDisplayed()
        openMenuItem(WorkoutHeaderTags.MENU_DISCARD)
        dialogButton("Descartar").performClick()
        waitForIdle()
        assertEquals("discardWorkout:", fake.calls.last())
        toast("Série 4 removida").assertDoesNotExist()
        assertFalse(fake.calls.any { it.startsWith("restoreSet") })
    }

    @Test
    fun sheetsWaitWhileLoading() = runComposeUiTest {
        val fake = Fake(stateOf(nothingDone).copy(loading = true))
        show(fake)
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        // Still loading: the rest sheet is not put up (an unloaded list would close it).
        onNodeWithText(REST_NOTE).assertDoesNotExist()
        fake.state.value = fake.state.value.copy(loading = false)
        waitForIdle()
        onNodeWithText(REST_NOTE).assertIsDisplayed()
    }

    @Test
    fun finishingSurvivesRecreation() = runComposeUiTest {
        val events = Events()
        val fake = Fake(stateOf(nineOfTwelve))
        val restoration = screenRestoration()
        show(fake, events, locale = null, restoration = restoration)
        onNodeWithTag(WorkoutHeaderTags.FINISH).performClick()
        waitForIdle()
        dialogButton("Finish").performClick()
        waitForIdle()
        assertEquals("finishWorkout:", fake.calls.last())
        restoration.emulateSaveAndRestore()
        waitForIdle()
        // Still waiting for the summary after the recreation.
        onNodeWithTag(WorkoutHeaderTags.FINISH).assertContentDescriptionEquals("Loading")
        fake.state.value = fake.state.value.copy(active = null, history = listOf(nineOfTwelve.copy(endedAt = START + ELAPSED_MS)))
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
        // A recreation after it opened does not open it again.
        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals(listOf("summary:w1"), events.list)
        assertEquals(1, fake.calls.count { it == "finishWorkout:" })
    }

    @Test
    fun renameKeyboardDoneSaves() = runComposeUiTest {
        val fake = Fake(stateOf(nineOfFifteen))
        show(fake)
        openMenuItem(WorkoutHeaderTags.MENU_RENAME)
        val field = onNode(hasSetTextAction() and hasTestTag(RenameSheetTags.FIELD), useUnmergedTree = true)
        field.performTextReplacement("Treino C")
        waitForIdle()
        field.performImeAction()
        waitForIdle()
        assertEquals("renameWorkout:w1,Treino C", fake.calls.last())
        onNodeWithTag(RenameSheetTags.SAVE).assertDoesNotExist()
    }

    /**
     * Plan decision 11, pre-flight F1: a catalog exercise shows its pt-BR name on the card, in its
     * menu and confirmation, in the rest sheet and in the dock, even while the timer carries the
     * English name the view model stored on an automatic rest. The rest the sheet starts carries
     * the entry and its snapshot name, as the automatic rest does (final review, item 1).
     */
    @Test
    fun catalogNamesFollowPortuguese() = runComposeUiTest {
        val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", convention = "Total load")
        val workout = WorkoutUi(
            id = "w1", name = "Treino A", startedAt = START,
            exercises = listOf(
                WorkoutExerciseUi("e1", bench, (1..3).map { n -> SetUi("e1s$n", reps = 8, weightKg = 60.0, completed = n == 1) }),
            ),
        )
        val fake = Fake(UiState(loading = false, active = workout, exercises = listOf(bench)))
        val autoRest = TimerUi("Barbell bench press", remainingSeconds = 90, durationSeconds = 120, running = true, workoutExerciseId = "e1")
        show(fake, timer = autoRest)
        val name = "Supino reto com barra"
        onNode(hasText(name) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).assertExists()
        onNode(hasText("Descanso · $name") and hasAnyAncestor(hasTestTag(TimerDockTag)), useUnmergedTree = true).assertExists()
        val shown = onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size

        // The menu's title.
        onNode(hasTestTag(ExerciseMenuTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(shown + 1, onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size, "menu title")
        // The removal's confirmation.
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText("Remover $name? As 3 séries dele saem deste treino.", useUnmergedTree = true).assertExists()
        dialogButton("Continuar treinando").performClick()
        waitForIdle()

        // The rest sheet, and the rest it starts carries the entry and its snapshot name.
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        assertEquals(shown + 1, onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size, "rest sheet")
        onNode(hasClickAction() and hasText("Iniciar descanso agora")).performClick()
        waitForIdle()
        assertEquals("startTimer:Barbell bench press,${workout.exercises.single().restSeconds},e1", fake.calls.last())
        onAllNodesWithText("Barbell bench press", substring = true, useUnmergedTree = true).assertCountEquals(0)

        // The expanded timer's title, by the same entry.
        onNodeWithTag(TimerOpenTag).performClick()
        waitForIdle()
        tag(TimerTitleTag).assertTextEquals("Descanso · $name · série 1 de 3")
        onAllNodesWithText("Barbell bench press", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * Final review, item 1: a rest started from the rest sheet is started as the automatic rest is,
     * with the entry and its snapshot name (what the timer stores), so after a change of language
     * the dock names the exercise in the new language, not in the one the rest started in.
     */
    @Test
    fun aManualRestFollowsALanguageChange() = runComposeUiTest {
        val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", convention = "Total load")
        val workout = WorkoutUi(
            id = "w1", name = "Treino A", startedAt = START,
            exercises = listOf(WorkoutExerciseUi("e1", bench, (1..3).map { n -> SetUi("e1s$n", reps = 8, weightKg = 60.0) })),
        )
        val timer = mutableStateOf(TimerUi())
        // The view model and the timer: the rest carries what startTimer was given.
        val fake = object : MoosclesActions by RecordingActions() {
            val calls = mutableListOf<String>()
            override fun startTimer(exerciseName: String, seconds: Int, workoutExerciseId: String?) {
                calls += "startTimer:$exerciseName,$seconds,$workoutExerciseId"
                timer.value = TimerUi(exerciseName, remainingSeconds = seconds, durationSeconds = seconds, running = true,
                    workoutExerciseId = workoutExerciseId)
            }
        }
        val locale = mutableStateOf("pt-BR")
        setContent {
            ScreenFrame(locale = locale.value, size = DpSize(412.dp, 800.dp)) {
                ActiveWorkoutRoute(
                    state = UiState(loading = false, active = workout, exercises = listOf(bench)),
                    timer = timer,
                    actions = fake,
                    onMinimize = {}, onOpenSummary = {}, onExerciseHistory = {}, onChooseRoutine = {}, onExerciseInfo = {},
                    nowMillis = { START + ELAPSED_MS },
                )
            }
        }
        waitForIdle()
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("Iniciar descanso agora")).performClick()
        waitForIdle()
        // What the timer stores is the snapshot name, whatever the screen's language.
        assertEquals(listOf("startTimer:Barbell bench press,${workout.exercises.single().restSeconds},e1"), fake.calls)
        onNode(hasText("Descanso · Supino reto com barra") and hasAnyAncestor(hasTestTag(TimerDockTag)), useUnmergedTree = true).assertExists()

        locale.value = "en"
        waitForIdle()
        onNode(hasText("Rest · Barbell bench press") and hasAnyAncestor(hasTestTag(TimerDockTag)), useUnmergedTree = true).assertExists()
        onAllNodesWithText("Supino reto com barra", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // The expanded timer names it by the entry too; with no set done yet, without "set 0 of 3".
        onNodeWithTag(TimerOpenTag).performClick()
        waitForIdle()
        tag(TimerTitleTag).assertTextEquals("Rest · Barbell bench press")
    }

    /** One exercise of three sets, with the [target] of its source routine. */
    private fun targeted(target: RoutineTargetUi?) = WorkoutUi(
        id = "w1", name = "Treino A", startedAt = START,
        exercises = listOf(
            WorkoutExerciseUi("e1", exercises[0], (1..3).map { n -> SetUi("e1s$n", reps = 8, weightKg = 60.0) }, target = target),
        ),
    )

    private val threeByEight get() = RoutineTargetUi(List(3) { TargetSetUi(8, 80.0) }, warmups = 0)

    @Test
    fun targetLineSitsUnderTheMeta() = runComposeUiTest {
        show(Fake(stateOf(targeted(threeByEight))))
        val nb = "\u00A0"
        val subline = tag(ExerciseCardSublineTag)
        assertEquals("Alvo 3×8 · 80${nb}kg", layoutOf(ExerciseCardSublineTag).layoutInput.text.text)
        subline.assertIsDisplayed()
        val meta = onNode(hasText("série 1 de 3", substring = true), useUnmergedTree = true).getBoundsInRoot()
        val name = onNode(hasText("Supino reto") and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).getBoundsInRoot()
        val bounds = subline.getBoundsInRoot()
        assertTrue(bounds.top >= meta.bottom - 0.5.dp && meta.top >= name.bottom - 0.5.dp, "name $name, meta $meta, target $bounds")
    }

    @Test
    fun noTargetWithoutASourceRoutine() = runComposeUiTest {
        show(Fake(stateOf(targeted(null))))
        onAllNodesWithTag(ExerciseCardSublineTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun targetLineWholeAtTwoX() {
        val target = RoutineTargetUi(listOf(TargetSetUi(10, 70.0), TargetSetUi(8, 75.0), TargetSetUi(6, 80.0)), warmups = 2)
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                show(Fake(stateOf(targeted(target))), size = DpSize(320.dp, 800.dp), fontScale = scale, density = density)
                val layout = layoutOf(ExerciseCardSublineTag)
                val nb = "\u00A0"
                assertEquals("Alvo 3 séries · 6–10 reps · 70–80${nb}kg +${nb}2${nb}aquec.", layout.layoutInput.text.text, "at $scale")
                assertFalse(layout.hasVisualOverflow, "at $scale the target line is cut: ${layout.size}")
                assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "at $scale the target line is clipped")
                val header = onNodeWithTag(exerciseHeaderTag("e1")).getBoundsInRoot()
                val bounds = tag(ExerciseCardSublineTag).getBoundsInRoot()
                assertTrue(bounds.left >= header.left && bounds.right <= header.right + 0.5.dp, "at $scale: $bounds in $header")
            }
            LocalAppLocale.restore()
        }
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val ELAPSED_MS = 2_538_000L
        const val REST_NOTE = "Vale para os próximos descansos deste exercício; o descanso atual mantém o próprio tempo."
        // The design system's internal tags of the toast and the dialog card.
        const val TOAST = "toast"
        const val DIALOG = "dialog"
    }
}
