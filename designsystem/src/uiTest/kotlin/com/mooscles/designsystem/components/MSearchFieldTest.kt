package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.restoreTouchMode
import com.mooscles.designsystem.tokens.MoosclesSpace
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MSearchFieldTest {
    // A test here asks for keyboard mode, which outlives it on Android: give the next tests touch mode.
    @AfterTest
    fun backToTouchMode() = restoreTouchMode()

    private val placeholder = "Search by name, muscle or equipment"
    private val clear = hasContentDescription("Clear search")

    /** An empty field that owns its value, as a screen does; clearing empties it. */
    private fun ComposeUiTest.searchField() {
        var value by mutableStateOf(TextFieldValue(""))
        withTheme {
            MSearchField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
                onClear = { value = TextFieldValue("") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        waitForIdle()
    }

    private fun ComposeUiTest.field() = onNode(hasSetTextAction())

    @Test
    fun clearShowsOnlyWithText() = runComposeUiTest {
        searchField()
        onNode(clear).assertDoesNotExist()
        field().performTextInput("bench")
        waitForIdle()
        val button = onNode(clear).assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val touch = button.fetchSemanticsNode().touchBoundsInRoot
        val min = with(density) { MoosclesTargets().min.toPx() } - 0.5f
        assertTrue(touch.width >= min && touch.height >= min, "clear is ${touch.width} x ${touch.height} px")
        // 8 dp between the field's target and the button's.
        val gap = touch.left - field().fetchSemanticsNode().boundsInRoot.right
        val space = with(density) { MoosclesSpace().s2.toPx() } - 0.5f
        assertTrue(gap >= space, "the clear button is $gap px after the field")
        field().performTextReplacement("")
        waitForIdle()
        onNode(clear).assertDoesNotExist()
    }

    /**
     * By touch the field keeps the focus; from the keyboard the focus is on the clear button, which
     * goes away with the text, so the focus is moved back into the field instead of being lost.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun clearEmptiesAndKeepsFocus() = runComposeUiTest {
        val clears = mutableListOf<Unit>()
        var inputMode: InputModeManager? = null
        var value by mutableStateOf(TextFieldValue(""))
        withTheme {
            inputMode = LocalInputModeManager.current
            MSearchField(value, { value = it }, placeholder, onClear = { clears += Unit; value = TextFieldValue("") }, Modifier.fillMaxWidth())
        }
        waitForIdle()
        field().performClick()
        field().performTextInput("bench")
        waitForIdle()
        field().assertIsFocused()
        onNode(clear).performClick()
        waitForIdle()
        assertEquals(1, clears.size)
        assertEquals("", value.text)
        field().assertIsFocused()
        onNode(clear).assertDoesNotExist()

        field().performTextInput("row")
        waitForIdle()
        runOnUiThread { inputMode?.requestInputMode(InputMode.Keyboard) }
        onNode(clear).requestFocus()
        waitForIdle()
        onNode(clear).assertIsFocused()
        onNode(clear).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(2, clears.size)
        assertEquals("", value.text)
        field().assertIsFocused()
    }

    /** The placeholder names the field: drawn while it is empty, and kept as its name once it has text. */
    @Test
    fun placeholderIsTheLabelWhileEmpty() = runComposeUiTest {
        searchField()
        fun texts() = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        onNodeWithTag(SearchFieldPlaceholderTag, useUnmergedTree = true).assertExists()
        assertEquals(listOf(placeholder), texts())
        assertEquals("", field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        field().performTextInput("bench")
        waitForIdle()
        onNodeWithTag(SearchFieldPlaceholderTag, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf(placeholder), texts(), "the typed field keeps its name")
        assertEquals("bench", field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
    }

    @Test
    fun hasTheSetTextAction() = runComposeUiTest {
        searchField()
        val config = field().fetchSemanticsNode().config
        assertEquals(ImeAction.Search, config.getOrNull(SemanticsProperties.ImeAction))
        assertTrue(SemanticsActions.OnImeAction in config, "no IME action")
        field().performTextReplacement("supino")
        waitForIdle()
        assertEquals("supino", field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        // Search runs while typing: the IME action only closes the keyboard, and never changes the text.
        field().performImeAction()
        waitForIdle()
        assertEquals("supino", field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
    }

    private fun ComposeUiTest.layoutOf(tag: String?): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = if (tag == null) field().fetchSemanticsNode() else onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "no text layout for $tag")
        return results.first()
    }

    /**
     * At 200% (and at the iOS linear 2x, ruling F23) in a 360 dp column, the placeholder of either
     * language wraps inside the grown box instead of being cut, and a typed query is shown whole.
     */
    @Test
    fun textStaysWholeAtTwoX() {
        for (text in listOf(placeholder, "Buscar por nome, músculo ou equipamento")) {
            for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
                runComposeUiTest {
                    var value by mutableStateOf(TextFieldValue(""))
                    withTheme(fontScale = scale, density = density) {
                        Box(Modifier.width(360.dp)) {
                            MSearchField(value, { value = it }, text, onClear = { value = TextFieldValue("") }, Modifier.fillMaxWidth())
                        }
                    }
                    waitForIdle()
                    val where = "\"$text\" scale=$scale density=$density"
                    val hint = layoutOf(SearchFieldPlaceholderTag)
                    assertFalse(hint.multiParagraph.didExceedMaxLines, "$where: the placeholder is cut")
                    assertTrue(hint.multiParagraph.height <= hint.size.height + 0.5f, "$where: the placeholder is clipped")
                    val box = onNodeWithTag(SearchFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val shown = onNodeWithTag(SearchFieldPlaceholderTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        shown.top >= box.top - 0.5f && shown.bottom <= box.bottom + 0.5f && shown.left >= box.left && shown.right <= box.right + 0.5f,
                        "$where: placeholder $shown outside the box $box",
                    )
                    field().performTextInput("Supino")
                    waitForIdle()
                    assertShownWhole(layoutOf(null), where)
                    val typed = field().fetchSemanticsNode().boundsInRoot
                    val grown = onNodeWithTag(SearchFieldBoxTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(typed.top >= grown.top - 0.5f && typed.bottom <= grown.bottom + 0.5f, "$where: the field $typed outside the box $grown")
                }
            }
        }
    }
}
