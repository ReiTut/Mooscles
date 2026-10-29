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
    val source: AppData,
    val handoff: WorkoutStartHandoff?,
    val ui: UiState,
)

internal data class WorkoutContentPublication(
    val prepared: PreparedWorkoutContent,
    val source: AppData,
    val handoff: WorkoutStartHandoff?,
    val ended: Set<String>,
)

/** Publication fence: background work cannot publish over a newer command or source emission. */
internal fun currentWorkoutContent(
    previous: UiState,
    prepared: PreparedWorkoutContent,
    source: AppData,
    handoff: WorkoutStartHandoff?,
    ended: Set<String>,
): UiState {
    val accepted = if (prepared.source === source && prepared.handoff == handoff) prepared.ui else previous
    return if (accepted.active?.id in ended) accepted.copy(active = null, secondaryLoading = false) else accepted
}
