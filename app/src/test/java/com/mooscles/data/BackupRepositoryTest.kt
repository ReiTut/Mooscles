package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.LargeHistoryFixture
import com.mooscles.backup.BackupApp
import com.mooscles.backup.BackupCodec
import com.mooscles.backup.BackupData
import com.mooscles.backup.BackupException
import com.mooscles.backup.BackupFile
import com.mooscles.backup.BackupPreferences
import com.mooscles.backup.ImportPlan
import com.mooscles.backup.WorkoutExerciseRow
import com.mooscles.backup.WorkoutRow
import com.mooscles.backup.WorkoutSetRow
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Export and additive import against the real Room implementation: one phone, one other install. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRepositoryTest {
    private lateinit var phoneDb: WorkoutDatabase
    private lateinit var phone: WorkoutRepository
    private lateinit var otherDb: WorkoutDatabase
    private lateinit var other: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val preferences = BackupPreferences(false, "system", true, 120, RoutineFolder.DEFAULT_ID)
    private val app = BackupApp("1.5.0", 17, "android")

    @Before fun setup() = runBlocking {
        phoneDb = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        phone = WorkoutRepository(phoneDb)
        phone.initialize()
        otherDb = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        other = WorkoutRepository(otherDb)
        other.initialize()
    }

    @After fun close() {
        phoneDb.close()
        otherDb.close()
    }

    private suspend fun export(repo: WorkoutRepository) = repo.exportBackup(preferences, app, "2026-10-27T12:00:00Z")

    /** What the import reads: the exported file after it went through the JSON file. */
    private suspend fun exportedFile(repo: WorkoutRepository) = BackupCodec.decode(BackupCodec.encode(export(repo)))

    private suspend fun checkpoints(db: WorkoutDatabase) = db.dao().targetCheckpoints().sortedBy { "${it.exerciseId}/${it.convention}" }

    private suspend fun catalogExercise(repo: WorkoutRepository, index: Int = 0) =
        repo.snapshot().exercises.filter { !it.custom && it.convention == LoadConvention.TOTAL_LOAD }[index]

    /** A finished workout with one completed set of [exerciseId] and one blank set left undone. */
    private suspend fun finishedWorkout(repo: WorkoutRepository, exerciseId: String, weightKg: Double, reps: Int, start: Long): String {
        val id = repo.startEmpty("Session $start", now = start)
        val entry = repo.addWorkoutExercise(id, exerciseId)
        repo.updateSet(repo.snapshot().exerciseSets(entry).first().copy(reps = reps, weightKg = weightKg, completed = true))
        assertTrue(repo.finishWorkout(id, start + 60_000))
        return id
    }

    /** Folder, routine and custom exercise of the other install, plus a finished workout from that routine. */
    private suspend fun otherInstallData(): String {
        val press = other.createCustomExercise("Imported landmine press", MuscleGroup.SHOULDERS, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val folder = other.createRoutineFolder("Imported folder")
        val routine = other.createRoutine("Imported pull", folder)
        val item = other.addRoutineExercise(routine, press)
        other.updateRoutineExercise(other.snapshot().routineItems(routine).single().copy(plannedSets = 3, targetReps = 6, targetWeightKg = 50.0))
        other.addRoutineSet(item)
        val workout = other.startRoutine(routine, now = 5_000_000)
        val entry = other.snapshot().workoutItems(workout).single()
        val sets = other.snapshot().exerciseSets(entry.id)
        other.updateSet(sets[0].copy(completed = true))
        other.updateSet(sets[1].copy(reps = 5, weightKg = 52.5, completed = true))
        assertTrue(other.finishWorkout(workout, 5_090_000))
        return workout
    }

    private suspend fun assertUntouched(before: AppData, beforeCheckpoints: List<RoutineTargetCheckpoint>) {
        assertEquals(before, phone.snapshot())
        assertEquals(beforeCheckpoints, checkpoints(phoneDb))
    }

    @Test fun exportThenImportIntoEmptyDatabaseIsIdentical() = runBlocking {
        otherInstallData()
        val exported = exportedFile(other)
        assertEquals(BackupCodec.FORMAT, exported.format)
        assertEquals(BackupCodec.FORMAT_VERSION, exported.formatVersion)
        assertEquals(app, exported.app)
        assertEquals(preferences, exported.preferences)
        assertTrue(checkpoints(otherDb).isNotEmpty())

        val plan = phone.importBackup(exported)

        assertEquals(other.snapshot(), phone.snapshot())
        assertEquals(checkpoints(otherDb), checkpoints(phoneDb))
        assertEquals(1, plan.summary.workoutsAdded)
        assertEquals(1, plan.summary.routinesAdded)
        assertEquals(1, plan.summary.customExercisesAdded)
    }

    @Test fun importingSameFileTwiceChangesNothing() = runBlocking {
        otherInstallData()
        val file = exportedFile(other)
        phone.importBackup(file)
        val afterFirst = phone.snapshot()
        val checkpointsAfterFirst = checkpoints(phoneDb)

        val second = phone.importBackup(file)

        assertUntouched(afterFirst, checkpointsAfterFirst)
        assertEquals(0, second.summary.workoutsAdded)
        assertEquals(1, second.summary.workoutsKept)
        assertEquals(0, second.summary.routinesAdded)
    }

    @Test fun mergeKeepsEveryLocalRowUnchanged() = runBlocking {
        val bench = catalogExercise(phone).id
        phone.createCustomExercise("Local curl", MuscleGroup.BICEPS, Equipment.CABLE, LoadConvention.TOTAL_LOAD)
        val localRoutine = phone.createRoutine("Local push")
        phone.addRoutineExercise(localRoutine, bench)
        val localWorkout = finishedWorkout(phone, bench, 60.0, 8, start = 1_000_000)
        val importedWorkout = otherInstallData()
        val source = exportedFile(other)
        // The other install also has a workout with the phone's workout id but different content.
        val conflictingItem = WorkoutExerciseRow("conflicting-item", localWorkout, bench, "Changed name", "CHEST", "BARBELL", "TOTAL_LOAD", 0, 90)
        val file = source.copy(data = source.data.copy(
            workouts = source.data.workouts + WorkoutRow(localWorkout, "Conflicting copy", 9_000_000, 9_060_000, "FINISHED", null),
            workoutExercises = source.data.workoutExercises + conflictingItem,
            workoutSets = source.data.workoutSets + WorkoutSetRow("conflicting-set", "conflicting-item", 0, 1, 500.0, true, false, false),
        ))
        val before = phone.snapshot()
        val beforeCheckpoints = checkpoints(phoneDb)

        val plan = phone.importBackup(file)

        val after = phone.snapshot()
        assertTrue(after.exercises.containsAll(before.exercises))
        assertTrue(after.routineFolders.containsAll(before.routineFolders))
        assertTrue(after.routines.containsAll(before.routines))
        assertTrue(after.routineExercises.containsAll(before.routineExercises))
        assertTrue(after.routineSets.containsAll(before.routineSets))
        assertTrue(after.workouts.containsAll(before.workouts))
        assertTrue(after.workoutExercises.containsAll(before.workoutExercises))
        assertTrue(after.sets.containsAll(before.sets))
        assertTrue(checkpoints(phoneDb).containsAll(beforeCheckpoints))

        // The same-id workout keeps the phone's content, without the file's extra exercise or set.
        assertEquals(before.workouts.single { it.id == localWorkout }, after.workouts.single { it.id == localWorkout })
        assertEquals(before.workoutItems(localWorkout), after.workoutItems(localWorkout))
        assertNull(after.sets.firstOrNull { it.id == "conflicting-set" })
        assertEquals(1, plan.summary.workoutsKept)
        assertNull(plan.preferences)

        // Every new aggregate is present: history exactly, routines and folders placed after the phone's.
        val otherData = other.snapshot()
        assertEquals(otherData.workouts.single { it.id == importedWorkout }, after.workouts.single { it.id == importedWorkout })
        val importedItems = otherData.workoutItems(importedWorkout)
        assertEquals(importedItems, after.workoutItems(importedWorkout))
        importedItems.forEach { assertEquals(otherData.exerciseSets(it.id), after.exerciseSets(it.id)) }
        assertTrue(after.exercises.containsAll(otherData.exercises.filter { it.custom }))
        val importedFolder = otherData.routineFolders.single { it.id != RoutineFolder.DEFAULT_ID }
        assertEquals(importedFolder.name, after.routineFolders.single { it.id == importedFolder.id }.name)
        val importedRoutine = otherData.routines.single()
        val placed = after.routines.single { it.id == importedRoutine.id }
        assertEquals(importedRoutine.copy(position = placed.position), placed)
        assertEquals(after.routines.map { it.position }.sorted(), after.routines.indices.toList())
        assertEquals(otherData.routineItems(importedRoutine.id), after.routineItems(importedRoutine.id))
        otherData.routineItems(importedRoutine.id).forEach { assertEquals(otherData.plannedSets(it.id), after.plannedSets(it.id)) }
    }

    @Test fun invalidFileLeavesDatabaseUntouched() = runBlocking {
        val bench = catalogExercise(phone).id
        phone.createRoutine("Local push")
        finishedWorkout(phone, bench, 60.0, 8, start = 1_000_000)
        otherInstallData()
        val valid = exportedFile(other)
        val missingReference = valid.data.copy(
            workoutExercises = valid.data.workoutExercises +
                WorkoutExerciseRow("orphan", "missing-workout", bench, "Bench", "CHEST", "BARBELL", "TOTAL_LOAD", 0, 90),
        )
        val twoActive = valid.data.copy(
            workouts = valid.data.workouts +
                WorkoutRow("active-1", "One", 7_000_000, null, "ACTIVE", null) +
                WorkoutRow("active-2", "Two", 8_000_000, null, "ACTIVE", null),
        )
        val before = phone.snapshot()
        val beforeCheckpoints = checkpoints(phoneDb)

        for (data in listOf<BackupData>(missingReference, twoActive)) {
            val error = runCatching { phone.importBackup(valid.copy(data = data)) }.exceptionOrNull()
            assertTrue("expected Invalid, got $error", error is BackupException.Invalid)
            assertUntouched(before, beforeCheckpoints)
        }
    }

    @Test fun invalidPreferencesLeaveFreshInstallUntouched() = runBlocking {
        otherInstallData()
        val valid = exportedFile(other)
        val before = phone.snapshot()
        val beforeCheckpoints = checkpoints(phoneDb)

        for (prefs in listOf(preferences.copy(theme = "neon"), preferences.copy(defaultRestSeconds = 900))) {
            val error = runCatching { phone.importBackup(valid.copy(preferences = prefs)) }.exceptionOrNull()
            assertTrue("expected Invalid, got $error", error is BackupException.Invalid)
            assertUntouched(before, beforeCheckpoints)
        }
    }

    @Test fun importRefusedWhileWorkoutActive() = runBlocking {
        otherInstallData()
        val file = exportedFile(other)
        phone.startEmpty()
        val before = phone.snapshot()
        val beforeCheckpoints = checkpoints(phoneDb)

        val error = runCatching { phone.importBackup(file) }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $error", error is IllegalStateException)
        assertEquals("Finish or discard the active workout to import", error?.message)
        assertUntouched(before, beforeCheckpoints)
    }

    @Test fun beforeCommitRunsLastInsideTheImportTransaction() = runBlocking {
        otherInstallData()
        val file = exportedFile(other)
        var seen: ImportPlan? = null
        var workoutsVisible = -1

        val plan = phone.importBackup(file) { planned ->
            seen = planned
            workoutsVisible = phone.snapshot().workouts.size
        }

        assertSame(plan, seen)
        assertEquals(1, workoutsVisible)
    }

    @Test fun beforeCommitFailureLeavesDatabaseUntouched() = runBlocking {
        otherInstallData()
        val file = exportedFile(other)
        val before = phone.snapshot()
        val beforeCheckpoints = checkpoints(phoneDb)

        val error = runCatching {
            phone.importBackup(file) { throw IllegalStateException("preferences could not be saved") }
        }.exceptionOrNull()

        assertEquals("preferences could not be saved", error?.message)
        assertUntouched(before, beforeCheckpoints)
    }

    @Test fun activeWorkoutInFileBecomesTheSession() = runBlocking {
        otherInstallData()
        val open = other.startEmpty("Open on tablet", now = 6_000_000)
        other.addWorkoutExercise(open, catalogExercise(other).id)
        assertNull(phone.activeWorkout())

        phone.importBackup(exportedFile(other))

        assertEquals(other.snapshot().workouts.single { it.id == open }, phone.activeWorkout())
        assertEquals(other.snapshot().workoutItems(open), phone.snapshot().workoutItems(open))
        // Starting a routine now resumes the imported session instead of opening a second one.
        assertEquals(open, phone.startRoutine(phone.snapshot().routines.single().id))
    }

    @Test fun inheritedTargetsFollowImportedRecord() = runBlocking {
        val bench = catalogExercise(phone).id
        finishedWorkout(phone, bench, 60.0, 8, start = 1_000_000)
        val routine = phone.createRoutine("Push")
        val item = phone.addRoutineExercise(routine, bench)
        val (inherited, fixed) = phone.snapshot().plannedSets(item)
        phone.updateRoutineSet(fixed.id, 5, 70.0)
        assertEquals(60.0, phone.snapshot().routineSets.single { it.id == inherited.id }.targetWeightKg)
        val fixedBefore = phone.snapshot().routineSets.single { it.id == fixed.id }
        // The other install lifted more on the same catalog exercise.
        finishedWorkout(other, bench, 100.0, 3, start = 2_000_000)

        phone.importBackup(exportedFile(other))

        val after = phone.snapshot()
        val followed = after.routineSets.single { it.id == inherited.id }
        assertTrue(followed.inheritPr)
        assertEquals(100.0, followed.targetWeightKg)
        assertEquals(3, followed.targetReps)
        assertEquals(fixedBefore, after.routineSets.single { it.id == fixed.id })
        val checkpoint = checkpoints(phoneDb).single { it.exerciseId == bench }
        assertEquals(100.0, checkpoint.weightKg)
        assertEquals(3, checkpoint.reps)
    }

    @Test fun largeArchiveExportAndImportStayFast() = runBlocking {
        val exercises = other.snapshot().exercises.filter { !it.custom && it.convention == LoadConvention.TOTAL_LOAD }.take(6)
        val dao = otherDb.dao()
        val rows = LargeHistoryFixture.rows(exercises)
        otherDb.writeTransaction {
            dao.insertWorkouts(rows.workouts)
            dao.insertWorkoutExercises(rows.entries)
            dao.insertSets(rows.sets)
        }
        assertEquals(12_688, other.snapshot().sets.size)

        lateinit var text: String
        val exportMs = measureTimeMillis { text = BackupCodec.encode(export(other)) }
        val importMs = measureTimeMillis { phone.importBackup(BackupCodec.decode(text)) }
        println("export=${exportMs}ms import=${importMs}ms")

        val imported = phone.snapshot()
        assertEquals(208, imported.workouts.size)
        assertEquals(12_688, imported.sets.size)
        assertTrue("export took ${exportMs}ms", exportMs < 15_000)
        assertTrue("import took ${importMs}ms", importMs < 15_000)
    }
}
