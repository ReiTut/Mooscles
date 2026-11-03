package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.error_blocked_by_update
import com.mooscles.screens.resources.error_exercise_gone
import com.mooscles.screens.resources.error_invalid_value
import com.mooscles.screens.resources.error_local_data_unavailable
import com.mooscles.screens.resources.error_needs_completed_set
import com.mooscles.screens.resources.error_save_failed
import com.mooscles.screens.resources.error_set_gone
import com.mooscles.screens.resources.error_set_limit
import com.mooscles.screens.resources.error_set_not_restored
import com.mooscles.screens.resources.error_timer_storage
import com.mooscles.screens.resources.error_workout_gone
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiError
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What the new screens say for [error], in the app's language. The screens never show
 * `UiState.error`, which is the raw English message; a failure without a code of its own
 * ([UiError.GENERIC]) reads like a failed save.
 */
@Composable
internal fun uiErrorText(error: UiError): String = stringResource(
    when (error) {
        UiError.SAVE_FAILED, UiError.GENERIC -> Res.string.error_save_failed
        UiError.TIMER_STORAGE -> Res.string.error_timer_storage
        UiError.LOCAL_DATA_UNAVAILABLE -> Res.string.error_local_data_unavailable
        UiError.BLOCKED_BY_UPDATE -> Res.string.error_blocked_by_update
        UiError.SET_GONE -> Res.string.error_set_gone
        UiError.EXERCISE_GONE -> Res.string.error_exercise_gone
        UiError.WORKOUT_GONE -> Res.string.error_workout_gone
        UiError.SET_LIMIT -> Res.string.error_set_limit
        UiError.INVALID_VALUE -> Res.string.error_invalid_value
        UiError.NEEDS_COMPLETED_SET -> Res.string.error_needs_completed_set
        UiError.SET_NOT_RESTORED -> Res.string.error_set_not_restored
    },
)

/**
 * Shows [errorCode] as an error toast on [host] and hands it back to the view model: put on the
 * screen once. When a code arrives, the screen cannot tell which write failed, so first every
 * optimistic done state and every awaited write is dropped ([ActiveWorkoutScreenState.dropAllOptimistic]):
 * a failed write sends no echo, and the rows show the database again. Then [onError] runs (the
 * screen gives the finish button back) and `clearError()` is called; the toast is shown in a
 * scope of its own, since clearing the code ends this effect. An error toast stays until it is
 * closed, and the host shows one toast at a time, so an undo toast that follows waits for it.
 */
@Composable
internal fun WorkoutErrorToast(
    errorCode: UiError?,
    screen: ActiveWorkoutScreenState,
    host: MToastHostState,
    actions: MoosclesActions,
    onError: () -> Unit,
) {
    val text = errorCode?.let { uiErrorText(it) }
    val scope = rememberCoroutineScope()
    val latestOnError by rememberUpdatedState(onError)
    LaunchedEffect(errorCode) {
        val message = text ?: return@LaunchedEffect
        screen.dropAllOptimistic()
        latestOnError()
        actions.clearError()
        scope.launch { host.show(message, MToastKind.Error) }
    }
}

/**
 * For a screen that has no toast host of its own (the summary): shows [errorCode] in the app's
 * language as an error toast over [modifier]'s area, and hands it back with `clearError()`, as the
 * workout does (plan decision 19). Never `UiState.error`.
 */
@Composable
fun ScreenErrorToast(errorCode: UiError?, actions: MoosclesActions, modifier: Modifier = Modifier) {
    val host = remember { MToastHostState() }
    ScreenErrorToast(errorCode, actions, host)
    MToastHost(host, modifier)
}

/**
 * [ScreenErrorToast] on a [host] the screen draws itself, for a screen that shows toasts of its own
 * too (the exercise detail's "Added to …"): the host shows one toast at a time.
 */
@Composable
internal fun ScreenErrorToast(errorCode: UiError?, actions: MoosclesActions, host: MToastHostState) {
    val text = errorCode?.let { uiErrorText(it) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(errorCode) {
        val message = text ?: return@LaunchedEffect
        actions.clearError()
        scope.launch { host.show(message, MToastKind.Error) }
    }
}
