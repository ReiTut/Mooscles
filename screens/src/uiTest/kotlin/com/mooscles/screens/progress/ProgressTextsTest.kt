package com.mooscles.screens.progress

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.charts.ChartTableRowData
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.spokenDate
import com.mooscles.screens.format.weekOf
import com.mooscles.screens.withScreen
import com.mooscles.ui.RecordKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProgressTextsTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 10, 31)
    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime(y, mo, d, 9, 0).toInstant(utc).toEpochMilliseconds()

    /** The app writes the unit after a no-break space. */
    private fun String.nb() = replace(" kg", "\u00A0kg")

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /** Runs [block] once per language, composed in the language, and returns the texts by language tag. */
    private fun <T> inBoth(block: @androidx.compose.runtime.Composable (String) -> T): Map<String, T> {
        val out = LinkedHashMap<String, T>()
        for (locale in listOf("pt-BR", "en")) {
            runComposeUiTest {
                withScreen(locale = locale) { out[locale] = block(locale) }
                waitForIdle()
            }
            LocalAppLocale.restore()
        }
        return out
    }

    private fun point(day: Int, value: Double, set: SetValue? = null, record: Boolean = false, month: Int = 10) =
        ChartPoint(at(2026, month, day), value, set, record, "w$day")

    private fun chart(points: List<ChartPoint>, trend: Trend?, weekly: Boolean = false, best: ChartPoint? = null) =
        ExerciseChart(points, weekly, points.size, points.first(), points.last(), best ?: points.maxBy { it.value }, trend, null)

    private val e1rm = listOf(
        point(3, 80.0, month = 6),
        point(12, 95.0, month = 9),
        point(28, 92.5),
    )

    @Test
    fun summarySaysPeriodFirstLastBestTrendAndCount() {
        val texts = inBoth {
            chartSummary(
                chart(e1rm, Trend.UP), ProgressMetric.E1RM, "Total load", ExercisePeriod.SIX_MONTHS, false, utc, today,
            )
        }
        assertEquals(
            "1RM estimado, últimos 6 meses: de 80 quilos em 3 de junho a 92,5 quilos em 28 de outubro; " +
                "máximo 95 quilos em 12 de setembro; tendência em alta; 3 treinos.",
            texts["pt-BR"],
        )
        assertEquals(
            "Estimated 1RM, last 6 months: from 80 kilograms on June 3 to 92.5 kilograms on October 28; " +
                "highest 95 kilograms on September 12; trending up; 3 workouts.",
            texts["en"],
        )
    }

    @Test
    fun noTrendBelowThreePoints() {
        val two = e1rm.take(2)
        val texts = inBoth {
            chartSummary(chart(two, null), ProgressMetric.E1RM, "Total load", ExercisePeriod.ALL, false, utc, today)
        }
        for ((locale, text) in texts) {
            assertFalse(text.contains("tendência") || text.contains("trending") || text.contains("steady"), "$locale: $text")
            assertTrue(text.endsWith("2 treinos.") || text.endsWith("2 workouts."), "$locale: $text")
        }
    }

    @Test
    fun assistanceSummarySaysLowest() {
        val points = listOf(point(3, 40.0), point(12, 30.0), point(28, 35.0))
        val texts = inBoth {
            chartSummary(
                chart(points, Trend.DOWN, best = points[1]), ProgressMetric.LOAD, "Assistance", ExercisePeriod.THREE_MONTHS,
                false, utc, today,
            )
        }
        assertTrue(texts["pt-BR"]!!.startsWith("Assistência, últimos 3 meses:"), texts["pt-BR"])
        assertTrue(texts["pt-BR"]!!.contains("mínimo 30 quilos em 12 de outubro"), texts["pt-BR"])
        // The words do not invert on Assistance: "down" is the literal word, though it is progress there.
        assertTrue(texts["pt-BR"]!!.contains("tendência em baixa"), texts["pt-BR"])
        assertTrue(texts["en"]!!.contains("lowest 30 kilograms on October 12"), texts["en"])
        assertTrue(texts["en"]!!.contains("trending down"), texts["en"])
    }

    @Test
    fun weeklySummarySaysTheWeeksBestWorkout() {
        val weeks = listOf(point(6, 9_800.0), point(13, 14_200.0), point(20, 9_000.0))
        val texts = inBoth {
            chartSummary(chart(weeks, Trend.FLAT, weekly = true), ProgressMetric.VOLUME, "Total load", ExercisePeriod.ALL, false, utc, today)
        }
        assertTrue(texts["pt-BR"]!!.contains("na semana de 6 de outubro"), texts["pt-BR"])
        assertTrue(texts["pt-BR"]!!.contains("melhor treino da semana, não a soma"), texts["pt-BR"])
        assertTrue(texts["pt-BR"]!!.contains("tendência estável"), texts["pt-BR"])
        assertTrue(texts["en"]!!.contains("in the week of October 6"), texts["en"])
        assertTrue(texts["en"]!!.contains("the week's best workout, not the sum"), texts["en"])
        assertTrue(texts["en"]!!.contains("steady"), texts["en"])
    }

    @Test
    fun axisUsesTheShortFormFromTenThousand() {
        val texts = inBoth {
            listOf(
                axisText(42_000.0, ProgressMetric.VOLUME), axisText(9_999.0, ProgressMetric.VOLUME),
                axisText(92.5, ProgressMetric.E1RM), axisText(12.0, ProgressMetric.REPS),
            )
        }
        assertEquals(listOf("42 mil", "9.999", "92,5", "12"), texts["pt-BR"])
        assertEquals(listOf("42k", "9,999", "92.5", "12"), texts["en"])
    }

    @Test
    fun sinceIsAFactWithItsSign() {
        val first = point(3, 80.0, month = 6)
        val texts = inBoth {
            listOf(
                sinceText(first, point(28, 92.5), ProgressMetric.LOAD, false, utc),
                sinceText(first, point(28, 75.0), ProgressMetric.LOAD, false, utc),
                sinceText(first, point(28, 80.0), ProgressMetric.LOAD, false, utc),
                sinceText(first, point(28, 83.0), ProgressMetric.REPS, false, utc),
                sinceText(first, point(28, 92.5), ProgressMetric.LOAD, false, utc, weekly = true),
            )
        }
        assertEquals(
            listOf(
                "+12,5 kg desde 3 jun".nb(), "−5 kg desde 3 jun".nb(), "0 kg desde 3 jun".nb(), "+3 repetições desde 3 jun",
                "+12,5 kg desde a semana de 3 jun".nb(),
            ),
            texts["pt-BR"],
        )
        assertEquals("+12.5 kg since Jun 3".nb(), texts["en"]!![0])
        assertEquals("−5 kg since Jun 3".nb(), texts["en"]!![1])
        assertEquals("+12.5 kg since the week of Jun 3".nb(), texts["en"]!![4])
    }

    @Test
    fun readoutJoinsDateValueAndSet() {
        val p = point(28, 92.5, SetValue(92.5, 85.0, 3))
        val weekly = point(6, 92.5, SetValue(92.5, 85.0, 3))
        val texts = inBoth {
            listOf(
                readoutText(p, ProgressMetric.E1RM, false, utc),
                readoutText(point(28, 9_800.0), ProgressMetric.VOLUME, false, utc),
                readoutText(weekly, ProgressMetric.E1RM, false, utc, weekly = true),
            )
        }
        assertEquals("28 out · 92,5 kg · 85 kg × 3".nb(), texts["pt-BR"]!![0])
        assertEquals("28 out · 9.800 kg".nb(), texts["pt-BR"]!![1])
        assertEquals("Semana de 6 out · 92,5 kg · 85 kg × 3".nb(), texts["pt-BR"]!![2])
        assertEquals("Oct 28 · 92.5 kg · 85 kg × 3".nb(), texts["en"]!![0])
        assertEquals("Week of Oct 6 · 92.5 kg · 85 kg × 3".nb(), texts["en"]!![2])
    }

    @Test
    fun tableRowsNewestFirstWithSetAndRecord() {
        val points = listOf(
            point(28, 90.0, SetValue(90.0, 90.0, 5), record = true),
            point(30, 92.5, SetValue(92.5, 85.0, 3)),
        )
        val texts = inBoth {
            tableRows(chart(points, null), ProgressMetric.E1RM, false, utc, DayOfWeek.MONDAY, today)
        }
        for ((locale, rows) in texts) {
            assertEquals(listOf(points[1].startedAt.toString(), points[0].startedAt.toString()), rows.map(ChartTableRowData::key), locale)
            assertEquals("85 kg × 3".nb(), rows[0].detail, locale)
            assertFalse(rows[0].record, locale)
            assertTrue(rows[1].record, locale)
        }
        assertEquals("30 out", texts["pt-BR"]!![0].label)
        assertEquals("92,5 kg".nb(), texts["pt-BR"]!![0].value)
        assertEquals("Oct 28", texts["en"]!![1].label)
    }

    @Test
    fun weeklyTableNamesTheWeekAndMarksTheRunningOne() {
        // 2026-10-26 is a Monday; today (Oct 31) is in the week of Oct 26.
        // Weekly points sit at midnight, the start of their week.
        val midnight = { d: Int -> LocalDateTime(2026, 10, d, 0, 0).toInstant(utc).toEpochMilliseconds() }
        val weeks = listOf(ChartPoint(midnight(6), 9_800.0, null, false, null), ChartPoint(midnight(26), 7_000.0, null, false, null))
        val texts = inBoth {
            tableRows(chart(weeks, null, weekly = true), ProgressMetric.VOLUME, false, utc, DayOfWeek.MONDAY, today)
        }
        assertEquals("Semana de 26 out · esta semana (em andamento)", texts["pt-BR"]!![0].label)
        assertEquals("Semana de 6 out", texts["pt-BR"]!![1].label)
        assertEquals("Week of Oct 26 · this week (in progress)", texts["en"]!![0].label)
        assertEquals("Week of Oct 6", texts["en"]!![1].label)
    }

    @Test
    fun metricLabelFollowsTheConvention() {
        val texts = inBoth {
            listOf(
                metricLabel(ProgressMetric.LOAD, "Added weight"), metricLabel(ProgressMetric.LOAD, "Assistance"),
                metricLabel(ProgressMetric.LOAD, "Total load"), metricLabel(ProgressMetric.E1RM, "Total load"),
                metricLabel(ProgressMetric.REPS, "Added weight"), metricLabel(ProgressMetric.VOLUME, "Per hand"),
            )
        }
        assertEquals(listOf("Carga adicional", "Assistência", "Carga", "1RM estimado", "Repetições", "Volume"), texts["pt-BR"])
        assertEquals(listOf("Added weight", "Assistance", "Load", "Estimated 1RM", "Reps", "Volume"), texts["en"])
    }

    @Test
    fun periodLabels() {
        val texts = inBoth { ExercisePeriod.entries.map { periodLabel(it) } }
        assertEquals(listOf("3 meses", "6 meses", "1 ano", "Tudo"), texts["pt-BR"])
        assertEquals(listOf("3 months", "6 months", "1 year", "All"), texts["en"])
    }

    @Test
    fun monthYearAndSpokenDates() {
        val texts = inBoth {
            listOf(
                ScreenDates.monthYear(2026, Month.OCTOBER),
                spokenDate(at(2026, 6, 3), utc, today),
                spokenDate(at(2025, 6, 3), utc, today),
                weekOf(at(2026, 10, 6), utc),
            )
        }
        assertEquals(listOf("outubro de 2026", "3 de junho", "3 de junho de 2025", "Semana de 6 out"), texts["pt-BR"])
        assertEquals(listOf("October 2026", "June 3", "June 3, 2025", "Week of Oct 6"), texts["en"])
    }

    @Test
    fun recordTextsInBothLanguages() {
        val texts = inBoth {
            listOf(
                recordsMonthHeader(2026, Month.OCTOBER, 5),
                recordsMonthHeader(2026, Month.OCTOBER, 1),
                recordKindLabel(RecordKind.WEIGHT),
                recordKindLabel(RecordKind.REPS),
                recordWasText(RecordKind.WEIGHT, 87.5, 6, previousKg = 85.0, previousReps = null, pounds = false),
                recordWasText(RecordKind.REPS, 80.0, 8, previousKg = null, previousReps = 6, pounds = false),
                recordWasText(RecordKind.REPS, 80.0, 8, previousKg = null, previousReps = 1, pounds = false),
                recordWasText(RecordKind.WEIGHT, 87.5, 6, previousKg = null, previousReps = null, pounds = false),
                recordLine(100.0, 3, at(2026, 9, 12), false, utc),
                recordSpoken(
                    "Supino", RecordKind.WEIGHT, 87.5, 6, previousKg = 85.0, previousReps = null, pounds = false,
                    epochMillis = at(2026, 10, 28), zone = utc, today = today, current = true, first = false,
                ),
                sessionSpoken(at(2026, 10, 28), "Treino A", "A 40 × 10, 80 × 8", utc, today),
            )
        }
        val pt = texts["pt-BR"]!!
        assertEquals("outubro de 2026 · 5 recordes", pt[0])
        assertEquals("outubro de 2026 · 1 recorde", pt[1])
        assertEquals("Carga", pt[2])
        assertEquals("Repetições", pt[3])
        assertEquals("87,5 kg × 6 · antes 85 kg".nb(), pt[4])
        assertEquals("80 kg × 8 · antes 6 repetições".nb(), pt[5])
        assertEquals("80 kg × 8 · antes 1 repetição".nb(), pt[6])
        assertEquals("87,5 kg × 6".nb(), pt[7])
        assertEquals("100 kg × 3 · 12 set".nb(), pt[8])
        assertEquals("Supino, Carga: 87,5 quilos por 6, antes 85 quilos, 28 de outubro, Atual", pt[9])
        assertEquals("28 de outubro, Treino A: A 40 × 10, 80 × 8", pt[10])
        val en = texts["en"]!!
        assertEquals("October 2026 · 5 records", en[0])
        assertEquals("October 2026 · 1 record", en[1])
        assertEquals("87.5 kg × 6 · was 85 kg".nb(), en[4])
        assertEquals("80 kg × 8 · was 6 reps".nb(), en[5])
        assertEquals("80 kg × 8 · was 1 rep".nb(), en[6])
        assertEquals("100 kg × 3 · Sep 12".nb(), en[8])
    }
}
