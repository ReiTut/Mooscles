package com.mooscles.screens.progress

import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone

/**
 * The progress index's timing on the JVM only, on the large history (208 workouts × 61 sets over 48 exercises).
 *
 * The checks that decide are relative, on the same machine and in the same run (pre-flight P19): an absolute bound
 * failed on CI runners while other Gradle forks ran (`WorkoutCalculationsBatchTimingTest`). The build must grow about
 * linearly with the history (twice the workouts in less than three times the time; a quadratic pass reads about
 * four), and a query must cost a small part of a build, which is what the one shared index buys. The absolute bounds
 * of the plan (a median build under 500 ms, queries under 20 ms) come after them and are wide on purpose; the real
 * budget is measured on the emulator with the R8 build (Task 16), and a debug Kotlin/Native build does not match the JIT.
 */
class ProgressIndexTimingTest {
    private val zone = TimeZone.of("America/Sao_Paulo")

    @Test
    fun buildAndQueriesAreFastOnTheLargeHistory() {
        val (history, records) = LargeProgressHistory.state()
        val (half, halfRecords) = LargeProgressHistory.state(48, LargeProgressHistory.WORKOUTS / 2)

        // The plan's five builds, the first one in the process included (class loading, the lazy catalog maps).
        val builds = List(5) { timed { ProgressIndex.build(history, records) } }
        val index = ProgressIndex.build(history, records)

        val keys = LargeProgressHistory.exercises(48).map { SeriesKey(it.id, it.convention) }
        val today = ScreenDates.localDate(history.first().startedAt, zone)
        val queries = List(CHART_QUERIES) { n ->
            val key = keys[(n * 7) % keys.size]
            val metrics = ProgressMetrics.available(key.convention, index.sessions(key))
            ExerciseChartQuery(key, metrics[n % metrics.size], ExercisePeriod.entries[n % ExercisePeriod.entries.size],
                today, zone, DayOfWeek.entries[n % DayOfWeek.entries.size])
        }
        val charts = queries.map { query -> timed { ExerciseCharts.query(index, query) } }
        val typed = "supino"
        val searches = List(RECORD_QUERIES) { n ->
            RecordsQuery(text = typed.take(1 + n % typed.length), currentOnly = n % 4 == 3, firstRecords = n % 2 == 1, zone = zone)
        }
        val timelines = searches.map { query -> timed { RecordTimelines.query(index, query) } }

        // Relative: seven warm runs of each side, the best of each.
        val fullBest = bestOfSeven { ProgressIndex.build(history, records) }
        val halfBest = bestOfSeven { ProgressIndex.build(half, halfRecords) }
        val chartBest = bestOfSeven { queries.forEach { ExerciseCharts.query(index, it) } } / CHART_QUERIES
        val recordsBest = bestOfSeven { searches.forEach { RecordTimelines.query(index, it) } } / RECORD_QUERIES

        val build = median(builds)
        val chart = median(charts)
        val timeline = median(timelines)
        println("TIMING progress build=${ms(build)} chart=${ms(chart)} records=${ms(timeline)} " +
            "warm build=${ms(fullBest)} half=${ms(halfBest)} chart=${ms(chartBest)} records=${ms(recordsBest)} " +
            "events=${index.events.size} current=${index.currentRecords.size}")

        assertTrue(index.events.size > 1000 && index.currentRecords.isNotEmpty(), "the history has many records")
        assertTrue(RecordTimelines.query(index, RecordsQuery(text = "supino", zone = zone)).total > 0, "\"supino\" finds bench records")
        assertTrue(fullBest < halfBest * 3, "twice the history took ${ms(fullBest)} against ${ms(halfBest)}: not linear")
        assertTrue(chartBest * CHEAPER < fullBest, "a chart query took ${ms(chartBest)}, a build ${ms(fullBest)}")
        assertTrue(recordsBest * CHEAPER < fullBest, "a records query took ${ms(recordsBest)}, a build ${ms(fullBest)}")

        assertTrue(build < 500.0, "median build ${ms(build)} (all: ${builds.map(::ms)})")
        assertTrue(chart < 20.0, "median chart query ${ms(chart)} (all: ${charts.map(::ms)})")
        assertTrue(timeline < 20.0, "median records query ${ms(timeline)} (all: ${timelines.map(::ms)})")
    }

    private inline fun timed(block: () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000.0
    }

    private inline fun bestOfSeven(block: () -> Unit): Double = List(7) { timed(block) }.min()

    private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

    private fun ms(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private companion object {
        const val CHART_QUERIES = 20
        const val RECORD_QUERIES = 20

        /** How many times a query must fit in one build of the index. */
        const val CHEAPER = 5
    }
}
