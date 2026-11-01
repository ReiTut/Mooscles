package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.domain.RoutineTargetRecords
import com.mooscles.domain.WeightUnits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineSetPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private var clock = 1000L

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }
    @After fun close() { database.close() }

    private suspend fun movement(convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        repo.createCustomExercise("Movement ${newId()}", MuscleGroup.CHEST,
            if (convention == LoadConvention.BODYWEIGHT) Equipment.BODYWEIGHT else Equipment.BARBELL, convention)

    private suspend fun plan(exerciseId: String): String {
        val routineId = repo.createRoutine("Routine ${newId()}")
        return repo.addRoutineExercise(routineId, exerciseId)
    }

    private suspend fun finish(exerciseId: String, vararg pairs: Pair<Double, Int>): String {
        val id = repo.startEmpty(now = clock.also { clock += 1000 })
        val entry = repo.addWorkoutExercise(id, exerciseId)
        while (repo.snapshot().exerciseSets(entry).size < pairs.size) repo.addSet(entry)
        pairs.forEachIndexed { index, pair ->
            repo.updateSet(repo.snapshot().exerciseSets(entry)[index].copy(weightKg = pair.first,
                reps = pair.second, completed = true))
        }
        repo.finishWorkout(id, now = clock)
        return id
    }

    @Test fun twoIndependentDefaultRowsInheritCurrentSameSetRecordAndNeverBecomePerformance() = runBlocking {
        val exercise = movement()
        finish(exercise, 20.0 to 20, 22.0 to 6)
        val entry = plan(exercise)
        val rows = repo.snapshot().plannedSets(entry)
        assertEquals(2, rows.size)
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertTrue(rows.all { it.inheritPr && it.targetWeightKg == 22.0 && it.targetReps == 6 })
        repo.updateRoutineSet(rows[0].id, 99, 500.0)
        assertEquals(22.0, RoutineTargetRecords.latest(repo.snapshot()).single().weightKg!!, 0.0)
        assertEquals(6, repo.snapshot().plannedSets(entry)[1].targetReps)
        val added = repo.addRoutineSet(entry)
        assertEquals(22.0, repo.snapshot().plannedSets(entry).first { it.id == added }.targetWeightKg!!, 0.0)
        assertTrue(repo.snapshot().plannedSets(entry).first { it.id == added }.inheritPr)
    }

    @Test fun exampleRowsSaveIndependentlyDuplicateInOrderAndInitializeCorrespondingWorkoutSets() = runBlocking {
        val exercise = movement()
        val entry = plan(exercise)
        repo.addRoutineSet(entry)
        val initial = repo.snapshot().plannedSets(entry)
        val targets = listOf(70.0 to 6, 65.0 to 10, 65.0 to 8)
        targets.forEachIndexed { index, pair -> repo.updateRoutineSet(initial[index].id, pair.second, pair.first) }
        repo.updateRoutineExerciseRest(entry, 155)
        val source = repo.snapshot()
        val routine = source.routineExercises.single { it.id == entry }.routineId
        val duplicate = repo.duplicateRoutine(routine)
        val copiedEntry = repo.snapshot().routineItems(duplicate).single()
        val copiedRows = repo.snapshot().plannedSets(copiedEntry.id)
        assertEquals(targets, copiedRows.map { it.targetWeightKg to it.targetReps })
        assertEquals(listOf(0, 1, 2), copiedRows.map { it.position })
        assertTrue(copiedRows.none { it.inheritPr })
        assertTrue(copiedRows.none { row -> row.id in initial.map { it.id } })
        assertEquals(155, copiedEntry.restSeconds)
        val workout = repo.startRoutine(duplicate, defaultRestSeconds = 30)
        val snapshot = repo.snapshot()
        val exerciseEntry = snapshot.workoutItems(workout).single()
        assertEquals(155, exerciseEntry.restSeconds)
        assertEquals(targets, snapshot.exerciseSets(exerciseEntry.id).map { it.weightKg to it.reps })
        assertTrue(snapshot.exerciseSets(exerciseEntry.id).none { it.completed })
        repo.updateRoutineSet(copiedRows[1].id, 3, 100.0)
        assertEquals(targets, repo.snapshot().exerciseSets(exerciseEntry.id).map { it.weightKg to it.reps })
        assertEquals(targets, repo.snapshot().plannedSets(entry).map { it.targetWeightKg to it.targetReps })
        repo.discardWorkout(workout)
        repo.deleteRoutine(duplicate)
        assertTrue(repo.snapshot().plannedSets(copiedEntry.id).isEmpty())
    }

    @Test fun newWeightAndSameWeightRepRecordsUpdateOnlyInheritedRowsAfterFinalization() = runBlocking {
        val exercise = movement()
        finish(exercise, 20.0 to 7)
        val entry = plan(exercise)
        val rows = repo.snapshot().plannedSets(entry)
        repo.updateRoutineSet(rows[1].id, 10, 18.0)
        val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
        val active = repo.startRoutine(routine)
        val activeEntry = repo.snapshot().workoutItems(active).single()
        val activeSet = repo.snapshot().exerciseSets(activeEntry.id).first()
        repo.updateSet(activeSet.copy(weightKg = 22.0, reps = 6, completed = true))
        assertEquals(20.0, repo.snapshot().plannedSets(entry).first().targetWeightKg!!, 0.0)
        val before = repo.snapshot().exerciseSets(activeEntry.id)
        repo.finishWorkout(active)
        assertEquals(before, repo.snapshot().exerciseSets(activeEntry.id))
        assertEquals(22.0 to 6, repo.snapshot().plannedSets(entry).first().let { it.targetWeightKg to it.targetReps })
        assertEquals(18.0 to 10, repo.snapshot().plannedSets(entry).last().let { it.targetWeightKg to it.targetReps })
        finish(exercise, 22.0 to 8, 18.0 to 40)
        assertEquals(22.0 to 8, repo.snapshot().plannedSets(entry).first().let { it.targetWeightKg to it.targetReps })
        assertEquals(18.0 to 10, repo.snapshot().plannedSets(entry).last().let { it.targetWeightKg to it.targetReps })
        finish(exercise, 20.0 to 50)
        assertEquals(22.0 to 8, repo.snapshot().plannedSets(entry).first().let { it.targetWeightKg to it.targetReps })
    }

    @Test fun editingOnlyRepsOrOnlyWeightMakesExactlyThatRowCustomEvenWhenBlank() = runBlocking {
        val exercise = movement()
        finish(exercise, 22.0 to 6)
        val entry = plan(exercise)
        repo.addRoutineSet(entry)
        val rows = repo.snapshot().plannedSets(entry)
        repo.updateRoutineSet(rows[0].id, 7, rows[0].targetWeightKg)
        repo.updateRoutineSet(rows[1].id, rows[1].targetReps, null)
        val before = repo.snapshot().plannedSets(entry)
        assertFalse(before[0].inheritPr)
        assertFalse(before[1].inheritPr)
        assertNull(before[1].targetWeightKg)
        finish(exercise, 25.0 to 4)
        val after = repo.snapshot().plannedSets(entry)
        assertEquals(before.take(2), after.take(2))
        assertEquals(25.0 to 4, after[2].let { it.targetWeightKg to it.targetReps })
        repo.inheritRoutineSet(rows[1].id)
        val restored = repo.snapshot().plannedSets(entry)[1]
        assertTrue(restored.inheritPr)
        assertEquals(25.0 to 4, restored.targetWeightKg to restored.targetReps)
    }

    @Test fun bodyweightInheritedRecordsKeepBlankLoadWhileExplicitBlankStaysCustom() = runBlocking {
        val exercise = movement(LoadConvention.BODYWEIGHT)
        val entry = plan(exercise)
        val first = repo.snapshot().plannedSets(entry)[0]
        repo.updateRoutineSet(first.id, 7, null)
        finish(exercise, 0.0 to 12)
        val rows = repo.snapshot().plannedSets(entry)
        assertEquals(null to 7, rows[0].targetWeightKg to rows[0].targetReps)
        assertFalse(rows[0].inheritPr)
        assertEquals(null to 12, rows[1].targetWeightKg to rows[1].targetReps)
        assertTrue(rows[1].inheritPr)
    }

    @Test fun removalReindexesSurvivorsAndCascadesWithRoutineWithoutMutatingOtherRows() = runBlocking {
        val entry = plan(movement())
        repo.addRoutineSet(entry)
        val rows = repo.snapshot().plannedSets(entry)
        rows.forEachIndexed { index, row -> repo.updateRoutineSet(row.id, index + 6, 20.5 + index) }
        val before = repo.snapshot().plannedSets(entry)
        repo.removeRoutineSet(rows[1].id)
        assertEquals(listOf(before[0], before[2].copy(position = 1)), repo.snapshot().plannedSets(entry))
        repo.removeRoutineSet(rows[2].id)
        try { repo.removeRoutineSet(rows[0].id); fail("Keep at least one row") } catch (_: IllegalArgumentException) { }
        val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
        repo.deleteRoutine(routine)
        assertTrue(repo.snapshot().routineSets.isEmpty())
    }

    @Test fun restIsPerRoutineExerciseAndDefaultsTo120DespiteGlobalPreference() = runBlocking {
        val exercise = movement()
        val one = plan(exercise)
        val two = plan(exercise)
        repo.updateRoutineExerciseRest(one, 95)
        assertEquals(120, repo.snapshot().routineExercises.single { it.id == two }.restSeconds)
        for (entry in listOf(one, two)) {
            val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
            val workout = repo.startRoutine(routine, defaultRestSeconds = 20)
            assertEquals(if (entry == one) 95 else 120, repo.snapshot().workoutItems(workout).single().restSeconds)
            repo.discardWorkout(workout)
        }
        val second = repo.snapshot().routineExercises.single { it.id == two }
        database.dao().updateRoutineExercise(second.copy(restSeconds = null))
        val workout = repo.startRoutine(second.routineId, defaultRestSeconds = 20)
        assertEquals(120, repo.snapshot().workoutItems(workout).single().restSeconds)
        repo.discardWorkout(workout)
        for (invalid in listOf(-1, 301, 999)) {
            try { repo.updateRoutineExerciseRest(one, invalid); fail("Invalid rest") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(95, repo.snapshot().routineExercises.single { it.id == one }.restSeconds)
        repo.updateRoutineExerciseRest(one, 300)
        assertEquals(300, repo.snapshot().routineExercises.single { it.id == one }.restSeconds)
    }

    @Test fun decimalPoundsTargetsAreStoredOnceAsKgAndNeverLeakAcrossVariations() = runBlocking {
        val dumbbell = movement(LoadConvention.PER_HAND)
        val cable = repo.createCustomExercise("Same press", MuscleGroup.CHEST, Equipment.CABLE, LoadConvention.TOTAL_LOAD)
        val first = plan(dumbbell)
        val second = plan(cable)
        val kg = WeightUnits.lbToKg(50.5)
        val row = repo.snapshot().plannedSets(first).first()
        repo.updateRoutineSet(row.id, 8, kg)
        finish(dumbbell, WeightUnits.lbToKg(55.5) to 6)
        assertEquals(kg, repo.snapshot().plannedSets(first).first().targetWeightKg!!, 0.0)
        assertTrue(repo.snapshot().plannedSets(second).all { it.targetWeightKg == null && it.targetReps == 0 })
        val routine = repo.snapshot().routineExercises.single { it.id == first }.routineId
        val workout = repo.startRoutine(routine)
        assertEquals(kg, repo.snapshot().exerciseSets(repo.snapshot().workoutItems(workout).single().id).first().weightKg, 0.0)
    }

    @Test fun perRowValuesInheritanceModesRestAndOrderSurviveDiskReopenAndNewRecords() = runBlocking {
        val filename = "routine-rows-${newId()}.db"
        var disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
        try {
            repo = WorkoutRepository(disk)
            repo.initialize()
            val exercise = movement()
            finish(exercise, 17.0 to 8)
            val entry = plan(exercise)
            repo.addRoutineSet(entry)
            val rows = repo.snapshot().plannedSets(entry)
            repo.updateRoutineSet(rows[1].id, 8, 17.0)
            repo.updateRoutineSet(rows[2].id, 7, null)
            repo.updateRoutineExerciseRest(entry, 145)
            val before = repo.snapshot()
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
            repo = WorkoutRepository(disk)
            repo.initialize()
            assertEquals(before.plannedSets(entry), repo.snapshot().plannedSets(entry))
            finish(exercise, 19.5 to 6)
            val after = repo.snapshot().plannedSets(entry)
            assertEquals(19.5 to 6, after[0].targetWeightKg to after[0].targetReps)
            assertEquals(before.plannedSets(entry).drop(1), after.drop(1))
            val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
            val workout = repo.startRoutine(routine)
            val workoutEntry = repo.snapshot().workoutItems(workout).single()
            assertEquals(145, workoutEntry.restSeconds)
            assertEquals(listOf(19.5 to 6, 17.0 to 8, 0.0 to 7), repo.snapshot().exerciseSets(workoutEntry.id).map { it.weightKg to it.reps })
        } finally { disk.close(); context.deleteDatabase(filename) }
    }

    @Test fun invalidTargetsDoNotOverwritePersistedRowsAndReplacementKeepsCountButUsesNewVariation() = runBlocking {
        val entry = plan(movement())
        repo.addRoutineSet(entry)
        val row = repo.snapshot().plannedSets(entry).first()
        repo.updateRoutineSet(row.id, 7, 17.0)
        val saved = repo.snapshot().plannedSets(entry)
        for ((reps, load) in listOf(-1 to 10.0, 10000 to 10.0, 7 to -1.0, 7 to Double.NaN, 7 to Double.POSITIVE_INFINITY)) {
            try { repo.updateRoutineSet(row.id, reps, load); fail("Invalid target") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(saved, repo.snapshot().plannedSets(entry))
        val replacement = movement(LoadConvention.BODYWEIGHT)
        finish(replacement, 0.0 to 12)
        repo.replaceRoutineExercise(entry, replacement)
        val replaced = repo.snapshot().plannedSets(entry)
        assertEquals(saved.map { it.id }, replaced.map { it.id })
        assertEquals(3, replaced.size)
        assertTrue(replaced.all { it.inheritPr && it.targetWeightKg == null && it.targetReps == 12 })
    }

    @Test fun genuineRecordBelowDeletedMaximumUpdatesExistingAndNewInheritedRows() = runBlocking {
        val exercise = movement()
        val entry = plan(exercise)
        val initial = repo.snapshot().plannedSets(entry)
        // The manual row comes first: a new row copies the last one (phase 4B), and only an inherited last row
        // makes the new one inherit.
        repo.updateRoutineSet(initial[0].id, 10, 17.0)
        finish(exercise, 20.0 to 8)
        val removed = finish(exercise, 30.0 to 5)
        assertEquals(30.0, database.dao().targetCheckpoints().single().weightKg!!, 0.0)
        repo.deleteWorkout(removed)
        val added = repo.addRoutineSet(entry)
        assertEquals(20.0 to 8, repo.snapshot().plannedSets(entry).single { it.id == added }.let { it.targetWeightKg to it.targetReps })
        // The last observed checkpoint is still 30 kg; 22 kg is a valid new PR in surviving history.
        val next = finish(exercise, 22.0 to 6)
        val rows = repo.snapshot().plannedSets(entry)
        assertEquals(listOf(17.0 to 10, 22.0 to 6, 22.0 to 6), rows.map { it.targetWeightKg to it.targetReps })
        assertEquals(listOf(false, true, true), rows.map { it.inheritPr })
        assertEquals(22.0, database.dao().targetCheckpoints().single().weightKg!!, 0.0)
        val completed = repo.snapshot().exerciseSets(repo.snapshot().workoutItems(next).single().id)
        finish(exercise, 18.0 to 50)
        assertEquals(rows, repo.snapshot().plannedSets(entry))
        assertEquals(completed, repo.snapshot().exerciseSets(repo.snapshot().workoutItems(next).single().id))
    }

    @Test fun correctedHistoricalMaximumRefreshesInheritedRowsWithoutChangingActiveSnapshots() = runBlocking {
        val exercise = movement()
        val entry = plan(exercise)
        val custom = repo.snapshot().plannedSets(entry)[1]
        repo.updateRoutineSet(custom.id, 9, 17.5)
        finish(exercise, 20.0 to 8)
        val corrected = finish(exercise, 30.0 to 5)
        val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
        val active = repo.startRoutine(routine)
        val historyEntry = repo.snapshot().workoutItems(corrected).single()
        val record = repo.snapshot().exerciseSets(historyEntry.id).first()
        repo.updateSet(record.copy(weightKg = 18.0, reps = 30))
        val before = repo.snapshot()
        repo.initialize()
        val after = repo.snapshot()
        assertEquals(20.0 to 8, after.plannedSets(entry)[0].let { it.targetWeightKg to it.targetReps })
        assertEquals(17.5 to 9, after.plannedSets(entry)[1].let { it.targetWeightKg to it.targetReps })
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(before.exerciseSets(before.workoutItems(active).single().id), after.exerciseSets(after.workoutItems(active).single().id))
        repo.discardWorkout(active)
        finish(exercise, 22.0 to 6)
        assertEquals(22.0 to 6, repo.snapshot().plannedSets(entry)[0].let { it.targetWeightKg to it.targetReps })
    }

    @Test fun removingAllEligibleHistoryClearsOnlyInheritedDefaultsAndPreservesActiveWorkout() = runBlocking {
        val exercise = movement()
        val entry = plan(exercise)
        val custom = repo.snapshot().plannedSets(entry)[1]
        repo.updateRoutineSet(custom.id, 7, null)
        val finished = finish(exercise, 30.0 to 5)
        val routine = repo.snapshot().routineExercises.single { it.id == entry }.routineId
        val active = repo.startRoutine(routine)
        repo.deleteWorkout(finished)
        val before = repo.snapshot()
        repo.initialize()
        val after = repo.snapshot()
        assertEquals(null to 0, after.plannedSets(entry)[0].let { it.targetWeightKg to it.targetReps })
        assertTrue(after.plannedSets(entry)[0].inheritPr)
        assertEquals(before.plannedSets(entry)[1], after.plannedSets(entry)[1])
        assertEquals(before.workoutItems(active), after.workoutItems(active))
        assertEquals(before.sets, after.sets)
        repo.discardWorkout(active)
        finish(exercise, 22.0 to 6)
        assertEquals(22.0 to 6, repo.snapshot().plannedSets(entry)[0].let { it.targetWeightKg to it.targetReps })
        assertEquals(null to 7, repo.snapshot().plannedSets(entry)[1].let { it.targetWeightKg to it.targetReps })
    }
}
