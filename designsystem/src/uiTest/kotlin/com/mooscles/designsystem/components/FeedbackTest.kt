package com.mooscles.designsystem.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.withTheme
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.tokens.MoosclesTiming
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FeedbackTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorBannerIsAPoliteLiveRegionWithIcon() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                withTheme(dark = dark) { ErrorBanner("Could not save", Modifier.testTag("banner")) }
                waitForIdle()
                val config = onNodeWithTag("banner").fetchSemanticsNode().config
                assertEquals(LiveRegionMode.Polite, config.getOrNull(SemanticsProperties.LiveRegion))
                onNodeWithTag(ErrorBannerIconTag, useUnmergedTree = true).assertExists()
                onNodeWithText("Error: Could not save").assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptyStateHasOnePrimaryAction() = runComposeUiTest {
        var started = 0
        withTheme {
            EmptyState(MIcons.FileText, "No workouts", "Finish one", primaryAction = "Start" to { started++ })
        }
        waitForIdle()
        onNodeWithText("No workouts").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(1, started)
        // The secondary action is optional and absent, so there is exactly one button.
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorBannerActionIsClickableAndHasAFullTouchTarget() = runComposeUiTest {
        var retried = 0
        withTheme { ErrorBanner("Could not save", actionLabel = "Retry", onAction = { retried++ }) }
        waitForIdle()
        val touch = onNodeWithText("Retry").fetchSemanticsNode().let { node ->
            // The button node is the clickable ancestor (or the text itself when merged).
            onAllNodes(hasClickAction()).onFirst().fetchSemanticsNode().touchBoundsInRoot
        }
        val minPx = with(density) { MoosclesTargets().min.toPx() }
        assertTrue(touch.height >= minPx - 0.5f, "touch height ${touch.height} < $minPx")
        onAllNodes(hasClickAction()).onFirst().performClick()
        assertEquals(1, retried)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun skeletonPulsesWithoutReducedMotion() = runComposeUiTest {
        mainClock.autoAdvance = false
        withTheme { Skeleton(Modifier.testTag("s")) }
        waitForIdle()
        mainClock.advanceTimeBy(MoosclesTiming().skeletonPulse / 2)
        val alpha = onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SkeletonAlpha)
        assertTrue(alpha != null && alpha < 1f, "alpha $alpha did not pulse")
        assertTrue(alpha >= MoosclesSizes().skeletonMinAlpha - 0.01f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun skeletonIsStillWithReducedMotion() = runComposeUiTest {
        mainClock.autoAdvance = false
        withTheme(reducedMotion = true) { Skeleton(Modifier.testTag("s")) }
        waitForIdle()
        val before = onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SkeletonAlpha)
        mainClock.advanceTimeBy(MoosclesTiming().skeletonPulse)
        val after = onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SkeletonAlpha)
        assertEquals(before, after)
        assertEquals(1f, after)
    }
}
