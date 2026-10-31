package com.mooscles.screens.format

import android.app.LocaleManager
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.designsystem.i18n.deviceRegion
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import org.junit.Assume.assumeTrue

/**
 * Spec D8 on the device: with the app's language set to a plain `en` (a per-app language without a region, as
 * `cmd locale set-app-locales com.mooscles --locales en` leaves it), [currentWeekStart] follows the system's
 * region, which [deviceRegion] reads from the system's locales. On the reference emulator (`en-US`) that is Sunday.
 */
@OptIn(ExperimentalTestApi::class)
class CurrentWeekStartDeviceTest {
    @Test
    fun aRegionlessAppLanguageStartsOnTheDeviceRegionsDay() {
        val saved = Locale.getDefault()
        val system = Resources.getSystem().configuration.locales.get(0).country
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            assertEquals("", Locale.getDefault().country, "the app's language has no region")
            assertTrue(system.isNotBlank(), "the emulator has a system region")
            var region: String? = null
            var shown: DayOfWeek? = null
            runComposeUiTest {
                setContent {
                    region = deviceRegion()
                    shown = currentWeekStart()
                }
                waitForIdle()
            }
            assertEquals(system, region, "the device region is the system's, not the app's")
            assertEquals(WeekStart.of("en", system), shown)
            if (system == "US") assertEquals(DayOfWeek.SUNDAY, shown, "an en-US device starts on Sunday")
        } finally {
            Locale.setDefault(saved)
        }
    }

    /**
     * The real per-app language (Android 13 and later), as the owner sets it: the platform applies it to the process's
     * resources, the system resources included, so the region has to come from the system's own locales (critique of
     * phase 5A, A1). The test app's language is restored afterwards.
     */
    @Test
    fun aPerAppLanguageWithoutRegionKeepsTheSystemRegion() {
        assumeTrue("per-app languages start on Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(LocaleManager::class.java)
        val system = manager.systemLocales.get(0).country
        assertTrue(system.isNotBlank(), "the emulator has a system region")
        val saved = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("en")
            // The language reaches the process as a configuration change; wait (at most 5 s) until the system
            // resources carry it, which is the state the app was in.
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (Resources.getSystem().configuration.locales.get(0).country.isNotEmpty() && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            println("CurrentWeekStartDeviceTest: system resources now ${Resources.getSystem().configuration.locales.toLanguageTags()}")
            var region: String? = null
            var shown: DayOfWeek? = null
            runComposeUiTest {
                setContent {
                    region = deviceRegion()
                    shown = currentWeekStart()
                }
                waitForIdle()
            }
            assertEquals(system, region, "the device region is the system's, not the per-app language's")
            if (system == "US") assertEquals(DayOfWeek.SUNDAY, shown, "an en-US device starts on Sunday")
        } finally {
            manager.applicationLocales = saved
        }
    }
}
