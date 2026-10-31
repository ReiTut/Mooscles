package com.mooscles.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.ds_pr_spoken
import com.mooscles.designsystem.resources.ds_set_type_warmup_short
import com.mooscles.designsystem.resources.ds_set_type_warmup_spoken
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.conventionLabel
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.exercise_not_found_body
import com.mooscles.screens.resources.progress_empty_action
import com.mooscles.screens.resources.progress_empty_body
import com.mooscles.screens.resources.progress_empty_title
import com.mooscles.screens.resources.progress_highlight
import com.mooscles.screens.resources.progress_missing
import com.mooscles.screens.resources.progress_other_convention
import com.mooscles.screens.resources.progress_records
import com.mooscles.screens.resources.progress_reps_count
import com.mooscles.screens.resources.progress_sessions
import com.mooscles.screens.resources.progress_view_all_records
import com.mooscles.screens.resources.records_spoken_join
import com.mooscles.screens.workout.RecompositionProbe
import com.mooscles.screens.workout.loadSpoken
import com.mooscles.screens.workout.loadText
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/** How many reps records the section shows before "View all N". */
internal const val SHOWN_REPS_RECORDS = 5

/** "Today" and the zone the screen reads its dates in (D8). Compared by value, so an equal one skips what reads it. */
@Immutable
internal data class ProgressCalendar(val zone: TimeZone, val today: LocalDate)

/** One row of the sessions: a session of the exercise in [convention] (the chart's, or an older one). */
@Immutable
internal class ProgressSession(val point: SessionPoint, val convention: String) {
    /** Unique along the list and a plain String, so a lazy list can save it in a Bundle. */
    val key: String get() = "session|${point.workoutId}|$convention"
}

/**
 * What the screen shows under the chart, for one exercise in its chart's [convention][ProgressSectionsQuery.convention]:
 * [weight], the current load record; [reps], the current reps records of the convention's group (unassisted, or
 * assisted on Assistance), the highest load first (on Assistance the lowest assistance first: less help is the
 * progress); [sessions], every session of the exercise in any convention, the newest first; and [otherConventions],
 * the conventions it has sessions in besides the chart's, the latest first.
 */
@Immutable
internal class ProgressSections(
    val weight: RecordEvent?,
    val reps: List<RecordEvent>,
    val sessions: List<ProgressSession>,
    val otherConventions: List<String>,
)

/**
 * The sections of [exerciseId] under the chart of [convention], one query of the cache: its records are the index's
 * current records ([ProgressIndex.currentRecords], each as its history event, so with the previous value; a record of
 * a deleted workout is gone), never the state's own copies.
 */
internal data class ProgressSectionsQuery(val exerciseId: String, val convention: String) : ProgressQuery<ProgressSections> {
    override fun compute(index: ProgressIndex): ProgressSections {
        val assistance = WorkingSets.convention(convention) == LoadConvention.ASSISTANCE
        val mine = index.currentRecords.filter { it.record.exercise.id == exerciseId && it.record.exercise.convention == convention }
        val weight = mine.filter { it.record.kind == RecordKind.WEIGHT }.maxByOrNull { it.record.weightKg }
        val group = mine.filter { it.record.kind == RecordKind.REPS && it.record.assistance == assistance }
        val reps = if (assistance) group.sortedBy { it.record.weightKg } else group.sortedByDescending { it.record.weightKg }
        val conventions = index.conventionsOf(exerciseId)
        val sessions = (if (convention in conventions) conventions else listOf(convention) + conventions)
            .flatMap { c -> index.sessions(SeriesKey(exerciseId, c)).map { ProgressSession(it, c) } }
            .sortedWith(compareByDescending<ProgressSession> { it.point.startedAt }.thenByDescending { it.point.workoutId })
        return ProgressSections(weight, reps, sessions, conventions.filter { it != convention })
    }
}

/**
 * The records of the exercise and its sessions, under the chart: "Records for this exercise" with the current load
 * record and the reps records by load (up to [SHOWN_REPS_RECORDS], then "View all N", which [onAllRecords] expands in
 * place), then "Sessions", the newest first, each one row that opens its workout ([onOpenWorkout]). A section with
 * nothing in it is left out.
 */
internal fun LazyListScope.progressSections(
    sections: ProgressSections,
    exerciseName: String,
    convention: String,
    pounds: Boolean,
    calendar: ProgressCalendar,
    allRecords: Boolean,
    onAllRecords: () -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
) {
    val weight = sections.weight
    val reps = sections.reps
    if (weight != null || reps.isNotEmpty()) {
        item(key = "records-title") { SectionHeading(stringResource(Res.string.progress_records), ProgressTags.RECORDS) }
        if (weight != null) {
            item(key = "records-weight-label") {
                RecordCaption(metricLabel(ProgressMetric.LOAD, convention), ProgressTags.RECORD_WEIGHT_LABEL)
            }
            item(key = "record|${RecordTimelines.key(weight)}") {
                RecordRow(weight, exerciseName, pounds, calendar, ProgressTags.RECORD_WEIGHT)
            }
        }
        if (reps.isNotEmpty()) {
            item(key = "records-reps-label") {
                RecordCaption(metricLabel(ProgressMetric.REPS, convention), ProgressTags.RECORD_REPS_LABEL)
            }
            val shown = if (allRecords) reps else reps.take(SHOWN_REPS_RECORDS)
            itemsIndexed(shown, key = { _, it -> "record|${RecordTimelines.key(it)}" }) { i, event ->
                RecordRow(event, exerciseName, pounds, calendar, ProgressTags.recordReps(i))
            }
            if (shown.size < reps.size) {
                item(key = "records-all") { ViewAllRecords(reps.size, onAllRecords) }
            }
        }
    }
    if (sections.sessions.isNotEmpty()) {
        item(key = "sessions-title") { SectionHeading(stringResource(Res.string.progress_sessions), ProgressTags.SESSIONS) }
        items(sections.sessions.size, key = { sections.sessions[it].key }) { i ->
            SessionRow(sections.sessions[i], pounds, calendar, onOpenWorkout)
        }
    }
}

/** The width of an item of the list, as above the chart: the content column, up to `contentMaxWidth`, with the 16 dp sides. */
@Composable
private fun Modifier.sectionItem(): Modifier =
    widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = MTheme.space.s4)

@Composable
private fun SectionHeading(text: String, tag: String) {
    MText(
        text,
        MTheme.type.titleSm,
        color = MTheme.colors.textPrimary,
        modifier = Modifier.sectionItem().padding(top = MTheme.space.s6, bottom = MTheme.space.s2).testTag(tag).semantics { heading() },
    )
}

/** "Load" over the load record, "Reps" over the reps records: what kind of record follows. */
@Composable
private fun RecordCaption(text: String, tag: String) {
    MText(
        text,
        MTheme.type.labelCaps,
        color = MTheme.colors.textSecondary,
        modifier = Modifier.sectionItem().padding(top = MTheme.space.s2).testTag(tag),
    )
}

/**
 * A current record: "100 kg × 3 · 12 set", and with the value it beat, "100 kg × 3 · antes 95 kg · 12 set" (the
 * history's event of the record carries it). One node, read with the unit in full ([recordSpoken]).
 */
@Composable
private fun RecordRow(event: RecordEvent, exerciseName: String, pounds: Boolean, calendar: ProgressCalendar, tag: String) {
    RecompositionProbe(tag)
    val record = event.record
    val text = if (record.firstRecord) {
        recordLine(record.weightKg, record.reps, record.date, pounds, calendar.zone)
    } else {
        stringResource(
            Res.string.progress_highlight,
            recordWasText(record.kind, record.weightKg, record.reps, record.previousKg, record.previousReps, pounds),
            shortDate(record.date, calendar.zone),
        )
    }
    val spoken = recordSpoken(
        exerciseName, record.kind, record.weightKg, record.reps, record.previousKg, record.previousReps, pounds,
        record.date, calendar.zone, calendar.today, current = false, first = false,
    )
    MText(
        text,
        MTheme.type.bodyLg,
        color = MTheme.colors.textPrimary,
        modifier = Modifier
            .sectionItem()
            .padding(vertical = MTheme.space.s2)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    )
}

/** "View all 7": shows every reps record in place. */
@Composable
private fun ViewAllRecords(count: Int, onClick: () -> Unit) {
    Box(Modifier.sectionItem()) {
        MButton(
            pluralCount(Res.plurals.progress_view_all_records, count),
            onClick = onClick,
            modifier = Modifier.testTag(ProgressTags.RECORDS_ALL),
            variant = MButtonVariant.Text,
        )
    }
}

/** One set as a session row writes it: "80 × 8", "A 40 × 10" for a warm-up (the set row's letter). No unit, as the board. */
@Composable
private fun setToken(set: SetUi, pounds: Boolean): String {
    val text = "${loadText(set.weightKg, pounds)} × ${set.reps}"
    return if (set.warmup) "${stringResource(DsRes.string.ds_set_type_warmup_short)} $text" else text
}

/** One set read aloud: "aquecimento, 40 quilos por 10", "12 repetições" without a load, then "recorde pessoal" on a record set. */
@Composable
private fun setSpoken(set: SetUi, pounds: Boolean): String {
    var text = if (set.weightKg == 0.0) pluralCount(Res.plurals.progress_reps_count, set.reps) else spokenLoadByReps(loadSpoken(set.weightKg, pounds), set.reps)
    if (set.warmup) text = stringResource(Res.string.records_spoken_join, stringResource(DsRes.string.ds_set_type_warmup_spoken), text)
    if (set.record != null) text = stringResource(Res.string.records_spoken_join, text, stringResource(DsRes.string.ds_pr_spoken))
    return text
}

/**
 * A session: its date, the workout's name and its completed sets in a line ("A 40 × 10 · 80 × 8 · 82,5 × 6"), with
 * the record badge on the set the domain gave the record (`SetUi.record`). One button for the reader, read as
 * [sessionSpoken]; a tap opens the workout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(session: ProgressSession, pounds: Boolean, calendar: ProgressCalendar, onOpenWorkout: (String) -> Unit) {
    val point = session.point
    val tag = ProgressTags.session(point.workoutId)
    RecompositionProbe(tag)
    val colors = MTheme.colors
    var setsSpoken = ""
    point.sets.forEachIndexed { i, set ->
        val one = setSpoken(set, pounds)
        setsSpoken = if (i == 0) one else stringResource(Res.string.records_spoken_join, setsSpoken, one)
    }
    val spoken = sessionSpoken(point.startedAt, point.workoutName, setsSpoken, calendar.zone, calendar.today)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    Box(Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = MTheme.space.s2)) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .clickable(interactionSource = source, indication = null, role = Role.Button) { onOpenWorkout(point.workoutId) }
                .semantics { contentDescription = spoken }
                .mFocusRing(shape, source)
                .background(if (pressed) colors.bgInset else Color.Transparent, shape)
                .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s3),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        ) {
            MText(shortDate(point.startedAt, calendar.zone), MTheme.type.labelMd, color = colors.textSecondary)
            MText(point.workoutName, MTheme.type.titleSm, color = colors.textPrimary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val last = point.sets.lastIndex
                point.sets.forEachIndexed { i, set ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.CenterVertically) {
                        MText(setToken(set, pounds), MTheme.type.bodyMd, color = colors.textPrimary)
                        if (set.record != null) {
                            // The row says "personal record" after the set; the badge adds nothing to the reading.
                            Box(Modifier.clearAndSetSemantics { testTag = ProgressTags.SESSION_RECORD }) { PrBadge(soft = true) }
                        }
                        if (i < last) MText(SEPARATOR, MTheme.type.bodyMd, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

/** Between two sets of a session row, at the end of the first, so a wrapped line never starts with it. */
private const val SEPARATOR = "·"

/**
 * The line under the chart when the exercise has sessions in another convention (D6, plan decision 8): "Workouts
 * logged as Per hand are not in this chart.", the conventions joined when there are several.
 */
@Composable
internal fun ConventionNote(conventions: List<String>) {
    val labels = conventions.map { conventionLabel(it) }.joinToString(", ")
    MText(
        stringResource(Res.string.progress_other_convention, labels),
        MTheme.type.bodyMd,
        color = MTheme.colors.textSecondary,
        modifier = Modifier.sectionItem().padding(top = MTheme.space.s2).testTag(ProgressTags.CONVENTION_NOTE),
    )
}

/** No session of the exercise in any convention: "You haven't done this exercise yet.", with "View in the library". */
@Composable
internal fun ProgressEmpty(onAbout: () -> Unit) {
    EmptyState(
        icon = MIcons.ChartLine,
        title = stringResource(Res.string.progress_empty_title),
        body = stringResource(Res.string.progress_empty_body),
        modifier = Modifier.sectionItem().testTag(ProgressTags.EMPTY),
        primaryAction = stringResource(Res.string.progress_empty_action) to onAbout,
    )
}

/** An exercise that is not in the library and has no history: "Exercise not found". */
@Composable
internal fun ProgressMissing() {
    EmptyState(
        icon = MIcons.SearchX,
        title = stringResource(Res.string.progress_missing),
        body = stringResource(Res.string.exercise_not_found_body),
        modifier = Modifier.sectionItem().testTag(ProgressTags.MISSING),
    )
}
