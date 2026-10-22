package com.mooscles.sharing

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.data.WorkoutStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the saved Room snapshot, real card factory, and Android Canvas in one export. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutShareExportIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: WorkoutRepository

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        repository = WorkoutRepository(database)
        repository.initialize()
    }

    @After fun close() { database.close() }

    @Test fun completedSavedWorkoutExportsItsActualSetsRecordsAndStatisticsWithoutMutatingHistory() = runBlocking {
        val bench = repository.createCustomExercise("Barbell bench press", MuscleGroup.CHEST,
            Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val pullup = repository.createCustomExercise("Wide-grip pull-up", MuscleGroup.BACK,
            Equipment.BODYWEIGHT, LoadConvention.BODYWEIGHT)
        val priorStart = Instant.parse("2026-10-20T18:00:00Z").toEpochMilli()
        val prior = repository.startEmpty("Previous upper body session", now = priorStart)
        complete(repository.addWorkoutExercise(prior, bench), listOf(20.0 to 7))
        complete(repository.addWorkoutExercise(prior, pullup), listOf(0.0 to 10))
        assertTrue(repository.finishWorkout(prior, now = priorStart + 40 * 60_000L))

        val selectedStart = Instant.parse("2026-10-22T18:00:00Z").toEpochMilli()
        val selected = repository.startEmpty("Upper body · New milestones", now = selectedStart)
        val benchEntry = repository.addWorkoutExercise(selected, bench)
        val pullupEntry = repository.addWorkoutExercise(selected, pullup)
        complete(benchEntry, listOf(20.0 to 7, 22.0 to 6))
        complete(pullupEntry, List(3) { 0.0 to 12 })
        assertTrue(repository.finishWorkout(selected, now = selectedStart + 44 * 60_000L))

        val before = repository.snapshot()
        // Reload through a new repository instance so the export cannot depend on in-memory UI state.
        val saved = WorkoutRepository(database).snapshot()
        assertEquals(before, saved)
        assertEquals(WorkoutStatus.FINISHED, saved.workouts.single { it.id == selected }.status)
        assertEquals(listOf(20.0 to 7, 22.0 to 6), saved.exerciseSets(benchEntry).map { it.weightKg to it.reps })
        assertEquals(List(3) { 0.0 to 12 }, saved.exerciseSets(pullupEntry).map { it.weightKg to it.reps })

        val card = WorkoutShareCards.from(saved, selected, pounds = false,
            locale = Locale.US, zoneId = ZoneId.of("UTC"))
        assertEquals(selected, card.workoutId)
        assertEquals(2, card.workoutNumber)
        assertEquals("Upper body · New milestones", card.workoutName)
        assertEquals(listOf("Barbell bench press", "Wide-grip pull-up"), card.rows.map { it.exerciseName })
        assertEquals(listOf(2, 3), card.rows.map { it.completedSets })
        assertEquals(listOf("22 kg × 6", "Bodyweight × 12"), card.rows.map { it.bestSetText })
        assertEquals(listOf(true, true), card.rows.map { it.hasPr })
        assertEquals(2, card.prCount)
        assertEquals("272 kg", card.volumeText)
        assertEquals("44:00", card.durationText)

        val png = WorkoutCardRenderer().export(context, card).single()
        assertTrue(png.isFile)
        assertTrue(png.length() > 10_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(png.absolutePath, bounds)
        assertEquals(1080, bounds.outWidth)
        assertTrue(bounds.outHeight in 700..WorkoutCardRenderer.MAX_PAGE_HEIGHT)
        assertEquals("image/png", bounds.outMimeType)
        val preview = File("build/outputs/share-previews/representative-kg.png")
        assertTrue(preview.parentFile!!.isDirectory || preview.parentFile!!.mkdirs())
        png.copyTo(preview, overwrite = true)
        assertArrayEquals(png.readBytes(), preview.readBytes())

        assertEquals(before, repository.snapshot())
        assertEquals(saved, WorkoutRepository(database).snapshot())
    }

    private suspend fun complete(entryId: String, performances: List<Pair<Double, Int>>) {
        while (repository.snapshot().exerciseSets(entryId).size < performances.size) repository.addSet(entryId)
        val sets = repository.snapshot().exerciseSets(entryId)
        performances.forEachIndexed { index, (weight, reps) ->
            repository.updateSet(sets[index].copy(weightKg = weight, reps = reps, completed = true))
        }
    }
}
