package com.mooscles.screens.share

import androidx.compose.runtime.Composable
import com.mooscles.data.AppData
import com.mooscles.data.ExerciseNames
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutStatus
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.resources.ds_pr_label
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.screens.format.BestSets
import com.mooscles.screens.format.Durations
import com.mooscles.screens.format.NoBestSet
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.format.two
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.best_set_assistance_only
import com.mooscles.screens.resources.best_set_bodyweight
import com.mooscles.screens.resources.best_set_no_working_set
import com.mooscles.screens.resources.best_set_warmup_only
import com.mooscles.screens.resources.date_day_fri
import com.mooscles.screens.resources.date_day_mon
import com.mooscles.screens.resources.date_day_sat
import com.mooscles.screens.resources.date_day_sun
import com.mooscles.screens.resources.date_day_thu
import com.mooscles.screens.resources.date_day_tue
import com.mooscles.screens.resources.date_day_wed
import com.mooscles.screens.resources.date_month_apr
import com.mooscles.screens.resources.date_month_aug
import com.mooscles.screens.resources.date_month_dec
import com.mooscles.screens.resources.date_month_feb
import com.mooscles.screens.resources.date_month_jan
import com.mooscles.screens.resources.date_month_jul
import com.mooscles.screens.resources.date_month_jun
import com.mooscles.screens.resources.date_month_mar
import com.mooscles.screens.resources.date_month_may
import com.mooscles.screens.resources.date_month_nov
import com.mooscles.screens.resources.date_month_oct
import com.mooscles.screens.resources.date_month_sep
import com.mooscles.screens.resources.share_assisted_set
import com.mooscles.screens.resources.share_best_set
import com.mooscles.screens.resources.share_date_format
import com.mooscles.screens.resources.share_duration
import com.mooscles.screens.resources.share_exercises
import com.mooscles.screens.resources.share_no_completed_sets
import com.mooscles.screens.resources.share_records
import com.mooscles.screens.resources.share_set_count
import com.mooscles.screens.resources.share_sets
import com.mooscles.screens.resources.share_volume
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DesignRes

/** One record of the workout on the card: "Bench press", "87,5 kg × 6". */
data class ShareRecordRow(val name: String, val detail: String)

/**
 * One exercise of the workout: its completed sets and its best set ("82,5 kg × 8", "Warm-up only").
 * [hasRecord] says the best set shown is itself a record set, which the card draws in brass with
 * the record icon; a record on another set of the exercise (more reps at a lighter load) is only
 * in the card's records, so the brass never marks a set that is not a record.
 */
data class ShareCardRow(val name: String, val completedSets: Int, val bestSet: String, val hasRecord: Boolean)

/** The fixed words the card draws, in the app's language. */
data class ShareCardLabels(
    val duration: String,
    val sets: String,
    val volume: String,
    val records: String,
    val exercises: String,
    val bestSet: String,
    val noCompletedSets: String,
    val recordBadge: String,
    /** The completed sets of an exercise, a template on its count: `%1$d set` for one, else `%1$d sets` (zero too). */
    val setCountOne: String,
    val setCountOther: String,
)

/**
 * The card's texts, already resolved from the resources, so [ShareCards.from] needs no
 * composition. The formats are the raw templates (`%1$s`, `%1$d`), filled by [fillTemplate] the
 * way the Compose resources formatter fills them.
 */
data class ShareCardTexts(
    /** January to December. */
    val months: List<String>,
    /** Monday to Sunday (the order of `DayOfWeek`). */
    val weekdays: List<String>,
    /** Weekday, day, month name, year, start and end time: `%1$s, %3$s %2$d, %4$d · %5$s–%6$s`. */
    val dateFormat: String,
    /** Repetitions with the body's weight only. */
    val bodyweightSet: String,
    /** Assistance load, then repetitions. */
    val assistedSet: String,
    val warmupOnly: String,
    val assistanceOnly: String,
    val noWorkingSet: String,
    val labels: ShareCardLabels,
    /**
     * Whether the catalog's exercise names are drawn in Portuguese ([catalogInPortuguese], which
     * [rememberShareCardTexts] reads); a custom exercise keeps its typed name either way.
     */
    val catalogPortuguese: Boolean = false,
) {
    init {
        require(months.size == 12) { "Twelve month names" }
        require(weekdays.size == 7) { "Seven weekday names" }
    }
}

/** Display-ready values of one saved, finished workout, never the current UI. */
data class ShareCardData(
    val workoutName: String,
    val dateText: String,
    val durationText: String,
    val setsText: String,
    val volumeText: String,
    val records: List<ShareRecordRow>,
    val rows: List<ShareCardRow>,
    /** Chronological ordinal among finished workouts; names the exported files. */
    val workoutNumber: Int,
    val labels: ShareCardLabels,
)

object ShareCards {
    private val chronology = compareBy<Workout> { it.startedAt }
        .thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }

    /**
     * The card of [workoutId] in [data]: the rules of the Android card it replaced (best set,
     * record badges, duration that cannot grow without an end time), with numbers in [locale] and
     * the date in [zone]. Fails with [IllegalArgumentException] for a missing or unfinished workout.
     */
    fun from(
        data: AppData,
        workoutId: String,
        pounds: Boolean,
        locale: NumberLocale,
        zone: TimeZone,
        texts: ShareCardTexts,
    ): ShareCardData {
        val workout = requireNotNull(data.workouts.firstOrNull { it.id == workoutId }) {
            "This workout is no longer available. Choose another completed workout."
        }
        require(workout.status == WorkoutStatus.FINISHED) { "Finish this workout before sharing it." }
        // A damaged old snapshot without an end time must not acquire a growing duration.
        val end = workout.endedAt ?: workout.startedAt
        val summary = requireNotNull(WorkoutCalculations.summary(data, workoutId, now = end))
        val unit = if (pounds) "lb" else "kg"
        fun plain(kg: Double) = MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds))
        fun weight(kg: Double) = MoosclesNumbers.weight(plain(kg), unit, locale)
        // The name shown, as on the screens: custom by the exercise table, as the presentation does.
        val custom = data.exercises.associate { it.id to it.custom }
        fun name(exerciseId: String, stored: String) =
            ExerciseNames.display(exerciseId, stored, custom[exerciseId] ?: false, texts.catalogPortuguese)
        fun loadByReps(kg: Double, reps: Int) =
            if (kg > 0.0) MoosclesNumbers.setText(plain(kg), unit, reps, locale) else fillTemplate(texts.bodyweightSet, reps)

        val rows = summary.exercises.filter { it.completedSets.isNotEmpty() }.map { entry ->
            val completed = entry.completedSets
            val assistanceConvention = entry.exercise.convention == LoadConvention.ASSISTANCE
            val eligible = completed.filter { BestSets.eligible(it.warmup, it.assistance, assistanceConvention) }
            val best = BestSets.best(eligible, { it.weightKg }, { it.reps })
            val bestText = if (best != null) loadByReps(best.weightKg, best.reps) else
                when (BestSets.whyNone(completed, { it.warmup }, { it.assistance }, assistanceConvention)) {
                    NoBestSet.WARMUP_ONLY -> texts.warmupOnly
                    NoBestSet.ASSISTANCE_ONLY -> texts.assistanceOnly
                    NoBestSet.NO_WORKING_SET -> texts.noWorkingSet
                }
            // Only the best set itself: brass beside "62,5 kg × 8" must mean that set is a record.
            val bestIsWeightPr = best != null && entry.pr?.let { record ->
                BestSets.sameLoad(best.weightKg, record.newWeightKg) && best.reps == record.reps
            } == true
            val bestIsRepsPr = best != null && entry.repsPrs.any { record ->
                !best.warmup && BestSets.sameLoad(best.weightKg, record.weightKg) && best.reps == record.newReps &&
                    (best.assistance || entry.exercise.convention == LoadConvention.ASSISTANCE) == record.assistance
            }
            ShareCardRow(name(entry.exercise.exerciseId, entry.exercise.name), completed.size, bestText, bestIsWeightPr || bestIsRepsPr)
        }

        // One line per variation, in workout order: its weight record, else its first repetitions record.
        val seen = mutableSetOf<Pair<String, LoadConvention>>()
        val records = summary.exercises.mapNotNull { entry ->
            val key = entry.exercise.exerciseId to entry.exercise.convention
            if (key in seen) return@mapNotNull null
            val weightRecord = entry.pr
            val repsRecord = entry.repsPrs.firstOrNull()
            val row = when {
                weightRecord != null ->
                    ShareRecordRow(name(weightRecord.exerciseId, weightRecord.exerciseName), loadByReps(weightRecord.newWeightKg, weightRecord.reps))
                repsRecord != null -> ShareRecordRow(name(repsRecord.exerciseId, repsRecord.exerciseName),
                    if (repsRecord.assistance) fillTemplate(texts.assistedSet, weight(repsRecord.weightKg), repsRecord.newReps)
                    else loadByReps(repsRecord.weightKg, repsRecord.newReps))
                else -> null
            }
            row?.also { seen += key }
        }

        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology)
        return ShareCardData(
            workoutName = workout.name,
            dateText = dateRange(workout.startedAt, end, zone, texts),
            durationText = Durations.clock(summary.durationMillis / 1000),
            setsText = MoosclesNumbers.localize(summary.completedSetCount.toString(), locale),
            volumeText = weight(summary.volumeKg),
            records = records,
            rows = rows,
            workoutNumber = finished.indexOfFirst { it.id == workoutId } + 1,
            labels = texts.labels,
        )
    }

    /**
     * `Tue, Oct 29, 2024 · 18:02–18:44` / `ter., 29 de out. de 2024 · 18:02–18:44`: the summary's
     * names and 24 h clock, with the year, since a shared picture outlives the season it was taken in.
     */
    private fun dateRange(startMillis: Long, endMillis: Long, zone: TimeZone, texts: ShareCardTexts): String {
        val start = ScreenDates.localDateTime(startMillis, zone)
        val end = ScreenDates.localDateTime(endMillis, zone)
        return fillTemplate(
            texts.dateFormat,
            texts.weekdays[start.dayOfWeek.ordinal],
            start.date.day,
            texts.months[start.date.month.number - 1],
            start.date.year,
            "${two(start.hour)}:${two(start.minute)}",
            "${two(end.hour)}:${two(end.minute)}",
        )
    }
}

private val placeholder = Regex("%(\\d+)\\$[ds]")

/** Fills `%N$s` and `%N$d` with the N-th argument, as the Compose resources formatter does. */
internal fun fillTemplate(template: String, vararg args: Any): String =
    placeholder.replace(template) { match ->
        val index = match.groupValues[1].toInt()
        require(index in 1..args.size) { "\"${match.value}\" in \"$template\" has no argument (${args.size} given)" }
        args[index - 1].toString()
    }

/** Resolves every text of the card in the composition's language. */
@Composable
fun rememberShareCardTexts(): ShareCardTexts {
    // Without arguments a string resource comes back as written, placeholders included.
    val months = listOf(
        Res.string.date_month_jan, Res.string.date_month_feb, Res.string.date_month_mar,
        Res.string.date_month_apr, Res.string.date_month_may, Res.string.date_month_jun,
        Res.string.date_month_jul, Res.string.date_month_aug, Res.string.date_month_sep,
        Res.string.date_month_oct, Res.string.date_month_nov, Res.string.date_month_dec,
    ).map { stringResource(it) }
    val weekdays = listOf(
        Res.string.date_day_mon, Res.string.date_day_tue, Res.string.date_day_wed, Res.string.date_day_thu,
        Res.string.date_day_fri, Res.string.date_day_sat, Res.string.date_day_sun,
    ).map { stringResource(it) }
    return ShareCardTexts(
        months = months,
        weekdays = weekdays,
        dateFormat = stringResource(Res.string.share_date_format),
        bodyweightSet = stringResource(Res.string.best_set_bodyweight),
        assistedSet = stringResource(Res.string.share_assisted_set),
        warmupOnly = stringResource(Res.string.best_set_warmup_only),
        assistanceOnly = stringResource(Res.string.best_set_assistance_only),
        noWorkingSet = stringResource(Res.string.best_set_no_working_set),
        catalogPortuguese = catalogInPortuguese(),
        labels = ShareCardLabels(
            duration = stringResource(Res.string.share_duration),
            sets = stringResource(Res.string.share_sets),
            volume = stringResource(Res.string.share_volume),
            records = stringResource(Res.string.share_records),
            exercises = stringResource(Res.string.share_exercises),
            bestSet = stringResource(Res.string.share_best_set),
            noCompletedSets = stringResource(Res.string.share_no_completed_sets),
            recordBadge = stringResource(DesignRes.string.ds_pr_label),
            // Both languages: "one" only for 1, zero stays plural (as `pluralCount`).
            setCountOne = pluralStringResource(Res.plurals.share_set_count, 1),
            setCountOther = pluralStringResource(Res.plurals.share_set_count, 2),
        ),
    )
}
