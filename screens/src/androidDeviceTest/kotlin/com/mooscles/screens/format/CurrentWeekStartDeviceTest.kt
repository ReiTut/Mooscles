package com.mooscles.screens.format

import android.content.res.Resources
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.deviceRegion
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek

/**
 * Spec D8 on the device: with the app's language set to a plain `en` (a per-app language without a region, as
 * `cmd locale set-app-locales com.mooscles --locales en` leaves it), [currentWeekStart] follows the system's
 * region, which [deviceRegion] reads from the system resources. On the reference emulator (`en-US`) that is Sunday.
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
            assertEquals(system, deviceRegion(), "the device region is the system's, not the app's")
            var shown: DayOfWeek? = null
            runComposeUiTest {
                setContent { shown = currentWeekStart() }
                waitForIdle()
            }
            assertEquals(WeekStart.of("en", system), shown)
            if (system == "US") assertEquals(DayOfWeek.SUNDAY, shown, "an en-US device starts on Sunday")
        } finally {
            Locale.setDefault(saved)
        }
    }
}
