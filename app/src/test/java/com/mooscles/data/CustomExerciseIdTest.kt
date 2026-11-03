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

/** A custom exercise created with the id the screen chose; real Room under Android's SQLite runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomExerciseIdTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    @Test fun createsWithTheGivenId() = runBlocking {
        val id = repo.createCustomExercise("  Machine row  ", MuscleGroup.BACK, Equipment.PLATE_LOADED_MACHINE,
            LoadConvention.TOTAL_LOAD, id = "screen-id-1")
        assertEquals("screen-id-1", id)
        assertEquals(
            Exercise("screen-id-1", "Machine row", MuscleGroup.BACK, Equipment.PLATE_LOADED_MACHINE, LoadConvention.TOTAL_LOAD, custom = true),
            repo.exercise("screen-id-1"),
        )
    }

    @Test fun refusesAnExistingIdWithoutWriting() = runBlocking {
        repo.createCustomExercise("Original press", MuscleGroup.CHEST, Equipment.DUMBBELL, LoadConvention.PER_HAND, id = "taken")
        val before = repo.snapshot()
        val failure = runCatching {
            repo.createCustomExercise("Replacement", MuscleGroup.BACK, Equipment.CABLE, LoadConvention.TOTAL_LOAD, id = "taken")
        }.exceptionOrNull()
        assertTrue("Expected DataRuleException, got $failure", failure is DataRuleException)
        assertEquals(DataError.INVALID_VALUE, (failure as DataRuleException).error)
        assertEquals("Exercise already exists.", failure.message)
        assertEquals(before, repo.snapshot())
        assertEquals(
            Exercise("taken", "Original press", MuscleGroup.CHEST, Equipment.DUMBBELL, LoadConvention.PER_HAND, custom = true),
            repo.exercise("taken"),
        )
    }

    @Test fun blankNameIsRefusedBeforeWriting() = runBlocking {
        val before = repo.snapshot()
        val failure = runCatching {
            repo.createCustomExercise("   ", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, id = "blank-name")
        }.exceptionOrNull()
        assertEquals(DataError.INVALID_VALUE, (failure as? DataRuleException)?.error)
        assertNull(repo.exercise("blank-name"))
        assertEquals(before, repo.snapshot())
    }

    /** The catalog seed inserts with IGNORE, so a custom `catalog_` row would shadow a future catalog entry. */
    @Test fun refusesAnEmptyOrCatalogPrefixedIdWithoutWriting() = runBlocking {
        val before = repo.snapshot()
        listOf("", "   ", "catalog_future_press", "catalog_barbell_bench_press").forEach { id ->
            val failure = runCatching {
                repo.createCustomExercise("Future press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, id = id)
            }.exceptionOrNull()
            assertEquals("id '$id'", DataError.INVALID_VALUE, (failure as? DataRuleException)?.error)
            assertEquals("id '$id'", "Invalid exercise id.", failure?.message)
        }
        assertEquals(before, repo.snapshot())
    }
}
