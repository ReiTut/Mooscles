package com.mooscles.data

/**
 * A database as version 5 (v1.4.x and v1.5.0) left it, for the v5 -> v6 migration tests on the
 * Android host and on the iOS simulator, which has no file system access to the exported JSON.
 *
 * [statements] are the `createSql` of every entity and index and the `setupQueries` of Room's
 * exported `5.json`, in its order, with `${TABLE_NAME}` resolved. `SchemaV5FixtureTest` (`:app`)
 * proves that they still match the file. [seed] fills it the way a real user's phone would be:
 * two folders, three routines, inherited and manual planned sets (blank ones too), a finished and
 * an active workout from routines, and the consumed-record checkpoints.
 */
internal object SchemaV5 {
    const val VERSION = 5
    const val IDENTITY_HASH = "48892117afebb5cd76d2a80e62cb729e"
    const val ACTIVE_WORKOUT_ID = "v5-active"

    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `muscle` TEXT NOT NULL, `equipment` TEXT NOT NULL, `convention` TEXT NOT NULL, `custom` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `routine_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `routines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, `folderId` TEXT NOT NULL DEFAULT 'default', PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_routines_folderId` ON `routines` (`folderId`)",
        "CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` TEXT NOT NULL, `routineId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `plannedSets` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetWeightKg` REAL, `restSeconds` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `routine_sets` (`id` TEXT NOT NULL, `routineExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetWeightKg` REAL, `inheritPr` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)",
        "CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `status` TEXT NOT NULL, `sourceRoutineId` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` TEXT NOT NULL, `workoutId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `name` TEXT NOT NULL, `muscle` TEXT NOT NULL, `equipment` TEXT NOT NULL, `convention` TEXT NOT NULL, `position` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` ON `workout_exercises` (`workoutId`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` ON `workout_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` TEXT NOT NULL, `workoutExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `completed` INTEGER NOT NULL, `warmup` INTEGER NOT NULL, `assistance` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutExerciseId` ON `workout_sets` (`workoutExerciseId`)",
        "CREATE TABLE IF NOT EXISTS `routine_target_checkpoints` (`exerciseId` TEXT NOT NULL, `convention` TEXT NOT NULL, `weightKg` REAL, `reps` INTEGER NOT NULL, PRIMARY KEY(`exerciseId`, `convention`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '48892117afebb5cd76d2a80e62cb729e')",
    )

    /** Rows only; the tests set `user_version` to [VERSION] the way their platform does. */
    val seed: List<String> = listOf(
        "INSERT INTO exercises VALUES ('v5-press', 'Saved press', 'CHEST', 'DUMBBELL', 'PER_HAND', 1), ('v5-row', 'Côte d''Azur “row” 💪', 'BACK', 'CABLE', 'TOTAL_LOAD', 1)",
        "INSERT INTO routine_folders VALUES ('default', 'My routines', 0), ('v5-upper', 'U/L', 1)",
        "INSERT INTO routines VALUES ('v5-push', 'Push', 0, 'v5-upper'), ('v5-pull', 'Pull', 1, 'v5-upper'), ('v5-full', 'Full body', 2, 'default')",
        "INSERT INTO routine_exercises VALUES ('v5-push-press', 'v5-push', 'v5-press', 0, 3, 7, 20.375, 155), ('v5-pull-row', 'v5-pull', 'v5-row', 0, 2, 10, 22.6796185, NULL), ('v5-full-press', 'v5-full', 'v5-press', 0, 1, 0, NULL, 0)",
        // Manual (inheritPr 0), blank and inherited (inheritPr 1) prescriptions.
        "INSERT INTO routine_sets VALUES ('v5-custom', 'v5-push-press', 0, 7, 20.375, 0), ('v5-blank', 'v5-push-press', 1, 0, NULL, 0), ('v5-inherited', 'v5-push-press', 2, 6, 22.25, 1), ('v5-row-first', 'v5-pull-row', 0, 10, 22.6796185, 0), ('v5-row-second', 'v5-pull-row', 1, 8, 25.0, 1), ('v5-full-only', 'v5-full-press', 0, 0, NULL, 1)",
        "INSERT INTO workouts VALUES ('v5-history', 'Saved history', 1000, 2000, 'FINISHED', 'v5-push'), ('$ACTIVE_WORKOUT_ID', 'Saved active', 3000, NULL, 'ACTIVE', 'v5-pull')",
        "INSERT INTO workout_exercises VALUES ('v5-history-entry', 'v5-history', 'v5-press', 'Historical name', 'CHEST', 'DUMBBELL', 'PER_HAND', 0, 95), ('v5-active-entry', '$ACTIVE_WORKOUT_ID', 'v5-row', 'Active name', 'BACK', 'CABLE', 'TOTAL_LOAD', 0, 155)",
        // A logged warm-up (workout_sets.warmup) must stay apart from the new planned one.
        "INSERT INTO workout_sets VALUES ('v5-finished', 'v5-history-entry', 0, 6, 22.25, 1, 0, 0), ('v5-warmup', 'v5-history-entry', 1, 3, 30.25, 1, 1, 0), ('v5-working', 'v5-active-entry', 0, 10, 22.6796185, 1, 0, 0), ('v5-unfinished', 'v5-active-entry', 1, 0, 0.0, 0, 0, 0)",
        "INSERT INTO routine_target_checkpoints VALUES ('v5-press', 'PER_HAND', 22.25, 6), ('v5-row', 'TOTAL_LOAD', NULL, 0)",
    )

    /** Rows in [seed], per table. */
    val seededRows: Map<String, Int> = mapOf(
        "exercises" to 2, "routine_folders" to 2, "routines" to 3, "routine_exercises" to 3, "routine_sets" to 6,
        "workouts" to 2, "workout_exercises" to 2, "workout_sets" to 4, "routine_target_checkpoints" to 2,
    )

    /**
     * Every saved value, read the same way before and after an upgrade. `routine_sets` is read by its
     * version 5 columns, so the comparison holds across the added `warmup`; every other table is read
     * whole, so a column that appears anywhere else fails it.
     */
    val savedQueries: Map<String, String> = seededRows.keys.associateWith { table ->
        if (table == "routine_sets") "SELECT id, routineExerciseId, position, targetReps, targetWeightKg, inheritPr FROM routine_sets ORDER BY id"
        else "SELECT * FROM `$table` ORDER BY 1, 2"
    }
}
