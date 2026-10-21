package com.mooscles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesTest {
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
