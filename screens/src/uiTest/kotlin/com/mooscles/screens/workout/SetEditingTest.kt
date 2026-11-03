package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OutsideTag = "set-editing-outside"
private const val DockTag = "set-editing-dock"
private const val ScreenTag = "set-editing-screen"
private const val NBSP = " "

/**
 * Editing a set in its row (spec D12, D21): the first tap edits with the stepper and no
 * keyboard, the second opens the keyboard; every step and valid key writes, a hold writes once;
 * and the guarantees of the old SetEditor (late echoes, invalid drafts, the unit switch, the
 * comma) hold for the drafts the screen now keeps.
 */
@OptIn(ExperimentalTestApi::class)
class SetEditingTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", convention = "Total load")

    /** Records every call; with [echo], an `updateSet` also changes [entry] as the database emission would. */
    private class Actions(
        val entry: MutableState<WorkoutExerciseUi>,
        val echo: Boolean,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            if (echo) entry.emit(setId) { it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) }
        }
    }

    private class Shown {
        var screen: ActiveWorkoutScreenState? = null
    }

    /**
     * Every set of [entry] as a [WorkoutSetRow] inside the outside-tap scope, with a strip under
     * them to tap outside. With [imeInsets], the rows sit after 2000 dp in a scrolling column that
     * ends above a 56 dp dock pinned over the insets, as the screen lays them out with a keyboard.
     * [visible] false takes the rows out of composition (the screen state stays).
     */
    private fun ComposeUiTest.showRows(
        entry: MutableState<WorkoutExerciseUi>,
        actions: MoosclesActions,
        pounds: MutableState<Boolean> = mutableStateOf(false),
        locale: String? = "en",
        restoration: ScreenRestoration? = null,
        visible: MutableState<Boolean> = mutableStateOf(true),
        imeInsets: WindowInsets? = null,
    ): Shown {
        val shown = Shown()
        withScreen(locale = locale, restoration = restoration) {
            val screen = rememberActiveWorkoutScreenState()
            shown.screen = screen
            val rows = @androidx.compose.runtime.Composable {
                if (visible.value) {
                    val current = entry.value
                    val suggestions = SetSuggestions.forExercise(current.sets, current.previousSets, current.exercise.convention, screen.cleared)
                    val previous = SetSuggestions.pairPrevious(current.sets, current.previousSets)
                    current.sets.forEachIndexed { i, set ->
                        key(set.id) {
                            WorkoutSetRow(
                                set = set, number = i + 1, total = current.sets.size, entry = current,
                                suggestion = suggestions[i], previous = previous[i], pounds = pounds.value,
                                screen = screen, actions = actions, onChangeType = {}, onRemove = { _, _ -> },
                            )
                        }
                    }
                }
            }
            if (imeInsets == null) {
                Column(Modifier.width(412.dp).endsSetEditingOnOutsideTap(screen)) {
                    rows()
                    Spacer(Modifier.fillMaxWidth().height(160.dp).testTag(OutsideTag))
                }
            } else {
                Box(Modifier.fillMaxSize().testTag(ScreenTag).endsSetEditingOnOutsideTap(screen)) {
                    Column(
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(imeInsets)
                            .padding(bottom = 56.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.fillMaxWidth().height(2000.dp))
                        rows()
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(imeInsets)
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag(DockTag),
                    )
                }
            }
        }
        waitForIdle()
        return shown
    }

    private fun entryOf(vararg sets: SetUi, previous: List<PreviousSetUi> = emptyList()) =
        mutableStateOf(WorkoutExerciseUi("e1", bench, sets = sets.toList(), previousSets = previous))

    private fun row(id: String) = hasTestTag(workoutSetRowTag(id))

    /** The editable text field in the [cell] (load or reps tag) of the row of [id]. */
    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

    private fun ComposeUiTest.load(id: String) = field(id, SetRowLoadTag)

    private fun ComposeUiTest.reps(id: String) = field(id, SetRowRepsTag)

    private fun ComposeUiTest.check(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

    private fun ComposeUiTest.stepperValue(): SemanticsNodeInteraction = onNodeWithTag(StepperValueTag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "no text layout result")
        return results.first()
    }

    /** Runs the custom accessibility action [label] of the row of [id]. */
    private fun ComposeUiTest.rowAction(id: String, label: String) {
        val actions = onNodeWithTag(workoutSetRowTag(id)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle { assertTrue(actions.single { it.label == label }.action(), label) }
        waitForIdle()
    }

    private fun ComposeUiTest.rowDescription(id: String): String =
        onNodeWithTag(workoutSetRowTag(id), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString(" ")

    private fun updates(actions: Actions) = actions.calls.filter { it.startsWith("updateSet:") }

    @Test
    fun firstTapEditsWithoutKeyboard() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0), SetUi("s2", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        load("s1").performClick()
        waitForIdle()
        assertEquals(SetEditTarget("s1", SetField.LOAD), shown.screen?.editing)
        onNodeWithContentDescription("Editing load of set 1").assertIsDisplayed()
        stepperValue().assertTextEquals("85.0${NBSP}kg")
        load("s1").assertIsNotFocused()
        assertEquals(emptyList(), actions.calls)
    }

    @Test
    fun secondTapFocusesTheField() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        load("s1").performClick()
        waitForIdle()
        load("s1").assertIsNotFocused()
        load("s1").performClick()
        waitForIdle()
        load("s1").assertIsFocused()
        assertEquals(SetEditTarget("s1", SetField.LOAD, keyboard = true), shown.screen?.editing)
        // With the keyboard open, a tap on the other field of the row moves it there at once.
        reps("s1").performClick()
        waitForIdle()
        reps("s1").assertIsFocused()
        assertEquals(SetEditTarget("s1", SetField.REPS, keyboard = true), shown.screen?.editing)
        assertEquals(emptyList(), actions.calls)
    }

    @Test
    fun keyboardButtonFocusesTheField() {
        for ((locale, label) in listOf("pt-BR" to "Abrir teclado", "en" to "Open keyboard")) {
            runComposeUiTest {
                val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
                val shown = showRows(entry, Actions(entry, echo = true), locale = locale)
                reps("s1").performClick()
                waitForIdle()
                reps("s1").assertIsNotFocused()
                onNodeWithContentDescription(label).performClick()
                waitForIdle()
                reps("s1").assertIsFocused()
                assertEquals(SetEditTarget("s1", SetField.REPS, keyboard = true), shown.screen?.editing, locale)
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun stepperStepsTwoPointFiveKgAndWritesEachStep() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions, locale = "pt-BR")
        load("s1").performClick()
        waitForIdle()
        stepperValue().assertTextEquals("85,0${NBSP}kg")
        assertShownWhole(stepperValue().layout(), "stepper value")
        onNodeWithText("passo de 2,5${NBSP}kg").assertIsDisplayed()
        assertShownWhole(onNodeWithText("passo de 2,5${NBSP}kg").layout(), "step label")
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,87.5,false,false,false"), actions.calls)
        stepperValue().assertTextEquals("87,5${NBSP}kg")
        assertEquals("87,5", load("s1").editableText())
        onNodeWithTag(StepperDecreaseTag).performClick()
        waitForIdle()
        onNodeWithTag(StepperDecreaseTag).performClick()
        waitForIdle()
        assertEquals(
            listOf("updateSet:s1,8,87.5,false,false,false", "updateSet:s1,8,85.0,false,false,false", "updateSet:s1,8,82.5,false,false,false"),
            actions.calls,
        )
        assertEquals("82,5", load("s1").editableText())
    }

    @Test
    fun poundsStepFiveLb() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = WeightUnits.lbToKg(80.0)))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions, pounds = mutableStateOf(true))
        load("s1").performClick()
        waitForIdle()
        stepperValue().assertTextEquals("80.0${NBSP}lb")
        onNodeWithText("step of 5${NBSP}lb").assertIsDisplayed()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,${WeightUnits.lbToKg(85.0)},false,false,false"), actions.calls)
        assertEquals("85", load("s1").editableText())
    }

    @Test
    fun repsStepOne() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions)
        reps("s1").performClick()
        waitForIdle()
        onNodeWithContentDescription("Editing reps of set 1").assertIsDisplayed()
        stepperValue().assertTextEquals("8")
        onNodeWithText("step of 1").assertIsDisplayed()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,9,85.0,false,false,false"), actions.calls)
        assertEquals("9", reps("s1").editableText())
    }

    @Test
    fun holdWritesOnceOnRelease() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions)
        load("s1").performClick()
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        // Steps at about 400, 480 and 560 ms; the next would be at 640.
        repeat(600) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        stepperValue().assertTextEquals("92.5${NBSP}kg")
        assertEquals(emptyList(), actions.calls, "a hold writes only when it ends")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(200) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,92.5,false,false,false"), actions.calls)
        assertEquals("92.5", load("s1").editableText())
    }

    @Test
    fun nextGoesToRepsAndDoneOnlyClosesTheKeyboard() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        load("s1").performImeAction()
        waitForIdle()
        reps("s1").assertIsFocused()
        assertEquals(SetEditTarget("s1", SetField.REPS, keyboard = true), shown.screen?.editing)
        reps("s1").performImeAction()
        waitForIdle()
        reps("s1").assertIsNotFocused()
        load("s1").assertIsNotFocused()
        // The row stays in edit, with the stepper; the set is not completed.
        assertEquals(SetEditTarget("s1", SetField.REPS), shown.screen?.editing)
        onNodeWithContentDescription("Editing reps of set 1").assertIsDisplayed()
        check("s1").assertIsOff()
        assertTrue(actions.calls.none { it.split(",")[3] == "true" }, "${actions.calls}")
    }

    @Test
    fun tappingOutsideEndsEditing() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0), SetUi("s2", reps = 6, weightKg = 80.0))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        load("s1").performClick()
        waitForIdle()
        assertNotNull(shown.screen?.editing)
        // A tap inside the row in edit keeps it.
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals("s1", shown.screen?.editing?.setId)
        onNodeWithTag(OutsideTag).performClick()
        waitForIdle()
        assertNull(shown.screen?.editing)
        onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertDoesNotExist()

        // With the keyboard open, the tap outside also closes it.
        load("s1").performClick()
        load("s1").performClick()
        waitForIdle()
        load("s1").assertIsFocused()
        onNodeWithTag(OutsideTag).performClick()
        waitForIdle()
        assertNull(shown.screen?.editing)
        load("s1").assertIsNotFocused()

        // Another row's field moves the edit there; the check ends it.
        load("s1").performClick()
        waitForIdle()
        reps("s2").performClick()
        waitForIdle()
        assertEquals(SetEditTarget("s2", SetField.REPS), shown.screen?.editing)
        check("s2").performClick()
        waitForIdle()
        assertNull(shown.screen?.editing)
        assertEquals("updateSet:s2,6,80.0,true,false,false", actions.calls.last())
    }

    @Test
    fun lateEchoDoesNotEraseANewerDraft() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 20.0))
        val actions = Actions(entry, echo = false)
        val shown = showRows(entry, actions)
        load("s1").performTextReplacement("22")
        reps("s1").performTextReplacement("6")
        load("s1").performTextReplacement("24")
        waitForIdle()
        assertEquals(
            listOf("updateSet:s1,8,22.0,false,false,false", "updateSet:s1,6,22.0,false,false,false", "updateSet:s1,6,24.0,false,false,false"),
            actions.calls,
        )
        // The echo of the first write arrives late: neither newer draft goes.
        entry.emit("s1") { it.copy(reps = 8, weightKg = 22.0) }
        waitForIdle()
        assertEquals("24", load("s1").editableText())
        assertEquals("6", reps("s1").editableText())
        entry.emit("s1") { it.copy(reps = 6, weightKg = 22.0) }
        waitForIdle()
        assertEquals("24", load("s1").editableText())
        assertEquals("6", reps("s1").editableText())
        entry.emit("s1") { it.copy(reps = 6, weightKg = 24.0) }
        waitForIdle()
        assertEquals("24", load("s1").editableText())
        assertEquals("6", reps("s1").editableText())
        assertEquals(emptyMap(), shown.screen?.drafts, "every draft was acknowledged")
    }

    /** Task 14 review: "8", "80", "8" with late echoes must keep the newest draft, whatever echo arrives. */
    @Test
    fun lateEchoInAnABASequenceKeepsTheNewestDraft() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0), SetUi("s2", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = false)
        val shown = showRows(entry, actions)
        for (id in listOf("s1", "s2")) {
            load(id).performTextReplacement("8")
            load(id).performTextReplacement("80")
            load(id).performTextReplacement("8")
        }
        waitForIdle()
        for (id in listOf("s1", "s2")) {
            assertEquals(
                listOf("updateSet:$id,8,8.0,false,false,false", "updateSet:$id,8,80.0,false,false,false", "updateSet:$id,8,8.0,false,false,false"),
                actions.calls.filter { it.startsWith("updateSet:$id,") },
            )
        }
        // s1: every echo arrives, late. The first "8" must not drop the draft, so the "80" after it
        // cannot come back.
        for (kg in listOf(8.0, 80.0)) {
            entry.emit("s1") { it.copy(weightKg = kg) }
            waitForIdle()
            assertEquals("8", load("s1").editableText(), "after the echo of $kg")
            assertEquals("8", shown.screen?.drafts?.get("s1" to SetField.LOAD), "after the echo of $kg")
        }
        entry.emit("s1") { it.copy(weightKg = 8.0) }
        waitForIdle()
        assertEquals("8", load("s1").editableText())
        assertNull(shown.screen?.drafts?.get("s1" to SetField.LOAD))
        // s2: the database folded the first echo into the later ones.
        entry.emit("s2") { it.copy(weightKg = 80.0) }
        waitForIdle()
        assertEquals("8", load("s2").editableText())
        entry.emit("s2") { it.copy(weightKg = 8.0) }
        waitForIdle()
        assertEquals("8", load("s2").editableText())
        assertNull(shown.screen?.drafts?.get("s2" to SetField.LOAD))
        // Folded into nothing: after "80" and "8" over a saved 8, no value changes. The draft
        // already shows the saved value and goes when the edit ends.
        load("s2").performTextReplacement("80")
        load("s2").performTextReplacement("8")
        waitForIdle()
        assertEquals("8", load("s2").editableText())
        onNodeWithTag(OutsideTag).performClick()
        waitForIdle()
        assertNull(shown.screen?.editing)
        assertEquals("8", load("s2").editableText())
        assertEquals(emptyMap(), shown.screen?.drafts)
    }

    /** Task 14 review: a draft in another form than the saved value's ("80.0", "082", "82,5") goes when the equal value is saved. */
    @Test
    fun draftInAnotherFormClearsOnTheEqualEcho() {
        for ((locale, typed, kg, shownAfter) in listOf(
            Quad("en", "80.0", 80.0, "80"),
            Quad("en", "82,5", 82.5, "82.5"),
            Quad("pt-BR", "82,5", 82.5, "82,5"),
            Quad("pt-BR", "82.5", 82.5, "82,5"),
        )) {
            runComposeUiTest {
                val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
                val actions = Actions(entry, echo = true)
                val shown = showRows(entry, actions, locale = locale)
                load("s1").performTextReplacement(typed)
                waitForIdle()
                assertEquals(listOf("updateSet:s1,8,$kg,false,false,false"), actions.calls, "$locale $typed")
                assertEquals(emptyMap(), shown.screen?.drafts, "$locale $typed")
                assertEquals(shownAfter, load("s1").editableText(), "$locale $typed")
                reps("s1").performTextReplacement("082")
                waitForIdle()
                assertEquals("updateSet:s1,82,$kg,false,false,false", actions.calls.last(), locale)
                assertEquals(emptyMap(), shown.screen?.drafts, "$locale 082")
                assertEquals("82", reps("s1").editableText(), locale)
            }
            LocalAppLocale.restore()
        }
    }

    private data class Quad(val locale: String, val typed: String, val kg: Double, val shown: String)

    @Test
    fun invalidDraftIsNotSentAndNotLost() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val actions = Actions(entry, echo = true)
        val visible = mutableStateOf(true)
        showRows(entry, actions, visible = visible)
        load("s1").performTextReplacement("82,")
        reps("s1").performTextReplacement("99999")
        waitForIdle()
        assertEquals(emptyList(), actions.calls, "an invalid draft is not sent")
        // A change from elsewhere does not erase what the person typed.
        entry.emit("s1") { it.copy(weightKg = 90.0, reps = 7) }
        waitForIdle()
        assertEquals("82,", load("s1").editableText())
        assertEquals("99999", reps("s1").editableText())
        // Nor does the row leaving composition.
        visible.value = false
        waitForIdle()
        visible.value = true
        waitForIdle()
        assertEquals("82,", load("s1").editableText())
        assertEquals("99999", reps("s1").editableText())
        // A valid reps value is written with the saved load, which the invalid load draft leaves standing.
        reps("s1").performTextReplacement("9")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,9,90.0,false,false,false"), actions.calls)
        assertEquals("82,", load("s1").editableText())
    }

    @Test
    fun unitSwitchConvertsTheDraftFromExactKg() = runComposeUiTest {
        val exact = 17.123456789
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 20.0), SetUi("s2", reps = 8, weightKg = exact))
        val actions = Actions(entry, echo = false)
        val pounds = mutableStateOf(false)
        val visible = mutableStateOf(true)
        showRows(entry, actions, pounds = pounds, visible = visible)
        load("s1").performTextReplacement("22.5")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,22.5,false,false,false"), actions.calls)
        // The switch happens while the rows are out of composition, as from the settings.
        visible.value = false
        waitForIdle()
        pounds.value = true
        visible.value = true
        waitForIdle()
        assertEquals("49.6", load("s1").editableText(), "22.5 kg in pounds")
        assertEquals("37.75", load("s2").editableText(), "17.123456789 kg in pounds")
        reps("s1").performTextReplacement("9")
        reps("s2").performTextReplacement("9")
        waitForIdle()
        // Both keep their exact kg, not 49.6 or 37.75 lb converted back.
        assertEquals(
            listOf("updateSet:s1,9,22.5,false,false,false", "updateSet:s2,9,$exact,false,false,false"),
            actions.calls.drop(1),
        )
    }

    @Test
    fun acknowledgedFieldsAcceptExternalChanges() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 20.0))
        val actions = Actions(entry, echo = false)
        showRows(entry, actions)
        load("s1").performTextReplacement("22")
        reps("s1").performTextReplacement("6")
        waitForIdle()
        assertEquals("updateSet:s1,6,22.0,false,false,false", actions.calls.last())
        entry.emit("s1") { it.copy(reps = 6, weightKg = 22.0) }
        waitForIdle()
        // Acknowledged: a change from elsewhere shows.
        entry.emit("s1") { it.copy(reps = 5, weightKg = 24.0) }
        waitForIdle()
        assertEquals("24", load("s1").editableText())
        assertEquals("5", reps("s1").editableText())
        // An invalid draft stays through the next change; the other field takes it.
        load("s1").performTextReplacement("82,")
        waitForIdle()
        entry.emit("s1") { it.copy(reps = 7, weightKg = 26.0) }
        waitForIdle()
        assertEquals("82,", load("s1").editableText())
        assertEquals("7", reps("s1").editableText())
    }

    @Test
    fun commaDecimalIsAccepted() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0))
        val actions = Actions(entry, echo = false)
        showRows(entry, actions)
        load("s1").performTextReplacement("82,5")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,82.5,false,false,false"), actions.calls)
        assertEquals("82,5", load("s1").editableText())
        // The comma is a decimal in check too: the set completes with 82.5.
        check("s1").performClick()
        waitForIdle()
        assertEquals("updateSet:s1,8,82.5,true,false,false", actions.calls.last())
    }

    @Test
    fun editingDraftAndClearedFieldsSurviveRecreation() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0), previous = listOf(PreviousSetUi(80.0, 6, warmup = false, assistance = false)))
        val actions = Actions(entry, echo = false)
        val restoration = screenRestoration()
        // No language override: leaving ProvideAppLocale rebuilds the theme's tree (LocaleEpoch),
        // which would drop the saved state the tester restores.
        val shown = showRows(entry, actions, locale = null, restoration = restoration)
        load("s1").performClick()
        waitForIdle()
        load("s1").performTextReplacement("82,")
        reps("s1").performTextReplacement("")
        waitForIdle()
        assertEquals(listOf("updateSet:s1,0,85.0,false,false,false"), actions.calls)
        val editing = shown.screen?.editing
        assertEquals("s1", editing?.setId)
        val before = actions.calls

        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals(editing, shown.screen?.editing)
        onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertExists()
        assertEquals("82,", load("s1").editableText())
        assertEquals("", reps("s1").editableText())
        assertEquals(setOf("s1" to SetField.REPS), shown.screen?.cleared)
        assertEquals(mapOf(("s1" to SetField.LOAD) to "82,", ("s1" to SetField.REPS) to ""), shown.screen?.drafts)
        // Still cleared: the previous session's 6 does not come back as a suggestion.
        val description = rowDescription("s1")
        assertFalse("suggestion" in description || "sugestão" in description, description)
        assertEquals(before, actions.calls, "recreating the row must not write")
        // The late echo of the reps write still finds what it acknowledges.
        entry.emit("s1") { it.copy(reps = 0) }
        waitForIdle()
        assertEquals(mapOf(("s1" to SetField.LOAD) to "82,"), shown.screen?.drafts)
    }

    @Test
    fun editSetActionFocusesTheLoadStepper() {
        for ((locale, name) in listOf("pt-BR" to "Editando carga da série 4", "en" to "Editing load of set 4")) {
            runComposeUiTest {
                val entry = entryOf(*Array(5) { SetUi("s${it + 1}", reps = 8, weightKg = 80.0) })
                val shown = showRows(entry, Actions(entry, echo = true), locale = locale)
                onNodeWithTag(workoutSetRowTag("s4")).performSemanticsAction(SemanticsActions.OnClick)
                waitForIdle()
                assertEquals("s4", shown.screen?.editing?.setId, locale)
                assertEquals(SetField.LOAD, shown.screen?.editing?.field, locale)
                onNodeWithContentDescription(name).assertIsFocused()
                load("s4").assertIsNotFocused()
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun editLoadAndEditRepsActionsFocusTheirStepper() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 80.0), SetUi("s2", reps = 8, weightKg = 80.0))
        val shown = showRows(entry, Actions(entry, echo = true))
        rowAction("s2", "Edit reps")
        assertEquals(SetField.REPS, shown.screen?.editing?.field)
        onNodeWithContentDescription("Editing reps of set 2").assertIsFocused()
        rowAction("s2", "Edit load")
        assertEquals(SetField.LOAD, shown.screen?.editing?.field)
        onNodeWithContentDescription("Editing load of set 2").assertIsFocused()
    }

    /** Review I1: emptied reps on a done set wait as a draft, but the load still writes, with the last valid reps. */
    @Test
    fun doneSetWithClearedRepsStillWritesTheLoad() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0, completed = true))
        val actions = Actions(entry, echo = true)
        val shown = showRows(entry, actions)
        reps("s1").performTextReplacement("")
        waitForIdle()
        assertEquals(emptyList(), actions.calls, "a done set is not written with 0 reps")
        load("s1").performClick()
        waitForIdle()
        assertEquals(SetField.LOAD, shown.screen?.editing?.field)
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,8,87.5,true,false,false"), actions.calls)
        check("s1").assertIsOn()
        assertEquals("87.5", load("s1").editableText())
        assertEquals("", shown.screen?.drafts?.get("s1" to SetField.REPS), "the emptied reps keep waiting")
    }

    @Test
    fun doneSetRepsStepperStopsAtOne() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 2, weightKg = 85.0, completed = true))
        val actions = Actions(entry, echo = true)
        showRows(entry, actions)
        reps("s1").performClick()
        waitForIdle()
        onNodeWithTag(StepperDecreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateSet:s1,1,85.0,true,false,false"), actions.calls)
        stepperValue().assertTextEquals("1")
        onNodeWithTag(StepperDecreaseTag).assertIsNotEnabled()
    }

    /** Review M4: after a refused check, "Edit reps" keeps the error, and repeating it focuses the stepper again. */
    @Test
    fun editRepsActionKeepsTheErrorAndFocusesAgain() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", weightKg = 80.0))
        val shown = showRows(entry, Actions(entry, echo = true))
        check("s1").performClick()
        waitForIdle()
        assertEquals(SetEditTarget("s1", SetField.REPS, error = true), shown.screen?.editing)
        rowAction("s1", "Edit reps")
        assertEquals(true, shown.screen?.editing?.error)
        onNodeWithText("Enter the reps", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Editing reps of set 1").assertIsFocused()
        // The focus moves on, inside the panel, without changing the edit.
        onNodeWithTag(StepperIncreaseTag).performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        onNodeWithContentDescription("Editing reps of set 1").assertIsNotFocused()
        assertEquals(SetEditTarget("s1", SetField.REPS, error = true, stepperFocus = true), shown.screen?.editing)
        rowAction("s1", "Edit reps")
        onNodeWithContentDescription("Editing reps of set 1").assertIsFocused()
    }

    /** S3: the iOS simulator opens no keyboard, so the keyboard is 300 dp of injected insets on both platforms. */
    @Test
    fun editingRowIsBroughtIntoViewAboveTheKeyboard() = runComposeUiTest {
        val entry = entryOf(SetUi("s1", reps = 8, weightKg = 85.0))
        val shown = showRows(entry, Actions(entry, echo = true), imeInsets = WindowInsets(bottom = 300.dp))
        val screen = bounds(onNodeWithTag(ScreenTag))
        val dock = bounds(onNodeWithTag(DockTag))
        val keyboard = with(density) { 300.dp.toPx() }
        assertTrue(abs(dock.bottom - (screen.bottom - keyboard)) <= 1f, "dock $dock, screen $screen")
        // The control: before the edit, the row (after 2000 dp) is off screen.
        assertFalse(wholeAbove(onNodeWithTag(workoutSetRowTag("s1")), dock), "row visible before the edit")
        load("s1").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        assertEquals(SetEditTarget("s1", SetField.LOAD, keyboard = true), shown.screen?.editing)
        val row = onNodeWithTag(workoutSetRowTag("s1"))
        val stepper = onNodeWithContentDescription("Editing load of set 1")
        assertTrue(wholeAbove(row, dock), "row ${bounds(row)} not wholly above the dock $dock")
        assertTrue(wholeAbove(stepper, dock), "stepper ${bounds(stepper)} not wholly above the dock $dock")
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    /**
     * Whether [node] is whole on screen and above [dock]. boundsInRoot is clipped by the scroll
     * viewport (a node out of it is an empty rect), so its full height must be there.
     */
    private fun ComposeUiTest.wholeAbove(node: SemanticsNodeInteraction, dock: Rect): Boolean {
        val rect = bounds(node)
        val full = node.fetchSemanticsNode().size.height
        return rect.height >= full - 0.5f && rect.top >= -0.5f && rect.bottom <= dock.top + 0.5f
    }
}

/** Changes the set [id] of the exercise as a database emission would. */
private fun MutableState<WorkoutExerciseUi>.emit(id: String, change: (SetUi) -> SetUi) {
    value = value.copy(sets = value.sets.map { if (it.id == id) change(it) else it })
}
