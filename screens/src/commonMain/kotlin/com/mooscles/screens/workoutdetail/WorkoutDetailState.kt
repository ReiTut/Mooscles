package com.mooscles.screens.workoutdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mooscles.screens.summary.SummaryStats
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi

/** Saveable mode of a saved workout. Android's host uses [editing] for its system Back handler. */
@Stable
class WorkoutDetailScreenState(editing: Boolean = false) {
    var editing by mutableStateOf(editing)
        private set

    fun beginEditing() { editing = true }
    fun finishEditing() { editing = false }

    companion object {
        val Saver: Saver<WorkoutDetailScreenState, Boolean> = Saver(
            save = { it.editing }, restore = { WorkoutDetailScreenState(it) },
        )
    }
}

@Composable
fun rememberWorkoutDetailScreenState(workoutId: String): WorkoutDetailScreenState =
    rememberSaveable(workoutId, saver = WorkoutDetailScreenState.Saver) { WorkoutDetailScreenState() }

/** Domain-backed numbers shared by the detail's visual and spoken statistics. */
internal data class DetailStats(val sets: Int, val exercises: Int, val prs: Int, val volumeKg: Double) {
    companion object {
        fun from(workout: WorkoutUi): DetailStats {
            val completed = workout.exercises.map(SummaryStats::completedSets)
            return DetailStats(completed.sumOf { it.size }, completed.count { it.isNotEmpty() },
                workout.prExerciseCount, workout.volumeKg)
        }
    }
}

/** Keep first records visibly separate while the headline PR total includes them (Build 25). */
internal data class DetailRecordSections(val beaten: List<RecordUi>, val first: List<RecordUi>) {
    companion object {
        fun from(workout: WorkoutUi) = DetailRecordSections(
            workout.records.filterNot(RecordUi::firstRecord), workout.records.filter(RecordUi::firstRecord),
        )
    }
}
