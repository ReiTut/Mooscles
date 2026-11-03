package com.mooscles

import android.os.Build
import android.os.Looper
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/** System trace sections around the view model's work, and the presentation probes the host tests read. */
class AndroidViewModelPlatform : ViewModelPlatform {
    /** Presentations finished, and how many of them ran on the main thread. */
    val presentedCount = AtomicInteger()
    val presentedOnMainThread = AtomicInteger()

    override fun <T> presentation(block: () -> T): T {
        Trace.beginSection("Mooscles:present")
        val state = try { block() } finally { Trace.endSection() }
        if (Looper.myLooper() == Looper.getMainLooper()) presentedOnMainThread.incrementAndGet()
        presentedCount.incrementAndGet()
        return state
    }

    override suspend fun <T> startingSession(block: suspend () -> T): T {
        val traceCookie = System.identityHashCode(this)
        if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection("Mooscles:startSession", traceCookie)
        try {
            return block()
        } finally {
            if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection("Mooscles:startSession", traceCookie)
        }
    }
}
