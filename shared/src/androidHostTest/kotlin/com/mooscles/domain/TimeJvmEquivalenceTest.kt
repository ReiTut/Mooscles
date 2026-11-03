package com.mooscles.domain

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Backups store `Instant.toString()`; the common text must match what `java.time` wrote. */
class TimeJvmEquivalenceTest {
    @Test
    fun instantTextMatchesJavaTime() {
        val random = Random(1027)
        val millis = listOf(0L, 1L, 999L, 1_000L, 1_000_000L, 1_793_000_000_000L) +
            List(10_000) { random.nextLong(0L, 4_102_444_800_000L) }
        for (ms in millis) {
            assertEquals(java.time.Instant.ofEpochMilli(ms).toString(), Instant.fromEpochMilliseconds(ms).toString(), "ms=$ms")
        }
        val seconds = listOf(0L, 1L, 1_793_000_000L, 4_102_444_799L)
        for (s in seconds) {
            for (n in listOf(0, 1_000_000, 120_000_000, 123_456_000, 123_456_789)) {
                assertEquals(
                    java.time.Instant.ofEpochSecond(s, n.toLong()).toString(),
                    Instant.fromEpochSeconds(s, n).toString(),
                    "s=$s n=$n"
                )
            }
        }
    }

    @Test
    fun currentIsoInstantIsJavaInstantText() {
        val text = currentIsoInstant()
        assertEquals(java.time.Instant.parse(text).toString(), text)
        val difference = abs(java.time.Instant.parse(text).toEpochMilli() - System.currentTimeMillis())
        assertTrue(difference <= 50, "currentIsoInstant() is $difference ms away from the system clock")
    }

    @Test
    fun currentTimeMillisTracksTheSystemClock() {
        val difference = abs(currentTimeMillis() - System.currentTimeMillis())
        assertTrue(difference <= 50, "currentTimeMillis() is $difference ms away from System.currentTimeMillis()")
    }
}
