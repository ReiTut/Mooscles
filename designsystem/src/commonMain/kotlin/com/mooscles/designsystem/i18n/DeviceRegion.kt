package com.mooscles.designsystem.i18n

/**
 * The device's region as set in the system (ISO 3166 alpha-2, such as `US` or `BR`), not the
 * app's language: an app language without a region (`en`, chosen per app) leaves this one
 * as it is. Null when the system has none. The screens fall back to it for what is regional
 * rather than linguistic, such as the first day of the week (spec D8).
 */
expect fun deviceRegion(): String?
