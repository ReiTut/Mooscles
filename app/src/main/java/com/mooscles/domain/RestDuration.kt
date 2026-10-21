package com.mooscles.domain

/** Editable rest durations use minutes and seconds, up to one hour. */
object RestDuration {
    private val inputPattern = Regex("[0-9]{1,2}:[0-5][0-9]")

    fun format(seconds: Int): String {
        require(seconds in 0..3600) { "Rest duration must be between 0 and 3600 seconds" }
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** Incomplete or invalid drafts are not a duration and must not be saved. */
    fun parse(text: String): Int? {
        if (!inputPattern.matches(text)) return null
        val (minutes, seconds) = text.split(':').map(String::toInt)
        return (minutes * 60 + seconds).takeIf { it <= 3600 }
    }
}
