package com.mooscles.designsystem.i18n

import android.content.res.Resources

/**
 * The system's first locale, from the system resources: a per-app language (`LocaleManager`) and
 * the gallery's override ([LocalAppLocale]) change the app's configuration and `Locale.getDefault()`,
 * never these.
 */
actual fun deviceRegion(): String? =
    Resources.getSystem().configuration.locales.takeIf { !it.isEmpty }?.get(0)?.country?.takeIf { it.isNotBlank() }
