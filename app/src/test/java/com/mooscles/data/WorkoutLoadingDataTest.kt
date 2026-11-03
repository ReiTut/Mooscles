package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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

/** Real Room queries guard bounded loading and the identities used by the lazy workout screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutLoadingDataTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val filename = "loading-${newId().take(8)}.db"
    private val queries = CopyOnWriteArrayList<String>()
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val scope = CoroutineScope(Dispatchers.Default)

    @Before fun open() {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename)
            .setDriver(RecordingDriver { sql -> queries += sql })
            .build()
        repo = WorkoutRepository(database)
    }

    @After fun close() {
        scope.cancel()
        database.close()
        context.deleteDatabase(filename)
    }

    /** Repeats one exercise variation, intentionally inserting its instance positions in reverse. */
    private suspend fun routine(count: Int, legacy: Boolean = false): String {
        val movement = Exercise(name = "Repeated movement", muscle = MuscleGroup.CHEST,
            equipment = Equipment.BODYWEIGHT, convention = LoadConvention.BODYWEIGHT, custom = true)
        val routine = Routine(name = "Routine $count")
        database.writeTransaction {
            val dao = database.dao()
            dao.insertExercise(movement)
            dao.insertRoutine(routine)
            for (position in (0 until count).reversed()) {
                val plan = RoutineExercise(routineId = routine.id, exerciseId = movement.id,
                    position = position, plannedSets = 3, targetReps = 4 + position,
                    targetWeightKg = null, restSeconds = 60 + position)
                dao.insertRoutineExercise(plan)
                if (!legacy) dao.insertRoutineSets(List(3) { row ->
                    RoutineSet(routineExerciseId = plan.id, position = row,
                        targetReps = position + row,
                        targetWeightKg = if (row == 1) position + 0.25 else null,
                        inheritPr = row == 2)
                })
            }
        }
        return routine.id
    }

    private fun loadingSelects() = queries.map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { sql ->
            sql.startsWith("SELECT", ignoreCase = true) &&
                Regex("\\bFROM (exercises|routines|routine_exercises|routine_sets|workouts|workout_exercises|workout_sets)\\b")
                    .containsMatchIn(sql)
        }

    private suspend fun observe(): Channel<AppData> {
        val channel = Channel<AppData>(Channel.UNLIMITED)
        scope.launch { repo.data.collect { channel.send(it) } }
        return channel
    }

    private suspend fun Channel<AppData>.nextMatching(predicate: (AppData) -> Boolean): AppData =
        withTimeout(10_000) {
            var value = receive()
            while (!predicate(value)) value = receive()
            value
        }

    @Test fun startQueryCountIsConstantAndLargeDuplicateInstancesKeepIndependentTargetsAndOrder() = runBlocking {
        val typical = routine(3)
        val large = routine(40)
        queries.clear()
        val typicalWorkout = repo.startRoutine(typical, now = 1000)
        val typicalReads = loadingSelects()
        repo.discardWorkout(typicalWorkout)
        queries.clear()
        val workout = repo.startRoutine(large, now = 2000)
        val largeReads = loadingSelects()
        assertEquals("Start uses five bulk reads for both routine sizes", 5, typicalReads.size)
        assertEquals(typicalReads.size, largeReads.size)
        assertFalse(largeReads.any { it == "SELECT * FROM exercises WHERE id = ?" })
        assertFalse(largeReads.any { it == "SELECT * FROM routine_sets WHERE routineExerciseId = ? ORDER BY position" })

        val data = repo.snapshot()
        val entries = data.workoutItems(workout)
        assertEquals(large, data.activeWorkout!!.sourceRoutineId)
        assertEquals((0 until 40).toList(), entries.map { it.position })
        assertEquals(40, entries.map { it.id }.toSet().size)
        assertEquals(1, entries.map { it.exerciseId }.toSet().size)
        assertEquals(120, data.sets.map { it.id }.toSet().size)
        entries.forEachIndexed { position, entry ->
            assertEquals(60 + position, entry.restSeconds)
            val rows = data.exerciseSets(entry.id)
            assertEquals(listOf(position, position + 1, position + 2), rows.map { it.reps })
            assertEquals(listOf(0.0, position + 0.25, 0.0), rows.map { it.weightKg })
            assertTrue(rows.none { it.completed || it.warmup || it.assistance })
        }
        assertEquals(workout, repo.startRoutine(typical, now = 9999))
        assertEquals(data, repo.snapshot())
    }

    @Test fun batchedLegacyRepairAndCurrentPerSetValuesSurviveReopeningAndResume() = runBlocking {
        val routine = routine(4, legacy = true)
        val workout = repo.startRoutine(routine, now = 1000)
        val initial = repo.snapshot()
        val entry = initial.workoutItems(workout)[2]
        val row = initial.exerciseSets(entry.id)[1]
        repo.updateSet(row.copy(reps = 12, weightKg = 0.0, completed = true))
        val expected = repo.snapshot()
        assertEquals(12, expected.routineSets.size)
        assertTrue(expected.routineSets.none { it.inheritPr })
        assertEquals(listOf(4, 5, 6, 7), expected.workoutItems(workout).map { expected.exerciseSets(it.id).first().reps })
        database.close()
        open()
        assertEquals(workout, repo.startRoutine(routine, now = 5000))
        assertEquals(expected, repo.snapshot())
        assertEquals(expected.sets.first { it.id == row.id }, repo.workoutSet(row.id))
        assertEquals(expected.workoutItems(workout).flatMap { expected.exerciseSets(it.id) }, repo.workoutSets(workout))
    }

    @Test fun activeSetEditReadsOneAtomicSnapshotAndKeepsEqualSnapshotLists() = runBlocking {
        val routine = routine(3)
        val workout = repo.startRoutine(routine)
        val channel = observe()
        val initial = channel.nextMatching { it.activeWorkout?.id == workout }
        val row = initial.exerciseSets(initial.workoutItems(workout).first().id).first()
        queries.clear()
        repo.updateSet(row.copy(reps = 11, weightKg = 17.25, completed = true))
        val changed = channel.nextMatching { it.sets.any { set -> set.id == row.id && set.completed } }
        val reads = loadingSelects()
        assertEquals(1, reads.count { it == "SELECT * FROM workout_sets ORDER BY position" })
        assertEquals(8, reads.size) // One targeted command lookup and one atomic seven-table read.
        assertSame(initial.exercises, changed.exercises)
        assertSame(initial.routines, changed.routines)
        assertSame(initial.routineExercises, changed.routineExercises)
        assertSame(initial.routineSets, changed.routineSets)
        assertSame(initial.workouts, changed.workouts)
        assertSame(initial.workoutExercises, changed.workoutExercises)
        assertEquals(17.25, changed.sets.first { it.id == row.id }.weightKg, 0.0)
        assertEquals(row, initial.sets.first { it.id == row.id }) // Previous snapshots remain immutable.
    }

    @Test fun tableCacheObservesExternalAtomicWritesAndCascadeDeletionWithoutOrphans() = runBlocking {
        val routine = routine(2)
        val workout = repo.startRoutine(routine)
        val channel = observe()
        val initial = channel.nextMatching { it.activeWorkout?.id == workout }
        val first = initial.workoutItems(workout).first()
        database.writeTransaction {
            database.dao().updateWorkoutExercise(first.copy(name = "Changed outside repository", restSeconds = 145))
            val row = initial.exerciseSets(first.id).last()
            database.dao().updateSet(row.copy(weightKg = 21.5, reps = 8, completed = true))
        }
        val changed = channel.nextMatching { it.workoutExercises.any { entry -> entry.name == "Changed outside repository" } }
        assertEquals(145, changed.workoutItems(workout).first().restSeconds)
        assertTrue(changed.exerciseSets(first.id).last().completed)
        assertEquals(21.5, changed.exerciseSets(first.id).last().weightKg, 0.0)
        repo.discardWorkout(workout)
        val deleted = channel.nextMatching { it.activeWorkout == null }
        assertTrue(deleted.workouts.isEmpty())
        assertTrue(deleted.workoutExercises.isEmpty())
        assertTrue(deleted.sets.isEmpty())
        assertEquals(initial.routineExercises, deleted.routineExercises)
        assertEquals(initial.routineSets, deleted.routineSets)
    }

    @Test fun activeIdObserverTracksSessionTransitionsWithoutEmittingForSetEdits() = runBlocking {
        val ids = Channel<String?>(Channel.UNLIMITED)
        scope.launch { repo.activeWorkoutIds.collect { ids.send(it) } }
        assertNull(withTimeout(10_000) { ids.receive() })
        val routine = routine(2)
        val workout = repo.startRoutine(routine)
        assertEquals(workout, withTimeout(10_000) { ids.receive() })
        val snapshots = observe()
        val before = snapshots.nextMatching { it.activeWorkout?.id == workout }
        val row = before.sets.first()
        repo.updateSet(row.copy(reps = 8, completed = true))
        snapshots.nextMatching { it.sets.any { set -> set.id == row.id && set.completed } }
        assertFalse(ids.tryReceive().isSuccess)
        repo.finishWorkout(workout)
        assertNull(withTimeout(10_000) { ids.receive() })
    }

    @Test fun concurrentCommittedGenerationsNeverMixMetadataWithSetValues() = runBlocking {
        val routine = routine(1)
        val workout = repo.startRoutine(routine)
        val snapshots = observe()
        val initial = snapshots.nextMatching { it.activeWorkout?.id == workout }
        val entry = initial.workoutItems(workout).single()
        val rows = initial.exerciseSets(entry.id)
        val writer = scope.launch {
            repeat(30) { index ->
                val generation = index + 1
                database.writeTransaction {
                    database.dao().updateWorkoutExercise(entry.copy(name = "Generation $generation"))
                    rows.forEach { row -> database.dao().updateSet(row.copy(reps = generation)) }
                }
            }
        }
        withTimeout(10_000) {
            var last = 0
            while (last < 30) {
                val snapshot = snapshots.receive()
                val current = snapshot.workoutItems(workout).single()
                if (current.name.startsWith("Generation ")) {
                    last = current.name.substringAfter("Generation ").toInt()
                    assertTrue("Metadata and sets must come from the same committed generation",
                        snapshot.exerciseSets(entry.id).all { it.reps == last })
                }
            }
        }
        writer.join()
    }
}
