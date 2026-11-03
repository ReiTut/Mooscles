package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_loading
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

class MButtonTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everySizeHasAtLeastTheMinimumTarget() {
        for (dark in listOf(false, true)) {
            for (size in MButtonSize.entries) {
                runComposeUiTest {
                    withTheme(dark = dark) { MButton("Save", onClick = {}, size = size, modifier = Modifier.testTag("b")) }
                    waitForIdle()
                    val touch = onNodeWithTag("b").fetchSemanticsNode().touchBoundsInRoot
                    val minPx = with(density) { MoosclesTargets().min.toPx() }
                    assertTrue(touch.height >= minPx - 0.5f, "touch height ${touch.height} < $minPx")
                    if (size == MButtonSize.L) onNodeWithTag("b").assertHeightIsEqualTo(MoosclesSizes().buttonL)
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingKeepsWidthIsNotClickableAndSaysLoading() = runComposeUiTest {
        var loading by mutableStateOf(false)
        var clicks = 0
        withTheme { MButton("Save", onClick = { clicks++ }, loading = loading, modifier = Modifier.testTag("b")) }
        waitForIdle()
        val before = onNodeWithTag("b").fetchSemanticsNode().size.width
        runOnUiThread { loading = true }
        waitForIdle()
        val after = onNodeWithTag("b").fetchSemanticsNode().size.width
        assertEquals(before.toFloat(), after.toFloat(), 0.5f)
        onNodeWithTag("b").performClick()
        waitForIdle()
        assertEquals(0, clicks)
        onNodeWithTag("b").assertContentDescriptionContains(getString(Res.string.ds_loading))
        onNodeWithTag("b").assertIsNotEnabled()
        onAllNodesWithText("Save").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun disabledIsNotEnabled() = runComposeUiTest {
        var clicks = 0
        withTheme { MButton("Save", onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("b")) }
        waitForIdle()
        onNodeWithTag("b").assertIsNotEnabled()
        onNodeWithTag("b").performClick()
        assertEquals(0, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun labelNeverOverflowsAtTwoHundredPercentOn320dp() = runComposeUiTest {
        val label = "Finish the workout and save it"
        withTheme(fontScale = 2f) {
            Box(Modifier.width(320.dp)) { MButton(label, onClick = {}, size = MButtonSize.L) }
        }
        waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        val node = onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        assertFalse(results.first().hasVisualOverflow)
        assertTrue(results.first().lineCount > 1, "the label should wrap at 200%")
    }
}
