package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.math.nextUp
import kotlin.test.*

class RoutineTargetRecordsTest {
    private fun workout(id: String, status: WorkoutStatus = WorkoutStatus.FINISHED) =
        Workout(id = id, name = id, startedAt = 1L, endedAt = if (status == WorkoutStatus.FINISHED) 2L else null,
            status = status)

    private fun exercise(workoutId: String, variation: String = "bench",
                         convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        WorkoutExercise(id = "$workoutId-$variation-${convention.name}", workoutId = workoutId,
            exerciseId = variation, name = variation, muscle = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL, convention = convention)

    private fun set(exercise: WorkoutExercise, kg: Double, reps: Int, completed: Boolean = true,
                    warmup: Boolean = false, assistance: Boolean = false) =
        WorkoutSet(workoutExerciseId = exercise.id, weightKg = kg, reps = reps,
            completed = completed, warmup = warmup, assistance = assistance)

    private fun history(previousKg: Double = 20.0, previousReps: Int = 7,
                        newKg: Double = 22.0, newReps: Int = 6,
                        convention: LoadConvention = LoadConvention.TOTAL_LOAD): AppData {
        val old = exercise("old", convention = convention)
        val new = exercise("new", convention = convention)
        return AppData(workouts = listOf(workout("old"), workout("new")), workoutExercises = listOf(old, new),
            sets = listOf(set(old, previousKg, previousReps), set(new, newKg, newReps)))
    }

    @Test fun `22 kg by 6 replaces 20 kg by 7 despite lower reps and volume`() {
        val record = RoutineTargetRecords.latest(history()).single()
        assertEquals(22.0, record.weightKg!!, 0.0)
        assertEquals(6, record.reps)
        assertTrue(RoutineTargetRecords.improves(record, record.copy(weightKg = 20.0, reps = 7)))
    }

    @Test fun `highest load and repetitions always come from the same actual set`() {
        val data = history()
        val entry = data.workoutItems("new").single()
        val record = RoutineTargetRecords.latest(data.copy(sets = data.sets +
            set(entry, 18.0, 30) + set(entry, 21.0, 20) + set(entry, 22.0, 5))).single()
        assertEquals(22.0, record.weightKg!!, 0.0)
        assertEquals(6, record.reps)
        assertTrue(data.sets.any { it.weightKg == record.weightKg && it.reps == record.reps })
    }

    @Test fun `several PR sets select highest repetitions at the highest eligible load`() {
        val data = history()
        val entry = data.workoutItems("new").single()
        val record = RoutineTargetRecords.latest(data.copy(sets = data.sets +
            set(entry, 23.0, 8) + set(entry, 25.0, 3) + set(entry, 25.0, 5) + set(entry, 22.0, 10))).single()
        assertEquals(25.0, record.weightKg!!, 0.0)
        assertEquals(5, record.reps)
    }

    @Test fun `same-weight repetition improvement advances the saved record`() {
        val record = RoutineTargetRecords.latest(history(newKg = 20.0, newReps = 9)).single()
        assertEquals(20.0, record.weightKg!!, 0.0)
        assertEquals(9, record.reps)
        assertTrue(RoutineTargetRecords.improves(record, record.copy(reps = 7)))
        assertFalse(RoutineTargetRecords.improves(record, record.copy(reps = 9)))
        assertFalse(RoutineTargetRecords.improves(record, record.copy(reps = 10)))
    }

    @Test fun `lower-weight high repetitions cannot downgrade the record weight`() {
        val record = RoutineTargetRecords.latest(history(newKg = 18.0, newReps = 30)).single()
        assertEquals(20.0, record.weightKg!!, 0.0)
        assertEquals(7, record.reps)
        assertFalse(RoutineTargetRecords.improves(record.copy(weightKg = 18.0, reps = 30), record))
    }

    @Test fun `bodyweight repetition record leaves the target weight blank`() {
        val record = RoutineTargetRecords.latest(history(previousKg = 0.0, previousReps = 7,
            newKg = 0.0, newReps = 12, convention = LoadConvention.BODYWEIGHT)).single()
        assertNull(record.weightKg)
        assertEquals(12, record.reps)
        assertTrue(RoutineTargetRecords.improves(record, record.copy(reps = 7)))
    }

    @Test fun `added bodyweight load wins over unloaded repetitions and cannot be downgraded`() {
        val record = RoutineTargetRecords.latest(history(previousKg = 10.0, previousReps = 5,
            newKg = 0.0, newReps = 20, convention = LoadConvention.BODYWEIGHT)).single()
        assertEquals(10.0, record.weightKg!!, 0.0)
        assertEquals(5, record.reps)
        assertFalse(RoutineTargetRecords.improves(record.copy(weightKg = null, reps = 20), record))
        assertTrue(RoutineTargetRecords.improves(record, record.copy(weightKg = null, reps = 20)))
    }

    @Test fun `unfinished warm-up assistance invalid and orphaned sets are ineligible`() {
        val entry = exercise("finished")
        val assisted = exercise("finished", "assisted-pullup", LoadConvention.ASSISTANCE)
        val active = exercise("active")
        val discarded = exercise("discarded")
        val data = AppData(workouts = listOf(workout("finished"), workout("active", WorkoutStatus.ACTIVE)),
            workoutExercises = listOf(entry, assisted, active, discarded), sets = listOf(
                set(entry, 22.0, 6), set(entry, 100.0, 10, completed = false),
                set(entry, 110.0, 10, warmup = true), set(entry, 120.0, 10, assistance = true),
                set(entry, Double.NaN, 10), set(entry, Double.POSITIVE_INFINITY, 10),
                set(entry, -1.0, 10), set(entry, 30.0, 0), set(assisted, 90.0, 20),
                set(active, 150.0, 20), set(discarded, 200.0, 20)))
        val record = RoutineTargetRecords.latest(data).single()
        assertEquals("bench", record.exerciseId)
        assertEquals(22.0, record.weightKg!!, 0.0)
        assertEquals(6, record.reps)
    }

    @Test fun `zero load in a weighted variation does not create a target record`() {
        assertTrue(RoutineTargetRecords.latest(history(previousKg = 0.0, newKg = 0.0)).isEmpty())
    }

    @Test fun `exercise variations and conventions remain independent`() {
        val barbell = exercise("finished", "barbell-bench")
        val cable = exercise("finished", "cable-press")
        val machine = exercise("finished", "machine-press")
        val dumbbell = exercise("finished", "dumbbell-bench", LoadConvention.PER_HAND)
        val otherConvention = exercise("finished", "barbell-bench", LoadConvention.ONE_SIDE)
        val data = AppData(workouts = listOf(workout("finished")),
            workoutExercises = listOf(barbell, cable, machine, dumbbell, otherConvention),
            sets = listOf(set(barbell, 60.0, 8), set(cable, 25.0, 12), set(machine, 80.0, 10),
                set(dumbbell, 20.0, 6), set(otherConvention, 30.0, 9)))
        val records = RoutineTargetRecords.latest(data)
        assertEquals(5, records.size)
        val barbellRecord = records.single { it.exerciseId == "barbell-bench" && it.convention == LoadConvention.TOTAL_LOAD }
        assertFalse(RoutineTargetRecords.improves(barbellRecord, barbellRecord.copy(exerciseId = "machine-press")))
        assertFalse(RoutineTargetRecords.improves(barbellRecord, barbellRecord.copy(convention = LoadConvention.ONE_SIDE)))
        assertEquals(20.0, records.single { it.exerciseId == "dumbbell-bench" }.weightKg!!, 0.0)
    }

    @Test fun `kg-pound noise permits same-load reps improvement without fabricating a heavier record`() {
        val roundTrip = WeightUnits.lbToKg(WeightUnits.kgToLb(20.0)).nextUp()
        val previous = RoutineTargetRecord("bench", LoadConvention.TOTAL_LOAD, 20.0, 7)
        assertFalse(RoutineTargetRecords.improves(previous.copy(weightKg = roundTrip), previous))
        val improved = RoutineTargetRecords.latest(history(newKg = roundTrip, newReps = 9)).single()
        assertEquals(roundTrip, improved.weightKg!!, 0.0)
        assertEquals(9, improved.reps)
        assertTrue(RoutineTargetRecords.improves(improved, previous))
    }

    @Test fun `conversion-equivalent loads retain the actual better repetition set`() {
        val barelyHigher = 20.0.nextUp()
        val record = RoutineTargetRecords.latest(history(newKg = barelyHigher, newReps = 3)).single()
        assertEquals(20.0, record.weightKg!!, 0.0)
        assertEquals(7, record.reps)
    }

    @Test fun `real small decimal load increases are preserved without decimal rounding`() {
        val record = RoutineTargetRecords.latest(history(newKg = 20.0000001, newReps = 1)).single()
        assertEquals(20.0000001, record.weightKg!!, 0.0)
        assertEquals(1, record.reps)
        assertTrue(RoutineTargetRecords.improves(record, record.copy(weightKg = 20.0, reps = 7)))
    }
}
