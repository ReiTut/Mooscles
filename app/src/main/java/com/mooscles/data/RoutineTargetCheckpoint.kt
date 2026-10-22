package com.mooscles.data

import androidx.room.Entity
import com.mooscles.domain.RoutineTargetRecord

/** Last consumed PR, independent of manually editable routine prescriptions. */
@Entity(tableName = "routine_target_checkpoints", primaryKeys = ["exerciseId", "convention"])
data class RoutineTargetCheckpoint(
    val exerciseId: String,
    val convention: LoadConvention,
    val weightKg: Double?,
    val reps: Int,
) {
    fun record() = RoutineTargetRecord(exerciseId, convention, weightKg, reps)
}
