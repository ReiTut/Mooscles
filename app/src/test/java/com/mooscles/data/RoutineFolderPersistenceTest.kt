package com.mooscles.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineFolderPersistenceTest {
    private val directory = Files.createTempDirectory("mf-").toFile()
    private val context: Context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
    }
    private val filename = "folders.db"
    private var database: WorkoutDatabase? = null

    private fun open(): WorkoutRepository {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver())
            .addMigrations(WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6).build()
        return WorkoutRepository(database!!)
    }

    @After fun close() {
        database?.close()
        SQLiteDatabase.deleteDatabase(context.getDatabasePath(filename))
        directory.delete()
    }

    @Test fun schemaFourMigrationAddsDefaultMembershipWithoutChangingAnySavedValues() = runBlocking {
        val before = createSchemaFourFixture()
        var repo = open()
        val migrated = repo.snapshot()
        assertEquals(6, database!!.userVersion())
        assertEquals(listOf(RoutineFolder(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)), migrated.routineFolders)
        assertEquals(listOf("r-second", "r-first"), migrated.routines.map { it.id })
        assertTrue(migrated.routines.all { it.folderId == RoutineFolder.DEFAULT_ID })
        assertEquals("active", migrated.activeWorkout!!.id)
        assertEquals("r-first", migrated.activeWorkout!!.sourceRoutineId)
        assertEquals(before, readSavedValues(database!!))
        assertEquals(listOf(listOf<Any?>(3L, 0L)), database!!.typedRows("SELECT COUNT(*), SUM(warmup) FROM routine_sets"))
        assertTrue(database!!.typedRows("PRAGMA foreign_key_check").isEmpty())

        // Reopening is a second real database connection, not another read of cached UI state.
        database!!.close()
        repo = open()
        assertEquals(migrated, repo.snapshot())
        assertEquals(before, readSavedValues(database!!))
    }

    @Test fun movingAndRenamingPersistWithoutChangingPrescriptionsOrActiveAndFinishedSnapshots() = runBlocking {
        var repo = open()
        repo.initialize()
        val upperLower = repo.createRoutineFolder("U/L")
        val fullBody = repo.createRoutineFolder("FULLBODY")
        val source = repo.createRoutine("Upper", upperLower)
        val other = repo.createRoutine("Full body", fullBody)
        val exercise = repo.createCustomExercise("Press", MuscleGroup.CHEST, Equipment.DUMBBELL, LoadConvention.PER_HAND)
        val plan = repo.addRoutineExercise(source, exercise)
        repo.addRoutineSet(plan)
        repo.snapshot().plannedSets(plan).forEachIndexed { index, row ->
            repo.updateRoutineSet(row.id, 8 - index, 17.25 - index)
        }
        repo.updateRoutineExerciseRest(plan, 155)
        val history = repo.startRoutine(source, now = 1000)
        val historyEntry = repo.snapshot().workoutItems(history).single()
        val completed = repo.snapshot().exerciseSets(historyEntry.id).first()
        repo.updateSet(completed.copy(completed = true))
        repo.finishWorkout(history, now = 2000)
        val active = repo.startRoutine(source, now = 3000)
        val before = repo.snapshot()

        val observedMove = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { repo.data.first { data -> data.routines.any { it.id == source && it.folderId == fullBody } } }
        }
        repo.moveRoutineToFolder(source, fullBody)
        repo.renameRoutineFolder(fullBody, "Full body split")
        val after = repo.snapshot()
        assertEquals(source, observedMove.await().routines.single { it.id == source }.id)
        assertEquals(before.routines.single { it.id == source }.copy(folderId = fullBody), after.routines.single { it.id == source })
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(active, repo.startRoutine(other, now = 9000))
        assertEquals(source, repo.snapshot().activeWorkout!!.sourceRoutineId)
        assertTrue(after.routines.none { it.folderId == upperLower })
        assertEquals(setOf(source, other), after.routines.filter { it.folderId == fullBody }.map { it.id }.toSet())
        val copied = repo.duplicateRoutine(source)
        assertEquals(fullBody, repo.snapshot().routines.single { it.id == copied }.folderId)
        val expected = repo.snapshot()

        database!!.close()
        repo = open()
        repo.initialize()
        assertEquals(expected.routines, repo.snapshot().routines)
        assertEquals(expected.routineFolders, repo.snapshot().routineFolders)
        assertEquals(expected.routineSets, repo.snapshot().routineSets)
        assertEquals(before.workouts, repo.snapshot().workouts)
        assertEquals(before.workoutExercises, repo.snapshot().workoutExercises)
        assertEquals(before.sets, repo.snapshot().sets)
        assertEquals(active, repo.snapshot().activeWorkout!!.id)
    }

    @Test fun invalidMembershipAndBlankNamesFailAtomicallyAndDefaultFolderRenameSurvivesInitialize() = runBlocking {
        val repo = open()
        val routine = repo.createRoutine("Default")
        repo.renameRoutineFolder(RoutineFolder.DEFAULT_ID, "My saved split")
        repo.initialize()
        assertEquals("My saved split", repo.snapshot().routineFolders.single().name)
        val before = repo.snapshot()
        for (operation in listOf<suspend () -> Unit>(
            { repo.createRoutine("Bad", "missing"); Unit },
            { repo.moveRoutineToFolder(routine, "missing") },
            { repo.createRoutineFolder("   "); Unit },
            { repo.renameRoutineFolder(RoutineFolder.DEFAULT_ID, "") },
            { repo.moveRoutineToFolder("missing-routine", RoutineFolder.DEFAULT_ID) }
        )) {
            try { operation(); fail("Invalid folder edit must not be accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.snapshot())
        }
    }

    private fun createSchemaFourFixture(): Map<String, List<List<Any?>>> {
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("rollback/v142-room-schema4.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val path = context.getDatabasePath(filename)
        path.parentFile!!.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (item in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) raw.execSQL(setup.getString(index))
            raw.execSQL("INSERT INTO exercises VALUES ('press', 'Saved press', 'CHEST', 'DUMBBELL', 'PER_HAND', 1)")
            raw.execSQL("INSERT INTO routines VALUES ('r-first', 'Duplicate name', 1), ('r-second', 'Duplicate name', 0)")
            raw.execSQL("INSERT INTO routine_exercises VALUES ('plan', 'r-first', 'press', 0, 3, 7, 20.375, 155)")
            raw.execSQL("INSERT INTO routine_sets VALUES ('custom', 'plan', 0, 7, 20.375, 0), ('blank', 'plan', 1, 0, NULL, 0), ('inherited', 'plan', 2, 6, 22.25, 1)")
            raw.execSQL("INSERT INTO workouts VALUES ('history', 'Saved history', 1000, 2000, 'FINISHED', 'r-first'), ('active', 'Saved active', 3000, NULL, 'ACTIVE', 'r-first')")
            raw.execSQL("INSERT INTO workout_exercises VALUES ('history-entry', 'history', 'press', 'Historical name', 'CHEST', 'DUMBBELL', 'PER_HAND', 0, 95), ('active-entry', 'active', 'press', 'Active name', 'CHEST', 'DUMBBELL', 'PER_HAND', 0, 155)")
            raw.execSQL("INSERT INTO workout_sets VALUES ('finished', 'history-entry', 0, 6, 22.25, 1, 0, 0), ('warmup', 'history-entry', 1, 3, 30.25, 1, 1, 0), ('working', 'active-entry', 0, 7, 20.375, 1, 0, 0), ('unfinished', 'active-entry', 1, 0, 0.0, 0, 0, 0)")
            raw.execSQL("INSERT INTO routine_target_checkpoints VALUES ('press', 'PER_HAND', 22.25, 6)")
            raw.version = 4
            savedQueries.mapValues { (_, sql) -> raw.rawQuery(sql, null).use { it.typedRows() } }
        }
    }

    /** Values keep their SQLite storage class, so a changed type or digit fails the comparison. */
    private suspend fun readSavedValues(db: WorkoutDatabase) = savedQueries.mapValues { (_, sql) -> db.typedRows(sql) }

    private val savedQueries = listOf("exercises", "routines", "routine_exercises", "routine_sets", "workouts", "workout_exercises", "workout_sets", "routine_target_checkpoints")
        .associateWith { table ->
            if (table == "routines") "SELECT id, name, position FROM routines ORDER BY id"
            // Version 4's columns: version 6 adds warmup, checked on its own.
            else if (table == "routine_sets") "SELECT id, routineExerciseId, position, targetReps, targetWeightKg, inheritPr FROM routine_sets ORDER BY 1"
            else "SELECT * FROM `$table` ORDER BY 1"
        }
}
