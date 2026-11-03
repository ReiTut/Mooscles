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

/**
 * The routine and folder commands of the phase 4B screens, on the real generated Room code: ids chosen by
 * the screen, folder deletion and ordering, planned warm-ups (D14) and undoing a removed planned set.
 * Every refusal writes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineCommandsPersistenceTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var clock = 1_000L

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    private suspend fun exercise(name: String = "Movement ${newId()}"): String =
        repo.createCustomExercise(name, MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)

    /** A finished workout whose only completed set makes [weightKg] × [reps] the exercise's record. */
    private suspend fun record(exerciseId: String, weightKg: Double, reps: Int) {
        val workout = repo.startEmpty(now = clock.also { clock += 1_000 })
        val entry = repo.addWorkoutExercise(workout, exerciseId)
        val set = repo.snapshot().exerciseSets(entry).first()
        repo.updateSet(set.copy(weightKg = weightKg, reps = reps, completed = true))
        assertTrue(repo.finishWorkout(workout, now = clock))
    }

    private suspend fun rows(entryId: String): List<RoutineSet> = repo.snapshot().plannedSets(entryId)

    private suspend fun routines(folderId: String): List<String> =
        repo.snapshot().routines.filter { it.folderId == folderId }.sortedBy { it.position }.map { it.id }

    /** A plan whose sets are A (a 40 kg × 10 warm-up), B (100 kg × 5) and C (inherited, no record: empty). */
    private suspend fun threeSets(): Pair<String, List<RoutineSet>> {
        val routine = repo.createRoutine("Push")
        val entry = repo.addRoutineExercise(routine, exercise())
        repo.addRoutineSet(entry)
        val (a, b) = rows(entry)
        repo.updateRoutineSet(a.id, 10, 40.0)
        repo.setRoutineSetWarmup(a.id, true)
        repo.updateRoutineSet(b.id, 5, 100.0)
        return entry to rows(entry)
    }

    private suspend fun assertRefused(error: DataError, call: suspend () -> Unit) {
        val before = repo.snapshot()
        val failure = try {
            call()
            null
        } catch (e: DataRuleException) {
            e
        }
        assertNotNull("Expected the command to be refused with $error", failure)
        assertEquals(error, failure!!.error)
        assertEquals("A refused command must write nothing", before, repo.snapshot())
    }

    @Test fun createRoutineUsesTheGivenIdAndFolder() = runBlocking {
        repo.createRoutine("Existing")
        val folder = repo.createRoutineFolder("Hypertrophy")

        assertEquals("screen-chosen", repo.createRoutine("  Push day ", folder, "screen-chosen"))

        val routine = repo.snapshot().routines.single { it.id == "screen-chosen" }
        assertEquals("Push day", routine.name)
        assertEquals(folder, routine.folderId)
        assertEquals(1, routine.position)
        assertTrue(repo.snapshot().routineItems(routine.id).isEmpty())
    }

    @Test fun createRoutineRefusesAnExistingId() = runBlocking {
        val existing = repo.createRoutine("Push")
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = existing) }
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = "  ") }
        assertRefused(DataError.INVALID_VALUE) { repo.createRoutine("Pull", id = "") }
        assertEquals("Push", repo.snapshot().routines.single().name)
    }

    @Test fun duplicateWithANameUsesItAndTheGivenId() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val original = repo.createRoutine("Treino A", folder)
        repo.addRoutineExercise(original, exercise())
        repo.createRoutine("Other")

        assertEquals("copy-id", repo.duplicateRoutine(original, " Treino A (cópia) ", "copy-id"))

        val copy = repo.snapshot().routines.single { it.id == "copy-id" }
        assertEquals("Treino A (cópia)", copy.name)
        assertEquals(folder, copy.folderId)
        assertEquals(2, copy.position)
        assertEquals(1, repo.snapshot().routineItems(copy.id).size)
        // The id the screen chose must be new, and the name follows the usual rule.
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "Again", "copy-id") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "Again", " ") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "   ", "another-id") }
        assertRefused(DataError.INVALID_VALUE) { repo.duplicateRoutine(original, "x".repeat(121), "another-id") }
        // Without a name the copy keeps today's English suffix.
        val legacy = repo.duplicateRoutine(original)
        assertEquals("Treino A (copy)", repo.snapshot().routines.single { it.id == legacy }.name)
    }

    @Test fun duplicateCopiesWarmups() = runBlocking {
        val (entry, original) = threeSets()
        val routine = repo.routineExercise(entry)!!.routineId

        val copy = repo.duplicateRoutine(routine, "Copy", "copy-id")

        val copiedEntry = repo.snapshot().routineItems(copy).single()
        val copied = rows(copiedEntry.id)
        assertEquals(original.map { it.copy(id = "", routineExerciseId = "") }, copied.map { it.copy(id = "", routineExerciseId = "") })
        assertEquals(listOf(true, false, false), copied.map { it.warmup })
        assertEquals(listOf(false, false, true), copied.map { it.inheritPr })
        assertTrue(copied.none { row -> row.id in original.map { it.id } })
    }

    @Test fun addRoutineExercisesKeepsTheOrderInOneTransaction() = runBlocking {
        val routine = repo.createRoutine("Push")
        val first = repo.addRoutineExercise(routine, exercise("First"))
        val (b, a, c) = listOf(exercise("B"), exercise("A"), exercise("C"))
        record(a, 60.0, 8)

        val added = repo.addRoutineExercises(routine, listOf(b, a, c))

        val items = repo.snapshot().routineItems(routine)
        assertEquals(listOf(first) + added, items.map { it.id })
        assertEquals(listOf(b, a, c), items.drop(1).map { it.exerciseId })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.position })
        // Each one exactly as addRoutineExercise adds it: two inherited working sets and the default rest.
        items.drop(1).forEach { item ->
            assertEquals(2, item.plannedSets)
            assertEquals(com.mooscles.domain.RestDuration.DEFAULT_SECONDS, item.restSeconds)
            assertTrue(rows(item.id).all { it.inheritPr && !it.warmup })
        }
        assertEquals(listOf(8 to 60.0, 8 to 60.0), rows(added[1]).map { it.targetReps to it.targetWeightKg })
        assertEquals(60.0, items[2].targetWeightKg!!, 0.0)
        assertTrue(repo.addRoutineExercises(routine, emptyList()).isEmpty())
    }

    @Test fun aMissingExerciseAddsNone() = runBlocking {
        val routine = repo.createRoutine("Push")
        val (a, b) = listOf(exercise(), exercise())
        assertRefused(DataError.EXERCISE_NOT_FOUND) { repo.addRoutineExercises(routine, listOf(a, "missing", b)) }
        assertRefused(DataError.EXERCISE_NOT_FOUND) { repo.addRoutineExercises(routine, listOf(a, b, "missing")) }
        assertRefused(DataError.ROUTINE_NOT_FOUND) { repo.addRoutineExercises("missing", listOf(a)) }
        assertTrue(repo.snapshot().routineItems(routine).isEmpty())
    }

    @Test fun deletingAFolderMovesItsRoutinesToTheEndOfTheDefault() = runBlocking {
        val doomed = repo.createRoutineFolder("Hypertrophy")
        val kept = repo.createRoutineFolder("Strength")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", doomed)
        val d2 = repo.createRoutine("D2")
        val k1 = repo.createRoutine("K1", kept)
        val a2 = repo.createRoutine("A2", doomed)
        val entry = repo.addRoutineExercise(a1, exercise())
        val before = repo.snapshot()

        repo.deleteRoutineFolder(doomed)

        val after = repo.snapshot()
        assertNull(repo.routineFolder(doomed))
        assertEquals(listOf(d1, d2, k1, a1, a2), after.routines.sortedBy { it.position }.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), after.routines.sortedBy { it.position }.map { it.position })
        assertEquals(listOf(d1, d2, a1, a2), routines(RoutineFolder.DEFAULT_ID))
        assertEquals(listOf(k1), routines(kept))
        // No routine is lost or renamed, and what they plan is untouched.
        assertEquals(before.routines.map { it.id to it.name }.toSet(), after.routines.map { it.id to it.name }.toSet())
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        assertEquals(entry, after.routineItems(a1).single().id)
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.deleteRoutineFolder(doomed) }
    }

    @Test fun deletingAFolderKeepsTheActiveWorkoutsSource() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val routine = repo.createRoutine("Upper", folder)
        repo.addRoutineExercise(routine, exercise())
        val workout = repo.startRoutine(routine)
        val before = repo.snapshot()

        repo.deleteRoutineFolder(folder)

        val after = repo.snapshot()
        assertEquals(routine, after.activeWorkout!!.sourceRoutineId)
        assertEquals(workout, after.activeWorkout!!.id)
        assertEquals(RoutineFolder.DEFAULT_ID, after.routines.single { it.id == routine }.folderId)
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
    }

    @Test fun theDefaultFolderCannotBeDeleted() = runBlocking {
        repo.createRoutine("Mine")
        repo.createRoutineFolder("Other")
        assertRefused(DataError.INVALID_VALUE) { repo.deleteRoutineFolder(RoutineFolder.DEFAULT_ID) }
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.deleteRoutineFolder("missing") }
        assertNotNull(repo.routineFolder(RoutineFolder.DEFAULT_ID))
    }

    @Test fun deletingRenumbersTheFolders() = runBlocking {
        val a = repo.createRoutineFolder("A")
        val b = repo.createRoutineFolder("B")
        val c = repo.createRoutineFolder("C")
        // The default folder reorders like any other, so it need not be first.
        repo.reorderRoutineFolders(listOf(a, RoutineFolder.DEFAULT_ID, b, c))

        repo.deleteRoutineFolder(b)

        val folders = repo.snapshot().routineFolders.sortedBy { it.position }
        assertEquals(listOf(a, RoutineFolder.DEFAULT_ID, c), folders.map { it.id })
        assertEquals(listOf(0, 1, 2), folders.map { it.position })
    }

    @Test fun reorderRoutineFoldersNeedsEveryId() = runBlocking {
        val a = repo.createRoutineFolder("A")
        val b = repo.createRoutineFolder("B")
        val default = RoutineFolder.DEFAULT_ID
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, default, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, a)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderRoutineFolders(listOf(b, a, "missing")) }

        repo.reorderRoutineFolders(listOf(b, default, a))

        val folders = repo.snapshot().routineFolders
        assertEquals(listOf(b, default, a), folders.map { it.id })
        assertEquals(listOf(0, 1, 2), folders.map { it.position })
        assertEquals(listOf("B", RoutineFolder.DEFAULT_NAME, "A"), folders.map { it.name })
    }

    @Test fun reorderFolderRoutinesKeepsTheOtherFoldersSlots() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", folder)
        val d2 = repo.createRoutine("D2")
        val a2 = repo.createRoutine("A2", folder)
        val a3 = repo.createRoutine("A3", folder)

        repo.reorderFolderRoutines(folder, listOf(a3, a1, a2))

        val positions = repo.snapshot().routines.associate { it.id to it.position }
        // The folder's routines trade the global slots they held (1, 3, 4); the others keep theirs.
        assertEquals(mapOf(d1 to 0, a3 to 1, d2 to 2, a1 to 3, a2 to 4), positions)
        assertEquals(listOf(a3, a1, a2), routines(folder))
        assertEquals(listOf(d1, d2), routines(RoutineFolder.DEFAULT_ID))
    }

    @Test fun reorderFolderRoutinesRefusesForeignIds() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", folder)
        val a2 = repo.createRoutine("A2", folder)
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a1, d1)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, d1)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a2)) }
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a2, a1, "missing")) }
        assertRefused(DataError.FOLDER_NOT_FOUND) { repo.reorderFolderRoutines("missing", listOf(a2, a1)) }
    }

    @Test fun warmupMakesTheSetManualAndKeepsItsValues() = runBlocking {
        val exerciseId = exercise()
        record(exerciseId, 80.0, 6)
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val (first, second) = rows(entry)
        assertTrue(first.inheritPr)

        repo.setRoutineSetWarmup(first.id, true)

        assertEquals(first.copy(warmup = true, inheritPr = false), repo.snapshot().routineSets.single { it.id == first.id })
        // Back to a working set: it stays manual, with the same values.
        repo.setRoutineSetWarmup(first.id, false)
        assertEquals(first.copy(inheritPr = false), repo.snapshot().routineSets.single { it.id == first.id })
        // Asking for what a set already is changes nothing: an inherited working set keeps following the record.
        repo.setRoutineSetWarmup(second.id, false)
        assertEquals(second, repo.snapshot().routineSets.single { it.id == second.id })
        assertRefused(DataError.ROUTINE_NOT_FOUND) { repo.setRoutineSetWarmup("missing", true) }
    }

    @Test fun inheritingAWarmupMakesAWorkingSet() = runBlocking {
        val exerciseId = exercise()
        record(exerciseId, 80.0, 6)
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val first = rows(entry).first()
        repo.updateRoutineSet(first.id, 12, 40.0)
        repo.setRoutineSetWarmup(first.id, true)

        repo.inheritRoutineSet(first.id)

        assertEquals(RoutineSet(first.id, entry, 0, 6, 80.0, inheritPr = true, warmup = false),
            repo.snapshot().routineSets.single { it.id == first.id })
    }

    @Test fun synchronizationIgnoresWarmups() = runBlocking {
        val exerciseId = exercise()
        val entry = repo.addRoutineExercise(repo.createRoutine("Push"), exerciseId)
        val (first, second) = rows(entry)
        // A backup can bring a warm-up that also says it follows the record; the screens never write one.
        val imported = first.copy(targetReps = 12, targetWeightKg = 40.0, inheritPr = true, warmup = true)
        database.dao().updateRoutineSet(imported)

        record(exerciseId, 100.0, 5)
        repo.initialize()

        val saved = rows(entry)
        assertEquals(imported, saved[0])
        assertEquals(second.copy(targetReps = 5, targetWeightKg = 100.0), saved[1])
        // The plan's legacy columns mirror the working set, not the warm-up (F19).
        assertEquals(5, repo.routineExercise(entry)!!.targetReps)
        assertEquals(100.0, repo.routineExercise(entry)!!.targetWeightKg!!, 0.0)
    }

    @Test fun mirroredPlanColumnsFollowTheFirstWorkingSet() = runBlocking {
        val (entry, original) = threeSets()
        var plan = repo.routineExercise(entry)!!
        assertEquals(3, plan.plannedSets)
        assertEquals(5 to 100.0, plan.targetReps to plan.targetWeightKg)

        original.drop(1).forEach { repo.setRoutineSetWarmup(it.id, true) }
        repo.updateRoutineSet(original[1].id, 6, 50.0)

        // With warm-ups only, the first row is mirrored, as before warm-ups existed.
        plan = repo.routineExercise(entry)!!
        assertEquals(10 to 40.0, plan.targetReps to plan.targetWeightKg)
    }

    @Test fun addRoutineSetCopiesTheLastSet() = runBlocking {
        val (entry, original) = threeSets()
        val inherited = repo.addRoutineSet(entry)
        assertEquals(original.last().copy(id = inherited, position = 3), rows(entry).last())

        repo.setRoutineSetWarmup(inherited, true)
        repo.updateRoutineSet(inherited, 8, 50.0)
        val warmup = repo.addRoutineSet(entry)

        val added = rows(entry).last()
        assertEquals(RoutineSet(warmup, entry, 4, 8, 50.0, inheritPr = false, warmup = true), added)

        // After a manual working set, the new one copies its values and stays manual.
        repo.setRoutineSetWarmup(warmup, false)
        val manual = repo.addRoutineSet(entry)
        assertEquals(RoutineSet(manual, entry, 5, 8, 50.0, inheritPr = false, warmup = false), rows(entry).last())
        assertEquals(6, repo.routineExercise(entry)!!.plannedSets)
    }

    @Test fun addRoutineSetAfterAnImportedInheritedWarmupMakesAManualWarmup() = runBlocking {
        val (entry, original) = threeSets()
        // A backup can bring a warm-up that also says it follows the record; the copy must not spread that.
        database.dao().updateRoutineSet(original.last().copy(targetReps = 12, targetWeightKg = 30.0, inheritPr = true, warmup = true))

        val added = repo.addRoutineSet(entry)

        assertEquals(RoutineSet(added, entry, 3, 12, 30.0, inheritPr = false, warmup = true), rows(entry).last())
    }

    @Test fun replaceRoutineExerciseKeepsWarmups() = runBlocking {
        val (entry, original) = threeSets()
        val replacement = exercise()
        record(replacement, 70.0, 9)

        repo.replaceRoutineExercise(entry, replacement)

        // A warm-up stays a warm-up, emptied and manual (F18); working sets inherit the new record.
        assertEquals(listOf(
            RoutineSet(original[0].id, entry, 0, 0, null, inheritPr = false, warmup = true),
            RoutineSet(original[1].id, entry, 1, 9, 70.0, inheritPr = true, warmup = false),
            RoutineSet(original[2].id, entry, 2, 9, 70.0, inheritPr = true, warmup = false),
        ), rows(entry))
        assertEquals(replacement, repo.routineExercise(entry)!!.exerciseId)
        assertEquals(9 to 70.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
    }

    @Test fun startRoutineCopiesWarmups() = runBlocking {
        val (entry, _) = threeSets()
        val routine = repo.routineExercise(entry)!!.routineId

        val workout = repo.startRoutine(routine)

        val sets = repo.snapshot().let { it.exerciseSets(it.workoutItems(workout).single().id) }
        assertEquals(listOf(true, false, false), sets.map { it.warmup })
        assertEquals(listOf(10 to 40.0, 5 to 100.0, 0 to 0.0), sets.map { it.reps to it.weightKg })
        assertTrue(sets.none { it.completed })
    }

    @Test fun restoreRoutineSetPutsItBackAtItsPositionWithItsId() = runBlocking {
        val (entry, original) = threeSets()
        val (a, b, c) = original
        repo.removeRoutineSet(a.id)
        assertEquals(listOf(b.id to 0, c.id to 1), rows(entry).map { it.id to it.position })

        repo.restoreRoutineSet(entry, 0, a)

        // Same id, values, warm-up flag and mode, at its place; the mirrored columns still follow B.
        assertEquals(original, rows(entry))
        assertEquals(5 to 100.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
        assertEquals(3, repo.routineExercise(entry)!!.plannedSets)
        // Past the end, it goes last.
        repo.removeRoutineSet(b.id)
        repo.restoreRoutineSet(entry, 9, b)
        assertEquals(listOf(a, c.copy(position = 1), b.copy(position = 2)), rows(entry))
    }

    @Test fun restoringAnInheritedSetFollowsTheCurrentRecord() = runBlocking {
        val (entry, original) = threeSets()
        val exerciseId = repo.routineExercise(entry)!!.exerciseId
        record(exerciseId, 90.0, 4)
        val c = rows(entry)[2]
        assertEquals(RoutineSet(original[2].id, entry, 2, 4, 90.0, inheritPr = true, warmup = false), c)
        repo.removeRoutineSet(c.id)
        record(exerciseId, 100.0, 5)

        // The undo holds the old snapshot (4 × 90); an inherited set comes back inherited, with today's record.
        repo.restoreRoutineSet(entry, 2, c)

        assertEquals(RoutineSet(c.id, entry, 2, 5, 100.0, inheritPr = true, warmup = false), rows(entry)[2])
        assertEquals(listOf(original[0].id, original[1].id, c.id), rows(entry).map { it.id })
    }

    @Test fun restoreRefusesInvalidValues() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        // The value rules are the ones updateRoutineSet applies.
        assertRefused(DataError.INVALID_VALUE) { repo.restoreRoutineSet(entry, 1, b.copy(targetReps = 10_000)) }
        assertRefused(DataError.INVALID_VALUE) { repo.restoreRoutineSet(entry, 1, b.copy(targetReps = -1)) }
        assertRefused(DataError.INVALID_VALUE) { repo.restoreRoutineSet(entry, 1, b.copy(targetWeightKg = -2.5)) }
        assertRefused(DataError.INVALID_VALUE) { repo.restoreRoutineSet(entry, 1, b.copy(targetWeightKg = Double.NaN)) }
        assertNull(repo.snapshot().routineSets.firstOrNull { it.id == b.id })
        repo.restoreRoutineSet(entry, 1, b)
        assertEquals(original, rows(entry))
    }

    @Test fun restoreRoutineSetTwiceRefusesTheSecond() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        repo.restoreRoutineSet(entry, 1, b)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 1, b) }
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 0, b) }
        assertEquals(original, rows(entry))
    }

    @Test fun restoreAfterTheEntryIsRemovedIsRefused() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        repo.removeRoutineExercise(entry)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSet(entry, 1, b) }
        assertNull(repo.snapshot().routineSets.firstOrNull { it.id == b.id })
    }

    @Test fun restoreRefusesTheFiftyFirstSet() = runBlocking {
        val (entry, original) = threeSets()
        val b = original[1]
        repo.removeRoutineSet(b.id)
        while (rows(entry).size < 50) repo.addRoutineSet(entry)
        // The limit is the one addRoutineSet applies, so the screen shows its text (F9).
        assertRefused(DataError.SET_LIMIT) { repo.restoreRoutineSet(entry, 1, b) }
        assertNull(repo.snapshot().routineSets.firstOrNull { it.id == b.id })
    }

    // (2026-10-31, owner's request) "Copy to all sets": one set's load and reps on every other working set.
    @Test fun copyToAllWritesTheOtherWorkingSetsOnlyAndMakesThemManual() = runBlocking {
        val (entry, original) = threeSets()
        repo.addRoutineSet(entry)
        val (a, b, c, d) = rows(entry)
        assertTrue(c.inheritPr && d.inheritPr)

        assertEquals(2, repo.copyRoutineSetToAll(b.id))

        // The warm-up and the source stay as they were; the other working sets take 5 × 100 and are manual.
        assertEquals(listOf(
            a,
            b,
            c.copy(targetReps = 5, targetWeightKg = 100.0, inheritPr = false),
            d.copy(targetReps = 5, targetWeightKg = 100.0, inheritPr = false),
        ), rows(entry))
        assertEquals(original[0], rows(entry)[0])
        assertEquals(4, repo.routineExercise(entry)!!.plannedSets)
        assertEquals(5 to 100.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
        // A source with no load copies no load.
        repo.updateRoutineSet(c.id, 12, null)
        assertEquals(2, repo.copyRoutineSetToAll(c.id))
        assertEquals(listOf(12 to null, 12 to null, 12 to null), rows(entry).drop(1).map { it.targetReps to it.targetWeightKg })
    }

    @Test fun copyToAllFromAnInheritedSourceCopiesTheValuesItShows() = runBlocking {
        val (entry, _) = threeSets()
        record(repo.routineExercise(entry)!!.exerciseId, 90.0, 4)
        val (a, b, c) = rows(entry)
        // C follows the record and shows 4 × 90.
        assertEquals(RoutineSet(c.id, entry, 2, 4, 90.0, inheritPr = true, warmup = false), c)

        assertEquals(1, repo.copyRoutineSetToAll(c.id))

        assertEquals(listOf(a, b.copy(targetReps = 4, targetWeightKg = 90.0, inheritPr = false), c), rows(entry))
    }

    @Test fun restoreRoutineSetTargetsPutsBackRepsLoadAndMode() = runBlocking {
        val (entry, _) = threeSets()
        val exerciseId = repo.routineExercise(entry)!!.exerciseId
        record(exerciseId, 90.0, 4)
        repo.addRoutineSet(entry)
        val before = rows(entry)
        val (a, b, c, d) = before
        assertTrue(c.inheritPr && d.inheritPr)
        repo.updateRoutineSet(c.id, 6, 70.0)
        val previous = rows(entry).drop(1).filter { it.id != b.id }.map { it }
        repo.copyRoutineSetToAll(b.id)

        repo.restoreRoutineSetTargets(entry, exerciseId, previous)

        // C comes back manual at 6 × 70, D inherited at the record, in one write.
        assertEquals(listOf(a, b, c.copy(targetReps = 6, targetWeightKg = 70.0, inheritPr = false), d), rows(entry))
        assertEquals(5 to 100.0, repo.routineExercise(entry)!!.let { it.targetReps to it.targetWeightKg })
        // An inherited set comes back with today's record, as restoreRoutineSet does.
        repo.copyRoutineSetToAll(b.id)
        record(exerciseId, 100.0, 6)
        repo.restoreRoutineSetTargets(entry, exerciseId, listOf(d))
        assertEquals(RoutineSet(d.id, entry, 3, 6, 100.0, inheritPr = true, warmup = false), rows(entry)[3])
    }

    @Test fun restoreRoutineSetTargetsRefusesAChangedEntry() = runBlocking {
        val (entry, original) = threeSets()
        val (_, b, c) = original
        val exerciseId = repo.routineExercise(entry)!!.exerciseId
        repo.copyRoutineSetToAll(b.id)
        // A set that is gone, one that became a warm-up, a set of another entry, a replaced exercise or an entry that is
        // gone: nothing is written.
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSetTargets(entry, exerciseId, listOf(c, c.copy(id = "gone"))) }
        repo.setRoutineSetWarmup(c.id, true)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSetTargets(entry, exerciseId, listOf(c)) }
        repo.setRoutineSetWarmup(c.id, false)
        val other = repo.addRoutineExercise(repo.routineExercise(entry)!!.routineId, exercise())
        val otherExercise = repo.routineExercise(other)!!.exerciseId
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSetTargets(other, otherExercise, listOf(c)) }
        assertRefused(DataError.INVALID_VALUE) { repo.restoreRoutineSetTargets(entry, exerciseId, listOf(c.copy(targetReps = -1, inheritPr = false))) }
        // The same entry and set ids, another exercise: the old values would land on the new movement.
        repo.replaceRoutineExercise(entry, otherExercise)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSetTargets(entry, exerciseId, listOf(c)) }
        repo.removeRoutineExercise(entry)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreRoutineSetTargets(entry, exerciseId, listOf(c)) }
    }

    @Test fun copyToAllWithNoOtherWorkingSetWritesNoSet() = runBlocking {
        val (entry, original) = threeSets()
        repo.removeRoutineSet(original[2].id)
        val before = rows(entry)

        // A warm-up and one working set: nothing to copy to.
        assertEquals(0, repo.copyRoutineSetToAll(original[1].id))

        assertEquals(before, rows(entry))
    }

    @Test fun copyToAllRefusesAMissingSourceAndAWarmup() = runBlocking {
        val (_, original) = threeSets()
        assertRefused(DataError.ROUTINE_NOT_FOUND) { repo.copyRoutineSetToAll("missing") }
        assertRefused(DataError.INVALID_VALUE) { repo.copyRoutineSetToAll(original[0].id) }
    }

    // Task 2 review M3: the screens show a routine whose folder no longer exists in the default folder, so the
    // default folder's reorder takes it as a member (it keeps its stored folderId).
    @Test fun defaultFolderReorderTakesRoutinesOfAMissingFolder() = runBlocking {
        val folder = repo.createRoutineFolder("Hypertrophy")
        val d1 = repo.createRoutine("D1")
        val a1 = repo.createRoutine("A1", folder)
        database.dao().insertRoutine(Routine("orphan", "Orphan", position = 2, folderId = "gone"))
        val d2 = repo.createRoutine("D2")

        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(RoutineFolder.DEFAULT_ID, listOf(d2, d1)) }
        repo.reorderFolderRoutines(RoutineFolder.DEFAULT_ID, listOf(d2, "orphan", d1))

        val saved = repo.snapshot().routines
        assertEquals(mapOf(d2 to 0, a1 to 1, "orphan" to 2, d1 to 3), saved.associate { it.id to it.position })
        assertEquals("gone", saved.single { it.id == "orphan" }.folderId)
        // Another folder's reorder stays exact: the orphan is not one of its routines.
        assertRefused(DataError.INVALID_VALUE) { repo.reorderFolderRoutines(folder, listOf(a1, "orphan")) }
    }

    // Task 2 review M4: the old editor's whole-exercise target edit writes the working sets only.
    @Test fun legacyTargetEditLeavesWarmupsAlone() = runBlocking {
        val (entry, original) = threeSets()
        val (a, b, c) = original

        repo.updateRoutineExercise(repo.routineExercise(entry)!!.copy(targetReps = 8, targetWeightKg = 60.0))

        val after = rows(entry)
        assertEquals(a, after[0])
        assertEquals(listOf(b.id, c.id), after.drop(1).map { it.id })
        after.drop(1).forEach {
            assertEquals(8 to 60.0, it.targetReps to it.targetWeightKg)
            assertFalse(it.inheritPr)
            assertFalse(it.warmup)
        }
    }
}
