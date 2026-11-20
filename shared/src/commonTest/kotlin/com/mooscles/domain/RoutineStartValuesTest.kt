package com.mooscles.domain

import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineSet
import com.mooscles.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutineStartValuesTest {
    private fun manual(kg: Double?, reps: Int, warmup: Boolean = false) =
        RoutineSet(routineExerciseId = "plan", targetReps = reps, targetWeightKg = kg, inheritPr = false, warmup = warmup)

    private fun follow(kg: Double?, reps: Int) =
        RoutineSet(routineExerciseId = "plan", targetReps = reps, targetWeightKg = kg, inheritPr = true)

    private fun done(kg: Double, reps: Int, warmup: Boolean = false, assistance: Boolean = false, completed: Boolean = true) =
        WorkoutSet(workoutExerciseId = "entry", weightKg = kg, reps = reps, completed = completed,
            warmup = warmup, assistance = assistance)

    private fun start(planned: List<RoutineSet>, previous: List<WorkoutSet>, convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        RoutineStartValues.forPlannedSets(planned, previous, convention).map { it.weightKg to it.reps }

    @Test fun manualSetsCopyTheirPreviousSetInsteadOfTheRoutineTarget() {
        // The reported leg press: targets 100 × 10 and 100 × 9, Previous 120 × 7 and 100 × 9.
        val previous = listOf(done(120.0, 7), done(100.0, 9))
        assertEquals(listOf(120.0 to 7, 100.0 to 9), start(listOf(manual(100.0, 10), manual(100.0, 9)), previous))
    }

    @Test fun aSetWithoutPreviousKeepsItsRoutineTarget() {
        // The reported bench press: one Previous set for a two-set plan.
        val values = start(listOf(manual(25.0, 5), manual(22.0, 6)), listOf(done(27.5, 6)))
        assertEquals(listOf(27.5 to 6, 22.0 to 6), values)
        assertEquals(listOf(25.0 to 5, 0.0 to 8), start(listOf(manual(25.0, 5), manual(null, 8)), emptyList()))
    }

    @Test fun previousIsCopiedExactlyEvenBelowTheTarget() {
        // No record raises or target floors: the fields show what was done last time.
        val previous = listOf(done(60.0, 5), done(102.5, 3))
        assertEquals(listOf(60.0 to 5, 102.5 to 3), start(listOf(manual(100.0, 10), manual(100.0, 10)), previous))
    }

    @Test fun warmupsPairWithPreviousWarmupsAndWorkingSetsWithWorkingSets() {
        val previous = listOf(done(40.0, 10, warmup = true), done(100.0, 8), done(100.0, 6))
        val planned = listOf(manual(20.0, 12, warmup = true), manual(30.0, 8, warmup = true), manual(90.0, 8), manual(90.0, 8))
        // The second warm-up has no Previous warm-up and keeps its target; the 100 × 6 back-off stays 6.
        assertEquals(listOf(40.0 to 10, 30.0 to 8, 100.0 to 8, 100.0 to 6), start(planned, previous))
    }

    @Test fun followTheRecordSetsAlsoCopyTheirPreviousSet() {
        val previous = listOf(done(110.0, 8), done(100.0, 9))
        assertEquals(listOf(110.0 to 8, 100.0 to 9), start(listOf(follow(120.0, 7), manual(90.0, 10)), previous))
    }

    @Test fun followTheRecordSetsWithoutARecordYetStartFromPrevious() {
        val previous = listOf(done(80.0, 8), done(75.0, 10))
        assertEquals(listOf(80.0 to 8, 75.0 to 10), start(listOf(follow(null, 0), follow(75.0, 10)), previous))
    }

    @Test fun followTheRecordSetsWithoutPreviousKeepTheirRecord() {
        assertEquals(listOf(120.0 to 7, 0.0 to 0), start(listOf(follow(120.0, 7), follow(null, 0)), emptyList()))
    }

    @Test fun bodyweightSetsCopyRepsAndAddedLoad() {
        val previous = listOf(done(0.0, 12), done(5.0, 8))
        val values = start(listOf(manual(null, 10), manual(null, 10)), previous, LoadConvention.BODYWEIGHT)
        assertEquals(listOf(0.0 to 12, 5.0 to 8), values)
    }

    @Test fun assistanceSetsCopyTheirAssistance() {
        val previous = listOf(done(30.0, 6, assistance = true))
        val values = start(listOf(manual(40.0, 5), manual(40.0, 5)), previous, LoadConvention.ASSISTANCE)
        assertEquals(listOf(30.0 to 6, 40.0 to 5), values)
    }

    @Test fun anAssistedPreviousSetDoesNotBecomeALoadForARegularSet() {
        val previous = listOf(done(20.0, 8, assistance = true), done(60.0, 8))
        assertEquals(listOf(50.0 to 8, 60.0 to 8), start(listOf(manual(50.0, 8), manual(50.0, 8)), previous))
    }

    @Test fun invalidPreviousRowsAreIgnoredLikeThePreviousColumn() {
        // The repository passes only valid completed sets; anything else is skipped before pairing.
        val previous = listOf(done(100.0, 0), done(Double.NaN, 5), done(90.0, 6, completed = false), done(80.0, 7))
        assertEquals(listOf(80.0 to 7, 70.0 to 5), start(listOf(manual(60.0, 5), manual(70.0, 5)), previous))
    }

    @Test fun anAddedWorkingSetCopiesThePreviousWorkingSetAtItsPlace() {
        val previous = listOf(done(40.0, 10, warmup = true), done(100.0, 8), done(Double.NaN, 5), done(95.0, 7))
        assertEquals(StartValues(100.0, 8), RoutineStartValues.previousWorking(previous, assistance = false, workingIndex = 0))
        assertEquals(StartValues(95.0, 7), RoutineStartValues.previousWorking(previous, assistance = false, workingIndex = 1))
        assertEquals(null, RoutineStartValues.previousWorking(previous, assistance = false, workingIndex = 2))
    }

    @Test fun anAddedSetNeverCopiesAPreviousSetOfTheOtherLoadKind() {
        val previous = listOf(done(20.0, 8, assistance = true))
        assertEquals(null, RoutineStartValues.previousWorking(previous, assistance = false, workingIndex = 0))
        assertEquals(StartValues(20.0, 8), RoutineStartValues.previousWorking(previous, assistance = true, workingIndex = 0))
    }
}
