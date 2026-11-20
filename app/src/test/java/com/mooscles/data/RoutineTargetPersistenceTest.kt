package com.mooscles.data

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

    private suspend fun plan(exercise: String, sets: Int = 2): RoutineExercise {
        val routine = repo.createRoutine("Routine ${newId()}")
        val entryId = repo.addRoutineExercise(routine, exercise)
        val entry = repo.snapshot().routineItems(routine).single()
        repo.updateRoutineExercise(entry.copy(plannedSets = sets, restSeconds = 135))
        return repo.snapshot().routineItems(routine).single { it.id == entryId }
    }

    private suspend fun start(plan: RoutineExercise) = repo.startRoutine(plan.routineId, now = clock.also { clock += 1000 })

    private suspend fun workoutSets(workout: String) = repo.snapshot().let { data ->
        data.exerciseSets(data.workoutItems(workout).first().id)
    }

    private suspend fun complete(set: WorkoutSet, weight: Double, reps: Int) =
        repo.updateSet(set.copy(weightKg = weight, reps = reps, completed = true))

    private suspend fun finish(plan: RoutineExercise, vararg performances: Pair<Double, Int>): String {
        val workout = start(plan)
        val sets = workoutSets(workout)
        performances.forEachIndexed { index, (weight, reps) -> complete(sets[index], weight, reps) }
        assertTrue(repo.finishWorkout(workout, now = clock))
        return workout
    }

    private suspend fun targets(plan: RoutineExercise) =
        repo.snapshot().plannedSets(plan.id).map { it.targetWeightKg to it.targetReps }

    @Test fun heavierCompletedSetReplacesOnlyItsOwnTargetsWithoutChangingStructure() = runBlocking {
        val exercise = movement()
        val first = plan(exercise)
        val otherRoutine = plan(exercise, sets = 4)
        finish(first, 20.0 to 7, 18.0 to 10)
        finish(otherRoutine, 17.5 to 8)
        val otherTargets = targets(otherRoutine)
        val otherExercise = repo.addRoutineExercise(first.routineId, movement())
        val order = repo.snapshot().routineItems(first.routineId).map { it.id }
        val workout = finish(first, 22.0 to 6)

        assertEquals(listOf(22.0 to 6, 18.0 to 10), targets(first))
        assertEquals(otherTargets, targets(otherRoutine))
        assertEquals(order, repo.snapshot().routineItems(first.routineId).map { it.id })
        assertEquals(0, repo.snapshot().routineExercises.first { it.id == otherExercise }.targetReps)
        assertEquals(135, repo.snapshot().routineItems(first.routineId).first().restSeconds)
        assertEquals(22.0, workoutSets(workout).first().weightKg, 0.0)
        assertFalse(repo.finishWorkout(workout))
    }

    @Test fun winningWeightAndRepsAreOneRealSetIncludingRepetitionImprovementAtTopWeight() = runBlocking {
        val plan = plan(movement())
        finish(plan, 20.0 to 25, 18.0 to 30)
        finish(plan, 22.0 to 6, 18.0 to 40)
        assertEquals(listOf(22.0 to 6, 18.0 to 40), targets(plan))
        finish(plan, 22.0 to 9, 24.0 to 5)
        assertEquals(listOf(22.0 to 9, 24.0 to 5), targets(plan))
        finish(plan, 20.0 to 50, 22.0 to 20)
        assertEquals(listOf(22.0 to 9, 24.0 to 5), targets(plan))
    }

    @Test fun bodyweightRecordsUpdateEachSetWithNullTargetAndZeroBlankWorkoutLoads() = runBlocking {
        val plan = plan(movement(LoadConvention.BODYWEIGHT))
        finish(plan, 0.0 to 10, 0.0 to 7)
        finish(plan, 0.0 to 12)
        assertEquals(listOf(null to 12, null to 7), targets(plan))
        val pending = start(plan)
        val next = workoutSets(pending)
        assertEquals(listOf(12, 7), next.map { it.reps })
        assertTrue(next.all { it.weightKg == 0.0 && !it.completed })

        repo.discardWorkout(pending)
        finish(plan, 5.25 to 9)
        assertEquals(listOf(5.25 to 9, null to 7), targets(plan))
        assertEquals(listOf(5.25 to 9, 0.0 to 7), workoutSets(start(plan)).map { it.weightKg to it.reps })
    }

    @Test fun quickWorkoutSetsCopyPreviousAndFallBackToTheWeightedBodyweightRecord() = runBlocking {
        val exercise = movement(LoadConvention.BODYWEIGHT)
        val initial = repo.startEmpty(now = 1_000)
        repo.addWorkoutExercise(initial, exercise)
        val original = workoutSets(initial)
        complete(original[0], 5.0, 12)
        complete(original[1], 0.0, 12)
        repo.finishWorkout(initial, now = 2_000)

        val improvement = repo.startEmpty(now = 3_000)
        repo.addWorkoutExercise(improvement, exercise)
        val next = workoutSets(improvement)
        assertEquals(listOf(5.0 to 12, 0.0 to 12), next.map { it.weightKg to it.reps })
        assertTrue(next.all { !it.completed && it.sourceRoutineSetId == null })
        complete(next[0], 5.25, 8)
        complete(next[1], 0.0, 20)
        repo.finishWorkout(improvement, now = 4_000)

        val lighter = repo.startEmpty(now = 5_000)
        repo.addWorkoutExercise(lighter, exercise)
        val lower = workoutSets(lighter)
        assertEquals(listOf(5.25 to 8, 0.0 to 20), lower.map { it.weightKg to it.reps })
        complete(lower[0], 5.0, 20)
        repo.finishWorkout(lighter, now = 6_000)

        val future = repo.startEmpty(now = 7_000)
        // One Previous set (5 × 20): the second set has none and starts at the weighted record.
        repo.addWorkoutExercise(future, exercise)
        assertEquals(listOf(5.0 to 20, 5.25 to 8), workoutSets(future).map { it.weightKg to it.reps })
        complete(workoutSets(future).first(), 5.25, 9)
        repo.finishWorkout(future, now = 8_000)
        val sameWeight = repo.startEmpty(now = 9_000)
        repo.addWorkoutExercise(sameWeight, exercise)
        assertEquals(listOf(5.25 to 9, 5.25 to 9), workoutSets(sameWeight).map { it.weightKg to it.reps })
        assertEquals(listOf(5.0 to 12, 0.0 to 12), workoutSets(initial).map { it.weightKg to it.reps })
    }

    @Test fun quickWorkoutTargetsUseOnlyEligibleSavedSetsForTheSameMovementAndLoadConvention() = runBlocking {
        val exercise = movement()
        val different = movement(LoadConvention.PER_HAND)
        val old = repo.startEmpty()
        repo.addWorkoutExercise(old, exercise)
        complete(workoutSets(old).first(), 80.0, 4)
        repo.finishWorkout(old)
        repo.saveExercise(repo.exercise(exercise)!!.copy(convention = LoadConvention.PER_HAND))

        val trained = repo.startEmpty()
        val entry = repo.addWorkoutExercise(trained, exercise)
        val rows = workoutSets(trained)
        val weight = WeightUnits.lbToKg(42.75)
        complete(rows[0], weight, 9)
        repo.updateSet(rows[1].copy(weightKg = 90.0, reps = 12, completed = true, warmup = true))
        val assisted = repo.workoutSet(repo.addSet(entry))!!
        repo.updateSet(assisted.copy(weightKg = 100.0, reps = 12, completed = true, warmup = false, assistance = true))
        val unfinished = repo.workoutSet(repo.addSet(entry))!!
        repo.updateSet(unfinished.copy(weightKg = 110.0, reps = 12, completed = false, assistance = false))
        val otherEntry = repo.addWorkoutExercise(trained, different)
        complete(repo.snapshot().exerciseSets(otherEntry).first(), 120.0, 12)
        repo.finishWorkout(trained)

        val active = repo.startEmpty()
        repo.addWorkoutExercise(active, exercise)
        val first = workoutSets(active)
        assertEquals(listOf(weight to 9, weight to 9), first.map { it.weightKg to it.reps })
        complete(first[0], 150.0, 13)
        val repeated = repo.addWorkoutExercise(active, exercise)
        assertEquals(listOf(weight to 9, weight to 9),
            repo.snapshot().exerciseSets(repeated).map { it.weightKg to it.reps })
        assertEquals(150.0, repo.workoutSet(first[0].id)!!.weightKg, 0.0)
        repo.discardWorkout(active)

        val future = repo.startEmpty()
        repo.addWorkoutExercise(future, exercise)
        assertEquals(listOf(weight to 9, weight to 9), workoutSets(future).map { it.weightKg to it.reps })
    }

    @Test fun activeReplacementUsesBodyweightRepsAndSavedWorkoutEditsStillStartBlank() = runBlocking {
        val bodyweight = movement(LoadConvention.BODYWEIGHT)
        val first = repo.startEmpty()
        repo.addWorkoutExercise(first, bodyweight)
        assertTrue(workoutSets(first).all { it.weightKg == 0.0 && it.reps == 0 })
        complete(workoutSets(first).first(), 0.0, 15)
        repo.finishWorkout(first)

        val active = repo.startEmpty()
        val entry = repo.addWorkoutExercise(active, movement())
        repo.replaceWorkoutExercise(entry, bodyweight)
        val replacement = workoutSets(active)
        assertEquals(listOf(0.0 to 15, 0.0 to 15), replacement.map { it.weightKg to it.reps })
        assertTrue(replacement.none { it.completed })

        val savedEntry = repo.addWorkoutExercise(first, bodyweight)
        assertTrue(repo.snapshot().exerciseSets(savedEntry).all { it.weightKg == 0.0 && it.reps == 0 })
        repo.replaceWorkoutExercise(savedEntry, bodyweight)
        assertTrue(repo.snapshot().exerciseSets(savedEntry).all { it.weightKg == 0.0 && it.reps == 0 })
        assertEquals(15, workoutSets(first).first().reps)
    }

    @Test fun warmupsUnfinishedAssistanceAndDiscardedWorkoutsCannotUpdatePlans() = runBlocking {
        val plan = plan(movement(), sets = 4)
        finish(plan, 20.0 to 7, 18.0 to 10, 16.0 to 12, 14.0 to 10)
        val before = targets(plan)
        val workout = start(plan)
        val sets = workoutSets(workout)
        repo.updateSet(sets[0].copy(weightKg = 100.0, reps = 12, completed = true, warmup = true))
        repo.updateSet(sets[1].copy(weightKg = 110.0, reps = 12, completed = false))
        repo.updateSet(sets[2].copy(weightKg = 120.0, reps = 12, completed = true, assistance = true))
        complete(sets[3], 13.0, 12)
        repo.finishWorkout(workout)
        assertEquals(before, targets(plan))

        val discarded = start(plan)
        complete(workoutSets(discarded).first(), 200.0, 12)
        repo.discardWorkout(discarded)
        repo.initialize()
        assertEquals(before, targets(plan))

        val assisted = plan(movement(LoadConvention.ASSISTANCE))
        finish(assisted, 60.0 to 20)
        assertEquals(listOf(null to 0, null to 0), targets(assisted))
    }

    @Test fun machineCableAndDumbbellVariationIdsNeverShareTargets() = runBlocking {
        val plans = listOf(Equipment.SELECTORIZED_MACHINE, Equipment.CABLE, Equipment.DUMBBELL).mapIndexed { i, equipment ->
            val exercise = repo.createCustomExercise("Press", MuscleGroup.CHEST, equipment, LoadConvention.TOTAL_LOAD)
            plan(exercise).also { finish(it, (20.0 - i) to 7, (15.0 - i) to 12) }
        }
        finish(plans[1], 22.0 to 6)
        assertEquals(listOf(20.0 to 7, 15.0 to 12), targets(plans[0]))
        assertEquals(listOf(22.0 to 6, 14.0 to 12), targets(plans[1]))
        assertEquals(listOf(18.0 to 7, 13.0 to 12), targets(plans[2]))
    }

    @Test fun customTargetsSurviveRepeatedInitializationAndNewerQualifyingRecords() = runBlocking {
        val plan = plan(movement())
        finish(plan, 22.0 to 6, 18.0 to 10)
        val custom = repo.snapshot().plannedSets(plan.id).last()
        repo.updateRoutineSet(custom.id, 5, 17.0)
        repeat(2) { repo.initialize() }
        finish(plan, 20.0 to 40, 22.0 to 6)
        assertEquals(listOf(22.0 to 6, 17.0 to 5), targets(plan))
        finish(plan, 22.0 to 7, 22.0 to 8)
        assertEquals(listOf(22.0 to 7, 17.0 to 5), targets(plan))
        assertFalse(repo.snapshot().plannedSets(plan.id).last().inheritPr)
    }

    @Test fun unlinkedHistoryLeavesSavedTargetsAndWorkoutSnapshotsIntact() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        // Older installations can already have populated Follow PR targets without a set link.
        val initialRows = repo.snapshot().plannedSets(plan.id)
        database.dao().updateRoutineSet(initialRows.first().copy(targetWeightKg = 20.0, targetReps = 7))
        val old = repo.startEmpty()
        repo.addWorkoutExercise(old, exercise)
        val oldSet = workoutSets(old).first()
        assertNull(oldSet.sourceRoutineSetId)
        complete(oldSet, 50.0, 6)
        repo.finishWorkout(old)
        val active = start(plan)
        val before = repo.snapshot()
        repo.initialize()
        val after = repo.snapshot()
        assertEquals(before.workoutItems(old), after.workoutItems(old))
        assertEquals(before.workoutItems(active), after.workoutItems(active))
        assertEquals(before.sets, after.sets)
        assertEquals(listOf(20.0 to 7, null to 0), targets(plan))
    }

    @Test fun routineTargetsWaitForSuccessfulFinalizationAndPreserveWorkingAndWarmupFlags() = runBlocking {
        val plan = plan(movement(), sets = 3)
        finish(plan, 20.0 to 7, 18.0 to 10, 16.0 to 12)
        val warmup = repo.snapshot().plannedSets(plan.id).last()
        repo.setRoutineSetWarmup(warmup.id, true)
        val before = targets(plan)
        val workout = start(plan)
        val sets = workoutSets(workout)
        repo.updateSet(sets[1].copy(weightKg = 22.0, reps = 6))
        val failure = runCatching { repo.finishWorkout(workout) }.exceptionOrNull()
        assertEquals(DataError.NO_COMPLETED_SET, (failure as? DataRuleException)?.error)
        assertEquals(before, targets(plan))
        repo.setCompleted(sets[1].id, true)
        assertEquals(before, targets(plan))
        val recordedSets = workoutSets(workout)
        assertTrue(repo.finishWorkout(workout))
        assertEquals(recordedSets, workoutSets(workout))
        assertEquals(listOf(20.0 to 7, 22.0 to 6, 16.0 to 12), targets(plan))
        val savedWarmup = repo.snapshot().plannedSets(plan.id).last()
        assertTrue(savedWarmup.warmup)
        assertFalse(savedWarmup.inheritPr)
    }

    @Test fun newAndReplacedRowsDoNotBorrowAnotherRowsRecord() = runBlocking {
        val exercise = movement()
        val recorded = plan(exercise)
        finish(recorded, 22.0 to 6)
        val newPlan = plan(exercise)
        assertEquals(listOf(null to 0, null to 0), targets(newPlan))
        val extra = repo.addRoutineSet(recorded.id)
        val rows = repo.snapshot().plannedSets(recorded.id)
        assertEquals(extra, rows.last().id)
        assertEquals(listOf(22.0 to 6, null to 0, null to 0), targets(recorded))
        val other = movement(LoadConvention.BODYWEIGHT)
        finish(plan(other), 0.0 to 12)
        repo.replaceRoutineExercise(recorded.id, other)
        assertEquals(rows.map { it.id }, repo.snapshot().plannedSets(recorded.id).map { it.id })
        assertEquals(listOf(null to 0, null to 0, null to 0), targets(recorded))
    }

    @Test fun canonicalPoundsPerformanceCopiesExactlyWithoutConvertingTwice() = runBlocking {
        val plan = plan(movement(LoadConvention.PER_HAND))
        val first = WeightUnits.lbToKg(50.5)
        val second = WeightUnits.lbToKg(42.75)
        finish(plan, first to 6, second to 11)
        assertEquals(listOf(first to 6, second to 11), targets(plan))
        val sets = workoutSets(start(plan))
        assertEquals(listOf(first to 6, second to 11), sets.map { it.weightKg to it.reps })
        assertTrue(sets.none { it.completed })
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
        fun openDisk() = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).setDriver(AndroidSQLiteDriver()).build()
        var disk = openDisk()
        try {
            repo = WorkoutRepository(disk)
            repo.initialize()
            val plan = plan(movement())
            finish(plan, 22.0 to 6, 18.0 to 12)
            val workout = start(plan)
            val activeSet = workoutSets(workout).first()
            val nextTargets = workoutSets(workout).map { it.weightKg to it.reps }
            assertEquals(listOf(22.0 to 6, 18.0 to 12), nextTargets)
            assertTrue(workoutSets(workout).none { it.completed })
            complete(activeSet, 22.0, 8)
            val manual = repo.snapshot().plannedSets(plan.id).last()
            repo.updateRoutineSet(manual.id, 5, 17.0)
            disk.close()
            disk = openDisk()
            repo = WorkoutRepository(disk)
            repo.initialize()
            assertEquals(listOf(22.0 to 6, 17.0 to 5), targets(plan))
            assertEquals(activeSet.sourceRoutineSetId, workoutSets(workout).first().sourceRoutineSetId)
            assertTrue(repo.finishWorkout(workout))
            disk.close()
            disk = openDisk()
            repo = WorkoutRepository(disk)
            repo.initialize()
            assertEquals(listOf(22.0 to 8, 17.0 to 5), targets(plan))
            assertEquals(targets(plan), workoutSets(start(plan)).map { it.weightKg to it.reps })
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun reorderingRepeatedExercisesAndAddingOrRemovingSetsKeepsTheExactPlannedOrigins() = runBlocking {
        val exercise = movement()
        val first = plan(exercise, sets = 3)
        val repeat = repo.addRoutineExercise(first.routineId, exercise)
        val originals = repo.snapshot().plannedSets(first.id)
        val repeated = repo.snapshot().plannedSets(repeat)
        val workout = start(first)
        val (firstWorkoutEntry, secondWorkoutEntry) = repo.snapshot().workoutItems(workout)
        val workoutRows = repo.snapshot().exerciseSets(firstWorkoutEntry.id)
        val repeatedRows = repo.snapshot().exerciseSets(secondWorkoutEntry.id)
        assertEquals(originals.map { it.id }, workoutRows.map { it.sourceRoutineSetId })
        assertEquals(repeated.map { it.id }, repeatedRows.map { it.sourceRoutineSetId })

        repo.removeRoutineSet(originals[0].id)
        repo.removeRoutineSet(originals[2].id)
        repo.restoreRoutineSet(first.id, 0, originals[2])
        val newPlanId = repo.addRoutineSet(first.id)
        repo.reorderRoutineExercises(first.routineId, listOf(repeat, first.id))
        repo.reorderWorkoutExercises(workout, listOf(secondWorkoutEntry.id, firstWorkoutEntry.id))
        repo.removeSet(workoutRows[1].id)
        val toRestore = repo.workoutSet(workoutRows[2].id)!!
        repo.removeSet(toRestore.id)
        repo.restoreSet(firstWorkoutEntry.id, toRestore.position, toRestore)
        assertEquals(toRestore.sourceRoutineSetId, repo.workoutSet(toRestore.id)?.sourceRoutineSetId)
        val added = repo.workoutSet(repo.addSet(firstWorkoutEntry.id))!!
        assertNull(added.sourceRoutineSetId)

        complete(repo.workoutSet(workoutRows[0].id)!!, 70.0, 5)
        complete(repo.workoutSet(workoutRows[2].id)!!, 42.0, 7)
        complete(added, 100.0, 6)
        complete(repeatedRows.first(), 25.0, 20)
        assertTrue(repo.finishWorkout(workout))
        val rows = repo.snapshot().plannedSets(first.id)
        assertEquals(listOf(originals[2].id, originals[1].id, newPlanId), rows.map { it.id })
        assertEquals(listOf(42.0 to 7, null to 0, null to 0), targets(first))
        assertEquals(listOf(25.0 to 20, null to 0),
            repo.snapshot().plannedSets(repeat).map { it.targetWeightKg to it.targetReps })
    }

    @Test fun aRecordFromADifferentLoadConventionDoesNotUpdateTheSamePlannedRow() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        val original = start(plan)
        val movement = repo.snapshot().exercises.single { it.id == exercise }
        repo.saveExercise(movement.copy(convention = LoadConvention.PER_HAND))
        complete(workoutSets(original).first(), 90.0, 2)
        assertTrue(repo.finishWorkout(original))
        assertEquals(listOf(null to 0, null to 0), targets(plan))
        finish(plan, 15.0 to 7)
        assertEquals(listOf(15.0 to 7, null to 0), targets(plan))
    }

    private suspend fun manualTargets(plan: RoutineExercise, vararg values: Pair<Double?, Int>) =
        repo.snapshot().plannedSets(plan.id).forEachIndexed { index, row ->
            values.getOrNull(index)?.let { (weight, reps) -> repo.updateRoutineSet(row.id, reps, weight) }
        }

    @Test fun manualRoutineSetsStartFromThePreviousSetsExactly() = runBlocking {
        val exercise = movement()
        val plan = plan(exercise)
        manualTargets(plan, 100.0 to 10, 100.0 to 9)
        // An older quick workout holds more reps at 100 kg; Previous is still copied as it was.
        val quick = repo.startEmpty(now = clock.also { clock += 1000 })
        repo.addWorkoutExercise(quick, exercise)
        complete(workoutSets(quick).first(), 100.0, 11)
        assertTrue(repo.finishWorkout(quick, now = clock))
        finish(plan, 120.0 to 7, 100.0 to 9)
        // Manual prescriptions are not rewritten by finishing; only the new workout's values change.
        assertEquals(listOf(100.0 to 10, 100.0 to 9), targets(plan))

        val abandoned = start(plan)
        assertEquals(listOf(120.0 to 7, 100.0 to 9), workoutSets(abandoned).map { it.weightKg to it.reps })
        workoutSets(abandoned).forEach { complete(it, 200.0, 20) }
        repo.discardWorkout(abandoned)

        val next = workoutSets(start(plan))
        assertEquals(listOf(120.0 to 7, 100.0 to 9), next.map { it.weightKg to it.reps })
        assertEquals(repo.snapshot().plannedSets(plan.id).map { it.id }, next.map { it.sourceRoutineSetId })
        assertTrue(next.none { it.completed })
        assertEquals(listOf(100.0 to 10, 100.0 to 9), targets(plan))
    }

    @Test fun setsWithoutPreviousKeepTargetsAndFollowTheRecordSetsCopyPrevious() = runBlocking {
        val plan = plan(movement(), sets = 3)
        val rows = repo.snapshot().plannedSets(plan.id)
        repo.updateRoutineSet(rows[1].id, 5, 25.0)
        repo.updateRoutineSet(rows[2].id, 6, 22.0)
        finish(plan, 30.0 to 5, 27.5 to 6)
        assertEquals(listOf(30.0 to 5, 25.0 to 5, 22.0 to 6), targets(plan))
        val first = start(plan)
        assertEquals(listOf(30.0 to 5, 27.5 to 6, 22.0 to 6), workoutSets(first).map { it.weightKg to it.reps })
        repo.discardWorkout(first)

        // A lighter session keeps the record as the Follow target, but the next start copies Previous.
        finish(plan, 25.0 to 4)
        assertEquals(listOf(30.0 to 5, 25.0 to 5, 22.0 to 6), targets(plan))
        assertEquals(listOf(25.0 to 4, 25.0 to 5, 22.0 to 6), workoutSets(start(plan)).map { it.weightKg to it.reps })
    }

    @Test fun routinesWithoutAnyPreviousSessionStillStartAtTheirTargets() = runBlocking {
        val plan = plan(movement())
        manualTargets(plan, 60.0 to 8, null to 12)
        val other = plan(movement())
        finish(other, 90.0 to 5)
        assertEquals(listOf(60.0 to 8, 0.0 to 12), workoutSets(start(plan)).map { it.weightKg to it.reps })
    }

    @Test fun emptyWorkoutSetsCopyThePreviousColumnIncludingAddedSets() = runBlocking {
        // The reported cable crunch: record 50 × 10, Previous 50 × 10 and 45 × 15.
        val exercise = movement()
        suspend fun quick(start: Long, vararg done: Pair<Double, Int>) {
            val workout = repo.startEmpty(now = start)
            val entry = repo.addWorkoutExercise(workout, exercise)
            while (repo.snapshot().exerciseSets(entry).size < done.size) repo.addSet(entry)
            repo.snapshot().exerciseSets(entry).zip(done).forEach { (set, value) -> complete(set, value.first, value.second) }
            assertTrue(repo.finishWorkout(workout, now = start + 500))
        }
        quick(1_000, 50.0 to 10, 35.0 to 20, 30.0 to 25)
        quick(2_000, 50.0 to 10, 45.0 to 15)

        val active = repo.startEmpty(now = 3_000)
        val entry = repo.addWorkoutExercise(active, exercise)
        assertEquals(listOf(50.0 to 10, 45.0 to 15), workoutSets(active).map { it.weightKg to it.reps })
        // A third set has no Previous set: it keeps the old default (the last load, reps from the suggestion).
        val third = repo.workoutSet(repo.addSet(entry))!!
        assertEquals(45.0 to 0, third.weightKg to third.reps)
        repo.discardWorkout(active)

        quick(4_000, 52.5 to 8, 45.0 to 15, 40.0 to 18)
        val next = repo.startEmpty(now = 5_000)
        val nextEntry = repo.addWorkoutExercise(next, exercise)
        val added = repo.workoutSet(repo.addSet(nextEntry))!!
        assertEquals(listOf(52.5 to 8, 45.0 to 15, 40.0 to 18), workoutSets(next).map { it.weightKg to it.reps })
        assertEquals(40.0 to 18, added.weightKg to added.reps)
        assertTrue(workoutSets(next).none { it.completed })
    }

    @Test fun addedRoutineSetsCopyPreviousAndSavedWorkoutEditsKeepTheOldDefault() = runBlocking {
        val plan = plan(movement())
        val first = start(plan)
        val entry = repo.snapshot().workoutItems(first).first().id
        repo.addSet(entry)
        workoutSets(first).zip(listOf(60.0 to 8, 55.0 to 10, 50.0 to 12)).forEach { (set, value) -> complete(set, value.first, value.second) }
        assertTrue(repo.finishWorkout(first, now = clock))

        val next = start(plan)
        val nextEntry = repo.snapshot().workoutItems(next).first().id
        val added = repo.workoutSet(repo.addSet(nextEntry))!!
        assertEquals(listOf(60.0 to 8, 55.0 to 10, 50.0 to 12), workoutSets(next).map { it.weightKg to it.reps })
        assertEquals(50.0 to 12, added.weightKg to added.reps)
        repo.discardWorkout(next)

        // Editing the saved workout adds a set the old way: the last load and no reps.
        val edited = repo.workoutSet(repo.addSet(entry))!!
        assertEquals(50.0 to 0, edited.weightKg to edited.reps)
    }
}
