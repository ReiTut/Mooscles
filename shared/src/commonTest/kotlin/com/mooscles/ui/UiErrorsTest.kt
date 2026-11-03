package com.mooscles.ui

import com.mooscles.data.DataError
import com.mooscles.data.DataRuleException
import com.mooscles.timer.RestTimerError
import com.mooscles.updates.TrainingBlockedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException

class UiErrorsTest {
    @Test fun mapsEveryDataErrorKind() {
        val expected = mapOf(
            DataError.WORKOUT_NOT_FOUND to UiError.WORKOUT_GONE,
            DataError.WORKOUT_EXERCISE_NOT_FOUND to UiError.EXERCISE_GONE,
            DataError.SET_NOT_FOUND to UiError.SET_GONE,
            DataError.EXERCISE_NOT_FOUND to UiError.EXERCISE_GONE,
            DataError.ROUTINE_NOT_FOUND to UiError.GENERIC,
            DataError.FOLDER_NOT_FOUND to UiError.GENERIC,
            DataError.SET_LIMIT to UiError.SET_LIMIT,
            DataError.INVALID_VALUE to UiError.INVALID_VALUE,
            DataError.NO_COMPLETED_SET to UiError.NEEDS_COMPLETED_SET,
            DataError.WORKOUT_NOT_ACTIVE to UiError.WORKOUT_GONE,
            DataError.RESTORE_UNAVAILABLE to UiError.SET_NOT_RESTORED,
        )
        assertEquals(DataError.entries.toSet(), expected.keys, "Every DataError needs an expected UiError here")
        DataError.entries.forEach { kind ->
            assertEquals(expected.getValue(kind), UiErrors.from(DataRuleException(kind, "Refused")), kind.name)
        }
    }

    @Test fun trainingBlockedMapsToBlockedByUpdate() =
        assertEquals(UiError.BLOCKED_BY_UPDATE, UiErrors.from(TrainingBlockedException("An update is being installed.")))

    @Test fun cancellationIsSaveFailed() =
        assertEquals(UiError.SAVE_FAILED, UiErrors.from(CancellationException("Child work was cancelled")))

    @Test fun unknownExceptionIsGeneric() {
        assertEquals(UiError.GENERIC, UiErrors.from(IllegalArgumentException("Untyped require")))
        assertEquals(UiError.GENERIC, UiErrors.from(IllegalStateException("Your workout was saved, but the screen could not open.")))
        assertEquals(UiError.GENERIC, UiErrors.from(RuntimeException()))
    }

    @Test fun timerStorageMapsToTimerStorage() =
        assertEquals(UiError.TIMER_STORAGE, UiErrors.from(RestTimerError.STORAGE_UNAVAILABLE))

    @Test fun preferencesFailureIsLocalDataUnavailable() {
        val problem = UiErrors.current(null, "Could not read saved preferences.", null, null)
        assertEquals(UiProblem("Could not read saved preferences.", UiError.LOCAL_DATA_UNAVAILABLE), problem)
    }

    @Test fun theProblemShownKeepsTodaysOrderAndCarriesItsCode() {
        val action = UiProblem("Set unavailable", UiError.SET_GONE)
        assertEquals(action, UiErrors.current(action, "Preferences", "Timer", RestTimerError.STORAGE_UNAVAILABLE))
        assertEquals(UiProblem("Timer", UiError.TIMER_STORAGE), UiErrors.current(null, null, "Timer", RestTimerError.STORAGE_UNAVAILABLE))
        // A timer text that arrives without a code still shows, as the generic error.
        assertEquals(UiProblem("Timer", UiError.GENERIC), UiErrors.current(null, null, "Timer", null))
        // A code without text is not a problem: the text decides, as UiState.error always did.
        assertNull(UiErrors.current(null, null, null, RestTimerError.STORAGE_UNAVAILABLE))
    }

    @Test fun subjectTravelsWithTheProblem() {
        val action = UiProblem("Routine already exists.", UiError.INVALID_VALUE, subject = "routine-1")
        assertEquals("routine-1", UiErrors.current(action, "Preferences", "Timer", null)?.subject)
        // Only a command has a subject: unreadable preferences and the timer are about nothing the screen sent.
        assertNull(UiErrors.current(null, "Preferences", "Timer", null)!!.subject)
        assertNull(UiErrors.current(null, null, "Timer", RestTimerError.STORAGE_UNAVAILABLE)!!.subject)
    }
}
