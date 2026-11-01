package com.mooscles.timer

import com.mooscles.ui.TimerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/**
 * A [RestTimer] with no alarm and no notification: a coroutine ticker over the same
 * [RestTimerPersistence], so it reads and writes exactly the keys the Android controller does.
 * Used by the iOS end-to-end flow test and as the base for the iOS timer.
 */
class InProcessRestTimer(
    private val persistence: RestTimerPersistence,
    private val scope: CoroutineScope,
    private val activeWorkoutId: suspend () -> String?,
    private val now: () -> Long,
    private val tickMillis: Long = 250L,
) : RestTimer {
    private val mutex = Mutex()
    private val problem = MutableStateFlow<String?>(null)
    private val problemCode = MutableStateFlow<RestTimerError?>(null)
    override val error: StateFlow<String?> = problem.asStateFlow()
    override val errorCode: StateFlow<RestTimerError?> = problemCode.asStateFlow()
    override fun clearError() { problem.value = null; problemCode.value = null }

    private val state = persistence.states.retryWhen { failure, attempt ->
        report(failure)
        delay((attempt + 1).coerceAtMost(30) * 1000)
        true
    }.catch { failure ->
        report(failure)
        emit(RestTimerState())
    }.stateIn(scope, SharingStarted.Eagerly, RestTimerState())
    private val ticks = flow { while (true) { emit(Unit); delay(tickMillis) } }

    /**
     * The countdown whose completion was last asked for: every tick that finds it at 0 would ask
     * again, so each one is claimed once. A new rest (a new token or deadline) is a new state.
     * Read and written only by the [ui] collector, one tick at a time. A failed claim is retried by
     * the single [complete] coroutine that [ui] launched in [scope] (it loops with a back-off), so
     * there is still at most one attempt in flight and no second writer of this field.
     */
    private var completing: RestTimerState? = null

    override val ui: StateFlow<TimerUi> = combine(state, ticks) { s, _ ->
        val current = now()
        if (s.running && s.remaining(current) == 0L && s != completing) {
            completing = s
            scope.launch { complete(s.token) }
        }
        TimerUi(s.exerciseName, ceil(s.remaining(current) / 1000.0).toInt(), (s.durationMillis / 1000).toInt(),
            s.running, s.paused, s.finished, progress = s.progress(current), workoutExerciseId = s.workoutExerciseId)
    }.stateIn(scope, SharingStarted.Eagerly, TimerUi())

    private fun report(failure: Throwable) {
        if (failure is CancellationException) throw failure
        // The code goes first, so the text never appears without it.
        problemCode.value = RestTimerError.STORAGE_UNAVAILABLE
        problem.value = "Unable to access the rest timer: ${failure.message ?: "local storage is unavailable"}. Your workout data is saved separately."
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (failure: Exception) { report(failure) }
    }

    private suspend fun mutate(command: suspend RestTimerPersistence.() -> RestTimerState) = safely {
        mutex.withLock {
            val workoutId = activeWorkoutId()
            persistence.reconcileSession(workoutId)
            if (workoutId == null) return@withLock
            persistence.command()
        }
    }

    override suspend fun start(name: String, seconds: Int, workoutExerciseId: String?) {
        mutate { this.start(name, seconds, workoutExerciseId) }
    }
    override suspend fun pause() { mutate { this.pause() } }
    override suspend fun resume() { mutate { this.resume() } }
    override suspend fun reset() { mutate { this.reset() } }
    override suspend fun skip() { mutate { this.skip() } }
    override suspend fun adjust(seconds: Int) { mutate { this.adjust(seconds) } }
    override suspend fun clear() = safely { clearForSessionEnd() }
    override suspend fun clearForSessionEnd() {
        mutex.withLock { persistence.clear() }
    }

    /**
     * Claims the durable completion; a second call for the same token finds nothing to claim. A
     * failed attempt (already reported) is retried every [COMPLETION_RETRY_MILLIS] for as long as
     * that same rest is still running, so a storage hiccup cannot leave it stuck at 0. Runs in
     * [scope] and touches only the mutex, the persistence and the error flows.
     */
    private suspend fun complete(token: Long) {
        while (true) {
            var succeeded = false
            safely {
                mutex.withLock {
                    val workoutId = activeWorkoutId()
                    persistence.reconcileSession(workoutId)
                    if (workoutId != null) persistence.claimCompletion(token)
                }
                succeeded = true
            }
            if (succeeded) return
            delay(COMPLETION_RETRY_MILLIS)
            val current = state.value
            if (!current.running || current.token != token) return
        }
    }

    private companion object {
        /** Back-off between attempts to claim a rest's completion after a failed write. */
        const val COMPLETION_RETRY_MILLIS = 2_000L
    }
}
