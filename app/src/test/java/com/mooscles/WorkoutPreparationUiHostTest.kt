package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.mooscles.ui.*
import java.io.File
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
class WorkoutPreparationUiHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun actions(block: (String, Array<out Any?>) -> Unit = { _, _ -> }) =
        Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Workout preparation UI actions"
                else -> { block(method.name, args.orEmpty()); null }
            }
        } as MoosclesActions

    private fun entry(index: Int) = WorkoutExerciseUi("entry-$index",
        ExerciseUi("shared-movement", "Movement ${index + 1}", "Chest", "Barbell"),
        sets = List(4) { set -> SetUi("set-$index-$set", 8 + set, 20.0 + index,
            completed = index == 0 && set == 1, warmup = set == 3) }, restSeconds = 90 + index,
        previous = "20 kg × 8 · 20 kg × 7")

    @Test fun preparingStatePreventsInteractionUntilTheEntireWorkoutIsReady() {
        val state = mutableStateOf(UiState(loading = false, workoutPreparing = true,
            active = WorkoutUi("workout", "Prepared session", 1, exercises = List(40, ::entry))))
        val actions = actions()
        compose.setContent { MaterialTheme { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) } }
        compose.onNodeWithTag("workout-preparing").assertIsDisplayed()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(workoutPreparing = false) }
        compose.onNodeWithTag("active-workout-list").assertExists()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("40. Movement 40"))
        compose.onNodeWithText("40. Movement 40").assertIsDisplayed()
    }

    @Test fun fortyExerciseWorkoutPreservesUnsavedInputsAcrossRepeatedFastScrollAndReorder() {
        val workout = WorkoutUi("workout", "Forty exercise scrolling", System.currentTimeMillis(), exercises = List(40, ::entry))
        val timer = TimerUi("Movement 1", 80, 120, running = true, workoutExerciseId = "entry-0")
        val state = mutableStateOf(UiState(loading = false, active = workout, timer = timer))
        val calls = mutableListOf<String>()
        val actions = actions { method, args ->
            calls += method
            if (method == "reorderWorkoutExercises") {
                @Suppress("UNCHECKED_CAST") val ids = args[1] as List<String>
                val previous = state.value.active!!
                state.value = state.value.copy(active = previous.copy(exercises = ids.map { id -> previous.exercises.single { it.id == id } }))
            }
        }
        lateinit var focus: FocusManager
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            focus = LocalFocusManager.current
            MaterialTheme { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) }
        }
        val list = compose.onNodeWithTag("active-workout-list")
        val input = hasTestTag("set-reps-0") and hasAnyAncestor(hasTestTag("workout-exercise-entry-0"))
        list.performScrollToNode(input)
        compose.onNode(input).performScrollTo().performTextReplacement("-")
        compose.onNode(input).assertTextContains("-")
        // Focused fields are intentionally pinned by Compose even outside the viewport.
        // Clear it before flinging; Compose/autofill may still retain that one offscreen item.
        compose.runOnIdle { focus.clearFocus(force = true) }
        val started = System.nanoTime()
        repeat(3) {
            repeat(4) { list.performTouchInput { swipeUp(durationMillis = 90) } }
            list.performScrollToNode(hasText("40. Movement 40"))
            compose.onNodeWithText("40. Movement 40").assertIsDisplayed()
            compose.onNode(input).assertIsNotDisplayed()
            val cardCount = compose.onAllNodes(SemanticsMatcher("workout exercise card") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("workout-exercise-") == true
            }).fetchSemanticsNodes().size
            assertTrue("Fast scrolling must keep composition bounded, had $cardCount cards", cardCount < 20)
            repeat(4) { list.performTouchInput { swipeDown(durationMillis = 90) } }
            list.performScrollToNode(input)
            compose.onNode(input).performScrollTo().assertTextContains("-")
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        // Force actual composition disposal/restoration independently of focused-item pinning.
        // The '-' draft was never persisted, so only correctly scoped saveable state can restore it.
        restoration.emulateSavedInstanceStateRestore()
        list.performScrollToNode(input)
        compose.onNode(input).performScrollTo().assertTextContains("-")
        list.performScrollToIndex(0)
        compose.onNodeWithTag("exercise-plan-reorder").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-order-drag-entry-0").performClick()
        compose.onNodeWithText("Move down").performClick()
        compose.onNodeWithTag("exercise-order-save").performClick()
        list.performScrollToNode(input)
        compose.onNode(input).performScrollTo().assertTextContains("-")
        compose.runOnIdle {
            assertEquals(listOf("entry-1", "entry-0"), state.value.active!!.exercises.take(2).map { it.id })
            assertEquals(workout.exercises.single { it.id == "entry-0" }, state.value.active!!.exercises.single { it.id == "entry-0" })
            assertSame(timer, state.value.timer)
            assertEquals(listOf("reorderWorkoutExercises"), calls)
            File("build/outputs/workout-performance/scroll-host.txt").apply {
                parentFile!!.mkdirs()
                writeText("40 exercises / 160 sets fully prepared. 24 fast swipes plus 6 end-to-end jumps: ${elapsedMs}ms host wall time.\n" +
                    "First card offscreen each round; composition remained bounded. Invalid draft preserved through scroll, forced state restoration and saved reorder.\n" +
                    "Host functional timing only; no Android-device frame-rate or jank measurement.\n")
            }
        }
    }

    @Test fun discardNavigationWaitsForConfirmedSuccessAndRemovesWorkoutFromBackStack() {
        val active = WorkoutUi("discard-me", "Never reopen this session", 1,
            exercises = listOf(entry(0)), sourceRoutineId = "routine")
        val state = mutableStateOf(UiState(loading = false, active = active,
            routines = listOf(RoutineUi("routine", "Saved routine"))))
        var requests = 0
        val actions = actions { method, _ -> if (method == "discardWorkout") requests++ }
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithText("Workout", substring = false).performClick()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("discard-workout"))
        compose.onNodeWithTag("discard-workout").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        // A request alone, including a failed database operation, cannot navigate.
        compose.onNodeWithText("Workout", substring = false).assertIsSelected()
        compose.onNodeWithTag("active-workout-list").assertExists()
        compose.runOnIdle { state.value = state.value.copy(active = null, discardedWorkoutId = active.id) }
        compose.onNodeWithText("Train", substring = false).assertIsSelected()
        compose.onNodeWithTag("routines-list").assertExists()
        compose.onAllNodesWithText("Resume active", substring = false).assertCountEquals(0)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        // Even restoring a previously saved Workout tab opens the empty state.
        compose.onNodeWithText("Workout", substring = false).performClick()
        compose.onNodeWithText("Ready to move?", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Never reopen this session").assertDoesNotExist()
    }
}
