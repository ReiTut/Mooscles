package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Contract checks with animation time stopped; phone captures establish painted visibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalTestApi::class)
class WorkoutOpeningOverlayHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val opening = mutableStateOf(false)
    private val exercise = ExerciseUi("exercise", "Opening test bench", "Chest", "Barbell", "Total load")
    private val workout = WorkoutUi("session", "Opening test", 1000,
        exercises = listOf(WorkoutExerciseUi("entry", exercise, listOf(SetUi("set", 8, 20.0)))),
        sourceRoutineId = "routine")
    private val state = mutableStateOf(UiState(loading = false,
        routines = listOf(RoutineUi("routine", "Opening routine"))))
    private val calls = mutableListOf<String>()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Opening screen test callbacks"
            "startWorkout" -> {
                calls += method.name
                opening.value = true // The presentation UiState deliberately still says false.
                null
            }
            "updateSet" -> {
                calls += method.name
                val values = requireNotNull(args)
                val saved = SetUi(values[0] as String, values[1] as Int, values[2] as Double,
                    values[3] as Boolean, values[4] as Boolean, values[5] as Boolean)
                state.value = state.value.copy(active = state.value.active!!.copy(exercises =
                    state.value.active!!.exercises.map { entry -> entry.copy(sets = entry.sets.map {
                        if (it.id == saved.id) saved else it
                    }) }))
                null
            }
            else -> { calls += method.name; null }
        }
    } as MoosclesActions

    @After fun releaseAnimationClockBeforeRuleCleanup() {
        compose.mainClock.autoAdvance = true
    }

    @Test fun directStartShowsOneFullWindowPaneOnTheNextFrameWithoutWaitingForPresentationOrFade() {
        showTrain()
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()

        compose.onAllNodesWithTag("starting-workout").assertCountEquals(1)
        val overlay = compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        assertEquals(compose.onNodeWithTag("app-root").getUnclippedBoundsInRoot(),
            overlay.getUnclippedBoundsInRoot())
        compose.onNodeWithText("Starting workout…").assertIsDisplayed()
        compose.onNodeWithText("Loading your routine in the background.").assertIsDisplayed()
        compose.onNode(hasTestTag("starting-workout-progress") or hasTestTag("starting-workout-static-progress"),
            useUnmergedTree = true).assertExists()
        compose.onNodeWithText("A fresh start").assertDoesNotExist()
        compose.onNodeWithTag("app-footer").assertDoesNotExist()
        assertFalse("The direct signal does not wait for combined UiState", state.value.startingWorkout)
        assertEquals(listOf("startWorkout"), calls)
    }

    @Test fun readyEssentialsRemoveThePaneOnTheNextFrameAndSecondaryLoadingDoesNotRestoreIt() {
        showTrain()
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()

        compose.runOnIdle {
            // A stale combined starting=true cannot override the direct completion signal.
            state.value = state.value.copy(active = workout, startingWorkout = true, secondaryLoading = true)
            opening.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        compose.onNodeWithTag("tab-workout").assertIsSelected()
        // The next-frame handoff is checked above. Lazy scrolling and input
        // dispatch need their normal frame clock, including prefetch/remeasure.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("set-load-0"))
        val input = compose.onNodeWithTag("set-load-0").assertIsDisplayed().assertIsEnabled()
        input.performTextReplacement("31.1250")
        input.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("31.1250")))
        compose.runOnIdle {
            assertEquals(31.125, state.value.active!!.exercises.single().sets.single().weightKg, 0.0)
            state.value = state.value.copy(secondaryLoading = false, active = state.value.active!!.copy(
                exercises = state.value.active!!.exercises.map { it.copy(previous = "20 kg × 8") }))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        input.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("31.1250")))
    }

    @Test fun openingPaneConsumesTapsInsteadOfOpeningTheCoveredSettingsButton() {
        showTrain()
        val settings = compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("app-root").performTouchInput { click(settings) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        compose.onNodeWithText("Make it yours", substring = true, ignoreCase = true).assertDoesNotExist()
        assertEquals(listOf("startWorkout"), calls)
    }

    @Test fun backDismissesThePaneAndACompletedSessionCanResumeWithoutAnotherLoader() {
        showTrain()
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        compose.onNodeWithTag("tab-routines").assertIsSelected()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle() // Retain the ordinary Back transition.
        compose.runOnIdle { state.value = state.value.copy(active = workout); opening.value = false }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-routine-start"))
        compose.onNodeWithText("Resume active").assertExists()
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        compose.onNodeWithTag("tab-workout").assertIsSelected()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("active-workout-list").assertIsDisplayed()
        assertEquals("Resume reuses saved essential data without starting another session", listOf("startWorkout"), calls)
    }

    @Test fun failedStartReleasesThePaneAndAllowsAnImmediateRetry() {
        showTrain()
        compose.mainClock.autoAdvance = false
        start().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            state.value = state.value.copy(error = "Routine unavailable")
            opening.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        compose.onNodeWithText("A fresh start").assertExists()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        // The error notification overlays this small window's bottom button.
        // This contract starts at the accepted callback; native tests cover taps.
        compose.onNodeWithText("Routine unavailable").assertIsDisplayed()
        // This item is initially outside the lazy viewport and has no node yet.
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("A fresh start")))
            .performScrollToNode(hasText("Start empty workout"))
        compose.onNodeWithText("Start empty workout").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Start empty workout").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }
        compose.runOnUiThread {
            assertTrue("The retry tap must actually reach its Start callback", opening.value)
            assertEquals(listOf("startWorkout", "startWorkout"), calls.filter { it == "startWorkout" })
            // A paused test clock does not pump Android's snapshot manager.
            // Deliver pending writes before its one available composition frame.
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        assertEquals(listOf("startWorkout", "startWorkout"), calls.filter { it == "startWorkout" })
    }

    private fun showTrain() {
        compose.setContent { MoosclesApp(state.value, actions, openingWorkoutState = opening) }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-routine-start"))
    }
    private fun start() = compose.onNodeWithTag("routine-routine-start")
}
