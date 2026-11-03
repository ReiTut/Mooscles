package com.mooscles.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.mooscles.designsystem.assertShownWhole
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MDialogTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun confirmIsTheLastActionAndCallsConfirm() = runComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        withTheme {
            MDialog(
                title = "Finish?",
                text = "Saved to history.",
                confirmLabel = "Finish",
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ },
                dismissLabel = "Keep going",
            )
        }
        waitForIdle()
        val dismissBounds = onNodeWithTag(DialogDismissTag).getBoundsInRoot()
        val confirmBounds = onNodeWithTag(DialogConfirmTag).getBoundsInRoot()
        assertTrue(confirmBounds.left >= dismissBounds.right, "the confirmation is at the end")
        onNodeWithTag(DialogConfirmTag).performClick()
        waitForIdle()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nullTextLeavesNoTextNodeAndNoGap() = runComposeUiTest {
        withTheme { MDialog("Delete?", null, "Delete", onConfirm = {}, onDismiss = {}, dismissLabel = "Cancel") }
        waitForIdle()
        val texts = onAllNodes(hasAnyAncestor(hasTestTag(DialogTag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().map { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text } }
        assertEquals(listOf("Delete?", "Cancel", "Delete"), texts)
        // Title, then the column's gap (space.s3) and the buttons' top padding (space.s2): nothing between.
        val gap = onNodeWithTag(DialogDismissTag).getBoundsInRoot().top - onNodeWithText("Delete?").getBoundsInRoot().bottom
        assertTrue(gap <= 21.dp, "gap $gap")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backAndScrimDismiss() {
        runComposeUiTest {
            var dismissed = 0
            withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = { dismissed++ }) }
            waitForIdle()
            onNodeWithTag(DialogTag).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(1, dismissed, "the back/escape key dismisses")
        }
        runComposeUiTest {
            var dismissed = 0
            withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = { dismissed++ }) }
            waitForIdle()
            onNodeWithTag(DialogScrimTag).performTouchInput { click(Offset(2f, 2f)) }
            waitForIdle()
            assertEquals(1, dismissed, "a tap on the scrim dismisses")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusMovesIntoTheDialog() = runComposeUiTest {
        withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = {}) }
        waitForIdle()
        onNode(isFocused()).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun destructiveUsesTheFilledDangerButton() = runComposeUiTest {
        withTheme { MDialog("Delete?", "Gone.", "Delete", onConfirm = {}, onDismiss = {}, destructive = true) }
        waitForIdle()
        onNodeWithTag(DialogConfirmDangerTag).assertExists()
        onNodeWithTag(DialogConfirmTag).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aDialogTallerThanTheWindowScrollsToItsActions() = runComposeUiTest {
        var confirmed = 0
        val text = List(40) { "Every set logged in this workout will be removed for good." }.joinToString(" ")
        withTheme(fontScale = 2f) {
            MDialog("Discard workout?", text, "Discard", onConfirm = { confirmed++ }, onDismiss = {}, dismissLabel = "Keep training")
        }
        waitForIdle()
        onNodeWithTag(DialogConfirmTag).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DialogConfirmTag).performClick()
        waitForIdle()
        assertEquals(1, confirmed)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.labelLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result for $label")
        return results.first()
    }

    /** At 200 % the two labels do not fit on one line together: the confirmation wraps below, whole, still last. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longActionLabelsWrapInsteadOfSqueezing() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            MDialog("Discard?", "Sets are lost.", "Discard workout", onConfirm = {}, onDismiss = {}, dismissLabel = "Continue training")
        }
        waitForIdle()
        val card = onNodeWithTag(DialogTag).getBoundsInRoot()
        val dismiss = onNodeWithTag(DialogDismissTag).getBoundsInRoot()
        val confirm = onNodeWithTag(DialogConfirmTag).getBoundsInRoot()
        for ((name, bounds) in listOf("dismiss" to dismiss, "confirm" to confirm)) {
            assertTrue(bounds.left >= card.left && bounds.right <= card.right, "$name $bounds outside the card $card")
        }
        assertTrue(confirm.top >= dismiss.bottom || confirm.left >= dismiss.right, "the confirmation is not last: $dismiss, $confirm")
        assertShownWhole(labelLayout("Continue training"), "dismiss")
        assertShownWhole(labelLayout("Discard workout"), "confirm")
    }

    /**
     * The scrim is clickable, so it is named ("Dismiss", as Material's "Close sheet"): a screen
     * reader never lands on a nameless control. It is read after the content (traversal index 1).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrimIsNamedDismissAndReadLast() = runComposeUiTest {
        withTheme { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = {}) }
        waitForIdle()
        onNodeWithTag(DialogScrimTag).assertContentDescriptionEquals("Dismiss")
        assertEquals(1f, onNodeWithTag(DialogScrimTag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
    }

    /**
     * The dialog is a window of its own; its content takes the host's density and font scale (on
     * Android the window would take the configuration's, and a 2x test would run at 1x). A density
     * of 2 differs from the emulator's and from the simulator's 1, so a window's own cannot pass.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun contentTakesTheHostDensityAndFontScale() = runComposeUiTest {
        withTheme(fontScale = 2f, density = 2f) { MDialog("Finish?", "Saved.", "Finish", onConfirm = {}, onDismiss = {}) }
        waitForIdle()
        val density = labelLayout("Finish?").layoutInput.density
        assertEquals(2f to 2f, density.density to density.fontScale)
    }
}
