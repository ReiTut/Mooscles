package com.mooscles.screens.library

import com.mooscles.data.Equipment
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.ExerciseUi
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The library's search timing on the JVM only: the limits are wide on purpose (the
 * WorkoutCalculationsBatchTimingTest showed that tight wall-clock limits are flaky on CI), and a
 * debug Kotlin/Native build on the iOS simulator does not match the JIT. The real budget (4 ms
 * median, 8 ms p95 per keystroke) is measured on the emulator with the R8 build (plan Task 22).
 */
class LibraryModelTimingTest {
    @Test
    fun typingOnTheCatalogPlusThreeHundredCustomExercisesIsFast() {
        val catalog = ExerciseCatalog.exercises.map {
            ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label, it.custom)
        }
        val words = listOf("Supino", "Remada", "Agachamento", "Rosca", "Press", "Row", "Curl", "Elevação")
        val customs = List(300) { index ->
            ExerciseUi(
                id = "custom-$index",
                name = "${words[index % words.size]} variação ${index / words.size} ${if (index % 3 == 0) "inclinado" else "livre"}",
                muscle = MuscleGroup.entries[index % MuscleGroup.entries.size].label,
                equipment = Equipment.entries[index % Equipment.entries.size].label,
                custom = true,
            )
        }
        val exercises = catalog + customs

        // The first build in the process: class loading and the lazy catalog maps included.
        val buildStarted = System.nanoTime()
        val model = LibraryModel.build(exercises, portuguese = true)
        val buildMs = (System.nanoTime() - buildStarted) / 1_000_000.0

        val query = "supino inclinado"
        val filters = LibraryFilters()
        val keystrokes = mutableListOf<Double>()
        repeat(ROUNDS) {
            for (length in 1..query.length) {
                val started = System.nanoTime()
                model.results(query.take(length), filters)
                keystrokes += (System.nanoTime() - started) / 1_000_000.0
            }
        }
        val sorted = keystrokes.sorted()
        val median = sorted[sorted.size / 2]
        val p95 = sorted[((sorted.size - 1) * 95) / 100]
        println("TIMING library median=${ms(median)} p95=${ms(p95)} build=${ms(buildMs)}")

        val found = model.results(query, filters).map { it.id }
        assertTrue("catalog_barbell_bench_incline" in found && "custom-0" in found, "found $found")
        assertTrue(median < 20.0, "median per keystroke ${median}ms (all: $keystrokes)")
        assertTrue(buildMs < 500.0, "build took ${buildMs}ms")
    }

    private fun ms(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private companion object {
        const val ROUNDS = 9
    }
}
