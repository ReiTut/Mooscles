package com.mooscles.timer

import org.junit.Assert.*
import org.junit.Test

class RestTimerStateTest {
    @Test fun restoredDeadlineIncludesBackgroundTime() {
        val original = RestTimerState(deadline = 100_000, durationMillis = 90_000)
        val restored = original.copy()
        assertEquals(15_000L, restored.remaining(85_000))
        assertEquals(0L, restored.remaining(150_000))
    }
    @Test fun pauseResumeDoesNotRestartFullRest() {
        val paused = RestTimerState(deadline = 100_000).pause(70_000)
        assertEquals(30_000L, paused.remaining(999_000))
        assertEquals(1_029_000L, paused.resume(999_000).deadline)
    }
    @Test fun quickAdjustmentsNeverGoNegative() {
        val state = RestTimerState(deadline = 100_000)
        assertEquals(25_000L, state.adjust(15_000, 90_000).remaining(90_000))
        assertEquals(0L, state.adjust(-15_000, 90_000).remaining(90_000))
    }
}
