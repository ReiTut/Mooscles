package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.currentTimeMillis
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState

/**
 * The `workout` route: the active workout ([ActiveWorkoutScreen]) when there is one; while it
 * opens ([UiState.startingWorkout], or the state still loading), [skeletonCards] placeholders
 * shaped like the cards ([WorkoutSkeleton]); otherwise "No workout in progress" ([NoWorkout]),
 * with "Choose routine" ([onChooseRoutine]) and "Empty workout" (`startWorkout(null)`).
 *
 * The screen's own state ([screen]) and its toast host live here, so they outlast the switch from
 * the placeholders to the workout. An app whose navigation drops the route when the workout is
 * minimized passes [screen] from above it, so the state lasts the session (cleared fields,
 * drafts, cards opened or closed by hand, the pending undo). Around everything, a tap outside
 * the row in edit ends the edit ([endsSetEditingOnOutsideTap]). The undo toast of a removal
 * ([UndoRemovalToast]) and the error toast ([WorkoutErrorToast]: the localized [UiState.errorCode],
 * never [UiState.error]) share that host.
 *
 * Confirming "Finish" keeps the workout's id ([rememberSaveable], so it survives recreation) and
 * calls `finishWorkout`; once the workout is in [UiState.history], [onOpenSummary] gets its id.
 * Meanwhile the finish buttons show their loading state. An error while the workout is still the
 * active one gives them back; the id is kept all the same, since the error may come from after
 * the finish committed (clearing the rest timer, say) ahead of the emission that ends the
 * workout, and the summary still opens when the history brings it. [timer] is not
 * read here: only the screen's rest timer reads it ([WorkoutTimerLayer]); the toasts sit above
 * it ([WorkoutToastHost]). [imeInsets] is the keyboard, which the list, the timer and the toasts
 * keep clear of; [nowMillis] is the clock the header's time reads. A card menu's "About the
 * exercise" calls [onExerciseInfo] with the catalog exercise's id (the app opens its detail).
 */
@Composable
fun ActiveWorkoutRoute(
    state: UiState,
    timer: State<TimerUi>,
    actions: MoosclesActions,
    onMinimize: () -> Unit,
    onOpenSummary: (workoutId: String) -> Unit,
    onExerciseHistory: (exerciseId: String) -> Unit,
    onChooseRoutine: () -> Unit,
    modifier: Modifier = Modifier,
    onExerciseInfo: (exerciseId: String) -> Unit = {},
    skeletonCards: Int = 1,
    imeInsets: WindowInsets = WindowInsets.ime,
    nowMillis: () -> Long = { currentTimeMillis() },
    screen: ActiveWorkoutScreenState = rememberActiveWorkoutScreenState(),
) {
    val host = remember { MToastHostState() }
    // The rest timer's height, from the screen, for the toasts. Read only by WorkoutToastHost.
    val timerHeight = remember { mutableIntStateOf(0) }
    var finishingId by rememberSaveable { mutableStateOf<String?>(null) }
    // The finish whose buttons an error gave back: the workout was still active when it came.
    var finishRefused by rememberSaveable { mutableStateOf<String?>(null) }
    var summaryOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val openSummary by rememberUpdatedState(onOpenSummary)
    val active = state.active

    LaunchedEffect(state.history, finishingId) {
        val id = finishingId ?: return@LaunchedEffect
        // Once: the route stays blank while the app moves to the summary.
        if (summaryOpened != id && state.history.any { it.id == id }) {
            summaryOpened = id
            openSummary(id)
        }
    }
    // Another workout in progress (started after this one finished) ends the wait.
    LaunchedEffect(active?.id) {
        if (active != null && finishingId != null && active.id != finishingId) finishingId = null
    }
    WorkoutErrorToast(state.errorCode, screen, host, actions, onError = {
        // Only a workout still active can have failed to finish; one already gone finished.
        if (finishingId != null && active?.id == finishingId) finishRefused = finishingId
    })

    Box(modifier.fillMaxSize().testTag(ActiveWorkoutTags.ROUTE).background(MTheme.colors.bgBase).endsSetEditingOnOutsideTap(screen)) {
        when {
            active != null -> ActiveWorkoutScreen(
                workout = active,
                catalog = state.exercises,
                history = state.history,
                pounds = state.preferences.pounds,
                loaded = !state.loading,
                discarding = state.discardingWorkout,
                screen = screen,
                host = host,
                actions = actions,
                finishing = finishingId == active.id && finishRefused != active.id,
                onConfirmFinish = {
                    finishRefused = null
                    finishingId = active.id
                    actions.finishWorkout()
                },
                onMinimize = onMinimize,
                onExerciseHistory = onExerciseHistory,
                onExerciseInfo = onExerciseInfo,
                imeInsets = imeInsets,
                nowMillis = nowMillis,
                timer = timer,
                onTimerHeight = { timerHeight.intValue = it },
            )
            // Finished: blank once the history has the workout (the summary opens) and while the app
            // moves there, so "No workout in progress" never flashes. A workout that left without
            // reaching the history falls through to the empty state, so the screen is never a dead end.
            finishingId != null && (summaryOpened == finishingId || state.history.any { it.id == finishingId }) -> Unit
            state.loading || state.startingWorkout -> WorkoutSkeleton(skeletonCards, onMinimize)
            else -> NoWorkout(onChooseRoutine, onEmptyWorkout = { actions.startWorkout(null) })
        }
        WorkoutToastHost(host, timerHeight, imeInsets)
    }
    UndoRemovalToast(screen, host, actions)
}

/**
 * The route's toasts, above the keyboard and above the rest timer ([timerHeight], in pixels, 0
 * without one), so the undo toast never covers −15 s, +15 s or skip (review M4). It reads the
 * height alone, so a change of it recomposes only this host.
 */
@Composable
private fun WorkoutToastHost(host: MToastHostState, timerHeight: IntState, imeInsets: WindowInsets) {
    val above = with(LocalDensity.current) { timerHeight.intValue.toDp() }
    MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(imeInsets.only(WindowInsetsSides.Bottom)).padding(bottom = above))
}
