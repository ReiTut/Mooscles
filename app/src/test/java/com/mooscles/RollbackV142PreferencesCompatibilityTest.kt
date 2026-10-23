package com.mooscles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.UpdatePhase
import com.mooscles.updates.UpdateStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reads v1.4.2's literal saved keys and updater JSON from actual preference files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RollbackV142PreferencesCompatibilityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val directory = Files.createTempDirectory("mr-prefs-").toFile()
    private val jobs = mutableListOf<Job>()
    private var now = 1_000_000L
    private var restoreTrainingStore: (() -> Unit)? = null

    private fun store(file: File): Pair<DataStore<Preferences>, Job> {
        file.parentFile!!.mkdirs()
        val job = SupervisorJob().also { jobs.add(it) }
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }) to job
    }

    @After fun close() = runBlocking {
        restoreTrainingStore?.invoke()
        jobs.forEach { it.cancelAndJoin() }
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
        Unit
    }

    @Test fun v142RunningTimerRetainsOwnerIntervalAndDeadlineThenClearsEverySessionKey() = runBlocking {
        val file = File(directory, "timer.preferences_pb")
        val (old, writer) = store(file)
        seedTimer(old, deadline = now + 65_000, paused = 0)
        writer.cancelAndJoin()
        now += 15_000
        val (restoredStore, reader) = store(file)
        val timer = RestTimerPersistence(restoredStore) { now }
        val state = timer.snapshot()
        assertEquals(77L, state.token)
        assertEquals("Repeated press", state.exerciseName)
        assertEquals("active-v142", state.workoutId)
        assertEquals("second-press-instance", state.workoutExerciseId)
        assertEquals(135_000L, state.durationMillis)
        assertEquals(150_000L, state.intervalMillis)
        assertEquals(50_000L, state.remaining(now))
        assertEquals(1f / 3f, state.progress(now), 0.00001f)
        assertTrue(state.running)
        assertEquals(state, timer.reconcileSession("active-v142"))
        timer.pause()
        now += 600_000
        reader.cancelAndJoin()
        val (reopenedStore, _) = store(file)
        val reopened = RestTimerPersistence(reopenedStore) { now }
        assertEquals(50_000L, reopened.snapshot().remaining(now))
        assertTrue(reopened.snapshot().paused)
        reopened.resume()
        assertEquals(now + 50_000, reopened.snapshot().deadline)
        reopened.clear()
        assertNull(reopened.snapshot().workoutId)
        assertNull(reopened.snapshot().workoutExerciseId)
        assertFalse(reopened.snapshot().running)
        assertNull(reopened.claimCompletion(77L))
        val raw = reopenedStore.data.first()
        assertNull(raw[stringPreferencesKey("timer_workout")])
        assertNull(raw[stringPreferencesKey("timer_workout_exercise")])
        assertEquals("preserve me", raw[stringPreferencesKey("unrelated_setting")])
    }

    @Test fun v142PausedTimerKeepsCustomResetDurationWithoutCountingAppClosedTime() = runBlocking {
        val file = File(directory, "paused.preferences_pb")
        val (old, writer) = store(file)
        seedTimer(old, deadline = 0, paused = 83_000)
        writer.cancelAndJoin()
        now += 900_000
        val (reader, _) = store(file)
        val timer = RestTimerPersistence(reader) { now }
        assertTrue(timer.snapshot().paused)
        assertEquals(83_000L, timer.snapshot().remaining(now))
        assertEquals(150_000L, timer.snapshot().intervalMillis)
        val reset = timer.reset()
        assertEquals(135_000L, reset.remaining(now))
        assertEquals(135_000L, reset.intervalMillis)
        assertEquals("second-press-instance", reset.workoutExerciseId)
        assertEquals("active-v142", reset.workoutId)
    }

    @Test fun v142TrainingPreferencesAndPendingUpdaterStateRemainReadableWithRestoredApplication() = runBlocking {
        // The production AppPreferences reader is the first opener after this v1.4.2-format file closes.
        val trainingFile = File(directory, "local_preferences.preferences_pb")
        val (oldTraining, trainingWriter) = store(trainingFile)
        oldTraining.edit {
            it[booleanPreferencesKey("pounds")] = true
            it[stringPreferencesKey("theme")] = "dark"
            it[booleanPreferencesKey("auto_rest")] = false
            it[intPreferencesKey("default_rest")] = 135
        }
        trainingWriter.cancelAndJoin()
        assertTrue(trainingFile.isFile)
        exportFixture(trainingFile, "local_preferences.preferences_pb")
        val (reopenedTraining, _) = store(trainingFile)
        bindIsolatedTrainingStore(reopenedTraining)
        val preferences = AppPreferences(context).flow.first()
        assertTrue(preferences.pounds)
        assertEquals("dark", preferences.theme)
        assertFalse(preferences.autoRest)
        assertEquals(135, preferences.defaultRestSeconds)

        val updaterFile = File(directory, "updates.preferences_pb")
        val (oldUpdater, updaterWriter) = store(updaterFile)
        // Literal state_v1 fields written by distributed Build 12, not generated by the new encoder.
        oldUpdater.edit { it[stringPreferencesKey("state_v1")] = """
            {"automatic":false,"wifiOnly":false,"prereleases":true,"phase":"APPROVAL_REQUIRED",
             "available":null,"checkedAt":1000,"attemptedAt":950,"retryAt":2000,"checkFailures":2,
             "cache":"{\"https://api.github.com/repos/ReiTut/Mooscles/releases?per_page=100\":{\"etag\":\"v142\",\"body\":\"[]\"}}",
             "downloadedBytes":20032522,"generation":9,"cancelledVersion":11,"sessionId":42,
             "pendingActionUri":"intent:#Intent;action=android.content.pm.action.CONFIRM_INSTALL;end",
             "sourcePermissionPending":true,"installAttemptedVersion":12,"lastInstalledVersion":12,
             "message":"Await Android installation confirmation"}
        """.trimIndent() }
        updaterWriter.cancelAndJoin()
        exportFixture(updaterFile, "app_updates.preferences_pb")
        val (restoredUpdater, updaterReader) = store(updaterFile)
        val snapshot = UpdateStore(restoredUpdater).snapshot()
        assertFalse(snapshot.automatic)
        assertFalse(snapshot.wifiOnly)
        assertTrue(snapshot.prereleases)
        assertEquals(UpdatePhase.APPROVAL_REQUIRED, snapshot.phase)
        assertEquals(1_000L, snapshot.checkedAt)
        assertEquals(950L, snapshot.attemptedAt)
        assertEquals(2_000L, snapshot.retryAt)
        assertEquals(2, snapshot.checkFailures)
        assertEquals(20_032_522L, snapshot.downloadedBytes)
        assertEquals(9L, snapshot.generation)
        assertEquals(11L, snapshot.cancelledVersion)
        assertEquals(42, snapshot.sessionId)
        assertEquals(12L, snapshot.installAttemptedVersion)
        assertEquals(12L, snapshot.lastInstalledVersion)
        assertTrue(snapshot.sourcePermissionPending)
        assertTrue(snapshot.pendingActionUri!!.contains("CONFIRM_INSTALL"))
        assertEquals("Await Android installation confirmation", snapshot.message)
        assertEquals(1, snapshot.cache.responses.size)
        updaterReader.cancelAndJoin()
        val (again, _) = store(updaterFile)
        assertEquals(snapshot, UpdateStore(again).snapshot())
        assertEquals(preferences, AppPreferences(context).flow.first())
    }

    private suspend fun seedTimer(store: DataStore<Preferences>, deadline: Long, paused: Long) {
        store.edit {
            it[longPreferencesKey("timer_token")] = 77
            it[stringPreferencesKey("timer_name")] = "Repeated press"
            it[longPreferencesKey("timer_duration")] = 135_000
            it[longPreferencesKey("timer_interval_duration")] = 150_000
            it[longPreferencesKey("timer_deadline")] = deadline
            it[longPreferencesKey("timer_paused")] = paused
            it[booleanPreferencesKey("timer_finished")] = false
            it[stringPreferencesKey("timer_workout")] = "active-v142"
            it[stringPreferencesKey("timer_workout_exercise")] = "second-press-instance"
            it[stringPreferencesKey("unrelated_setting")] = "preserve me"
        }
    }

    private fun bindIsolatedTrainingStore(store: DataStore<Preferences>) {
        // The production top-level delegate caches one process-wide INSTANCE, even for a new
        // Context. Bind this real reopened file only for the test and restore the preceding
        // suite's instance afterwards; changing Context alone cannot isolate this reader.
        val field = Class.forName("com.mooscles.AppPreferencesKt").getDeclaredField("moosclesStore\$delegate")
            .apply { isAccessible = true }
        val delegate = field.get(null)
        val instance = delegate.javaClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val previous = instance.get(delegate)
        instance.set(delegate, store)
        restoreTrainingStore = { instance.set(delegate, previous) }
    }

    private fun exportFixture(source: File, name: String) {
        val app = if (File("src/main/AndroidManifest.xml").isFile) File(".") else File("app")
        val output = File(app, "build/outputs/rollback-device-fixture/datastore").also { it.mkdirs() }
        source.copyTo(File(output, name), overwrite = true)
    }
}
