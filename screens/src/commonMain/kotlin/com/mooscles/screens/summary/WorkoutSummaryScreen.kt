package com.mooscles.screens.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.format.NoBestSet
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.durationText
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.summaryDateRange
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.best_set_assistance_only
import com.mooscles.screens.resources.best_set_no_working_set
import com.mooscles.screens.resources.best_set_warmup_only
import com.mooscles.screens.resources.summary_assistance_load
import com.mooscles.screens.resources.summary_bodyweight
import com.mooscles.screens.resources.summary_consistency
import com.mooscles.screens.resources.summary_details
import com.mooscles.screens.resources.summary_done
import com.mooscles.screens.resources.summary_duration
import com.mooscles.screens.resources.summary_exercises
import com.mooscles.screens.resources.summary_first_records
import com.mooscles.screens.resources.summary_record_line
import com.mooscles.screens.resources.summary_records
import com.mooscles.screens.resources.summary_reps_record
import com.mooscles.screens.resources.summary_set
import com.mooscles.screens.resources.summary_sets
import com.mooscles.screens.resources.summary_share
import com.mooscles.screens.resources.summary_title
import com.mooscles.screens.resources.summary_volume
import com.mooscles.screens.resources.summary_weight_record
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import com.mooscles.screens.workout.bestSetText
import org.jetbrains.compose.resources.stringResource

/** The card of the four numbers; it carries [SummaryEntrance] when this composition played its entrance. */
const val SummaryStatsTag = "summary-stats"

/** The "Records" section (records beaten); absent without one. */
const val SummaryRecordsTag = "summary-records"

/** The "First records" section; absent without one. */
const val SummaryFirstRecordsTag = "summary-first-records"

/** The four numbers of the summary, in reading order. */
enum class SummaryStat { DURATION, SETS, VOLUME, EXERCISES }

/** The test tag of one number of the stats card. */
fun summaryStatTag(stat: SummaryStat): String = "summary-stat-${stat.name.lowercase()}"

/** The test tag of the row of the workout exercise [entryId]. */
fun summaryExerciseTag(entryId: String): String = "summary-exercise-$entryId"

/** For tests: the entrance the stats card played, absent when it did not play (already seen before a recreation). */
internal val SummaryEntrance = SemanticsPropertyKey<MotionChoice>("SummaryEntrance")

/** From this font scale the stats stand in one column and an exercise's best set goes under its name. */
private const val ONE_COLUMN_FONT_SCALE = 1.5f

/**
 * The end of a finished [workout] (spec D19): "Workout complete", its name and date range; the
 * duration, completed sets, volume (`WorkoutUi.volumeKg`) and exercises, sets and exercises
 * counted over the valid completed sets ([SummaryStats.completedSets]) as the share card does; the records beaten,
 * then the first records (each section only when it has one); how many workouts were finished in
 * the last 28 calendar days of [zone] ([ScreenDates.finishedInLast28Days] over [history], this
 * workout counted once even when [history] does not hold it yet); each exercise with valid completed
 * sets and its best set ([SummaryStats.bestSet], the share card's rule); and the actions Done,
 * Share and View details.
 *
 * The stats card enters once, with the `WorkoutFinished` key moment (a spring from 0.92 with the
 * opacity, a fade with reduced motion) and the `SetCompleted` haptic. A screen recreated from its
 * saved state does not replay it. The numbers use `num.lg`, stepping down to `num.md` and then
 * `num.sm` only when one of them would not fit its column on one line.
 *
 * Loads are shown in pounds when [pounds]; numbers follow the app's language.
 */
@Composable
fun WorkoutSummaryScreen(
    workout: WorkoutUi,
    history: List<WorkoutUi>,
    pounds: Boolean,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = currentTimeMillis(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val colors = MTheme.colors
    val type = MTheme.type
    val space = MTheme.space
    val locale = currentNumberLocale()
    val oneColumn = LocalDensity.current.fontScale >= ONE_COLUMN_FONT_SCALE
    val end = workout.endedAt ?: workout.startedAt
    // The domain's valid completed sets (`WorkoutCalculations.validCompleted`), as the share card counts.
    val counted = workout.exercises.map { it to SummaryStats.completedSets(it) }.filter { (_, sets) -> sets.isNotEmpty() }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.s4, vertical = space.s6),
        verticalArrangement = Arrangement.spacedBy(space.s6),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space.s1)) {
            MText(stringResource(Res.string.summary_title), type.titleLg, Modifier.semantics { heading() })
            MText(workout.name, type.titleMd)
            MText(summaryDateRange(workout.startedAt, end, zone), type.bodyMd, color = colors.textSecondary)
        }

        StatsCard(
            values = listOf(
                StatValue(SummaryStat.DURATION, stringResource(Res.string.summary_duration), durationText((end - workout.startedAt) / 1000L)),
                StatValue(SummaryStat.SETS, stringResource(Res.string.summary_sets), MoosclesNumbers.localize(counted.sumOf { (_, sets) -> sets.size }.toString(), locale)),
                StatValue(SummaryStat.VOLUME, stringResource(Res.string.summary_volume), weightText(workout.volumeKg, pounds, locale)),
                StatValue(SummaryStat.EXERCISES, stringResource(Res.string.summary_exercises), MoosclesNumbers.localize(counted.size.toString(), locale)),
            ),
            oneColumn = oneColumn,
        )

        val beaten = workout.records.filterNot { it.firstRecord }
        val first = workout.records.filter { it.firstRecord }
        if (beaten.isNotEmpty()) {
            RecordSection(stringResource(Res.string.summary_records), beaten, soft = false, pounds, locale, Modifier.testTag(SummaryRecordsTag))
        }
        if (first.isNotEmpty()) {
            RecordSection(stringResource(Res.string.summary_first_records), first, soft = true, pounds, locale, Modifier.testTag(SummaryFirstRecordsTag))
        }

        val others = history.filterNot { it.id == workout.id }
        val recent = ScreenDates.finishedInLast28Days(others + workout, nowMillis, zone)
        MText(pluralCount(Res.plurals.summary_consistency, recent), type.bodyMd, color = colors.textSecondary)

        val exercises = counted.map { (entry, _) -> entry }
        if (exercises.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().card().padding(space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                exercises.forEach { entry -> ExerciseLine(entry, pounds, locale, oneColumn) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
            MButton(stringResource(Res.string.summary_done), onDone, Modifier.fillMaxWidth(), MButtonVariant.Primary, MButtonSize.L)
            MButton(stringResource(Res.string.summary_share), onShare, Modifier.fillMaxWidth(), MButtonVariant.Secondary, MButtonSize.L, icon = MIcons.Share2)
            MButton(stringResource(Res.string.summary_details), onDetails, Modifier.fillMaxWidth(), MButtonVariant.Text)
        }
    }
}

@Composable
private fun Modifier.card(): Modifier {
    val shape = RoundedCornerShape(MTheme.radius.lg)
    return background(MTheme.colors.bgSurface, shape).border(MTheme.sizes.border, MTheme.colors.borderSubtle, shape)
}

/** One number of the stats card, with its label. */
private class StatValue(val stat: SummaryStat, val label: String, val value: String)

/**
 * The four numbers, two columns (one from [ONE_COLUMN_FONT_SCALE]), with the entrance. Every value
 * gets the same style: the largest of `num.lg`, `num.md` and `num.sm` in which all of them fit
 * their column on one line (`num.sm` when none does).
 */
@Composable
private fun StatsCard(values: List<StatValue>, oneColumn: Boolean) {
    val space = MTheme.space
    val type = MTheme.type
    val reduced = MTheme.reducedMotion
    val haptics = MTheme.haptics
    val fadeMs = MTheme.motion.fast
    // Saved, so a screen recreated after the entrance (rotation, process death) does not replay it.
    var entered by rememberSaveable { mutableStateOf(false) }
    val play = remember { !entered }
    val choice = remember { MotionPolicy.keyMoment(KeyMoment.WorkoutFinished, reduced) }
    val scale = remember { Animatable(if (play && choice is MotionChoice.Spring) choice.fromScale else 1f) }
    val alpha = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!play) return@LaunchedEffect
        entered = true
        haptics.perform(HapticEvent.SetCompleted)
        try {
            when (choice) {
                is MotionChoice.Spring -> coroutineScope {
                    launch { scale.animateTo(1f, spring(choice.tokens.dampingRatio, choice.tokens.stiffness)) }
                    launch { alpha.animateTo(1f, tween(fadeMs)) }
                }
                is MotionChoice.Fade -> alpha.animateTo(1f, tween(choice.durationMs))
            }
        } finally {
            // Whatever ended the entrance, the card rests whole and visible.
            withContext(NonCancellable) {
                scale.snapTo(1f)
                alpha.snapTo(1f)
            }
        }
    }

    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        Modifier
            .testTag(SummaryStatsTag)
            .semantics { if (play) this[SummaryEntrance] = choice }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .fillMaxWidth()
            .card()
            .padding(space.s4),
    ) {
        val gap = space.s4
        val columnWidth = with(LocalDensity.current) {
            (if (oneColumn) constraints.maxWidth else ((constraints.maxWidth - gap.roundToPx()) / 2)).coerceAtLeast(0)
        }
        val candidates = listOf(type.numLg, type.numMd, type.numSm)
        val numberStyle: TextStyle = candidates.firstOrNull { style ->
            values.all { measurer.measure(it.value, style, softWrap = false, maxLines = 1).size.width <= columnWidth }
        } ?: candidates.last()
        val cells = values.map { stat ->
            @Composable { cellModifier: Modifier -> StatCell(stat.stat, stat.label, stat.value, numberStyle, cellModifier) }
        }
        if (oneColumn) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { cells.forEach { it(Modifier.fillMaxWidth()) } }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                cells.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        pair.forEach { it(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(stat: SummaryStat, label: String, value: String, numberStyle: TextStyle, modifier: Modifier) {
    Column(
        modifier.testTag(summaryStatTag(stat)).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        MText(label, MTheme.type.labelCaps, color = MTheme.colors.textSecondary)
        MText(value, numberStyle)
    }
}

/** A titled list of records, one line each with the badge ([soft] for first records). */
@Composable
private fun RecordSection(
    title: String,
    records: List<RecordUi>,
    soft: Boolean,
    pounds: Boolean,
    locale: NumberLocale,
    modifier: Modifier,
) {
    val space = MTheme.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.s3)) {
        MText(title, MTheme.type.titleSm, Modifier.semantics { heading() }, color = MTheme.colors.textSecondary)
        records.forEach { record ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrBadge(soft = soft)
                MText(
                    stringResource(Res.string.summary_record_line, exerciseName(record.exercise), recordDetail(record, pounds, locale)),
                    MTheme.type.bodyMd,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * "87,5 kg × 6 · antes 85 kg" for a weight record beaten, "80 kg × 8 · antes 80 kg × 6" for a
 * repetitions record beaten, "82,5 kg × 8" for a first record.
 */
@Composable
private fun recordDetail(record: RecordUi, pounds: Boolean, locale: NumberLocale): String {
    val load = recordLoad(record, pounds, locale)
    val previousKg = record.previousKg
    val previousReps = record.previousReps
    return when {
        record.kind == RecordKind.WEIGHT && previousKg != null ->
            stringResource(Res.string.summary_weight_record, load, record.reps, weightText(previousKg, pounds, locale))
        record.kind == RecordKind.REPS && previousReps != null ->
            stringResource(Res.string.summary_reps_record, load, record.reps, previousReps)
        else -> stringResource(Res.string.summary_set, load, record.reps)
    }
}

/** The load of a record: "80 kg", "Bodyweight" with no load, "40 kg assistance" for an assisted repetitions record. */
@Composable
private fun recordLoad(record: RecordUi, pounds: Boolean, locale: NumberLocale): String = when {
    record.kind == RecordKind.REPS && record.assistance ->
        stringResource(Res.string.summary_assistance_load, weightText(record.weightKg, pounds, locale))
    record.weightKg == 0.0 -> stringResource(Res.string.summary_bodyweight)
    else -> weightText(record.weightKg, pounds, locale)
}

/** One exercise and its best set; the best set goes under the name from [ONE_COLUMN_FONT_SCALE]. */
@Composable
private fun ExerciseLine(entry: WorkoutExerciseUi, pounds: Boolean, locale: NumberLocale, stacked: Boolean) {
    val colors = MTheme.colors
    val type = MTheme.type
    val name = exerciseName(entry.exercise)
    val best = SummaryStats.bestSet(entry)
    val bestText = best?.let { bestSetText(it.weightKg, it.reps, pounds, locale) }
    val value: @Composable (Modifier) -> Unit = { valueModifier ->
        if (bestText != null) {
            MText(bestText, type.numSm, valueModifier)
        } else {
            val why = when (SummaryStats.noBestSet(entry)) {
                NoBestSet.WARMUP_ONLY -> Res.string.best_set_warmup_only
                NoBestSet.ASSISTANCE_ONLY -> Res.string.best_set_assistance_only
                NoBestSet.NO_WORKING_SET -> Res.string.best_set_no_working_set
            }
            MText(stringResource(why), type.bodyMd, valueModifier, color = colors.textSecondary)
        }
    }
    val rowModifier = Modifier.fillMaxWidth().testTag(summaryExerciseTag(entry.id)).semantics(mergeDescendants = true) {}
    if (stacked) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
            MText(name, type.bodyLg)
            value(Modifier)
        }
    } else {
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3), verticalAlignment = Alignment.CenterVertically) {
            MText(name, type.bodyLg, Modifier.weight(1f))
            value(Modifier)
        }
    }
}

private fun unit(pounds: Boolean) = if (pounds) "lb" else "kg"

/** "3.720 kg": [kg] in the chosen unit, the domain's rounding, the language's separators. */
private fun weightText(kg: Double, pounds: Boolean, locale: NumberLocale): String =
    MoosclesNumbers.weight(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), unit(pounds), locale)
