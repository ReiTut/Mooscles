package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MBottomSheetTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrimTapAndBackClose() {
        runComposeUiTest {
            var dismissed = 0
            withTheme { MBottomSheet(onDismiss = { dismissed++ }) { MText("Sheet", MTheme.type.bodyLg) } }
            waitForIdle()
            onNodeWithTag(SheetScrimTag).performTouchInput { click(Offset(2f, 2f)) }
            waitForIdle()
            assertEquals(1, dismissed, "a tap on the scrim closes")
        }
        runComposeUiTest {
            var dismissed = 0
            withTheme { MBottomSheet(onDismiss = { dismissed++ }) { MText("Sheet", MTheme.type.bodyLg) } }
            waitForIdle()
            onNodeWithTag(SheetTag).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(1, dismissed, "back/escape closes")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun heightIsCappedAtNinetyPercent() = runComposeUiTest {
        withTheme {
            MBottomSheet(onDismiss = {}) { Spacer(Modifier.fillMaxWidth().height(4_000.dp)) }
        }
        waitForIdle()
        val screen = onNodeWithTag(SheetScrimTag).getBoundsInRoot().let { (it.bottom - it.top).value }
        val sheet = onNodeWithTag(SheetTag).getBoundsInRoot().let { (it.bottom - it.top).value }
        assertTrue(sheet <= screen * 0.9f + 1f, "sheet $sheet of screen $screen")
        assertTrue(sheet >= screen * 0.9f - 1f, "tall content fills the cap: sheet $sheet of screen $screen")
    }

    /**
     * The scrim is clickable, so it is named ("Dismiss", as Material's "Close sheet"): a screen
     * reader never lands on a nameless control. It is read after the content (traversal index 1).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrimIsNamedDismissAndReadLast() = runComposeUiTest {
        withTheme { MBottomSheet(onDismiss = {}) { MText("Sheet", MTheme.type.bodyLg) } }
        waitForIdle()
        onNodeWithTag(SheetScrimTag).assertContentDescriptionEquals("Dismiss")
        assertEquals(1f, onNodeWithTag(SheetScrimTag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
    }

    /**
     * The sheet is a window of its own; its content takes the host's density and font scale (on
     * Android the window would take the configuration's, and a 2x test would run at 1x). A density
     * of 2 differs from the emulator's and from the simulator's 1, so a window's own cannot pass.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun contentTakesTheHostDensityAndFontScale() = runComposeUiTest {
        withTheme(fontScale = 2f, density = 2f) { MBottomSheet(onDismiss = {}) { MText("Sheet", MTheme.type.bodyLg) } }
        waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        val action = onNodeWithText("Sheet", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        val density = results.first().layoutInput.density
        assertEquals(2f to 2f, density.density to density.fontScale)
    }

    /**
     * `initialFocus` takes the focus once the sheet is open, and is asked once: when the focus then
     * moves to another field, it is not pulled back (however many frames pass).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun initialFocusIsRequestedOnceAndNeverAgain() = runComposeUiTest {
        val first = FocusRequester()
        val second = FocusRequester()
        val firstGains = mutableIntStateOf(0)
        val recompositions = mutableIntStateOf(0)
        withTheme {
            MBottomSheet(onDismiss = {}, initialFocus = first) {
                // Read in the sheet's content: bumping it recomposes the content, where a wrongly keyed effect would ask again.
                val tick = recompositions.intValue
                BasicTextField(
                    value = "one",
                    onValueChange = {},
                    modifier = Modifier.testTag("first-field").focusRequester(first).onFocusChanged { if (it.isFocused) firstGains.intValue++ },
                )
                BasicTextField(value = "two$tick", onValueChange = {}, modifier = Modifier.testTag("second-field").focusRequester(second))
            }
        }
        waitForIdle()
        onNodeWithTag("first-field").assertIsFocused()
        assertEquals(1, firstGains.intValue, "the field took the focus once")

        runOnIdle { second.requestFocus() }
        waitForIdle()
        onNodeWithTag("second-field").assertIsFocused()
        // The content recomposes (twice), time passes, frames pass: nothing asks the first field again.
        repeat(2) {
            runOnIdle { recompositions.intValue++ }
            waitForIdle()
        }
        onNodeWithTag("second-field").assertTextContains("two2")
        repeat(90) { mainClock.advanceTimeByFrame() }
        waitForIdle()
        onNodeWithTag("second-field").assertIsFocused()
        onNodeWithTag("first-field").assertIsNotFocused()
        assertEquals(1, firstGains.intValue, "the focus was not pulled back")
    }
}
