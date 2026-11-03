package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mooscles.designsystem.gallery.GalleryLanguage
import com.mooscles.designsystem.gallery.GalleryPage
import com.mooscles.designsystem.gallery.GalleryTags
import com.mooscles.designsystem.gallery.galleryEntryTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.ui.DeveloperGallery
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.MoosclesApp
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdateUi
import java.lang.reflect.Proxy
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Review focus 1: switching the gallery to Portuguese and leaving it must bring the rest of the
 * app back in the system language (strings and dates) and keep the navigation where it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryLocaleRestoreHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val checkedAt = 1_798_000_000_000L
    private val state = mutableStateOf(UiState(
        loading = false,
        updates = UpdateUi(installedVersionName = "1.4.5", installedVersionCode = 16, lastCheckedAt = checkedAt),
    ))
    private val probe = mutableStateOf(false)
    private lateinit var system: Locale
    private lateinit var systemConfiguration: Locale

    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Gallery locale actions"
            else -> null
        }
    } as MoosclesActions

    @Before fun start() {
        DeveloperGallery.resetForTest()
        system = Locale.getDefault()
        systemConfiguration = compose.activity.resources.configuration.locales[0]
        compose.setContent {
            Box {
                MoosclesApp(state.value, actions)
                // A string composed outside the gallery after leaving it.
                if (probe.value) BasicText(stringResource(Res.string.ds_licenses_title), Modifier.testTag("outside-probe"))
            }
        }
    }

    @After fun finish() {
        LocalAppLocale.restore()
        DeveloperGallery.resetForTest()
    }

    @Test fun leavingTheGalleryRestoresTheSystemLanguage() {
        openGalleryInPortuguese()
        compose.onNode(hasContentDescription("Voltar") and hasClickAction()).performClick()
        assertBackOnSettingsInTheSystemLanguage()
    }

    @Test fun systemBackClosesThePageThenLeavesInTheSystemLanguage() {
        openGalleryInPortuguese()
        compose.onNodeWithTag(galleryEntryTag(GalleryPage.Chips)).performScrollTo().performClick()
        compose.onNodeWithText("Tipos de chip").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag(GalleryTags.SCREEN).assertExists()
        compose.onNodeWithTag(galleryEntryTag(GalleryPage.Chips)).assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertBackOnSettingsInTheSystemLanguage()
    }

    @Test fun aFooterTabLeavesTheGalleryInTheSystemLanguageWithoutResettingNavigation() {
        openGalleryInPortuguese()
        compose.onNodeWithTag("tab-history").performClick()
        compose.onNodeWithTag(GalleryTags.SCREEN).assertDoesNotExist()
        // A rebuilt NavController would show its start destination, Train, instead.
        compose.onNodeWithTag("tab-history").assertIsSelected()
        assertSystemLanguage()
    }

    private fun openGalleryInPortuguese() {
        compose.onNodeWithContentDescription("Settings").performClick()
        repeat(DeveloperGallery.TAPS) { compose.onNodeWithTag("update-installed-version").performScrollTo().performClick() }
        compose.onNodeWithTag("open-gallery").performScrollTo().performClick()
        compose.onNodeWithTag(GalleryTags.SCREEN).assertExists()
        compose.onNodeWithText("Design gallery").assertExists()
        compose.onNodeWithTag(GalleryTags.language(GalleryLanguage.Portuguese)).performClick()
        compose.onNodeWithText("Galeria de design").assertExists()
        compose.runOnIdle { assertEquals("pt", Locale.getDefault().language) }
    }

    private fun assertBackOnSettingsInTheSystemLanguage() {
        compose.onNodeWithTag(GalleryTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithTag("app-updates").assertExists()
        assertSystemLanguage()
        // The Settings strings (Compose resources) are read again in the system language.
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("App updates").assertExists()
        compose.onNodeWithText("Atualizações do app").assertDoesNotExist()
        // A date formatted by Settings after the return, in the system language.
        val english = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, system).format(Date(checkedAt))
        val portuguese = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("pt-BR")).format(Date(checkedAt))
        assertNotEquals(english, portuguese)
        compose.onNodeWithTag("update-last-checked").performScrollTo().assertTextEquals(english)
        // The back stack is intact: Settings sits on Train, which is the root.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab-routines").assertIsSelected()
        compose.onNodeWithTag("app-updates").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(compose.activity.isFinishing)
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertTrue("Train is the root under Settings", compose.activity.isFinishing)
        }
    }

    private fun assertSystemLanguage() {
        compose.runOnIdle {
            assertEquals(system, Locale.getDefault())
            assertEquals(systemConfiguration, compose.activity.resources.configuration.locales[0])
            assertFalse("The language was already restored when the gallery left", LocalAppLocale.restore())
            probe.value = true
        }
        compose.onNodeWithTag("outside-probe").assertTextEquals("Open-source licenses")
    }
}
