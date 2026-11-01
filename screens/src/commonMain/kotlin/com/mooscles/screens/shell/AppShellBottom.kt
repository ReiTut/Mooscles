package com.mooscles.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import com.mooscles.designsystem.components.ActiveWorkoutBar
import com.mooscles.designsystem.components.MTabBar
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.workout.RecompositionProbe
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi

/** The whole bottom of the shell: the workout bar (when there is one) and the tab bar, with the keyboard reserved. */
const val AppFooterTag = "app-footer"

/** The in-progress workout bar inside the shell, as placed above the tabs. */
const val ShellWorkoutBarTag = "shell-workout-bar"

/**
 * The bottom of the app shell: with a workout in progress ([UiState.active]), the
 * [ActiveWorkoutBar] (spec D8) sits on the [MTabBar] of [shellTabs] (D2, D7).
 *
 * Tapping the bar calls [onOpenWorkout] and its skip button `skipTimer`. The tabs call
 * [onSelectTab] or, on the selected one, [onReselectTab]; they are off while [enabled] is false
 * or the state is still loading.
 *
 * The keyboard ([imeInsets]) is reserved once, as the old `WorkoutBottomBar` did: the shell is the
 * bar plus the taller of the tabs and the keyboard, the tabs stay at the bottom behind the
 * keyboard, and the bar rides on top of it. Content padded by the shell's height needs no IME
 * padding of its own.
 *
 * Only the bar reads [timer] and the clock ([nowMillis], once a second), so a timer tick or a
 * second recomposes the bar and never the tabs.
 */
@Composable
fun AppShellBottom(
    state: UiState,
    timer: State<TimerUi>,
    selectedTab: String?,
    onSelectTab: (String) -> Unit,
    onReselectTab: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    actions: MoosclesActions,
    modifier: Modifier = Modifier,
    imeInsets: WindowInsets = WindowInsets.ime,
    enabled: Boolean = true,
    nowMillis: () -> Long = { currentTimeMillis() },
) {
    val active = state.active
    val tabsEnabled = enabled && !state.loading
    Layout(
        modifier = modifier.fillMaxWidth().testTag(AppFooterTag),
        content = {
            Box {
                if (active != null) {
                    ShellWorkoutBar(active, timer, nowMillis, onOpenWorkout, onSkipRest = { actions.skipTimer() })
                }
            }
            Box { ShellTabBar(selectedTab, onSelectTab, onReselectTab, tabsEnabled) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val bar = measurables[0].measure(loose)
        val tabs = measurables[1].measure(loose)
        // Read here, in the layout: the keyboard moving re-places the shell without recomposing it.
        val bottom = maxOf(tabs.height, imeInsets.getBottom(this))
        val height = constraints.constrainHeight(bar.height + bottom)
        layout(constraints.maxWidth, height) {
            bar.placeRelative(0, 0)
            tabs.placeRelative(0, height - tabs.height)
        }
    }
}

/** The bar of [workout]: the only reader of [timer] and of the clock in the shell. */
@Composable
private fun ShellWorkoutBar(
    workout: WorkoutUi,
    timer: State<TimerUi>,
    nowMillis: () -> Long,
    onOpen: () -> Unit,
    onSkipRest: () -> Unit,
) {
    RecompositionProbe("bar")
    val elapsed by rememberElapsedSeconds(workout.startedAt, nowMillis)
    ActiveWorkoutBar(
        state = workoutBarState(workout, elapsed, timer.value, rememberBarRestPhase(timer.value)),
        onOpen = onOpen,
        onSkipRest = onSkipRest,
        modifier = Modifier.testTag(ShellWorkoutBarTag),
    )
}

/** The tab bar, in a scope of its own that reads nothing of the timer. */
@Composable
private fun ShellTabBar(
    selectedTab: String?,
    onSelect: (String) -> Unit,
    onReselect: (String) -> Unit,
    enabled: Boolean,
) {
    RecompositionProbe("tabs")
    MTabBar(shellTabs(), selectedTab, onSelect, onReselect, enabled = enabled)
}
