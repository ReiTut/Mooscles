package com.mooscles.screens.exercise

import com.mooscles.data.ExerciseAnatomy
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.screens.exercise.figures.ExerciseFigures
import com.mooscles.screens.exercise.figures.FigureHighlights
import com.mooscles.ui.ExerciseUi

/**
 * What a list, the picker or the workout card shows for [exercise] (spec D5): a catalog exercise
 * with a figure is its end frame, still, with its primary muscles lit; anything else (a custom
 * exercise, or a catalog one still without a figure) is the equipment icon.
 */
internal fun exerciseMedia(exercise: ExerciseUi): ExerciseMedia {
    if (exercise.custom) return ExerciseMedia.None
    val frames = ExerciseFigures.forExercise(exercise.id) ?: return ExerciseMedia.None
    val primary = ExerciseAnatomy.forExercise(exercise.id, exercise.muscle)?.primary.orEmpty()
    return ExerciseMedia.Figure(frames.end, FigureHighlights.forRegions(primary))
}
