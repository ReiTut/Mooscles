package com.mooscles.screens.progress

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.currentWeekStart
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * What the progress screen and the Records tab read from the world (spec D8): the clock, the zone and the first day of
 * the week (only the progress's weekly chart reads it). Tests pass fixed ones, or a clock they move.
 */
@Immutable
internal class ProgressSeams(
    val nowMillis: () -> Long,
    val zone: () -> TimeZone,
    val weekStart: @Composable () -> DayOfWeek = { currentWeekStart() },
) {
    /** "Today" and the zone, as they are now. */
    fun calendar(): ProgressCalendar {
        val zone = zone()
        return ProgressCalendar(zone, ScreenDates.localDate(nowMillis(), zone))
    }

    companion object {
        val System = ProgressSeams(
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
            zone = { TimeZone.currentSystemDefault() },
        )
    }
}

/** "Today" and the zone a screen reads its dates in (D8). Compared by value, so an equal one skips what reads it. */
@Immutable
internal data class ProgressCalendar(val zone: TimeZone, val today: LocalDate)

/** The [ProgressCalendar] of [seams], read once when the screen opens and again when [key] changes. */
@Composable
internal fun rememberProgressCalendar(seams: ProgressSeams, key: Any? = null): ProgressCalendar =
    remember(seams, key) { seams.calendar() }

/** The width of an item of the progress list: the content column, up to `contentMaxWidth`, with the 16 dp sides. */
@Composable
internal fun Modifier.progressItem(): Modifier =
    widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = MTheme.space.s4)
