package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PrToastTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dismissesAfterFourSecondsWithoutAction() = runComposeUiTest {
        var dismissed = 0
        var visible by mutableStateOf(true)
        mainClock.autoAdvance = false
        withTheme { PrToast("New record: Bench press", "85 kg × 6", visible, { dismissed++; visible = false }) }
        mainClock.advanceTimeByFrame()
        onNodeWithTag(PrToastTag).assertExists()
        // No button, nothing to tap: the toast has no click action.
        assertFalse(SemanticsActions.OnClick in onNodeWithTag(PrToastTag).fetchSemanticsNode().config)
        val hold = MoosclesTiming().prToast
        mainClock.advanceTimeBy(hold - 100)
        assertEquals(0, dismissed)
        mainClock.advanceTimeBy(200)
        assertEquals(1, dismissed)
        mainClock.advanceTimeByFrame()
        onNodeWithTag(PrToastTag).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reducedMotionUsesFade() = runComposeUiTest {
        withTheme(reducedMotion = true) { PrToast("t", "d", true, {}) }
        waitForIdle()
        onNodeWithTag(PrToastTag).assert(SemanticsMatcher.expectValue(MotionChoiceUsed, "Fade"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun movingMotionUsesSpring() = runComposeUiTest {
        withTheme(reducedMotion = false) { PrToast("t", "d", true, {}) }
        waitForIdle()
        onNodeWithTag(PrToastTag).assert(SemanticsMatcher.expectValue(MotionChoiceUsed, "Spring"))
    }
}
