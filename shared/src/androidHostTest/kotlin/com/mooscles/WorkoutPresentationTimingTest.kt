package com.mooscles

import com.mooscles.data.AppData
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.UiState
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The two timing limits of [WorkoutPresentationTest], on the JVM only: they were set for the JIT, which a debug
 * Kotlin/Native build on the iOS simulator does not match.
 */
class WorkoutPresentationTimingTest {
    /**
     * Best of three cold presents (each on a fresh presenter, so a loaded machine does not fail it), then a cached
     * one with equal data. The 1000 ms ceiling still fails the per-workout passes, which never went below 2114 ms.
     */
    private fun assertPresentsQuickly(data: AppData, label: String): UiState {
        lateinit var presenter: WorkoutPresentation
        lateinit var first: UiState
        val coldMs = List(3) {
            presenter = WorkoutPresentation()
            measureTimeMillis { first = presenter.present(data, PreferencesUi()) }
        }
        val firstMs = coldMs.min()
        lateinit var second: UiState
        val secondMs = measureTimeMillis { second = presenter.present(data.copy(), PreferencesUi()) }
        println("$label present=${coldMs}ms best=${firstMs}ms cached=${secondMs}ms")
        assertEquals(LargeHistoryFixture.WORKOUTS, first.history.size)
        assertTrue(first.history.any { it.records.isNotEmpty() })
        assertSame(first.history, second.history)
        assertTrue(firstMs < 1000, "$label best cold present took ${firstMs}ms ($coldMs)")
        assertTrue(secondMs < 200, "$label cached present took ${secondMs}ms")
        return first
    }

    @Test fun largeHistoryPresentsQuickly() {
        val data = LargeHistoryFixture.appData()
        assertEquals(LargeHistoryFixture.SETS, data.sets.size)
        assertPresentsQuickly(data, "six exercises")
    }

    @Test fun largeHistoryWithManyExercisesPresentsQuickly() {
        val data = LargeHistoryFixture.appData(LargeHistoryFixture.variedExercises(48))
        assertEquals(48, data.workoutExercises.map { it.exerciseId to it.convention }.distinct().size)
        val ui = assertPresentsQuickly(data, "48 exercises")
        assertTrue(ui.history.first().exercises.any { it.previous != null })
    }
}
