package com.mooscles.data

import androidx.room.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mooscles.TemporaryDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS entry point on the simulator: BundledSQLiteDriver, the migrations and the shared transactions. */
class IosDatabaseTest {
    private val directory = TemporaryDirectory()
    private val opened = mutableListOf<WorkoutDatabase>()

    private fun open(): WorkoutDatabase = openWorkoutDatabase(directory.path).also { opened += it }

    @AfterTest fun tearDown() {
        opened.forEach { it.close() }
        directory.delete()
    }

    @Test fun freshDatabaseHasTheDefaultFolderAndVersionSix() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()

        val data = repository.data.first()
        val folder = data.routineFolders.single { it.id == RoutineFolder.DEFAULT_ID }
        assertEquals("My routines", folder.name)
        assertEquals(ExerciseCatalog.exercises.size, data.exercises.size)
        assertEquals(6L, database.userVersion())
        assertTrue(directory.exists(WorkoutDatabase.FILE_NAME))
    }

    /**
     * The same upgrade as `RoutineWarmupMigrationTest` on the Android host, on the SQLite that ships
     * with the iOS app: a version 5 file with the [SchemaV5] seed comes out of [openWorkoutDatabase]
     * at version 6 with every saved value unchanged and every planned set a working set.
     */
    @Test fun v5DatabaseMigratesToSixOnTheBundledDriver() = runBlocking<Unit> {
        val before = BundledSQLiteDriver().let { driver ->
            FileSystem.SYSTEM.createDirectories(directory.path.toPath())
            val raw = driver.open("${directory.path}/${WorkoutDatabase.FILE_NAME}")
            try {
                SchemaV5.statements.forEach(raw::execSQL)
                SchemaV5.seed.forEach(raw::execSQL)
                raw.execSQL("PRAGMA user_version = ${SchemaV5.VERSION}")
                SchemaV5.savedQueries.mapValues { (_, sql) -> raw.prepare(sql).use { it.typedRows() } }
            } finally {
                raw.close()
            }
        }
        assertEquals(SchemaV5.seededRows, before.mapValues { it.value.size })

        val database = open()
        val data = WorkoutRepository(database).data.first()
        assertEquals(6L, database.userVersion())
        assertEquals(before, database.savedValues())
        assertEquals(List(SchemaV5.seededRows.getValue("routine_sets")) { listOf<Any?>(0L) },
            database.rows("SELECT warmup FROM routine_sets"))
        assertTrue(data.routineSets.isNotEmpty() && data.routineSets.none { it.warmup })
        assertEquals(SchemaV5.ACTIVE_WORKOUT_ID, data.activeWorkout?.id)
        assertEquals(listOf(listOf<Any?>("ok")), database.rows("PRAGMA integrity_check"))
        assertTrue(database.rows("PRAGMA foreign_key_check").isEmpty())

        database.close()
        val reopened = open()
        assertEquals(before, reopened.savedValues())
        assertEquals(data, WorkoutRepository(reopened).data.first())
    }

    private suspend fun WorkoutDatabase.userVersion(): Long = useReaderConnection { connection ->
        connection.usePrepared("PRAGMA user_version") { check(it.step()); it.getLong(0) }
    }

    private suspend fun WorkoutDatabase.rows(sql: String): List<List<Any?>> =
        useReaderConnection { connection -> connection.usePrepared(sql) { it.typedRows() } }

    private suspend fun WorkoutDatabase.savedValues() = SchemaV5.savedQueries.mapValues { (_, sql) -> rows(sql) }

    /** Each value as its SQLite storage class, so a changed type or digit fails the comparison. */
    private fun SQLiteStatement.typedRows(): List<List<Any?>> = buildList {
        while (step()) add(List(getColumnCount()) { column ->
            when (getColumnType(column)) {
                SQLITE_DATA_NULL -> null
                SQLITE_DATA_INTEGER -> getLong(column)
                SQLITE_DATA_FLOAT -> getDouble(column)
                SQLITE_DATA_BLOB -> getBlob(column).toList()
                else -> getText(column)
            }
        })
    }

    @Test fun workoutRoundTripsAfterReopening() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val routine = repository.createRoutine("Push")
        repository.addRoutineExercise(routine, ExerciseCatalog.exercises.first().id)
        val workout = repository.startRoutine(routine, now = 1_000_000L)
        val first = repository.workoutSets(workout).minBy { it.position }
        repository.updateSet(first.copy(reps = 8, weightKg = 60.0, completed = true))
        assertTrue(repository.finishWorkout(workout, now = 1_600_000L))
        val before = repository.data.first()
        database.close()

        val after = WorkoutRepository(open()).data.first()
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(before.routines, after.routines)
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        val finished = after.workouts.single()
        assertEquals(WorkoutStatus.FINISHED, finished.status)
        assertEquals(1_600_000L, finished.endedAt)
        assertEquals(routine, finished.sourceRoutineId)
        val completed = after.sets.single { it.completed }
        assertEquals(8, completed.reps)
        assertEquals(60.0, completed.weightKg)
    }

    @Test fun failedNestedTransactionRollsBackTheOuterOne() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val before = repository.snapshot()

        val error = runCatching {
            database.writeTransaction {
                repository.createRoutine("A")
                val nested = runCatching {
                    database.writeTransaction {
                        repository.createRoutine("B")
                        error("Nested failure")
                    }
                }.exceptionOrNull()
                assertEquals("Nested failure", nested?.message)
                // The caller swallows the nested failure and returns normally.
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "$error")
        // The wording of the outer message is the common implementation's; assert on the type and
        // on the nested failure it carries as its cause instead.
        val causes = generateSequence<Throwable>(error) { it.cause }.map { it.message }.toList()
        assertTrue("Nested failure" in causes, "$causes")
        assertEquals(before, repository.snapshot())
        assertTrue(repository.snapshot().routines.none { it.name == "A" || it.name == "B" })
    }

    @Test fun nestedRepositoryCommandsJoinTheOuterTransactionAndFailAllOrNothing() = runBlocking<Unit> {
        val database = open()
        val repository = WorkoutRepository(database)
        repository.initialize()
        val before = repository.snapshot()

        val error = runCatching {
            database.writeTransaction {
                val routine = repository.createRoutine("Inside the outer transaction")
                // A nested command reads the outer, uncommitted write on the same connection.
                assertTrue(repository.snapshot().routines.any { it.id == routine })
                repository.createRoutine("Rejected", folderId = "missing-folder")
            }
        }.exceptionOrNull()

        assertEquals("Folder not found.", error?.message)
        assertEquals(before, repository.snapshot())
    }
}
