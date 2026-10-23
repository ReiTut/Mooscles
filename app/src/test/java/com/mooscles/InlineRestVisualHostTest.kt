package com.mooscles

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
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
class InlineRestVisualHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativePreviewsShowInlineRestLabelProgressAndFooterInConstrainedViewport() {
        val constrained = mutableStateOf(false)
        val entries = listOf(
            WorkoutExerciseUi("bench", ExerciseUi("bench", "Incline barbell bench press", "CHEST", "BARBELL"),
                sets = listOf(SetUi("bench-1", 8, 17.0), SetUi("bench-2", 8, 17.0), SetUi("bench-3", 7, 17.0))),
            WorkoutExerciseUi("long", ExerciseUi("long", "Seated Overhead Press (Machine) with a deliberately long custom exercise name", "SHOULDERS", "MACHINE"),
                sets = listOf(SetUi("long-1", 8, 35.5), SetUi("long-2", 7, 35.5)), restSeconds = 135))
        val state = UiState(loading = false, preferences = PreferencesUi(theme = "dark"),
            active = WorkoutUi("active", "Upper body", System.currentTimeMillis(), exercises = entries),
            timer = TimerUi("Pec deck fly", 69, 120, running = true, progress = 0.575f))
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Rest preview actions"
                else -> null
            }
        } as MoosclesActions
        compose.setContent {
            MoosclesTheme("dark") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(if (constrained.value) 550.dp else 900.dp)) { MoosclesApp(state, actions) }
                    }
                }
            }
        }
        compose.onNodeWithText("Workout", substring = false).performClick()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-bench"))
        compose.onNodeWithTag("workout-rest-edit-bench").assertIsDisplayed()
        assertDockAboveFooterAndOutsideList()
        capture("inline-label-and-progress.png")

        compose.onNodeWithTag("active-workout-list").performScrollToNode(
            hasText("2. Seated Overhead Press (Machine) with a deliberately long custom exercise name"))
        compose.onNodeWithText("2. Seated Overhead Press (Machine) with a deliberately long custom exercise name").assertIsDisplayed()
        capture("long-exercise-name-and-timer.png")
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-long"))
        // Robolectric has no real IME. Reduce the entire app viewport to verify
        // scrolling, touch controls, and dock layout under keyboard-sized pressure.
        compose.runOnIdle { constrained.value = true }
        val density = compose.activity.resources.displayMetrics.density
        val viewport = compose.onNodeWithTag("active-workout-list").fetchSemanticsNode().boundsInRoot
        assertTrue("The list must actually shrink above the simulated keyboard",
            viewport.height <= 550 * density + 1)
        val appFooter = compose.onNodeWithText("Train", substring = false).fetchSemanticsNode().boundsInRoot
        assertTrue("The footer must also stay within the constrained app viewport",
            appFooter.bottom <= 550 * density + 1)
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-long"))
        compose.onNodeWithTag("workout-rest-edit-long").performScrollTo().assertIsDisplayed()
        assertDockAboveFooterAndOutsideList()
        capture("rest-label-constrained-viewport.png")
        // Dialog focus, numeric input, cancellation, saving, and lazy-card
        // disposal are exercised by InlineRestEditorHostTest in the supported
        // legacy graphics mode. Native dialog focus does not idle in this host.
    }

    private fun assertDockAboveFooterAndOutsideList() {
        val list = compose.onNodeWithTag("active-workout-list").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("active-rest-timer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithText("Train", substring = false).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("rest-timer-progress").assertIsDisplayed()
        assertTrue("The workout viewport must end above the measured timer dock", list.bottom <= dock.top)
        assertTrue("Timer controls must leave footer navigation visible", dock.bottom <= footer.top)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/rest-timer-previews", name)
            file.parentFile!!.mkdirs()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            assertTrue(file.length() > 1000)
        }
    }
}
