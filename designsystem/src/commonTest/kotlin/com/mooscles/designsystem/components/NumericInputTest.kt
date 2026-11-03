package com.mooscles.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumericInputTest {
    @Test
    fun parsesBothSeparators() {
        assertEquals(82.5, NumericInput.parseDecimal("82,5", max = 1000.0))
        assertEquals(82.5, NumericInput.parseDecimal("82.5", max = 1000.0))
        assertEquals(82.0, NumericInput.parseDecimal("82", max = 1000.0))
        assertEquals(1000.0, NumericInput.parseDecimal("1000", max = 1000.0))
    }

    @Test
    fun rejectsInvalid() {
        for (text in listOf("", "-1", "1.234,5", "1.255", "abc", "1 2", "+5", "82,", ",5", "1.2.3")) {
            assertNull(NumericInput.parseDecimal(text, max = 10_000.0), "\"$text\"")
        }
        assertNull(NumericInput.parseDecimal("1000.5", max = 1000.0))
        assertNull(NumericInput.parseDecimal("82.55", maxDecimals = 1, max = 1000.0))
        assertEquals(82.55, NumericInput.parseDecimal("82.55", maxDecimals = 2, max = 1000.0))
    }

    @Test
    fun parsesIntegers() {
        assertEquals(8, NumericInput.parseInteger("8", max = 999))
        assertEquals(999, NumericInput.parseInteger("999", max = 999))
        assertEquals(12, NumericInput.parseInteger("012", max = 999))
        for (text in listOf("", "-1", "8,5", "8.5", "1 0", "abc", "1000", "99999999999")) {
            assertNull(NumericInput.parseInteger(text, max = 999), "\"$text\"")
        }
    }

    @Test
    fun typingAllowsPartialNumbersOnly() {
        assertTrue(NumericInput.isTypable("", NumericKind.Load))
        assertTrue(NumericInput.isTypable("82,", NumericKind.Load))
        assertTrue(NumericInput.isTypable("82.5", NumericKind.Load))
        assertFalse(NumericInput.isTypable("8a", NumericKind.Load))
        assertFalse(NumericInput.isTypable("-", NumericKind.Load))
        assertFalse(NumericInput.isTypable("1.2.3", NumericKind.Load))
        assertTrue(NumericInput.isTypable("12", NumericKind.Reps))
        assertFalse(NumericInput.isTypable("1,5", NumericKind.Reps))
        assertFalse(NumericInput.isTypable("123456789", NumericKind.Reps))
    }
}
