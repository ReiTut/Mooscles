package com.mooscles.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test-only [DataStore] kept in memory; [failWrites] makes every update throw. */
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val current = MutableStateFlow(emptyPreferences())
    var failWrites: Boolean = false
    override val data: Flow<Preferences> = current
    val snapshot: Map<String, Any> get() = current.value.asMap().mapKeys { it.key.name }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failWrites) throw IllegalStateException("disk full")
        val updated = transform(current.value)
        current.value = updated
        return updated
    }
}
