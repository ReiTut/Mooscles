package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
        compose.onNodeWithTag("rest-duration").assertTextContains("1:30")
        compose.onNodeWithText("Use 0:00 to 5:00, with seconds from 00 to 59.").assertExists()
    }

    @Test fun numericTimerTypingKeepsColonAndAdvancesSeconds() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(90, { saved += it }) } }
        val field = compose.onNodeWithTag("rest-duration")
        field.performTextReplacement("2")
        field.assertTextContains("2:00")
        field.performTextInput("0")
        field.performTextInput("3")
        field.assertTextContains("2:03")
        field.performTextReplacement("5:00")
        field.performTextReplacement("5:01")
        field.assertTextContains("5:00")
        compose.runOnIdle { assertEquals(listOf(120, 123, 300), saved) }
    }

    @Test fun invalidInputDoesNotModifyLegacyHistoricalDuration() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(600, { saved += it }) } }
        compose.onNodeWithTag("rest-duration").performTextReplacement("6:00")
        compose.onNodeWithTag("rest-duration").assertTextContains("5:00")
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
    }

    @Test fun timerIncreaseButtonIsDisabledAtFiveMinutes() {
        val adjustments = mutableListOf<Int>()
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
            arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Timer button test actions"
                else -> { if (method.name == "adjustTimer") adjustments += args!![0] as Int; null }
            }
        } as MoosclesActions
        compose.setContent { MaterialTheme {
            TimerPanel(TimerUi(remainingSeconds = 300, durationSeconds = 300, paused = true), actions, 90, compact = true)
        } }
        compose.onNodeWithText("+0:15").assertIsNotEnabled().performClick()
        compose.onNodeWithText("−0:15").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(-15), adjustments) }
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

    @Test fun blankWeightCompletesAsBodyweightAndStaysBlankAfterEditorRestoration() {
        val entry = mutableStateOf(WorkoutExerciseUi("entry", ExerciseUi("pullup", "Pull-up", "Back", "Bodyweight", "Added weight"),
            sets = listOf(SetUi("set"))))
        val editorRevision = mutableIntStateOf(0)
        val editable = mutableStateOf(true)
        val updates = mutableListOf<Array<out Any?>>()
        val actions = testActions { method, args ->
            if (method == "updateSet") {
                updates += args
                entry.value = entry.value.copy(sets = listOf(SetUi(args[0] as String, args[1] as Int,
                    args[2] as Double, args[3] as Boolean, args[4] as Boolean, args[5] as Boolean)))
            }
        }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    key(editorRevision.intValue) { WorkoutExerciseCard(entry.value, 0, 1, false, actions, editable.value, {}, {}) }
                }
            }
        }
        compose.onNodeWithTag("set-load-0").performScrollTo().assert(blankInput)
        compose.onNodeWithTag("set-complete-0").performClick()
        compose.onNodeWithText("Enter reps between 1 and 9999 to complete this set.").assertExists()
        compose.runOnIdle { assertTrue(updates.isEmpty()) }
        compose.onNodeWithTag("set-reps-0").performTextReplacement("12")
        compose.onNodeWithTag("set-complete-0").performClick()
        compose.runOnIdle {
            assertEquals(0.0, entry.value.sets.single().weightKg, 0.0)
            assertEquals(12, entry.value.sets.single().reps)
            assertTrue(entry.value.sets.single().completed)
            editorRevision.intValue++
        }
        compose.onNodeWithTag("set-load-0").performScrollTo().assert(blankInput)
        compose.onNodeWithContentDescription("Undo set completion").assertExists()
        compose.runOnIdle { editable.value = false }
        compose.onNodeWithText("Bodyweight × 12").performScrollTo().assertIsDisplayed()
    }

    @Test fun restTimerStaysVisibleWhileLongWorkoutScrollsOnSmallScreen() {
        val calls = mutableListOf<String>()
        val actions = testActions { method, _ -> calls += method }
        val entries = List(12) { index -> WorkoutExerciseUi("entry-$index",
            ExerciseUi("exercise-$index", "Movement ${index + 1}", "Back", "Bodyweight", "Added weight"),
            sets = listOf(SetUi("set-$index", reps = 10, completed = true))) }
        val state = UiState(loading = false, active = WorkoutUi("workout", "Long workout", System.currentTimeMillis(), exercises = entries),
            timer = TimerUi("Pull-up", remainingSeconds = 120, durationSeconds = 120, running = true))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, 470.dp)) { ActiveWorkoutScreen(state, actions, {}, {}, {}) }
            }
        }
        val dockBefore = compose.onNodeWithTag("active-rest-timer").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("finish-workout"))
        compose.onNodeWithTag("finish-workout").assertIsDisplayed()
        val dockAfter = compose.onNodeWithTag("active-rest-timer").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(dockBefore, dockAfter)
        val listBounds = compose.onNodeWithTag("active-workout-list").getUnclippedBoundsInRoot()
        assertTrue("The timer must be outside the list viewport", listBounds.bottom <= dockAfter.top)
        compose.onNodeWithContentDescription("Pause rest timer").performClick()
        compose.runOnIdle { assertTrue("pauseTimer" in calls) }
        compose.onNodeWithTag("active-workout-list").performScrollToIndex(0)
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
    }

    private val blankInput = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))

    private fun testActions(onAction: (String, Array<out Any?>) -> Unit): MoosclesActions =
        Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Workout input test actions"
                else -> { onAction(method.name, args ?: emptyArray()); null }
            }
        } as MoosclesActions
}
