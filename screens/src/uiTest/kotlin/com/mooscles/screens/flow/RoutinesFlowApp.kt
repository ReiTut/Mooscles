package com.mooscles.screens.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.routines.RoutineEditorRoute
import com.mooscles.screens.routines.TrainRoute
import com.mooscles.screens.workout.ActiveWorkoutRoute

/** The screens of [RoutinesFlowApp], as the strings its `screen` holds. */
internal object RoutinesScreens {
    const val LIBRARY = "library"
    const val TRAIN = "train"
    const val WORKOUT = "workout"
    fun info(exerciseId: String) = "info:$exerciseId"
    fun editor(routineId: String) = "editor:$routineId"
}

/**
 * The routines' path as `:app` hosts it (`MoosclesApp`), without its NavHost: the library, an exercise's detail, the
 * Train tab, the routine editor and the workout, switched by [screen] (a [RoutinesScreens] value) over the view
 * model's `contentUi`, with the same callbacks the app passes (a new routine opens its editor, "Start workout" opens
 * the workout, "About the exercise" its detail). Clear of the system bars and the keyboard, as the app's window is.
 */
@Composable
internal fun RoutinesFlowApp(viewModel: FlowViewModel, screen: MutableState<String>) {
    val state by viewModel.contentUi.collectAsState()
    val timer = viewModel.timerUi.collectAsState()
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val current = screen.value
        when {
            current == RoutinesScreens.LIBRARY -> LibraryRoute(
                state, viewModel,
                onOpenExercise = { screen.value = RoutinesScreens.info(it) },
                onOpenSettings = {},
            )
            current.startsWith("info:") -> ExerciseInfoRoute(
                exerciseId = current.removePrefix("info:"),
                state = state,
                actions = viewModel,
                onBack = { screen.value = RoutinesScreens.LIBRARY },
                onOpenHistory = {},
                onOpenRoutine = { screen.value = RoutinesScreens.editor(it) },
                onOpenWorkout = { screen.value = RoutinesScreens.WORKOUT },
            )
            current == RoutinesScreens.TRAIN -> TrainRoute(
                state, viewModel,
                onOpenRoutine = { screen.value = RoutinesScreens.editor(it) },
                onOpenWorkout = { screen.value = RoutinesScreens.WORKOUT },
                onOpenSettings = {},
            )
            current.startsWith("editor:") -> RoutineEditorRoute(
                routineId = current.removePrefix("editor:"),
                state = state,
                actions = viewModel,
                onBack = { screen.value = RoutinesScreens.TRAIN },
                onGone = { screen.value = RoutinesScreens.TRAIN },
                onOpenWorkout = { screen.value = RoutinesScreens.WORKOUT },
                onExerciseInfo = { screen.value = RoutinesScreens.info(it) },
                onOpenRoutine = { screen.value = RoutinesScreens.editor(it) },
            )
            current == RoutinesScreens.WORKOUT -> ActiveWorkoutRoute(
                state = state,
                timer = timer,
                actions = viewModel,
                onMinimize = { screen.value = RoutinesScreens.TRAIN },
                onOpenSummary = {},
                onExerciseHistory = {},
                onChooseRoutine = {},
            )
        }
    }
}
