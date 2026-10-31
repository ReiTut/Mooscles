package com.mooscles

import com.mooscles.data.ActiveWorkoutSnapshot
import com.mooscles.data.AppData
import com.mooscles.ui.UiState

/** Exists only between Start/resume and the first matching full repository read. */
internal data class WorkoutStartHandoff(
    val token: Long,
    val snapshot: ActiveWorkoutSnapshot,
    val revision: Long = 0,
    val editing: Boolean = false,
)

internal data class PreparedWorkoutContent(
    val source: AppData?,
    val handoff: WorkoutStartHandoff?,
    val ui: UiState,
    val recovery: ActiveWorkoutSnapshot? = null,
)

internal data class WorkoutContentPublication(
    val prepared: PreparedWorkoutContent,
    val source: AppData?,
    val handoff: WorkoutStartHandoff?,
    val ended: Set<String>,
    val recovery: ActiveWorkoutSnapshot? = null,
)

/** Publication fence: background work cannot publish over a newer command or source emission. */
internal fun currentWorkoutContent(
    previous: UiState,
    prepared: PreparedWorkoutContent,
    source: AppData?,
    handoff: WorkoutStartHandoff?,
    ended: Set<String>,
    recovery: ActiveWorkoutSnapshot? = null,
): UiState {
    val accepted = if (prepared.source === source && prepared.handoff == handoff && prepared.recovery == recovery)
        prepared.ui else previous
    return if (accepted.active?.id in ended) accepted.copy(active = null, secondaryLoading = false) else accepted
}
