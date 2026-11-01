package com.mooscles.domain

import kotlin.test.*

class RestDurationTest {
    @Test fun `new default is two minutes with a fixed colon and two seconds digits`() {
        assertEquals(120, RestDuration.DEFAULT_SECONDS)
        assertEquals("2:00", RestDuration.format(RestDuration.DEFAULT_SECONDS))
        assertEquals("2:00", RestDuration.editor(RestDuration.DEFAULT_SECONDS).text)
        assertEquals(300, RestDuration.MAX_SECONDS)
    }

    @Test fun `formats rest as minutes and two digit seconds`() {
        assertEquals("0:00", RestDuration.format(0))
        assertEquals("0:05", RestDuration.format(5))
        assertEquals("1:30", RestDuration.format(90))
        assertEquals("2:00", RestDuration.format(120))
        assertEquals("60:00", RestDuration.format(3600))
    }

    @Test fun `every supported rest duration round trips`() {
        for (seconds in 0..RestDuration.MAX_SECONDS) {
            assertEquals(seconds, RestDuration.parse(RestDuration.format(seconds)), "Round trip for $seconds")
        }
    }

    @Test fun `accepts a fixed minute digit and exactly two second digits`() {
        assertEquals(0, RestDuration.parse("0:00"))
        assertEquals(5, RestDuration.parse("0:05"))
        assertEquals(120, RestDuration.parse("2:00"))
        assertEquals(299, RestDuration.parse("4:59"))
        assertEquals(300, RestDuration.parse("5:00"))
    }

    @Test fun `rejects partial drafts invalid seconds and durations over five minutes`() {
        listOf("", " ", "1", "1:", ":30", "1:5", "1:000", "1:60", "0:60", "60:01", "99:59",
            "100:00", "-1:00", "+1:00", " 1:00", "1:00 ", "a:00", "1:aa", "1.30", "1:30 min",
            "5:01", "6:00", "00:05", "02:00")
            .forEach { assertNull(RestDuration.parse(it), "Must reject '$it'") }
    }

    @Test
    fun `format rejects negative duration`() {
        assertFailsWith<IllegalArgumentException> { RestDuration.format(-1) }
    }

    @Test fun `format preserves durations saved in completed history by earlier versions`() {
        assertEquals("60:01", RestDuration.format(3601))
        assertEquals("7:30", RestDuration.format(450))
    }

    private fun type(value: RestDuration.EditorValue, digits: String): RestDuration.EditorValue {
        val start = minOf(value.selectionStart, value.selectionEnd)
        val end = maxOf(value.selectionStart, value.selectionEnd)
        val text = value.text.take(start) + digits + value.text.drop(end)
        return RestDuration.edit(value, RestDuration.EditorValue(text, start + digits.length, start + digits.length))
    }

    private fun backspace(value: RestDuration.EditorValue): RestDuration.EditorValue {
        val start = minOf(value.selectionStart, value.selectionEnd)
        val end = maxOf(value.selectionStart, value.selectionEnd)
        val from = if (start == end) (start - 1).coerceAtLeast(0) else start
        return RestDuration.edit(value, RestDuration.EditorValue(value.text.removeRange(from, end), from, from))
    }

    @Test fun `minutes first typing automatically selects both seconds slots in order`() {
        var value = type(RestDuration.editor(90), "2")
        assertEquals("2:00", value.text)
        assertEquals(2, value.selectionStart)
        assertEquals(3, value.selectionEnd)
        value = type(value, "3")
        assertEquals("2:30", value.text)
        value = type(value, "0")
        assertEquals("2:30", value.text)
        assertEquals(4, value.selectionStart)
        assertEquals(4, value.selectionEnd)
    }

    @Test fun `a typed zero advances so two zero three means two minutes three seconds`() {
        var value = type(RestDuration.editor(90), "2")
        value = type(value, "0")
        assertEquals(3, value.selectionStart)
        value = type(value, "3")
        assertEquals("2:03", value.text)
    }

    @Test fun `backspace clears digits and crosses the permanent colon`() {
        var value = RestDuration.EditorValue("2:35", 4, 4)
        value = backspace(value)
        assertEquals(RestDuration.EditorValue("2:30", 3, 3), value)
        value = backspace(value)
        assertEquals(RestDuration.EditorValue("2:00", 2, 2), value)
        value = backspace(value)
        assertEquals(RestDuration.EditorValue("0:00", 0, 0), value)
        assertEquals("2:00", type(value, "2").text)
    }

    @Test fun `selection replacement updates seconds or starts a fresh duration`() {
        assertEquals("2:03", type(RestDuration.EditorValue("2:45", 2, 4), "03").text)
        assertEquals("3:00", type(RestDuration.editor(135), "3").text)
        assertEquals("0:00", backspace(RestDuration.editor(135)).text)
        assertEquals("2:03", type(RestDuration.editor(90), "203").text)
        assertEquals("2:30", type(RestDuration.editor(90), "230").text)
        assertEquals("4:59", type(RestDuration.editor(90), "4:59").text)
    }

    @Test fun `invalid typing and pastes preserve valid value and selection`() {
        val previous = RestDuration.editor(120)
        listOf("501", "600", "260", "2003", "5:01", "6:00", "2:003", "1:", "-100", "2.30", "2:60")
            .forEach { assertEquals(previous, type(previous, it), "Must reject $it") }
        val max = type(previous, "5")
        assertEquals("5:00", max.text)
        assertEquals(max, type(max, "1"))
        val end = RestDuration.EditorValue("2:00", 4, 4)
        assertEquals(end, type(end, "3"))
    }

    @Test fun `an invalid seconds tens digit can be replaced without losing the selected slot`() {
        val minutes = type(RestDuration.editor(90), "2")
        assertEquals(minutes, type(minutes, "6"))
        assertEquals("2:50", type(minutes, "5").text)
    }
}
