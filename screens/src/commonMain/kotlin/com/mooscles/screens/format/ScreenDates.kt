package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.date_day_fri
import com.mooscles.screens.resources.date_day_mon
import com.mooscles.screens.resources.date_day_sat
import com.mooscles.screens.resources.date_day_sun
import com.mooscles.screens.resources.date_day_thu
import com.mooscles.screens.resources.date_day_tue
import com.mooscles.screens.resources.date_day_wed
import com.mooscles.screens.resources.date_month_apr
import com.mooscles.screens.resources.date_month_full_apr
import com.mooscles.screens.resources.date_month_full_aug
import com.mooscles.screens.resources.date_month_full_dec
import com.mooscles.screens.resources.date_month_full_feb
import com.mooscles.screens.resources.date_month_full_jan
import com.mooscles.screens.resources.date_month_full_jul
import com.mooscles.screens.resources.date_month_full_jun
import com.mooscles.screens.resources.date_month_full_mar
import com.mooscles.screens.resources.date_month_full_may
import com.mooscles.screens.resources.date_month_full_nov
import com.mooscles.screens.resources.date_month_full_oct
import com.mooscles.screens.resources.date_month_full_sep
import com.mooscles.screens.resources.date_month_year
import com.mooscles.screens.resources.date_spoken
import com.mooscles.screens.resources.date_spoken_year
import com.mooscles.screens.resources.progress_week_of
import com.mooscles.screens.resources.date_month_aug
import com.mooscles.screens.resources.date_month_dec
import com.mooscles.screens.resources.date_month_feb
import com.mooscles.screens.resources.date_month_jan
import com.mooscles.screens.resources.date_month_jul
import com.mooscles.screens.resources.date_month_jun
import com.mooscles.screens.resources.date_month_mar
import com.mooscles.screens.resources.date_month_may
import com.mooscles.screens.resources.date_month_nov
import com.mooscles.screens.resources.date_month_oct
import com.mooscles.screens.resources.date_month_sep
import com.mooscles.screens.resources.date_range_format
import com.mooscles.screens.resources.date_short_format
import com.mooscles.screens.resources.duration_hours
import com.mooscles.screens.resources.duration_hours_minutes
import com.mooscles.screens.resources.duration_under_minute
import com.mooscles.screens.resources.duration_minutes
import com.mooscles.ui.WorkoutUi
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Calendar math in common code: kotlinx-datetime only for the time zone rules, never for text. */
object ScreenDates {
    /** The wall-clock date and time of [epochMillis] in [zone], daylight saving included. */
    fun localDateTime(epochMillis: Long, zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)

    /** The calendar date of [epochMillis] in [zone]. */
    fun localDate(epochMillis: Long, zone: TimeZone): LocalDate = localDateTime(epochMillis, zone).date

    /**
     * Finished workouts whose end falls on one of the last 28 calendar days of [zone], today
     * included (today minus 27 days through today). A workout still running does not count.
     */
    fun finishedInLast28Days(history: List<WorkoutUi>, nowMillis: Long, zone: TimeZone): Int {
        val today = localDateTime(nowMillis, zone).date
        val first = today.minus(DatePeriod(days = 27))
        return history.count { workout ->
            val end = workout.endedAt ?: return@count false
            val day = localDateTime(end, zone).date
            day >= first && day <= today
        }
    }

    /** `outubro de 2026` / `October 2026`: the header of a month. */
    @Composable
    fun monthYear(year: Int, month: Month): String =
        stringResource(Res.string.date_month_year, stringResource(fullMonthNames[month.number - 1]), year.toString())
}

private val fullMonthNames: List<StringResource> = listOf(
    Res.string.date_month_full_jan, Res.string.date_month_full_feb, Res.string.date_month_full_mar,
    Res.string.date_month_full_apr, Res.string.date_month_full_may, Res.string.date_month_full_jun,
    Res.string.date_month_full_jul, Res.string.date_month_full_aug, Res.string.date_month_full_sep,
    Res.string.date_month_full_oct, Res.string.date_month_full_nov, Res.string.date_month_full_dec,
)

private val monthNames: List<StringResource> = listOf(
    Res.string.date_month_jan, Res.string.date_month_feb, Res.string.date_month_mar,
    Res.string.date_month_apr, Res.string.date_month_may, Res.string.date_month_jun,
    Res.string.date_month_jul, Res.string.date_month_aug, Res.string.date_month_sep,
    Res.string.date_month_oct, Res.string.date_month_nov, Res.string.date_month_dec,
)

private fun weekdayName(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.date_day_mon
    DayOfWeek.TUESDAY -> Res.string.date_day_tue
    DayOfWeek.WEDNESDAY -> Res.string.date_day_wed
    DayOfWeek.THURSDAY -> Res.string.date_day_thu
    DayOfWeek.FRIDAY -> Res.string.date_day_fri
    DayOfWeek.SATURDAY -> Res.string.date_day_sat
    DayOfWeek.SUNDAY -> Res.string.date_day_sun
}

/** `21 out` / `Oct 21`. */
@Composable
fun shortDate(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = ScreenDates.localDate(epochMillis, zone)
    return stringResource(Res.string.date_short_format, date.day, stringResource(monthNames[date.month.number - 1]))
}

/**
 * A date to be read aloud, in full: `3 de junho` / `June 3`, with the year (`3 de junho de 2025`) only when it is not
 * the year of [today].
 */
@Composable
fun spokenDate(epochMillis: Long, zone: TimeZone, today: LocalDate): String {
    val date = ScreenDates.localDate(epochMillis, zone)
    val month = stringResource(fullMonthNames[date.month.number - 1])
    return if (date.year == today.year) {
        stringResource(Res.string.date_spoken, date.day, month)
    } else {
        stringResource(Res.string.date_spoken_year, date.day, month, date.year)
    }
}

/** `Semana de 6 out` / `Week of Oct 6`: a weekly point is dated at the start of its week (P25), and always reads like this. */
@Composable
fun weekOf(weekStartMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    stringResource(Res.string.progress_week_of, shortDate(weekStartMillis, zone))

/** `ter., 29 out · 18:02–18:44` / `Tue, Oct 29 · 18:02–18:44` (24 h clock in both languages). */
@Composable
fun summaryDateRange(startMillis: Long, endMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val start = ScreenDates.localDateTime(startMillis, zone)
    val end = ScreenDates.localDateTime(endMillis, zone)
    return stringResource(
        Res.string.date_range_format,
        stringResource(weekdayName(start.dayOfWeek)),
        shortDate(startMillis, zone),
        "${two(start.hour)}:${two(start.minute)}",
        "${two(end.hour)}:${two(end.minute)}",
    )
}

/**
 * `< 1 min` under a minute, `42 min` under an hour, `1 h` on whole hours and `1 h 12 min`
 * otherwise (whole minutes, minutes not zero-padded).
 */
@Composable
fun durationText(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val minutes = (total / 60L).toInt()
    return when {
        total < 60L -> stringResource(Res.string.duration_under_minute)
        minutes < 60 -> stringResource(Res.string.duration_minutes, minutes)
        minutes % 60 == 0 -> stringResource(Res.string.duration_hours, minutes / 60)
        else -> stringResource(Res.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
