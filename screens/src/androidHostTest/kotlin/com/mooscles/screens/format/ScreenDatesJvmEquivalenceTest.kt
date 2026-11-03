package com.mooscles.screens.format

import com.mooscles.ui.WorkoutUi
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common calendar math against java.time, around daylight saving changes and odd offsets. */
class ScreenDatesJvmEquivalenceTest {
    private val zones = listOf("America/Sao_Paulo", "Europe/Berlin", "UTC", "Asia/Kolkata")

    /** Every 7 minutes, 3 days either side of each change. */
    private val sample: List<Long> by lazy { buildInstants() }
    private fun instants() = sample

    private fun buildInstants(): List<Long> {
        val centers = listOf(
            "2018-11-04T03:00:00Z", "2019-02-17T02:00:00Z", // Sao Paulo (clocks forward, then back)
            "2026-03-29T01:00:00Z", "2026-10-25T01:00:00Z", // Berlin
            "2026-06-15T12:00:00Z",
        ).map { Instant.parse(it) }
        val result = mutableListOf<Long>()
        for (center in centers) {
            var t = center.minus(3, ChronoUnit.DAYS)
            val end = center.plus(3, ChronoUnit.DAYS)
            while (t < end) {
                result += t.toEpochMilli()
                t = t.plus(7, ChronoUnit.MINUTES)
            }
        }
        return result
    }

    @Test
    fun localDateTimeMatchesJavaTime() {
        for (id in zones) {
            val java = ZoneId.of(id)
            val zone = kotlinx.datetime.TimeZone.of(id)
            for (ms in instants()) {
                val expected = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), java).toLocalDateTime()
                val actual = ScreenDates.localDateTime(ms, zone)
                val label = "$id @ $ms"
                assertEquals(expected.year, actual.year, label)
                assertEquals(expected.monthValue, actual.month.ordinal + 1, label)
                assertEquals(expected.dayOfMonth, actual.day, label)
                assertEquals(expected.hour, actual.hour, label)
                assertEquals(expected.minute, actual.minute, label)
                assertEquals(expected.second, actual.second, label)
                assertEquals(expected.dayOfWeek.value, actual.dayOfWeek.ordinal + 1, label)
            }
        }
    }

    @Test
    fun windowMatchesJavaTime() {
        val all = instants()
        for (id in zones) {
            val java = ZoneId.of(id)
            val zone = kotlinx.datetime.TimeZone.of(id)
            val history = all.filterIndexed { i, _ -> i % 5 == 0 }.map {
                WorkoutUi(id = "w$it", name = "W", startedAt = it - 1000, endedAt = it)
            }
            for (now in all.filterIndexed { i, _ -> i % 997 == 0 }) {
                val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), java).toLocalDate()
                val first = today.minusDays(27)
                val expected = history.count {
                    val day = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.endedAt!!), java).toLocalDate()
                    !day.isBefore(first) && !day.isAfter(today)
                }
                assertEquals(expected, ScreenDates.finishedInLast28Days(history, now, zone), "$id @ $now")
            }
        }
    }
}
