package com.mooscles.timer

import com.mooscles.updates.UpdateCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The rest notification's actions, with the rule each one has on the screen (decision 24):
 * ±15 s take the [gate] and are refused while an update installs; Skip does not take the gate
 * and then lets a deferred update go on.
 */
class RestTimerActions(
    private val gate: Mutex,
    private val updates: UpdateCoordinator,
    private val timer: RestTimer,
) {
    suspend fun minus15() = adjust(-15)
    suspend fun plus15() = adjust(15)
    suspend fun skip() {
        timer.skip()
        updates.onTrainingChanged()
    }

    private suspend fun adjust(seconds: Int) = gate.withLock {
        updates.assertTrainingMayStart()
        timer.adjust(seconds)
    }

    /**
     * Runs the notification action named [action]. The receiver has no screen to explain a
     * refusal or a failure, so nothing is shown: the timer, and its notification, stay as they were.
     * An action that cannot finish within [RECEIVER_TIMEOUT_MILLIS] (the gate held by an import or an
     * installation) is dropped the same way, inside the broadcast's `goAsync` budget (10 s).
     */
    suspend fun perform(action: String?) {
        try {
            // withLock releases the gate on the timeout's cancellation, so it can never leak.
            withTimeoutOrNull(RECEIVER_TIMEOUT_MILLIS) {
                when (action) {
                    RestNotifications.ACTION_MINUS -> minus15()
                    RestNotifications.ACTION_PLUS -> plus15()
                    RestNotifications.ACTION_SKIP -> skip()
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
        }
    }

    companion object {
        const val RECEIVER_TIMEOUT_MILLIS = 8_000L
    }
}
