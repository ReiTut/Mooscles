package com.mooscles.screens.routines

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** What a routine card says about its routine: counts, muscles and when it was last done (spec "Treinar (aba)"). */
class RoutineSummariesTest {
    private fun exercise(id: String, muscle: String) = ExerciseUi(id, id, muscle, "Barbell")
    private fun planned(id: String, muscle: String, sets: Int) = PlannedExerciseUi("e-$id", exercise(id, muscle), sets = sets)
    private fun routine(vararg exercises: PlannedExerciseUi) = RoutineUi("r", "Routine", exercises.toList())

    @Test
    fun countsExercisesAndPlannedSets() {
        val r = routine(planned("a", "Chest", 4), planned("b", "Back", 3), planned("a", "Chest", 2))
        assertEquals(3, RoutineSummaries.exerciseCount(r))
        assertEquals(9, RoutineSummaries.setCount(r))
        assertEquals(0, RoutineSummaries.exerciseCount(routine()))
        assertEquals(0, RoutineSummaries.setCount(routine()))
    }

    @Test
    fun topThreeMusclesByFrequencyThenEnumOrder() {
        // Triceps x3, Chest x2, Shoulders x2, Back x1, Core x1: Triceps first by frequency; Chest before
        // Shoulders by enum order (a tie); the one-exercise groups are cut by the limit of three.
        val r = routine(
            planned("1", "Shoulders", 3), planned("2", "Triceps", 3), planned("3", "Chest", 3), planned("4", "Triceps", 3),
            planned("5", "Shoulders", 3), planned("6", "Chest", 3), planned("7", "Triceps", 3), planned("8", "Back", 3), planned("9", "Core", 3),
        )
        assertEquals(listOf("Triceps", "Chest", "Shoulders"), RoutineSummaries.topMuscles(r))
        assertEquals(listOf("Triceps"), RoutineSummaries.topMuscles(r, max = 1))
        assertEquals(listOf("Chest", "Back"), RoutineSummaries.topMuscles(routine(planned("1", "Back", 1), planned("2", "Chest", 1))))
        assertEquals(emptyList(), RoutineSummaries.topMuscles(routine()))
    }

    @Test
    fun lastDoneBuckets() {
        val zone = TimeZone.UTC
        fun at(day: Int, hour: Int) = LocalDateTime(2026, 10, day, hour, 0).toInstant(zone).toEpochMilliseconds()
        val now = at(30, 9)
        assertEquals(LastDone.Never, RoutineSummaries.lastDone(null, now, zone))
        // Calendar days, not 24-hour spans: 23:00 yesterday is yesterday, 00:30 today is today.
        assertEquals(LastDone.Today, RoutineSummaries.lastDone(at(30, 0), now, zone))
        assertEquals(LastDone.Today, RoutineSummaries.lastDone(at(30, 20), now, zone))
        assertEquals(LastDone.Yesterday, RoutineSummaries.lastDone(at(29, 23), now, zone))
        assertEquals(LastDone.DaysAgo(3), RoutineSummaries.lastDone(at(27, 12), now, zone))
        val september = { day: Int -> LocalDateTime(2026, 9, day, 12, 0).toInstant(zone).toEpochMilliseconds() }
        assertEquals(LastDone.DaysAgo(30), RoutineSummaries.lastDone(september(30), now, zone))
        val older = september(29)
        assertEquals(LastDone.On(older), RoutineSummaries.lastDone(older, now, zone))
    }
}
