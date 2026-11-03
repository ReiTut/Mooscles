package com.mooscles

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installed apps keep their preferences and timer only if the delegate keeps opening this exact file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MoosclesStoreTest {
    @Test fun delegateFileIsFilesDatastoreLocalPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            File(context.filesDir, "datastore/local_preferences.preferences_pb"),
            context.preferencesDataStoreFile(MoosclesStore.NAME),
        )
        assertEquals("${MoosclesStore.NAME}.preferences_pb", MoosclesStore.FILE_NAME)
        assertEquals("local_preferences", MoosclesStore.NAME)
    }
}
