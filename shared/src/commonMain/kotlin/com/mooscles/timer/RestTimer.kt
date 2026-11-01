package com.mooscles.timer

import com.mooscles.ui.TimerUi
import kotlinx.coroutines.flow.StateFlow

/** Why the rest timer reported [RestTimer.error]. */
enum class RestTimerError { STORAGE_UNAVAILABLE }

/** The rest timer as the view model drives it. Android implements it with alarms and notifications. */
interface RestTimer {
    val ui: StateFlow<TimerUi>
    val error: StateFlow<String?>
    /** The reason for [error]; set before [error], so a reported error always has its code. */
    val errorCode: StateFlow<RestTimerError?>
    /** Clears [error] and [errorCode]. */
    fun clearError()
    suspend fun start(name: String, seconds: Int, workoutExerciseId: String? = null)
    /** Clears the timer and reports storage failures through [error] instead of throwing. */
    suspend fun clear()
    /** Like [clear], but propagates storage failures, so a session end never reports success after a failed clear. */
    suspend fun clearForSessionEnd()
    suspend fun pause()
    suspend fun resume()
    suspend fun reset()
    suspend fun skip()
    suspend fun adjust(seconds: Int)
}
