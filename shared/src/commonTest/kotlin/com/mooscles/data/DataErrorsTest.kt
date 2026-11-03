package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DataErrorsTest {
    private fun set(reps: Int = 8, weightKg: Double = 60.0, completed: Boolean = false) =
        WorkoutSet(workoutExerciseId = "entry", position = 0, reps = reps, weightKg = weightKg, completed = completed)

    /** Every rule in [EditRules]: the message is the one users saw before, only the type is new. */
    @Test fun editRulesKeepTheirMessagesAndGainAType() {
        val rules: List<Triple<() -> Unit, DataError, String>> = listOf(
            Triple({ EditRules.name("") }, DataError.INVALID_VALUE, "Enter a name."),
            Triple({ EditRules.name("a".repeat(121)) }, DataError.INVALID_VALUE, "Names must be 120 characters or fewer."),
            Triple({ EditRules.target(10000, null) }, DataError.INVALID_VALUE, "Target reps must be blank or between 1 and 9999."),
            Triple({ EditRules.target(5, -1.0) }, DataError.INVALID_VALUE, "Enter a valid non-negative target weight."),
            Triple({ EditRules.plannedSets(0) }, DataError.INVALID_VALUE, "Planned sets must be between 1 and 50."),
            Triple({ EditRules.routineSetCount(51) }, DataError.SET_LIMIT, "A maximum of 50 planned sets is supported per exercise."),
            Triple({ EditRules.workoutSetCount(101) }, DataError.SET_LIMIT, "A maximum of 100 sets is supported per exercise."),
            Triple({ EditRules.rest(301) }, DataError.INVALID_VALUE, "Rest must be between 0:00 and 5:00."),
            Triple({ EditRules.workoutSet(set(reps = 10000)) }, DataError.INVALID_VALUE, "Reps must be between 0 and 9999."),
            Triple({ EditRules.workoutSet(set(weightKg = Double.NaN)) }, DataError.INVALID_VALUE, "Enter a valid non-negative weight."),
            Triple({ EditRules.workoutSet(set(reps = 0, completed = true)) }, DataError.INVALID_VALUE, "Enter at least 1 rep before completing a set."),
        )
        rules.forEach { (call, error, message) ->
            val failure = assertFailsWith<DataRuleException> { call() }
            assertIs<IllegalArgumentException>(failure)
            assertEquals(error, failure.error, message)
            assertEquals(message, failure.message)
        }
    }

    @Test fun dataRequireThrowsOnlyWhenTheRuleFails() {
        dataRequire(true, DataError.SET_LIMIT) { error("The message is built only on failure") }
        val failure = assertFailsWith<DataRuleException> { dataRequire(false, DataError.SET_LIMIT) { "Too many" } }
        assertEquals(DataError.SET_LIMIT, failure.error)
        assertEquals("Too many", failure.message)
    }

    @Test fun dataRequireNotNullReturnsTheValueOrThrowsItsType() {
        assertEquals("kept", dataRequireNotNull("kept", DataError.SET_NOT_FOUND) { "Set not found." })
        val missing: String? = null
        val failure = assertFailsWith<DataRuleException> { dataRequireNotNull(missing, DataError.SET_NOT_FOUND) { "Set not found." } }
        assertEquals(DataError.SET_NOT_FOUND, failure.error)
        assertEquals("Set not found.", failure.message)
        assertNull(failure.cause)
    }
}
