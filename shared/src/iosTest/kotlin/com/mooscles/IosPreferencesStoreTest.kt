package com.mooscles

import com.mooscles.backup.BackupPreferences
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.PreferencesUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS DataStore factory on the simulator: one file for preferences and the timer, reopened. */
class IosPreferencesStoreTest {
    private val directory = TemporaryDirectory()

    @AfterTest fun tearDown() = directory.delete()

    @Test fun preferencesAndTimerPersistAcrossStoreInstances() = runBlocking<Unit> {
        val now = { 1_000_000L }
        val firstScope = SupervisorJob()
        val (savedPreferences, savedTimer) = try {
            val store = createMoosclesStore(directory.path, CoroutineScope(Dispatchers.IO + firstScope))
            val preferences = AppPreferences(store)
            preferences.restore(BackupPreferences(true, "dark", false, 150, "default"))
            val timer = RestTimerPersistence(store, now)
            timer.start("Press", 90, "entry")
            val savedPreferences = preferences.snapshot()
            val savedTimer = timer.snapshot()
            assertEquals(PreferencesUi(pounds = true, theme = "dark", autoRest = false, defaultRestSeconds = 150,
                selectedRoutineFolderId = "default"), savedPreferences)
            assertEquals("Press", savedTimer.exerciseName)
            assertEquals(1_090_000L, savedTimer.deadline)
            assertEquals("entry", savedTimer.workoutExerciseId)
            savedPreferences to savedTimer
        } finally {
            // DataStore allows one active instance per file; closing the scope releases it, even when an assertion failed.
            firstScope.cancelAndJoin()
        }

        val secondScope = SupervisorJob()
        try {
            val reopened = createMoosclesStore(directory.path, CoroutineScope(Dispatchers.IO + secondScope))
            assertEquals(savedPreferences, AppPreferences(reopened).snapshot())
            assertEquals(savedTimer, RestTimerPersistence(reopened, now).snapshot())
            assertTrue(directory.exists("datastore/local_preferences.preferences_pb"))
        } finally {
            secondScope.cancelAndJoin()
        }
    }

    // Side effect, left in place on purpose: moosclesDataDirectory() creates the simulator app's
    // real Library/Application Support directory. It is empty, harmless, and the simulator is disposable.
    @Test fun dataDirectoryIsApplicationSupportInTheSandbox() {
        val path = moosclesDataDirectory()
        assertTrue(path.endsWith("/Library/Application Support"), path)
        assertTrue(FileSystem.SYSTEM.metadata(path.toPath()).isDirectory, path)
    }
}
