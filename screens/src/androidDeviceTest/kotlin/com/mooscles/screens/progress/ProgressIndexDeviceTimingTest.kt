package com.mooscles.screens.progress

import android.os.SystemClock
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone

/**
 * The progress index's timing on the device (plan 5A Task 16, Step 4; spec "Desempenho"), on [LargeProgressHistory]
 * (208 workouts × 61 sets over 48 exercises):
 * - the build of the index, 9 rounds, the first one in the process included (class loading, the lazy catalog maps);
 * - chart queries (each metric, period and week start in turn over the exercises) and records timeline queries (the
 *   chips and searches), 9 rounds of 20 each;
 * - the keystrokes of "supino" in the Records search, letter by letter, 9 rounds: what the tab computes per key.
 *
 * Each query is the uncached cost (the screens memoize them in [ProgressIndexCache]). It logs
 * `PROGRESS_INDEX_TIMING build median=… p95=… chart … records … keys …`, with the spec's goals (a build: median 60 ms,
 * p95 120 ms; a query or a key: median 4 ms, p95 8 ms), and the phase report compares them.
 *
 * This runs in the module's debuggable test APK, not in the R8 `performance` build, so it asserts loose limits (four
 * times each goal) rather than the goals themselves: a debuggable runtime on an emulator sharing the Mac's CPU is
 * slower than the shipped build, and a tight wall-clock limit would be flaky. The R8 build's index time is read from
 * the `Mooscles:progressIndex` trace section of the app instead.
 */
class ProgressIndexDeviceTimingTest {
    private val zone = TimeZone.of("America/Sao_Paulo")

    @Test
    fun buildQueriesAndKeysOnTheLargeHistory() {
        val (history, records) = LargeProgressHistory.state()

        val builds = List(ROUNDS) { timed { ProgressIndex.build(history, records) } }
        val index = ProgressIndex.build(history, records)

        val keys = LargeProgressHistory.exercises(48).map { SeriesKey(it.id, it.convention) }
        val today = ScreenDates.localDate(history.first().startedAt, zone)
        val charts = List(QUERIES) { n ->
            val key = keys[(n * 7) % keys.size]
            val metrics = ProgressMetrics.available(key.convention, index.sessions(key))
            ExerciseChartQuery(key, metrics[n % metrics.size], ExercisePeriod.entries[n % ExercisePeriod.entries.size],
                today, zone, DayOfWeek.entries[n % DayOfWeek.entries.size])
        }
        val searches = List(QUERIES) { n ->
            RecordsQuery(text = SEARCH.take(n % (SEARCH.length + 1)), currentOnly = n % 4 == 3, firstRecords = n % 2 == 1, zone = zone)
        }
        val chartTimes = ArrayList<Double>()
        val recordTimes = ArrayList<Double>()
        val keyTimes = ArrayList<Double>()
        repeat(ROUNDS) {
            charts.forEach { query -> chartTimes += timed { ExerciseCharts.query(index, query) } }
            searches.forEach { query -> recordTimes += timed { RecordTimelines.query(index, query) } }
            for (length in 1..SEARCH.length) {
                val query = RecordsQuery(text = SEARCH.take(length), zone = zone)
                keyTimes += timed { RecordTimelines.query(index, query) }
            }
        }

        val build = Stats(builds)
        val chart = Stats(chartTimes)
        val timeline = Stats(recordTimes)
        val key = Stats(keyTimes)
        println(
            "PROGRESS_INDEX_TIMING build $build first=${ms(builds.first())} rounds=${builds.map(::ms)} " +
                "chart $chart records $timeline keys $key " +
                "events=${index.events.size} current=${index.currentRecords.size} workouts=${history.size} " +
                "goal=build(median<=60,p95<=120),query/key(median<=4,p95<=8)",
        )

        assertTrue(index.events.size > 1000 && index.currentRecords.isNotEmpty(), "the history has many records")
        assertTrue(RecordTimelines.query(index, RecordsQuery(text = SEARCH, zone = zone)).total > 0, "\"$SEARCH\" finds bench records")
        assertTrue(build.median < BUILD_MEDIAN_MS * LOOSE, "median build ${ms(build.median)} ms (all: ${builds.map(::ms)})")
        assertTrue(build.p95 < BUILD_P95_MS * LOOSE, "p95 build ${ms(build.p95)} ms (all: ${builds.map(::ms)})")
        for ((name, stats) in listOf("chart query" to chart, "records query" to timeline, "key" to key)) {
            assertTrue(stats.median < QUERY_MEDIAN_MS * LOOSE, "median $name ${ms(stats.median)} ms")
            assertTrue(stats.p95 < QUERY_P95_MS * LOOSE, "p95 $name ${ms(stats.p95)} ms")
        }
    }

    /** The median, the 95th percentile and the largest of [values], in ms. */
    private class Stats(values: List<Double>) {
        private val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        val p95 = sorted[((sorted.size - 1) * 95) / 100]
        val max = sorted.last()
        val count = sorted.size
        override fun toString() = "median=${ms(median)} p95=${ms(p95)} max=${ms(max)} n=$count"
    }

    private inline fun timed(block: () -> Unit): Double {
        val started = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
    }

    private companion object {
        const val ROUNDS = 9
        const val QUERIES = 20
        const val SEARCH = "supino"

        // The spec's goals, measured on the emulator (see the class comment).
        const val BUILD_MEDIAN_MS = 60.0
        const val BUILD_P95_MS = 120.0
        const val QUERY_MEDIAN_MS = 4.0
        const val QUERY_P95_MS = 8.0

        /** How far above a goal the debuggable test APK may land before the test fails. */
        const val LOOSE = 4.0

        fun ms(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
    }
}
