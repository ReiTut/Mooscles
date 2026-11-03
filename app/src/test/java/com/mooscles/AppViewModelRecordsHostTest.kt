package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.RecordKind
import com.mooscles.ui.TargetSetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Verify the actual Room -> ViewModel presentation mapping, including duplicate cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelRecordsHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun savedFirstBenchAndBodyweightPullupRecordsCountAsTwoExercisesAfterReopening() {
        val workoutId = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val bench = repo.createCustomExercise("Barbell bench press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val pullup = repo.createCustomExercise("Wide-grip pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, LoadConvention.BODYWEIGHT)
            val id = repo.startEmpty(now = 1_000)
            val benchEntry = repo.addWorkoutExercise(id, bench)
            repo.addSet(benchEntry)
            val pullupEntry = repo.addWorkoutExercise(id, pullup)
            repo.snapshot().exerciseSets(benchEntry).forEachIndexed { index, set ->
                repo.updateSet(set.copy(weightKg = 20.0, reps = if (index < 2) 10 else 8, completed = true))
            }
            repo.snapshot().exerciseSets(pullupEntry).forEach { set ->
                repo.updateSet(set.copy(weightKg = 0.0, reps = 10, completed = true))
            }
            repo.finishWorkout(id, now = 61_000)
            id
        }
        repeat(2) {
            val model = newModel()
            awaitUi(model) { !it.loading && it.history.any { workout -> workout.id == workoutId } }
            val saved = model.ui.value.history.single { it.id == workoutId }
            assertEquals(2, saved.prExerciseCount)
            assertEquals(0, saved.beatenPrExerciseCount)
            assertEquals(3, saved.records.size)
            assertTrue(saved.records.all { it.firstRecord })
            assertEquals(5, saved.completedSets)
            assertEquals(560.0, saved.volumeKg, 0.0)
            model.viewModelScope.cancel()
        }
    }

    @Test fun savedDualRecordsMapToEligibleCardsAndRecalculateAfterHistoryCorrection() {
        val seeded = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val bench = repo.createCustomExercise("Record bench", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val pullup = repo.createCustomExercise("Record pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, LoadConvention.BODYWEIGHT)
            val earlier = repo.startEmpty(now = 1_000)
            val earlierBench = repo.addWorkoutExercise(earlier, bench)
            val earlierPullup = repo.addWorkoutExercise(earlier, pullup)
            val oldBenchSet = repo.snapshot().exerciseSets(earlierBench).first()
            val oldPullupSet = repo.snapshot().exerciseSets(earlierPullup).first()
            repo.updateSet(oldBenchSet.copy(weightKg = 100.0, reps = 10, completed = true))
            repo.updateSet(oldPullupSet.copy(weightKg = 0.0, reps = 8, completed = true))
            repo.finishWorkout(earlier, now = 2_000)

            val current = repo.startEmpty(now = 3_000)
            val currentBench = repo.addWorkoutExercise(current, bench)
            val currentPullup = repo.addWorkoutExercise(current, pullup)
            val incompleteBench = repo.addWorkoutExercise(current, bench)
            val newBenchSet = repo.snapshot().exerciseSets(currentBench).first()
            val newPullupSet = repo.snapshot().exerciseSets(currentPullup).first()
            val incompleteSet = repo.snapshot().exerciseSets(incompleteBench).first()
            repo.updateSet(newBenchSet.copy(weightKg = 105.0, reps = 3, completed = true))
            repo.updateSet(newPullupSet.copy(weightKg = 0.0, reps = 12, completed = true))
            repo.updateSet(incompleteSet.copy(weightKg = 1_000.0, reps = 1))
            repo.finishWorkout(current, now = 4_000)
            Seed(current, currentBench, currentPullup, incompleteBench, oldBenchSet.id, oldPullupSet.id)
        }

        val model = newModel()
        awaitUi(model) { !it.loading && it.history.any { workout -> workout.id == seeded.workout && workout.prExerciseCount == 2 } }
        val summary = model.ui.value.history.first { it.id == seeded.workout }
        assertEquals(2, summary.prExerciseCount)
        val beaten = summary.records.filterNot { it.firstRecord }
        assertEquals(2, beaten.size)
        assertEquals(100.0, beaten.single { it.kind == RecordKind.WEIGHT }.previousKg!!, 0.0)
        assertEquals(105.0, beaten.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
        assertEquals(8, beaten.single { it.kind == RecordKind.REPS }.previousReps)
        assertEquals(12, beaten.single { it.kind == RecordKind.REPS }.reps)
        assertTrue(summary.exercises.first { it.id == seeded.benchEntry }.records.any { it.kind == RecordKind.WEIGHT })
        assertTrue(summary.exercises.first { it.id == seeded.pullupEntry }.records.any { it.kind == RecordKind.REPS && !it.firstRecord })
        assertTrue(summary.exercises.first { it.id == seeded.incompleteEntry }.records.isEmpty())
        assertEquals(315.0, summary.volumeKg, 0.0)

        runBlocking(Dispatchers.IO) {
            val repo = app.repository
            val data = repo.snapshot()
            repo.updateSet(data.sets.first { it.id == seeded.oldBenchSet }.copy(weightKg = 110.0))
            repo.updateSet(data.sets.first { it.id == seeded.oldPullupSet }.copy(reps = 15))
        }
        awaitUi(model) { it.history.firstOrNull { workout -> workout.id == seeded.workout }?.beatenPrExerciseCount == 0 }
        val corrected = model.ui.value.history.first { it.id == seeded.workout }
        assertTrue(corrected.records.none { !it.firstRecord })
        assertEquals(1, corrected.prExerciseCount) // The initial reps record at 105 kg still qualifies.

        // A fresh presentation instance reads the persisted correction rather than cached badges.
        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) { !it.loading && it.history.any { workout -> workout.id == seeded.workout } }
        val reopened = restored.ui.value.history.first { it.id == seeded.workout }
        assertEquals(0, reopened.beatenPrExerciseCount)
        assertEquals(1, reopened.prExerciseCount)
        assertTrue(reopened.exercises.first { it.id == seeded.incompleteEntry }.records.isEmpty())
    }

    @Test fun completedPrReachesTheSavedRoutinePresentationAndFutureWorkoutAfterReopening() {
        val routineId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.preferences.pounds(false)
            val repo = app.repository
            repo.initialize()
            val bench = repo.createCustomExercise("Target bench", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val prior = repo.startEmpty(now = 1_000)
            val priorEntry = repo.addWorkoutExercise(prior, bench)
            val priorSet = repo.snapshot().exerciseSets(priorEntry).first()
            repo.updateSet(priorSet.copy(weightKg = 20.0, reps = 7, completed = true))
            repo.finishWorkout(prior, now = 2_000)
            val routine = repo.createRoutine("PR target routine")
            val entry = repo.addRoutineExercise(routine, bench)
            repo.addRoutineSet(entry)
            val rows = repo.snapshot().plannedSets(entry)
            repo.updateRoutineSet(rows[1].id, reps = 10, weightKg = 18.5)
            repo.updateRoutineExerciseRest(entry, 105)
            repo.startRoutine(routine)
            routine
        }

        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null && it.routines.any { routine -> routine.id == routineId } }
        val active = model.ui.value.active!!
        val setId = active.exercises.single().sets.first().id
        model.updateSet(setId, reps = 6, weightKg = 22.0, completed = true, warmup = false, assistance = false)
        awaitUi(model) { it.active?.exercises?.single()?.sets?.first()?.completed == true }
        val duringSession = model.ui.value.routines.single { it.id == routineId }.exercises.single()
        assertEquals(20.0, duringSession.weightKg!!, 0.0)
        assertEquals(7, duringSession.reps)
        assertEquals(listOf(20.0, 18.5, 20.0), duringSession.setTargets.map { it.weightKg })
        assertEquals(listOf(true, false, true), duringSession.setTargets.map { it.inherited })

        model.finishWorkout()
        awaitUi(model) {
            val plan = it.routines.singleOrNull { routine -> routine.id == routineId }?.exercises?.single()
            it.active == null && plan?.weightKg == 22.0 && plan.reps == 6 && it.history.any { workout -> workout.id == active.id }
        }
        val savedPlan = model.ui.value.routines.single { it.id == routineId }.exercises.single()
        assertEquals(3, savedPlan.sets)
        assertEquals(105, savedPlan.restSeconds)
        assertEquals(listOf(22.0, 18.5, 22.0), savedPlan.setTargets.map { it.weightKg })
        assertEquals(listOf(6, 10, 6), savedPlan.setTargets.map { it.reps })
        assertEquals(listOf(true, false, true), savedPlan.setTargets.map { it.inherited })
        val savedHistory = model.ui.value.history.single { it.id == active.id }
        assertEquals(22.0, savedHistory.exercises.single().sets.first().weightKg, 0.0)
        assertEquals(6, savedHistory.exercises.single().sets.first().reps)

        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) {
            !it.loading && it.routines.singleOrNull { routine -> routine.id == routineId }?.exercises?.single()?.let { plan ->
                plan.weightKg == 22.0 && plan.reps == 6
            } == true
        }
        restored.startWorkout(routineId)
        awaitUi(restored) { it.active.let { a -> a?.id != null && a.id != active.id } }
        val future = restored.ui.value.active!!.exercises.single()
        assertEquals(105, future.restSeconds)
        assertEquals(3, future.sets.size)
        assertEquals(listOf(22.0, 18.5, 22.0), future.sets.map { it.weightKg })
        assertEquals(listOf(6, 10, 6), future.sets.map { it.reps })
        assertTrue(future.sets.none { it.completed })
        assertEquals(savedHistory, restored.ui.value.history.single { it.id == active.id })
    }

    @Test fun manualRowActionsPersistAcrossModelsWithoutMutatingAnActiveSnapshot() {
        val entry = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val routine = repo.createRoutine("Independent rows")
            val exercise = repo.createCustomExercise("Cable row", MuscleGroup.BACK, Equipment.CABLE, LoadConvention.TOTAL_LOAD)
            repo.addRoutineExercise(routine, exercise).also { repo.startRoutine(routine) }
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null && it.routines.any { routine -> routine.exercises.any { e -> e.id == entry } } }
        val before = model.ui.value.active!!
        val plan = model.ui.value.routines.single().exercises.single()
        assertEquals(2, plan.setTargets.size)
        val first = plan.setTargets.first().id
        model.updateRoutineSet(first, 8, 17.25)
        model.addRoutineSet(entry)
        model.updateRoutineExerciseRest(entry, 150)
        awaitUi(model) { state -> state.routines.single().exercises.single().let {
            it.setTargets.size == 3 && it.setTargets.first().weightKg == 17.25 && it.restSeconds == 150
        } }
        val saved = model.ui.value.routines.single().exercises.single()
        assertFalse(saved.setTargets.first().inherited)
        assertTrue(saved.setTargets.drop(1).all { it.inherited })
        // The routine is the active workout's source: only its target line follows the edit (phase 4B, D16).
        assertEquals(before.logged(), model.ui.value.active!!.logged())
        assertEquals(TargetSetUi(8, 17.25), model.ui.value.active!!.exercises.single().target!!.working.first())
        model.viewModelScope.cancel()
        val reopened = newModel()
        awaitUi(reopened) { !it.loading && it.routines.singleOrNull()?.exercises?.singleOrNull()?.setTargets?.size == 3 }
        assertEquals(saved, reopened.ui.value.routines.single().exercises.single())
        assertEquals(before.logged(), reopened.ui.value.active!!.logged())
    }

    /** The workout as logged, without the target line, which follows the source routine as it is now. */
    private fun WorkoutUi.logged() = copy(exercises = exercises.map { it.copy(target = null) })

    @Test fun activeSourceIdSurvivesPresentationRecreationAndOtherStartsWithoutResettingTraining() {
        val seeded = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val exercise = repo.createCustomExercise("Session press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val first = repo.createRoutine("Identical name")
            val second = repo.createRoutine("Identical name")
            repo.addRoutineExercise(first, exercise)
            repo.addRoutineExercise(second, exercise)
            val id = repo.startRoutine(second, now = System.currentTimeMillis() - 50_000)
            val entry = repo.snapshot().workoutItems(id).single()
            repo.updateWorkoutExerciseRest(entry.id, 145)
            val set = repo.snapshot().exerciseSets(entry.id).first()
            repo.updateSet(set.copy(reps = 8, weightKg = 20.0, completed = true))
            app.timer.start(entry.name, 145)
            Triple(first, second, id)
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == seeded.third && it.timer.running }
        val before = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val timer = app.timer.state.value
        assertEquals(seeded.second, model.ui.value.active!!.sourceRoutineId)
        assertEquals(145, model.ui.value.active!!.exercises.single().restSeconds)
        assertEquals(1, model.ui.value.active!!.completedSets)
        assertTrue(model.ui.value.timer.progress in 0f..1f)

        // Both starts are serialized before the rename, which is an observable command barrier.
        model.startWorkout(seeded.first)
        model.startWorkout(null)
        model.renameRoutine(seeded.second, "Renamed source")
        awaitUi(model) { it.routines.single { routine -> routine.id == seeded.second }.name == "Renamed source" }
        runBlocking(Dispatchers.IO) { app.repository.reorderRoutines(
            app.repository.snapshot().routines.map { it.id }.let { ids -> listOf(seeded.second) + (ids - seeded.second) })  }
        awaitUi(model) { it.routines.firstOrNull()?.id == seeded.second }
        val after = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(timer, app.timer.state.value)
        assertEquals(seeded.second, model.ui.value.active!!.sourceRoutineId)

        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) { !it.loading && it.active?.id == seeded.third && it.timer.running }
        assertEquals(seeded.second, restored.ui.value.active!!.sourceRoutineId)
        assertEquals(before.activeWorkout!!.startedAt, restored.ui.value.active!!.startedAt)
        assertEquals(145, restored.ui.value.active!!.exercises.single().restSeconds)
        assertEquals(timer, app.timer.state.value)
        restored.finishWorkout()
        awaitUi(restored) { it.active == null && !it.timer.running && !it.timer.paused }
        assertTrue(restored.ui.value.history.any { it.id == seeded.third && it.sourceRoutineId == seeded.second })
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.ui.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("ViewModel did not publish expected saved records: ${model.ui.value.error}", predicate(model.ui.value))
    }

    private data class Seed(val workout: String, val benchEntry: String, val pullupEntry: String,
        val incompleteEntry: String, val oldBenchSet: String, val oldPullupSet: String)
}
