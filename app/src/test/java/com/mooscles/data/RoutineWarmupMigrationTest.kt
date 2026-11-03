package com.mooscles.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 6 adds `routine_sets.warmup`. A phone on version 5 (the [SchemaV5] seed, built from the
 * exported `5.json`) is opened the way the app opens it, through [buildMooscles]: every saved value
 * must come out unchanged, with every planned set a working set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineWarmupMigrationTest {
    private val directory = Files.createTempDirectory("rw-").toFile()
    private val context: Context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
    }
    private val opened = mutableListOf<WorkoutDatabase>()

    @After fun cleanUp() {
        opened.forEach { it.close() }
        directory.deleteRecursively()
    }

    private fun open(name: String): WorkoutDatabase =
        Room.databaseBuilder(context, WorkoutDatabase::class.java, name).buildMooscles(AndroidSQLiteDriver())
            .also { opened += it }

    /** A version 5 file with the [SchemaV5] seed; returns its saved values as written. */
    private fun createVersionFive(name: String): Map<String, List<List<Any?>>> {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            exportedSchemaStatements(SchemaV5.VERSION).forEach(raw::execSQL)
            SchemaV5.seed.forEach(raw::execSQL)
            raw.version = SchemaV5.VERSION
            SchemaV5.savedQueries.mapValues { (_, sql) -> raw.rawQuery(sql, null).use { it.typedRows() } }
        }
    }

    private suspend fun WorkoutDatabase.savedValues() = SchemaV5.savedQueries.mapValues { (_, sql) -> typedRows(sql) }

    @Test fun v5DatabaseMigratesWithEveryRowIntactAndWarmupZero() = runBlocking {
        val before = createVersionFive("v5.db")
        assertEquals(SchemaV5.seededRows, before.mapValues { it.value.size })

        val database = open("v5.db")
        val data = WorkoutRepository(database).data.first()
        assertEquals(6, database.userVersion())
        assertEquals(listOf(listOf<Any?>(exportedIdentityHash(6))),
            database.typedRows("SELECT identity_hash FROM room_master_table WHERE id = 42"))
        assertEquals(before, database.savedValues())
        assertEquals(List(SchemaV5.seededRows.getValue("routine_sets")) { listOf<Any?>(0L) },
            database.typedRows("SELECT warmup FROM routine_sets"))
        assertTrue(data.routineSets.isNotEmpty() && data.routineSets.none { it.warmup })
        assertEquals(SchemaV5.ACTIVE_WORKOUT_ID, data.activeWorkout?.id)
        assertEquals("v5-pull", data.activeWorkout?.sourceRoutineId)
        assertTrue(database.typedRows("PRAGMA foreign_key_check").isEmpty())
        assertEquals(listOf(listOf<Any?>("ok")), database.typedRows("PRAGMA integrity_check"))

        // Reopening is a second real connection on the migrated file, not a cached read.
        database.close()
        val reopened = open("v5.db")
        assertEquals(6, reopened.userVersion())
        assertEquals(before, reopened.savedValues())
        assertEquals(data, WorkoutRepository(reopened).data.first())
    }

    /**
     * A new install gets the column from the entity, an upgrade from the ALTER. Both must leave the
     * same schema behind, read by SQLite itself and as text: SQLite writes an added column's
     * definition after the last column, before the table constraints, so the stored CREATE text
     * matches the one Room generates.
     */
    @Test fun freshInstallAndMigrationReachTheSameSchema() = runBlocking {
        createVersionFive("migrated.db")
        val migrated = open("migrated.db")
        val fresh = open("fresh.db")
        assertEquals(6, migrated.userVersion())
        assertEquals(6, fresh.userVersion())

        val master = "SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name"
        assertEquals(fresh.typedRows(master), migrated.typedRows(master))
        val tables = fresh.typedRows("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").map { it.single() as String }
        assertTrue("routine_sets" in tables)
        for (table in tables) {
            for (pragma in listOf("table_info", "foreign_key_list", "index_list")) {
                assertEquals("$pragma($table)", fresh.typedRows("PRAGMA $pragma(`$table`)"), migrated.typedRows("PRAGMA $pragma(`$table`)"))
            }
            for (index in fresh.typedRows("PRAGMA index_list(`$table`)").map { it[1] as String }) {
                assertEquals("index_info($index)", fresh.typedRows("PRAGMA index_info(`$index`)"), migrated.typedRows("PRAGMA index_info(`$index`)"))
            }
        }
        assertEquals(listOf(listOf<Any?>(6L, "warmup", "INTEGER", 1L, "0", 0L)),
            migrated.typedRows("PRAGMA table_info(`routine_sets`)").filter { it[1] == "warmup" })
    }

    @Test fun warmupRoundTripsThroughTheEntity() = runBlocking {
        createVersionFive("v5.db")
        val database = open("v5.db")
        val planned = database.dao().routineSet("v5-custom")!!
        database.dao().updateRoutineSet(planned.copy(warmup = true))
        database.dao().insertRoutineSet(RoutineSet("v5-new-warmup", "v5-full-press", position = 1, targetReps = 5,
            targetWeightKg = 10.0, inheritPr = false, warmup = true))
        database.close()

        val reopened = open("v5.db")
        assertEquals(planned.copy(warmup = true), reopened.dao().routineSet("v5-custom"))
        assertEquals(true, reopened.dao().routineSet("v5-new-warmup")?.warmup)
        assertEquals(listOf(listOf<Any?>("v5-custom", 1L), listOf<Any?>("v5-new-warmup", 1L)),
            reopened.typedRows("SELECT id, warmup FROM routine_sets WHERE warmup != 0 ORDER BY id"))
        // A row written without the column (an older insert path) reads as a working set.
        reopened.useWriterConnection { it.execSQL("INSERT INTO routine_sets (id, routineExerciseId, position, targetReps, targetWeightKg, inheritPr) VALUES ('v5-raw', 'v5-full-press', 2, 3, NULL, 0)") }
        assertEquals(false, reopened.dao().routineSet("v5-raw")?.warmup)
    }
}
