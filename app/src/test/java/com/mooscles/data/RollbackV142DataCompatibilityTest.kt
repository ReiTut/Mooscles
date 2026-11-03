package com.mooscles.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.domain.RoutineTargetRecords
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.WorkoutCalculations
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

/** Host SQLite transition test, not a claim that Android's package installer was exercised. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RollbackV142DataCompatibilityTest {
    private val directory = Files.createTempDirectory("mr-db-").toFile()
    // Windows native SQLite needs a short path independent of the descriptive test method name.
    private val context: Context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
        }
    }
    private val filename = "rb-${newId().take(8)}.db"
    private var database: WorkoutDatabase? = null
    private val preciseKg = WeightUnits.lbToKg(50.0)
    private val fixture = fixture()
    private val checkpoints = listOf(
        RoutineTargetCheckpoint("barbell", LoadConvention.TOTAL_LOAD, preciseKg, 6),
        RoutineTargetCheckpoint("cable", LoadConvention.TOTAL_LOAD, 17.375, 9),
        RoutineTargetCheckpoint("pullup", LoadConvention.BODYWEIGHT, null, 11),
    )

    @After fun close() {
        database?.close()
        SQLiteDatabase.deleteDatabase(context.getDatabasePath(filename))
        directory.delete()
    }

    @Test fun distributedV142SchemaOpensWithoutDowngradeAndPreservesAllSnapshotsAndTargets() = runBlocking {
        createDistributedV142Database()
        val repo = open()
        assertFixtureEquals(fixture, repo.snapshot())
        assertEquals(checkpoints.sortedBy { it.exerciseId }, database!!.dao().targetCheckpoints().sortedBy { it.exerciseId })
        val initialRecords = WorkoutCalculations.records(repo.snapshot())
        val initialReps = WorkoutCalculations.repsRecords(repo.snapshot())
        val initialTargets = RoutineTargetRecords.latest(repo.snapshot())
        assertEquals(preciseKg, initialRecords.single { it.exerciseId == "barbell" }.weightKg, 0.0)
        assertEquals(6, initialRecords.single { it.exerciseId == "barbell" }.reps)

        repo.initialize()
        assertFixtureEquals(fixture, repo.snapshot())
        database!!.close()
        val reopened = open()
        reopened.initialize()
        reopened.initialize()
        val actual = reopened.snapshot()
        assertFixtureEquals(fixture, actual)
        assertEquals(initialRecords, WorkoutCalculations.records(actual))
        assertEquals(initialReps, WorkoutCalculations.repsRecords(actual))
        assertEquals(initialTargets, RoutineTargetRecords.latest(actual))
        assertEquals(checkpoints.sortedBy { it.exerciseId }, database!!.dao().targetCheckpoints().sortedBy { it.exerciseId })
        assertEquals("routine", actual.activeWorkout!!.sourceRoutineId)
        assertEquals(listOf("active-repeat", "active-cable", "active-first"), actual.workoutItems("active").map { it.id })
        // The existing active session is resumed, without copying today's plan over its saved data.
        assertEquals("active", reopened.startRoutine("routine", now = 9_000))
        assertFixtureEquals(fixture, reopened.snapshot())
    }

    @Test fun futureWorkoutUsesV142SavedOrderAndIndependentTargetsAfterRestoredRepositoryReopens() = runBlocking {
        createDistributedV142Database()
        open().initialize()
        database!!.close()
        val repo = open()
        val before = repo.snapshot()
        // Explicitly discard only this test's active fixture so a future workout can be started.
        repo.discardWorkout("active")
        val next = repo.startRoutine("routine", now = 20_000)
        val actual = repo.snapshot()
        val plan = fixture.routineItems("routine")
        val entries = actual.workoutItems(next)
        assertEquals(listOf("barbell", "cable", "barbell", "pullup"), entries.map { it.exerciseId })
        assertEquals(listOf(65, 175, 135, 120), entries.map { it.restSeconds })
        assertEquals("routine", actual.activeWorkout!!.sourceRoutineId)
        assertEquals(before.routineExercises, actual.routineExercises)
        assertEquals(before.routineSets, actual.routineSets)
        entries.zip(plan).forEach { (entry, prescription) ->
            val expected = fixture.plannedSets(prescription.id)
            val sets = actual.exerciseSets(entry.id)
            assertEquals(expected.map { it.targetReps }, sets.map { it.reps })
            assertEquals(expected.map { it.targetWeightKg ?: 0.0 }, sets.map { it.weightKg })
            assertTrue(sets.none { it.completed || it.warmup || it.assistance })
        }
        assertEquals(fixture.workouts.filter { it.status == WorkoutStatus.FINISHED },
            actual.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedBy { it.startedAt })
        val historyIds = fixture.workoutExercises.filter { it.workoutId != "active" }.map { it.id }.toSet()
        assertEquals(fixture.workoutExercises.filter { it.id in historyIds }.sortedBy { it.id },
            actual.workoutExercises.filter { it.id in historyIds }.sortedBy { it.id })
        assertEquals(fixture.sets.filter { it.workoutExerciseId in historyIds }.sortedBy { it.id },
            actual.sets.filter { it.workoutExerciseId in historyIds }.sortedBy { it.id })
        database!!.close()
        assertEquals(actual, open().snapshot())
    }

    private fun open(): WorkoutRepository = runBlocking {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver())
            .addMigrations(WorkoutDatabase.MIGRATION_1_2, WorkoutDatabase.MIGRATION_2_3, WorkoutDatabase.MIGRATION_3_4, WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6)
            .build()
        assertEquals(6, database!!.userVersion())
        assertEquals(listOf(listOf<Any?>(exportedIdentityHash(6))),
            database!!.typedRows("SELECT identity_hash FROM room_master_table WHERE id = 42"))
        WorkoutRepository(database!!)
    }

    private fun assertFixtureEquals(expected: AppData, actual: AppData) {
        val ids = expected.exercises.map { it.id }.toSet()
        assertEquals(expected.exercises.sortedBy { it.id }, actual.exercises.filter { it.id in ids }.sortedBy { it.id })
        assertEquals(expected.routines.sortedBy { it.id }, actual.routines.sortedBy { it.id })
        assertEquals(expected.routineExercises.sortedBy { it.id }, actual.routineExercises.sortedBy { it.id })
        assertEquals(expected.routineSets.sortedBy { it.id }, actual.routineSets.sortedBy { it.id })
        assertEquals(expected.workouts.sortedBy { it.id }, actual.workouts.sortedBy { it.id })
        assertEquals(expected.workoutExercises.sortedBy { it.id }, actual.workoutExercises.sortedBy { it.id })
        assertEquals(expected.sets.sortedBy { it.id }, actual.sets.sortedBy { it.id })
    }

    private fun fixture(): AppData {
        val barbell = Exercise("barbell", "Repeated press", MuscleGroup.CHEST, Equipment.BARBELL, custom = true)
        val cable = barbell.copy(id = "cable", equipment = Equipment.CABLE)
        val pullup = Exercise("pullup", "Pull up", MuscleGroup.BACK, Equipment.BODYWEIGHT, LoadConvention.BODYWEIGHT, true)
        val routine = Routine("routine", "Saved reordered routine")
        val repeat = RoutineExercise("plan-repeat", routine.id, barbell.id, 0, 3, 6, preciseKg, 65)
        val cablePlan = RoutineExercise("plan-cable", routine.id, cable.id, 1, 2, 9, 17.375, 175)
        val first = RoutineExercise("plan-first", routine.id, barbell.id, 2, 2, 7, 20.375, 135)
        val body = RoutineExercise("plan-body", routine.id, pullup.id, 3, 1, 11, null, null)
        val planned = listOf(
            RoutineSet("repeat-inherited", repeat.id, 0, 6, preciseKg, true),
            RoutineSet("repeat-custom", repeat.id, 1, 12, 18.125, false),
            RoutineSet("repeat-blank", repeat.id, 2, 0, null, false),
            RoutineSet("cable-inherited", cablePlan.id, 0, 9, 17.375, true),
            RoutineSet("cable-custom", cablePlan.id, 1, 15, 12.625, false),
            RoutineSet("first-custom", first.id, 0, 7, 20.375, false),
            RoutineSet("first-inherited", first.id, 1, 6, preciseKg, true),
            RoutineSet("body-inherited", body.id, 0, 11, null, true),
        )
        val history = Workout("history", "Original historical title", 1_000, 2_000, WorkoutStatus.FINISHED, routine.id)
        val active = Workout("active", "Unchanged active title", 3_000, sourceRoutineId = routine.id)
        fun entry(id: String, workout: Workout, exercise: Exercise, position: Int, rest: Int) =
            WorkoutExercise(id, workout.id, exercise.id, "Snapshot $id", exercise.muscle,
                exercise.equipment, exercise.convention, position, rest)
        val entries = listOf(
            entry("history-barbell", history, barbell, 0, 95),
            entry("history-cable", history, cable, 1, 140),
            entry("history-body", history, pullup, 2, 90),
            entry("active-repeat", active, barbell, 0, 155),
            entry("active-cable", active, cable, 1, 0),
            entry("active-first", active, barbell, 2, 300),
        )
        val sets = listOf(
            WorkoutSet("history-record", "history-barbell", 0, 6, preciseKg, true),
            WorkoutSet("history-lower", "history-barbell", 1, 12, 20.375, true),
            WorkoutSet("history-warmup", "history-barbell", 2, 3, 50.125, true, warmup = true),
            WorkoutSet("history-cable", "history-cable", 0, 9, 17.375, true),
            WorkoutSet("history-assisted", "history-cable", 1, 14, 40.625, true, assistance = true),
            WorkoutSet("history-body", "history-body", 0, 11, 0.0, true),
            WorkoutSet("active-completed", "active-repeat", 0, 5, 99.125, true),
            WorkoutSet("active-unfinished", "active-repeat", 1, 7, preciseKg),
            WorkoutSet("active-warmup", "active-cable", 0, 4, 7.625, true, warmup = true),
            WorkoutSet("active-assistance", "active-cable", 1, 9, 35.375, assistance = true),
            WorkoutSet("active-blank", "active-first", 0),
        )
        return AppData(listOf(barbell, cable, pullup), listOf(routine), listOf(first, repeat, cablePlan, body),
            listOf(history, active), entries, sets, planned)
    }

    private fun createDistributedV142Database() {
        // Immutable copy from the v1.4.2 release source, Git blob aa9f6dc6dc595072bf99034210e19555e937a660.
        val text = javaClass.classLoader!!.getResourceAsStream("rollback/v142-room-schema4.json")!!
            .bufferedReader().use { it.readText() }
        val schema = JSONObject(text).getJSONObject("database")
        assertEquals(4, schema.getInt("version"))
        assertEquals("fa80dd71df6c40254d9d1b07ebd3f1a6", schema.getString("identityHash"))
        val path = context.getDatabasePath(filename)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
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
            fixture.exercises.forEach { raw.insert("exercises", "id" to it.id, "name" to it.name,
                "muscle" to it.muscle.name, "equipment" to it.equipment.name, "convention" to it.convention.name, "custom" to 1) }
            fixture.routines.forEach { raw.insert("routines", "id" to it.id, "name" to it.name, "position" to it.position) }
            fixture.routineExercises.forEach { raw.insert("routine_exercises", "id" to it.id, "routineId" to it.routineId,
                "exerciseId" to it.exerciseId, "position" to it.position, "plannedSets" to it.plannedSets,
                "targetReps" to it.targetReps, "targetWeightKg" to it.targetWeightKg, "restSeconds" to it.restSeconds) }
            fixture.routineSets.forEach { raw.insert("routine_sets", "id" to it.id, "routineExerciseId" to it.routineExerciseId,
                "position" to it.position, "targetReps" to it.targetReps, "targetWeightKg" to it.targetWeightKg,
                "inheritPr" to if (it.inheritPr) 1 else 0) }
            fixture.workouts.forEach { raw.insert("workouts", "id" to it.id, "name" to it.name, "startedAt" to it.startedAt,
                "endedAt" to it.endedAt, "status" to it.status.name, "sourceRoutineId" to it.sourceRoutineId) }
            fixture.workoutExercises.forEach { raw.insert("workout_exercises", "id" to it.id, "workoutId" to it.workoutId,
                "exerciseId" to it.exerciseId, "name" to it.name, "muscle" to it.muscle.name, "equipment" to it.equipment.name,
                "convention" to it.convention.name, "position" to it.position, "restSeconds" to it.restSeconds) }
            fixture.sets.forEach { raw.insert("workout_sets", "id" to it.id, "workoutExerciseId" to it.workoutExerciseId,
                "position" to it.position, "reps" to it.reps, "weightKg" to it.weightKg, "completed" to if (it.completed) 1 else 0,
                "warmup" to if (it.warmup) 1 else 0, "assistance" to if (it.assistance) 1 else 0) }
            checkpoints.forEach { raw.insert("routine_target_checkpoints", "exerciseId" to it.exerciseId,
                "convention" to it.convention.name, "weightKg" to it.weightKg, "reps" to it.reps) }
            raw.version = 4
        }
        // Ignored output supports a separate, actual emulator upgrade check with this exact
        // representative distributed database. The compatibility assertions remain host tests.
        val app = if (File("src/main/AndroidManifest.xml").isFile) File(".") else File("app")
        val output = File(app, "build/outputs/rollback-device-fixture").also { it.mkdirs() }
        path.copyTo(File(output, "mooscles-v142.db"), overwrite = true)
    }

    private fun SQLiteDatabase.insert(table: String, vararg values: Pair<String, Any?>) {
        val columns = values.joinToString(", ") { "`${it.first}`" }
        val placeholders = values.joinToString(", ") { "?" }
        execSQL("INSERT INTO `$table` ($columns) VALUES ($placeholders)", values.map { it.second }.toTypedArray())
    }
}
