package com.mooscles

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Empty is Android's System default, not a persisted copy of the current device language. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""), ENGLISH("en"), PORTUGUESE("pt-BR");

    companion object {
        fun fromTag(tag: String) = when (tag.substringBefore(',').substringBefore('-').lowercase()) {
            "en" -> ENGLISH
            "pt" -> PORTUGUESE
            else -> SYSTEM
        }
    }
}

/** Android-only locale metadata. It never writes to workout preferences, backups or timer storage. */
object AppLanguages {
    private const val FILE = "app_language"
    private const val KEY = "language"

    private fun legacy(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selection(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= 33) {
        AppLanguage.fromTag(context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
    } else AppLanguage.fromTag(legacy(context).getString(KEY, "").orEmpty())

    /** Transfer once on an OS upgrade; after this only LocaleManager owns the selection. */
    fun migrateToFramework(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = legacy(context)
        if (!prefs.contains(KEY)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty) {
            manager.applicationLocales = LocaleList.forLanguageTags(prefs.getString(KEY, "").orEmpty())
        }
        prefs.edit().remove(KEY).apply()
    }

    fun set(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            legacy(context).edit().apply {
                if (language == AppLanguage.SYSTEM) remove(KEY) else putString(KEY, language.tag)
            }.apply()
        }
    }

    /** Before Android 13 the platform has no per-app override. Use an isolated base context. */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val config = configuration(context)
        LocaleList.setDefault(config.locales)
        return context.createConfigurationContext(config)
    }

    fun configuration(context: Context): Configuration = Configuration(context.resources.configuration).apply {
        val language = selection(context)
        setLocales(if (language == AppLanguage.SYSTEM) Resources.getSystem().configuration.locales
            else LocaleList.forLanguageTags(language.tag))
    }

    /** Deliver a locale-only change in place; do not key/recreate the remembered editor tree. */
    @Suppress("DEPRECATION")
    fun applyLegacy(context: Context): Configuration {
        val config = configuration(context)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        LocaleList.setDefault(config.locales)
        return config
    }
}

class AppLanguageController(private val activity: ComponentActivity) {
    var selected by mutableStateOf(AppLanguages.selection(activity))
        private set
    var configuration by mutableStateOf(Configuration(activity.resources.configuration))
        private set

    fun refresh() {
        selected = AppLanguages.selection(activity)
        configuration = Configuration(activity.resources.configuration)
    }

    fun select(language: AppLanguage) {
        if (selected == language) return
        AppLanguages.set(activity, language)
        refresh()
        if (Build.VERSION.SDK_INT < 33) {
            AppLanguages.applyLegacy(activity.application)
            activity.onConfigurationChanged(AppLanguages.applyLegacy(activity))
            // A manual legacy resource update does not dispatch ViewRoot's configuration event.
            // Publish its copy to Compose too, without replacing remembered navigation/editors.
            refresh()
        }
    }
}

val LocalAppLanguageController = staticCompositionLocalOf<AppLanguageController?> { null }
