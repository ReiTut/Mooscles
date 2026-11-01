package com.mooscles.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Installed apps keep their data in `databases/mooscles.db`. Moving the database into the shared
 * module must open exactly that file, at the current version, and never create a second database next to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutDatabaseFileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() {
        context.deleteDatabase("mooscles.db")
    }

    @Test fun productionOpenUsesMoosclesDbInTheDatabasesDirectory() {
        assertEquals("mooscles.db", WorkoutDatabase.FILE_NAME)
        val database = WorkoutDatabase.open(context)
        try {
            runBlocking {
                database.dao().routineFolders()
                assertEquals(6, database.userVersion())
            }
        } finally {
            database.close()
        }
        val file = context.getDatabasePath("mooscles.db")
        assertTrue("The production database must be ${file.path}", file.isFile)
        val databases = file.parentFile!!.listFiles { candidate -> candidate.name.endsWith(".db") }!!
            .map { it.name }
        assertEquals(listOf("mooscles.db"), databases)
    }
}
