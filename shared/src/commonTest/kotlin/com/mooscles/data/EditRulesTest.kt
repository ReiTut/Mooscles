package com.mooscles.data

import com.mooscles.domain.RestDuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditRulesTest {
    private fun message(block: () -> Unit): String? =
        assertFailsWith<IllegalArgumentException> { block() }.message

    @Test fun nameTrimsAndAccepts() = assertEquals("Bench", EditRules.name("  Bench  "))

    @Test fun nameRejectsBlankAndTooLong() {
        assertEquals("Enter a name.", message { EditRules.name("   ") })
        assertEquals("Names must be 120 characters or fewer.", message { EditRules.name("a".repeat(121)) })
        assertEquals("a".repeat(120), EditRules.name("a".repeat(120)))
    }

    @Test fun nameRuleAgreesWithTheThrowingRule() {
        for (value in listOf("", "   ", " Bench ", "a".repeat(120), " ${"a".repeat(120)} ", "a".repeat(121))) {
            val accepted = runCatching { EditRules.name(value) }.isSuccess
            assertEquals(accepted, NameRule.problem(value) == null, "\"$value\"")
        }
        assertEquals(NameRule.Problem.BLANK, NameRule.problem(" \t "))
        assertEquals(NameRule.Problem.TOO_LONG, NameRule.problem("a".repeat(NameRule.MAX_LENGTH + 1)))
        assertEquals(null, NameRule.problem("a".repeat(NameRule.MAX_LENGTH)))
    }

    @Test fun targetValidAndInvalid() {
        EditRules.target(0, null)
        EditRules.target(9999, 100000.0)
        assertEquals("Target reps must be blank or between 1 and 9999.", message { EditRules.target(10000, null) })
        assertEquals("Enter a valid non-negative target weight.", message { EditRules.target(5, -1.0) })
        assertEquals("Enter a valid non-negative target weight.", message { EditRules.target(5, Double.NaN) })
    }

    @Test fun plannedSetsBounds() {
        EditRules.plannedSets(1)
        EditRules.plannedSets(50)
        assertEquals("Planned sets must be between 1 and 50.", message { EditRules.plannedSets(0) })
        assertEquals("Planned sets must be between 1 and 50.", message { EditRules.plannedSets(51) })
    }

    @Test fun routineSetCountBounds() {
        EditRules.routineSetCount(EditRules.MAX_ROUTINE_SETS)
        assertEquals("A maximum of 50 planned sets is supported per exercise.", message { EditRules.routineSetCount(51) })
    }

    @Test fun workoutSetCountBounds() {
        EditRules.workoutSetCount(EditRules.MAX_WORKOUT_SETS)
        assertEquals("A maximum of 100 sets is supported per exercise.", message { EditRules.workoutSetCount(101) })
    }

    @Test fun restBounds() {
        EditRules.rest(null)
        EditRules.rest(0)
        EditRules.rest(RestDuration.MAX_SECONDS)
        assertEquals("Rest must be between 0:00 and 5:00.", message { EditRules.rest(-1) })
        assertEquals("Rest must be between 0:00 and 5:00.", message { EditRules.rest(RestDuration.MAX_SECONDS + 1) })
    }

    @Test fun workoutSetRules() {
        val ok = WorkoutSet(workoutExerciseId = "w", reps = 5, weightKg = 20.0, completed = true)
        EditRules.workoutSet(ok)
        assertEquals("Reps must be between 0 and 9999.", message { EditRules.workoutSet(ok.copy(reps = 10000)) })
        assertEquals("Enter a valid non-negative weight.", message { EditRules.workoutSet(ok.copy(weightKg = Double.POSITIVE_INFINITY)) })
        assertEquals("Enter a valid non-negative weight.", message { EditRules.workoutSet(ok.copy(weightKg = -0.5)) })
        assertEquals("Enter at least 1 rep before completing a set.", message { EditRules.workoutSet(ok.copy(reps = 0)) })
    }

    @Test fun limits() {
        assertEquals(50, EditRules.MAX_ROUTINE_SETS)
        assertEquals(100, EditRules.MAX_WORKOUT_SETS)
    }
}
