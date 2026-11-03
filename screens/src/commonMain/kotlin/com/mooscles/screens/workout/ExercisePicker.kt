package com.mooscles.screens.workout

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi

/**
 * The recent exercises of the exercise picker and the library (spec "Seletor de exercício"). The
 * search, the filters and the order of both are the library's [com.mooscles.screens.library.LibraryModel].
 */
object ExercisePicker {
    /** How many recent exercises the picker shows. */
    const val RECENT_LIMIT = 8

    /**
     * The exercises of the latest workouts in [history], newest workout first (by `startedAt`,
     * whatever order the list has) and in each workout's own order, each exercise once (by id), up
     * to [limit]. They are the history's snapshots: the picker shows the catalog's current entry
     * for each id.
     */
    fun recents(history: List<WorkoutUi>, limit: Int = RECENT_LIMIT): List<ExerciseUi> {
        val seen = HashSet<String>()
        val recent = ArrayList<ExerciseUi>()
        for (workout in history.sortedByDescending { it.startedAt }) {
            for (entry in workout.exercises) {
                if (recent.size >= limit) return recent
                if (seen.add(entry.exercise.id)) recent += entry.exercise
            }
        }
        return recent
    }

    /**
     * The [recents] as they are now, for the picker and the library ("Recently used"): each id's
     * entry in [current] (not the history's snapshot), the ids [current] no longer has left out
     * before counting, so one that is gone makes room for the next, up to [limit].
     */
    fun currentRecents(history: List<WorkoutUi>, current: Map<String, ExerciseUi>, limit: Int = RECENT_LIMIT): List<ExerciseUi> =
        recents(history, limit = Int.MAX_VALUE).mapNotNull { current[it.id] }.take(limit)
}
