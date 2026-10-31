package com.mooscles.designsystem.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The system's first locale's region. From Android 13 on it comes from [LocaleManager.getSystemLocales]: a per-app
 * language (`cmd locale set-app-locales … en`) is applied to the process's resources, the system resources included,
 * so `Resources.getSystem()` reads the app's `en` there and loses the system's `US`. Before Android 13 there is no
 * per-app language in the platform, and the system resources are the system's. The gallery's override
 * ([LocalAppLocale]) changes neither.
 */
@Composable
actual fun deviceRegion(): String? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { systemRegion(context) }
}

/** [deviceRegion] outside a composition. */
fun systemRegion(context: Context): String? {
    val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.systemLocales
    } else {
        null
    }
    val locales: LocaleList = system?.takeIf { !it.isEmpty } ?: Resources.getSystem().configuration.locales
    return locales.takeIf { !it.isEmpty }?.get(0)?.country?.takeIf { it.isNotBlank() }
}
