package com.mooscles.screens.workoutdetail

import com.mooscles.data.LoadConvention
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutDetailModelTest {
    private val bench = ExerciseUi("bench", "Bench", "Chest", "Barbell", LoadConvention.TOTAL_LOAD.label)
    private fun workout(records: List<RecordUi> = emptyList(), sets: List<SetUi> = emptyList()) =
        WorkoutUi("saved", "Saved workout", 1, 2,
            exercises = listOf(WorkoutExerciseUi("entry", bench, sets)), records = records)

    @Test fun emptyWorkoutHasZeroStatistics() {
        assertEquals(DetailStats(0, 0, 0, 0.0), DetailStats.from(workout()))
    }

    @Test fun statisticsUseValidCompletedSetsIncludingWarmupsLikeTheSummary() {
        val workout = workout(sets = listOf(
            SetUi("done", 6, 22.0, completed = true),
            SetUi("warmup", 10, 10.0, completed = true, warmup = true),
            SetUi("pending", 8, 30.0),
            SetUi("no-reps", 0, 20.0, completed = true),
        ))
        assertEquals(2, DetailStats.from(workout).sets)
        assertEquals(1, DetailStats.from(workout).exercises)
        assertEquals(workout.volumeKg, DetailStats.from(workout).volumeKg)
    }

    @Test fun differentRecordTypesAndRepeatedRecordsCountTheVariationOnlyOnce() {
        val weight = RecordUi(bench, 22.0, 6, previousKg = 20.0)
        val reps = RecordUi(bench, 22.0, 7, kind = RecordKind.REPS, previousReps = 6)
        val workout = workout(listOf(weight, reps, weight))
        assertEquals(1, DetailStats.from(workout).prs)
    }

    @Test fun differentConventionsAndExercisesRemainIndependent() {
        val perHand = bench.copy(convention = LoadConvention.PER_HAND.label)
        val machine = bench.copy(id = "machine")
        val workout = workout(listOf(RecordUi(bench, 22.0, 6, previousKg = 20.0),
            RecordUi(perHand, 12.0, 6, previousKg = 10.0), RecordUi(machine, 40.0, 6, previousKg = 35.0)))
        assertEquals(3, DetailStats.from(workout).prs)
    }
}
