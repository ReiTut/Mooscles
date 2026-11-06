package com.mooscles.designsystem.charts

/** Calendar geometry only: the caller owns dates, time zones and the region's first weekday. */
object CalendarMonthGrid {
    /** Whole weeks, padded with null cells, with every day exactly once. [firstDayOffset] is zero based. */
    fun cells(daysInMonth: Int, firstDayOffset: Int): List<Int?> {
        require(daysInMonth in 28..31)
        require(firstDayOffset in 0..6)
        val count = ((firstDayOffset + daysInMonth + 6) / 7) * 7
        return List(count) { position -> (position - firstDayOffset + 1).takeIf { it in 1..daysInMonth } }
    }
}
