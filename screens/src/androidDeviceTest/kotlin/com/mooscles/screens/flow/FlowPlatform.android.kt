package com.mooscles.screens.flow

import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.MoosclesStore
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.buildMooscles
import kotlinx.coroutines.CoroutineScope
import java.io.File

// The emulator's side of FlowHarness: the same declarations as FlowPlatform.ios.kt (not expect/actual).

/** A new, empty directory of the test app's cache for one flow; [deleteFlowDirectory] removes it. */
internal fun createFlowDirectory(): String {
    val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    val directory = File(cache, "flow-${System.nanoTime()}")
    check(directory.mkdirs()) { "Could not create $directory" }
    return directory.absolutePath
}

internal fun deleteFlowDirectory(directory: String) {
    File(directory).deleteRecursively()
}

/**
 * `<directory>/mooscles.db` through the app's own path on Android: Room with the system SQLite
 * ([AndroidSQLiteDriver]) and [buildMooscles] (the migrations and the creation callback). Room
 * takes an absolute name as it is, so the file stays in [directory].
 */
internal fun openFlowDatabase(directory: String): WorkoutDatabase = Room.databaseBuilder(
    InstrumentationRegistry.getInstrumentation().targetContext,
    WorkoutDatabase::class.java,
    "$directory/${WorkoutDatabase.FILE_NAME}",
).buildMooscles(AndroidSQLiteDriver())

/**
 * The preferences file at the app's layout (`<directory>/datastore/local_preferences.preferences_pb`),
 * opened by [PreferenceDataStoreFactory] as the app's `preferencesDataStore` delegate does; cancel
 * [scope] to close it.
 */
internal fun openFlowStore(directory: String, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope) { File(directory, "datastore/${MoosclesStore.FILE_NAME}") }

/** On the emulator the capture is only taken (it proves `captureToImage` works); nothing is written. */
internal fun saveFlowCapture(name: String, image: ImageBitmap) = Unit
