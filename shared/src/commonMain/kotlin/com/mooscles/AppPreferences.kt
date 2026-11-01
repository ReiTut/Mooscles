package com.mooscles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.mooscles.backup.BackupPreferences
import com.mooscles.backup.PreferenceRules
import com.mooscles.ui.PreferencesUi
import com.mooscles.domain.RestDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first

/**
 * Display preferences in the shared store. The platform opens the store once per process and passes it
 * in (Android: `Context.moosclesStore` in :app); the timer's `timer_*` keys live in the same store.
 */
class AppPreferences(private val store: DataStore<Preferences>) {
    private val problem = MutableStateFlow<String?>(null)
    val error = problem.asStateFlow()
    fun clearError() { problem.value = null }
    private val pounds = booleanPreferencesKey("pounds")
    private val theme = stringPreferencesKey("theme")
    private val autoRest = booleanPreferencesKey("auto_rest")
    private val defaultRest = intPreferencesKey("default_rest")
    private val routineFolder = stringPreferencesKey("selected_routine_folder")
    private fun read(saved: Preferences) = PreferencesUi(saved[pounds] ?: false, saved[theme] ?: "system",
        saved[autoRest] ?: true, saved[defaultRest] ?: RestDuration.DEFAULT_SECONDS,
        saved[routineFolder] ?: com.mooscles.data.RoutineFolder.DEFAULT_ID)
    /** Commands read durable settings even while display-state updates are pending. */
    suspend fun snapshot(): PreferencesUi = read(store.data.first())
    // Filter after projection because timer keys share the underlying store.
    val flow = flow {
        var emitted = false
        var backoff = 1000L
        while (true) {
            try {
                store.edit { saved ->
                    saved[defaultRest]?.let { seconds ->
                        if (seconds > RestDuration.MAX_SECONDS) saved[defaultRest] = RestDuration.MAX_SECONDS
                    }
                }
                store.data.collect {
                    emit(read(it))
                    emitted = true
                    backoff = 1000L
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                problem.value = "Could not read saved preferences. Workout data is preserved. ${failure.message}"
                if (!emitted) { emit(PreferencesUi()); emitted = true }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }.distinctUntilChanged()
    private fun requireTheme(value: String) = require(PreferenceRules.validTheme(value))
    private fun requireRest(value: Int) = require(PreferenceRules.validRest(value))
    private fun requireFolder(id: String) = require(id.isNotBlank()) { "Choose a routine folder." }
    suspend fun pounds(value: Boolean) { store.edit { it[pounds] = value } }
    suspend fun theme(value: String) { requireTheme(value); store.edit { it[theme] = value } }
    suspend fun autoRest(value: Boolean) { store.edit { it[autoRest] = value } }
    suspend fun defaultRest(value: Int) { requireRest(value); store.edit { it[defaultRest] = value } }
    suspend fun selectRoutineFolder(id: String) {
        requireFolder(id)
        store.edit { it[routineFolder] = id }
    }
    /** Validates everything first, then writes all five values in one edit, so a bad file changes nothing. */
    suspend fun restore(value: BackupPreferences) {
        requireTheme(value.theme)
        requireRest(value.defaultRestSeconds)
        requireFolder(value.selectedRoutineFolderId)
        store.edit {
            it[pounds] = value.pounds
            it[theme] = value.theme
            it[autoRest] = value.autoRest
            it[defaultRest] = value.defaultRestSeconds
            it[routineFolder] = value.selectedRoutineFolderId
        }
    }
}
