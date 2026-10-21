package com.mooscles.timer

/** Wall-clock deadline survives process death and includes time spent outside the app. */
data class RestTimerState(
    val token: Long = 0, val exerciseName: String = "Rest timer", val durationMillis: Long = 90_000,
    val deadline: Long = 0, val pausedMillis: Long = 0, val finished: Boolean = false,
) {
    val running get() = deadline > 0 && !finished
    val paused get() = pausedMillis > 0 && !finished
    fun remaining(now: Long) = if (running) (deadline - now).coerceAtLeast(0) else pausedMillis
    // A late tap must not swallow an expiration before the ticker/receiver can alert.
    fun pause(now: Long) = if (running && remaining(now) > 0)
        copy(deadline = 0, pausedMillis = remaining(now)) else this
    fun resume(now: Long) = if (paused) copy(deadline = now + pausedMillis, pausedMillis = 0) else this
    fun adjust(deltaMillis: Long, now: Long): RestTimerState {
        val remaining = (remaining(now) + deltaMillis).coerceAtLeast(0)
        return if (running) copy(deadline = now + remaining)
        else copy(pausedMillis = remaining, finished = if (remaining > 0) false else finished)
    }
}
