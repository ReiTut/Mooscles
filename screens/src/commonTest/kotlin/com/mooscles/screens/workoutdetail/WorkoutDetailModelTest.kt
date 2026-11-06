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
import kotlin.test.assertTrue

class WorkoutDetailModelTest {
    private val bench = ExerciseUi("bench", "Bench", "Chest", "Barbell", LoadConvention.TOTAL_LOAD.label)
    private fun workout(records: List<RecordUi> = emptyList(), sets: List<SetUi> = emptyList()) =
        WorkoutUi("saved", "Saved workout", 1, 2,
            exercises = listOf(WorkoutExerciseUi("entry", bench, sets)), records = records)

    @Test fun emptyWorkoutHasZeroStatisticsAndRecordSections() {
        val workout = workout()
        assertEquals(DetailStats(0, 0, 0, 0.0), DetailStats.from(workout))
        assertEquals(DetailRecordSections(emptyList(), emptyList()), DetailRecordSections.from(workout))
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

    @Test fun firstRecordsQualifyForTheHeadlinePrTotal() {
        val workout = workout(listOf(RecordUi(bench, 22.0, 6)))
        assertEquals(1, DetailStats.from(workout).prs)
        assertTrue(DetailRecordSections.from(workout).beaten.isEmpty())
        assertEquals(workout.records, DetailRecordSections.from(workout).first)
    }

    @Test fun differentRecordTypesAndRepeatedRecordsCountTheVariationOnlyOnce() {
        val weight = RecordUi(bench, 22.0, 6, previousKg = 20.0)
        val reps = RecordUi(bench, 22.0, 7, kind = RecordKind.REPS, previousReps = 6)
        val workout = workout(listOf(weight, reps, weight))
        assertEquals(1, DetailStats.from(workout).prs)
        assertEquals(listOf(weight, reps, weight), DetailRecordSections.from(workout).beaten)
    }

    @Test fun differentConventionsAndExercisesRemainIndependent() {
        val perHand = bench.copy(convention = LoadConvention.PER_HAND.label)
        val machine = bench.copy(id = "machine")
        val workout = workout(listOf(RecordUi(bench, 22.0, 6), RecordUi(perHand, 12.0, 6), RecordUi(machine, 40.0, 6)))
        assertEquals(3, DetailStats.from(workout).prs)
    }

    @Test fun bodyweightRepetitionFirstRecordCountsWithoutInventingLoad() {
        val pullup = bench.copy(id = "pullup", convention = LoadConvention.BODYWEIGHT.label)
        val record = RecordUi(pullup, 0.0, 10, kind = RecordKind.REPS)
        val workout = workout(listOf(record))
        assertEquals(1, DetailStats.from(workout).prs)
        assertEquals(0.0, DetailRecordSections.from(workout).first.single().weightKg)
    }

    @Test fun firstAndBeatenSectionsPreserveTheirRecordValuesAndOrder() {
        val first = RecordUi(bench, 20.0, 7)
        val weight = RecordUi(bench, 22.0, 6, previousKg = 20.0)
        val reps = RecordUi(bench, 22.0, 8, kind = RecordKind.REPS, previousReps = 6)
        val sections = DetailRecordSections.from(workout(listOf(first, weight, reps)))
        assertEquals(listOf(first), sections.first)
        assertEquals(listOf(weight, reps), sections.beaten)
        assertEquals(20.0, sections.beaten.first().previousKg)
        assertEquals(6, sections.beaten.last().previousReps)
    }

}
