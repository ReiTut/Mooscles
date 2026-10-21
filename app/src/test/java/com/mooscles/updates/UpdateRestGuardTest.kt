package com.mooscles.updates

import com.mooscles.timer.RestTimerState
import org.junit.Assert.*
import org.junit.Test

class UpdateRestGuardTest {
    @Test fun runningAndPausedRestDeferInstallation() {
        assertTrue(UpdatePolicy.restTimerBusy(RestTimerState(deadline = 120_000), 100_000))
        assertTrue(UpdatePolicy.restTimerBusy(RestTimerState(pausedMillis = 20_000), 100_000))
        assertFalse(UpdatePolicy.mayInstall(hasActiveWorkout = true, hasRestTimer = false))
        assertFalse(UpdatePolicy.mayInstall(hasActiveWorkout = false, hasRestTimer = true))
    }
    @Test fun expiredRestRestoredByABackgroundWorkerDoesNotBlockAnOtherwiseIdleApp() {
        // A background worker can start before the foreground controller claims completion.
        assertFalse(UpdatePolicy.restTimerBusy(RestTimerState(deadline = 99_000), 100_000))
        assertFalse(UpdatePolicy.restTimerBusy(RestTimerState(deadline = 100_000), 100_000))
        assertFalse(UpdatePolicy.restTimerBusy(RestTimerState(finished = true), 100_000))
        assertTrue(UpdatePolicy.mayInstall(hasActiveWorkout = false, hasRestTimer = false))
    }
}
