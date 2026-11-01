package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mooscles.domain.WorkoutCalculations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    @Test fun catalogIsSubstantialUniqueAndSeedsIdempotently() = runBlocking {
        repo.initialize()
        val data = repo.snapshot()
        assertTrue(data.exercises.size >= 150)
        assertEquals(data.exercises.size, data.exercises.map { it.id }.toSet().size)
        assertTrue(data.exercises.map { it.equipment }.toSet().containsAll(listOf(Equipment.BARBELL,
            Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.CABLE, Equipment.BODYWEIGHT,
            Equipment.SMITH_MACHINE, Equipment.SELECTORIZED_MACHINE, Equipment.PLATE_LOADED_MACHINE)))
    }

    @Test fun routineCrudOrderingReplacementAndDuplicationPersist() = runBlocking {
        val exercises = repo.snapshot().exercises
        val push = repo.createRoutine("Push")
        val pull = repo.createRoutine("Pull")
        val one = repo.addRoutineExercise(push, exercises[0].id)
        val two = repo.addRoutineExercise(push, exercises[1].id)
        val plan = repo.snapshot().routineItems(push).first()
        repo.updateRoutineExercise(plan.copy(plannedSets = 4, targetReps = 8, targetWeightKg = 22.5, restSeconds = 150))
        repo.reorderRoutineExercises(push, listOf(two, one))
        repo.replaceRoutineExercise(two, exercises[2].id)
        repo.reorderRoutines(listOf(pull, push))
        repo.renameRoutine(push, "Push A")
        val copy = repo.duplicateRoutine(push)
        val data = repo.snapshot()
        assertEquals(pull, data.routines.first().id)
        assertEquals(listOf(two, one), data.routineItems(push).map { it.id })
        assertEquals(exercises[2].id, data.routineItems(push).first().exerciseId)
        assertEquals(4, data.routineItems(copy).last().plannedSets)
        assertEquals(22.5, data.routineItems(copy).last().targetWeightKg!!, 0.0)
        assertEquals(150, data.routineItems(copy).last().restSeconds)
        assertTrue(data.routineItems(copy).none { it.id in data.routineItems(push).map { e -> e.id } })
        repo.deleteRoutine(push)
        assertEquals(2, repo.snapshot().routineItems(copy).size)
        assertTrue(repo.snapshot().routineItems(push).isEmpty())
    }

    @Test fun routineAndCustomRenameCannotRewriteHistoricalSnapshots() = runBlocking {
        val exercise = repo.createCustomExercise("My press", MuscleGroup.CHEST, Equipment.DUMBBELL, LoadConvention.PER_HAND)
        val routine = repo.createRoutine("Original routine")
        repo.addRoutineExercise(routine, exercise)
        val workout = repo.startRoutine(routine, now = 1000)
        val entry = repo.snapshot().workoutItems(workout).single()
        repo.updateSet(repo.snapshot().exerciseSets(entry.id).first().copy(reps = 5, weightKg = 20.0, completed = true))
        repo.finishWorkout(workout, now = 61000)
        repo.renameRoutine(routine, "Renamed routine")
        repo.saveExercise(repo.snapshot().exercises.first { it.id == exercise }.copy(name = "Renamed press", convention = LoadConvention.ONE_SIDE))
        repo.deleteRoutine(routine)
        val data = repo.snapshot()
        assertEquals("Original routine", data.workouts.single().name)
        assertEquals("My press", data.workoutItems(workout).single().name)
        assertEquals(LoadConvention.PER_HAND, data.workoutItems(workout).single().convention)
        assertEquals(exercise, data.workoutItems(workout).single().exerciseId)
    }

    @Test fun activeCreationAndFinishingAreAtomicAndIdempotent() = runBlocking {
        val ids = listOf(async { repo.startEmpty(now = 1000) }, async { repo.startEmpty(now = 1000) }).awaitAll()
        assertEquals(1, ids.toSet().size)
        val workout = ids.first()
        val exercise = repo.addWorkoutExercise(workout, repo.snapshot().exercises.first().id)
        val set = repo.snapshot().exerciseSets(exercise).first()
        repo.updateSet(set.copy(weightKg = 40.5, reps = 3, completed = true))
        val results = listOf(async { repo.finishWorkout(workout, now = 61000) }, async { repo.finishWorkout(workout, now = 62000) }).awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, repo.snapshot().workouts.size)
        assertNull(repo.snapshot().activeWorkout)
        assertEquals(1, WorkoutCalculations.summary(repo.snapshot(), workout)!!.completedSetCount)
    }

    @Test fun editingAndDeletingHistoryRecomputeRecords() = runBlocking {
        val exerciseId = repo.snapshot().exercises.first().id
        suspend fun completed(weight: Double, start: Long): Pair<String, String> {
            val workout = repo.startEmpty(now = start)
            val exercise = repo.addWorkoutExercise(workout, exerciseId)
            val set = repo.snapshot().exerciseSets(exercise).first()
            repo.updateSet(set.copy(reps = 3, weightKg = weight, completed = true))
            repo.finishWorkout(workout, start + 1000)
            return workout to set.id
        }
        val first = completed(100.0, 1000)
        val second = completed(105.0, 3000)
        assertEquals(1, WorkoutCalculations.summary(repo.snapshot(), second.first)!!.prs.size)
        val original = repo.snapshot().sets.first { it.id == first.second }
        repo.updateSet(original.copy(weightKg = 110.0))
        assertTrue(WorkoutCalculations.summary(repo.snapshot(), second.first)!!.prs.isEmpty())
        repo.deleteWorkout(first.first)
        assertEquals(1, WorkoutCalculations.summary(repo.snapshot(), second.first)!!.firstRecords.size)
        assertFalse(repo.snapshot().sets.any { it.id == first.second })
    }

    @Test fun discardingRemovesChildrenAndDoesNotProduceRecords() = runBlocking {
        val workout = repo.startEmpty()
        val entry = repo.addWorkoutExercise(workout, repo.snapshot().exercises.first().id)
        repo.updateSet(repo.snapshot().exerciseSets(entry).first().copy(reps = 10, weightKg = 300.0, completed = true))
        repo.discardWorkout(workout)
        val data = repo.snapshot()
        assertTrue(data.workouts.isEmpty())
        assertTrue(data.workoutExercises.isEmpty())
        assertTrue(data.sets.isEmpty())
        assertTrue(WorkoutCalculations.records(data).isEmpty())
    }

    @Test fun processReopenRestoresActiveWorkoutAndDecimalSetsWithoutDuplicates() = runBlocking {
        val fileName = "persistence-${newId()}.db"
        var fileDatabase = Room.databaseBuilder(context, WorkoutDatabase::class.java, fileName).setDriver(AndroidSQLiteDriver()).build()
        try {
            val fileRepo = WorkoutRepository(fileDatabase)
            fileRepo.initialize()
            val workout = fileRepo.startEmpty(now = 1000)
            val entry = fileRepo.addWorkoutExercise(workout, fileRepo.snapshot().exercises.first().id)
            val set = fileRepo.snapshot().exerciseSets(entry).first()
            fileRepo.updateSet(set.copy(weightKg = 22.75, reps = 7, completed = true))
            fileDatabase.close()
            fileDatabase = Room.databaseBuilder(context, WorkoutDatabase::class.java, fileName).setDriver(AndroidSQLiteDriver()).build()
            val reopened = WorkoutRepository(fileDatabase)
            val data = reopened.data.first()
            assertEquals(workout, data.activeWorkout!!.id)
            assertEquals(22.75, data.sets.first { it.id == set.id }.weightKg, 0.0)
            assertTrue(data.sets.first { it.id == set.id }.completed)
            assertEquals(workout, reopened.startEmpty())
            assertEquals(1, reopened.snapshot().workouts.size)
        } finally {
            fileDatabase.close()
            context.deleteDatabase(fileName)
        }
    }

    @Test fun invalidNumericSetsFailWithoutOverwritingPersistedData() = runBlocking {
        val workout = repo.startEmpty()
        val entry = repo.addWorkoutExercise(workout, repo.snapshot().exercises.first().id)
        val set = repo.snapshot().exerciseSets(entry).first()
        try {
            repo.updateSet(set.copy(reps = 1, weightKg = -2.0, completed = true))
            fail("Negative load should be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(set, repo.snapshot().sets.first { it.id == set.id })
        try {
            repo.setCompleted(set.id, true)
            fail("Zero reps cannot be completed")
        } catch (_: IllegalArgumentException) { }
        assertFalse(repo.snapshot().sets.first { it.id == set.id }.completed)
    }
}
