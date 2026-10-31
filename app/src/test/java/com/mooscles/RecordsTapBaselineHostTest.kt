package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.data.ExerciseCatalog
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Today's taps on the old records screens (plan 5A, Task 13, Step 1), measured before the swap, over
 * the real navigation graph. Each `performClick` counts 1; typed text does not count (focusing the
 * search field by tapping it does). Temporary: deleted with the old screens in Task 13.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class RecordsTapBaselineHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private lateinit var state: MutableState<UiState>
    private var taps = 0

    private fun tap(node: SemanticsNodeInteraction) {
        node.performClick()
        taps++
        compose.waitForIdle()
    }

    /** From the Train tab: the Records tab, the search field (tap to focus), the typed name; the record shows. */
    @Test fun seeCurrentRecordOfAnExercise() {
        show()
        tap(compose.onNodeWithTag("tab-records"))
        val target = state.value.records.last().exercise
        tap(compose.onNodeWithText("Find a movement’s record"))
        compose.onNode(hasSetTextAction()).performTextInput(target.name)
        compose.waitForIdle()
        compose.onNode(hasText(target.name) and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Weight PR").assertIsDisplayed()
        println("TAP baseline seeCurrentRecordOfAnExercise: $taps")
        assertEquals(2, taps)
    }

    /** From the Train tab: the Records tab, then the record card, which opens the exercise history. */
    @Test fun recordToExerciseHistory() {
        show()
        tap(compose.onNodeWithTag("tab-records"))
        // The screen sorts by name: the first card is the alphabetically first record.
        val first = state.value.records.minBy { it.exercise.name }.exercise
        tap(compose.onNodeWithText(first.name))
        compose.onNodeWithText("Movement history", substring = true, ignoreCase = true).assertIsDisplayed()
        println("TAP baseline recordToExerciseHistory: $taps")
        assertEquals(2, taps)
    }

    private fun show() {
        val exercises = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
        val records = exercises.take(6).mapIndexed { i, e -> RecordUi(e, 60.0 + i * 5, 5, date = 1_700_000_000_000L + i) }
        state = mutableStateOf(UiState(loading = false, exercises = exercises, records = records))
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
    }

    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Records tap baseline actions"
            else -> { calls += method.name + ":" + (args ?: emptyArray()).joinToString(","); null }
        }
    } as MoosclesActions
}
