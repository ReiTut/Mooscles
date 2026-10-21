package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.RecordKind
import com.mooscles.ui.UiState
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
