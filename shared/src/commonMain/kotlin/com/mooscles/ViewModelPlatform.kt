package com.mooscles

/** Platform hooks around the view model's work, for tracing and test probes. Neither may change the result. */
interface ViewModelPlatform {
    /** Wraps one presentation; runs on the presentation dispatcher, never in composition. */
    fun <T> presentation(block: () -> T): T

    /** Wraps one workout start, from the command until the editor shows it (or the start fails). */
    suspend fun <T> startingSession(block: suspend () -> T): T

    /** Runs the blocks as they are. */
    object None : ViewModelPlatform {
        override fun <T> presentation(block: () -> T): T = block()
        override suspend fun <T> startingSession(block: suspend () -> T): T = block()
    }
}
