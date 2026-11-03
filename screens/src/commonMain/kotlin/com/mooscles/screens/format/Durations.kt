package com.mooscles.screens.format

import com.mooscles.designsystem.format.MoosclesNumbers

/** Elapsed time as a clock, the same in every language. */
object Durations {
    /** `42:18` under an hour (the design system's m:ss), `1:02:18` from an hour on. */
    fun clock(elapsedSeconds: Long): String {
        val seconds = elapsedSeconds.coerceAtLeast(0L)
        if (seconds < 3600L) return MoosclesNumbers.clock(seconds.toInt())
        val hours = seconds / 3600L
        val rest = (seconds % 3600L).toInt()
        val minutes = rest / 60
        val secs = rest % 60
        return "$hours:${two(minutes)}:${two(secs)}"
    }
}

internal fun two(value: Int) = if (value < 10) "0$value" else "$value"
