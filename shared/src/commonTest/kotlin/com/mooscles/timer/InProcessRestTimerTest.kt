package com.mooscles.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InProcessRestTimerTest {
    private val epoch = 1_000_000L

    private class Rig(val store: InMemoryPreferencesStore, val timer: InProcessRestTimer,
                      val persistence: RestTimerPersistence, var workout: String?)

    private fun TestScope.rig(workout: String? = "w1"): Rig {
        val store = InMemoryPreferencesStore()
        val clock = { epoch + testScheduler.currentTime }
        val persistence = RestTimerPersistence(store, clock)
        lateinit var rig: Rig
        val timer = InProcessRestTimer(persistence, backgroundScope, { rig.workout }, clock)
        rig = Rig(store, timer, persistence, workout)
        return rig
    }

    private suspend fun TestScope.settle(millis: Long = 0) { advanceTimeBy(millis); runCurrent() }

    @Test fun startCountsDownFromTheDuration() = runTest {
        val r = rig()
        r.timer.start("Bench", 120); settle()
        assertEquals(120, r.timer.ui.value.remainingSeconds)
        settle(30_000)
        assertEquals(90, r.timer.ui.value.remainingSeconds)
    }

    @Test fun pauseFreezesAndResumeContinues() = runTest {
        val r = rig()
        r.timer.start("Bench", 120); settle(30_000)
        r.timer.pause(); settle()
        assertTrue(r.timer.ui.value.paused)
        settle(20_000)
        assertEquals(90, r.timer.ui.value.remainingSeconds)
        r.timer.resume(); settle(10_000)
        assertTrue(r.timer.ui.value.running)
        assertEquals(80, r.timer.ui.value.remainingSeconds)
    }

    @Test fun plusFifteenIsCappedAtFiveMinutes() = runTest {
        val r = rig()
        r.timer.start("Bench", 300); settle(10_000)
        r.timer.adjust(15); settle()
        assertEquals(300, r.timer.ui.value.remainingSeconds)
    }

    @Test fun minusFifteenNeverGoesBelowZero() = runTest {
        val r = rig()
        r.timer.start("Bench", 10); settle()
        r.timer.adjust(-15); settle()
        assertEquals(0, r.timer.ui.value.remainingSeconds)
    }

    @Test fun skipEndsTheIntervalWithoutCompletion() = runTest {
        val r = rig()
        r.timer.start("Bench", 60); settle(5_000)
        r.timer.skip(); settle(70_000)
        val ui = r.timer.ui.value
        assertFalse(ui.running); assertFalse(ui.finished); assertEquals(0, ui.remainingSeconds)
    }

    @Test fun resetRestartsTheInterval() = runTest {
        val r = rig()
        r.timer.start("Bench", 60); settle(20_000)
        r.timer.reset(); settle()
        val ui = r.timer.ui.value
        assertFalse(ui.running); assertEquals(60, ui.remainingSeconds)
    }

    @Test fun reachingZeroCompletesOnce() = runTest {
        val r = rig()
        r.timer.start("Bench", 2); settle(2_500)
        assertTrue(r.timer.ui.value.finished)
        val claimed = r.persistence.snapshot()
        assertTrue(claimed.finished)
        // The token was already claimed, so a second claim finds nothing.
        assertNull(r.persistence.claimCompletion(claimed.token))
        settle(5_000)
        assertTrue(r.timer.ui.value.finished)
    }

    @Test fun failedCompletionAttemptsAreRateLimited() = runTest {
        val store = InMemoryPreferencesStore()
        val clock = { epoch + testScheduler.currentTime }
        var lookups = 0
        val timer = InProcessRestTimer(RestTimerPersistence(store, clock), backgroundScope, { lookups++; "w1" }, clock)
        timer.start("Bench", 2); settle()
        // The claim cannot be written, so the rest stays running at 0 while the ticks (every 250 ms) go on.
        store.failWrites = true
        val before = lookups
        settle(2_000)
        settle(2_500)
        assertTrue(timer.ui.value.running)
        assertEquals(0, timer.ui.value.remainingSeconds)
        val attempts = lookups - before
        assertTrue(attempts in 2..3, "completion attempts over 4.5 s of ticks at 0 (back-off 2 s): $attempts")
        assertEquals(RestTimerError.STORAGE_UNAVAILABLE, timer.errorCode.value)
        // A new rest is a new countdown: its end is claimed again.
        store.failWrites = false
        timer.start("Row", 1); settle(1_500)
        assertTrue(timer.ui.value.finished)
    }

    @Test fun aFailedCompletionIsRetriedAndFinishesWhenStorageRecovers() = runTest {
        val store = InMemoryPreferencesStore()
        val clock = { epoch + testScheduler.currentTime }
        val timer = InProcessRestTimer(RestTimerPersistence(store, clock), backgroundScope, { "w1" }, clock)
        timer.start("Bench", 2); settle()
        store.failWrites = true
        settle(2_500)
        assertTrue(timer.ui.value.running)
        assertFalse(timer.ui.value.finished)
        store.failWrites = false
        settle(2_500)
        assertTrue(timer.ui.value.finished)
    }

    @Test fun clearForSessionEndRemovesTheTimer() = runTest {
        val r = rig()
        r.timer.start("Bench", 60); settle()
        r.timer.clearForSessionEnd(); settle()
        val ui = r.timer.ui.value
        assertFalse(ui.running); assertEquals(0, ui.remainingSeconds)
    }

    @Test fun noActiveWorkoutIgnoresStart() = runTest {
        val r = rig(workout = null)
        r.timer.start("Bench", 60); settle()
        assertFalse(r.timer.ui.value.running)
        assertNull(r.timer.error.value)
    }

    @Test fun writesTheSameKeysAsTheController() = runTest {
        val r = rig()
        r.timer.start("Bench", 60, "we1"); settle()
        assertEquals(
            setOf("timer_token", "timer_name", "timer_duration", "timer_interval_duration", "timer_deadline",
                "timer_paused", "timer_finished", "timer_workout", "timer_workout_exercise"),
            r.store.snapshot.keys)
    }

    @Test fun writesTheSameValuesAsDrivingThePersistenceDirectly() = runTest {
        val a = rig()
        val reference = InMemoryPreferencesStore()
        val clock = { epoch + testScheduler.currentTime }
        val direct = RestTimerPersistence(reference, clock)
        suspend fun step(op: suspend InProcessRestTimer.() -> Unit, ref: suspend RestTimerPersistence.() -> Unit) {
            a.timer.op()
            direct.reconcileSession("w1"); direct.ref()
            assertEquals(reference.snapshot, a.store.snapshot)
            settle(7_000)
        }
        step({ start("Bench", 90, "we1") }, { start("Bench", 90, "we1") })
        step({ pause() }, { pause() })
        step({ resume() }, { resume() })
        step({ adjust(15) }, { adjust(15) })
        step({ reset() }, { reset() })
        step({ skip() }, { skip() })
        step({ clearForSessionEnd() }, { clear() })
    }

    @Test fun storageFailureSetsTheErrorCode() = runTest {
        val r = rig()
        r.store.failWrites = true
        r.timer.start("Bench", 60); settle()
        assertEquals(RestTimerError.STORAGE_UNAVAILABLE, r.timer.errorCode.value)
        assertNotNull(r.timer.error.value)
        r.timer.clearError()
        assertNull(r.timer.error.value); assertNull(r.timer.errorCode.value)
    }
}
