package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.INJECTS_TOUCH_CANCEL
import com.mooscles.designsystem.withTheme
import com.mooscles.designsystem.i18n.ProvideAppLocale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetRowSwipeTest {
    private class Calls {
        var completes = 0
        var removes = 0
    }

    /** A 400 dp row with the swipe; returns its left edge at rest. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.swipeRow(
        haptics: RecordingHaptics,
        calls: Calls,
        complete: Boolean = true,
        remove: Boolean = true,
        reducedMotion: Boolean = false,
    ): Float {
        withTheme(haptics = haptics, reducedMotion = reducedMotion) {
            Box(Modifier.width(400.dp).height(56.dp).setRowSwipe(complete, remove, { calls.completes++ }, { calls.removes++ }).testTag("swipe"))
        }
        waitForIdle()
        return onNodeWithTag("swipe").fetchSemanticsNode().boundsInRoot.left
    }

    /** Drags the row by [fraction] of its width (negative to the left), slowly enough to be a drag, and lets go. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.drag(fraction: Float) {
        onNodeWithTag("swipe").performTouchInput {
            swipe(start = center, end = Offset(centerX + width * fraction, centerY), durationMillis = 400)
        }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.assertBackAtRest(left: Float) {
        val now = onNodeWithTag("swipe").fetchSemanticsNode().boundsInRoot.left
        assertEquals(left, now, 0.5f, "the row did not go back to the center")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rightPastThirtyFivePercentCompletes() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(0.4f)
        assertEquals(1, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    /** The anchors change mid-settle when a completed set turns its right side off; that drag still completes once. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun completingTurnsTheRightSideOffAndCommitsOnce() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        var done by mutableStateOf(false)
        withTheme(haptics = haptics) {
            Box(
                Modifier.width(400.dp).height(56.dp)
                    .setRowSwipe(!done, true, { calls.completes++; done = true }, { calls.removes++ })
                    .testTag("swipe"),
            )
        }
        waitForIdle()
        val left = onNodeWithTag("swipe").fetchSemanticsNode().boundsInRoot.left
        drag(0.4f)
        assertEquals(1, calls.completes)
        assertEquals(0, calls.removes)
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shortOfTheThresholdSettlesBack() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(0.3f)
        drag(-0.3f)
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leftPastTheThresholdRemoves() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        drag(-0.4f)
        assertEquals(0, calls.completes)
        assertEquals(1, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun disabledDoesNothing() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls, complete = false, remove = false)
        mainClock.autoAdvance = false
        onNodeWithTag("swipe").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(width * 0.05f, 0f)) }
        }
        mainClock.advanceTimeBy(100)
        // Halfway across the row the finger is still down, and the row has not moved.
        assertBackAtRest(left)
        onNodeWithTag("swipe").performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        drag(-0.4f)
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalDragScrollsTheList() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        var list: LazyListState? = null
        withTheme(haptics = haptics) {
            val state = rememberLazyListState().also { list = it }
            LazyColumn(Modifier.height(300.dp).width(400.dp), state = state) {
                items(30) { index ->
                    Box(
                        Modifier.width(400.dp).height(56.dp)
                            .setRowSwipe(true, true, { calls.completes++ }, { calls.removes++ })
                            .testTag("row-$index"),
                    )
                }
            }
        }
        waitForIdle()
        // Up the list with a little sideways drift, as a thumb does.
        onNodeWithTag("row-2").performTouchInput {
            swipe(start = center, end = Offset(centerX + 12.dp.toPx(), centerY - 150.dp.toPx()), durationMillis = 400)
        }
        waitForIdle()
        onNodeWithTag("row-4").performTouchInput { swipeUp(startY = centerY, endY = centerY - 100.dp.toPx(), durationMillis = 150) }
        waitForIdle()
        val scrolled = list!!.firstVisibleItemIndex > 0 || list!!.firstVisibleItemScrollOffset > 0
        assertTrue(scrolled, "the list did not scroll")
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** Review I1: a side that is off neither moves nor ticks; the other side still works. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rightDisabledOnlyRemoves() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls, complete = false)
        mainClock.autoAdvance = false
        onNodeWithTag("swipe").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(width * 0.05f, 0f)) }
        }
        mainClock.advanceTimeBy(100)
        assertBackAtRest(left)
        onNodeWithTag("swipe").performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(0, calls.completes)
        assertEquals(emptyList(), haptics.events.toList(), "the disabled side ticked")
        drag(-0.4f)
        assertEquals(0, calls.completes)
        assertEquals(1, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leftDisabledOnlyCompletes() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls, remove = false)
        drag(-0.4f)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
        drag(0.4f)
        assertEquals(1, calls.completes)
        assertEquals(0, calls.removes)
        assertBackAtRest(left)
    }

    /** Review I1: a done set passes completeEnabled = false, so a right swipe can never uncheck it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun completedRowCannotBeUncheckedBySwipe() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Completed)
        withTheme(haptics = haptics) {
            ProvideAppLocale("en") {
                val done = status != SetStatus.Pending
                SetRow(
                    SetRowState(4, 5, "80 kg × 6", "80 kilograms by 6", "85", "85 kilograms", 6, SetType.Normal, status),
                    onCompleteChange = { status = if (it) SetStatus.Completed else SetStatus.Pending },
                    onEditLoad = {}, onEditReps = {}, onChangeType = {}, onRemove = {},
                    modifier = Modifier.width(400.dp)
                        // The check path toggles, so wired to a done row it would uncheck.
                        .setRowSwipe(completeEnabled = !done, removeEnabled = true, onComplete = { status = if (done) SetStatus.Pending else SetStatus.Completed }, onRemove = {})
                        .testTag("swipe"),
                )
            }
        }
        waitForIdle()
        drag(0.4f)
        assertEquals(SetStatus.Completed, status)
        onNodeWithTag(SetRowCheckTag).assertIsOn()
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** Review M1: a drag the system cancels past the threshold goes back without completing or removing. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancelledDragPastTheThresholdDoesNothing() = runComposeUiTest {
        // Only the emulator can send a cancel; on the iOS simulator this is proven nowhere yet.
        if (!INJECTS_TOUCH_CANCEL) return@runComposeUiTest
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        for (direction in listOf(1f, -1f)) {
            onNodeWithTag("swipe").performTouchInput {
                down(center)
                repeat(20) { moveBy(Offset(direction * width * 0.025f, 0f)) }
                cancel()
            }
            waitForIdle()
            assertBackAtRest(left)
        }
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        // It crossed the line twice: the ticks are honest, only the commit is withheld.
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events.toList())
    }

    /** Review M4: only the release position counts; a fast flick that stays short does nothing. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shortFastFlickDoesNothing() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        for (direction in listOf(1f, -1f)) {
            onNodeWithTag("swipe").performTouchInput {
                swipe(start = center, end = Offset(centerX + direction * width * 0.3f, centerY), durationMillis = 50)
            }
            waitForIdle()
            assertBackAtRest(left)
        }
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** Review M4: crossing the line and coming back under it before letting go ticks once and does nothing. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun crossingAndComingBackDoesNothing() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls)
        onNodeWithTag("swipe").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(width * 0.025f, 0f)) }
            repeat(12) { moveBy(Offset(-width * 0.025f, 0f)) }
            up()
        }
        waitForIdle()
        assertEquals(0, calls.completes)
        assertEquals(0, calls.removes)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        assertBackAtRest(left)
    }

    /** Review M4: with reduced motion the row is back at once, no spring, and the swipe still counts. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reducedMotionSnapsBack() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val calls = Calls()
        val left = swipeRow(haptics, calls, reducedMotion = true)
        mainClock.autoAdvance = false
        onNodeWithTag("swipe").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(width * 0.025f, 0f)) }
            up()
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertBackAtRest(left)
        assertEquals(1, calls.completes)
        mainClock.autoAdvance = true
    }
}
