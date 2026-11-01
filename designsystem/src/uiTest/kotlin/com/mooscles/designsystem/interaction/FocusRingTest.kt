package com.mooscles.designsystem.interaction

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class FocusRingTest {
    private fun ringIs(visible: Boolean) = SemanticsMatcher.expectValue(FocusRingVisible, visible)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusedControlShowsTheRingAndBlurredDoesNot() = runComposeUiTest {
        val requester = FocusRequester()
        lateinit var focusManager: FocusManager
        withTheme {
            focusManager = LocalFocusManager.current
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .testTag("control")
                    .size(MTheme.targets.min)
                    .mFocusRing(RoundedCornerShape(MTheme.radius.md), source)
                    .focusRequester(requester)
                    .focusable(interactionSource = source),
            )
        }
        waitForIdle()
        // Some platforms hand the first focusable the initial focus: start from a blurred control.
        runOnUiThread { focusManager.clearFocus(force = true) }
        waitForIdle()
        onNodeWithTag("control").assert(ringIs(false))
        runOnUiThread { requester.requestFocus() }
        waitForIdle()
        onNodeWithTag("control").assert(ringIs(true))
        runOnUiThread { focusManager.clearFocus(force = true) }
        waitForIdle()
        onNodeWithTag("control").assert(ringIs(false))
    }
}
