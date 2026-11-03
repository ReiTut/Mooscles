package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import kotlin.test.assertEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class RestTimerExpandedTest {
    private val visual = RestTimerVisual(84, 120, TimerPhase.Running, "Rest")

    @androidx.compose.runtime.Composable
    private fun Expanded(
        visual: RestTimerVisual,
        upNext: String? = null,
        onMinus: () -> Unit = {},
        onPlus: () -> Unit = {},
        onSkip: () -> Unit = {},
        title: String = "Rest · Bench press · set 4 of 5",
        totalText: String? = "2:00",
        onPauseResume: () -> Unit = {},
        onRestart: () -> Unit = {},
        onClose: () -> Unit = {},
        canAdd: Boolean = true,
    ) = RestTimerExpanded(
        visual, upNext, onMinus, onPlus, onSkip,
        title = title, totalText = totalText, onPauseResume = onPauseResume, onRestart = onRestart, onClose = onClose,
        canAdd = canAdd,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun discsAreEightyAndLabelled() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { Expanded(visual) } }
        waitForIdle()
        val disc = with(density) { MoosclesSizes().timerDisc.toPx() }
        for (tag in listOf(TimerMinusTag, TimerPlusTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(kotlin.math.abs(touch.width - disc) < 1f && kotlin.math.abs(touch.height - disc) < 1f, "$tag ${touch.width} x ${touch.height} != $disc")
        }
        onNodeWithTag(TimerMinusTag).assertContentDescriptionEquals("Minus 15 seconds")
        onNodeWithTag(TimerPlusTag).assertContentDescriptionEquals("Plus 15 seconds")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsUpNextWhenGiven() = runComposeUiTest {
        val next: String? = "Incline press"
        withTheme { ProvideAppLocale("en") { Expanded(visual, upNext = next) } }
        waitForIdle()
        onNodeWithTag(TimerUpNextTag).assertExists()
        onNodeWithText("Incline press", useUnmergedTree = true).assertExists()
        onNodeWithText("Up next", useUnmergedTree = true).assertExists()
        onNodeWithText("Skip rest", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesUpNextWhenAbsent() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { Expanded(visual) } }
        waitForIdle()
        onNodeWithTag(TimerUpNextTag).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusAndMinusTick() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var minus = 0
        var plus = 0
        withTheme(haptics = haptics) { Expanded(visual, onMinus = { minus++ }, onPlus = { plus++ }) }
        waitForIdle()
        onNodeWithTag(TimerMinusTag).performClick()
        onNodeWithTag(TimerPlusTag).performClick()
        waitForIdle()
        assertEquals(1, minus)
        assertEquals(1, plus)
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun timeFitsInsideTheRingAtTwoHundredPercent() {
        for (scale in listOf(1f, 1.3f, 2f)) {
            runComposeUiTest {
                withTheme(fontScale = scale) {
                    Box(Modifier.width(360.dp)) { Expanded(RestTimerVisual(600, 900, TimerPhase.Running, "Rest")) }
                }
                waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
                val action = node.config[SemanticsActions.GetTextLayoutResult].action
                assertTrue(action != null && action(results), "no text layout result")
                assertShownWhole(results.first(), "scale $scale")
                val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                val time = node.boundsInRoot
                val clear = with(density) { (MoosclesSizes().expandedRing - (MoosclesSizes().tickLength + 4.dp + MoosclesSizes().expandedRingStroke) * 2).toPx() }
                assertTrue(time.width <= clear + 1f, "scale $scale: time ${time.width} wider than the clear ring $clear")
                assertTrue(time.left >= ring.left && time.right <= ring.right, "scale $scale: time outside the ring")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun discLabelsAreShownWholeAtTwoHundredPercent() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            ProvideAppLocale("en") {
                Box(Modifier.width(360.dp)) { Expanded(visual) }
            }
        }
        waitForIdle()
        val disc = with(density) { MoosclesSizes().timerDisc.toPx() }
        for ((tag, label) in listOf(TimerMinusTag to "\u221215 s", TimerPlusTag to "+15 s")) {
            val results = mutableListOf<TextLayoutResult>()
            val node = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val action = node.config[SemanticsActions.GetTextLayoutResult].action
            assertTrue(action != null && action(results), "no text layout result for $label")
            assertShownWhole(results.first(), tag)
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.width >= disc - 0.5f && touch.height >= disc - 0.5f, "$tag ${touch.width} x ${touch.height} smaller than $disc")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restFinishedOnlyOnTheTurnToDoneNotWhenShownAlreadyDone() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(RestTimerVisual(0, 120, TimerPhase.Done, "Rest"))
        withTheme(haptics = haptics) { Expanded(state) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events, "shown already done")
        state = RestTimerVisual(30, 120, TimerPhase.Running, "Rest")
        waitForIdle()
        state = RestTimerVisual(0, 120, TimerPhase.Done, "Rest")
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTitleTotalAndControls() {
        for ((phase, toggle) in listOf(TimerPhase.Running to "Pausar", TimerPhase.Paused to "Retomar")) {
            runComposeUiTest {
                var toggled = 0
                var restarted = 0
                var skipped = 0
                withTheme {
                    ProvideAppLocale("pt-BR") {
                        Expanded(
                            RestTimerVisual(84, 120, phase, "Descanso"),
                            title = "Descanso · Supino reto · série 4 de 5",
                            onPauseResume = { toggled++ }, onRestart = { restarted++ }, onSkip = { skipped++ },
                        )
                    }
                }
                waitForIdle()
                onNodeWithText("Descanso · Supino reto · série 4 de 5", useUnmergedTree = true).assertExists()
                onNodeWithText("de 2:00", useUnmergedTree = true).assertExists()
                onNodeWithText(toggle, useUnmergedTree = true).assertExists()
                onNodeWithTag(TimerPauseResumeTag).performClick()
                onNodeWithTag(TimerRestartTag).performClick()
                onNodeWithTag(TimerSkipTag).performClick()
                onNodeWithText("Recomeçar", useUnmergedTree = true).assertExists()
                onNodeWithText("Pular descanso", useUnmergedTree = true).assertExists()
                waitForIdle()
                assertEquals(listOf(1, 1, 1), listOf(toggled, restarted, skipped), "$phase")
            }
        }
        runComposeUiTest {
            // No total: the "of" line is not drawn.
            withTheme { ProvideAppLocale("en") { Expanded(visual, totalText = null) } }
            waitForIdle()
            onNodeWithTag(TimerTotalTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun closeIsLabelled() {
        for ((locale, label) in listOf("en" to "Close timer", "pt-BR" to "Fechar timer")) {
            runComposeUiTest {
                var closed = 0
                withTheme { ProvideAppLocale(locale) { Expanded(visual, onClose = { closed++ }) } }
                waitForIdle()
                onNodeWithTag(TimerCloseTag).assertContentDescriptionEquals(label).performClick()
                waitForIdle()
                assertEquals(1, closed)
                val close = onNodeWithTag(TimerCloseTag).fetchSemanticsNode().touchBoundsInRoot
                val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                assertTrue(close.bottom <= ring.top, "$locale: close sits at the top, above the ring")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun controlsSitInTheBottomHalf() {
        for (next in listOf(null, "Set 5 · 85 × 6")) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { Expanded(visual, upNext = next) } }
                waitForIdle()
                val root = onRoot().fetchSemanticsNode().boundsInRoot
                val expanded = onNodeWithTag(TimerExpandedTag).fetchSemanticsNode().boundsInRoot
                assertEquals(root.height, expanded.height, 1f, "the expanded timer fills the screen")
                val half = expanded.top + expanded.height / 2f
                for (tag in listOf(TimerMinusTag, TimerPlusTag)) {
                    val top = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
                    assertTrue(top >= half, "upNext=$next: $tag top $top above the half $half")
                }
                val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                assertTrue(ring.bottom <= half + 1f, "upNext=$next: the ring stays in the top half")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusIsDisabledWhenItCannotAdd() = runComposeUiTest {
        var plus = 0
        withTheme { Expanded(RestTimerVisual(295, 300, TimerPhase.Running, "Rest"), onPlus = { plus++ }, canAdd = false) }
        waitForIdle()
        onNodeWithTag(TimerPlusTag).assertIsNotEnabled().performClick()
        onNodeWithTag(TimerMinusTag).assertIsEnabled()
        waitForIdle()
        assertEquals(0, plus)
    }

    /**
     * The board "Descanso e recorde": inside the ring, only the phase ("Rest", "Paused", "Rest
     * complete"); the exercise is in the title. A screen's dock label ("Rest · Barbell bench
     * press") drawn there ran over the ring's stroke. Every corner of the status, of the time (or,
     * done, of the check) and of the total stays within the ring's clear circle, in both languages,
     * at 1.0, 1.3 and 2.0 and at the iOS sizes of 2x (linear scaling, CI run 40937989893: "Paused"
     * ran out of it), on 320 and 360 dp.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ringShowsOnlyThePhaseInsideItsClearCircle() {
        val long = "Rest · Barbell bench press with a long name"
        for ((language, words) in listOf(
            "en" to listOf("Rest", "Paused", "Rest complete"),
            "pt-BR" to listOf("Descanso", "Pausado", "Descanso concluído"),
        )) {
            for ((phase, word) in listOf(TimerPhase.Running, TimerPhase.Paused, TimerPhase.Done).zip(words)) {
                for ((scale, density) in listOf(1f to null, 1.3f to null, 2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
                    for (width in listOf(320.dp, 360.dp)) {
                        runComposeUiTest {
                            withTheme(fontScale = scale, density = density) {
                                ProvideAppLocale(language) {
                                    Box(Modifier.width(width)) { Expanded(RestTimerVisual(if (phase == TimerPhase.Done) 0 else 84, 120, phase, long)) }
                                }
                            }
                            waitForIdle()
                            val case = "$language $phase $scale $width"
                            val inRing = hasAnyAncestor(hasTestTag(TimerRingTag))
                            onNode(hasText(long, substring = true) and inRing, useUnmergedTree = true).assertDoesNotExist()
                            val status = onNode(hasText(word) and inRing, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                            val middle = onNodeWithTag(if (phase == TimerPhase.Done) TimerDoneCheckTag else TimerTimeTag, useUnmergedTree = true)
                                .fetchSemanticsNode().boundsInRoot
                            val total = onNodeWithTag(TimerTotalTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                            val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                            // In the content's density (1 for the iOS-sized case), not the device's.
                            val px = density ?: this.density.density
                            val sizes = MoosclesSizes()
                            val clear = ring.height / 2f - (sizes.tickLength + 4.dp + sizes.expandedRingStroke).value * px
                            val center = ring.center
                            for ((name, box) in listOf("status" to status, "middle" to middle, "total" to total)) {
                                for ((x, y) in listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom)) {
                                    val distance = kotlin.math.hypot(x - center.x, y - center.y)
                                    assertTrue(distance <= clear + 1f, "$case: $name corner ($x, $y) of $box outside the clear circle $clear")
                                }
                            }
                            if (phase != TimerPhase.Done) {
                                val results = mutableListOf<TextLayoutResult>()
                                val time = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
                                val action = time.config[SemanticsActions.GetTextLayoutResult].action
                                assertTrue(action != null && action(results), "$case: no text layout result")
                                assertShownWhole(results.first(), case)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Spec "Expandido": done, the full ring shows the check, inside the ring even with iOS-sized 2x text. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doneShowsTheCheckInsideTheRing() {
        for ((phase, check) in listOf(TimerPhase.Running to false, TimerPhase.Paused to false, TimerPhase.Done to true)) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { Expanded(RestTimerVisual(if (check) 0 else 84, 120, phase, "Rest")) } }
                waitForIdle()
                val node = onNodeWithTag(TimerDoneCheckTag, useUnmergedTree = true)
                if (check) node.assertExists() else node.assertDoesNotExist()
            }
        }
        for ((scale, density) in listOf(1f to null, 2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                withTheme(fontScale = scale, density = density) {
                    ProvideAppLocale("pt-BR") { Box(Modifier.width(360.dp)) { Expanded(RestTimerVisual(0, 120, TimerPhase.Done, "Rest")) } }
                }
                waitForIdle()
                val ring = onNodeWithTag(TimerRingTag).fetchSemanticsNode().boundsInRoot
                val status = onNode(hasText("Descanso concluído") and hasAnyAncestor(hasTestTag(TimerRingTag)), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val check = onNodeWithTag(TimerDoneCheckTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val total = onNodeWithTag(TimerTotalTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                // The check stands for the time, and the ring's middle (status to total) stays within its clear circle.
                onNodeWithTag(TimerTimeTag, useUnmergedTree = true).assertDoesNotExist()
                // In the content's density (1 for the iOS-sized case), not the device's.
                val px = density ?: this.density.density
                val sizes = MoosclesSizes()
                val clear = ring.height / 2f - (sizes.tickLength + 4.dp + sizes.expandedRingStroke).value * px
                val center = ring.center
                assertTrue(status.bottom <= check.top + 0.5f && check.bottom <= total.top + 0.5f, "scale $scale: order $status, $check, $total")
                assertTrue(center.y - status.top <= clear, "scale $scale: status $status above the clear circle of $ring")
                assertTrue(total.bottom - center.y <= clear, "scale $scale: total $total below the clear circle of $ring")
            }
        }
    }
}
