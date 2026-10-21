package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WorkoutInputHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restCanReturnToOriginalValueBeforePersistenceEmits() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(90, { saved += it }) } }
        compose.onNodeWithTag("rest-duration").performTextReplacement("2:00")
        compose.onNodeWithTag("rest-duration").performTextReplacement("1:30")
        compose.onNodeWithTag("rest-duration").performTextReplacement("1:")
        compose.runOnIdle { assertEquals(listOf(120, 90), saved) }
        compose.onNodeWithText("Enter minutes:seconds, from 0:00 to 60:00.").assertExists()
    }

    @Test fun clearedIncompleteRepsStayBlankWhenWeightChanges() {
        val updates = mutableListOf<Array<out Any?>>()
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
            arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Workout input test actions"
                else -> { if (method.name == "updateSet") updates += args!!; null }
            }
        } as MoosclesActions
        val entry = WorkoutExerciseUi("entry", ExerciseUi("bench", "Bench press", "Chest", "Barbell"),
            sets = listOf(SetUi("set", reps = 8, weightKg = 10.0)))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("input-scroll")) {
                    WorkoutExerciseCard(entry, 0, 1, false, actions, true, {}, {})
                }
            }
        }
        compose.onNodeWithTag("set-reps-0").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("set-load-0").performTextReplacement("20")
        compose.runOnIdle {
            assertEquals(0, updates.first()[1])
            assertEquals(0, updates.last()[1])
            assertEquals(20.0, updates.last()[2] as Double, 0.0)
            assertEquals(false, updates.last()[3])
        }
    }
}
