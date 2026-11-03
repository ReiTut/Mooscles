package com.mooscles.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.theme.MTheme
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** [assertShownWhole] fails when it should, so the tests that lean on it cannot pass in the void. */
@OptIn(ExperimentalTestApi::class)
class TextFitTest {
    private fun ComposeUiTest.layoutOf(text: String, width: Dp, oneLine: Boolean): TextLayoutResult {
        withTheme {
            Box(Modifier.width(width)) {
                MText(
                    text,
                    if (oneLine) MTheme.type.numDock else MTheme.type.bodyMd,
                    softWrap = !oneLine,
                    maxLines = if (oneLine) 1 else Int.MAX_VALUE,
                )
            }
        }
        waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        val action = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        return results.first()
    }

    @Test
    fun passesForATextWithRoom() = runComposeUiTest {
        assertShownWhole(layoutOf("00:00", 300.dp, oneLine = true))
    }

    @Test
    fun failsForAOneLineTextNarrowerThanItNeeds() = runComposeUiTest {
        val layout = layoutOf("00:00", 20.dp, oneLine = true)
        assertFails { assertShownWhole(layout) }
    }

    @Test
    fun failsForATextThatWraps() = runComposeUiTest {
        val layout = layoutOf("Rest complete for now", 60.dp, oneLine = false)
        assertFails { assertShownWhole(layout) }
    }
}
