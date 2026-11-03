package com.mooscles

import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Rect
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.designsystem.components.WorkoutBarRestTag
import com.mooscles.designsystem.components.WorkoutBarSkipTag
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device UI checks in the Compose test Activity. Every action is a local
 * synthetic callback: this class never reads or writes saved workout data,
 * preferences, or the application's real timer controller.
 */
@RunWith(AndroidJUnit4::class)
class UiFeatureSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realKeyboardKeepsTimerAndFocusedInputAccessibleWithoutChangingItsSession() {
        val exercise = ExerciseUi("smoke-exercise", "Keyboard test bench press", "Chest", "Barbell")
        val workout = WorkoutUi("synthetic-keyboard-workout", "Keyboard smoke workout", System.currentTimeMillis(),
            exercises = listOf(WorkoutExerciseUi("smoke-entry", exercise,
                sets = List(4) { SetUi("smoke-set-$it", reps = 8, weightKg = 20.0) })))
        val state = mutableStateOf(UiState(loading = false, exercises = listOf(exercise), active = workout))
        val timer = mutableStateOf(TimerUi(exercise.name, 120, 120, running = true, workoutExerciseId = "smoke-entry"))
        val timerActions = mutableListOf<String>()
        val deadline = SystemClock.elapsedRealtime() + 120_000
        val actions = actions { method, _ -> if (method.contains("Timer")) timerActions += method }
        edgeToEdgeTestActivity()
        compose.setContent {
            // An isolated clock drives the real dock while the IME changes its
            // geometry. No operation reaches the saved application's countdown.
            LaunchedEffect(Unit) {
                while (true) {
                    val remaining = ((deadline - SystemClock.elapsedRealtime() + 999) / 1_000).toInt().coerceAtLeast(0)
                    timer.value = timer.value.copy(remainingSeconds = remaining, progress = remaining / 120f)
                    delay(200)
                }
            }
            MoosclesApp(state.value, actions, timer)
        }
        // The shell (Task 24): the workout bar carries the rest above the tab bar on every tab
        // route. The library's search field opens the keyboard under it.
        tab("library").performClick()
        val closedFooter = bounds(MTabBarTag)
        val closedTimer = bounds("shell-workout-bar")
        assertTrue("Closed-keyboard workout bar must sit above the tab bar", closedTimer.bottom <= closedFooter.top + 1)
        val remainingBefore = timer.value.remainingSeconds

        search().performClick()
        compose.runOnUiThread {
            ViewCompat.getWindowInsetsController(compose.activity.window.decorView)?.show(WindowInsetsCompat.Type.ime())
        }
        // IME visibility becomes true at the beginning of its animation, when
        // its inset can still be only the navigation-bar height. Wait until the
        // measured dock and actual window insets agree and remain settled.
        var previousIme = -1
        var sawDockedKeyboard = false
        var settledSince = SystemClock.elapsedRealtime()
        try {
            compose.waitUntil(15_000) {
                val insets = keyboardInsets()
                sawDockedKeyboard = sawDockedKeyboard || insets.second > closedFooter.height
                val now = SystemClock.elapsedRealtime()
                val aligned = insets.first && insets.second > closedFooter.height &&
                    kotlin.math.abs(bounds("shell-workout-bar").bottom - (windowHeight() - insets.second)) <= 2
                if (previousIme != insets.second) status("keyboard-opening-insets", keyboardGeometry())
                if (!aligned || previousIme != insets.second) settledSince = now
                previousIme = insets.second
                aligned && now - settledSince >= 250
            }
        } catch (failure: Throwable) {
            val geometry = keyboardGeometry()
            if (failure is ComposeTimeoutException && !sawDockedKeyboard) {
                status("keyboard-unavailable", "No docked software keyboard was rendered; real keyboard positioning was not verified. $geometry")
                screenshot("feature-smoke-keyboard-unavailable.png")
                assumeTrue("This device did not render a docked software keyboard (IME inset never exceeded the measured footer). " +
                    "Real keyboard positioning is unverified. $geometry", sawDockedKeyboard)
            }
            status("keyboard-alignment-failure", geometry)
            screenshot("feature-smoke-keyboard-failure.png")
            throw AssertionError("Real IME did not align with timer after settling: $geometry", failure)
        }
        compose.waitUntil(10_000) {
            val field = search().fetchSemanticsNode().boundsInRoot
            val dock = bounds("shell-workout-bar")
            field.height > 0 && field.bottom <= dock.top + 1
        }
        search().assertIsFocused().assertIsDisplayed()
        search().performTextReplacement("bench")
        val openedTimer = bounds("shell-workout-bar")
        val openedFooter = bounds(MTabBarTag)
        val imeBottom = keyboardInsets().second
        val windowHeight = windowHeight()
        assertEquals("The complete workout bar must end at the real keyboard's top edge", (windowHeight - imeBottom).toFloat(), openedTimer.bottom, 2f)
        assertEquals("The workout bar keeps its full height", closedTimer.height, openedTimer.height, 2f)
        assertEquals("The tab bar must keep its previous keyboard behavior", closedFooter.top, openedFooter.top, 2f)
        assertEquals(closedFooter.bottom, openedFooter.bottom, 2f)
        compose.onNodeWithTag(WorkoutBarSkipTag).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertIsDisplayed()
        screenshot("feature-smoke-keyboard-open.png")
        status("keyboard-open", "imeBottom=$imeBottom; windowHeight=$windowHeight; timer=$openedTimer; footer=$openedFooter; field=${search().fetchSemanticsNode().boundsInRoot}")

        compose.runOnUiThread {
            ViewCompat.getWindowInsetsController(compose.activity.window.decorView)?.hide(WindowInsetsCompat.Type.ime())
        }
        settledSince = SystemClock.elapsedRealtime()
        compose.waitUntil(15_000) {
            val insets = keyboardInsets()
            val settled = !insets.first && insets.second == 0 &&
                kotlin.math.abs(bounds("shell-workout-bar").bottom - closedTimer.bottom) <= 2
            val now = SystemClock.elapsedRealtime()
            if (!settled) settledSince = now
            settled && now - settledSince >= 250
        }
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutBarSkipTag).assertIsDisplayed()
        screenshot("feature-smoke-keyboard-closed.png")
        compose.waitUntil(3_000) { timer.value.remainingSeconds < remainingBefore }
        compose.runOnIdle {
            assertTrue("Keyboard transitions must not restart, pause, reset, or otherwise command the timer", timerActions.isEmpty())
            assertTrue(timer.value.running)
            assertEquals(120, timer.value.durationSeconds)
            assertEquals("smoke-entry", timer.value.workoutExerciseId)
            assertTrue("The isolated clock must continue rather than restart", timer.value.remainingSeconds < remainingBefore)
            assertEquals("synthetic-keyboard-workout", state.value.active?.id)
        }
        status("keyboard-closed", "timer=${bounds("shell-workout-bar")}; footer=${bounds(MTabBarTag)}; remaining=${timer.value.remainingSeconds}; timerCommands=${timerActions.size}")
    }

    @Test fun foldersAndRepeatedDiscardUseTheCorrectPageAndDoNotRestoreDiscardedSessions() {
        val upper = RoutineUi("smoke-upper", "Upper routine", folderId = "upper")
        val full = RoutineUi("smoke-full", "Full body routine", folderId = "full")
        val firstWorkout = WorkoutUi("synthetic-discard-one", "Navigation smoke workout", System.currentTimeMillis(), sourceRoutineId = upper.id)
        val state = mutableStateOf(UiState(loading = false, active = firstWorkout,
            routines = listOf(upper, full), routineFolders = listOf(RoutineFolderUi("default", "My routines"),
                RoutineFolderUi("upper", "U/L"), RoutineFolderUi("full", "FULLBODY")),
            preferences = PreferencesUi(selectedRoutineFolderId = "upper")))
        var discardCalls = 0
        val actions = actions { method, args ->
            when (method) {
                "selectRoutineFolder" -> state.value = state.value.copy(preferences = state.value.preferences.copy(selectedRoutineFolderId = args.first() as String))
                "discardWorkout" -> {
                    discardCalls++
                    state.value = state.value.copy(active = null, discardNavigationRequest = state.value.discardNavigationRequest + 1)
                }
            }
        }
        edgeToEdgeTestActivity()
        compose.setContent { MoosclesApp(state.value, actions) }
        chooseFolder("full")
        compose.onNodeWithTag("train-folder-full").assertIsSelected()
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-smoke-full-start"))
        compose.onNodeWithTag("routine-smoke-full-start").assertTextEquals("Start")
        compose.onNodeWithTag("routine-smoke-upper-start").assertDoesNotExist()
        compose.runOnIdle { assertSame("Switching folders must preserve the active workout", firstWorkout, state.value.active) }
        tab("history").performClick()
        tab("routines").performClick()
        compose.onNodeWithTag("train-folder-full").assertIsSelected()
        chooseFolder("upper")
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-smoke-upper-start"))
        compose.onNodeWithTag("routine-smoke-upper-start").assertTextEquals("Resume workout")
        repeat(2) { cycle ->
            if (cycle > 0) compose.runOnIdle {
                state.value = state.value.copy(active = firstWorkout.copy(id = "synthetic-discard-two"))
            }
            compose.onNodeWithTag("shell-workout-bar").performClick()
            compose.onNodeWithTag("workout-route").assertIsDisplayed()
            compose.onNodeWithTag("workout-more").performClick()
            compose.onNodeWithTag("workout-menu-discard").performClick()
            compose.onNodeWithTag("dialog-confirm-danger").performClick()
            assertTrain()
            compose.onNodeWithTag("workout-route").assertDoesNotExist()
            compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
            listOf("library", "history", "records", "routines").forEach { route ->
                tab(route).performClick()
                tab(route).assertIsSelected()
            }
            assertTrain()
        }
        compose.runOnIdle { assertEquals(2, discardCalls); assertNull(state.value.active) }
        tab("library").performClick()
        // performClick returns after dispatching the tap. Wait for the actual
        // navigation destination and its Back handler before dispatching Back.
        tab("library").assertIsSelected()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrain()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        screenshot("feature-smoke-discard-train.png")
        status("discard-navigation", "discardCycles=$discardCalls; selectedFolder=${state.value.preferences.selectedRoutineFolderId}; actualPage=Train; activeWorkout=null")
        // The final root Back exits only this synthetic test Activity. It cannot
        // expose a stale Workout entry, and it never finishes the real session.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("Discard must leave Train as the actual root", compose.activity.isFinishing || compose.activity.isDestroyed)
    }

    private fun edgeToEdgeTestActivity() = compose.runOnUiThread {
        compose.activity.enableEdgeToEdge()
        compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun chooseFolder(id: String) {
        // The new Train tab (plan 4B): the folders are chips at the top of the list.
        compose.onNodeWithTag("routines-list").performScrollToIndex(0)
        compose.onNodeWithTag("train-folder-$id").performClick()
    }
    private fun assertTrain() {
        tab("routines").assertIsSelected()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }
    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")
    /** The library's search field, the text field of the library tab's first screen. */
    private fun search() = compose.onAllNodes(hasSetTextAction()).onFirst()
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun keyboardInsets(): Pair<Boolean, Int> {
        var result = false to 0
        compose.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            result = (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) to (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
        }
        return result
    }
    private fun windowHeight(): Int {
        var result = 0
        compose.runOnUiThread { result = compose.activity.window.decorView.height }
        return result
    }
    private fun keyboardGeometry(): String {
        var window = ""
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val content = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            val visible = Rect()
            decor.getWindowVisibleDisplayFrame(visible)
            val insets = ViewCompat.getRootWindowInsets(decor)
            val focus = compose.activity.currentFocus
            val input = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            window = "imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime())}; ime=${insets?.getInsets(WindowInsetsCompat.Type.ime())}; " +
                "systemBars=${insets?.getInsets(WindowInsetsCompat.Type.systemBars())}; decor=${decor.width}x${decor.height}; " +
                "root=${decor.rootView.width}x${decor.rootView.height}; content=${content.width}x${content.height}; contentScreen=${location.toList()}; visibleFrame=$visible; " +
                "windowFocus=${decor.hasWindowFocus()}; focusedView=${focus?.javaClass?.name}; textEditor=${focus?.onCheckIsTextEditor()}; " +
                "imeActive=${focus?.let { input.isActive(it) }}; acceptingText=${input.isAcceptingText}; softInputMode=${compose.activity.window.attributes.softInputMode}"
        }
        fun safeBounds(tag: String) = runCatching { bounds(tag).toString() }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
        val field = runCatching { search().fetchSemanticsNode().boundsInRoot.toString() }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
        return "$window; timer=${safeBounds("shell-workout-bar")}; footer=${safeBounds(MTabBarTag)}; " +
            "field=$field; composeRoot=${runCatching { compose.onRoot().fetchSemanticsNode().boundsInRoot }.getOrNull()}"
    }
    private fun actions(block: (String, List<Any?>) -> Unit): MoosclesActions =
        Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Isolated smoke actions"
                else -> { block(method.name, args.orEmpty().toList()); null }
            }
        } as MoosclesActions
    private fun status(name: String, detail: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
        putString("check", name)
        putString("details", detail)
        putBoolean("usesSyntheticStateOnly", true)
    })
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Device screenshot unavailable" }
        try {
            FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
