package com.mooscles.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Pins the ISO text `currentIsoInstant()` writes, so the iOS simulator checks it too.
 * Every expected string was printed by `java.time.Instant.ofEpochSecond(s, n).toString()`.
 */
class TimeTest {
    @Test
    fun instantTextMatchesTheJavaTable() {
        val table = listOf(
            Triple(0L, 0, "1970-01-01T00:00:00Z"),
            Triple(0L, 120_000_000, "1970-01-01T00:00:00.120Z"),
            Triple(1_793_000_000L, 0, "2026-10-26T07:33:20Z"),
            Triple(1_793_000_000L, 1_000_000, "2026-10-26T07:33:20.001Z"),
            Triple(1_793_000_000L, 123_456_000, "2026-10-26T07:33:20.123456Z"),
            Triple(1_793_000_000L, 123_456_789, "2026-10-26T07:33:20.123456789Z"),
            Triple(4_102_444_799L, 999_000_000, "2099-12-31T23:59:59.999Z"),
        )
        for ((seconds, nanos, expected) in table) {
            assertEquals(expected, Instant.fromEpochSeconds(seconds, nanos).toString(), "s=$seconds n=$nanos")
        }
    }

    @Test
    fun currentIsoInstantHasTheJavaShape() {
        val shape = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.(\d{3}|\d{6}|\d{9}))?Z$""")
        val text = currentIsoInstant()
        assertTrue(shape.matches(text), "Not java.time Instant text: $text")
    }
}
