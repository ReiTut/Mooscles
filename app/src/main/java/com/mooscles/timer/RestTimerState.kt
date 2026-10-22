package com.mooscles.timer

import com.mooscles.domain.RestDuration

/** Wall-clock deadline survives process death and includes time spent outside the app. */
data class RestTimerState(
    val token: Long = 0, val exerciseName: String = "Rest timer",
    val durationMillis: Long = RestDuration.DEFAULT_SECONDS * 1000L,
    val deadline: Long = 0, val pausedMillis: Long = 0, val finished: Boolean = false,
) {
    val running get() = deadline > 0 && !finished
    val paused get() = pausedMillis > 0 && !finished
    fun remaining(now: Long) = if (running) (deadline - now).coerceAtLeast(0) else pausedMillis
    // A late tap must not swallow an expiration before the ticker/receiver can alert.
    fun pause(now: Long) = if (running && remaining(now) > 0)
        copy(deadline = 0, pausedMillis = remaining(now)) else this
    fun resume(now: Long) = if (paused) copy(deadline = now + pausedMillis, pausedMillis = 0) else this
    /** Preserve elapsed/background time when normalizing timers saved by an earlier version. */
    fun bounded(now: Long): RestTimerState = copy(
        durationMillis = durationMillis.coerceIn(0, MAX_MILLIS),
        pausedMillis = pausedMillis.coerceIn(0, MAX_MILLIS),
        deadline = if (running && remaining(now) > MAX_MILLIS) now + MAX_MILLIS else deadline,
    )
    fun adjust(deltaMillis: Long, now: Long): RestTimerState {
        val safe = bounded(now)
        val remaining = (safe.remaining(now).coerceIn(0, MAX_MILLIS) +
            deltaMillis.coerceIn(-MAX_MILLIS, MAX_MILLIS)).coerceIn(0, MAX_MILLIS)
        return if (safe.running) safe.copy(deadline = now + remaining)
        else safe.copy(pausedMillis = remaining, finished = if (remaining > 0) false else safe.finished)
    }

    companion object { const val MAX_MILLIS = RestDuration.MAX_SECONDS * 1000L }
}
