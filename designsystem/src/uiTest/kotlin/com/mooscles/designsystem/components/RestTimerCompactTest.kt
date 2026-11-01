package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RestTimerCompactTest {
    private fun visual(seconds: Int, phase: TimerPhase = TimerPhase.Running) = RestTimerVisual(seconds, 120, phase, "Rest")

    private fun ComposeUiTest.timeLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        return results.first()
    }

    @Test
    fun isFiftySixTallWithRingTimeAndSkip() = runComposeUiTest {
        var skipped = 0
        var opened = 0
        withTheme {
            ProvideAppLocale("en") {
                Box(Modifier.width(360.dp)) { RestTimerCompact(visual(84), { skipped++ }, onOpen = { opened++ }) }
            }
        }
        waitForIdle()
        val compact = onNodeWithTag(TimerCompactTag).fetchSemanticsNode().boundsInRoot
        val row = with(density) { MoosclesSizes().rowMin.toPx() }
        assertEquals(row, compact.height, 1f, "the compact timer is one 56 dp row")
        val ring = onNodeWithTag(TimerCompactRingTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ringSize = with(density) { MoosclesSizes().ringBar.toPx() }
        assertEquals(ringSize, ring.width, 1f, "the small ring")
        assertEquals("tnum", timeLayout().layoutInput.style.fontFeatureSettings)
        val skip = onNodeWithTag(TimerSkipTag).assertContentDescriptionEquals("Skip rest").fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(skip.width >= target - 0.5f && skip.height >= target - 0.5f, "skip ${skip.width} x ${skip.height}")
        onNodeWithTag(TimerSkipTag).performClick()
        onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals("Open rest timer, 1 minute and 24 seconds remaining").performClick()
        waitForIdle()
        assertEquals(1, skipped)
        assertEquals(1, opened)
    }

    @Test
    fun timeIsWholeAtTwoHundredOn320dp() {
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            for (seconds in listOf(84, 300)) {
                runComposeUiTest {
                    withTheme(fontScale = scale, density = density) {
                        Box(Modifier.width(320.dp)) { RestTimerCompact(visual(seconds), {}, onOpen = {}) }
                    }
                    waitForIdle()
                    val where = "scale $scale, $seconds s"
                    assertShownWhole(timeLayout(), where)
                    val compact = onNodeWithTag(TimerCompactTag).fetchSemanticsNode().boundsInRoot
                    val time = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val skip = onNodeWithTag(TimerSkipTag).fetchSemanticsNode().boundsInRoot
                    assertTrue(time.right <= skip.left + 0.5f, "$where: the time runs under skip")
                    assertTrue(skip.right <= compact.right + 0.5f, "$where: skip outside the row")
                }
            }
        }
    }

    @Test
    fun hasNoAdjustButtons() = runComposeUiTest {
        withTheme { RestTimerCompact(visual(84), {}) }
        waitForIdle()
        onNodeWithTag(TimerMinusTag).assertDoesNotExist()
        onNodeWithTag(TimerPlusTag).assertDoesNotExist()
        onNodeWithTag(TimerSkipTag).assertExists()
    }

    /** Task 13 review M1, on the compact timer: paused or done, the open button says so; running, no state. */
    @Test
    fun openLabelReadsPausedAndDone() {
        val cases = listOf(
            visual(84) to ("Open rest timer, 1 minute and 24 seconds remaining" to null),
            visual(84, TimerPhase.Paused) to ("Open rest timer, 1 minute and 24 seconds remaining" to "Paused"),
            visual(0, TimerPhase.Done) to ("Open rest timer" to "Rest complete"),
        )
        for ((shown, expected) in cases) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { RestTimerCompact(shown, {}, onOpen = {}) } }
                waitForIdle()
                val body = onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals(expected.first)
                assertEquals(expected.second, body.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
            }
        }
    }

    /** Task 13 review M2, on the compact timer: the body that opens the timer answers a press. */
    @Test
    fun bodyPressScales() = runComposeUiTest {
        withTheme { Box(Modifier.width(360.dp)) { RestTimerCompact(visual(84), {}, onOpen = {}) } }
        waitForIdle()
        val rest = onNodeWithTag(TimerOpenTag).fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithTag(TimerOpenTag).performTouchInput { down(center) }
        mainClock.advanceTimeBy(MoosclesMotion().press + 100L)
        val pressed = onNodeWithTag(TimerOpenTag).fetchSemanticsNode().boundsInRoot
        onNodeWithTag(TimerOpenTag).performTouchInput { cancel() }
        mainClock.autoAdvance = true
        val expected = rest.width * MoosclesMotion().pressScale
        assertTrue(kotlin.math.abs(pressed.width - expected) < 2f, "pressed ${pressed.width}, at rest ${rest.width}, expected $expected")
    }
}
