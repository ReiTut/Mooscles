package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.domain.WorkoutCalculations
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    @Test fun nestedRepositoryCommandsJoinTheOuterTransactionAndFailAllOrNothing() = runBlocking {
        val before = repo.snapshot()
        val error = runCatching {
            database.writeTransaction {
                val routine = repo.createRoutine("Inside the outer transaction")
                // A nested command reads the outer, uncommitted write on the same connection.
                assertTrue(repo.snapshot().routines.any { it.id == routine })
                repo.createRoutine("Rejected", folderId = "missing-folder")
            }
        }.exceptionOrNull()
        assertEquals("Folder not found.", error?.message)
        assertEquals(before, repo.snapshot())
    }

    @Test fun aCaughtNestedFailureStillRollsBackTheOuterTransaction() = runBlocking {
        val before = repo.snapshot()
        val error = runCatching {
            database.writeTransaction {
                repo.createRoutine("A")
                val nested = runCatching {
                    database.writeTransaction {
                        repo.createRoutine("B")
                        error("Nested failure")
                    }
                }.exceptionOrNull()
                assertEquals("Nested failure", nested?.message)
                // The caller swallows the nested failure and returns normally.
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        // Coroutine stack-trace recovery may wrap the exception in a copy, so walk the causes.
        val causes = generateSequence(error) { it.cause }.map { it.message }.toList()
        assertTrue(causes.toString(), "A nested write transaction failed, so the enclosing one is rolled back" in causes)
        assertTrue(causes.toString(), "Nested failure" in causes)
        assertEquals(before, repo.snapshot())
        assertTrue(repo.snapshot().routines.none { it.name == "A" || it.name == "B" })
    }

    /**
     * A nested command must run on the enclosing transaction's connection without starting its own
     * connection use: Room refreshes the invalidation tracker after every `useWriterConnection`, and
     * each refresh would park a Room IO thread on the connection pool until the outer commit.
     *
     * Scoped to this test's own database: its driver records the thread of every statement it
     * prepares. A refresh prepares its `BEGIN` before it blocks, so while the outer transaction
     * holds the connection, any statement prepared on another thread is a use that has to wait.
     * Room's IO threads are shared by every database in the JVM, so stacks alone do not say whose
     * pool a thread is waiting on.
     */
    @Test fun nestedCommandsDoNotUseThisDatabaseFromOtherThreadsDuringTheTransaction() = runBlocking {
        val statements = CopyOnWriteArrayList<Pair<String, Thread>>()
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java)
            .setDriver(RecordingDriver { sql -> statements += sql to Thread.currentThread() }).build()
        val repository = WorkoutRepository(db)
        val observed = Channel<AppData>(Channel.CONFLATED)
        val collector = launch(Dispatchers.Default) { repository.data.collect { observed.send(it) } }
        try {
            repository.initialize()
            withTimeout(10_000) { observed.receive() }
            // Quiescence: earlier top-level commands' refreshes and reloads are finished.
            withTimeout(10_000) {
                do { val count = statements.size; delay(300) } while (statements.size != count)
            }
            val foreign = db.writeTransaction {
                val owner = Thread.currentThread()
                val start = statements.size
                repository.createRoutine("Inside the outer transaction")
                repeat(6) { repository.snapshot() }
                repository.localState()
                // Give any refresh started by a nested call time to reach the connection.
                var found = emptyList<String>()
                for (attempt in 0 until 50) {
                    found = statements.drop(start).filter { it.second !== owner }.map { "${it.second.name}: ${it.first}" }
                    if (found.isNotEmpty()) break
                    Thread.sleep(20)
                }
                found
            }
            assertEquals("Statements on this database from other threads during the transaction", emptyList<String>(), foreign)
            withTimeout(10_000) { while (observed.receive().routines.none { it.name == "Inside the outer transaction" }) Unit }
        } finally {
            collector.cancel()
            db.close()
        }
    }

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

    @Test fun emptyRoutinePlansRemainBlankWhileSavedTargetsInitializeRoutineSets() = runBlocking {
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
        assertTrue(initialSets.all { it.reps == 8 && it.weightKg == 22.5 && !it.completed })
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

        for (invalid in listOf(-1, 301, 3601)) {
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
        var disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
        try {
            val first = WorkoutRepository(disk)
            first.initialize()
            val workout = first.startEmpty(now = 1000)
            val exercise = first.addWorkoutExercise(workout, first.snapshot().exercises.first().id)
            val set = first.snapshot().exerciseSets(exercise).first()
            first.updateSet(set.copy(reps = 7, weightKg = 42.125, completed = true))
            first.updateWorkoutExerciseRest(exercise, 135)
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
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

    @Test fun everyCreationPathDefaultsToTwoSetsWithoutChangingExplicitPlans() = runBlocking {
        val movements = repo.snapshot().exercises.take(2)
        val routine = repo.createRoutine("Two by default")
        val planId = repo.addRoutineExercise(routine, movements[0].id)
        assertEquals(2, repo.snapshot().routineItems(routine).single().plannedSets)
        val workout = repo.startRoutine(routine, now = 1000)
        val routineEntry = repo.snapshot().workoutItems(workout).single()
        assertEquals(2, repo.snapshot().exerciseSets(routineEntry.id).size)
        val added = repo.addWorkoutExercise(workout, movements[1].id)
        assertEquals(2, repo.snapshot().exerciseSets(added).size)
        repo.addSet(added)
        assertEquals(3, repo.snapshot().exerciseSets(added).size)
        repo.removeSet(repo.snapshot().exerciseSets(added).last().id)
        assertEquals(2, repo.snapshot().exerciseSets(added).size)
        repo.replaceWorkoutExercise(added, movements[0].id)
        assertEquals(2, repo.snapshot().exerciseSets(added).size)
        repo.updateSet(repo.snapshot().exerciseSets(routineEntry.id).first().copy(reps = 8, completed = true))
        repo.finishWorkout(workout, now = 2000)
        val plan = repo.snapshot().routineExercises.first { it.id == planId }
        repo.updateRoutineExercise(plan.copy(plannedSets = 5))
        repo.replaceRoutineExercise(planId, movements[1].id)
        val duplicate = repo.duplicateRoutine(routine)
        repo.initialize()
        assertEquals(5, repo.snapshot().routineItems(routine).single().plannedSets)
        assertEquals(5, repo.snapshot().routineItems(duplicate).single().plannedSets)
        val next = repo.startRoutine(duplicate, now = 3000)
        assertEquals(5, repo.snapshot().exerciseSets(repo.snapshot().workoutItems(next).single().id).size)
        assertEquals(2, repo.snapshot().exerciseSets(routineEntry.id).size)
    }

    @Test fun legacyRestNormalizationPreservesCompletedHistoryAndCustomSetCounts() = runBlocking {
        val exerciseId = repo.snapshot().exercises.first().id
        val routine = repo.createRoutine("Legacy plan")
        val planId = repo.addRoutineExercise(routine, exerciseId)
        repeat(2) { repo.addRoutineSet(planId) }
        val plan = repo.snapshot().routineItems(routine).single()
        database.dao().updateRoutineExercise(plan.copy(restSeconds = 600))
        val finished = Workout(id = "legacy-finished", name = "History", startedAt = 1000,
            endedAt = 2000, status = WorkoutStatus.FINISHED)
        val active = Workout(id = "legacy-active", name = "Active", startedAt = 3000)
        database.dao().insertWorkout(finished)
        database.dao().insertWorkout(active)
        val movement = repo.snapshot().exercises.first { it.id == exerciseId }
        val historical = WorkoutExercise(id = "old-history", workoutId = finished.id, exerciseId = exerciseId,
            name = movement.name, muscle = movement.muscle, equipment = movement.equipment,
            convention = movement.convention, position = 0, restSeconds = 600)
        val current = historical.copy(id = "old-active", workoutId = active.id, restSeconds = 900)
        database.dao().insertWorkoutExercise(historical)
        database.dao().insertWorkoutExercise(current)
        val bodyweight = WorkoutSet(workoutExerciseId = historical.id, position = 0, reps = 12,
            weightKg = 0.0, completed = true)
        database.dao().insertSet(bodyweight)
        repo.initialize()
        repo.initialize()
        val data = repo.snapshot()
        assertEquals(300, data.routineItems(routine).single().restSeconds)
        assertEquals(4, data.routineItems(routine).single().plannedSets)
        assertEquals(4, data.plannedSets(plan.id).size)
        assertEquals(300, data.workoutItems(active.id).single().restSeconds)
        assertEquals(historical, data.workoutItems(finished.id).single())
        assertEquals(bodyweight, data.exerciseSets(historical.id).single())
        assertEquals(0.0, WorkoutCalculations.summary(data, finished.id)!!.volumeKg, 0.0)
        for (invalid in listOf(301, 360)) {
            try { repo.updateRoutineExercise(data.routineItems(routine).single().copy(restSeconds = invalid)); fail() }
            catch (_: IllegalArgumentException) { }
            try { repo.updateWorkoutExerciseRest(current.id, invalid); fail() }
            catch (_: IllegalArgumentException) { }
            try { repo.addWorkoutExercise(active.id, exerciseId, invalid); fail() }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(300, repo.snapshot().workoutItems(active.id).single().restSeconds)
    }

    @Test fun bodyweightZeroLoadCompletesAndSurvivesDiskReopenAndHistory() = runBlocking {
        val filename = "bodyweight-${newId()}.db"
        var disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
        try {
            var saved = WorkoutRepository(disk)
            saved.initialize()
            val movement = saved.snapshot().exercises.first { it.convention == LoadConvention.BODYWEIGHT }
            val workout = saved.startEmpty(now = 1000)
            val entry = saved.addWorkoutExercise(workout, movement.id)
            val set = saved.snapshot().exerciseSets(entry).first()
            saved.updateSet(set.copy(reps = 12, weightKg = 0.0, completed = true))
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
            saved = WorkoutRepository(disk)
            saved.initialize()
            val restored = saved.snapshot().sets.first { it.id == set.id }
            assertEquals(0.0, restored.weightKg, 0.0)
            assertEquals(12, restored.reps)
            assertTrue(restored.completed)
            saved.finishWorkout(workout, now = 2000)
            val summary = WorkoutCalculations.summary(saved.snapshot(), workout)!!
            assertEquals(0.0, summary.volumeKg, 0.0)
            assertEquals(1, summary.firstRepsRecords.size)
            assertTrue(summary.firstRecords.isEmpty())
        } finally { disk.close(); context.deleteDatabase(filename) }
    }
}
