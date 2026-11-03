package com.mooscles.screens.format

import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenDatesTest {
    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    private fun toEpoch(dt: LocalDateTime, zone: TimeZone): Long = dt.toInstant(zone).toEpochMilliseconds()

    private fun finished(end: Long) = WorkoutUi(id = "w$end", name = "W", startedAt = end - 1000, endedAt = end)

    @Test
    fun windowCountsTwentyEightCalendarDaysIncludingToday() {
        val now = toEpoch(LocalDateTime(2026, 10, 29, 10, 0), saoPaulo)
        val history = listOf(
            finished(toEpoch(LocalDateTime(2026, 10, 2, 8, 0), saoPaulo)),
            finished(toEpoch(LocalDateTime(2026, 10, 1, 23, 59), saoPaulo)),
        )
        assertEquals(1, ScreenDates.finishedInLast28Days(history, now, saoPaulo))
    }

    @Test
    fun windowUsesTheDeviceZone() {
        val now = toEpoch(LocalDateTime(2026, 10, 29, 10, 0), saoPaulo)
        // 2026-10-02T01:00Z is 2026-10-01 22:00 in Sao Paulo (UTC-3): outside the window there.
        val end = toEpoch(LocalDateTime(2026, 10, 2, 1, 0), TimeZone.UTC)
        assertEquals(0, ScreenDates.finishedInLast28Days(listOf(finished(end)), now, saoPaulo))
        assertEquals(1, ScreenDates.finishedInLast28Days(listOf(finished(end)), now, TimeZone.UTC))
    }

    @Test
    fun runningAndFutureWorkoutsDoNotCount() {
        val now = toEpoch(LocalDateTime(2026, 10, 29, 10, 0), saoPaulo)
        val running = WorkoutUi(id = "r", name = "R", startedAt = now - 1000)
        val tomorrow = finished(toEpoch(LocalDateTime(2026, 10, 30, 9, 0), saoPaulo))
        assertEquals(0, ScreenDates.finishedInLast28Days(listOf(running, tomorrow), now, saoPaulo))
    }

    @Test
    fun clockFormats() {
        assertEquals("42:18", Durations.clock(2538))
        assertEquals("1:02:18", Durations.clock(3738))
        assertEquals("0:00", Durations.clock(0))
        assertEquals("59:59", Durations.clock(3599))
        assertEquals("1:00:00", Durations.clock(3600))
    }
}
