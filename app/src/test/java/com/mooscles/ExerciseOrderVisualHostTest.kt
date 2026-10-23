package com.mooscles

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseOrderVisualHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun compactOrderingRemainsReadableAt360DpWithLongAndRepeatedNames() = preview("order-360dp.png")

    @Test
    @Config(qualifiers = "w430dp-h932dp-xhdpi")
    fun compactOrderingRemainsReadableAt430DpWithLongAndRepeatedNames() = preview("order-430dp.png")

    private fun preview(fileName: String) {
        val original = RoutineEditorHostTest.fixtures()
        val routine = original.copy(exercises = listOf(
            original.exercises.first(),
            original.exercises.first().copy(id = "repeated-bench", setTargets = listOf(RoutineSetUi("repeated-set", 6, 22.0))),
            PlannedExerciseUi("long", ExerciseUi("machine", "Seated Overhead Press (Machine) with a deliberately long custom exercise name", "SHOULDERS", "MACHINE"),
                setTargets = listOf(RoutineSetUi("long-set", 8, 35.5))),
        ))
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Order preview actions"
                else -> null
            }
        } as MoosclesActions
        compose.setContent {
            MoosclesTheme("dark") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    RoutineEditor(routine, UiState(loading = false), actions, {})
                }
            }
        }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("exercise-plan-reorder"))
        compose.onNodeWithTag("exercise-plan-reorder").assertIsDisplayed().performClick()
        compose.onNodeWithTag("routine-editor-list").assertExists()
        compose.onNodeWithTag("exercise-order-editor").assertIsDisplayed()
        compose.onNodeWithTag("exercise-order-drag-bench").assertIsDisplayed()
        compose.onNodeWithTag("exercise-order-drag-repeated-bench").assertIsDisplayed()
        compose.onNodeWithTag("exercise-order-drag-long").assertIsDisplayed()
        compose.onNodeWithTag("exercise-order-save").assertIsDisplayed()
        compose.onNodeWithTag("exercise-order-cancel").assertIsDisplayed()
        val orderContent = hasAnyAncestor(hasTestTag("exercise-order-editor"))
        compose.onAllNodes(orderContent and (hasText("kg", substring = true, ignoreCase = true) or hasText("Reps", substring = true, ignoreCase = true)))
            .assertCountEquals(0)
        // There are no focused numeric inputs in this capture. Their drafts and
        // dialog cancellation are exercised separately in legacy graphics mode.
        compose.waitForIdle()
        compose.runOnIdle {
            val view = ShadowDialog.getLatestDialog().window!!.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/outputs/workout-order-previews", fileName)
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            assertTrue(output.length() > 1_000)
        }
    }
}
