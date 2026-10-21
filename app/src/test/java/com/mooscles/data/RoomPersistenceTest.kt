package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mooscles.domain.WorkoutCalculations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real generated Room implementation under Android's SQLite runtime, without requiring a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomPersistenceTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    @Test fun routineEditsPreserveWorkoutSnapshotsAndDuplicateIndependentPlans() = runBlocking {
        val exercise = repo.createCustomExercise("Original press", MuscleGroup.CHEST, Equipment.DUMBBELL, LoadConvention.PER_HAND)
        val routine = repo.createRoutine("Push")
        val entry = repo.addRoutineExercise(routine, exercise)
        repo.updateRoutineExercise(repo.snapshot().routineItems(routine).single().copy(
            plannedSets = 4, targetReps = 8, targetWeightKg = 22.75, restSeconds = 135))
        val duplicate = repo.duplicateRoutine(routine)
        val workout = repo.startRoutine(routine, now = 1000)
        val workoutEntry = repo.snapshot().workoutItems(workout).single()
        repo.updateSet(repo.snapshot().exerciseSets(workoutEntry.id).first().copy(reps = 8, completed = true))
        assertTrue(repo.finishWorkout(workout, 61000))
        repo.renameRoutine(routine, "Changed")
        repo.saveExercise(repo.snapshot().exercises.first { it.id == exercise }.copy(name = "Renamed press", convention = LoadConvention.ONE_SIDE))
        repo.removeRoutineExercise(entry)
        repo.deleteRoutine(routine)
        val data = repo.snapshot()
        assertEquals("Push", data.workouts.single().name)
        assertEquals("Original press", data.workoutItems(workout).single().name)
        assertEquals(LoadConvention.PER_HAND, data.workoutItems(workout).single().convention)
        assertEquals(4, data.exerciseSets(workoutEntry.id).size)
        assertEquals(135, data.workoutItems(workout).single().restSeconds)
        assertEquals(22.75, data.exerciseSets(workoutEntry.id).first().weightKg, 0.0)
        assertEquals(4, data.routineItems(duplicate).single().plannedSets)
        assertNotEquals(entry, data.routineItems(duplicate).single().id)
    }

    @Test fun routinePlansAndNewSetsLeaveRepetitionsUnentered() = runBlocking {
        val routine = repo.createRoutine("Push")
        repo.addRoutineExercise(routine, repo.snapshot().exercises.first().id)
        val initialPlan = repo.snapshot().routineItems(routine).single()
        assertEquals(0, initialPlan.targetReps)
        repo.updateRoutineExercise(initialPlan.copy(restSeconds = 120))
        assertEquals(0, repo.snapshot().routineItems(routine).single().targetReps)

        repo.updateRoutineExercise(initialPlan.copy(targetReps = 8, plannedSets = 2, targetWeightKg = 22.5))
        val workout = repo.startRoutine(routine)
        val exercise = repo.snapshot().workoutItems(workout).single()
        val initialSets = repo.snapshot().exerciseSets(exercise.id)
        assertEquals(2, initialSets.size)
        assertTrue(initialSets.all { it.reps == 0 && !it.completed })
        assertEquals(8, repo.snapshot().routineItems(routine).single().targetReps)
        repo.updateSet(initialSets.last().copy(reps = 12, weightKg = 25.0, completed = true))
        val addedId = repo.addSet(exercise.id)
        val added = repo.snapshot().sets.first { it.id == addedId }
        assertEquals(0, added.reps)
        assertFalse(added.completed)
        assertEquals(25.0, added.weightKg, 0.0)
        try {
            repo.setCompleted(addedId, true)
            fail("Unentered reps cannot complete a set")
        } catch (_: IllegalArgumentException) { }
        assertEquals(added, repo.snapshot().sets.first { it.id == addedId })
        assertEquals(12, repo.snapshot().sets.first { it.id == initialSets.last().id }.reps)
    }

    @Test fun exerciseRestChangesAreIndependentAndPreservePersistedExerciseDetails() = runBlocking {
        val routine = repo.createRoutine("Push")
        val exerciseId = repo.snapshot().exercises.first().id
        repo.addRoutineExercise(routine, exerciseId)
        val plan = repo.snapshot().routineItems(routine).single()
        repo.updateRoutineExercise(plan.copy(restSeconds = 120))
        val workout = repo.startRoutine(routine, defaultRestSeconds = 90, now = 1000)
        val entry = repo.snapshot().workoutItems(workout).single()
        repo.updateWorkoutExercise(entry.copy(name = "Workout-specific name"))
        val persisted = repo.snapshot().workoutItems(workout).single()
        repo.updateWorkoutExerciseRest(entry.id, 150)
        assertEquals(persisted.copy(restSeconds = 150), repo.snapshot().workoutItems(workout).single())
        assertEquals(120, repo.snapshot().routineItems(routine).single().restSeconds)

        for (invalid in listOf(-1, 3601)) {
            try {
                repo.updateWorkoutExerciseRest(entry.id, invalid)
                fail("Out-of-range rest duration should be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(150, repo.snapshot().workoutItems(workout).single().restSeconds)
        }

        repo.updateRoutineExercise(plan.copy(restSeconds = 180))
        assertEquals(150, repo.snapshot().workoutItems(workout).single().restSeconds)
        val set = repo.snapshot().exerciseSets(entry.id).first()
        repo.updateSet(set.copy(reps = 5, completed = true))
        repo.finishWorkout(workout, now = 2000)
        val next = repo.startRoutine(routine, now = 3000)
        assertEquals(180, repo.snapshot().workoutItems(next).single().restSeconds)
        assertEquals(150, repo.snapshot().workoutItems(workout).single().restSeconds)
        assertEquals(5, repo.snapshot().sets.first { it.id == set.id }.reps)
    }

    @Test fun duplicateStartAndFinishRemainAtomicWithOnePersistedWorkout() = runBlocking {
        val starts = listOf(async { repo.startEmpty(now = 1000) }, async { repo.startEmpty(now = 1000) }).awaitAll()
        assertEquals(1, starts.toSet().size)
        val workout = starts.first()
        val exercise = repo.addWorkoutExercise(workout, repo.snapshot().exercises.first().id)
        val set = repo.snapshot().exerciseSets(exercise).first()
        repo.updateSet(set.copy(weightKg = 100.0, reps = 3, completed = true))
        val results = listOf(async { repo.finishWorkout(workout, 10000) }, async { repo.finishWorkout(workout, 20000) }).awaitAll()
        assertEquals(1, results.count { it })
        val data = repo.snapshot()
        assertEquals(1, data.workouts.size)
        assertNull(data.activeWorkout)
        assertEquals(WorkoutStatus.FINISHED, data.workouts.single().status)
        assertEquals(1, WorkoutCalculations.summary(data, workout)!!.completedSetCount)
    }

    @Test fun savedHistoryCorrectionsAndDeletionImmediatelyRecalculateRecords() = runBlocking {
        val exerciseId = repo.snapshot().exercises.first { it.convention == LoadConvention.TOTAL_LOAD }.id
        suspend fun completed(weight: Double, start: Long): Pair<String, String> {
            val workout = repo.startEmpty(now = start)
            val exercise = repo.addWorkoutExercise(workout, exerciseId)
            val set = repo.snapshot().exerciseSets(exercise).first()
            repo.updateSet(set.copy(reps = 3, weightKg = weight, completed = true))
            repo.finishWorkout(workout, start + 1000)
            return workout to set.id
        }
        val first = completed(100.0, 1000)
        val next = completed(105.0, 3000)
        assertEquals(1, WorkoutCalculations.summary(repo.snapshot(), next.first)!!.prs.size)
        val original = repo.snapshot().sets.first { it.id == first.second }
        repo.updateSet(original.copy(weightKg = 110.0))
        assertTrue(WorkoutCalculations.summary(repo.snapshot(), next.first)!!.prs.isEmpty())
        assertEquals(110.0, WorkoutCalculations.records(repo.snapshot()).single().weightKg, 0.0)
        repo.deleteWorkout(first.first)
        val afterDelete = repo.snapshot()
        assertFalse(afterDelete.sets.any { it.id == first.second })
        assertEquals(1, WorkoutCalculations.summary(afterDelete, next.first)!!.firstRecords.size)
        assertEquals(105.0, WorkoutCalculations.records(afterDelete).single().weightKg, 0.0)
    }

    @Test fun onDiskReopenRestoresActiveDecimalSetsAndDiscardCascades() = runBlocking {
        val filename = "reopen-${newId()}.db"
        var disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).build()
        try {
            val first = WorkoutRepository(disk)
            first.initialize()
            val workout = first.startEmpty(now = 1000)
            val exercise = first.addWorkoutExercise(workout, first.snapshot().exercises.first().id)
            val set = first.snapshot().exerciseSets(exercise).first()
            first.updateSet(set.copy(reps = 7, weightKg = 42.125, completed = true))
            first.updateWorkoutExerciseRest(exercise, 135)
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).build()
            val second = WorkoutRepository(disk)
            second.initialize()
            val restored = second.snapshot()
            assertEquals(workout, restored.activeWorkout!!.id)
            assertEquals(42.125, restored.sets.first { it.id == set.id }.weightKg, 0.0)
            assertEquals(7, restored.sets.first { it.id == set.id }.reps)
            assertTrue(restored.sets.first { it.id == set.id }.completed)
            assertEquals(135, restored.workoutItems(workout).single().restSeconds)
            assertEquals(workout, second.startEmpty())
            assertEquals(1, second.snapshot().workouts.size)
            assertTrue(restored.exercises.size >= 150)
            assertEquals(restored.exercises.size, restored.exercises.map { it.id }.toSet().size)
            second.discardWorkout(workout)
            assertTrue(second.snapshot().workoutExercises.isEmpty())
            assertTrue(second.snapshot().sets.isEmpty())
            assertTrue(WorkoutCalculations.records(second.snapshot()).isEmpty())
        } finally {
            disk.close()
            context.deleteDatabase(filename)
        }
    }
}
