package com.mooscles.screens.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import com.mooscles.designsystem.components.RestTimerExpanded
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_timer_title
import com.mooscles.screens.resources.workout_timer_title_short
import com.mooscles.screens.workout.RestUpNext
import com.mooscles.screens.workout.upNextText
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.mooscles.designsystem.components.ExerciseAddSetTag
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.exerciseFooterTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.TimerCompactTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerExpandedTag
import com.mooscles.designsystem.components.TimerMinusTag
import com.mooscles.designsystem.components.TimerOpenTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.components.TimerTitleTag
import com.mooscles.designsystem.components.WorkoutBarSkipTag
import com.mooscles.designsystem.components.WorkoutBarTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenFrame
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.assertWorkoutTargets
import com.mooscles.screens.isPlaced
import com.mooscles.screens.screenReaderOrder
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.screens.shell.ShellWorkoutBarTag
import com.mooscles.screens.summary.WorkoutSummaryScreen
import com.mooscles.screens.traversalGroupWithin
import com.mooscles.screens.workout.ActiveWorkoutRoute
import com.mooscles.screens.workout.RecordToastTag
import com.mooscles.screens.workout.WorkoutClockTag
import com.mooscles.screens.workout.WorkoutHeaderTags
import com.mooscles.screens.workout.exerciseHeaderTag
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The accessibility and large text sweep of the new workout screens (spec "Acessibilidade",
 * plan's global constraints): the active workout with sets pending, a set in edit, the record
 * toast, the docked rest, the expanded rest, the paused rest, the summary, the tab bar and the
 * workout bar, each in light and dark × at 1.0, 1.3 and 2.0 ([SWEEP_FONT_SCALES]: 2.01 too on
 * Android) × on 320 and 412 dp wide phones, in Portuguese (the longer texts). One test per state:
 * it reaches the state once, then changes the theme, the scale and the width within the same
 * composition ([ScreenFrame] over [Combo] state) and checks each combination. In each: every
 * clickable has a label and a 48 dp target ([assertClickablesLabeledAndSized]), the workout's
 * actions 56 dp ([assertWorkoutTargets]), every number is whole ([assertNumbersWhole]) and no
 * button text is cut ([assertButtonTextsWhole]). The workout list is checked in three looks
 * ([listPasses]: its top, the bench's footer, its end), each requiring its controls to be there.
 * All the failures of a state are reported together.
 *
 * Plus the screen reader's order over the workout ([readingOrderIsHeaderToastCardsDock]) and the
 * dock and the workout bar as traversal groups ([dockAndWorkoutBarAreTraversalGroups]).
 *
 * The expanded timer is a dialog, a window of its own: it takes the theme and the frame's density
 * and font scale (it passes the host's in, as the design system's overlays do), so [restExpanded]
 * checks it at each scale, but not the frame's width (the window is the whole screen's), so its
 * content is also swept in the frame ([restExpandedContent]).
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutAccessibilitySweepTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    // --- The data -------------------------------------------------------------------------------

    private val bench = ExerciseUi("bench", "Barbell bench press", "Chest", "Barbell", convention = "Total load")
    private val row = ExerciseUi("row", "Overhand barbell row", "Back", "Barbell", convention = "Total load")
    private val press = ExerciseUi("press", "Seated dumbbell shoulder press", "Shoulders", "Dumbbell", convention = "Per hand")

    private fun previous(kg: Double, reps: Int, count: Int = 3) = List(count) { PreviousSetUi(kg, reps, warmup = false, assistance = false) }

    /**
     * "Push day", 42:18 in: the bench with set 1 done at 80 × 8, set 2 to do at 82.5 × 8 and set 3
     * empty (suggested from set 1), last week's 80 × 8 three times beside them; the row and the
     * shoulder press, three sets each to do (their cards collapsed).
     */
    private val workout = WorkoutUi(
        id = "w1",
        name = "Push day",
        startedAt = START,
        exercises = listOf(
            WorkoutExerciseUi(
                "e1", bench,
                listOf(SetUi("s1", 8, 80.0, completed = true), SetUi("s2", 8, 82.5), SetUi("s3")),
                previousSets = previous(80.0, 8), previousDate = START - WEEK_MS,
            ),
            WorkoutExerciseUi("e2", row, List(3) { SetUi("r${it + 1}", 10, 70.0) }, previousSets = previous(70.0, 10)),
            WorkoutExerciseUi("e3", press, List(3) { SetUi("p${it + 1}", 10, 22.5) }, previousSets = previous(22.5, 10)),
        ),
    )

    private val resting = TimerUi("Barbell bench press", remainingSeconds = 84, durationSeconds = 120, running = true, workoutExerciseId = "e1")
    private val paused = resting.copy(running = false, paused = true)

    /** The workout finished at 1:02:18 with its record: set 2 at 82.5 × 8 beat last week's 80. */
    private val finished = WorkoutUi(
        id = "w1",
        name = "Push day",
        startedAt = START,
        endedAt = START + (62 * 60 + 18) * 1_000L,
        exercises = listOf(
            WorkoutExerciseUi("e1", bench, listOf(SetUi("s1", 8, 80.0, true), SetUi("s2", 8, 82.5, true), SetUi("s3", 8, 82.5, true))),
            WorkoutExerciseUi("e2", row, List(3) { SetUi("r${it + 1}", 10, 70.0, true) }),
            WorkoutExerciseUi("e3", press, List(3) { SetUi("p${it + 1}", 10, 22.5, true) }),
        ),
        records = listOf(RecordUi(bench, 82.5, 8, previousKg = 80.0, workoutId = "w1", date = START)),
    )

    /**
     * Records every call and echoes `updateSet` into [state] as the view model would, the set
     * done carrying its record from [records] (set 2's weight record).
     */
    private class Fake(initial: UiState, val recording: RecordingActions = RecordingActions()) : MoosclesActions by recording {
        val state: MutableState<UiState> = mutableStateOf(initial)
        val records = mapOf("s2" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0))

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            val active = state.value.active ?: return
            state.value = state.value.copy(
                active = active.copy(
                    exercises = active.exercises.map { e ->
                        e.copy(sets = e.sets.map {
                            if (it.id != setId) it
                            else it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance,
                                record = records[setId]?.takeIf { completed })
                        })
                    },
                ),
            )
        }
    }

    private fun stateOf(active: WorkoutUi?) =
        UiState(loading = false, active = active, exercises = listOf(bench, row, press), history = listOf(finished))

    // --- The combinations -----------------------------------------------------------------------

    /** One combination of the sweep: the theme, the font scale and the phone's width. */
    private data class Combo(val dark: Boolean, val fontScale: Float, val width: Dp) {
        override fun toString() = "${if (dark) "dark" else "light"} ${fontScale}x ${width.value.toInt()}dp"
    }

    private val combos: List<Combo> = listOf(false, true).flatMap { dark ->
        SWEEP_FONT_SCALES.flatMap { scale -> listOf(320.dp, 412.dp).map { Combo(dark, scale, it) } }
    }

    /** A phone of [width]: 640 dp tall at 320, 800 at 412. */
    private fun sizeOf(width: Dp) = DpSize(width, if (width < 400.dp) 640.dp else 800.dp)

    /**
     * Sets [content] once, in the frame of the current [Combo] (light, 1.0, 412 dp first), lets
     * [reach] bring it to the state, then checks each combination (see the class comment). With
     * [frozen], the clock is stopped from [reach] on (for the record toast, which holds for
     * `timing.prToast` on the effects' clock): each combination advances it a few frames only.
     */
    private fun ComposeUiTest.sweep(
        state: String,
        workoutActions: Boolean = true,
        numbers: Int = 1,
        frozen: Boolean = false,
        scaled: String? = null,
        passes: List<Pass> = listOf(Pass("as shown")),
        reach: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        var combo by mutableStateOf(Combo(dark = false, fontScale = 1f, width = 412.dp))
        setContent {
            ScreenFrame(dark = combo.dark, fontScale = combo.fontScale, locale = "pt-BR", size = sizeOf(combo.width), content = content)
        }
        waitForIdle()
        // A lazy list scrolled with the clock stopped never lays out what it scrolls to (and the
        // scroll keeps looking): a frozen state is checked as shown.
        require(!frozen || passes.all { it.scroll == null }) { "$state: no scrolling with a frozen clock" }
        if (frozen) mainClock.autoAdvance = false
        try {
            reach()
            val failures = mutableListOf<String>()
            var buttonTexts = 0
            for (each in combos) {
                combo = each
                for (pass in passes) {
                    pass.scroll?.invoke(this)
                    if (frozen) repeat(FROZEN_FRAMES) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    val where = "$state, $each, ${pass.name}"
                    try {
                        // What this pass is for must be there: a check of nothing passes in the void.
                        for (tag in pass.required) {
                            assertTrue(onAllNodes(hasTestTag(tag) and isPlaced, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "$where: no $tag shown")
                        }
                        assertClickablesLabeledAndSized(where)
                        if (workoutActions) assertWorkoutTargets(where)
                        assertNumbersWhole(atLeast = numbers)
                        buttonTexts += assertButtonTextsWhole(where)
                        if (scaled != null) assertDrawnAt(scaled, each.fontScale, where)
                    } catch (failure: AssertionError) {
                        failures += "$where: ${failure.message}"
                    }
                }
            }
            println("SWEEP $state: ${combos.size} combinations x ${passes.size} passes, $buttonTexts button texts measured, ${failures.size} failing")
            assertTrue(failures.isEmpty(), "${failures.size} of ${combos.size} combinations fail:\n" + failures.joinToString("\n"))
        } finally {
            mainClock.autoAdvance = true
        }
    }

    /**
     * One look at a state: [scroll] brings part of it into view (null: as it is), and the tags in
     * [required] must then be there.
     */
    private class Pass(val name: String, val required: List<String> = emptyList(), val scroll: (ComposeUiTest.() -> Unit)? = null)

    /**
     * The workout list in three looks, each scrolled to: its top (or [focus], required), the
     * bench's footer ("Add set") and the list's end ("Add exercise", "Finish workout"), so the
     * 56 dp and text checks of the controls at the end of the list never skip them for not being composed.
     */
    private fun listPasses(focus: String? = null) = listOf(
        Pass("list top", listOfNotNull(focus)) {
            if (focus == null) onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToIndex(0)
            else onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(focus))
        },
        Pass("bench footer", listOf(ExerciseAddSetTag)) {
            onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseFooterTag("e1")))
        },
        Pass("list end", listOf(ActiveWorkoutTags.ADD_EXERCISE, ActiveWorkoutTags.FINISH_WORKOUT)) {
            onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.END))
        },
    )

    /** The workout route over [fake], its timer at [timer]. */
    @Composable
    private fun WorkoutRoute(fake: Fake, timer: TimerUi = TimerUi()) {
        val timerState = androidx.compose.runtime.remember { mutableStateOf(timer) }
        ActiveWorkoutRoute(
            state = fake.state.value,
            timer = timerState,
            actions = fake,
            onMinimize = {},
            onOpenSummary = {},
            onExerciseHistory = {},
            onChooseRoutine = {},
            imeInsets = WindowInsets(0, 0, 0, 0),
            nowMillis = { NOW },
        )
    }

    /** The shell's bottom over [state], at the bottom of the phone, Train selected. */
    @Composable
    private fun ShellBottom(state: UiState, timer: TimerUi = TimerUi()) {
        val timerState = androidx.compose.runtime.remember { mutableStateOf(timer) }
        Box(Modifier.fillMaxSize()) {
            AppShellBottom(
                state = state,
                timer = timerState,
                selectedTab = ShellTabs.TRAIN,
                onSelectTab = {},
                onReselectTab = {},
                onOpenWorkout = {},
                actions = RecordingActions(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                imeInsets = WindowInsets(0, 0, 0, 0),
                nowMillis = { NOW },
            )
        }
    }

    private fun ComposeUiTest.loadField(setId: String) =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and hasAnyAncestor(hasTestTag(workoutSetRowTag(setId))), useUnmergedTree = true)

    private fun ComposeUiTest.exists(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * The text of [tag] is laid out at [fontScale]: the combination reaches it (the expanded timer
     * is a dialog, a window of its own, which must take the screen's density).
     */
    private fun ComposeUiTest.assertDrawnAt(tag: String, fontScale: Float, where: String) {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "$where: $tag has no text layout")
        val drawn = results.first().layoutInput.density.fontScale
        assertTrue(abs(drawn - fontScale) < 0.001f, "$where: $tag is laid out at ${drawn}x")
    }

    /** Checks set 2 and steps the stopped clock until its record toast is in. */
    private fun ComposeUiTest.bringTheRecordToast() {
        onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag(workoutSetRowTag("s2")))).performClick()
        var frames = 0
        while (!exists(RecordToastTag) && frames < RECORD_FRAMES) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            frames++
        }
        onNodeWithTag(RecordToastTag).assertIsDisplayed()
    }

    // --- The states -----------------------------------------------------------------------------

    @Test
    fun pendingSets() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("pending", passes = listPasses()) { WorkoutRoute(fake) }
    }

    @Test
    fun setInEdit() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("editing", passes = listPasses(focus = StepperIncreaseTag), reach = {
            loadField("s2").performClick()
            waitForIdle()
            onNodeWithTag(StepperIncreaseTag).assertIsDisplayed()
        }) { WorkoutRoute(fake) }
    }

    /** Checked as shown, the clock stopped (the toast's hold): the list's end is the pending state's. */
    @Test
    fun recordToast() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("record", frozen = true, passes = listOf(Pass("as shown", listOf(RecordToastTag))), reach = { bringTheRecordToast() }) { WorkoutRoute(fake) }
        assertTrue(fake.recording.calls.any { it.startsWith("updateSet:s2,8,82.5,true") }, fake.recording.calls.toString())
    }

    @Test
    fun restDocked() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("dock", passes = listPasses(), reach = { assertTrue(exists(TimerDockTag)) }) { WorkoutRoute(fake, resting) }
    }

    /** The real dialog over the list, its title drawn at each combination's scale ([assertDrawnAt]). */
    @Test
    fun restExpanded() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("expanded", scaled = TimerTitleTag, passes = listPasses(), reach = {
            onNodeWithTag(TimerOpenTag).performClick()
            waitUntil("the expanded timer opens", timeoutMillis = WAIT_MS) { exists(TimerExpandedTag) }
            waitForIdle()
        }) { WorkoutRoute(fake, resting) }
    }

    /**
     * The expanded timer's content at every combination. [restExpanded] sweeps the real dialog,
     * at the frame's scales but at the screen's width (a dialog is a window of its own, as wide as
     * the screen). So the content the dialog shows ([RestTimerExpanded], with the screen's own title
     * and "Up next" for this rest) is swept here in the frame, at the phone's width less the dialog's sides.
     */
    @Test
    fun restExpandedContent() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("expanded content", scaled = TimerTitleTag) {
            val next = RestUpNext.of(workout, resting.workoutExerciseId)
            Box(Modifier.fillMaxSize().background(MTheme.colors.bgBase).padding(horizontal = MTheme.space.s4), contentAlignment = Alignment.TopCenter) {
                RestTimerExpanded(
                    visual = RestTimerVisual(resting.remainingSeconds, resting.durationSeconds, TimerPhase.Running,
                        stringResource(Res.string.workout_timer_title_short, bench.name)),
                    upNext = next?.let { upNextText(it, pounds = false) },
                    onMinus15 = {},
                    onPlus15 = {},
                    onSkip = {},
                    title = stringResource(Res.string.workout_timer_title, bench.name, 1, 3),
                    totalText = MoosclesNumbers.clock(resting.durationSeconds),
                    onPauseResume = {},
                    onRestart = {},
                    onClose = {},
                    modifier = Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun restPaused() = runComposeUiTest(testTimeout = 5.minutes) {
        val fake = Fake(stateOf(workout))
        sweep("paused", passes = listPasses(), reach = { assertTrue(exists(TimerDockTag)) }) { WorkoutRoute(fake, paused) }
    }

    @Test
    fun summary() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("summary", workoutActions = false) {
            WorkoutSummaryScreen(finished, listOf(finished), pounds = false, onDone = {}, onShare = {}, onDetails = {},
                nowMillis = NOW, zone = TimeZone.UTC)
        }
    }

    @Test
    fun tabBar() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("tab bar", workoutActions = false, numbers = 0) { ShellBottom(stateOf(null)) }
    }

    @Test
    fun workoutBar() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("workout bar") { ShellBottom(stateOf(workout), resting) }
    }

    // --- The screen reader ----------------------------------------------------------------------

    /** The first tag among [tags] on [node] or above it, in the merged tree. */
    private fun regionOf(node: SemanticsNode, tags: Set<String>): String? {
        var current: SemanticsNode? = node
        while (current != null) {
            current.config.getOrNull(SemanticsProperties.TestTag)?.let { if (it in tags) return it }
            current = current.parent
        }
        return null
    }

    /**
     * The order a screen reader reads the workout in ([screenReaderOrder]), with the record toast
     * up and the rest docked: Minimize, the title, the time and the sets, Finish, More options (the
     * header), the record toast, the cards in order (the bench's header, its rows and its "Add
     * set", then the row's and the press's headers), the list's end ("Add exercise", "Finish
     * workout"), and the dock last.
     */
    @Test
    fun readingOrderIsHeaderToastCardsDock() = runComposeUiTest(testTimeout = 2.minutes) {
        val fake = Fake(stateOf(workout))
        // Tall enough for the three cards (the list composes only what its viewport shows), from the
        // window's top: a taller frame is centered and would put the header and the toast above a
        // smaller window (the iOS simulator's), where the toast is not displayed.
        setContent {
            ScreenFrame(locale = "pt-BR") {
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(412.dp, 1200.dp)) { WorkoutRoute(fake, resting) }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        try {
            bringTheRecordToast()
            val header = listOf(
                WorkoutHeaderTags.MINIMIZE, WorkoutHeaderTags.TITLE, WorkoutClockTag, WorkoutHeaderTags.SETS,
                WorkoutHeaderTags.FINISH, WorkoutHeaderTags.MORE,
            )
            val cards = listOf(
                exerciseHeaderTag("e1"), workoutSetRowTag("s1"), workoutSetRowTag("s2"), workoutSetRowTag("s3"), exerciseFooterTag("e1"),
                exerciseHeaderTag("e2"), exerciseHeaderTag("e3"), ActiveWorkoutTags.ADD_EXERCISE, ActiveWorkoutTags.FINISH_WORKOUT,
            )
            val expected = header + RecordToastTag + cards + TimerDockTag
            val order = screenReaderOrder()
            val regions = order.mapNotNull { regionOf(it, expected.toSet()) }
            // Each region once, in a row: its nodes are read together.
            val sequence = regions.fold(mutableListOf<String>()) { acc, region -> acc.apply { if (lastOrNull() != region) add(region) } }
            val read = order.joinToString("\n") { "${regionOf(it, expected.toSet())} '${spokenLabel(it)}' ${it.boundsInRoot}" }
            assertEquals(expected, sequence, "the screen reader's order:\n$read\n")
            // Nothing after the dock either, of what this test does not name.
            assertEquals(TimerDockTag, regionOf(order.last(), setOf(TimerDockTag)), "the dock is not read last: ${order.last().config}")
        } finally {
            mainClock.autoAdvance = true
        }
    }

    /**
     * The dock (and, at large text, the compact timer) and the workout bar are traversal groups,
     * so a screen reader reads each whole, body before its buttons, wherever the layout puts them.
     */
    @Test
    fun dockAndWorkoutBarAreTraversalGroups() = runComposeUiTest(testTimeout = 2.minutes) {
        val fake = Fake(stateOf(workout))
        var part by mutableStateOf(0)
        var fontScale by mutableStateOf(1f)
        setContent {
            ScreenFrame(locale = "pt-BR", fontScale = fontScale, size = sizeOf(412.dp)) {
                if (part == 0) WorkoutRoute(fake, resting) else ShellBottom(stateOf(workout), resting)
            }
        }
        waitForIdle()
        assertOneGroup(TimerDockTag, listOf(TimerOpenTag, TimerMinusTag, TimerPlusTag, TimerSkipTag))
        fontScale = 2f
        waitForIdle()
        assertOneGroup(TimerCompactTag, listOf(TimerOpenTag, TimerSkipTag))
        fontScale = 1f
        part = 1
        waitForIdle()
        assertOneGroup(ShellWorkoutBarTag, listOf(WorkoutBarTag, WorkoutBarSkipTag))
    }

    /** Every one of [members] (unmerged) sits in the same traversal group, inside [container]. */
    private fun ComposeUiTest.assertOneGroup(container: String, members: List<String>) {
        val box = onNodeWithTag(container, useUnmergedTree = true).fetchSemanticsNode()
        val groups = members.map { tag ->
            val node = onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true).fetchSemanticsNode()
            assertNotNull(traversalGroupWithin(node, box), "$tag is in no traversal group inside $container").id
        }
        assertEquals(1, groups.toSet().size, "$container: its parts are in different traversal groups: ${members.zip(groups)}")
    }

    private companion object {
        /** 2024-10-29 18:02 UTC. */
        const val START = 1_730_224_920_000L
        const val NOW = START + (42 * 60 + 18) * 1_000L
        const val WEEK_MS = 7 * 24 * 3_600_000L
        const val WAIT_MS = 10_000L

        /** At most this many frames for the record toast to come in (a second of the effects' clock). */
        const val RECORD_FRAMES = 60

        /** The frames each combination advances the stopped clock: 48 ms, 16 combinations well within the toast's 4 s. */
        const val FROZEN_FRAMES = 3
    }
}
