package com.mooscles.timer

import com.mooscles.domain.RestDuration

/** Wall-clock deadline survives process death and includes time spent outside the app. */
data class RestTimerState(
    val token: Long = 0, val exerciseName: String = "Rest timer",
    val durationMillis: Long = RestDuration.DEFAULT_SECONDS * 1000L,
    val deadline: Long = 0, val pausedMillis: Long = 0, val finished: Boolean = false,
    val workoutId: String? = null,
    /** Elapsed plus remaining time for this interval, including accepted +/- adjustments. */
    val intervalMillis: Long = durationMillis,
    /** Instance ownership survives ordering changes and distinguishes repeated movements. */
    val workoutExerciseId: String? = null,
) {
    val running get() = deadline > 0 && !finished
    val paused get() = pausedMillis > 0 && !finished
    fun remaining(now: Long) = if (running) (deadline - now).coerceAtLeast(0) else pausedMillis
    fun progress(now: Long): Float {
        if (finished) return 0f
        val remaining = remaining(now).coerceIn(0, MAX_MILLIS)
        if (remaining == 0L) return 0f
        return (remaining.toDouble() / intervalMillis.coerceAtLeast(remaining).toDouble())
            .toFloat().coerceIn(0f, 1f)
    }
    // A late tap must not swallow an expiration before the ticker/receiver can alert.
    fun pause(now: Long) = if (running && remaining(now) > 0)
        copy(deadline = 0, pausedMillis = remaining(now)) else this
    fun resume(now: Long) = if (paused) copy(deadline = now + pausedMillis, pausedMillis = 0) else this
    /** Preserve elapsed/background time when normalizing timers saved by an earlier version. */
    fun bounded(now: Long): RestTimerState {
        val remaining = remaining(now).coerceIn(0, MAX_MILLIS)
        return copy(
            durationMillis = durationMillis.coerceIn(0, MAX_MILLIS),
            pausedMillis = pausedMillis.coerceIn(0, MAX_MILLIS),
            deadline = if (running && remaining(now) > MAX_MILLIS) now + MAX_MILLIS else deadline,
            intervalMillis = intervalMillis.coerceAtLeast(remaining),
        )
    }
    fun adjust(deltaMillis: Long, now: Long): RestTimerState {
        val safe = bounded(now)
        val previousRemaining = safe.remaining(now).coerceIn(0, MAX_MILLIS)
        val remaining = (previousRemaining +
            deltaMillis.coerceIn(-MAX_MILLIS, MAX_MILLIS)).coerceIn(0, MAX_MILLIS)
        val effectiveChange = remaining - previousRemaining
        // Only accepted seconds change the interval. Elapsed time stays constant, even if
        // the new remainder is greater than the reset prescription or hits the 5-minute cap.
        val adjustedInterval = if (effectiveChange > 0) {
            safe.intervalMillis.coerceAtMost(Long.MAX_VALUE - effectiveChange) + effectiveChange
        } else (safe.intervalMillis + effectiveChange).coerceAtLeast(remaining)
        return if (safe.running) safe.copy(deadline = now + remaining, intervalMillis = adjustedInterval)
        else safe.copy(pausedMillis = remaining, finished = if (remaining > 0) false else safe.finished,
            intervalMillis = adjustedInterval)
    }

    companion object { const val MAX_MILLIS = RestDuration.MAX_SECONDS * 1000L }
}
