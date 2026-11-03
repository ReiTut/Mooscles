package com.mooscles.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test fun progressUsesActualIntervalAndSubsecondElapsedTime() {
        val timer = RestTimerState(deadline = 190_000, durationMillis = 90_000)
        assertEquals(1f, timer.progress(100_000), 0f)
        assertEquals(0.75f, timer.progress(122_500), 0.00001f)
        assertEquals(0f, timer.progress(190_000), 0f)
        assertEquals(0f, timer.progress(999_000), 0f)
    }

    @Test fun pausedProgressFreezesAndResumesFromTheSameFraction() {
        val paused = RestTimerState(deadline = 190_000, durationMillis = 90_000).pause(122_500)
        assertEquals(0.75f, paused.progress(122_500), 0.00001f)
        assertEquals(paused.progress(122_500), paused.progress(999_000), 0f)
        val resumed = paused.resume(999_000)
        assertEquals(paused.progress(999_000), resumed.progress(999_000), 0f)
        assertEquals(0.5f, resumed.progress(1_021_500), 0.00001f)
    }

    @Test fun adjustmentsPreserveElapsedTimeAndKeepResetDurationSeparate() {
        val original = RestTimerState(deadline = 220_000, durationMillis = 120_000)
        val extended = original.adjust(15_000, 160_000)
        assertEquals(75_000L, extended.remaining(160_000))
        assertEquals(135_000L, extended.intervalMillis)
        assertEquals(120_000L, extended.durationMillis)
        assertEquals(75f / 135f, extended.progress(160_000), 0.00001f)
        val shortened = extended.adjust(-30_000, 160_000)
        assertEquals(45_000L, shortened.remaining(160_000))
        assertEquals(105_000L, shortened.intervalMillis)
        assertEquals(45f / 105f, shortened.progress(160_000), 0.00001f)
    }

    @Test fun extendingPastOriginalDurationDoesNotLeaveProgressClippedFull() {
        val timer = RestTimerState(deadline = 160_000, durationMillis = 60_000)
        val extended = timer.adjust(120_000, 130_000)
        assertEquals(150_000L, extended.remaining(130_000))
        assertEquals(180_000L, extended.intervalMillis)
        assertEquals(150f / 180f, extended.progress(130_000), 0.00001f)
        assertTrue(extended.progress(130_000) < 1f)
        assertTrue(extended.progress(145_000) < extended.progress(130_000))
    }

    @Test fun cappedAndNegativeAdjustmentsUseOnlyAcceptedSeconds() {
        val timer = RestTimerState(deadline = 400_000, durationMillis = 300_000)
        val capped = timer.adjust(30_000, 110_000)
        assertEquals(300_000L, capped.remaining(110_000))
        assertEquals(310_000L, capped.intervalMillis)
        assertEquals(capped, capped.adjust(Long.MAX_VALUE, 110_000))
        val ended = capped.adjust(Long.MIN_VALUE, 110_000)
        assertEquals(10_000L, ended.intervalMillis) // The already elapsed ten seconds remain.
        assertEquals(0f, ended.progress(110_000), 0f)
    }

    @Test fun invalidOrZeroBasisNeverProducesOutOfRangeOrNonFiniteProgress() {
        for (timer in listOf(
            RestTimerState(),
            RestTimerState(durationMillis = 0),
            RestTimerState(durationMillis = 0, pausedMillis = 15_000, intervalMillis = 0),
            RestTimerState(pausedMillis = 15_000, intervalMillis = -10),
            RestTimerState(pausedMillis = 15_000, intervalMillis = Long.MAX_VALUE),
            RestTimerState(pausedMillis = 15_000, finished = true),
        )) {
            val progress = timer.progress(100_000)
            assertTrue(progress.isFinite())
            assertTrue(progress in 0f..1f)
        }
        val overflow = RestTimerState(pausedMillis = 1, intervalMillis = Long.MAX_VALUE).adjust(15_000, 0)
        assertEquals(Long.MAX_VALUE, overflow.intervalMillis)
        assertTrue(overflow.progress(0).isFinite())
    }
}
