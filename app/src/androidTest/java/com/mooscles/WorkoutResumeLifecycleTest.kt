package com.mooscles

import android.content.Context
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mooscles.data.*
import com.mooscles.timer.RestTimerState
import com.mooscles.timer.RestTimerPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home/Recents correctness only. Launcher, real process death and optimized timing use the external driver. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WorkoutResumeLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var app: MoosclesApplication
    private lateinit var activity: MainActivity

    @Test fun homeReturnsAndActivityRecreationRetainEditorDraftsSessionAndTimer() {
        assumeTrue("Explicitly enable the isolated resume check",
            InstrumentationRegistry.getArguments().getString("resumeLifecycle") == "true")
        app = ApplicationProvider.getApplicationContext()
        require(app.packageName == "com.mooscles.measurement") {
            "Resume lifecycle verification refuses the production application package."
        }
        val repo = app.repository
        io { repo.initialize() }
        val before = io { repo.snapshot() }
        require(before.activeWorkout == null) { "Finish the task-owned measurement session before this isolated check." }
        val idleTimer = io { RestTimerPersistence(app.moosclesStore).snapshot() }
        require(!idleTimer.running && !idleTimer.paused) { "An idle isolated timer is required." }
        val preferences = io { app.preferences.snapshot() }
        var routineId: String? = null
        var sessionId: String? = null
        var failure: Throwable? = null
        try {
            io { app.preferences.pounds(false) }
            routineId = io { repo.createRoutine("Resume lifecycle ${SystemClock.elapsedRealtime()}") }
            val movement = before.exercises.first { it.convention == LoadConvention.TOTAL_LOAD }
            repeat(2) { io { repo.addRoutineExercise(requireNotNull(routineId), movement.id) } }
            io {
                val planned = repo.snapshot()
                planned.routineItems(requireNotNull(routineId)).forEach { entry ->
                    planned.plannedSets(entry.id).forEach { row -> repo.updateRoutineSet(row.id, 6, 31.125) }
                }
            }
            launchAndAwaitActivity()
            awaitUi { !model().contentUi.value.loading }
            instrumentation.runOnMainSync { model().startWorkout(routineId) }
            awaitUi { model().contentUi.value.active?.sourceRoutineId == routineId }
            sessionId = requireNotNull(model().contentUi.value.active).id
            compose.onNodeWithTag("tab-workout").performClick()
            awaitUi { !model().contentUi.value.loading && !model().contentUi.value.secondaryLoading }
            val initial = requireNotNull(io { repo.activeSnapshot() })
            val first = initial.sets.first { it.workoutExerciseId == initial.entries.first().id }
            list().performScrollToNode(hasTestTag("workout-set-${first.id}"))
            val load = field(first.id, "set-load-0")
            val reps = field(first.id, "set-reps-0")
            load.performTextReplacement("31.1250")
            reps.performTextReplacement("6")
            field(first.id, "set-complete-0").performClick()
            awaitUi { io { repo.workoutSet(first.id) }?.completed == true }
            // Invalid text is an unsaved UI draft and must not replace the saved six reps.
            reps.performTextReplacement("6x")
            reps.performClick().performTextInputSelection(TextRange(1, 2))
            hideKeyboardWithoutClearingFocus()
            val expected = requireNotNull(io { repo.activeSnapshot() })
            assertEquals(31.125, expected.sets.first { it.id == first.id }.weightKg, 0.0)
            assertEquals(6, expected.sets.first { it.id == first.id }.reps)
            val entry = expected.entries.first()
            if (preferences.autoRest) awaitUi { app.timer.state.value.running && app.timer.state.value.workoutExerciseId == entry.id }
            io { app.timer.start(entry.name, 180, entry.id) }
            awaitUi { app.timer.state.value.running && app.timer.state.value.workoutExerciseId == entry.id && app.timer.state.value.durationMillis == 180_000L }
            val countdown = app.timer.state.value
            val retainedActivity = activity
            val retainedModel = model()

            for (backgroundMillis in listOf(1_000L, 6_000L)) {
                homeAndReturn(backgroundMillis)
                assertSame("Ordinary Home return retains the Activity", retainedActivity, activity)
                assertSame("Ordinary Home return retains the model", retainedModel, model())
                assertEditor(first.id, expected, countdown)
                reps.assertIsFocused()
                assertEquals(TextRange(1, 2), reps.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            }

            instrumentation.runOnMainSync { activity.recreate() }
            awaitResumedActivity { it !== retainedActivity }
            assertSame("Configuration recreation retains the existing ViewModel", retainedModel, model())
            awaitUi { model().contentUi.value.active?.id == sessionId }
            list().performScrollToNode(hasTestTag("workout-set-${first.id}"))
            assertEditor(first.id, expected, countdown)

            hideKeyboardWithoutClearingFocus()
            compose.onNodeWithTag("tab-library").performClick()
            compose.onNodeWithTag("tab-library").assertIsSelected()
            homeAndReturn(1_000L)
            compose.onNodeWithTag("tab-library").assertIsSelected()
            compose.onNodeWithTag("tab-workout").assertIsNotSelected()
            assertEquals(expected, io { repo.activeSnapshot() })
            assertTimerUnchanged(countdown)
            compose.onNodeWithTag("tab-workout").performClick()
            list().performScrollToNode(hasTestTag("workout-set-${first.id}"))
            assertEditor(first.id, expected, countdown)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("resumeLifecycle", "Home to actual Recents-card return 1s/6s, Activity recreation, Library return, exact saved rows, invalid/trailing-zero drafts and unchanged timer token/deadline verified; launcher/performance measured separately")
                putString("workoutId", sessionId)
                putLong("timerToken", countdown.token)
                putLong("timerDeadline", countdown.deadline)
            })
        } catch (caught: Throwable) {
            failure = caught
            throw caught
        } finally {
            try {
                // Normal repository deletion, restricted to the session created above.
                io {
                    val owned = repo.activeWorkout()?.takeIf {
                        it.id == sessionId || routineId != null && it.sourceRoutineId == routineId
                    }
                    if (owned != null) {
                        repo.discardWorkout(owned.id)
                        app.timer.clearForSessionEnd()
                    }
                    routineId?.let { repo.deleteRoutine(it) }
                    app.preferences.pounds(preferences.pounds)
                }
                assertEquals("All pre-existing database rows must survive the native check", before, io { repo.snapshot() })
                assertEquals("Existing user settings must be restored", preferences, io { app.preferences.snapshot() })
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun assertEditor(setId: String, expected: ActiveWorkoutSnapshot, timer: RestTimerState) {
        compose.onNodeWithTag("tab-workout").assertIsSelected()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        compose.onNodeWithTag("app-loading").assertDoesNotExist()
        field(setId, "set-load-0").assertIsDisplayed().assert(editable("31.1250"))
        field(setId, "set-reps-0").assertIsDisplayed().assert(editable("6x"))
        field(setId, "set-complete-0").assertIsOn()
        assertEquals(expected, io { app.repository.activeSnapshot() })
        assertTimerUnchanged(timer)
        assertFalse("Returning is not a new Start", model().workoutOpening.value)
    }

    private fun assertTimerUnchanged(expected: RestTimerState) {
        val actual = app.timer.state.value
        assertEquals("Lifecycle must not restart/pause/reset the timer", expected, actual)
        assertTrue(actual.running)
        assertTrue("The original wall-clock deadline must continue progressing", actual.remaining(System.currentTimeMillis()) < actual.durationMillis)
    }

    private fun homeAndReturn(backgroundMillis: Long) {
        val taskId = activity.taskId
        shell("input keyevent KEYCODE_HOME")
        val stoppedBy = SystemClock.elapsedRealtime() + 10_000
        while (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && SystemClock.elapsedRealtime() < stoppedBy) Thread.sleep(30)
        assertFalse("Home must actually stop the Activity", activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        Thread.sleep(backgroundMillis)
        // Return through a real task-card action, avoiding both shell MAIN launches
        // and background-restricted programmatic moveToFront calls.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val keyTime = SystemClock.uptimeMillis()
        check(automation.injectInputEvent(KeyEvent(keyTime, keyTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_APP_SWITCH, 0), true))
        check(automation.injectInputEvent(KeyEvent(keyTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_APP_SWITCH, 0), true))
        check(findRecentsCard(automation).performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Actual isolated Recents card rejected its click." }
        awaitResumedActivity { it.taskId == taskId }
    }

    private fun findRecentsCard(automation: UiAutomation): AccessibilityNodeInfo {
        val label = "Mooscles Performance Test"
        val allowedOwners = setOf("com.miui.home", "com.android.systemui", "com.android.launcher3", "com.google.android.apps.nexuslauncher", "bitpit.launcher")
        val end = SystemClock.elapsedRealtime() + 10_000
        do {
            val root = automation.rootInActiveWindow
            if (root != null) {
                val owner = root.packageName?.toString().orEmpty()
                // Wait out the Home/app surface while overview is opening; never
                // mistake a Niagara launcher icon for the actual Recents task card.
                if (owner == app.packageName || owner == "bitpit.launcher") { Thread.sleep(50); continue }
                check(owner in allowedOwners) { "Unexpected Recents owner: $owner; no click sent." }
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                fun visit(node: AccessibilityNodeInfo) {
                    if (node.packageName?.toString() == owner && node.isVisibleToUser) nodes += node
                    repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
                }
                visit(root)
                for (candidate in nodes) {
                    val description = candidate.contentDescription?.toString().orEmpty()
                    val exactTaskLabel = candidate.text?.toString() == label || description == label || description.startsWith("$label,")
                    // Never select the icon that opens app information.
                    if (!exactTaskLabel || candidate.viewIdResourceName?.endsWith(":id/icon") == true) continue
                    var clickable: AccessibilityNodeInfo? = candidate
                    repeat(6) {
                        val current = clickable ?: return@repeat
                        if (current.packageName?.toString() != owner) { clickable = null; return@repeat }
                        val bounds = Rect().also(current::getBoundsInScreen)
                        if (current.isVisibleToUser && current.isEnabled && current.isClickable && bounds.width() > 20 && bounds.height() > 20) return current
                        clickable = current.parent
                    }
                }
            }
            Thread.sleep(50)
        } while (SystemClock.elapsedRealtime() < end)
        error("Exact isolated-app Recents card not found; no guessed coordinates or shell-launch fallback.")
    }

    private fun launchAndAwaitActivity() {
        val output = shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n ${app.packageName}/com.mooscles.MainActivity")
        check(!output.contains("Error:")) { "Unable to launch the isolated Activity: $output" }
        awaitResumedActivity { true }
    }

    private fun awaitResumedActivity(accept: (MainActivity) -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        do {
            var resumed: MainActivity? = null
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull(accept)
            }
            resumed?.let { activity = it; return }
            Thread.sleep(30)
        } while (SystemClock.elapsedRealtime() < end)
        error("The isolated MainActivity did not reach the resumed state.")
    }

    private fun hideKeyboardWithoutClearingFocus() {
        instrumentation.runOnMainSync {
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            ViewCompat.getWindowInsetsController(activity.window.decorView)?.hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitForIdle()
    }
    private fun model() = ViewModelProvider(activity)[AppViewModel::class.java]
    private fun awaitUi(predicate: () -> Boolean) = compose.waitUntil(30_000, predicate)
    private fun list() = compose.onNodeWithTag("active-workout-list")
    private fun field(set: String, tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("workout-set-$set")))
    private fun editable(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
