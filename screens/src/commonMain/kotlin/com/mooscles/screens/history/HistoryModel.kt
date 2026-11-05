package com.mooscles.screens.history

import androidx.compose.runtime.Immutable
import com.mooscles.data.ExerciseAnatomy
import com.mooscles.data.ExerciseNames
import com.mooscles.data.MuscleGroup
import com.mooscles.data.MuscleRegion
import com.mooscles.data.SearchFolding
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.progress.ExerciseCharts
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressQuery
import com.mooscles.screens.progress.Trend
import com.mooscles.screens.progress.Trends
import com.mooscles.screens.progress.WorkingSets
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** Prepared once with the process's existing [ProgressIndex], never during composition or a search keystroke. */
@Immutable
class HistoryWorkout internal constructor(
    val workout: WorkoutUi,
    val completedSets: Int,
    val volumeKg: Double,
    val prCount: Int,
    val beatenPrCount: Int,
    val firstPrCount: Int,
    val previewExercises: List<ExerciseUi>,
    internal val searchText: String,
    internal val exerciseIds: Set<String>,
    internal val sessionExercises: List<ExerciseUi>,
    internal val muscleSets: Map<MuscleRegion, Double>,
    internal val fullBodySets: Double,
)

/** Immutable history metadata belonging to an index, not a second cache with its own invalidation. */
@Immutable
class HistoryIndex private constructor(val items: List<HistoryWorkout>) {
    /** Latest stored definitions, including deleted/unfinished historical exercises for the history picker. */
    val exercises: List<ExerciseUi> = items.flatMap { it.workout.exercises.map { entry -> entry.exercise } }.distinctBy { it.id }
    val allExerciseIds: Set<String> = items.flatMap { it.exerciseIds }.toSet()
    val sourceRoutineIds: Set<String> = items.mapNotNull { it.workout.sourceRoutineId }.toSet()

    companion object {
        fun build(history: List<WorkoutUi>): HistoryIndex = HistoryIndex(history
            .sortedWith(compareByDescending<WorkoutUi> { it.startedAt }.thenByDescending { it.id })
            .map { workout ->
                var completedSets = 0
                var volume = 0.0
                var fullBody = 0.0
                val muscles = LinkedHashMap<MuscleRegion, Double>()
                val sessionExercises = LinkedHashMap<String, ExerciseUi>()
                val previews = ArrayList<ExerciseUi>()
                val names = StringBuilder(workout.name)
                for (entry in workout.exercises) {
                    val exercise = entry.exercise
                    names.append(' ').append(exercise.name)
                        .append(' ').append(ExerciseNames.display(exercise.id, exercise.name, exercise.custom, false))
                        .append(' ').append(ExerciseNames.display(exercise.id, exercise.name, exercise.custom, true))
                    val convention = WorkingSets.convention(exercise.convention)
                    var valid = 0
                    var working = 0
                    entry.sets.forEach { set ->
                        if (WorkingSets.valid(set)) {
                            valid++
                            if (!set.warmup) working++
                        }
                        volume += WorkingSets.volumeKg(set, convention)
                    }
                    completedSets += valid
                    if (valid > 0) {
                        if (exercise.id !in sessionExercises) sessionExercises[exercise.id] = exercise
                        if (previews.size < 3) previews += exercise
                    }
                    if (working > 0) {
                        val anatomy = ExerciseAnatomy.forExercise(exercise.id, exercise.muscle)
                        if (anatomy != null) {
                            anatomy.primary.forEach { region -> muscles[region] = (muscles[region] ?: 0.0) + working }
                            anatomy.secondary.forEach { region -> muscles[region] = (muscles[region] ?: 0.0) + working * 0.5 }
                        } else if (MuscleGroup.fromLabelOrName(exercise.muscle) == MuscleGroup.FULL_BODY) {
                            fullBody += working
                        }
                    }
                }
                HistoryWorkout(workout, completedSets, volume, workout.prExerciseCount, workout.beatenPrExerciseCount,
                    workout.firstRecordExerciseCount, previews.toList(), SearchFolding.fold(names.toString()),
                    workout.exercises.map { it.exercise.id }.toSet(), sessionExercises.values.toList(), muscles.toMap(), fullBody)
            })
    }
}

/** Filter categories are AND; selected routines are OR. Unknown/deleted source routines belong to [otherRoutines]. */
data class HistoryQuery(
    val search: String = "",
    val routineIds: Set<String> = emptySet(),
    val otherRoutines: Boolean = false,
    val existingRoutineIds: Set<String> = emptySet(),
    val exerciseId: String? = null,
    val beatenOnly: Boolean = false,
    val day: LocalDate? = null,
    val zone: TimeZone,
) : ProgressQuery<HistoryResult> {
    override fun compute(index: ProgressIndex): HistoryResult {
        val data = index.historyData
        val words = SearchFolding.fold(search).split(' ').filter(String::isNotEmpty)
        val selected = data.items.filter { item ->
            val source = item.workout.sourceRoutineId
            val routineMatches = (routineIds.isEmpty() && !otherRoutines) || source in routineIds ||
                (otherRoutines && (source == null || source !in existingRoutineIds))
            routineMatches && words.all(item.searchText::contains) &&
                (exerciseId == null || exerciseId in item.exerciseIds) && (!beatenOnly || item.beatenPrCount > 0) &&
                (day == null || ScreenDates.localDate(item.workout.startedAt, zone) == day)
        }
        val months = selected.groupBy { monthStart(ScreenDates.localDate(it.workout.startedAt, zone)) }
            .map { (month, items) -> HistoryMonth(month, items) }
        return HistoryResult(this, selected, months, data.allExerciseIds, data.sourceRoutineIds,
            data.items.lastOrNull()?.let { ScreenDates.localDate(it.workout.startedAt, zone) })
    }
}

@Immutable
class HistoryMonth(val month: LocalDate, val items: List<HistoryWorkout>) {
    val workouts: List<WorkoutUi> = items.map(HistoryWorkout::workout)
}

@Immutable
class HistoryResult(
    val query: HistoryQuery,
    val items: List<HistoryWorkout>,
    val months: List<HistoryMonth>,
    val allExerciseIds: Set<String>,
    val sourceRoutineIds: Set<String>,
    val earliestDay: LocalDate?,
) {
    val workouts: List<WorkoutUi> = items.map(HistoryWorkout::workout)
    val total: Int get() = items.size
}

/** The calendar represents the whole history, independently of the list's filters. */
data class HistoryCalendarQuery(val year: Int, val month: Int, val zone: TimeZone) : ProgressQuery<HistoryCalendarMonth> {
    override fun compute(index: ProgressIndex): HistoryCalendarMonth {
        val first = LocalDate(year, month, 1)
        val next = first.plus(1, DateTimeUnit.MONTH)
        val days = LinkedHashMap<Int, MutableList<String>>()
        val names = LinkedHashMap<Int, MutableList<String>>()
        index.historyData.items.forEach { item ->
            val date = ScreenDates.localDate(item.workout.startedAt, zone)
            if (date >= first && date < next) {
                days.getOrPut(date.day) { ArrayList() } += item.workout.id
                names.getOrPut(date.day) { ArrayList() } += item.workout.name
            }
        }
        return HistoryCalendarMonth(first, days.mapValues { it.value.toList() }, names.mapValues { it.value.toList() },
            index.historyData.items.lastOrNull()?.let { ScreenDates.localDate(it.workout.startedAt, zone) })
    }
}

@Immutable
class HistoryCalendarMonth(
    val month: LocalDate,
    val dayWorkouts: Map<Int, List<String>>,
    val dayWorkoutNames: Map<Int, List<String>>,
    val earliestDay: LocalDate?,
) {
    val year: Int get() = month.year
    val monthNumber: Int get() = month.month.number
    val totalWorkouts: Int = dayWorkouts.values.sumOf { it.size }
}

enum class HistoryPeriod { FOUR_WEEKS, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL }
enum class HistoryBucketKind { WEEK, MONTH, QUARTER }

@Immutable
data class HistoryBucket(
    val kind: HistoryBucketKind,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val workouts: Int,
    val volumeKg: Double,
    val partial: Boolean,
)

@Immutable
data class MuscleDistribution(val region: MuscleRegion, val sets: Double, val perWeek: Double)

@Immutable
data class FrequentExercise(val exercise: ExerciseUi, val sessions: Int, val latestAt: Long)

@Immutable
class OverallProgress(
    val query: OverallProgressQuery,
    val hasHistory: Boolean,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val bucketKind: HistoryBucketKind,
    val workouts: Int,
    val sets: Int,
    val volumeKg: Double,
    val workoutsPerWeek: Double,
    val weeksWithWorkouts: Int,
    val weeks: Int,
    val buckets: List<HistoryBucket>,
    val muscles: List<MuscleDistribution>,
    val fullBodySets: Double,
    val mostPerformed: List<FrequentExercise>,
    val workoutTrend: Trend?,
    val volumeTrend: Trend?,
) {
    val period: HistoryPeriod get() = query.period
    val primaryRegions: Set<MuscleRegion> = muscles.filter { it.sets >= (muscles.firstOrNull()?.sets ?: 0.0) * 0.5 }
        .map(MuscleDistribution::region).toSet()
    val secondaryRegions: Set<MuscleRegion> = muscles.map(MuscleDistribution::region).toSet() - primaryRegions
}

/** Calendar-based periods, queried on the existing background memo. Fixed periods end today, not in future weeks. */
data class OverallProgressQuery(
    val period: HistoryPeriod = HistoryPeriod.THREE_MONTHS,
    val today: LocalDate,
    val zone: TimeZone,
    val weekStart: DayOfWeek,
) : ProgressQuery<OverallProgress> {
    override fun compute(index: ProgressIndex): OverallProgress {
        val all = index.historyData.items.map { it to ScreenDates.localDate(it.workout.startedAt, zone) }
        val earliest = all.minOfOrNull { it.second } ?: today
        val latest = all.maxOfOrNull { it.second } ?: today
        val end = if (period == HistoryPeriod.ALL) maxOf(today, latest) else today
        val currentWeek = ExerciseCharts.weekStartOf(today, weekStart)
        val start = when (period) {
            HistoryPeriod.FOUR_WEEKS -> currentWeek.minus(DatePeriod(days = 3 * 7))
            HistoryPeriod.THREE_MONTHS -> currentWeek.minus(DatePeriod(days = 12 * 7))
            HistoryPeriod.SIX_MONTHS -> currentWeek.minus(DatePeriod(days = 25 * 7))
            HistoryPeriod.ONE_YEAR -> monthStart(today).minus(11, DateTimeUnit.MONTH)
            HistoryPeriod.ALL -> monthStart(earliest)
        }
        val months = (end.year - start.year) * 12 + end.month.number - start.month.number + 1
        val kind = when (period) {
            HistoryPeriod.FOUR_WEEKS, HistoryPeriod.THREE_MONTHS, HistoryPeriod.SIX_MONTHS -> HistoryBucketKind.WEEK
            HistoryPeriod.ONE_YEAR -> HistoryBucketKind.MONTH
            HistoryPeriod.ALL -> if (months > 36) HistoryBucketKind.QUARTER else HistoryBucketKind.MONTH
        }
        val chosen = all.filter { (_, day) -> day >= start && day <= end }
        val firstWeek = ExerciseCharts.weekStartOf(start, weekStart)
        val lastWeek = ExerciseCharts.weekStartOf(end, weekStart)
        val weeks = ((lastWeek.toEpochDays() - firstWeek.toEpochDays()) / 7 + 1).toInt().coerceAtLeast(1)
        val activeWeeks = chosen.map { ExerciseCharts.weekStartOf(it.second, weekStart) }.toSet().size
        val inBuckets = chosen.groupBy { bucketStart(it.second, kind, weekStart) }
        val buckets = ArrayList<HistoryBucket>()
        var bucket = bucketStart(start, kind, weekStart)
        while (bucket <= end) {
            val next = nextBucket(bucket, kind)
            val inBucket = inBuckets[bucket].orEmpty()
            buckets += HistoryBucket(kind, bucket, next, inBucket.size, inBucket.sumOf { it.first.volumeKg },
                today >= bucket && today < next)
            bucket = next
        }
        val muscleCounts = LinkedHashMap<MuscleRegion, Double>()
        val frequent = LinkedHashMap<String, FrequentExercise>()
        chosen.forEach { (item, _) ->
            item.muscleSets.forEach { (region, sets) -> muscleCounts[region] = (muscleCounts[region] ?: 0.0) + sets }
            item.sessionExercises.forEach { exercise ->
                val previous = frequent[exercise.id]
                frequent[exercise.id] = FrequentExercise(previous?.exercise ?: exercise, (previous?.sessions ?: 0) + 1,
                    maxOf(previous?.latestAt ?: Long.MIN_VALUE, item.workout.startedAt))
            }
        }
        val muscles = muscleCounts.entries.sortedWith(compareByDescending<Map.Entry<MuscleRegion, Double>> { it.value }
            .thenBy { it.key.ordinal }).map { MuscleDistribution(it.key, it.value, it.value / weeks) }
        val complete = buckets.filterNot(HistoryBucket::partial)
        fun trend(value: (HistoryBucket) -> Double): Trend? = Trends.of(
            complete.map { (it.start.toEpochDays() - start.toEpochDays()).toDouble() }, complete.map(value))
        return OverallProgress(this, all.isNotEmpty(), start, end, kind, chosen.size,
            chosen.sumOf { it.first.completedSets }, chosen.sumOf { it.first.volumeKg }, chosen.size.toDouble() / weeks,
            activeWeeks, weeks, buckets, muscles, chosen.sumOf { it.first.fullBodySets },
            frequent.values.sortedWith(compareByDescending<FrequentExercise> { it.sessions }
                .thenByDescending { it.latestAt }.thenBy { it.exercise.id }).take(5),
            trend { it.workouts.toDouble() }, trend(HistoryBucket::volumeKg))
    }
}

private fun monthStart(date: LocalDate) = LocalDate(date.year, date.month, 1)

private fun bucketStart(date: LocalDate, kind: HistoryBucketKind, weekStart: DayOfWeek): LocalDate = when (kind) {
    HistoryBucketKind.WEEK -> ExerciseCharts.weekStartOf(date, weekStart)
    HistoryBucketKind.MONTH -> monthStart(date)
    HistoryBucketKind.QUARTER -> LocalDate(date.year, ((date.month.number - 1) / 3) * 3 + 1, 1)
}

private fun nextBucket(date: LocalDate, kind: HistoryBucketKind): LocalDate = when (kind) {
    HistoryBucketKind.WEEK -> date.plus(7, DateTimeUnit.DAY)
    HistoryBucketKind.MONTH -> date.plus(1, DateTimeUnit.MONTH)
    HistoryBucketKind.QUARTER -> date.plus(3, DateTimeUnit.MONTH)
}
