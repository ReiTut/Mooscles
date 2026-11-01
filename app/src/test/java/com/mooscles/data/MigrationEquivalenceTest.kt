package com.mooscles.data

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real installed databases must come out of every upgrade path exactly as before the move from
 * SupportSQLite to Room's SQLite driver API.
 *
 * Each case builds an old database from that version's exported schema JSON, fills it with the
 * fixtures of the existing migration tests (plus edge rows for the 2 -> 3 row copy), opens it
 * through the production [WorkoutDatabase.open], and compares a full dump (file header journal
 * mode, user_version, sqlite_master and every row of every table, typed) with a reference.
 *
 * The references in `src/test/resources/migration-equivalence/` were generated once, before the
 * switch, against the SupportSQLite migrations (Room 2.7.2): commit 3eed409 added this test with a
 * `MOOSCLES_WRITE_MIGRATION_REFERENCE=<directory>` switch that wrote the dumps, and they were
 * committed with it. The switch was then removed: the references are the old behavior the driver
 * code is checked against and must never be regenerated from it.
 *
 * Version 6 (2026-10-30) only adds `routine_sets.warmup`. The references stay as they were: the
 * comparison takes the version 6 additions out of the dump ([withoutVersionSixAdditions]), and
 * [v6AdditionsAreOnlyTheWarmupColumnAtZero] proves that those additions are all there is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationEquivalenceTest {
    private val directory = Files.createTempDirectory("me-").toFile()
    private val context: Context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
    }
    private val file get() = context.getDatabasePath("mooscles.db")

    @After fun cleanUp() {
        SQLiteDatabase.deleteDatabase(file)
        directory.deleteRecursively()
    }

    @Test fun migrationsProduceTheSameDatabaseAsBefore() {
        val drivers = (1..4).map { version ->
            createLegacyDatabase(version)
            val driver = openAndClose()
            assertMatchesReference("from-v$version.txt", withoutVersionSixAdditions(dump(generatedRoutineSetIds = version <= 2)))
            SQLiteDatabase.deleteDatabase(file)
            driver
        }
        assertTrue("Every upgrade must run on the SQLite driver", drivers.all { it })
    }

    @Test fun freshInstallProducesTheSameDatabaseAsBefore() {
        val driver = openAndClose()
        assertMatchesReference("fresh-install.txt", withoutVersionSixAdditions(dump(generatedRoutineSetIds = false)))
        assertTrue("A fresh install must run on the SQLite driver", driver)
    }

    /**
     * The other direction, exactly: each reference with the version 6 additions put in is the whole
     * dump. Nothing else changed, and every planned set that a real upgrade carries is a working set.
     */
    @Test fun v6AdditionsAreOnlyTheWarmupColumnAtZero() {
        val cases = (1..4).map { "from-v$it.txt" to it } + ("fresh-install.txt" to null)
        for ((name, version) in cases) {
            if (version != null) createLegacyDatabase(version)
            openAndClose()
            val actual = dump(generatedRoutineSetIds = version != null && version <= 2)
            assertEquals("$name with the version 6 additions", withVersionSixAdditions(reference(name)), actual)
            SQLiteDatabase.deleteDatabase(file)
        }
    }

    private val hashFive by lazy { exportedIdentityHash(5) }
    private val hashSix by lazy { exportedIdentityHash(6) }

    /** Reference -> version 6: user_version, Room's identity hash, the column definition and a zero in each planned set. */
    private fun withVersionSixAdditions(reference: String): String = editDump(reference,
        userVersion = "5" to "6", hash = hashFive to hashSix,
        column = { it.replace(WITHOUT_WARMUP, WITH_WARMUP) },
        plannedSet = { "$it$WARMUP_ZERO" })

    /** Version 6 dump -> what the reference recorded. Only a planned set whose warmup is 0 loses it. */
    private fun withoutVersionSixAdditions(dump: String): String = editDump(dump,
        userVersion = "6" to "5", hash = hashSix to hashFive,
        column = { it.replace(WITH_WARMUP, WITHOUT_WARMUP) },
        plannedSet = { it.removeSuffix(WARMUP_ZERO) })

    /**
     * Applies each edit where it belongs and nowhere else, and fails if a place it expects is
     * missing, so a dump of another shape cannot pass by being left untouched.
     */
    private fun editDump(
        text: String, userVersion: Pair<String, String>, hash: Pair<String, String>,
        column: (String) -> String, plannedSet: (String) -> String,
    ): String {
        val out = mutableListOf<String>()
        // Planned set rows are collected and sorted again: a changed suffix can change their order.
        val plannedSets = mutableListOf<String>()
        var section = ""
        var edits = listOf(0, 0, 0)
        for (line in text.lines()) {
            if (line.startsWith("== ")) {
                out += plannedSets.sorted().also { plannedSets.clear() }
                section = line.removePrefix("== ")
                out += line
                continue
            }
            when {
                line == "user_version: ${userVersion.first}" -> {
                    out += "user_version: ${userVersion.second}"
                    edits = listOf(edits[0] + 1, edits[1], edits[2])
                }
                section == "sqlite_master" && line.startsWith("type=text:table | name=text:routine_sets |") -> {
                    out += column(line).also { check(it != line) { "routine_sets definition not as expected: $line" } }
                    edits = listOf(edits[0], edits[1] + 1, edits[2])
                }
                section == "room_master_table" && line == "id=integer:42 | identity_hash=text:${hash.first}" -> {
                    out += "id=integer:42 | identity_hash=text:${hash.second}"
                    edits = listOf(edits[0], edits[1], edits[2] + 1)
                }
                section == "routine_sets" && line.isNotEmpty() -> plannedSets += plannedSet(line)
                else -> out += line
            }
        }
        check(plannedSets.isEmpty()) { "routine_sets is not the last table of the dump" }
        assertEquals("user_version, routine_sets definition and identity hash, edited once each", listOf(1, 1, 1), edits)
        return out.joinToString("\n")
    }

    private fun reference(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("migration-equivalence/$name")
            ?.bufferedReader()?.use { it.readText() } ?: error("Missing reference $name")

    /**
     * Opening runs the migrations or the creation callback; closing flushes WAL into the file.
     * Returns whether the database ran on a SQLite driver: with one, Room has no SupportSQLite
     * open helper at all.
     */
    private fun openAndClose(): Boolean {
        val database = WorkoutDatabase.open(context)
        try {
            runBlocking { database.dao().routineFolders() }
            return runCatching { database.openHelper }.isFailure
        } finally {
            database.close()
        }
    }

    private fun assertMatchesReference(name: String, actual: String) {
        val expected = reference(name)
        assertEquals("Database produced by $name differs from the pre-driver reference", expected, actual)
    }

    /**
     * Rows are rendered with their SQLite storage class and sorted, so the dump does not depend on
     * physical row order. Routine set IDs created by MIGRATION_2_3 are random UUIDs: they are
     * checked for shape and uniqueness, then masked.
     */
    private fun dump(generatedRoutineSetIds: Boolean): String = buildString {
        val header = file.readBytes()
        appendLine("file format write/read version: ${header[18]}/${header[19]}")
        val db = SQLiteDatabase.openDatabase(file.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        db.use {
            appendLine("user_version: ${db.version}")
            appendLine("integrity_check: ${db.rows("PRAGMA integrity_check").single()}")
            appendLine("foreign_key_check: ${db.rows("PRAGMA foreign_key_check")}")
            appendLine("== sqlite_master")
            db.rows("SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name").forEach { appendLine(it) }
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", null)
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            for (table in tables) {
                appendLine("== $table")
                var rows = db.rows("SELECT * FROM `$table`")
                if (table == "routine_sets" && generatedRoutineSetIds) {
                    val ids = rows.map { it.substringBefore(" | ").removePrefix("id=text:") }
                    assertTrue(ids.all { Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(it) })
                    assertEquals(ids.size, ids.toSet().size)
                    rows = rows.map { "id=<generated uuid> | " + it.substringAfter(" | ") }
                }
                rows.sorted().forEach { appendLine(it) }
            }
        }
    }

    private fun SQLiteDatabase.rows(sql: String): List<String> = rawQuery(sql, null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString(" | ") { column ->
                "${cursor.getColumnName(column)}=${cursor.typed(column)}"
            })
        }
    }

    private fun Cursor.typed(column: Int): String = when (getType(column)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_INTEGER -> "integer:${getLong(column)}"
        // Double.toString is the shortest exact round-trip form, so no precision is hidden.
        Cursor.FIELD_TYPE_FLOAT -> "real:${getDouble(column)}"
        Cursor.FIELD_TYPE_STRING -> "text:${getString(column)}"
        else -> "blob:${getBlob(column).joinToString(",")}"
    }

    private fun createLegacyDatabase(version: Int) {
        val schema = JSONObject(exportedSchemaFile(version).readText()).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
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
            fixture(version).forEach(raw::execSQL)
            raw.version = version
        }
    }

    /** The fixtures of RoutineTargetMigrationTest (v1-v3) and RoutineFolderPersistenceTest (v4). */
    private fun fixture(version: Int): List<String> = when (version) {
        1 -> listOf(
            "INSERT INTO exercises VALUES ('legacy-bench', 'Barbell press', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 1), ('legacy-cable', 'Cable press', 'CHEST', 'CABLE', 'TOTAL_LOAD', 1)",
            "INSERT INTO routines VALUES ('legacy-push', 'Push', 0), ('legacy-full-body', 'Full body', 1)",
            "INSERT INTO routine_exercises VALUES ('legacy-bench-plan', 'legacy-push', 'legacy-bench', 0, 4, 7, 20.0, 135), ('legacy-cable-plan', 'legacy-push', 'legacy-cable', 1, 3, 10, 15.0, 90), ('legacy-other-bench-plan', 'legacy-full-body', 'legacy-bench', 0, 2, 8, 18.0, NULL)",
            "INSERT INTO workouts VALUES ('legacy-finished', 'Original completed workout', 1000, 2000, 'FINISHED'), ('legacy-active', 'In-progress workout', 3000, NULL, 'ACTIVE')",
            "INSERT INTO workout_exercises VALUES ('legacy-history-entry', 'legacy-finished', 'legacy-bench', 'Original snapshot name', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 0, 90), ('legacy-active-entry', 'legacy-active', 'legacy-bench', 'In-progress snapshot name', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 0, 135)",
            "INSERT INTO workout_sets VALUES ('legacy-record-set', 'legacy-history-entry', 0, 6, 22.0, 1, 0, 0), ('legacy-lower-set', 'legacy-history-entry', 1, 10, 20.0, 1, 0, 0), ('legacy-warmup-set', 'legacy-history-entry', 2, 3, 30.0, 1, 1, 0), ('legacy-active-completed-set', 'legacy-active-entry', 0, 4, 40.0, 1, 0, 0), ('legacy-active-unfinished-set', 'legacy-active-entry', 1, 7, 20.0, 0, 0, 0)",
        ) + edgeRows
        2 -> listOf(
            "INSERT INTO exercises VALUES ('v2-exercise', 'Press', 'CHEST', 'DUMBBELL', 'PER_HAND', 1)",
            "INSERT INTO routines VALUES ('v2-routine', 'Original routine', 0)",
            "INSERT INTO routine_exercises VALUES ('v2-weighted', 'v2-routine', 'v2-exercise', 0, 4, 7, 20.375, 135), ('v2-blank', 'v2-routine', 'v2-exercise', 1, 3, 0, NULL, NULL)",
            "INSERT INTO routine_target_checkpoints VALUES ('v2-exercise', 'PER_HAND', 20.375, 7)",
        ) + edgeRows
        3 -> listOf(
            "INSERT INTO exercises VALUES ('v3-press', 'Saved custom press', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 1)",
            "INSERT INTO routines VALUES ('v3-routine-first', 'Same name', 1), ('v3-routine-second', 'Same name', 0)",
            "INSERT INTO routine_exercises VALUES ('v3-plan', 'v3-routine-first', 'v3-press', 0, 2, 8, 17.25, 135)",
            "INSERT INTO routine_sets VALUES ('v3-row-first', 'v3-plan', 0, 8, 17.25, 0), ('v3-row-second', 'v3-plan', 1, 0, NULL, 1)",
            "INSERT INTO workouts VALUES ('v3-active', 'Same name', 3000, NULL, 'ACTIVE'), ('v3-finished', 'Same name', 1000, 2000, 'FINISHED')",
            "INSERT INTO workout_exercises VALUES ('v3-active-entry', 'v3-active', 'v3-press', 'Unchanged active snapshot', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 0, 155), ('v3-history-entry', 'v3-finished', 'v3-press', 'Unchanged historical snapshot', 'CHEST', 'BARBELL', 'TOTAL_LOAD', 0, 95)",
            "INSERT INTO workout_sets VALUES ('v3-active-completed', 'v3-active-entry', 0, 8, 17.25, 1, 0, 0), ('v3-active-unfinished', 'v3-active-entry', 1, 7, 18.0, 0, 0, 0), ('v3-history-working', 'v3-history-entry', 0, 7, 17.25, 1, 0, 0), ('v3-history-warmup', 'v3-history-entry', 1, 4, 10.0, 1, 1, 0)",
            "INSERT INTO routine_target_checkpoints VALUES ('v3-press', 'TOTAL_LOAD', 17.25, 8)",
        ) + edgeRows
        4 -> listOf(
            "INSERT INTO exercises VALUES ('press', 'Saved press', 'CHEST', 'DUMBBELL', 'PER_HAND', 1)",
            "INSERT INTO routines VALUES ('r-first', 'Duplicate name', 1), ('r-second', 'Duplicate name', 0)",
            "INSERT INTO routine_exercises VALUES ('plan', 'r-first', 'press', 0, 3, 7, 20.375, 155)",
            "INSERT INTO routine_sets VALUES ('custom', 'plan', 0, 7, 20.375, 0), ('blank', 'plan', 1, 0, NULL, 0), ('inherited', 'plan', 2, 6, 22.25, 1)",
            "INSERT INTO workouts VALUES ('history', 'Saved history', 1000, 2000, 'FINISHED', 'r-first'), ('active', 'Saved active', 3000, NULL, 'ACTIVE', 'r-first')",
            "INSERT INTO workout_exercises VALUES ('history-entry', 'history', 'press', 'Historical name', 'CHEST', 'DUMBBELL', 'PER_HAND', 0, 95), ('active-entry', 'active', 'press', 'Active name', 'CHEST', 'DUMBBELL', 'PER_HAND', 0, 155)",
            "INSERT INTO workout_sets VALUES ('finished', 'history-entry', 0, 6, 22.25, 1, 0, 0), ('warmup', 'history-entry', 1, 3, 30.25, 1, 1, 0), ('working', 'active-entry', 0, 7, 20.375, 1, 0, 0), ('unfinished', 'active-entry', 1, 0, 0.0, 0, 0, 0)",
            "INSERT INTO routine_target_checkpoints VALUES ('press', 'PER_HAND', 22.25, 6)",
        ) + edgeRows
        else -> error("No fixture for version $version")
    }

    private companion object {
        const val WITHOUT_WARMUP = "`inheritPr` INTEGER NOT NULL, PRIMARY KEY(`id`)"
        const val WITH_WARMUP = "`inheritPr` INTEGER NOT NULL, `warmup` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`)"
        const val WARMUP_ZERO = " | warmup=integer:0"
    }

    /**
     * Columns common to every version: zero and negative planned set counts (2 -> 3 copies none),
     * a null rest, an imprecise lb-derived weight, and text that needs quoting and Unicode.
     */
    private val edgeRows = listOf(
        "INSERT INTO exercises VALUES ('edge-exercise', 'Côte d''Azur “row” 💪', 'BACK', 'CABLE', 'TOTAL_LOAD', 1)",
        "INSERT INTO routines VALUES ('edge-routine', 'Edge ''routine''', 7)",
        "INSERT INTO routine_exercises VALUES ('edge-zero', 'edge-routine', 'edge-exercise', 0, 0, 5, NULL, 0), ('edge-negative', 'edge-routine', 'edge-exercise', 1, -2, 5, 1.5, NULL), ('edge-precise', 'edge-routine', 'edge-exercise', 2, 1, 6, 22.6796185, 300)",
    )
}
