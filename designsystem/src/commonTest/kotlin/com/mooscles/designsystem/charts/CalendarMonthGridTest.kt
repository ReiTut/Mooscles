package com.mooscles.designsystem.charts

import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.tokens.Contrast
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalendarMonthGridTest {
    @Test
    fun alignedFebruaryHasFourCompleteRows() {
        val cells = CalendarMonthGrid.cells(28, 0)
        assertEquals(28, cells.size)
        assertEquals((1..28).toList(), cells)
    }

    @Test
    fun lateStartingLongMonthHasSixRowsWithoutDuplicatingDays() {
        val cells = CalendarMonthGrid.cells(31, 6)
        assertEquals(42, cells.size)
        assertTrue(cells.take(6).all { it == null })
        assertEquals((1..31).toList(), cells.filterNotNull())
        assertTrue(cells.drop(37).all { it == null })
    }

    @Test
    fun everyMonthAndWeekOffsetProducesWholeWeeks() {
        for (days in 28..31) for (offset in 0..6) {
            val cells = CalendarMonthGrid.cells(days, offset)
            assertEquals(0, cells.size % 7)
            assertEquals(offset, cells.indexOf(1))
            assertEquals((1..days).toList(), cells.filterNotNull())
            assertTrue(cells.size in 28..42)
        }
    }

    @Test
    fun impossibleGeometryIsRejected() {
        for (days in listOf(0, 27, 32)) assertFailsWith<IllegalArgumentException> { CalendarMonthGrid.cells(days, 0) }
        for (offset in listOf(-1, 7)) assertFailsWith<IllegalArgumentException> { CalendarMonthGrid.cells(30, offset) }
    }

    @Test
    fun calendarTokensDocumentTheOnlyFortyDpTargetException() {
        val sizes = MoosclesSizes()
        assertEquals(40.dp, sizes.calendarCellMin)
        assertEquals(4.dp, sizes.calendarMultipleDot)
        assertEquals(48.dp, sizes.calendarSwipeDistance)
    }

    @Test
    fun calendarTextAndMarksHaveContrastInBothThemes() {
        for (colors in listOf(MoosclesColors.Light, MoosclesColors.Dark)) {
            assertTrue(Contrast.ratio(colors.textPrimary, colors.bgSurface) >= 4.5)
            assertTrue(Contrast.ratio(colors.textPrimary, colors.accentSubtle) >= 4.5)
            assertTrue(Contrast.ratio(colors.textSecondary, colors.bgSurface) >= 4.5)
            assertTrue(Contrast.ratio(colors.accentText, colors.accentSubtle) >= 3.0)
            assertTrue(Contrast.ratio(colors.borderStrong, colors.bgSurface) >= 3.0)
        }
    }

    @Test
    fun sharePreviewActionsKeepAVisiblePageViewport() {
        assertEquals(180.dp, MoosclesSizes().sharePreviewPlaceholder)
        assertEquals(0.6f, MoosclesSizes().sharePreviewActionsMaxFraction)
    }
}
