package com.mooscles.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MOverlayWindow
import com.mooscles.designsystem.components.RestTimerCompact
import com.mooscles.designsystem.components.RestTimerDock
import com.mooscles.designsystem.components.RestTimerExpanded
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.ds_reps_count
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.RestDuration
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_timer_title
import com.mooscles.screens.resources.workout_timer_title_short
import com.mooscles.screens.resources.workout_up_next_reps
import com.mooscles.screens.resources.workout_up_next_set
import com.mooscles.screens.resources.workout_up_next_value
import com.mooscles.screens.shell.rememberBarRestPhase
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.TimerUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/** The test tags of the rest timer on the workout screen. */
internal object WorkoutTimerTags {
    /** The docked or compact timer with its margins: absent, and 0 tall, while there is no rest. */
    const val LAYER = "workout-timer-layer"
}

/** From this font scale on, the compact timer stands in for the docked one (spec D15). */
private const val COMPACT_FONT_SCALE = 1.5f

/** What −15 s and +15 s move the rest by. */
private const val ADJUST_SECONDS = 15

/**
 * The exercise of [workout] whose rest [timer] counts; none for a rest of another workout, or one
 * without an entry (a rest the rest sheet started before it passed its entry, final review item 1).
 */
private fun timerEntryOf(workout: WorkoutUi, timer: TimerUi): WorkoutExerciseUi? =
    timer.workoutExerciseId?.let { id -> workout.exercises.firstOrNull { it.id == id } }

/** The last rest the layer drew, kept for the exit animation, when the timer already has none. Not state. */
private class LastRest {
    var visual: RestTimerVisual? = null
}

/**
 * The rest timer of the active workout (spec "Dock, compacto e expandido"), and the only place of
 * the screen that reads [timer]'s value: a tick recomposes this layer, never a card or a row.
 *
 * - The dock ([RestTimerDock]) is there only while the rest runs or is paused, and for
 *   `timing.timerDoneHold` after this layer saw it end (plan decision 21, the same rule as the
 *   workout bar: [rememberBarRestPhase]); a rest already over when the screen opens shows nothing.
 *   Without a rest it takes no room. It enters from below with `motion.base` and leaves with
 *   `motion.fast` (a fade of `motion.fast` with reduced motion).
 * - With the keyboard open ([imeInsets]) or from a font scale of 1.5, the compact timer
 *   ([RestTimerCompact], D15) stands in for it: ring, time and skip, with no ±15 s. With the
 *   keyboard it sits right on it: the layer reserves the keyboard's height ([imeInsets]) under
 *   the timer, with or without a rest, so the list above it ends above both.
 * - Tapping the body opens the expanded timer ([ExpandedRest], decision 23), whose opening is kept
 *   with the workout's id ([rememberSaveable]); the dock leaves while it is open, so one timer
 *   speaks and gives the end's haptic once. Closing it ("Close timer" or back) gives the focus
 *   back to the dock's body. It closes on its own once there is no rest left (after the done
 *   hold, a skip, the end of the session). After the done hold it hands back to the next set
 *   ([onRestOver], with the set [RestUpNext] finds): the screen brings that set into view.
 * - +15 s stops at the 5:00 ceiling (`canAdd`).
 *
 * The screen lays it under the list, not over it, so an edited row is brought into view above the
 * timer and the keyboard ([pounds] is the up next load's unit). [onHeightChanged] gets the height
 * of the timer (0 without one, the keyboard's room left out), so the toasts can sit above it.
 */
@Composable
internal fun WorkoutTimerLayer(
    timer: State<TimerUi>,
    workout: WorkoutUi,
    pounds: Boolean,
    actions: MoosclesActions,
    imeInsets: WindowInsets,
    modifier: Modifier = Modifier,
    onRestOver: (setId: String) -> Unit = {},
    onHeightChanged: (Int) -> Unit = {},
) {
    val current = timer.value
    val phase = rememberBarRestPhase(current)
    var expanded by rememberSaveable(workout.id) { mutableStateOf(false) }
    val dockFocus = remember { FocusRequester() }
    var focusReturns by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Only the opening and closing of the keyboard, not every frame of its animation.
    val keyboard by remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val compact = keyboard || density.fontScale >= COMPACT_FONT_SCALE

    // By the timer's exercise of this workout (pre-flight F1): on an automatic rest the view model
    // stores the entry's snapshot name, which does not follow the screen's language (plan decision
    // 11), so the label shows the entry's name as the screen shows it.
    val timerEntry = remember(workout, current.workoutExerciseId) { timerEntryOf(workout, current) }
    val label = stringResource(
        Res.string.workout_timer_title_short,
        timerEntry?.let { exerciseName(it.exercise) } ?: current.exerciseName,
    )
    val visual = phase?.let {
        RestTimerVisual(if (it == TimerPhase.Done) 0 else current.remainingSeconds.coerceAtLeast(0), current.durationSeconds, it, label)
    }
    val last = remember { LastRest() }
    if (visual != null) last.visual = visual
    val shown = visual ?: last.visual
    val canAdd = current.remainingSeconds < RestDuration.MAX_SECONDS

    val restOver by rememberUpdatedState(onRestOver)
    val height by rememberUpdatedState(onHeightChanged)
    LaunchedEffect(visual == null) {
        if (visual != null) return@LaunchedEffect
        // Closed by the end of the done hold, not by a skip: back to the set that comes next.
        if (expanded && last.visual?.phase == TimerPhase.Done) {
            RestUpNext.of(workout, current.workoutExerciseId)?.let { restOver(it.setId) }
        }
        expanded = false
    }
    DisposableEffect(Unit) { onDispose { height(0) } }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0) return@LaunchedEffect
        // Once the dock is back in the layout.
        withFrameNanos { }
        dockFocus.requestFocus()
    }

    val motion = MTheme.motion
    val reduced = MTheme.reducedMotion
    val space = MTheme.space
    // The keyboard's room is reserved here, under the timer, also when there is no rest.
    Column(modifier.fillMaxWidth().windowInsetsPadding(imeInsets.only(WindowInsetsSides.Bottom))) {
        // Always there (0 tall without a rest), so the height it reports goes back to 0.
        Column(Modifier.fillMaxWidth().onSizeChanged { height(it.height) }) {
            AnimatedVisibility(
                visible = visual != null && !expanded,
                modifier = Modifier.fillMaxWidth().testTag(WorkoutTimerTags.LAYER),
                enter = if (reduced) fadeIn(tween(motion.fast)) else slideInVertically(tween(motion.base, easing = motion.easeOut)) { it },
                exit = if (reduced) fadeOut(tween(motion.fast)) else slideOutVertically(tween(motion.fast, easing = motion.easeOut)) { it },
            ) {
                if (shown != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = space.s4, end = space.s4, top = space.s2)
                            // On the keyboard, nothing between them; otherwise a margin from the screen's edge.
                            .then(if (keyboard) Modifier else Modifier.padding(bottom = space.s3)),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        DockedRest(
                            visual = shown,
                            compact = compact,
                            canAdd = canAdd,
                            actions = actions,
                            onOpen = { expanded = true },
                            // A request on the group goes to its first focusable child: the body.
                            modifier = Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().focusRequester(dockFocus).focusGroup(),
                        )
                    }
                }
            }
        }
    }
    if (expanded && visual != null) {
        ExpandedRest(
            visual = visual,
            timer = current,
            workout = workout,
            pounds = pounds,
            canAdd = canAdd,
            actions = actions,
            onClose = {
                expanded = false
                focusReturns++
            },
        )
    }
}

/** The docked timer, or the compact one: recomposes with each tick (`RecompositionProbe("dock")`). */
@Composable
private fun DockedRest(
    visual: RestTimerVisual,
    compact: Boolean,
    canAdd: Boolean,
    actions: MoosclesActions,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    RecompositionProbe("dock")
    if (compact) {
        RestTimerCompact(visual, onSkip = { actions.skipTimer() }, modifier = modifier, onOpen = onOpen)
    } else {
        RestTimerDock(
            visual,
            onMinus15 = { actions.adjustTimer(-ADJUST_SECONDS) },
            onPlus15 = { actions.adjustTimer(ADJUST_SECONDS) },
            onSkip = { actions.skipTimer() },
            modifier = modifier,
            onOpen = onOpen,
            canAdd = canAdd,
        )
    }
}

/**
 * The expanded timer (spec "Expandido"), a full-screen dialog over the workout (decision 23: it
 * holds the focus and closes with back on both platforms). It rises with `motion.slow` and
 * `ease.drawer`, or fades in with reduced motion. The title is "Rest · Bench press · set 4 of 5"
 * (the completed sets of the timer's exercise of all of them), or "Rest · Bench press" when the
 * rest has no exercise of this workout (started from the rest sheet); under "Up next", the set
 * [RestUpNext] finds ("set 5 · 85 × 6"), or no line.
 */
@Composable
private fun ExpandedRest(
    visual: RestTimerVisual,
    timer: TimerUi,
    workout: WorkoutUi,
    pounds: Boolean,
    canAdd: Boolean,
    actions: MoosclesActions,
    onClose: () -> Unit,
) {
    val entry = remember(workout, timer.workoutExerciseId) { timerEntryOf(workout, timer) }
    val done = entry?.sets?.count { it.completed } ?: 0
    // A rest started from the rest sheet before any set is done has no "set 0 of n": the name alone.
    val title = if (entry != null && done > 0) {
        stringResource(Res.string.workout_timer_title, exerciseName(entry.exercise), done, entry.sets.size)
    } else if (entry != null) {
        stringResource(Res.string.workout_timer_title_short, exerciseName(entry.exercise))
    } else {
        stringResource(Res.string.workout_timer_title_short, timer.exerciseName)
    }
    val next = remember(workout, timer.workoutExerciseId) { RestUpNext.of(workout, timer.workoutExerciseId) }
    val upNext = next?.let { upNextText(it, pounds) }

    val motion = MTheme.motion
    val reduced = MTheme.reducedMotion
    val close by rememberUpdatedState(onClose)
    val focus = remember { FocusRequester() }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shown.animateTo(1f, if (reduced) tween(motion.fast) else tween(motion.slow, easing = motion.easeDrawer))
    }
    // The design system's overlay window: the host's density and font scale reach the timer.
    MOverlayWindow(onDismissRequest = { close() }) {
        // Inside the Dialog's own composition, so the requester is attached before it is used.
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val closes = event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)
                    if (closes) close()
                    closes
                }
                .focusRequester(focus)
                .focusable()
                .graphicsLayer {
                    if (reduced) alpha = shown.value else translationY = (1f - shown.value) * size.height
                }
                .background(MTheme.colors.bgBase)
                .semantics { paneTitle = title }
                .padding(horizontal = MTheme.space.s4),
            contentAlignment = Alignment.TopCenter,
        ) {
            RestTimerExpanded(
                visual = visual,
                upNext = upNext,
                onMinus15 = { actions.adjustTimer(-ADJUST_SECONDS) },
                onPlus15 = { actions.adjustTimer(ADJUST_SECONDS) },
                onSkip = { actions.skipTimer() },
                title = title,
                totalText = MoosclesNumbers.clock(timer.durationSeconds),
                onPauseResume = { if (visual.phase == TimerPhase.Paused) actions.resumeTimer() else actions.pauseTimer() },
                onRestart = { actions.resetTimer() },
                onClose = { close() },
                modifier = Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxSize(),
                canAdd = canAdd,
            )
        }
    }
}

/** What [next] is, as "Up next" names it ([UpNext.parts]): "set 5 · 85 × 6", "set 2 · 12 reps" or "set 2". */
@Composable
internal fun upNextText(next: UpNext, pounds: Boolean): String = when (next.parts) {
    UpNextParts.LoadAndReps -> stringResource(Res.string.workout_up_next_value, next.setNumber, loadText(next.weightKg, pounds), next.reps)
    UpNextParts.Reps -> stringResource(Res.string.workout_up_next_reps, next.setNumber, pluralCount(DsRes.plurals.ds_reps_count, next.reps))
    UpNextParts.SetOnly -> stringResource(Res.string.workout_up_next_set, next.setNumber)
}
