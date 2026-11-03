package com.mooscles

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.mooscles.backup.BackupPreferences
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.PreferencesUi
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesTest {
    private val isolatedJob = SupervisorJob()
    private lateinit var storeDirectory: File
    private lateinit var restorePreviousStore: () -> Unit

    /**
     * The production delegate caches one store process-wide, so writes here would reach later test
     * classes. Give every test its own file and put the previous instance back afterwards.
     */
    @Before fun isolateStore() {
        storeDirectory = Files.createTempDirectory("mooscles-preferences-test-").toFile()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(isolatedJob + Dispatchers.IO), produceFile = { storeDirectory.resolve("test.preferences_pb") })
        val delegate = Class.forName("com.mooscles.AppPreferencesKt")
            .getDeclaredField("moosclesStore\$delegate").apply { isAccessible = true }.get(null)
        val instance = delegate.javaClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val previous = instance.get(delegate)
        instance.set(delegate, store)
        restorePreviousStore = { instance.set(delegate, previous) }
    }

    @After fun restoreStore() = runBlocking {
        isolatedJob.cancelAndJoin()
        restorePreviousStore()
        storeDirectory.deleteRecursively()
        Unit
    }

    @Test fun timerAndUnrelatedWritesDoNotEmitDisplayPreferencesWhileSettingsStillPersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("mooscles-display-preferences-").toFile()
        val file = directory.resolve("isolated.preferences_pb")
        // The production delegate caches its store process-wide. Bind only this test's real
        // file, then restore the previous instance, as the rollback compatibility tests do.
        val delegateField = Class.forName("com.mooscles.AppPreferencesKt")
            .getDeclaredField("moosclesStore\$delegate").apply { isAccessible = true }
        val delegate = delegateField.get(null)
        val instance = delegate.javaClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val previousStore = instance.get(delegate)
        var storeJob: Job = SupervisorJob()
        var collector: Job? = null
        val events = Channel<PreferencesUi>(Channel.UNLIMITED)
        try {
            var store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(storeJob + Dispatchers.IO), produceFile = { file })
            instance.set(delegate, store)
            val preferences = AppPreferences(context)
            collector = launch(Dispatchers.Default) { preferences.flow.collect { events.send(it) } }
            assertEquals(PreferencesUi(), withTimeout(5_000) { events.receive() })

            val timer = RestTimerPersistence(store) { 1_000_000L }
            timer.start("Bench press", 120, "working-set-exercise")
            assertNull("Timer start must not trigger card preparation", withTimeoutOrNull(300) { events.receive() })
            timer.clear()
            assertNull("Workout startup's timer clear must not trigger card preparation", withTimeoutOrNull(300) { events.receive() })
            timer.reset()
            assertNull("Timer reset must not trigger card preparation", withTimeoutOrNull(300) { events.receive() })
            val unrelatedMetadata = longPreferencesKey("unrelated_update_checked_at")
            store.edit { it[unrelatedMetadata] = 42L }
            assertNull("Unrelated storage writes must not emit display settings", withTimeoutOrNull(300) { events.receive() })

            var expected = PreferencesUi()
            preferences.theme("dark")
            expected = expected.copy(theme = "dark")
            assertEquals(expected, withTimeout(5_000) { events.receive() })
            preferences.pounds(true)
            expected = expected.copy(pounds = true)
            assertEquals(expected, withTimeout(5_000) { events.receive() })
            preferences.autoRest(false)
            expected = expected.copy(autoRest = false)
            assertEquals(expected, withTimeout(5_000) { events.receive() })
            preferences.defaultRest(135)
            expected = expected.copy(defaultRestSeconds = 135)
            assertEquals(expected, withTimeout(5_000) { events.receive() })
            preferences.selectRoutineFolder("saved-folder")
            expected = expected.copy(selectedRoutineFolderId = "saved-folder")
            assertEquals(expected, withTimeout(5_000) { events.receive() })
            assertEquals(expected, preferences.snapshot())

            collector.cancelAndJoin()
            collector = null
            storeJob.cancelAndJoin()
            storeJob = SupervisorJob()
            store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(storeJob + Dispatchers.IO), produceFile = { file })
            instance.set(delegate, store)
            assertEquals(expected, AppPreferences(context).flow.first())
            assertEquals(expected, AppPreferences(context).snapshot())
            assertEquals(42L, store.data.first()[unrelatedMetadata])
            assertEquals(120_000L, RestTimerPersistence(store) { 1_000_000L }.snapshot().pausedMillis)
        } finally {
            collector?.cancelAndJoin()
            storeJob.cancelAndJoin()
            instance.set(delegate, previousStore)
            events.close()
            file.delete()
            directory.delete()
        }
    }

    @Test fun missingDefaultRestUsesTwoMinutesWithoutReplacingOtherPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rest = intPreferencesKey("default_rest")
        val timerDeadline = longPreferencesKey("timer_deadline")
        context.moosclesStore.edit { it.remove(rest); it[timerDeadline] = 123456L }
        assertEquals(120, AppPreferences(context).flow.first().defaultRestSeconds)
        assertEquals(123456L, context.moosclesStore.data.first()[timerDeadline])
    }

    @Test fun savedCustomDefaultRestSurvivesReopeningPreferencesIncludingPreviousNinetySeconds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (seconds in listOf(90, 135, 0, 300)) {
            AppPreferences(context).defaultRest(seconds)
            assertEquals(seconds, AppPreferences(context).flow.first().defaultRestSeconds)
            assertEquals(seconds, context.moosclesStore.data.first()[intPreferencesKey("default_rest")])
        }
    }

    @Test fun normalizesLegacyDefaultRestWithoutChangingTimerData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rest = intPreferencesKey("default_rest")
        val timerDeadline = longPreferencesKey("timer_deadline")
        context.moosclesStore.edit { it[rest] = 900; it[timerDeadline] = 123456L }
        assertEquals(300, AppPreferences(context).flow.first().defaultRestSeconds)
        val stored = context.moosclesStore.data.first()
        assertEquals(300, stored[rest])
        assertEquals(123456L, stored[timerDeadline])
    }

    @Test fun restoreWritesAllFiveValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AppPreferences(context)
        preferences.restore(BackupPreferences(pounds = false, theme = "system", autoRest = true, defaultRestSeconds = 120, selectedRoutineFolderId = "default"))
        val restored = BackupPreferences(pounds = true, theme = "dark", autoRest = false, defaultRestSeconds = 75, selectedRoutineFolderId = "upper")

        preferences.restore(restored)

        val expected = PreferencesUi(pounds = true, theme = "dark", autoRest = false, defaultRestSeconds = 75, selectedRoutineFolderId = "upper")
        assertEquals(expected, AppPreferences(context).snapshot())
        assertEquals(expected, AppPreferences(context).flow.first())
        assertEquals(restored, expected.toBackup())
    }

    @Test fun restoreRejectsInvalidTheme() = runBlocking {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext<Context>())
        val saved = BackupPreferences(pounds = true, theme = "light", autoRest = false, defaultRestSeconds = 90, selectedRoutineFolderId = "upper")
        preferences.restore(saved)

        for (theme in listOf("DARK", "neon", "")) {
            try { preferences.restore(saved.copy(pounds = false, theme = theme, defaultRestSeconds = 30)); fail("Theme must be known") }
            catch (_: IllegalArgumentException) { }
        }

        assertEquals(saved, preferences.snapshot().toBackup())
    }

    @Test fun restoreRejectsOutOfRangeRest() = runBlocking {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext<Context>())
        val saved = BackupPreferences(pounds = true, theme = "dark", autoRest = true, defaultRestSeconds = 300, selectedRoutineFolderId = "default")
        preferences.restore(saved)

        for (seconds in listOf(-1, 301)) {
            try { preferences.restore(saved.copy(pounds = false, defaultRestSeconds = seconds)); fail("Duration must be bounded") }
            catch (_: IllegalArgumentException) { }
        }

        assertEquals(saved, preferences.snapshot().toBackup())
    }

    @Test fun factoryWritesThroughTheDelegateStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreferences(context).pounds(true)
        assertEquals(true, context.moosclesStore.data.first()[booleanPreferencesKey("pounds")])
        // A second instance shares the process-wide store instead of opening the file again.
        assertTrue(AppPreferences(context).snapshot().pounds)
    }

    @Test fun invalidDefaultRestCannotOverwriteSavedMaximum() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AppPreferences(context)
        preferences.defaultRest(300)
        for (invalid in listOf(-1, 301, 360)) {
            try { preferences.defaultRest(invalid); fail("Duration must be bounded") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(300, preferences.flow.first().defaultRestSeconds)
    }
}
