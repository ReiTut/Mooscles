package com.mooscles.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Opens `<directory>/mooscles.db` with the SQLite compiled into the app ([BundledSQLiteDriver]),
 * through the same migrations and creation callback as Android ([buildMooscles]). The app passes
 * `moosclesDataDirectory()` (Application Support); tests pass a temporary directory.
 *
 * Open it once per process and keep it: Room's connection pool owns the file. [directory] is
 * created if it does not exist yet, because SQLite creates the file but not its parents.
 */
fun openWorkoutDatabase(directory: String): WorkoutDatabase {
    FileSystem.SYSTEM.createDirectories(directory.toPath())
    return Room.databaseBuilder<WorkoutDatabase>(name = "$directory/${WorkoutDatabase.FILE_NAME}")
        .buildMooscles(BundledSQLiteDriver())
}
