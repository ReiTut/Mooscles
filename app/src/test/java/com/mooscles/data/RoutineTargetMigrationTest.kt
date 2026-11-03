package com.mooscles.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds actual old databases from Room's exported schemas before running every migration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineTargetMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun versionThreeUpgradePreservesAllDataAndDoesNotGuessLegacySourcesFromMatchingNames() = runBlocking {
        val filename = "rm-v3-${newId().take(8)}.db"
        val exercise = Exercise("v3-press", "Saved custom press", MuscleGroup.CHEST,
            Equipment.BARBELL, LoadConvention.TOTAL_LOAD, custom = true)
        val first = Routine("v3-routine-first", "Same name", position = 1)
        val second = Routine("v3-routine-second", "Same name", position = 0)
        val plan = RoutineExercise("v3-plan", first.id, exercise.id, plannedSets = 2,
            targetReps = 8, targetWeightKg = 17.25, restSeconds = 135)
        val planned = listOf(
            RoutineSet("v3-row-first", plan.id, position = 0, targetReps = 8, targetWeightKg = 17.25, inheritPr = false),
            RoutineSet("v3-row-second", plan.id, position = 1, targetReps = 0, targetWeightKg = null, inheritPr = true)
        )
        val active = Workout("v3-active", "Same name", startedAt = 3000)
        val finished = Workout("v3-finished", "Same name", startedAt = 1000, endedAt = 2000,
            status = WorkoutStatus.FINISHED)
        val activeEntry = WorkoutExercise("v3-active-entry", active.id, exercise.id,
            "Unchanged active snapshot", exercise.muscle, exercise.equipment, exercise.convention,
            restSeconds = 155)
        val historyEntry = activeEntry.copy(id = "v3-history-entry", workoutId = finished.id,
            name = "Unchanged historical snapshot", restSeconds = 95)
        val rows = listOf(
            WorkoutSet("v3-active-completed", activeEntry.id, position = 0, reps = 8, weightKg = 17.25, completed = true),
            WorkoutSet("v3-active-unfinished", activeEntry.id, position = 1, reps = 7, weightKg = 18.0),
            WorkoutSet("v3-history-working", historyEntry.id, position = 0, reps = 7, weightKg = 17.25, completed = true),
            WorkoutSet("v3-history-warmup", historyEntry.id, position = 1, reps = 4, weightKg = 10.0, completed = true, warmup = true)
        )
        val legacy = AppData(listOf(exercise), listOf(first, second), listOf(plan), listOf(active, finished),
            listOf(activeEntry, historyEntry), rows, planned)
        createLegacyDatabase(filename, legacy, version = 3)
        var database: WorkoutDatabase? = null
        try {
            database = openMigrating(filename)
            var repo = WorkoutRepository(database)
            val upgraded = repo.snapshot()
            assertEquals(6, database.userVersion())
            assertLegacyDataUnchanged(legacy, upgraded)
            assertEquals(planned.sortedBy { it.id }, upgraded.routineSets.sortedBy { it.id })
            assertTrue(upgraded.workouts.all { it.sourceRoutineId == null })
            assertEquals(active.id, upgraded.activeWorkout!!.id)
            assertEquals(active.id, repo.startRoutine(first.id))
            assertEquals(active.id, repo.startRoutine(second.id))
            assertEquals(upgraded, repo.snapshot())
            database.close()
            database = openMigrating(filename)
            repo = WorkoutRepository(database)
            assertEquals(upgraded, repo.snapshot())
        } finally {
            database?.close()
            context.deleteDatabase(filename)
        }
    }

    @Test fun versionTwoUpgradeCopiesDecimalAndBlankPrescriptionsWithExactCountsAndRest() = runBlocking {
        val filename = "rm-v2-${newId().take(8)}.db"
        val exercise = Exercise("v2-exercise", "Press", MuscleGroup.CHEST, Equipment.DUMBBELL,
            LoadConvention.PER_HAND, custom = true)
        val routine = Routine("v2-routine", "Original routine")
        val weighted = RoutineExercise("v2-weighted", routine.id, exercise.id, position = 0,
            plannedSets = 4, targetReps = 7, targetWeightKg = 20.375, restSeconds = 135)
        val blank = RoutineExercise("v2-blank", routine.id, exercise.id, position = 1,
            plannedSets = 3, targetReps = 0, targetWeightKg = null, restSeconds = null)
        val legacy = AppData(exercises = listOf(exercise), routines = listOf(routine), routineExercises = listOf(weighted, blank))
        createLegacyDatabase(filename, legacy, version = 2)
        var database: WorkoutDatabase? = null
        try {
            database = openMigrating(filename)
            var repo = WorkoutRepository(database)
            repo.initialize()
            val snapshot = repo.snapshot()
            assertEquals(listOf(weighted, blank), snapshot.routineItems(routine.id))
            for (plan in listOf(weighted, blank)) {
                val rows = snapshot.plannedSets(plan.id)
                assertEquals(plan.plannedSets, rows.size)
                assertEquals((0 until plan.plannedSets).toList(), rows.map { it.position })
                assertTrue(rows.all { !it.inheritPr && it.targetReps == plan.targetReps && it.targetWeightKg == plan.targetWeightKg })
            }
            database.close()
            database = openMigrating(filename)
            repo = WorkoutRepository(database)
            repo.initialize()
            assertEquals(snapshot.routineSets, repo.snapshot().routineSets)
            val workout = repo.startRoutine(routine.id, defaultRestSeconds = 30)
            val started = repo.snapshot()
            assertEquals(listOf(135, 120), started.workoutItems(workout).map { it.restSeconds })
            assertTrue(started.exerciseSets(started.workoutItems(workout)[0].id).all { it.reps == 7 && it.weightKg == 20.375 })
            assertTrue(started.exerciseSets(started.workoutItems(workout)[1].id).all { it.reps == 0 && it.weightKg == 0.0 })
            assertEquals(4, started.exerciseSets(started.workoutItems(workout)[0].id).size)
            assertEquals(3, started.exerciseSets(started.workoutItems(workout)[1].id).size)
        } finally { database?.close(); context.deleteDatabase(filename) }
    }

    @Test fun versionOneUpgradePreservesEveryLegacyPrescriptionAsCustomAndLeavesSnapshotsUnchanged() = runBlocking {
        val filename = "rm-${newId().take(8)}.db"
        val bench = Exercise("legacy-bench", "Barbell press", MuscleGroup.CHEST,
            Equipment.BARBELL, LoadConvention.TOTAL_LOAD, custom = true)
        val cable = bench.copy(id = "legacy-cable", name = "Cable press", equipment = Equipment.CABLE)
        val push = Routine("legacy-push", "Push", position = 0)
        val fullBody = Routine("legacy-full-body", "Full body", position = 1)
        val firstPlan = RoutineExercise("legacy-bench-plan", push.id, bench.id,
            position = 0, plannedSets = 4, targetReps = 7, targetWeightKg = 20.0, restSeconds = 135)
        val cablePlan = RoutineExercise("legacy-cable-plan", push.id, cable.id,
            position = 1, plannedSets = 3, targetReps = 10, targetWeightKg = 15.0, restSeconds = 90)
        val otherPlan = RoutineExercise("legacy-other-bench-plan", fullBody.id, bench.id,
            position = 0, plannedSets = 2, targetReps = 8, targetWeightKg = 18.0, restSeconds = null)
        val finished = Workout("legacy-finished", "Original completed workout", startedAt = 1000,
            endedAt = 2000, status = WorkoutStatus.FINISHED)
        val active = Workout("legacy-active", "In-progress workout", startedAt = 3000)
        val historyEntry = WorkoutExercise("legacy-history-entry", finished.id, bench.id,
            "Original snapshot name", bench.muscle, bench.equipment, bench.convention,
            position = 0, restSeconds = 90)
        val activeEntry = historyEntry.copy(id = "legacy-active-entry", workoutId = active.id,
            name = "In-progress snapshot name", restSeconds = 135)
        val sets = listOf(
            WorkoutSet("legacy-record-set", historyEntry.id, position = 0, reps = 6,
                weightKg = 22.0, completed = true),
            WorkoutSet("legacy-lower-set", historyEntry.id, position = 1, reps = 10,
                weightKg = 20.0, completed = true),
            WorkoutSet("legacy-warmup-set", historyEntry.id, position = 2, reps = 3,
                weightKg = 30.0, completed = true, warmup = true),
            WorkoutSet("legacy-active-completed-set", activeEntry.id, position = 0, reps = 4,
                weightKg = 40.0, completed = true),
            WorkoutSet("legacy-active-unfinished-set", activeEntry.id, position = 1, reps = 7,
                weightKg = 20.0, completed = false)
        )
        val legacy = AppData(listOf(bench, cable), listOf(push, fullBody),
            listOf(firstPlan, cablePlan, otherPlan), listOf(finished, active),
            listOf(historyEntry, activeEntry), sets)
        createLegacyDatabase(filename, legacy, version = 1)

        var database: WorkoutDatabase? = null
        try {
            database = openMigrating(filename)
            var repository = WorkoutRepository(database)
            assertEquals(6, database.userVersion())
            assertTrue(database.dao().targetCheckpoints().isEmpty())
            assertLegacyDataUnchanged(legacy, repository.snapshot())

            repository.initialize()
            val synchronized = repository.snapshot()
            assertEquals(firstPlan,
                synchronized.routineItems(push.id).first())
            assertEquals(cablePlan, synchronized.routineItems(push.id).last())
            assertEquals(otherPlan,
                synchronized.routineItems(fullBody.id).single())
            for (plan in legacy.routineExercises) {
                val rows = synchronized.plannedSets(plan.id)
                assertEquals(plan.plannedSets, rows.size)
                assertEquals((0 until plan.plannedSets).toList(), rows.map { it.position })
                assertTrue(rows.all { it.targetReps == plan.targetReps && it.targetWeightKg == plan.targetWeightKg && !it.inheritPr })
            }
            assertEquals(listOf(firstPlan.id, cablePlan.id), synchronized.routineItems(push.id).map { it.id })
            assertSnapshotsUnchanged(legacy, synchronized)
            val checkpoint = database.dao().targetCheckpoints().single()
            assertEquals(bench.id, checkpoint.exerciseId)
            assertEquals(LoadConvention.TOTAL_LOAD, checkpoint.convention)
            assertEquals(22.0, requireNotNull(checkpoint.weightKg), 0.0)
            assertEquals(6, checkpoint.reps)

            // The consumed-record marker must survive reopening, so old history cannot undo edits.
            val manual = synchronized.routineItems(push.id).first().copy(targetWeightKg = 18.0, targetReps = 12)
            repository.updateRoutineExercise(manual)
            database.close()
            database = openMigrating(filename)
            repository = WorkoutRepository(database)
            repository.initialize()
            repository.initialize()
            val reopened = repository.snapshot()
            assertEquals(manual, reopened.routineItems(push.id).first())
            assertEquals(cablePlan, reopened.routineItems(push.id).last())
            assertEquals(otherPlan,
                reopened.routineItems(fullBody.id).single())
            assertEquals(listOf(checkpoint), database.dao().targetCheckpoints())
            assertSnapshotsUnchanged(legacy, reopened)
        } finally {
            database?.close()
            context.deleteDatabase(filename)
        }
    }

    private fun openMigrating(filename: String): WorkoutDatabase =
        Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver())
            .addMigrations(WorkoutDatabase.MIGRATION_1_2, WorkoutDatabase.MIGRATION_2_3, WorkoutDatabase.MIGRATION_3_4, WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6).build()

    private fun assertLegacyDataUnchanged(expected: AppData, actual: AppData) {
        assertEquals(expected.exercises.sortedBy { it.id }, actual.exercises.sortedBy { it.id })
        assertEquals(expected.routines.sortedBy { it.id }, actual.routines.sortedBy { it.id })
        assertEquals(expected.routineExercises.sortedBy { it.id }, actual.routineExercises.sortedBy { it.id })
        assertSnapshotsUnchanged(expected, actual)
    }

    private fun assertSnapshotsUnchanged(expected: AppData, actual: AppData) {
        assertEquals(expected.workouts.sortedBy { it.id }, actual.workouts.sortedBy { it.id })
        assertEquals(expected.workoutExercises.sortedBy { it.id }, actual.workoutExercises.sortedBy { it.id })
        assertEquals(expected.sets.sortedBy { it.id }, actual.sets.sortedBy { it.id })
    }

    private fun createLegacyDatabase(filename: String, data: AppData, version: Int) {
        val schema = JSONObject(exportedSchemaFile(version).readText()).getJSONObject("database")
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
            data.exercises.forEach { exercise ->
                raw.insert("exercises", "id" to exercise.id, "name" to exercise.name,
                    "muscle" to exercise.muscle.name, "equipment" to exercise.equipment.name,
                    "convention" to exercise.convention.name, "custom" to if (exercise.custom) 1 else 0)
            }
            data.routines.forEach { routine ->
                raw.insert("routines", "id" to routine.id, "name" to routine.name, "position" to routine.position)
            }
            data.routineExercises.forEach { plan ->
                raw.insert("routine_exercises", "id" to plan.id, "routineId" to plan.routineId,
                    "exerciseId" to plan.exerciseId, "position" to plan.position, "plannedSets" to plan.plannedSets,
                    "targetReps" to plan.targetReps, "targetWeightKg" to plan.targetWeightKg, "restSeconds" to plan.restSeconds)
            }
            if (version >= 3) data.routineSets.forEach { set ->
                raw.insert("routine_sets", "id" to set.id, "routineExerciseId" to set.routineExerciseId,
                    "position" to set.position, "targetReps" to set.targetReps,
                    "targetWeightKg" to set.targetWeightKg, "inheritPr" to if (set.inheritPr) 1 else 0)
            }
            data.workouts.forEach { workout ->
                raw.insert("workouts", "id" to workout.id, "name" to workout.name, "startedAt" to workout.startedAt,
                    "endedAt" to workout.endedAt, "status" to workout.status.name)
            }
            data.workoutExercises.forEach { exercise ->
                raw.insert("workout_exercises", "id" to exercise.id, "workoutId" to exercise.workoutId,
                    "exerciseId" to exercise.exerciseId, "name" to exercise.name, "muscle" to exercise.muscle.name,
                    "equipment" to exercise.equipment.name, "convention" to exercise.convention.name,
                    "position" to exercise.position, "restSeconds" to exercise.restSeconds)
            }
            data.sets.forEach { set ->
                raw.insert("workout_sets", "id" to set.id, "workoutExerciseId" to set.workoutExerciseId,
                    "position" to set.position, "reps" to set.reps, "weightKg" to set.weightKg,
                    "completed" to if (set.completed) 1 else 0, "warmup" to if (set.warmup) 1 else 0,
                    "assistance" to if (set.assistance) 1 else 0)
            }
            raw.version = schema.getInt("version")
            assertEquals(version, raw.version)
        }
    }

    private fun SQLiteDatabase.insert(table: String, vararg values: Pair<String, Any?>) {
        val columns = values.joinToString(", ") { "`${it.first}`" }
        val placeholders = values.joinToString(", ") { "?" }
        execSQL("INSERT INTO `$table` ($columns) VALUES ($placeholders)", values.map { it.second }.toTypedArray())
    }
}
