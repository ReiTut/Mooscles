package com.mooscles

import android.app.Application
import android.app.LocaleManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLanguageHostTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var original: LocaleList

    @Before fun before() {
        original = Resources.getSystem().configuration.locales
        context.getSharedPreferences("app_language", 0).edit().clear().commit()
    }
    @After fun after() {
        val config = Configuration(Resources.getSystem().configuration).apply { setLocales(original) }
        @Suppress("DEPRECATION")
        Resources.getSystem().updateConfiguration(config, Resources.getSystem().displayMetrics)
        LocaleList.setDefault(original)
    }

    @Test fun frameworkSelectionIsTheOnlyAuthorityAndSupportsExternalChanges() {
        val os = context.getSystemService(LocaleManager::class.java)
        assertEquals(AppLanguage.SYSTEM, AppLanguages.selection(context))
        AppLanguages.set(context, AppLanguage.PORTUGUESE)
        assertEquals("pt-BR", os.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.PORTUGUESE, AppLanguages.selection(context))
        os.applicationLocales = LocaleList.forLanguageTags("en")
        assertEquals(AppLanguage.ENGLISH, AppLanguages.selection(context))
        os.applicationLocales = LocaleList.getEmptyLocaleList()
        assertEquals(AppLanguage.SYSTEM, AppLanguages.selection(context))
        assertFalse(context.getSharedPreferences("app_language", 0).contains("language"))
    }

    @Test fun osUpgradeMigratesOnceAndNeverResurrectsASelectionClearedInSettings() {
        context.getSharedPreferences("app_language", 0).edit().putString("language", "pt-BR").commit()
        AppLanguages.migrateToFramework(context)
        assertEquals(AppLanguage.PORTUGUESE, AppLanguages.selection(context))
        AppLanguages.set(context, AppLanguage.SYSTEM)
        AppLanguages.migrateToFramework(context)
        assertEquals(AppLanguage.SYSTEM, AppLanguages.selection(context))
    }

    @Test fun osUpgradePreservesAnExistingFrameworkChoice() {
        context.getSharedPreferences("app_language", 0).edit().putString("language", "pt-BR").commit()
        AppLanguages.set(context, AppLanguage.ENGLISH)
        AppLanguages.migrateToFramework(context)
        assertEquals(AppLanguage.ENGLISH, AppLanguages.selection(context))
    }

    @Config(sdk = [28])
    @Test fun legacySelectionPersistsInNewContextsWithoutTouchingWorkoutPreferences() {
        val unrelated = context.getSharedPreferences("workout-sentinel", 0)
        unrelated.edit().putLong("weight-bits", 22.123456789.toBits()).putLong("timer-deadline", 987654321).commit()
        AppLanguages.set(context, AppLanguage.PORTUGUESE)
        val reopened = context.createConfigurationContext(Configuration(context.resources.configuration))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguages.selection(reopened))
        assertEquals("pt-BR", AppLanguages.wrap(reopened).resources.configuration.locales[0].toLanguageTag())
        assertEquals(22.123456789.toBits(), unrelated.getLong("weight-bits", 0))
        assertEquals(987654321L, unrelated.getLong("timer-deadline", 0))
    }

    @Config(sdk = [28])
    @Test fun systemDefaultTracksDeviceChangesInsteadOfTheAppModifiedDefaultLocale() {
        fun device(tag: String) {
            val config = Configuration(Resources.getSystem().configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            @Suppress("DEPRECATION")
            Resources.getSystem().updateConfiguration(config, Resources.getSystem().displayMetrics)
        }
        device("en-US")
        assertEquals(AppLanguage.SYSTEM, AppLanguages.selection(context))
        AppLanguages.set(context, AppLanguage.PORTUGUESE)
        AppLanguages.wrap(context)
        assertEquals("pt", Locale.getDefault().language)
        AppLanguages.set(context, AppLanguage.SYSTEM)
        assertEquals("en-US", AppLanguages.wrap(context).resources.configuration.locales[0].toLanguageTag())
        device("pt-BR")
        assertEquals("pt-BR", AppLanguages.wrap(context).resources.configuration.locales[0].toLanguageTag())
        device("fr-FR")
        assertEquals("Mooscles", AppLanguages.wrap(context).getString(R.string.app_name))
        assertEquals(AppLanguage.SYSTEM, AppLanguages.selection(context))
        assertFalse(context.getSharedPreferences("app_language", 0).contains("language"))
    }

    @Test fun advertisedLanguagesMatchThePicker() {
        val parser = context.resources.getXml(R.xml.locales_config)
        val tags = mutableListOf<String>()
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.name == "locale")
                    tags += it.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
            }
        }
        assertEquals(AppLanguage.entries.filterNot { it == AppLanguage.SYSTEM }.map { it.tag }, tags)
    }
}
