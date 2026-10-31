package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Essential editor readiness must not uncover other incompletely loaded routes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RecoveryUiHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val movement = ExerciseUi("recovery-movement", "Recovered bench", "Chest", "Barbell", "Total load")
    private val workout = WorkoutUi("recovery-session", "Recovered workout", System.currentTimeMillis(),
        exercises = listOf(WorkoutExerciseUi("recovery-entry", movement,
            sets = listOf(SetUi("recovery-set", reps = 6, weightKg = 20.0)))), sourceRoutineId = "routine")
    private val state = mutableStateOf(UiState(loading = false, active = workout))
    private val calls = mutableListOf<String>()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Recovered editor callbacks"
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

    @Test fun recoveredWorkoutEssentialsRemainEditableWhileTheArchiveIsStillLoading() {
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithTag("tab-workout").performClick()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("set-load-0"))
        compose.runOnIdle { state.value = state.value.copy(loading = true, secondaryLoading = true) }

        compose.onNodeWithTag("tab-workout").assertIsSelected()
        compose.onNodeWithTag("app-loading").assertDoesNotExist()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        val load = compose.onNodeWithTag("set-load-0").assertIsDisplayed().assertIsEnabled()
        load.performTextReplacement("31.1250")
        load.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("31.1250")))
        compose.runOnIdle {
            assertTrue(state.value.loading)
            assertEquals(31.125, state.value.active!!.exercises.single().sets.single().weightKg, 0.0)
            assertEquals(listOf("updateSet"), calls)
        }
        compose.onNodeWithTag("app-loading").assertDoesNotExist()
    }

    @Test fun cachedWorkoutDoesNotUncoverALibraryRouteWhoseDataIsStillLoading() {
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithTag("tab-library").performClick()
        compose.onNodeWithTag("tab-library").assertIsSelected()
        compose.runOnIdle { state.value = state.value.copy(loading = true, secondaryLoading = true) }

        compose.onNodeWithTag("app-loading").assertIsDisplayed()
        compose.onNodeWithTag("tab-library").assertIsSelected()
        compose.onNodeWithTag("tab-workout").assertIsNotSelected()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(workout.id, state.value.active!!.id)
            assertTrue("Loading a different tab must not start or modify the cached workout", calls.isEmpty())
        }
    }
}
