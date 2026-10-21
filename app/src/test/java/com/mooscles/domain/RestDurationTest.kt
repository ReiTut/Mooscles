package com.mooscles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestDurationTest {
    @Test fun `formats rest as minutes and two digit seconds`() {
        assertEquals("0:00", RestDuration.format(0))
        assertEquals("0:05", RestDuration.format(5))
        assertEquals("1:30", RestDuration.format(90))
        assertEquals("2:00", RestDuration.format(120))
        assertEquals("60:00", RestDuration.format(3600))
    }

    @Test fun `every supported rest duration round trips`() {
        for (seconds in 0..3600) {
            assertEquals("Round trip for $seconds", seconds, RestDuration.parse(RestDuration.format(seconds)))
        }
    }

    @Test fun `accepts one or two minute digits and exactly two second digits`() {
        assertEquals(0, RestDuration.parse("0:00"))
        assertEquals(5, RestDuration.parse("00:05"))
        assertEquals(120, RestDuration.parse("2:00"))
        assertEquals(120, RestDuration.parse("02:00"))
        assertEquals(3599, RestDuration.parse("59:59"))
        assertEquals(3600, RestDuration.parse("60:00"))
    }

    @Test fun `rejects partial drafts invalid seconds and durations over one hour`() {
        listOf("", " ", "1", "1:", ":30", "1:5", "1:000", "1:60", "0:60", "60:01", "99:59",
            "100:00", "-1:00", "+1:00", " 1:00", "1:00 ", "a:00", "1:aa", "1.30", "1:30 min")
            .forEach { assertNull("Must reject '$it'", RestDuration.parse(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `format rejects negative duration`() {
        RestDuration.format(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `format rejects duration over one hour`() {
        RestDuration.format(3601)
    }
}
