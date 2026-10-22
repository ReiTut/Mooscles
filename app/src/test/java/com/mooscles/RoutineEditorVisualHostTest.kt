package com.mooscles

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mooscles.ui.*
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w430dp-h932dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoutineEditorVisualHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeEditorPreviewsCoverMultipleExercisesLongNamesAndConstrainedFocusedInput() {
        val constrained = mutableStateOf(false)
        val original = RoutineEditorHostTest.fixtures()
        val routine = original.copy(exercises = original.exercises + PlannedExerciseUi("long", ExerciseUi("machine", "Seated Overhead Press (Machine) with a deliberately long custom exercise name", "SHOULDERS", "MACHINE"),
            setTargets = listOf(RoutineSetUi("long-1", 8, 35.5), RoutineSetUi("long-2", 7, 35.5, false))))
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Visual test actions"
                else -> null
            }
        } as MoosclesActions
        compose.setContent {
            MoosclesTheme("dark") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(if (constrained.value) 360.dp else 900.dp)) {
                            RoutineEditor(routine, UiState(loading = false), actions, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-bench-menu"))
        compose.onNodeWithTag("routine-set-bench-1-load").assertIsDisplayed()
        capture("reference-set-rows.png")

        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-long-menu"))
        compose.onNodeWithText("Seated Overhead Press (Machine) with a deliberately long custom exercise name").assertIsDisplayed()
        capture("long-exercise-name.png")

        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-set-long-2-reps"))
        compose.onNodeWithTag("routine-set-long-2-reps").performClick().assertIsFocused()
        // A native host render verifies the constrained viewport; a real IME
        // still needs a physical-device check because Robolectric has none.
        compose.runOnIdle { constrained.value = true }
        val viewport = compose.onNodeWithTag("routine-editor-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Native render must actually constrain the editor above the simulated keyboard",
            viewport.height <= 360 * compose.activity.resources.displayMetrics.density + 1)
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-set-long-2-reps"))
        compose.onNodeWithTag("routine-set-long-2-reps").assertIsDisplayed()
        capture("focused-input-constrained-viewport.png")
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-long-add-set"))
        compose.onNodeWithTag("routine-long-add-set").assertIsDisplayed()
        capture("add-set-constrained-viewport.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/routine-editor-previews", name)
            file.parentFile!!.mkdirs()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            assertTrue(file.length() > 1000)
        }
    }
}
