package com.mooscles.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Atomic timer transitions; the persisted deadline, rather than a UI countdown, is authoritative. */
class RestTimerPersistence(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val tokenKey = longPreferencesKey("timer_token")
    private val nameKey = stringPreferencesKey("timer_name")
    private val durationKey = longPreferencesKey("timer_duration")
    private val deadlineKey = longPreferencesKey("timer_deadline")
    private val pausedKey = longPreferencesKey("timer_paused")
    private val finishedKey = booleanPreferencesKey("timer_finished")

    private fun decode(p: Preferences) = RestTimerState(
        p[tokenKey] ?: 0, p[nameKey] ?: "Rest timer", p[durationKey] ?: 90_000,
        p[deadlineKey] ?: 0, p[pausedKey] ?: 0, p[finishedKey] ?: false
    )

    val states = store.data.map(::decode).distinctUntilChanged()
    suspend fun snapshot(): RestTimerState = states.first()

    private suspend fun update(transform: (RestTimerState) -> RestTimerState): RestTimerState {
        var updated = RestTimerState()
        store.edit { preferences ->
            updated = transform(decode(preferences))
            preferences[tokenKey] = updated.token
            preferences[nameKey] = updated.exerciseName
            preferences[durationKey] = updated.durationMillis
            preferences[deadlineKey] = updated.deadline
            preferences[pausedKey] = updated.pausedMillis
            preferences[finishedKey] = updated.finished
        }
        return updated
    }

    /** Completing another set replaces the timer and invalidates every previous completion token. */
    suspend fun start(name: String, seconds: Int): RestTimerState {
        require(seconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        return update { previous ->
            val currentTime = now()
            RestTimerState(
                token = maxOf(currentTime, previous.token + 1),
                exerciseName = name,
                durationMillis = seconds * 1000L,
                deadline = currentTime + seconds * 1000L
            )
        }
    }

    suspend fun pause() = update { it.pause(now()) }
    suspend fun resume() = update { it.resume(now()) }
    suspend fun reset() = update {
        it.copy(token = maxOf(now(), it.token + 1), deadline = 0,
            pausedMillis = it.durationMillis, finished = false)
    }
    suspend fun skip() = update {
        it.copy(token = maxOf(now(), it.token + 1), deadline = 0,
            pausedMillis = 0, finished = false)
    }
    suspend fun adjust(seconds: Int) = update { it.adjust(seconds * 1000L, now()) }

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
