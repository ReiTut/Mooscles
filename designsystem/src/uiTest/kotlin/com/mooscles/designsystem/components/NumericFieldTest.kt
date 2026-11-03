package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasContentDescription
import com.mooscles.designsystem.interaction.FocusRingVisible
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumericFieldTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isLabelledAndReportsItsErrorOnItsOwnNode() = runComposeUiTest {
        withTheme { NumericField("85", {}, NumericKind.Load, "Load", error = "Too heavy") }
        waitForIdle()
        val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals("85", config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals(listOf("Load"), config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Error: Too heavy", config.getOrNull(SemanticsProperties.Error))
        onNode(androidx.compose.ui.test.hasText("Too heavy"), useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun targetIsFiftySixAndTheBoxSeventyTwoByFortyEight() = runComposeUiTest {
        withTheme { NumericField("", {}, NumericKind.Reps, "Reps") }
        waitForIdle()
        val touch = onNode(hasSetTextAction()).fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f, "touch ${touch.height} < $target")
        val box = onNodeWithTag(NumericFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sizes = MoosclesSizes()
        assertEquals(with(density) { sizes.numericFieldWidth.toPx() }, box.width, 0.5f)
        assertEquals(with(density) { sizes.numericFieldHeight.toPx() }, box.height, 0.5f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onlyTextThatCanBecomeANumberIsPassedOn() = runComposeUiTest {
        var text by mutableStateOf("")
        withTheme { NumericField(text, { text = it }, NumericKind.Load, "Load") }
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("82,5")
        waitForIdle()
        assertEquals("82,5", text)
        onNode(hasSetTextAction()).performTextInput("kg")
        waitForIdle()
        assertEquals("82,5", text)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectedShowsTheRingWithoutFocus() {
        for (selected in listOf(true, false)) {
            runComposeUiTest {
                withTheme { NumericField("85", {}, NumericKind.Load, "Load", selected = selected) }
                waitForIdle()
                onNode(hasSetTextAction()).assertIsNotFocused()
                val box = onNodeWithTag(NumericFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().config
                assertEquals(selected, box.getOrNull(FocusRingVisible), "selected=$selected")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun imeActionCallsBack() {
        for (action in listOf(ImeAction.Next, ImeAction.Done)) {
            runComposeUiTest {
                var calls = 0
                withTheme { NumericField("85", {}, NumericKind.Load, "Load", imeAction = action, onImeAction = { calls++ }) }
                waitForIdle()
                val field = onNode(hasSetTextAction())
                assertEquals(action, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ImeAction))
                field.performImeAction()
                waitForIdle()
                assertEquals(1, calls, "$action")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusRequesterFocusesTheField() = runComposeUiTest {
        val focus = FocusRequester()
        withTheme { NumericField("8", {}, NumericKind.Reps, "Reps", focusRequester = focus) }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsNotFocused()
        runOnUiThread { focus.requestFocus() }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
        val box = onNodeWithTag(NumericFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(true, box.getOrNull(FocusRingVisible))
    }

    /** Review M4: with Next only the callback runs; the field does not move the focus by itself. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nextLeavesTheFocusToTheCaller() = runComposeUiTest {
        val first = FocusRequester()
        var calls = 0
        withTheme {
            Column {
                NumericField("85", {}, NumericKind.Load, "Load", imeAction = ImeAction.Next, onImeAction = { calls++ }, focusRequester = first)
                NumericField("8", {}, NumericKind.Reps, "Reps")
            }
        }
        waitForIdle()
        runOnUiThread { first.requestFocus() }
        waitForIdle()
        onNode(hasSetTextAction() and hasContentDescription("Load")).performImeAction()
        waitForIdle()
        assertEquals(1, calls)
        onNode(hasSetTextAction() and hasContentDescription("Load")).assertIsFocused()
        onNode(hasSetTextAction() and hasContentDescription("Reps")).assertIsNotFocused()
    }

    /** Review M4: Done only closes the keyboard; the field keeps the focus. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doneKeepsTheFocus() = runComposeUiTest {
        val focus = FocusRequester()
        var calls = 0
        withTheme {
            Column {
                NumericField("8", {}, NumericKind.Reps, "Reps", imeAction = ImeAction.Done, onImeAction = { calls++ }, focusRequester = focus)
                NumericField("85", {}, NumericKind.Load, "Load")
            }
        }
        waitForIdle()
        runOnUiThread { focus.requestFocus() }
        waitForIdle()
        onNode(hasSetTextAction() and hasContentDescription("Reps")).performImeAction()
        waitForIdle()
        assertEquals(1, calls)
        onNode(hasSetTextAction() and hasContentDescription("Reps")).assertIsFocused()
        onNode(hasSetTextAction() and hasContentDescription("Load")).assertIsNotFocused()
    }
}
