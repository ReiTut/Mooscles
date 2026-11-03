package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repository's refusals keep their messages and gain a [DataError], on the real generated Room code.
 * Each refusal also leaves the database as it was, as an untyped `require` did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutRepositoryErrorTypesTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    private suspend fun assertRefused(error: DataError, message: String, call: suspend () -> Unit): DataRuleException {
        val before = repo.snapshot()
        val failure = try {
            call()
            null
        } catch (e: DataRuleException) {
            e
        }
        assertNotNull("Expected \"$message\" to be refused", failure)
        assertEquals(message, failure!!.message)
        assertEquals(message, error, failure.error)
        assertTrue(failure is IllegalArgumentException)
        assertEquals("A refused change must write nothing: $message", before, repo.snapshot())
        return failure
    }

    private suspend fun catalogExercise(index: Int = 0): Exercise =
        repo.snapshot().exercises.filter { !it.custom }.sortedBy { it.id }[index]

    private suspend fun activeEntry(): Pair<String, String> {
        val workoutId = repo.startEmpty()
        return workoutId to repo.addWorkoutExercise(workoutId, catalogExercise().id)
    }

    @Test fun missingSetUpdateIsSetNotFoundWithTheSameMessage() = runBlocking {
        val (_, entryId) = activeEntry()
        assertRefused(DataError.SET_NOT_FOUND, "Set not found.") {
            repo.updateSet(WorkoutSet(id = "missing-set", workoutExerciseId = entryId, reps = 8, weightKg = 60.0))
        }
        Unit
    }

    @Test fun finishingWithoutACompletedSetIsNoCompletedSet() = runBlocking {
        val (workoutId, _) = activeEntry()
        assertRefused(DataError.NO_COMPLETED_SET, "Complete at least one set before finishing.") { repo.finishWorkout(workoutId) }
        Unit
    }

    @Test fun theHundredAndFirstSetIsSetLimit() = runBlocking {
        val (_, entryId) = activeEntry()
        while (repo.snapshot().sets.count { it.workoutExerciseId == entryId } < 100) repo.addSet(entryId)
        assertRefused(DataError.SET_LIMIT, "A maximum of 100 sets is supported per exercise.") { repo.addSet(entryId) }
        Unit
    }

    @Test fun discardingAFinishedWorkoutIsWorkoutNotActive() = runBlocking {
        val workoutId = finishedWorkout()
        assertRefused(DataError.WORKOUT_NOT_ACTIVE, "Only active workouts can be discarded.") { repo.discardWorkout(workoutId) }
        Unit
    }

    /** One call per distinct refusal in the repository, with the type the phase 3 table gives it. */
    @Test fun everyRepositoryRuleKeepsItsMessageAndGainsItsType() = runBlocking {
        finishedWorkout()
        val bench = catalogExercise(0)
        val other = catalogExercise(1)
        val routineId = repo.createRoutine("Push")
        val otherRoutineId = repo.createRoutine("Pull")
        val planId = repo.addRoutineExercise(routineId, bench.id)
        val plan = repo.routineExercise(planId)!!
        val (workoutId, entryId) = activeEntry()
        val otherEntryId = repo.addWorkoutExercise(workoutId, other.id)
        val entry = repo.workoutExercise(entryId)!!
        val set = repo.workoutSets(workoutId).first { it.workoutExerciseId == entryId }

        assertRefused(DataError.EXERCISE_NOT_FOUND, "Exercise not found.") { repo.addRoutineExercise(routineId, "missing") }
        assertRefused(DataError.ROUTINE_NOT_FOUND, "Routine exercise not found.") { repo.addRoutineSet("missing") }
        assertRefused(DataError.FOLDER_NOT_FOUND, "Folder not found.") { repo.renameRoutineFolder("missing", "Folder") }
        assertRefused(DataError.FOLDER_NOT_FOUND, "Folder not found.") { repo.createRoutine("Legs", folderId = "missing") }
        assertRefused(DataError.ROUTINE_NOT_FOUND, "Routine not found.") { repo.renameRoutine("missing", "Legs") }
        assertRefused(DataError.INVALID_VALUE, "Invalid routine order.") { repo.reorderRoutines(listOf(routineId)) }
        assertRefused(DataError.INVALID_VALUE, "Cannot move an exercise to another routine.") {
            repo.updateRoutineExercise(plan.copy(routineId = otherRoutineId))
        }
        assertRefused(DataError.INVALID_VALUE, "Use replacement to change exercise identity.") {
            repo.updateRoutineExercise(plan.copy(exerciseId = other.id))
        }
        assertRefused(DataError.ROUTINE_NOT_FOUND, "Routine set not found.") { repo.removeRoutineSet("missing") }
        assertRefused(DataError.INVALID_VALUE, "Invalid exercise order.") { repo.reorderRoutineExercises(routineId, emptyList()) }
        assertRefused(DataError.INVALID_VALUE, "Only custom exercises can be edited.") { repo.saveExercise(bench.copy(name = "Renamed")) }
        assertRefused(DataError.WORKOUT_NOT_FOUND, "Workout not found.") { repo.renameWorkout("missing", "Workout") }
        assertRefused(DataError.WORKOUT_NOT_FOUND, "Workout not found.") { repo.finishWorkout("missing") }
        assertRefused(DataError.WORKOUT_EXERCISE_NOT_FOUND, "Workout exercise not found.") { repo.addSet("missing") }
        assertRefused(DataError.INVALID_VALUE, "Exercise identity cannot change.") {
            repo.updateWorkoutExercise(entry.copy(exerciseId = other.id))
        }
        assertRefused(DataError.INVALID_VALUE, "Invalid exercise order.") { repo.reorderWorkoutExercises(workoutId, emptyList()) }
        assertRefused(DataError.SET_NOT_FOUND, "Set not found.") { repo.setCompleted("missing", true) }
        assertRefused(DataError.INVALID_VALUE, "Cannot move a set to another exercise.") {
            repo.updateSet(set.copy(workoutExerciseId = otherEntryId))
        }
        // EditRules reached through the repository keep their own types.
        assertRefused(DataError.INVALID_VALUE, "Enter at least 1 rep before completing a set.") {
            repo.updateSet(set.copy(reps = 0, completed = true))
        }

        // The last planned set cannot go: trim the plan to one set, then try to remove it.
        repo.updateRoutineExercise(plan.copy(plannedSets = 1))
        val lastSet = repo.snapshot().routineSets.single { it.routineExerciseId == planId }
        assertRefused(DataError.INVALID_VALUE, "Keep at least one planned set.") { repo.removeRoutineSet(lastSet.id) }
        Unit
    }

    private suspend fun finishedWorkout(): String {
        val (workoutId, entryId) = activeEntry()
        val set = repo.workoutSets(workoutId).first { it.workoutExerciseId == entryId }
        repo.updateSet(set.copy(reps = 5, weightKg = 80.0, completed = true))
        assertTrue(repo.finishWorkout(workoutId))
        return workoutId
    }
}
