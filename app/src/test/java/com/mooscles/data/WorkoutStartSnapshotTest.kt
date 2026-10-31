package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutStartSnapshotTest {
    private lateinit var db: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val queries = Collections.synchronizedList(mutableListOf<String>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WorkoutDatabase::class.java)
            .setQueryCallback(RoomDatabase.QueryCallback { sql, _ -> queries.add(sql) }, Executor { it.run() })
            .build()
        repo = WorkoutRepository(db)
    }
    @After fun close() {
        scope.cancel()
        runBlocking { scope.coroutineContext[Job]?.join() }
        db.close()
    }

    private suspend fun routine(): String {
        val movement = Exercise(name = "Snapshot movement", muscle = MuscleGroup.CHEST,
            equipment = Equipment.DUMBBELL, convention = LoadConvention.PER_HAND, custom = true)
        val routine = Routine(name = "Atomic start")
        db.withTransaction {
            db.dao().insertExercise(movement)
            db.dao().insertRoutine(routine)
            for (position in (0..2).reversed()) {
                val entry = RoutineExercise(routineId = routine.id, exerciseId = movement.id,
                    position = position, plannedSets = 3, restSeconds = 60 + position)
                db.dao().insertRoutineExercise(entry)
                db.dao().insertRoutineSets(List(3) { set -> RoutineSet(routineExerciseId = entry.id,
                    position = set, targetWeightKg = 17.125 + position + set,
                    targetReps = 6 + position + set, inheritPr = false) })
            }
        }
        return routine.id
    }

    @Test fun newStartReturnsTheExactInsertedWorkingStateWithoutReadingTheArchive() = runBlocking {
        val routine = routine()
        queries.clear()
        val saved = repo.startRoutineSnapshot(routine, now = 1000)
        val startupQueries = synchronized(queries) { queries.toList() }
        assertTrue(startupQueries.none { it.contains("FROM workout_sets", ignoreCase = true) })
        assertEquals(routine, saved.workout.sourceRoutineId)
        assertEquals(listOf(0, 1, 2), saved.entries.map { it.position })
        assertEquals(listOf(60, 61, 62), saved.entries.map { it.restSeconds })
        assertEquals(3, saved.entries.map { it.id }.distinct().size)
        assertEquals(9, saved.sets.map { it.id }.distinct().size)
        assertEquals(17.125, saved.sets.first().weightKg, 0.0)
        assertTrue(saved.matches(repo.snapshot()))
    }

    @Test fun resumePreservesExactValuesFlagsAndIdentityWithoutCreatingAnotherWorkout() = runBlocking {
        val saved = repo.startRoutineSnapshot(routine(), now = 1000)
        val changed = saved.sets[1].copy(reps = 6, weightKg = 31.125, completed = true, warmup = true, assistance = true)
        repo.updateSet(changed)
        val resumed = repo.startRoutineSnapshot(saved.workout.sourceRoutineId!!, now = 9000)
        assertEquals(saved.workout.id, resumed.workout.id)
        assertEquals(changed, resumed.sets.single { it.id == changed.id })
        assertTrue(resumed.matches(repo.snapshot()))
        assertEquals(1, repo.snapshot().workouts.size)
        assertEquals(resumed, repo.activeSnapshot())
    }

    @Test fun emptyStartIsAtomicAndRepeatedStartResumesTheSameSession() = runBlocking {
        val saved = repo.startEmptySnapshot("Empty", now = 1000)
        assertTrue(saved.entries.isEmpty())
        assertTrue(saved.sets.isEmpty())
        assertTrue(saved.matches(repo.snapshot()))
        assertEquals(saved.workout.id, repo.startEmpty(now = 2000))
        assertEquals(1, repo.snapshot().workouts.size)
    }

    @Test fun ordinaryInvalidationsKeepTheOriginalFullFlowWithoutExtraNarrowReads() = runBlocking {
        val saved = repo.startRoutineSnapshot(routine(), now = 1000)
        val observed = Channel<AppData>(Channel.UNLIMITED)
        val collector = scope.launch { repo.data.collect { observed.send(it) } }
        withTimeout(15_000) { observed.receive() }
        queries.clear()
        val changed = saved.sets.first().copy(reps = 7)
        repo.updateSet(changed)
        val actual = withTimeout(15_000) {
            var next = observed.receive()
            while (next.sets.none { it.id == changed.id && it.reps == 7 }) next = observed.receive()
            next
        }
        collector.cancelAndJoin()
        assertEquals(changed, actual.sets.single { it.id == changed.id })
        val invalidationQueries = synchronized(queries) { queries.toList() }
        assertTrue(invalidationQueries.any { it.contains("SELECT * FROM workout_sets ORDER BY position", ignoreCase = true) })
        assertTrue(invalidationQueries.none { it.contains("SELECT * FROM exercises WHERE id IN (SELECT exerciseId FROM workout_exercises", ignoreCase = true) })
    }

    @Test fun discardedOrFinishedSnapshotCannotAcknowledgeAnotherActiveSession() = runBlocking {
        val routine = routine()
        val old = repo.startRoutineSnapshot(routine, now = 1000)
        repo.discardWorkout(old.workout.id)
        assertNull(repo.activeSnapshot())
        val replacement = repo.startRoutineSnapshot(routine, now = 2000)
        assertNotEquals(old.workout.id, replacement.workout.id)
        assertFalse(old.matches(repo.snapshot()))
        repo.updateSet(replacement.sets.first().copy(completed = true))
        assertTrue(repo.finishWorkout(replacement.workout.id, now = 3000))
        assertNull(repo.activeSnapshot())
        assertFalse(replacement.matches(repo.snapshot()))
    }
}
