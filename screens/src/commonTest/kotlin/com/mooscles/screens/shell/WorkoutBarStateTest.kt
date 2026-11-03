package com.mooscles.screens.shell

import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.ui.TimerUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutBarStateTest {
    @Test
    fun elapsedIsWholeSecondsAndNeverNegative() {
        assertEquals(0L, elapsedSeconds(startedAt = 10_000L, now = 10_999L))
        assertEquals(1L, elapsedSeconds(startedAt = 10_000L, now = 11_000L))
        assertEquals(2538L, elapsedSeconds(startedAt = 0L, now = 2_538_400L))
        // A clock behind the start (changed by hand, a restore) reads zero, not a negative time.
        assertEquals(0L, elapsedSeconds(startedAt = 10_000L, now = 4_000L))
    }

    @Test
    fun onlyARunningOrPausedRestIsLive() {
        assertEquals(TimerPhase.Running, liveRestPhase(TimerUi(remainingSeconds = 84, running = true)))
        assertEquals(TimerPhase.Paused, liveRestPhase(TimerUi(remainingSeconds = 84, paused = true)))
        // The end is shown only after a transition the bar saw (rememberBarRestPhase), never from the state alone.
        assertNull(liveRestPhase(TimerUi(remainingSeconds = 0, finished = true)))
        assertNull(liveRestPhase(TimerUi(remainingSeconds = 0, running = true, finished = true)))
        assertNull(liveRestPhase(TimerUi()))
    }
}
