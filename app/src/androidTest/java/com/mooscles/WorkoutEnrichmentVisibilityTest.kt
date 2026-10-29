package com.mooscles

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mooscles.ui.*
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real IME and app chrome; every callback is synthetic, with no saved-data/timer writes. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WorkoutEnrichmentVisibilityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var explicitActivity: ComponentActivity
    private val exercise = ExerciseUi("synthetic-bench", "Enrichment test bench", "Chest", "Barbell", "Total load")
    private val initial = WorkoutUi("synthetic-enrichment", "Enrichment visibility", System.currentTimeMillis(),
        exercises = List(3) { entry -> WorkoutExerciseUi("entry-$entry", exercise,
            sets = List(4) { row -> SetUi("set-$entry-$row", 8, 20.0) }, restSeconds = 120) })
    private val state = mutableStateOf(UiState(loading = false, active = initial, secondaryLoading = true))
    private val timer = mutableStateOf(TimerUi(exercise.name, 120, 120, running = true, workoutExerciseId = "entry-0"))
    private val timerActions = mutableListOf<String>()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Synthetic enrichment callbacks"
            "updateSet" -> {
                val values = requireNotNull(args)
                val changed = SetUi(values[0] as String, values[1] as Int, values[2] as Double,
                    values[3] as Boolean, values[4] as Boolean, values[5] as Boolean)
                state.value = state.value.copy(active = state.value.active!!.copy(exercises =
                    state.value.active!!.exercises.map { entry -> entry.copy(sets = entry.sets.map {
                        if (it.id == changed.id) changed else it
                    }) }))
                null
            }
            else -> { if (method.name.contains("Timer")) timerActions += method.name; null }
        }
    } as MoosclesActions

    @Test fun fullLateHistoryAndPrInformationKeepsCurrentAndLaterInputsVisibleAboveTheKeyboard() {
        try {
            launchSyntheticActivity()
            compose.runOnUiThread {
                explicitActivity.enableEdgeToEdge()
                explicitActivity.setContent { MoosclesApp(state.value, actions, timer) }
            }
            compose.onNodeWithTag("tab-workout").performClick()
            list().performScrollToNode(hasTestTag("workout-set-set-0-0"))
            val current = field("set-0-0", "set-load-0")
            current.performTextReplacement("31.1250")
            field("set-0-0", "set-reps-0").performTextReplacement("6")
            field("set-0-0", "set-complete-0").performClick()
            current.performClick().performTextInputSelection(TextRange(2, 5))
            awaitKeyboardSettled()
            current.assertIsFocused().assertIsDisplayed()
            val firstBounds = current.getUnclippedBoundsInRoot()
            val savedRows = state.value.active!!.exercises.flatMap { it.sets }
            val savedTimer = timer.value
            screenshot("current-before")
            enrich(0)
            current.assertIsFocused().assertIsDisplayed().assert(editable("31.1250"))
            assertEquals(TextRange(2, 5), current.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            assertEquals(firstBounds, current.getUnclippedBoundsInRoot())
            assertEquals(savedRows, state.value.active!!.exercises.flatMap { it.sets })
            assertEquals(savedTimer, timer.value)
            assertTrue(imeVisible())
            screenshot("current-after")

            // A second boundary case: an earlier exercise's short, nonempty
            // metadata starts the viewport while the next exercise is edited.
            hideKeyboard()
            compose.runOnUiThread {
                state.value = state.value.copy(active = state.value.active!!.copy(exercises =
                    state.value.active!!.exercises.mapIndexed { index, entry -> entry.copy(
                        previous = if (index == 0) "Earlier history" else null, records = emptyList()) }))
            }
            list().performScrollToNode(hasTestTag("workout-set-set-1-0"))
            val later = field("set-1-0", "set-load-0")
            later.performTextReplacement("31.1250")
            later.performClick().performTextInputSelection(TextRange(2, 5))
            awaitKeyboardSettled()
            // 3 global rows, then heading + 4 sets + footer = metadata index 9.
            list().performScrollToIndex(9)
            val earlierMetadata = compose.onNodeWithTag("exercise-metadata-entry-0").assertIsDisplayed()
            compose.onNodeWithText("Previous: Earlier history", substring = false).assertIsDisplayed()
            val metadataTop = earlierMetadata.getUnclippedBoundsInRoot().top - list().getUnclippedBoundsInRoot().top
            assertTrue("Earlier metadata must actually start the viewport: $metadataTop",
                metadataTop >= 0.dp && metadataTop <= 32.dp)
            later.assertIsDisplayed().assertIsFocused()
            val laterBounds = later.getUnclippedBoundsInRoot()
            val laterRows = state.value.active!!.exercises.flatMap { it.sets }
            screenshot("earlier-before")
            enrich(0)
            later.assertIsDisplayed().assertIsFocused().assert(editable("31.1250"))
            assertEquals(TextRange(2, 5), later.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            assertEquals(laterBounds, later.getUnclippedBoundsInRoot())
            assertEquals(laterRows, state.value.active!!.exercises.flatMap { it.sets })
            assertEquals(savedTimer, timer.value)
            assertTrue(imeVisible())
            screenshot("earlier-after")

            hideKeyboard()
            list().performScrollToNode(hasTestTag("exercise-metadata-entry-0"))
            compose.onNodeWithText("Previous: $previous", substring = false).assertExists()
            compose.onNodeWithText("Weight PR", substring = true).assertExists()
            compose.onNodeWithText("Reps PR", substring = true).assertExists()
            assertTrue("Enrichment must never dispatch timer commands", timerActions.isEmpty())
            assertEquals(listOf("entry-0", "entry-1", "entry-2"), state.value.active!!.exercises.map { it.id })
        } catch (failure: Throwable) {
            runCatching { screenshot("failure") }.onFailure { failure.addSuppressed(it) }
            throw failure
        } finally {
            try { if (::explicitActivity.isInitialized) hideKeyboard() }
            finally {
                if (::explicitActivity.isInitialized) compose.runOnUiThread { explicitActivity.finish() }
            }
        }
    }

    /** Shell launch avoids vendor restrictions on instrumentation background starts. */
    private fun launchSyntheticActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = instrumentation.targetContext.packageName
        val output = instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n $targetPackage/androidx.activity.ComponentActivity"
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        check(!output.contains("Error:")) { "Unable to launch exported Compose test Activity: $output" }
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            var resumed: ComponentActivity? = null
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ComponentActivity>()
                    .singleOrNull { it.javaClass == ComponentActivity::class.java }
            }
            resumed?.let { explicitActivity = it; return }
            Thread.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Compose test Activity did not resume after shell launch: $output")
    }

    private val previous = List(12) { "24 kg × ${8 + it % 3}" }.joinToString(" · ")
    private fun enrich(index: Int) = compose.runOnUiThread {
        val workout = state.value.active!!
        state.value = state.value.copy(secondaryLoading = false, active = workout.copy(exercises =
            workout.exercises.mapIndexed { current, entry ->
                if (current != index) entry else entry.copy(previous = previous, records = listOf(
                    RecordUi(entry.exercise, 31.125, 6, previousKg = 24.0, workoutId = workout.id),
                    RecordUi(entry.exercise, 24.0, 12, workoutId = workout.id, kind = RecordKind.REPS, previousReps = 10)))
            }))
    }
    private fun imeVisible() = ViewCompat.getRootWindowInsets(explicitActivity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    private fun awaitKeyboardSettled() {
        compose.runOnUiThread {
            ViewCompat.getWindowInsetsController(explicitActivity.window.decorView)?.show(WindowInsetsCompat.Type.ime())
        }
        var inset = -1
        var stableSince = SystemClock.elapsedRealtime()
        compose.waitUntil(10_000) {
            val current = ViewCompat.getRootWindowInsets(explicitActivity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            if (current != inset) stableSince = SystemClock.elapsedRealtime()
            inset = current
            current > 100 && imeVisible() && SystemClock.elapsedRealtime() - stableSince > 350
        }
    }
    private fun hideKeyboard() {
        compose.runOnUiThread {
            val activity = explicitActivity
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()
    }
    private fun list() = compose.onNodeWithTag("active-workout-list")
    private fun field(set: String, tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("workout-set-$set")))
    private fun editable(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
    private fun screenshot(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = context.getExternalFilesDir("startup-enrichment")
            ?: File(context.cacheDir, "startup-enrichment")
        check(directory.exists() || directory.mkdirs())
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(directory, "$label-${SystemClock.elapsedRealtime()}.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
