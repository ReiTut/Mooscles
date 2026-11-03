package com.mooscles.ui

import com.mooscles.data.DataError
import com.mooscles.data.DataRuleException
import com.mooscles.timer.RestTimerError
import com.mooscles.updates.TrainingBlockedException
import kotlinx.coroutines.CancellationException

/** Translates typed failures into [UiError]. Anything without a type is [UiError.GENERIC]. */
object UiErrors {
    fun from(failure: Throwable): UiError = when (failure) {
        is DataRuleException -> from(failure.error)
        is TrainingBlockedException -> UiError.BLOCKED_BY_UPDATE
        // A cancellation that escapes a command is a failed command, not the end of the screen.
        is CancellationException -> UiError.SAVE_FAILED
        else -> UiError.GENERIC
    }

    fun from(timer: RestTimerError): UiError = when (timer) {
        RestTimerError.STORAGE_UNAVAILABLE -> UiError.TIMER_STORAGE
    }

    private fun from(error: DataError): UiError = when (error) {
        DataError.SET_NOT_FOUND -> UiError.SET_GONE
        DataError.WORKOUT_EXERCISE_NOT_FOUND, DataError.EXERCISE_NOT_FOUND -> UiError.EXERCISE_GONE
        DataError.WORKOUT_NOT_FOUND, DataError.WORKOUT_NOT_ACTIVE -> UiError.WORKOUT_GONE
        DataError.SET_LIMIT -> UiError.SET_LIMIT
        DataError.INVALID_VALUE -> UiError.INVALID_VALUE
        DataError.NO_COMPLETED_SET -> UiError.NEEDS_COMPLETED_SET
        DataError.RESTORE_UNAVAILABLE -> UiError.SET_NOT_RESTORED
        DataError.ROUTINE_NOT_FOUND, DataError.FOLDER_NOT_FOUND -> UiError.GENERIC
    }

    /**
     * The one problem the screen shows: a failed action first, then unreadable preferences, then the timer,
     * as [UiState.error] has always chosen. Preference failures are reads, so they are local data.
     */
    internal fun current(action: UiProblem?, preferences: String?, timer: String?, timerCode: RestTimerError?): UiProblem? =
        action
            ?: preferences?.let { UiProblem(it, UiError.LOCAL_DATA_UNAVAILABLE) }
            ?: timer?.let { text -> UiProblem(text, timerCode?.let { code -> from(code) } ?: UiError.GENERIC) }
}

/**
 * [UiState.error]'s text, its [UiState.errorCode] and its [UiState.errorSubject], set and cleared together. Only a
 * failed command has a [subject].
 */
internal data class UiProblem(val text: String, val code: UiError, val subject: String? = null)
