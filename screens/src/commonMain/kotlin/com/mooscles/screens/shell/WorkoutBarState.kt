package com.mooscles.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mooscles.designsystem.components.ActiveWorkoutBarState
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.components.spokenDuration
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.ds_sets_done_of
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.Durations
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.shell_elapsed_under_minute
import com.mooscles.screens.resources.shell_rest_done_spoken
import com.mooscles.screens.resources.shell_rest_paused_spoken
import com.mooscles.ui.TimerUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

/** Whole seconds from [startedAt] to [now]; zero when the clock is behind the start. */
internal fun elapsedSeconds(startedAt: Long, now: Long): Long = (now - startedAt).coerceAtLeast(0L) / MS_PER_SECOND

/**
 * The phase of a rest that is on for [timer]: running or paused. A finished timer has none here;
 * the bar shows its end only for a while after it saw the rest end ([rememberBarRestPhase]).
 */
internal fun liveRestPhase(timer: TimerUi): TimerPhase? = when {
    timer.finished -> null
    timer.paused -> TimerPhase.Paused
    timer.running -> TimerPhase.Running
    else -> null
}

/** What the last composition of the bar saw: whether the timer was running. Not state. */
private class RunningSeen {
    var running = false
}

/**
 * The phase of the rest the bar shows for [timer] (plan decision 21): running or paused while the
 * rest is on; Done for `timing.timerDoneHold` after this composition saw the timer go from
 * running to finished, then nothing, so the sets come back. A timer already finished when the bar
 * is first composed (the app opening, a recreation, a return from the workout) shows no rest.
 * The workout screen's timer (`WorkoutTimerLayer`) follows the same rule through this function.
 */
@Composable
internal fun rememberBarRestPhase(timer: TimerUi): TimerPhase? {
    val seen = remember { RunningSeen() }
    // Decided when `finished` changes, from what the previous composition saw, before this one records it.
    val sawEnd = remember(timer.finished) { timer.finished && seen.running }
    var holdOver by remember(sawEnd) { mutableStateOf(false) }
    val hold = MTheme.timing.timerDoneHold
    LaunchedEffect(sawEnd) {
        if (sawEnd) {
            delay(hold)
            holdOver = true
        }
    }
    SideEffect { seen.running = timer.running }
    return if (sawEnd && !holdOver) TimerPhase.Done else liveRestPhase(timer)
}

/**
 * The seconds of the workout started at [startedAt]: read from [nowMillis] when first composed,
 * then once a second, on the second the clock turns. Only the composable that reads the value
 * recomposes when it changes.
 */
@Composable
internal fun rememberElapsedSeconds(startedAt: Long, nowMillis: () -> Long): State<Long> {
    val clock = rememberUpdatedState(nowMillis)
    return produceState(elapsedSeconds(startedAt, nowMillis()), startedAt) {
        while (true) {
            val millis = (clock.value() - startedAt).coerceAtLeast(0L)
            value = millis / MS_PER_SECOND
            delay(MS_PER_SECOND - millis % MS_PER_SECOND)
        }
    }
}

/**
 * What the in-progress bar shows for [workout] after [elapsedSeconds] with [timer], localized:
 * the elapsed clock ("42:18", "1:02:18") read in whole minutes ("42 minutes", "1 hour and
 * 2 minutes"; "less than a minute" before the first), the sets done of all ("9 of 15 sets") and,
 * when [phase] is not null ([rememberBarRestPhase]), the rest and its reading: the time left,
 * with ", paused" while paused, or "complete" at the end.
 */
@Composable
internal fun workoutBarState(workout: WorkoutUi, elapsedSeconds: Long, timer: TimerUi, phase: TimerPhase?): ActiveWorkoutBarState {
    val sets = remember(workout.exercises) { workout.completedSets to workout.exercises.sumOf { it.sets.size } }
    val minutes = (elapsedSeconds / SECONDS_PER_MINUTE).toInt()
    val elapsedSpoken = if (minutes == 0) {
        stringResource(Res.string.shell_elapsed_under_minute)
    } else {
        spokenDuration(minutes * SECONDS_PER_MINUTE)
    }
    val restLabel = stringResource(DsRes.string.ds_timer_rest)
    val remaining = if (phase == TimerPhase.Done) 0 else timer.remainingSeconds.coerceAtLeast(0)
    val restSpoken = when (phase) {
        null -> null
        TimerPhase.Done -> stringResource(Res.string.shell_rest_done_spoken)
        TimerPhase.Paused -> stringResource(Res.string.shell_rest_paused_spoken, spokenDuration(remaining))
        TimerPhase.Running -> spokenDuration(remaining)
    }
    return ActiveWorkoutBarState(
        workoutName = workout.name,
        elapsedText = Durations.clock(elapsedSeconds),
        elapsedSpoken = elapsedSpoken,
        setsText = pluralCount(DsRes.plurals.ds_sets_done_of, sets.second, sets.first, sets.second),
        rest = phase?.let { RestTimerVisual(remaining, timer.durationSeconds, it, restLabel) },
        restSpoken = restSpoken,
    )
}
