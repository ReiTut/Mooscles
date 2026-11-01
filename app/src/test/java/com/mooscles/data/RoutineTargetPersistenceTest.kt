package com.mooscles.data

import androidx.room.execSQL
import androidx.room.useWriterConnection
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
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
class RoutineTargetPersistenceTest {
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

    private suspend fun movement(convention: LoadConvention = LoadConvention.TOTAL_LOAD): String =
        repo.createCustomExercise("Test ${newId()}", MuscleGroup.CHEST,
            if (convention == LoadConvention.BODYWEIGHT) Equipment.BODYWEIGHT else Equipment.BARBELL, convention)

    private suspend fun plan(exercise: String, weight: Double? = 20.0, reps: Int = 7, sets: Int = 3): RoutineExercise {
        val routine = repo.createRoutine("Routine ${newId()}")
        repo.addRoutineExercise(routine, exercise)
        val item = repo.snapshot().routineItems(routine).single()
        repo.updateRoutineExercise(item.copy(plannedSets = sets, restSeconds = 135))
        // Inherited targets originate in actual finished history, including for on-disk repos.
        val convention = repo.snapshot().exercises.first { it.id == exercise }.convention
        if (convention != LoadConvention.ASSISTANCE && ((weight ?: 0.0) > 0.0 || convention == LoadConvention.BODYWEIGHT)) {
            finish(exercise, (weight ?: 0.0) to reps)
        }
        return repo.snapshot().routineItems(routine).single()
    }

    private suspend fun finish(exercise: String, vararg performances: Pair<Double, Int>): String {
        val workout = repo.startEmpty(now = clock.also { clock += 1000 })
        val entry = repo.addWorkoutExercise(workout, exercise)
        while (repo.snapshot().exerciseSets(entry).size < performances.size) repo.addSet(entry)
        performances.forEachIndexed { index, pair ->
            val set = repo.snapshot().exerciseSets(entry)[index]
            repo.updateSet(set.copy(weightKg = pair.first, reps = pair.second, completed = true))
        }
        repo.finishWorkout(workout, now = clock)
        return workout
    }

    private suspend fun saved(plan: RoutineExercise) = repo.snapshot().routineItems(plan.routineId).single { it.id == plan.id }

    @Test fun heavierCompletedSetReplacesBothTargetsInEveryRoutineWithoutChangingStructure() = runBlocking {
        val exercise = movement()
        val first = plan(exercise)
        val second = plan(exercise, sets = 4)
        val other = repo.addRoutineExercise(first.routineId, movement())
        val order = repo.snapshot().routineItems(first.routineId).map { it.id }
        val workout = finish(exercise, 22.0 to 6)
        for (original in listOf(first, second)) {
            assertEquals(original.copy(targetWeightKg = 22.0, targetReps = 6), saved(original))
        }
        assertEquals(order, repo.snapshot().routineItems(first.routineId).map { it.id })
        assertEquals(0, repo.snapshot().routineExercises.first { it.id == other }.targetReps)
        val completed = repo.snapshot()
        assertEquals(22.0, completed.exerciseSets(completed.workoutItems(workout).single().id).first().weightKg, 0.0)
        assertFalse(repo.finishWorkout(workout))
    }

    @Test fun winningWeightAndRepsAreOneRealSetIncludingRepetitionImprovementAtTopWeight() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        finish(exercise, 20.0 to 25, 22.0 to 6, 22.0 to 8)
        assertEquals(22.0, saved(plan).targetWeightKg!!, 0.0)
        assertEquals(8, saved(plan).targetReps)
        finish(exercise, 22.0 to 9, 18.0 to 30)
        assertEquals(22.0, saved(plan).targetWeightKg!!, 0.0)
        assertEquals(9, saved(plan).targetReps)
        finish(exercise, 20.0 to 50)
        assertEquals(22.0, saved(plan).targetWeightKg!!, 0.0)
        assertEquals(9, saved(plan).targetReps)
    }

    @Test fun bodyweightRecordUpdatesRepsWithNullTargetAndZeroBlankWorkoutLoad() = runBlocking {
        val exercise = movement(LoadConvention.BODYWEIGHT)
        val plan = plan(exercise, weight = null, reps = 7)
        finish(exercise, 0.0 to 10)
        assertNull(saved(plan).targetWeightKg)
        assertEquals(10, saved(plan).targetReps)
        finish(exercise, 0.0 to 12)
        val next = repo.startRoutine(plan.routineId)
        val sets = repo.snapshot().exerciseSets(repo.snapshot().workoutItems(next).single().id)
        assertTrue(sets.all { it.weightKg == 0.0 && it.reps == 12 && !it.completed })
    }

    @Test fun warmupsUnfinishedAssistanceAndDiscardedWorkoutsCannotUpdatePlans() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        val workout = repo.startEmpty()
        val entry = repo.addWorkoutExercise(workout, exercise)
        repo.addSet(entry)
        val sets = repo.snapshot().exerciseSets(entry)
        repo.updateSet(sets[0].copy(weightKg = 100.0, reps = 12, completed = true, warmup = true))
        repo.updateSet(sets[1].copy(weightKg = 110.0, reps = 12, completed = false))
        repo.updateSet(sets[2].copy(weightKg = 120.0, reps = 12, completed = true, assistance = true))
        repo.finishWorkout(workout)
        assertEquals(plan, saved(plan))
        val discarded = repo.startEmpty()
        val discardedEntry = repo.addWorkoutExercise(discarded, exercise)
        repo.updateSet(repo.snapshot().exerciseSets(discardedEntry).first().copy(weightKg = 200.0, reps = 12, completed = true))
        repo.discardWorkout(discarded)
        repo.initialize()
        assertEquals(plan, saved(plan))
        val assisted = movement(LoadConvention.ASSISTANCE)
        val assistedPlan = plan(assisted)
        finish(assisted, 60.0 to 20)
        assertEquals(assistedPlan, saved(assistedPlan))
    }

    @Test fun machineCableAndDumbbellVariationIdsNeverShareTargets() = runBlocking {
        val ids = listOf(Equipment.SELECTORIZED_MACHINE, Equipment.CABLE, Equipment.DUMBBELL).map {
            repo.createCustomExercise("Press", MuscleGroup.CHEST, it, LoadConvention.TOTAL_LOAD)
        }
        val plans = ids.map { plan(it) }
        finish(ids[1], 22.0 to 6)
        assertEquals(plans[0], saved(plans[0]))
        assertEquals(plans[2], saved(plans[2]))
        assertEquals(plans[1].copy(targetWeightKg = 22.0, targetReps = 6), saved(plans[1]))
    }

    @Test fun customTargetsSurviveRepeatedInitializationAndNewerQualifyingRecords() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        finish(exercise, 22.0 to 6)
        val manual = saved(plan).copy(targetWeightKg = 21.0, targetReps = 5)
        repo.updateRoutineExercise(manual)
        repeat(2) { repo.initialize() }
        finish(exercise, 20.0 to 40, 22.0 to 6)
        assertEquals(manual, saved(plan))
        finish(exercise, 22.0 to 7)
        assertEquals(manual, saved(plan))
        assertTrue(repo.snapshot().plannedSets(plan.id).none { it.inheritPr })
    }

    @Test fun activeAndCompletedSnapshotsRemainUnchangedDuringLegacySynchronization() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        val old = finish(exercise, 22.0 to 6)
        // Remove only the feature's checkpoint to model a pre-feature installation.
        database.useWriterConnection { it.execSQL("DELETE FROM routine_target_checkpoints") }
        repo.updateRoutineExercise(saved(plan).copy(targetWeightKg = 20.0, targetReps = 7))
        val active = repo.startRoutine(plan.routineId)
        val before = repo.snapshot()
        repo.initialize()
        val after = repo.snapshot()
        assertEquals(before.workoutItems(old), after.workoutItems(old))
        assertEquals(before.workoutItems(active), after.workoutItems(active))
        assertEquals(before.sets, after.sets)
        assertEquals(20.0, saved(plan).targetWeightKg!!, 0.0)
        assertEquals(7, saved(plan).targetReps)
    }

    @Test fun routineTargetsWaitForFinalizationAndPreserveWorkingAndWarmupSetFlags() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        val workout = repo.startRoutine(plan.routineId)
        val entry = repo.snapshot().workoutItems(workout).single()
        val sets = repo.snapshot().exerciseSets(entry.id)
        repo.updateSet(sets[0].copy(weightKg = 10.0, reps = 15, warmup = true, completed = true))
        repo.updateSet(sets[1].copy(weightKg = 22.0, reps = 6, completed = true))
        assertEquals(plan, saved(plan))
        val before = repo.snapshot().exerciseSets(entry.id)
        repo.finishWorkout(workout)
        assertEquals(before, repo.snapshot().exerciseSets(entry.id))
        assertEquals(plan.copy(targetWeightKg = 22.0, targetReps = 6), saved(plan))
    }

    @Test fun newlyReferencedAndReplacedVariationsStartFromTheirExistingRecords() = runBlocking {
        val exercise = movement()
        finish(exercise, 22.0 to 6)
        val routine = repo.createRoutine("New")
        repo.addRoutineExercise(routine, exercise)
        val first = repo.snapshot().routineItems(routine).single()
        assertEquals(22.0, first.targetWeightKg!!, 0.0)
        assertEquals(6, first.targetReps)
        val other = movement(LoadConvention.BODYWEIGHT)
        finish(other, 0.0 to 12)
        repo.replaceRoutineExercise(first.id, other)
        val replacement = repo.snapshot().routineItems(routine).single()
        assertNull(replacement.targetWeightKg)
        assertEquals(12, replacement.targetReps)
        assertEquals(first.plannedSets, replacement.plannedSets)
    }

    @Test fun canonicalPoundsPerformanceCopiesExactlyWithoutConvertingTwice() = runBlocking {
        val exercise = movement(LoadConvention.PER_HAND)
        val plan = plan(exercise)
        val kg = WeightUnits.lbToKg(50.5)
        finish(exercise, kg to 6)
        assertEquals(kg, saved(plan).targetWeightKg!!, 0.0)
        val workout = repo.startRoutine(plan.routineId)
        assertTrue(repo.snapshot().exerciseSets(repo.snapshot().workoutItems(workout).single().id).all {
            it.weightKg == kg && it.reps == 6 && !it.completed
        })
    }

    @Test fun freshRestDefaultsAre120WhileExplicit90And135RemainSaved() = runBlocking {
        val exercise = movement()
        val routine = repo.createRoutine("Default rest")
        repo.addRoutineExercise(routine, exercise)
        val first = repo.startRoutine(routine)
        assertEquals(120, repo.snapshot().workoutItems(first).single().restSeconds)
        val added = repo.addWorkoutExercise(first, movement())
        assertEquals(120, repo.snapshot().workoutExercises.first { it.id == added }.restSeconds)
        repo.discardWorkout(first)
        val plan = repo.snapshot().routineItems(routine).single()
        for (seconds in listOf(90, 135)) {
            repo.updateRoutineExercise(plan.copy(restSeconds = seconds))
            repo.initialize()
            val id = repo.startRoutine(routine)
            assertEquals(seconds, repo.snapshot().workoutItems(id).single().restSeconds)
            repo.discardWorkout(id)
        }
    }

    @Test fun onDiskReopenPreservesTargetsManualOverridesAndFutureWorkoutInitialization() = runBlocking {
        val name = "rt-${newId().take(8)}.db"
        var disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).setDriver(AndroidSQLiteDriver()).build()
        try {
            val memoryRepo = repo
            repo = WorkoutRepository(disk)
            repo.initialize()
            val exercise = movement()
            val plan = plan(exercise)
            finish(exercise, 22.0 to 6)
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).setDriver(AndroidSQLiteDriver()).build()
            repo = WorkoutRepository(disk)
            repo.initialize()
            assertEquals(22.0, saved(plan).targetWeightKg!!, 0.0)
            assertEquals(6, saved(plan).targetReps)
            val next = repo.startRoutine(plan.routineId)
            assertTrue(repo.snapshot().exerciseSets(repo.snapshot().workoutItems(next).single().id).all {
                it.weightKg == 22.0 && it.reps == 6 && !it.completed
            })
            repo.discardWorkout(next)
            val manual = saved(plan).copy(targetWeightKg = 21.0, targetReps = 4)
            repo.updateRoutineExercise(manual)
            disk.close()
            disk = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).setDriver(AndroidSQLiteDriver()).build()
            repo = WorkoutRepository(disk)
            repo.initialize()
            assertEquals(manual, saved(plan))
            finish(exercise, 24.0 to 3)
            assertEquals(manual, saved(plan))
            repo = memoryRepo
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
