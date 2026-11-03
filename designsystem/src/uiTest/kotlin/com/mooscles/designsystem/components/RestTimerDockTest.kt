package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RestTimerDockTest {
    private fun visual(seconds: Int, phase: TimerPhase = TimerPhase.Running, total: Int = 120) =
        RestTimerVisual(seconds, total, phase, "Rest")

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.announcement(): String =
        onNodeWithTag(TimerAnnounceTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun timeIsTabularAndNeverEllipsized() = runComposeUiTest {
        withTheme(fontScale = 2f) {
            Box(Modifier.width(320.dp)) { RestTimerDock(visual(600), {}, {}, {}) }
        }
        waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result")
        val layout = results.first()
        assertEquals("tnum", layout.layoutInput.style.fontFeatureSettings)
        assertShownWhole(layout)
    }

    /**
     * The dock is one traversal group, like the compact timer and the workout bar, so a screen
     * reader reads its body and its buttons together, whichever row the buttons are on.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dockIsATraversalGroup() {
        for (scale in listOf(1f, 2f)) {
            runComposeUiTest {
                withTheme(fontScale = scale) { Box(Modifier.width(320.dp)) { RestTimerDock(visual(84), {}, {}, {}, onOpen = {}) } }
                waitForIdle()
                val dock = onNodeWithTag(TimerDockTag, useUnmergedTree = true).fetchSemanticsNode()
                assertEquals(true, dock.config.getOrNull(SemanticsProperties.IsTraversalGroup), "at $scale the dock is not a traversal group")
                for (tag in listOf(TimerOpenTag, TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
                    var node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().parent
                    while (node != null && node.id != dock.id) {
                        assertNotEquals(true, node.config.getOrNull(SemanticsProperties.IsTraversalGroup), "at $scale $tag is in a group of its own")
                        node = node.parent
                    }
                    assertEquals(dock.id, node?.id, "at $scale $tag is not in the dock")
                }
            }
        }
    }

    /** Between announcements the node holds no text, so a screen reader has no stop on it to read a stale time. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun announcerIsNotAStopBetweenAnnouncements() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { RestTimerDock(visual(84), {}, {}, {}, onOpen = {}) } }
        mainClock.advanceTimeBy(MoosclesTiming().timerAnnounceHold + 100)
        waitForIdle()
        val config = onNodeWithTag(TimerAnnounceTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(null, config.getOrNull(SemanticsProperties.Text))
        assertEquals(null, config.getOrNull(SemanticsProperties.Focused))
        assertEquals(null, config.getOrNull(SemanticsActions.OnClick))
        // The visible dock still names itself and its state.
        onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals("Open rest timer, 1 minute and 24 seconds remaining")
    }

    /** The text is there while it is announced, then cleared after the hold; a later change announces again. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun announcerClearsAfterAnnouncing() = runComposeUiTest {
        mainClock.autoAdvance = false
        var state by mutableStateOf(visual(84))
        withTheme { ProvideAppLocale("en") { RestTimerDock(state, {}, {}, {}) } }
        mainClock.advanceTimeByFrame()
        assertEquals("Rest, 1 minute and 24 seconds remaining", announcement())
        mainClock.advanceTimeBy(MoosclesTiming().timerAnnounceHold - 100)
        assertEquals("Rest, 1 minute and 24 seconds remaining", announcement())
        mainClock.advanceTimeBy(200)
        assertEquals(null, onNodeWithTag(TimerAnnounceTag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        state = visual(97)
        mainClock.advanceTimeByFrame()
        assertEquals("Rest, 1 minute and 37 seconds remaining", announcement())
        mainClock.advanceTimeBy(MoosclesTiming().timerAnnounceHold + 100)
        assertEquals(null, onNodeWithTag(TimerAnnounceTag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun announcementChangesOnlyOnStartAdjustAndFinish() = runComposeUiTest {
        mainClock.autoAdvance = false
        var state by mutableStateOf(visual(84))
        withTheme { ProvideAppLocale("en") { RestTimerDock(state, {}, {}, {}) } }
        mainClock.advanceTimeByFrame()
        val start = announcement()
        assertEquals("Rest, 1 minute and 24 seconds remaining", start)
        state = visual(83)
        mainClock.advanceTimeByFrame()
        assertEquals(start, announcement())
        state = visual(82)
        mainClock.advanceTimeByFrame()
        assertEquals(start, announcement())
        state = visual(97)
        mainClock.advanceTimeByFrame()
        val adjusted = announcement()
        assertNotEquals(start, adjusted)
        state = visual(0, TimerPhase.Done)
        mainClock.advanceTimeByFrame()
        assertNotEquals(adjusted, announcement())
        assertEquals("Rest complete", announcement())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun finishFiresRestFinishedOnce() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(visual(1))
        withTheme(haptics = haptics) { RestTimerDock(state, {}, {}, {}) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events)
        state = visual(0, TimerPhase.Done)
        waitForIdle()
        state = visual(0, TimerPhase.Done, total = 121)
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }

    /** Switching between the dock and the expanded timer during the done hold does not buzz again. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun switchingViewsWhileDoneDoesNotRepeatRestFinished() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var state by mutableStateOf(visual(1))
        var expanded by mutableStateOf(false)
        withTheme(haptics = haptics) {
            if (expanded) RestTimerExpanded(state, null, {}, {}, {}, title = "Rest", totalText = null, onPauseResume = {}, onRestart = {}, onClose = {}) else RestTimerDock(state, {}, {}, {})
        }
        waitForIdle()
        state = visual(0, TimerPhase.Done)
        waitForIdle()
        expanded = true
        waitForIdle()
        expanded = false
        waitForIdle()
        assertEquals(listOf(HapticEvent.RestFinished), haptics.events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusAndMinusTick() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var minus = 0
        var plus = 0
        withTheme(haptics = haptics) { RestTimerDock(visual(84), { minus++ }, { plus++ }, {}) }
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
    fun actionsMeetTheWorkoutTarget() = runComposeUiTest {
        withTheme { RestTimerDock(visual(84), {}, {}, {}) }
        waitForIdle()
        val target = with(density) { MoosclesTargets().workout.toPx() }
        for (tag in listOf(TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.height >= target - 0.5f && touch.width >= target - 0.5f, "$tag ${touch.width} x ${touch.height} < $target")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsStackAtOneAndAHalf() {
        runComposeUiTest {
            withTheme(fontScale = 1f) { Box(Modifier.width(360.dp)) { RestTimerDock(visual(84), {}, {}, {}) } }
            waitForIdle()
            val minus = onNodeWithTag(TimerMinusTag).fetchSemanticsNode().boundsInRoot
            val plus = onNodeWithTag(TimerPlusTag).fetchSemanticsNode().boundsInRoot
            assertEquals(minus.top, plus.top, "side by side at 1.0")
        }
        runComposeUiTest {
            withTheme(fontScale = 1.5f) { Box(Modifier.width(360.dp)) { RestTimerDock(visual(84), {}, {}, {}) } }
            waitForIdle()
            val minus = onNodeWithTag(TimerMinusTag).fetchSemanticsNode().boundsInRoot
            val plus = onNodeWithTag(TimerPlusTag).fetchSemanticsNode().boundsInRoot
            val skip = onNodeWithTag(TimerSkipTag).fetchSemanticsNode().boundsInRoot
            assertEquals(minus.left, plus.left, "same column")
            assertTrue(plus.top >= minus.bottom - 0.5f, "plus under minus")
            assertTrue(skip.top >= plus.bottom - 0.5f, "skip under plus")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun timeNeverWrapsOrClipsAtAnyScaleOrWidth() {
        for (width in listOf(360, 320)) {
            for (scale in listOf(1f, 1.3f, 1.4f, 1.5f)) {
                runComposeUiTest {
                    withTheme(fontScale = scale) { Box(Modifier.width(width.dp)) { RestTimerDock(visual(600), {}, {}, {}) } }
                    waitForIdle()
                    val results = mutableListOf<TextLayoutResult>()
                    val node = onNodeWithTag(TimerTimeTag, useUnmergedTree = true).fetchSemanticsNode()
                    val action = node.config[SemanticsActions.GetTextLayoutResult].action
                    assertTrue(action != null && action(results), "no text layout result")
                    val where = "width $width scale $scale"
                    assertShownWhole(results.first(), where)
                    val dock = onNodeWithTag(TimerDockTag).fetchSemanticsNode().boundsInRoot
                    val time = node.boundsInRoot
                    assertTrue(time.left >= dock.left && time.right <= dock.right, "$where: time outside the dock")
                    // The buttons never leave the dock either.
                    for (tag in listOf(TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
                        val button = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                        assertTrue(button.right <= dock.right + 1f, "$where: $tag outside the dock")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun visibleLabelsAreInTheAccessibleNames() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { RestTimerDock(visual(84), {}, {}, {}) } }
        waitForIdle()
        onNodeWithTag(TimerMinusTag).assertContentDescriptionEquals("Minus 15 seconds")
        onNodeWithTag(TimerPlusTag).assertContentDescriptionEquals("Plus 15 seconds")
        onNodeWithText("\u221215 s", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pausedShowsThePauseIconAndRunningDoesNot() {
        for ((phase, paused) in listOf(TimerPhase.Paused to true, TimerPhase.Running to false, TimerPhase.Done to false)) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { RestTimerDock(visual(60, phase), {}, {}, {}) } }
                waitForIdle()
                val icon = onNodeWithTag(TimerPausedIconTag, useUnmergedTree = true)
                if (paused) icon.assertExists() else icon.assertDoesNotExist()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bodyTapOpensWithTheSpokenLabel() {
        runComposeUiTest {
            var opened = 0
            withTheme { ProvideAppLocale("pt-BR") { RestTimerDock(visual(84), {}, {}, {}, onOpen = { opened++ }) } }
            waitForIdle()
            onNodeWithTag(TimerOpenTag)
                .assertHasClickAction()
                .assertContentDescriptionEquals("Abrir timer de descanso, tempo restante: 1 minuto e 24 segundos")
            onNodeWithTag(TimerOpenTag).performClick()
            waitForIdle()
            assertEquals(1, opened)
        }
        runComposeUiTest {
            withTheme { ProvideAppLocale("en") { RestTimerDock(visual(84), {}, {}, {}, onOpen = {}) } }
            waitForIdle()
            onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals("Open rest timer, 1 minute and 24 seconds remaining")
        }
        runComposeUiTest {
            // Without onOpen the body is not a button.
            withTheme { RestTimerDock(visual(84), {}, {}, {}) }
            waitForIdle()
            val node = onNodeWithTag(TimerOpenTag).fetchSemanticsNode()
            assertTrue(SemanticsActions.OnClick !in node.config, "no click without onOpen")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plusIsDisabledWhenItCannotAdd() = runComposeUiTest {
        var plus = 0
        var minus = 0
        var canAdd by mutableStateOf(false)
        withTheme { RestTimerDock(visual(295), { minus++ }, { plus++ }, {}, canAdd = canAdd) }
        waitForIdle()
        onNodeWithTag(TimerPlusTag).assertIsNotEnabled()
        onNodeWithTag(TimerMinusTag).assertIsEnabled()
        onNodeWithTag(TimerPlusTag).performClick()
        onNodeWithTag(TimerMinusTag).performClick()
        waitForIdle()
        assertEquals(0, plus)
        assertEquals(1, minus)
        canAdd = true
        waitForIdle()
        onNodeWithTag(TimerPlusTag).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(1, plus)
    }

    /** Task 13 review M1: paused or done, the open button says so instead of only "… remaining". */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun openLabelReadsPausedAndDone() {
        val cases = listOf(
            visual(84) to ("Abrir timer de descanso, tempo restante: 1 minuto e 24 segundos" to null),
            visual(84, TimerPhase.Paused) to ("Abrir timer de descanso, tempo restante: 1 minuto e 24 segundos" to "Pausado"),
            visual(0, TimerPhase.Done) to ("Abrir timer de descanso" to "Descanso concluído"),
        )
        for ((shown, expected) in cases) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("pt-BR") { RestTimerDock(shown, {}, {}, {}, onOpen = {}) } }
                waitForIdle()
                val body = onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals(expected.first)
                val state = body.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
                assertEquals(expected.second, state, "${shown.phase}")
            }
        }
    }

    /** Task 13 review M2: the body that opens the timer answers a press, like the other controls. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bodyPressScales() = runComposeUiTest {
        withTheme { Box(Modifier.width(412.dp)) { RestTimerDock(visual(84), {}, {}, {}, onOpen = {}) } }
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
