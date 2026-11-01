package com.mooscles.screens.library

import android.os.SystemClock
import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.ExerciseUi
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The library's per-keystroke search on the device (plan Task 22, Step 4): the catalog plus 300
 * custom exercises, "supino inclinado" typed letter by letter in 9 rounds, timed around
 * [LibraryModel.results] as the screen calls it on every keystroke. It logs
 * `LIBRARY_SEARCH_TIMING median=<ms> p95=<ms> ...`; the goal (median 4 ms, p95 8 ms) is read from
 * that log and compared in the phase report.
 *
 * This runs in the debuggable test APK, not in the R8 `performance` build (pre-flight F32), so
 * the asserted limits are loose on purpose: an emulator sharing the Mac's CPU and a debuggable
 * runtime are slower than the shipped build, and a tight wall-clock limit would be flaky.
 */
class LibrarySearchTimingTest {
    @Test
    fun typingOnTheCatalogPlusThreeHundredCustomExercises() {
        val catalog = ExerciseCatalog.exercises.map {
            ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label, it.custom)
        }
        val words = listOf("Supino", "Remada", "Agachamento", "Rosca", "Press", "Row", "Curl", "Elevação")
        val customs = List(CUSTOMS) { index ->
            ExerciseUi(
                id = "custom-$index",
                name = "${words[index % words.size]} variação ${index / words.size} ${if (index % 3 == 0) "inclinado" else "livre"}",
                muscle = MuscleGroup.entries[index % MuscleGroup.entries.size].label,
                equipment = Equipment.entries[index % Equipment.entries.size].label,
                custom = true,
            )
        }
        val buildStarted = SystemClock.elapsedRealtimeNanos()
        val model = LibraryModel.build(catalog + customs, portuguese = true)
        val buildMs = (SystemClock.elapsedRealtimeNanos() - buildStarted) / 1_000_000.0

        val filters = LibraryFilters()
        val keystrokes = mutableListOf<Double>()
        val rounds = mutableListOf<Double>()
        repeat(ROUNDS) {
            val roundStarted = keystrokes.size
            for (length in 1..QUERY.length) {
                val started = SystemClock.elapsedRealtimeNanos()
                model.results(QUERY.take(length), filters)
                keystrokes += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            }
            rounds += keystrokes.subList(roundStarted, keystrokes.size).sorted()[QUERY.length / 2]
        }
        val sorted = keystrokes.sorted()
        val median = sorted[sorted.size / 2]
        val p95 = sorted[((sorted.size - 1) * 95) / 100]
        println(
            "LIBRARY_SEARCH_TIMING median=${ms(median)} p95=${ms(p95)} max=${ms(sorted.last())} " +
                "build=${ms(buildMs)} keystrokes=${keystrokes.size} exercises=${model.exercises.size} " +
                "roundMedians=${rounds.map(::ms)} goal=median<=4,p95<=8",
        )

        val found = model.results(QUERY, filters).map { it.id }
        assertTrue("catalog_barbell_bench_incline" in found && "custom-0" in found, "found $found")
        assertTrue(median < LOOSE_MEDIAN_MS, "median per keystroke ${median}ms (all: $keystrokes)")
        assertTrue(p95 < LOOSE_P95_MS, "p95 per keystroke ${p95}ms (all: $keystrokes)")
    }

    private fun ms(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private companion object {
        const val QUERY = "supino inclinado"
        const val ROUNDS = 9
        const val CUSTOMS = 300
        // Loose bounds for the debuggable APK on a shared emulator (F32); the goal is 4 / 8 ms.
        const val LOOSE_MEDIAN_MS = 20.0
        const val LOOSE_P95_MS = 40.0
    }
}
