package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
class RoutineResumeHostTest {
    @get:Rule val compose = createComposeRule()
    private val first = RoutineUi("first", "Upper day")
    private val second = RoutineUi("second", "Upper day")
    private val workout = WorkoutUi("saved-session", "Upper day", 1_000, sourceRoutineId = second.id)
    private val state = mutableStateOf(UiState(loading = false, routines = listOf(first, second), active = workout))
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private var navigations = 0
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Resume test actions"
            else -> { calls += method.name to args.orEmpty().toList(); null }
        }
    } as MoosclesActions

    @Test fun identicalNamesUseOnlyTheMatchingPersistentIdAndResumeWithoutStarting() {
        show()
        button(first.id).assertTextEquals("Start")
        button(second.id).assertTextEquals("Resume active").performClick()
        compose.onAllNodesWithText("Resume active", substring = false).assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, navigations); assertTrue(calls.isEmpty()) }
    }

    @Test fun renamingAndReorderingDoNotMoveTheResumeAssociation() {
        show()
        compose.runOnIdle {
            state.value = state.value.copy(routines = listOf(second.copy(name = "Renamed routine"), first.copy(name = "Renamed routine")))
        }
        button(second.id).assertTextEquals("Resume active")
        button(first.id).assertTextEquals("Start")
    }

    @Test fun startingAnotherRoutineProtectsTheExistingSessionAndOffersExplicitResume() {
        show()
        button(first.id).assertTextEquals("Start").performClick()
        compose.onNodeWithText("A workout is already in progress").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, navigations); assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("Cancel", substring = false).performClick()
        button(first.id).performClick()
        compose.onNodeWithText("Resume current workout").performClick()
        compose.runOnIdle { assertEquals(1, navigations); assertTrue(calls.isEmpty()) }
    }

    @Test fun freeOrLegacyUnassociatedSessionNeverMarksACardAsResumable() {
        state.value = state.value.copy(active = workout.copy(sourceRoutineId = null))
        show()
        button(first.id).assertTextEquals("Start")
        button(second.id).assertTextEquals("Start")
        compose.onAllNodesWithText("Resume active", substring = false).assertCountEquals(0)
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("Resume workout", substring = false))
        compose.onNodeWithText("Resume workout", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, navigations); assertTrue(calls.isEmpty()) }
    }

    @Test fun finishingAndDiscardingRefreshBothCardsImmediately() {
        show()
        button(second.id).assertTextEquals("Resume active")
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        button(second.id).assertTextEquals("Start")
        button(first.id).assertTextEquals("Start")
        compose.runOnIdle { state.value = state.value.copy(active = workout.copy(sourceRoutineId = first.id)) }
        button(first.id).assertTextEquals("Resume active")
        button(second.id).assertTextEquals("Start")
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        button(first.id).assertTextEquals("Start")
        button(second.id).assertTextEquals("Start")
    }

    @Test fun noActiveSessionStartsTheSelectedRoutine() {
        state.value = state.value.copy(active = null)
        show()
        button(first.id).performClick()
        compose.runOnIdle {
            assertEquals(listOf("startWorkout" to listOf(first.id)), calls)
            assertEquals(1, navigations)
        }
    }

    @Test fun editorAlsoProtectsAnUnrelatedActiveSession() {
        compose.setContent { MaterialTheme { RoutineEditor(first, state.value, actions, { navigations++ }) } }
        compose.onNodeWithTag("routine-editor-start").assertTextEquals("Start this routine").performClick()
        compose.onNodeWithText("A workout is already in progress").assertIsDisplayed()
        compose.onNodeWithText("Resume current workout").performClick()
        compose.runOnIdle { assertEquals(1, navigations); assertTrue(calls.isEmpty()) }
    }

    private fun show() {
        compose.setContent { MaterialTheme { RoutinesScreen(state.value, actions, {}, { navigations++ }) } }
    }
    private fun button(id: String): SemanticsNodeInteraction {
        val tag = "routine-$id-start"
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
}
