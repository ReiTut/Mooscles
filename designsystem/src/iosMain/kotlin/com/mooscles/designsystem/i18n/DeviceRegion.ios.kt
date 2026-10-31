package com.mooscles.designsystem.i18n

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

/** The region of iOS's Language & Region settings, which a per-app language does not change. */
actual fun deviceRegion(): String? =
    (NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String)?.takeIf { it.isNotBlank() }
