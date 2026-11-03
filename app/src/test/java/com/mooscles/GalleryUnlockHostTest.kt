package com.mooscles

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mooscles.ui.DeveloperGallery
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.MoosclesTheme
import com.mooscles.ui.SettingsScreen
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdateActions
import com.mooscles.ui.UpdateSection
import com.mooscles.ui.UpdateUi
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The hidden gallery entry: seven quick taps on the installed version. The unlock is memory only:
 * it writes no preference and lasts while the process lives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryUnlockHostTest {
    @get:Rule val compose = createComposeRule()

    private val updates = UpdateUi(installedVersionName = "1.4.5", installedVersionCode = 16)

    @Before fun lock() = DeveloperGallery.resetForTest()

    @After fun relock() = DeveloperGallery.resetForTest()

    @Test fun sevenQuickTapsRevealTheEntry() {
        var opened = 0
        compose.setContent { Section { UpdateSection(updates, NoUpdateActions, onOpenGallery = { opened++ }) } }
        repeat(6) {
            compose.onNodeWithTag("update-installed-version").performScrollTo().performClick()
            compose.onNodeWithTag("open-gallery").assertDoesNotExist()
        }
        compose.onNodeWithTag("update-installed-version").performScrollTo().performClick()
        compose.onNodeWithTag("open-gallery").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(DeveloperGallery.unlocked)
            assertEquals(1, opened)
        }
    }

    @Test fun slowTapsRestartTheCount() {
        var now = 1_000L
        // Six taps exactly 2,000 ms apart still count; the seventh comes 2,001 ms later.
        repeat(6) { tap ->
            if (tap > 0) now += 2_000
            assertFalse(DeveloperGallery.registerVersionTap(now))
        }
        now += 2_001
        assertFalse("A gap of 2,001 ms starts the count over", DeveloperGallery.registerVersionTap(now))
        assertFalse(DeveloperGallery.unlocked)
        repeat(5) { now += 500; assertFalse(DeveloperGallery.registerVersionTap(now)) }
        now += 2_000
        assertTrue("The seventh tap after the restart unlocks", DeveloperGallery.registerVersionTap(now))
        assertTrue(DeveloperGallery.unlocked)
    }

    @Test fun unlockWritesNoPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The production delegate caches one store process-wide (see AppPreferencesTest): bind a
        // file of this test, then put the previous instance back.
        val storeJob = SupervisorJob()
        val directory = Files.createTempDirectory("mooscles-gallery-unlock-").toFile()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(storeJob + Dispatchers.IO), produceFile = { directory.resolve("isolated.preferences_pb") })
        val delegate = Class.forName("com.mooscles.AppPreferencesKt")
            .getDeclaredField("moosclesStore\$delegate").apply { isAccessible = true }.get(null)
        val instance = delegate.javaClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val previous = instance.get(delegate)
        instance.set(delegate, store)
        try {
            store.edit { it[booleanPreferencesKey("pounds")] = true }
            val calls = mutableListOf<String>()
            val before = store.data.first().asMap()
            val filesBefore = files(directory) + files(context.filesDir)
            compose.setContent {
                MoosclesTheme("light") { SettingsScreen(UiState(loading = false, updates = updates), recording(calls)) }
            }
            repeat(DeveloperGallery.TAPS) { compose.onNodeWithTag("update-installed-version").performScrollTo().performClick() }
            compose.onNodeWithTag("open-gallery").performScrollTo()
            compose.waitForIdle()
            assertTrue(DeveloperGallery.unlocked)
            assertEquals("Unlocking calls no action", emptyList<String>(), calls)
            assertEquals(before, store.data.first().asMap())
            assertEquals(mapOf<Preferences.Key<*>, Any>(Pair(booleanPreferencesKey("pounds"), true)), before)
            assertEquals("No preference file is written or changed", filesBefore, files(directory) + files(context.filesDir))
        } finally {
            storeJob.cancelAndJoin()
            instance.set(delegate, previous)
            directory.deleteRecursively()
        }
    }

    /** Every file under [root], by relative path, with its bytes as a comparable list. */
    private fun files(root: File): Map<String, List<Byte>> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }

    private fun recording(calls: MutableList<String>): MoosclesActions = Proxy.newProxyInstance(
        MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Recording actions"
            else -> { calls += method.name; null }
        }
    } as MoosclesActions
}

/** Recreating the Activity keeps the entry: the unlock belongs to the process, not the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryUnlockRecreationHostTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun lock() = DeveloperGallery.resetForTest()

    @After fun relock() = DeveloperGallery.resetForTest()

    @Test fun unlockSurvivesRecreation() {
        val updates = UpdateUi(installedVersionName = "1.4.5", installedVersionCode = 16)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { Section { UpdateSection(updates, NoUpdateActions) } } }
            repeat(DeveloperGallery.TAPS) { compose.onNodeWithTag("update-installed-version").performScrollTo().performClick() }
            compose.onNodeWithTag("open-gallery").assertExists()
            scenario.recreate()
            scenario.onActivity { it.setContent { Section { UpdateSection(updates, NoUpdateActions) } } }
            compose.onNodeWithTag("update-installed-version").assertExists()
            compose.onNodeWithTag("open-gallery").assertExists()
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
}

private object NoUpdateActions : UpdateActions {
    override fun checkForUpdates() = Unit
    override fun setAutomaticUpdates(enabled: Boolean) = Unit
    override fun setUpdateWifiOnly(enabled: Boolean) = Unit
    override fun setIncludePrereleases(enabled: Boolean) = Unit
    override fun downloadUpdate() = Unit
    override fun cancelUpdate() = Unit
    override fun installUpdate() = Unit
}
