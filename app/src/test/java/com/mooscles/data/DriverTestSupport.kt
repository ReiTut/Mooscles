package com.mooscles.data

import android.database.Cursor
import androidx.room.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File
import org.json.JSONObject

/*
 * Helpers for tests that inspect the database through Room's own driver connection, which replaced
 * `openHelper` (a database with a driver has no SupportSQLite open helper).
 */

/**
 * Room's exported schema JSON for [version], in `shared/schemas`. Gradle runs `:app` tests from
 * `app/`, an IDE may run them from the repository root, so both are tried.
 */
fun exportedSchemaFile(version: Int): File {
    val path = "shared/schemas/com.mooscles.data.WorkoutDatabase/$version.json"
    return listOf(File("../$path"), File(path)).firstOrNull { it.isFile }
        ?: error("Room's exported version $version schema was not found at $path")
}

/**
 * What Room ran to create the exported [version]: each entity's `createSql` followed by its
 * indices, in the file's order, then the `setupQueries`, with `${TABLE_NAME}` resolved.
 */
fun exportedSchemaStatements(version: Int): List<String> {
    val schema = JSONObject(exportedSchemaFile(version).readText()).getJSONObject("database")
    check(schema.getInt("version") == version)
    return buildList {
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            add(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            if (indices != null) for (item in 0 until indices.length()) {
                add(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) add(setup.getString(index))
    }
}

/** The `identityHash` Room exported for [version]. */
fun exportedIdentityHash(version: Int): String =
    JSONObject(exportedSchemaFile(version).readText()).getJSONObject("database").getString("identityHash")

/** `PRAGMA user_version`, read on Room's connection. */
suspend fun WorkoutDatabase.userVersion(): Int = useReaderConnection { connection ->
    connection.usePrepared("PRAGMA user_version") { check(it.step()); it.getInt(0) }
}

/**
 * Every row of [sql], read on Room's connection, with each value kept as its SQLite storage class
 * (Long, Double, String, ByteArray as a List or null) so that equality is exact.
 */
suspend fun WorkoutDatabase.typedRows(sql: String): List<List<Any?>> = useReaderConnection { connection ->
    connection.usePrepared(sql) { statement ->
        buildList { while (statement.step()) add(List(statement.getColumnCount()) { statement.typed(it) }) }
    }
}

private fun SQLiteStatement.typed(column: Int): Any? = when (getColumnType(column)) {
    SQLITE_DATA_NULL -> null
    SQLITE_DATA_INTEGER -> getLong(column)
    SQLITE_DATA_FLOAT -> getDouble(column)
    SQLITE_DATA_BLOB -> getBlob(column).toList()
    else -> getText(column)
}

/** The same typed reading on a raw framework cursor, for fixtures written before Room opens. */
fun Cursor.typedRows(): List<List<Any?>> = buildList {
    while (moveToNext()) add(List(columnCount) { column ->
        when (getType(column)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(column)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(column)
            Cursor.FIELD_TYPE_BLOB -> getBlob(column).toList()
            else -> getString(column)
        }
    })
}

/**
 * The production [AndroidSQLiteDriver] with every SQL statement Room prepares reported to
 * [onPrepare], which replaces Room's query callback (unsupported with a driver). It keeps the
 * driver's own connection pool, so Room manages connections exactly as in the app.
 */
class RecordingDriver(private val onPrepare: (String) -> Unit) : SQLiteDriver {
    private val delegate = AndroidSQLiteDriver()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("hasConnectionPool")
    override val hasConnectionPool: Boolean get() = delegate.hasConnectionPool

    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        return object : SQLiteConnection by connection {
            override fun prepare(sql: String): SQLiteStatement = connection.prepare(sql).also { onPrepare(sql) }
        }
    }
}
