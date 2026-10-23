package com.mooscles.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Full-order saves touch instance positions only and survive an actual on-disk SQLite reopen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseReorderPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val filename = "order-${newId().take(8)}.db"
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private lateinit var press: String
    private lateinit var cablePress: String

    @Before fun setup() = runBlocking {
        open()
        press = repo.createCustomExercise("Same display name", MuscleGroup.CHEST,
            Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        cablePress = repo.createCustomExercise("Same display name", MuscleGroup.CHEST,
            Equipment.CABLE, LoadConvention.TOTAL_LOAD)
    }

    private fun open() {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).build()
        repo = WorkoutRepository(database)
    }

    private suspend fun reopen() {
        database.close()
        open()
        repo.initialize()
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(filename)
    }

    private suspend fun plan(name: String = "Repeated exercises"): String {
        val id = repo.createRoutine(name)
        // Two occurrences of one catalog exercise, plus an identically named variation.
        val entries = listOf(repo.addRoutineExercise(id, press), repo.addRoutineExercise(id, press),
            repo.addRoutineExercise(id, cablePress))
        entries.forEachIndexed { index, entry ->
            if (index == 1) repo.addRoutineSet(entry)
            repo.updateRoutineExerciseRest(entry, listOf(65, 120, 175)[index])
            repo.snapshot().plannedSets(entry).forEachIndexed { setIndex, set ->
                repo.updateRoutineSet(set.id, 5 + index + setIndex,
                    if (index == 2 && setIndex == 0) null else 20.0 + index * 5 + setIndex)
            }
        }
        return id
    }

    private suspend fun recordProgress(workout: String) {
        repo.snapshot().workoutItems(workout).forEachIndexed { index, entry ->
            repo.updateWorkoutExercise(entry.copy(name = "Occurrence $index", restSeconds = 75 + index * 50))
            repo.snapshot().exerciseSets(entry.id).forEachIndexed { setIndex, row ->
                repo.updateSet(row.copy(reps = 7 + index + setIndex, weightKg = 22.5 + index + setIndex,
                    completed = index == 0 && setIndex == 0, warmup = setIndex == 1,
                    assistance = index == 2))
            }
        }
    }

    private suspend fun rejected(block: suspend () -> Unit) {
        try {
            block()
            fail("A stale, invalid, or non-active order must be rejected before any row changes")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun routineOrderKeepsIndependentTargetsAndRestAndControlsFutureWorkoutsAfterReopen() = runBlocking {
        val routine = plan()
        val unrelated = plan("Other routine")
        val before = repo.snapshot()
        val entries = before.routineItems(routine)
        assertEquals(entries[0].exerciseId, entries[1].exerciseId)
        assertNotEquals(entries[0].id, entries[1].id)
        val ordered = listOf(entries[1].id, entries[2].id, entries[0].id)

        repo.reorderRoutineExercises(routine, ordered)
        val saved = repo.snapshot()
        assertEquals(ordered, saved.routineItems(routine).map { it.id })
        assertEquals(ordered.indices.toList(), saved.routineItems(routine).map { it.position })
        entries.forEach { entry ->
            val moved = saved.routineItems(routine).single { it.id == entry.id }
            assertEquals(entry.copy(position = ordered.indexOf(entry.id)), moved)
            assertEquals(before.plannedSets(entry.id), saved.plannedSets(entry.id))
        }
        assertEquals(before.routines, saved.routines)
        assertEquals(before.routineItems(unrelated), saved.routineItems(unrelated))
        assertEquals(before.routineSets, saved.routineSets)
        assertEquals(before.workouts, saved.workouts)
        assertEquals(before.workoutExercises, saved.workoutExercises)
        assertEquals(before.sets, saved.sets)

        reopen()
        val restored = repo.snapshot()
        assertEquals(saved.routineItems(routine), restored.routineItems(routine))
        assertEquals(saved.routineSets, restored.routineSets)
        val workout = repo.startRoutine(routine, now = 1000)
        val snapshot = repo.snapshot()
        val actual = snapshot.workoutItems(workout)
        assertEquals(ordered.map { id -> entries.single { it.id == id }.exerciseId }, actual.map { it.exerciseId })
        actual.zip(restored.routineItems(routine)).forEach { (exercise, prescription) ->
            assertEquals(prescription.restSeconds, exercise.restSeconds)
            assertEquals(restored.plannedSets(prescription.id).map { it.targetReps },
                snapshot.exerciseSets(exercise.id).map { it.reps })
            assertEquals(restored.plannedSets(prescription.id).map { it.targetWeightKg ?: 0.0 },
                snapshot.exerciseSets(exercise.id).map { it.weightKg })
        }
    }

    @Test fun activeOrderPreservesDuplicateInstancesProgressSourcePlanAndFinishedHistoryOnReopen() = runBlocking {
        val routine = plan()
        val finished = repo.startRoutine(routine, now = 1000)
        recordProgress(finished)
        repo.finishWorkout(finished, now = 2000)
        val active = repo.startRoutine(routine, now = 3000)
        recordProgress(active)
        val before = repo.snapshot()
        val entries = before.workoutItems(active)
        val ordered = listOf(entries[2].id, entries[0].id, entries[1].id)

        repo.reorderWorkoutExercises(active, ordered)
        val saved = repo.snapshot()
        assertEquals(ordered, saved.workoutItems(active).map { it.id })
        assertEquals(ordered.indices.toList(), saved.workoutItems(active).map { it.position })
        entries.forEach { entry ->
            assertEquals(entry.copy(position = ordered.indexOf(entry.id)),
                saved.workoutItems(active).single { it.id == entry.id })
            assertEquals(before.exerciseSets(entry.id), saved.exerciseSets(entry.id))
        }
        assertEquals(before.workouts, saved.workouts)
        assertEquals(before.workoutItems(finished), saved.workoutItems(finished))
        assertEquals(before.sets, saved.sets)
        assertEquals(before.routines, saved.routines)
        assertEquals(before.routineExercises, saved.routineExercises)
        assertEquals(before.routineSets, saved.routineSets)

        reopen()
        val restored = repo.snapshot()
        assertEquals(saved.workouts, restored.workouts)
        assertEquals(saved.workoutExercises, restored.workoutExercises)
        assertEquals(saved.sets, restored.sets)
        assertEquals(saved.routineExercises, restored.routineExercises)
        assertEquals(saved.routineSets, restored.routineSets)
        assertEquals(active, repo.startRoutine(routine, now = 5000))
        assertEquals(ordered, repo.snapshot().workoutItems(active).map { it.id })
    }

    @Test fun routineRejectsMissingRepeatedForeignAndStaleInstanceIdsWithoutPartialWrites() = runBlocking {
        val routine = plan()
        val foreignRoutine = plan("Foreign")
        val before = repo.snapshot()
        val ids = before.routineItems(routine).map { it.id }
        val foreign = before.routineItems(foreignRoutine).first().id
        for (invalid in listOf(emptyList(), ids.dropLast(1), ids + "missing", listOf(ids[2], ids[0], ids[0]),
            listOf(ids[2], foreign, ids[0]), listOf(ids[2], press, ids[0]))) {
            rejected { repo.reorderRoutineExercises(routine, invalid) }
            assertEquals(before, repo.snapshot())
        }
        repo.addRoutineExercise(routine, press)
        val concurrentlyChanged = repo.snapshot()
        rejected { repo.reorderRoutineExercises(routine, ids.reversed()) }
        assertEquals(concurrentlyChanged, repo.snapshot())
        rejected { repo.reorderRoutineExercises("missing-routine", emptyList()) }
        assertEquals(concurrentlyChanged, repo.snapshot())
    }

    @Test fun activeRejectsInvalidAndStaleOrdersWithoutTouchingAnyPerformanceRows() = runBlocking {
        val routine = plan()
        val active = repo.startRoutine(routine, now = 1000)
        recordProgress(active)
        val before = repo.snapshot()
        val ids = before.workoutItems(active).map { it.id }
        val routineInstanceId = before.routineItems(routine).first().id
        for (invalid in listOf(emptyList(), ids.dropLast(1), ids + "missing", listOf(ids[2], ids[1], ids[1]),
            listOf(ids[2], routineInstanceId, ids[0]), listOf(ids[2], press, ids[0]))) {
            rejected { repo.reorderWorkoutExercises(active, invalid) }
            assertEquals(before, repo.snapshot())
        }
        repo.addWorkoutExercise(active, press)
        val concurrentlyChanged = repo.snapshot()
        rejected { repo.reorderWorkoutExercises(active, ids.reversed()) }
        assertEquals(concurrentlyChanged, repo.snapshot())
        rejected { repo.reorderWorkoutExercises("missing-workout", emptyList()) }
        assertEquals(concurrentlyChanged, repo.snapshot())
    }

    @Test fun finishedAndDiscardedSessionsCannotBeChangedByAnOldOrderingScreen() = runBlocking {
        val routine = plan()
        val finished = repo.startRoutine(routine, now = 1000)
        recordProgress(finished)
        val finishedIds = repo.snapshot().workoutItems(finished).map { it.id }
        repo.finishWorkout(finished, now = 2000)
        val discarded = repo.startRoutine(routine, now = 3000)
        val discardedIds = repo.snapshot().workoutItems(discarded).map { it.id }
        repo.discardWorkout(discarded)
        val before = repo.snapshot()
        rejected { repo.reorderWorkoutExercises(finished, finishedIds.reversed()) }
        assertEquals(before, repo.snapshot())
        rejected { repo.reorderWorkoutExercises(discarded, discardedIds.reversed()) }
        assertEquals(before, repo.snapshot())
        reopen()
        assertEquals(before.workouts, repo.snapshot().workouts)
        assertEquals(before.workoutExercises, repo.snapshot().workoutExercises)
        assertEquals(before.sets, repo.snapshot().sets)
        assertNull(repo.snapshot().activeWorkout)
    }

    @Test fun storageFailureRollsBackEarlierPositionWritesForBothOwners() = runBlocking {
        val routine = plan()
        val active = repo.startRoutine(routine, now = 1000)
        recordProgress(active)
        val before = repo.snapshot()
        val routineIds = before.routineItems(routine).map { it.id }
        val workoutIds = before.workoutItems(active).map { it.id }
        val sql = database.openHelper.writableDatabase
        // In each three-cycle, moving position 2 to 0 succeeds before position 0 to 1 fails.
        // A partially applied order would therefore be visible without the owner transaction.
        for (table in listOf("routine_exercises", "workout_exercises")) {
            sql.execSQL("CREATE TRIGGER reject_reorder BEFORE UPDATE OF position ON $table " +
                "WHEN OLD.position = 0 AND NEW.position = 1 " +
                "BEGIN SELECT RAISE(ABORT, 'injected position failure'); END")
            try {
                try {
                    if (table == "routine_exercises") {
                        repo.reorderRoutineExercises(routine, listOf(routineIds[2], routineIds[0], routineIds[1]))
                    } else {
                        repo.reorderWorkoutExercises(active, listOf(workoutIds[2], workoutIds[0], workoutIds[1]))
                    }
                    fail("The injected write failure must reach the caller")
                } catch (_: SQLiteException) { }
                assertEquals(before, repo.snapshot())
            } finally {
                sql.execSQL("DROP TRIGGER reject_reorder")
            }
        }
        reopen()
        assertEquals(before.routineExercises, repo.snapshot().routineExercises)
        assertEquals(before.workoutExercises, repo.snapshot().workoutExercises)
        assertEquals(before.sets, repo.snapshot().sets)
    }

    @Test fun validEmptyAndUnchangedOrdersAreSafeAndLegacyMoveCommandsKeepTheirIdentities() = runBlocking {
        val emptyRoutine = repo.createRoutine("Empty")
        val emptyWorkout = repo.startEmpty(now = 1000)
        val empty = repo.snapshot()
        repo.reorderRoutineExercises(emptyRoutine, emptyList())
        repo.reorderWorkoutExercises(emptyWorkout, emptyList())
        assertEquals(empty, repo.snapshot())
        repo.discardWorkout(emptyWorkout)

        val routine = plan()
        val routineIds = repo.snapshot().routineItems(routine).map { it.id }
        repo.moveRoutineExercise(routineIds.last(), -1)
        val expectedPlans = listOf(routineIds[0], routineIds[2], routineIds[1])
        assertEquals(expectedPlans, repo.snapshot().routineItems(routine).map { it.id })
        val active = repo.startRoutine(routine, now = 2000)
        val exerciseIds = repo.snapshot().workoutItems(active).map { it.id }
        repo.moveWorkoutExercise(exerciseIds.last(), -1)
        val expectedEntries = listOf(exerciseIds[0], exerciseIds[2], exerciseIds[1])
        val beforeNoOp = repo.snapshot()
        repo.reorderRoutineExercises(routine, expectedPlans)
        repo.reorderWorkoutExercises(active, expectedEntries)
        assertEquals(beforeNoOp, repo.snapshot())
        assertEquals(expectedEntries, repo.snapshot().workoutItems(active).map { it.id })
    }
}
