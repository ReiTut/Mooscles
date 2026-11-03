package com.mooscles.screens.routines

import com.mooscles.data.MuscleGroup
import com.mooscles.screens.format.ScreenDates
import com.mooscles.ui.RoutineUi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** When a routine was last done, in the buckets the card words ("today", "3 days ago", a date, "never"). */
internal sealed interface LastDone {
    data object Never : LastDone
    data object Today : LastDone
    data object Yesterday : LastDone

    /** 2 to [RoutineSummaries.RELATIVE_DAYS] calendar days ago. */
    data class DaysAgo(val n: Int) : LastDone

    /** Longer ago than that: the date of [epochMillis]. */
    data class On(val epochMillis: Long) : LastDone
}

/** What a routine card says about its routine, as pure functions so they are tested without a screen. */
internal object RoutineSummaries {
    /** The last day count worded as "N days ago"; a day older than this is shown as a date. */
    const val RELATIVE_DAYS = 30

    fun exerciseCount(r: RoutineUi): Int = r.exercises.size

    fun setCount(r: RoutineUi): Int = r.exercises.sumOf { it.sets }

    /**
     * The [max] muscle groups (English labels, as `ExerciseUi.muscle` carries them) that most of the routine's
     * exercises train; a tie goes to the group earlier in the [MuscleGroup] enum, and a label no group has after them.
     */
    fun topMuscles(r: RoutineUi, max: Int = 3): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (planned in r.exercises) counts[planned.exercise.muscle] = (counts[planned.exercise.muscle] ?: 0) + 1
        return counts.entries
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Map.Entry<String, Int>>>(
                    { -it.value.value },
                    { MuscleGroup.fromLabelOrName(it.value.key)?.ordinal ?: Int.MAX_VALUE },
                    { it.index },
                ),
            )
            .take(max)
            .map { it.value.key }
    }

    /** [at] against [now] in [zone], by calendar day: an `endedAt` in the future counts as today. */
    fun lastDone(at: Long?, now: Long, zone: TimeZone): LastDone {
        if (at == null) return LastDone.Never
        val then = ScreenDates.localDateTime(at, zone).date
        val today = ScreenDates.localDateTime(now, zone).date
        val days = then.daysUntil(today)
        return when {
            days <= 0 -> LastDone.Today
            days == 1 -> LastDone.Yesterday
            days <= RELATIVE_DAYS -> LastDone.DaysAgo(days)
            else -> LastDone.On(at)
        }
    }
}
