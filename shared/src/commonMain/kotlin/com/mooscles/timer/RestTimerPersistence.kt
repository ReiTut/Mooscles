package com.mooscles.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mooscles.domain.RestDuration
import com.mooscles.domain.currentTimeMillis
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Atomic timer transitions; the persisted deadline, rather than a UI countdown, is authoritative. */
class RestTimerPersistence(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = ::currentTimeMillis
) {
    private val tokenKey = longPreferencesKey("timer_token")
    private val nameKey = stringPreferencesKey("timer_name")
    private val durationKey = longPreferencesKey("timer_duration")
    private val intervalKey = longPreferencesKey("timer_interval_duration")
    private val deadlineKey = longPreferencesKey("timer_deadline")
    private val pausedKey = longPreferencesKey("timer_paused")
    private val finishedKey = booleanPreferencesKey("timer_finished")
    private val workoutKey = stringPreferencesKey("timer_workout")
    private val exerciseKey = stringPreferencesKey("timer_workout_exercise")

    private fun decode(p: Preferences): RestTimerState {
        val duration = p[durationKey] ?: RestDuration.DEFAULT_SECONDS * 1000L
        return RestTimerState(
            p[tokenKey] ?: 0, p[nameKey] ?: "Rest timer", duration,
            p[deadlineKey] ?: 0, p[pausedKey] ?: 0, p[finishedKey] ?: false, p[workoutKey],
            // Previous versions persisted only the reset duration. bounded() also handles
            // an older countdown that was extended beyond that duration.
            p[intervalKey] ?: duration.coerceIn(0, RestTimerState.MAX_MILLIS),
            // Older name-only timers stay unassigned; never guess between repeated exercises.
            workoutExerciseId = p[exerciseKey]
        )
    }

    val states = store.data.map { preferences ->
        val saved = decode(preferences)
        if (saved.bounded(now()) != saved) update { it } else saved
    }.distinctUntilChanged()
    suspend fun snapshot(): RestTimerState = states.first()

    private suspend fun update(transform: (RestTimerState) -> RestTimerState): RestTimerState {
        var updated = RestTimerState()
        store.edit { preferences ->
            updated = transform(decode(preferences).bounded(now())).bounded(now())
            preferences[tokenKey] = updated.token
            preferences[nameKey] = updated.exerciseName
            preferences[durationKey] = updated.durationMillis
            preferences[intervalKey] = updated.intervalMillis
            preferences[deadlineKey] = updated.deadline
            preferences[pausedKey] = updated.pausedMillis
            preferences[finishedKey] = updated.finished
            if (updated.workoutId == null) preferences.remove(workoutKey)
            else preferences[workoutKey] = updated.workoutId!!
            if (updated.workoutExerciseId == null) preferences.remove(exerciseKey)
            else preferences[exerciseKey] = updated.workoutExerciseId!!
        }
        return updated
    }

    /** Completing another set replaces the timer and invalidates every previous completion token. */
    suspend fun start(name: String, seconds: Int, workoutExerciseId: String? = null): RestTimerState {
        require(seconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        return update { previous ->
            val currentTime = now()
            RestTimerState(
                token = maxOf(currentTime, previous.token + 1),
                exerciseName = name,
                durationMillis = seconds * 1000L,
                deadline = currentTime + seconds * 1000L,
                workoutId = previous.workoutId,
                workoutExerciseId = workoutExerciseId
            )
        }
    }

    suspend fun pause() = update { it.pause(now()) }
    suspend fun resume() = update { it.resume(now()) }
    suspend fun reset() = update {
        it.copy(token = maxOf(now(), it.token + 1), deadline = 0,
            pausedMillis = it.durationMillis, finished = false, intervalMillis = it.durationMillis)
    }
    suspend fun skip() = update {
        it.copy(token = maxOf(now(), it.token + 1), deadline = 0,
            pausedMillis = 0, finished = false)
    }
    suspend fun adjust(seconds: Int) = update { it.adjust(seconds * 1000L, now()) }

    /** Clear only the active countdown; routine/exercise rest prescriptions live elsewhere. */
    suspend fun clear() = update { cleared(it) }

    /** Bind legacy timers to the actual resumable session and reject timers from any other one. */
    suspend fun reconcileSession(workoutId: String?): RestTimerState {
        fun reconcile(saved: RestTimerState): RestTimerState = when {
            workoutId == null -> if (saved == RestTimerState(token = saved.token)) saved else cleared(saved)
            saved.workoutId == null -> saved.copy(workoutId = workoutId)
            saved.workoutId == workoutId -> saved
            else -> cleared(saved).copy(workoutId = workoutId)
        }
        val saved = snapshot()
        return if (reconcile(saved) == saved) saved else update(::reconcile)
    }

    private fun cleared(previous: RestTimerState) = RestTimerState(token = maxOf(now(), previous.token + 1))

    /** A receiver and the foreground ticker may race; only one can claim the durable completion. */
    suspend fun claimCompletion(token: Long): RestTimerState? {
        var claimed: RestTimerState? = null
        update { state ->
            if (state.token == token && state.running && state.remaining(now()) == 0L) {
                claimed = state
                state.copy(deadline = 0, pausedMillis = 0, finished = true)
            } else state
        }
        return claimed
    }
}
