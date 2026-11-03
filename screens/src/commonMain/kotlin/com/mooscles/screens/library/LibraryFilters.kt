package com.mooscles.screens.library

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.mooscles.ui.ExerciseUi

/**
 * The library's filters (spec "Biblioteca", Filtros): muscle groups and equipment, by the English
 * labels `ExerciseUi` carries, and "My exercises" (only custom ones). Inside a dimension any value
 * matches ("or"); between dimensions all must hold ("and"); an empty dimension keeps everything.
 * The muscle is the exercise's own `MuscleGroup`, which every exercise has, not the anatomy table.
 */
@Immutable
data class LibraryFilters(
    val muscles: Set<String> = emptySet(),
    val equipment: Set<String> = emptySet(),
    val mineOnly: Boolean = false,
) {
    /** Whether any filter is on (the screen then shows only the results and "Clear filters"). */
    val active: Boolean get() = muscles.isNotEmpty() || equipment.isNotEmpty() || mineOnly

    /** Whether [exercise] passes every dimension. */
    fun accepts(exercise: ExerciseUi): Boolean =
        (muscles.isEmpty() || exercise.muscle in muscles) &&
            (equipment.isEmpty() || exercise.equipment in equipment) &&
            (!mineOnly || exercise.custom)

    companion object {
        /** For `rememberSaveable`: two lists of labels and a flag, all of them savable in a `Bundle`. */
        val Saver: Saver<LibraryFilters, Any> = listSaver(
            save = { listOf(ArrayList(it.muscles), ArrayList(it.equipment), it.mineOnly) },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                LibraryFilters(
                    muscles = (saved[0] as List<String>).toSet(),
                    equipment = (saved[1] as List<String>).toSet(),
                    mineOnly = saved[2] as Boolean,
                )
            },
        )
    }
}
