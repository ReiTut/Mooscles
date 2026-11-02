package com.mooscles

import android.content.res.Configuration
import android.content.res.Resources
import android.content.pm.ActivityInfo
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AppLanguageUiHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val systemLocales = Resources.getSystem().configuration.locales

    @After fun reset() {
        deviceLanguage(systemLocales)
        AppLanguages.set(compose.activity, AppLanguage.SYSTEM)
        AppLanguages.applyLegacy(compose.activity)
    }

    @Suppress("DEPRECATION")
    private fun deviceLanguage(locales: LocaleList) {
        val resources = Resources.getSystem()
        resources.updateConfiguration(Configuration(resources.configuration).apply { setLocales(locales) }, resources.displayMetrics)
    }

    @Test fun changingLanguageRefreshesResourcesWithoutReplacingNavigationOrFocusedDraft() {
        // Match MainActivity's manifest contract; this generic test Activity otherwise recreates
        // when Robolectric broadcasts a device locale change, losing its test-only setContent.
        val info = compose.activity.packageManager.getActivityInfo(compose.activity.componentName, 0)
        info.configChanges = info.configChanges or ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
        shadowOf(compose.activity.packageManager).addOrUpdateActivity(info)
        lateinit var language: AppLanguageController
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { _, method, _ ->
            when (method.name) { "hashCode" -> 1; "equals" -> false; "toString" -> "Language test"; else -> null }
        } as MoosclesActions
        compose.setContent {
            language = remember { AppLanguageController(compose.activity) }
            CompositionLocalProvider(LocalAppLanguageController provides language, LocalConfiguration provides language.configuration) {
                Column {
                    var draft by remember { mutableStateOf("22.123456789") }
                    BasicTextField(draft, { draft = it }, Modifier.testTag("preserved-draft"))
                    MoosclesApp(UiState(loading = false), actions)
                }
            }
        }
        compose.onNodeWithTag("preserved-draft").performTextReplacement("22.")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language-portuguese").performScrollTo().performClick()
        compose.onNodeWithText("Idioma").assertExists()
        compose.onNodeWithTag("language-portuguese").assertIsSelected()
        compose.onNodeWithTag("preserved-draft").assertTextEquals("22.")
        compose.onNodeWithTag("settings-content").assertExists()
        compose.onNodeWithTag("language-english").performScrollTo().performClick()
        compose.onNodeWithText("Language").assertExists()
        compose.onNodeWithTag("preserved-draft").assertTextEquals("22.")
        compose.onNodeWithTag("language-system").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppLanguage.SYSTEM, language.selected) }
        for ((tag, title) in listOf("pt-BR" to "Idioma", "fr-FR" to "Language", "en-US" to "Language")) {
            compose.runOnIdle {
                deviceLanguage(LocaleList.forLanguageTags(tag))
                compose.activity.onConfigurationChanged(AppLanguages.applyLegacy(compose.activity))
                language.refresh()
            }
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithTag("preserved-draft").assertTextEquals("22.")
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab-routines").assertIsSelected()
    }
}
