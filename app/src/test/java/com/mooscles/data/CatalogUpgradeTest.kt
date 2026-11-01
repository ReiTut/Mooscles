package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.domain.WorkoutCalculations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise catalog refresh on a populated installation, using the real Room DAO. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogUpgradeTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: WorkoutRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repository = WorkoutRepository(database)
    }

    @After fun close() { database.close() }

    @Test fun populatedInstallRefreshesNamesAndAddsVariationsOnceWithoutChangingCustomExercisesOrHistory() = runBlocking {
        val existing = Exercise("catalog_selectorized_shoulder_press", "Selectorized shoulder press",
            MuscleGroup.SHOULDERS, Equipment.SELECTORIZED_MACHINE, LoadConvention.ONE_SIDE)
        val custom = Exercise("my-home-press", "My home shoulder press", MuscleGroup.SHOULDERS,
            Equipment.OTHER, LoadConvention.PER_HAND, custom = true)
        database.dao().seedExercises(listOf(existing, custom))
        val workout = Workout("finished", "Existing workout", 1000, 61000, WorkoutStatus.FINISHED)
        val snapshot = WorkoutExercise("finished-press", workout.id, existing.id, existing.name,
            existing.muscle, existing.equipment, existing.convention, restSeconds = 143)
        val performance = WorkoutSet("finished-set", snapshot.id, reps = 6, weightKg = 22.0, completed = true)
        database.dao().insertWorkout(workout)
        database.dao().insertWorkoutExercise(snapshot)
        database.dao().insertSet(performance)
        val records = WorkoutCalculations.records(repository.snapshot())

        repository.initialize()
        repository.initialize()

        val updated = repository.snapshot()
        assertEquals(existing.copy(name = "Seated Overhead Press (Machine)"),
            updated.exercises.single { it.id == existing.id })
        assertEquals(custom, updated.exercises.single { it.id == custom.id })
        assertEquals(ExerciseCatalog.exercises.size + 1, updated.exercises.size)
        assertEquals(updated.exercises.size, updated.exercises.map { it.id }.toSet().size)
        assertEquals(1, updated.exercises.count { it.id == "catalog_ez_bar_curl" })
        assertEquals(1, updated.exercises.count { it.id == "catalog_trap_bar_deadlift" })
        assertEquals(listOf(workout), updated.workouts)
        assertEquals(listOf(snapshot), updated.workoutExercises)
        assertEquals(listOf(performance), updated.sets)
        assertEquals(records, WorkoutCalculations.records(updated))
        assertTrue(ExerciseSearch.matches(updated.exercises.single { it.id == existing.id }, "seated machine overhead press"))
    }

    @Test fun explicitlyCustomCatalogIdentityIsNotRenamedOrReclassified() = runBlocking {
        val custom = Exercise("catalog_selectorized_shoulder_press", "My adapted shoulder movement",
            MuscleGroup.TRICEPS, Equipment.OTHER, LoadConvention.ONE_SIDE, custom = true)
        database.dao().insertExercise(custom)
        repository.initialize()
        assertEquals(custom, repository.snapshot().exercises.single { it.id == custom.id })
    }
}
