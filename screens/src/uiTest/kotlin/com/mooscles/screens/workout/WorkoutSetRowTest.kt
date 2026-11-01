package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowPreviousTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The set row of the active workout: suggestions sent on check, the optimistic disc, copying the
 * previous result, the swipe and the fields cleared on purpose (spec D10, D11).
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutSetRowTest {
    /** Puts the system language back, so a failed test cannot leak a language into the next one. */
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", convention = "Total load")
    private val pullUp = ExerciseUi("pullup", "Pull-up", "Back", "Bodyweight", convention = "Added weight")

    /**
     * Records every call; with [echo], an `updateSet` also changes [entry] the way the database
     * emission would, so the row sees its own write come back.
     */
    private class Actions(
        val entry: MutableState<WorkoutExerciseUi>,
        val echo: Boolean,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

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
    }

    private class Shown {
        var screen: ActiveWorkoutScreenState? = null
        val removed = mutableListOf<Pair<String, Int>>()
    }

    /** Every set of [entry] as a [WorkoutSetRow], with the suggestions and pairing the card computes. */
    private fun ComposeUiTest.showRows(
        entry: MutableState<WorkoutExerciseUi>,
        actions: MoosclesActions,
        haptics: RecordingHaptics = RecordingHaptics(),
        pounds: Boolean = false,
        locale: String? = null,
        restoration: ScreenRestoration? = null,
    ): Shown {
        val shown = Shown()
        withScreen(haptics = haptics, locale = locale, restoration = restoration) {
            val screen = rememberActiveWorkoutScreenState()
            shown.screen = screen
            val current = entry.value
            val suggestions = SetSuggestions.forExercise(current.sets, current.previousSets, current.exercise.convention, screen.cleared)
            val previous = SetSuggestions.pairPrevious(current.sets, current.previousSets)
            Column(Modifier.width(412.dp)) {
                current.sets.forEachIndexed { i, set ->
                    key(set.id) {
                        WorkoutSetRow(
                            set = set, number = i + 1, total = current.sets.size, entry = current,
                            suggestion = suggestions[i], previous = previous[i], pounds = pounds,
                            screen = screen, actions = actions,
                            onChangeType = {}, onRemove = { removed, position -> shown.removed += removed.id to position },
                        )
                    }
                }
            }
        }
        waitForIdle()
        return shown
    }

    private fun entryOf(vararg sets: SetUi, exercise: ExerciseUi = bench, previous: List<PreviousSetUi> = emptyList()) =
        mutableStateOf(WorkoutExerciseUi("e1", exercise, sets = sets.toList(), previousSets = previous))

    private fun row(id: String) = hasTestTag(workoutSetRowTag(id))

    private fun ComposeUiTest.check(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

    private fun ComposeUiTest.previousCell(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag(SetRowPreviousTag) and hasAnyAncestor(row(id)), useUnmergedTree = true)

    /** The editable text field in the [cell] (load or reps tag) of the row of [id]. */
    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * What the row node of [id] says, without the separate nodes inside it. A field's suggestion has
     * no semantics of its own (the field is read by its label and value), so the row's reading,
     * built from the same values, is where a test sees it.
     */
    private fun ComposeUiTest.rowDescription(id: String): String =
        onNodeWithTag(workoutSetRowTag(id), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString(" ")

    @Test
    fun prefilledSetCompletesWithOneTap() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = true)
        val haptics = RecordingHaptics()
        showRows(entry, actions, haptics)
        check("s1").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,80.0,true,false,false"), actions.calls)
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        check("s1").assertIsOn()
    }

    @Test
    fun suggestionFromAboveIsShownAndSentOnCheck() {
        for ((locale, spoken) in listOf(
            "pt-BR" to "sugestão 82,5 quilos, 8 repetições",
            "en" to "suggestion 82.5 kilograms, 8 reps",
        )) {
            runComposeUiTest {
                val entry = entryOf(
                    SetUi("s1", reps = 8, weightKg = 82.5, completed = true),
                    SetUi("s2"),
                    previous = listOf(PreviousSetUi(80.0, 8, warmup = false, assistance = false), PreviousSetUi(80.0, 8, warmup = false, assistance = false)),
                )
                val actions = Actions(entry, echo = true)
                showRows(entry, actions, locale = locale)
                val description = rowDescription("s2")
                assertTrue(spoken in description, "$locale: \"$description\" lacks \"$spoken\"")
                assertEquals("", field("s2", SetRowLoadTag).editableText(), "$locale: a suggestion is not a value")
                check("s2").performClick()
                waitForIdle()
                assertEquals(listOf("updateSet:s2,8,82.5,true,false,false"), actions.calls, locale)
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun discFillsBeforeTheEcho() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = false)
        val shown = showRows(entry, actions)
        check("s1").assertIsOff()
        check("s1").performClick()
        check("s1").assertIsOn()
        assertEquals(false, entry.value.sets.single().completed, "the state must not have changed")
        assertEquals(mapOf("s1" to true), shown.screen?.optimistic)
        assertEquals(listOf("updateSet:s1,8,80.0,true,false,false"), actions.calls)
    }

    @Test
    fun optimisticClearsWhenTheEchoArrives() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        check("s1").performClick()
        waitForIdle()
        check("s1").assertIsOn()
        assertEquals(emptyMap(), shown.screen?.optimistic)
        // With the optimistic value gone, the state alone decides: a change from elsewhere shows.
        entry.value = entry.value.copy(sets = listOf(entry.value.sets.single().copy(completed = false)))
        waitForIdle()
        check("s1").assertIsOff()
    }

    @Test
    fun noRepsRejectsAndOpensRepsWithError() {
        for ((locale, message) in listOf("pt-BR" to "Informe as repetições", "en" to "Enter the reps")) {
            runComposeUiTest {
                val entry = entryOf(SetUi("s1", weightKg = 80.0))
                val actions = Actions(entry, echo = true)
                val haptics = RecordingHaptics()
                val shown = showRows(entry, actions, haptics, locale = locale)
                check("s1").performClick()
                waitForIdle()
                assertEquals(listOf(HapticEvent.Rejected), haptics.events.toList(), locale)
                onNodeWithText(message, useUnmergedTree = true).assertIsDisplayed()
                assertTrue(actions.calls.none { it.startsWith("updateSet:") }, "$locale: ${actions.calls}")
                assertEquals(SetEditTarget("s1", SetField.REPS, error = true), shown.screen?.editing, locale)
                check("s1").assertIsOff()
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun uncheckSendsCompletedFalse() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0, completed = true))
        val actions = Actions(entry, echo = true)
        val haptics = RecordingHaptics()
        showRows(entry, actions, haptics)
        check("s1").assertIsOn()
        check("s1").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,80.0,false,false,false"), actions.calls)
        assertEquals(listOf(HapticEvent.SetUnchecked), haptics.events.toList())
        check("s1").assertIsOff()
    }

    @Test
    fun tappingPreviousCopiesWithoutCompleting() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 5, weightKg = 85.0), previous = listOf(PreviousSetUi(80.0, 6, warmup = false, assistance = false)))
        val actions = Actions(entry, echo = true)
        val haptics = RecordingHaptics()
        showRows(entry, actions, haptics)
        previousCell("s1").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,6,80.0,false,false,false"), actions.calls)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        check("s1").assertIsOff()
        assertEquals("80", field("s1", SetRowLoadTag).editableText())
        assertEquals("6", field("s1", SetRowRepsTag).editableText())
    }

    @Test
    fun tappingPreviousOnACompletedSetDoesNothing() = runComposeUiTest {
        val entry = entryOf(
            SetUi("s1", reps = 5, weightKg = 85.0, completed = true),
            previous = listOf(PreviousSetUi(80.0, 6, warmup = false, assistance = false)),
        )
        val actions = Actions(entry, echo = true)
        val haptics = RecordingHaptics()
        showRows(entry, actions, haptics)
        previousCell("s1").performClick()
        waitForIdle()
        assertEquals(emptyList(), actions.calls)
        assertEquals(emptyList(), haptics.events.toList())
    }

    @Test
    fun swipeRightCompletesLikeTheCheck() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = false)
        val haptics = RecordingHaptics()
        val shown = showRows(entry, actions, haptics)
        // From the set number, so the drag does not start on a text field.
        onNodeWithTag(workoutSetRowTag("s1")).performTouchInput {
            swipe(start = Offset(width * 0.05f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400)
        }
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,80.0,true,false,false"), actions.calls)
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.SetCompleted), haptics.events.toList())
        check("s1").assertIsOn()
        assertEquals(emptyList(), shown.removed)
    }

    @Test
    fun swipeLeftAsksToRemoveWithItsPosition() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0), SetUi("s2", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        onNodeWithTag(workoutSetRowTag("s2")).performTouchInput {
            // From the check, pinned to the right edge, so the drag cannot start on a text field.
            swipe(start = Offset(width * 0.97f, centerY), end = Offset(width * 0.5f, centerY), durationMillis = 400)
        }
        waitForIdle()
        assertEquals(listOf("s2" to 1), shown.removed)
        assertEquals(emptyList(), actions.calls)
    }

    @Test
    fun poundsSendTheExactSourceKg() = runComposeUiTest {
        val entry = entryOf(SetUi("s1"), previous = listOf(PreviousSetUi(36.287389, 8, warmup = false, assistance = false)))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions, pounds = true, locale = "en")
        // 36.287389 kg is 80 lb: the suggestion is shown in pounds and sent in the source's kg.
        val description = rowDescription("s1")
        assertTrue("suggestion 80 pounds, 8 reps" in description, description)
        check("s1").performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,36.287389,true,false,false"), actions.calls)
    }

    @Test
    fun clearedRepsStayBlankWhenTheLoadChanges() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 10.0), previous = listOf(PreviousSetUi(80.0, 6, warmup = false, assistance = false)))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions, locale = "en")
        field("s1", SetRowRepsTag).performTextReplacement("")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,0,10.0,false,false,false"), actions.calls)
        assertEquals(setOf("s1" to SetField.REPS), shown.screen?.cleared)
        // Without the mark, the previous session's 6 would come back as a suggestion.
        rowDescription("s1").let { assertTrue("load 10 kilograms, 0 reps" in it && "suggestion" !in it, it) }
        field("s1", SetRowLoadTag).performTextReplacement("20")
        waitForIdle()
        assertEquals("updateSet:s1,0,20.0,false,false,false", actions.calls.last())
        assertEquals("", field("s1", SetRowRepsTag).editableText())
        rowDescription("s1").let { assertTrue("load 20 kilograms, 0 reps" in it && "suggestion" !in it, it) }
    }

    @Test
    fun addedWeightZeroCompletesAndSurvivesRestoration() = runComposeUiTest {
        val entry = entryOf(SetUi("s1"), exercise = pullUp)
        val actions = Actions(entry, echo = true)
        val haptics = RecordingHaptics()
        val restoration = screenRestoration()
        showRows(entry, actions, haptics, restoration = restoration)
        assertEquals("", field("s1", SetRowLoadTag).editableText())
        // No reps yet: refused, with the error on the reps field.
        check("s1").performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.Rejected), haptics.events.toList())
        assertNotNull(field("s1", SetRowRepsTag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error))
        field("s1", SetRowRepsTag).performTextReplacement("12")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,12,0.0,false,false,false"), actions.calls)
        field("s1", SetRowRepsTag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        // Zero added weight is bodyweight, a real value: it completes as 0 kg.
        check("s1").performClick()
        waitForIdle()
        assertEquals("updateSet:s1,12,0.0,true,false,false", actions.calls.last())
        check("s1").assertIsOn()
        val before = actions.calls

        restoration.emulateSaveAndRestore()
        waitForIdle()
        check("s1").assertIsOn()
        assertEquals("", field("s1", SetRowLoadTag).editableText())
        assertEquals("12", field("s1", SetRowRepsTag).editableText())
        field("s1", SetRowRepsTag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        assertEquals(before, actions.calls, "recreating the row must not write")
    }
}
