package com.mooscles

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single process-wide store behind [AppPreferences] and the rest timer. It stays here, in this
 * file's `AppPreferencesKt` facade, so the same file opens and tests can swap the delegate's instance.
 */
val Context.moosclesStore by preferencesDataStore(name = MoosclesStore.NAME)

fun AppPreferences(context: Context): AppPreferences = AppPreferences(context.moosclesStore)
