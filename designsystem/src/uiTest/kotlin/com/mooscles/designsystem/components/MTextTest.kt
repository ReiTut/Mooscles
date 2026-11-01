package com.mooscles.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MTextTest {
    private val original = "Rest timer wide"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun capsIsVisualOnlyAndSemanticsKeepTheOriginalText() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                withTheme(dark = dark) {
                    MText(original, MTheme.type.labelCaps, Modifier.testTag("caps"))
                    MText(original, MTheme.type.labelCaps, Modifier.testTag("plain"), caps = false)
                }
                waitForIdle()
                val capsWidth = onNodeWithTag("caps").fetchSemanticsNode().size.width
                val plainWidth = onNodeWithTag("plain").fetchSemanticsNode().size.width
                assertTrue(capsWidth > plainWidth, "upper case should draw wider: $capsWidth vs $plainWidth")
                onNodeWithTag("caps").assertExists()
                onAllNodesWithText(original.uppercase()).assertCountEquals(0)
                onAllNodesWithText(original).assertCountEquals(2)
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun explicitCapsSurvivesAStyleCopy() = runComposeUiTest {
        withTheme {
            MText(original, MTheme.type.labelCaps, Modifier.testTag("default"))
            MText(original, MTheme.type.labelCaps.copy(letterSpacing = MTheme.type.labelCaps.letterSpacing), Modifier.testTag("copy"), caps = true)
            MText(original, MTheme.type.labelCaps.copy(letterSpacing = MTheme.type.labelCaps.letterSpacing), Modifier.testTag("copyNoCaps"), caps = false)
        }
        waitForIdle()
        val a = onNodeWithTag("default").fetchSemanticsNode().size.width
        assertEquals(a, onNodeWithTag("copy").fetchSemanticsNode().size.width)
        assertTrue(onNodeWithTag("copyNoCaps").fetchSemanticsNode().size.width < a)
        onAllNodesWithText(original).assertCountEquals(3)
    }
}
