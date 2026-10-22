package com.mooscles.sharing

import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.WorkoutCalculations
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/** Display-ready values derived from one saved, completed workout, never the current UI. */
data class WorkoutShareCard(
    val workoutId: String,
    val workoutNumber: Int,
    val workoutName: String,
    val dateTime: String,
    val rows: List<ShareExerciseRow>,
    val durationText: String,
    val volumeText: String,
    val prCount: Int
)

data class ShareExerciseRow(
    val exerciseName: String,
    val completedSets: Int,
    val bestSetText: String,
    val hasPr: Boolean
)

object WorkoutShareCards {
    private val chronology = compareBy<Workout> { it.startedAt }
        .thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }

    fun from(
        data: AppData,
        workoutId: String,
        pounds: Boolean,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WorkoutShareCard {
        val workout = requireNotNull(data.workouts.firstOrNull { it.id == workoutId }) {
            "This workout is no longer available. Choose another completed workout."
        }
        require(workout.status == WorkoutStatus.FINISHED) { "Finish this workout before sharing it." }
        // A damaged old snapshot without an end time must not acquire a growing duration.
        val summary = requireNotNull(WorkoutCalculations.summary(data, workoutId,
            now = workout.endedAt ?: workout.startedAt))
        val number = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        val integer = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
        val unit = if (pounds) "lb" else "kg"
        fun weight(kg: Double) = "${number.format(WeightUnits.fromKg(kg, pounds))} $unit"
        val rows = summary.exercises.filter { it.completedSets.isNotEmpty() }.map { entry ->
            val completed = entry.completedSets
            val eligible = completed.filter {
                !it.warmup && !it.assistance && entry.exercise.convention != LoadConvention.ASSISTANCE
            }
            val best = bestSet(eligible)
            val bestText = when {
                best != null && best.weightKg > 0.0 -> "${weight(best.weightKg)} × ${integer.format(best.reps)}"
                best != null -> "Bodyweight × ${integer.format(best.reps)}"
                completed.all { it.warmup } -> "Warm-up only"
                entry.exercise.convention == LoadConvention.ASSISTANCE || completed.all { it.assistance } -> "Assistance only"
                else -> "No working set"
            }
            val hasWeightPr = entry.pr?.let { record ->
                eligible.any { sameLoad(it.weightKg, record.newWeightKg) && it.reps == record.reps }
            } ?: false
            val hasRepsPr = entry.repsPrs.any { record ->
                completed.any { set ->
                    !set.warmup && sameLoad(set.weightKg, record.weightKg) && set.reps == record.newReps &&
                        (set.assistance || entry.exercise.convention == LoadConvention.ASSISTANCE) == record.assistance
                }
            }
            ShareExerciseRow(entry.exercise.name, completed.size, bestText, hasWeightPr || hasRepsPr)
        }
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology)
        return WorkoutShareCard(
            workoutId = workout.id,
            workoutNumber = finished.indexOfFirst { it.id == workoutId } + 1,
            workoutName = workout.name,
            dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale).withZone(zoneId).format(Instant.ofEpochMilli(workout.startedAt)),
            rows = rows,
            durationText = duration(summary.durationMillis / 1000, locale),
            volumeText = weight(summary.volumeKg),
            prCount = summary.prExerciseCount
        )
    }

    /** Round-trip unit noise is equal load; both displayed values still come from one real set. */
    private fun bestSet(sets: List<WorkoutSet>): WorkoutSet? {
        val highest = sets.maxOfOrNull { it.weightKg } ?: return null
        return sets.filter { sameLoad(it.weightKg, highest) }.maxByOrNull { it.reps }
    }

    private fun sameLoad(first: Double, second: Double) =
        abs(first - second) <= maxOf(Math.ulp(first), Math.ulp(second)) * 4

    private fun duration(seconds: Long, locale: Locale): String {
        val safe = seconds.coerceAtLeast(0L)
        return if (safe >= 3600) String.format(locale, "%d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
        else String.format(locale, "%d:%02d", safe / 60, safe % 60)
    }
}
