package com.mooscles.screens.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek

class WeekStartTest {
    @Test
    fun brazilUsAndCanadaStartOnSunday() {
        for (tag in listOf("pt-BR", "en-US", "en-CA")) {
            assertEquals(DayOfWeek.SUNDAY, WeekStart.of(tag), tag)
        }
    }

    @Test
    fun mostOfEuropeStartsOnMonday() {
        // pt-PT is decided by CLDR (checked against the JVM), not by the language.
        for (tag in listOf("de-DE", "fr-FR", "en-GB")) {
            assertEquals(DayOfWeek.MONDAY, WeekStart.of(tag), tag)
        }
    }

    @Test
    fun aSaturdayRegion() {
        assertEquals(DayOfWeek.SATURDAY, WeekStart.of("ar-EG"))
    }

    @Test
    fun withoutRegionPortugueseIsSundayOthersMonday() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt"))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en"))
    }

    /** Spec D8: an app language without a region (`en`, chosen per app) starts on the device region's day. */
    @Test
    fun aRegionlessLanguageTakesTheDeviceRegion() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("en", deviceRegion = "US"))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en", deviceRegion = "GB"))
        assertEquals(DayOfWeek.SATURDAY, WeekStart.of("ar", deviceRegion = "eg"))
        // The app language's own region wins over the device's.
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en-GB", deviceRegion = "US"))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt-BR", deviceRegion = "DE"))
        // No usable device region: by the language, as before.
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en", deviceRegion = null))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt", deviceRegion = ""))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en", deviceRegion = "419"))
    }

    @Test
    fun unknownRegionFallsBackByLanguage() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt-ZZ"))
        assertEquals(DayOfWeek.MONDAY, WeekStart.of("en-ZZ"))
    }

    @Test
    fun scriptAndExtensionsDoNotHideTheRegion() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("zh-Hant-TW"))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("pt_BR"))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.of("en-US-u-ca-gregory"))
    }
}
