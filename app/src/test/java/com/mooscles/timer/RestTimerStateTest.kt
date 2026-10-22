package com.mooscles.timer

import org.junit.Assert.*
import org.junit.Test

class RestTimerStateTest {
    @Test fun missingDurationDefaultsToTwoMinutesWhileSavedDurationsRemainExplicit() {
        assertEquals(120_000L, RestTimerState().durationMillis)
        assertEquals(90_000L, RestTimerState(durationMillis = 90_000).durationMillis)
        assertEquals(135_000L, RestTimerState(durationMillis = 135_000).durationMillis)
    }

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
    @Test fun quickAdjustmentsCannotExceedFiveMinutesRunningOrPaused() {
        val running = RestTimerState(deadline = 390_000, durationMillis = 300_000)
        assertEquals(300_000L, running.adjust(15_000, 100_000).remaining(100_000))
        val paused = RestTimerState(pausedMillis = 300_000, durationMillis = 300_000)
        assertEquals(300_000L, paused.adjust(15_000, 100_000).remaining(100_000))
        assertEquals(300_000L, paused.adjust(Long.MAX_VALUE, 100_000).remaining(100_000))
    }
    @Test fun olderTimersNormalizeRemainingTimeWithoutRestartingElapsedTime() {
        val running = RestTimerState(deadline = 700_000, durationMillis = 600_000)
        assertEquals(400_000L, running.bounded(100_000).deadline)
        assertEquals(700_000L, running.bounded(500_000).deadline)
        assertEquals(300_000L, running.bounded(500_000).durationMillis)
        assertEquals(300_000L, RestTimerState(pausedMillis = 600_000).bounded(0).pausedMillis)
    }
}
