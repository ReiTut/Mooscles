package com.mooscles

import android.os.Trace
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import java.util.concurrent.atomic.AtomicInteger

/**
 * The application's progress index build ([MoosclesApplication.progressIndex]) inside the system trace section
 * [SECTION], so its time and how often it runs can be read from `atrace` on the R8 `performance` build (plan 5A
 * Task 16: the spec's budget is measured there, and the device timing test runs only in the debuggable test APK).
 * [builds] counts the builds that ended, for the host tests, as `AndroidViewModelPlatform` counts presentations.
 */
object ProgressIndexTrace {
    const val SECTION = "Mooscles:progressIndex"

    private val ended = AtomicInteger()

    /** Builds that ended (returned or threw) in this process. */
    val builds: Int get() = ended.get()

    fun build(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex {
        Trace.beginSection(SECTION)
        try {
            return ProgressIndex.build(history, records)
        } finally {
            Trace.endSection()
            ended.incrementAndGet()
        }
    }
}
