package com.mooscles

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mooscles.ui.PreferencesUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

val Context.moosclesStore by preferencesDataStore(name = "local_preferences")

class AppPreferences(context: Context) {
    private val problem = MutableStateFlow<String?>(null)
    val error = problem.asStateFlow()
    fun clearError() { problem.value = null }
    private val store = context.moosclesStore
    private val pounds = booleanPreferencesKey("pounds")
    private val theme = stringPreferencesKey("theme")
    private val autoRest = booleanPreferencesKey("auto_rest")
    private val defaultRest = intPreferencesKey("default_rest")
    val flow = flow {
        var emitted = false
        var backoff = 1000L
        while (true) {
            try {
                store.data.collect {
                    emit(PreferencesUi(it[pounds] ?: false, it[theme] ?: "system", it[autoRest] ?: true, it[defaultRest] ?: 90))
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
    }
    suspend fun pounds(value: Boolean) { store.edit { it[pounds] = value } }
    suspend fun theme(value: String) { require(value in listOf("light", "dark", "system")); store.edit { it[theme] = value } }
    suspend fun autoRest(value: Boolean) { store.edit { it[autoRest] = value } }
    suspend fun defaultRest(value: Int) { require(value in 0..3600); store.edit { it[defaultRest] = value } }
}
