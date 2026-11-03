package com.mooscles.screens.workout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.PrToast
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.format.displayName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_record_reps_detail
import com.mooscles.screens.resources.workout_record_reps_title
import com.mooscles.screens.resources.workout_record_weight_detail
import com.mooscles.screens.resources.workout_record_weight_title
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/** The test tag of the slot the record toast sits in, at the top of the list. */
internal const val RecordToastTag = "workout-record-toast"

/**
 * A record to announce: the set [setId] of [exerciseName], done at [weightKg] × [reps] (exact kg),
 * beat [record]. [key] is `"<setId>:<weightKg>:<reps>:<kind>"`.
 */
data class RecordEvent(
    val key: String,
    val setId: String,
    val exerciseName: String,
    val record: SetRecordUi,
    val weightKg: Double,
    val reps: Int,
)

/** The best record announced in one category ([RecordAnnouncer.category]) this session. */
data class RecordBest(val weightKg: Double, val reps: Int)

/**
 * What one emission of the workout settles ([RecordAnnouncer.step]): the checks it [consumed] (their
 * completion arrived), the records to [announce] and the best announced per category after them.
 */
data class RecordStep(val consumed: Set<String>, val announce: List<RecordEvent>, val announced: Map<String, RecordBest>)

/**
 * Which record the active workout celebrates (spec D17, D18): only a set the person just completed
 * here. The record is the presentation's (`SetUi.record`, chosen by the domain), never the screen's
 * guess; the screen only decides whether to celebrate it.
 */
object RecordAnnouncer {
    /** The key of [record] on the set [setId] done at [weightKg] × [reps]. */
    fun key(setId: String, weightKg: Double, reps: Int, record: SetRecordUi): String = "$setId:$weightKg:$reps:${record.kind}"

    /**
     * The category a record competes in: the exercise and its convention (records are kept per
     * both, across the entries of the same exercise) and the kind; a reps record also by the load
     * of [set], since it is "most reps at this load", and by its assistance mode, which the domain
     * keeps apart (an assisted set, or any set of the assistance convention).
     */
    fun category(entry: WorkoutExerciseUi, record: SetRecordUi, set: SetUi): String {
        val base = "${entry.exercise.id}|${entry.exercise.convention}|${record.kind}"
        if (record.kind != RecordKind.REPS) return base
        val assisted = set.assistance || entry.exercise.convention == LoadConvention.ASSISTANCE.label
        return if (assisted) "$base|${set.weightKg}|assisted" else "$base|${set.weightKg}"
    }

    /**
     * Whether [weightKg] × [reps] strictly beats [best]: a weight record by a heavier load, or the
     * same load with more reps; a reps record (same load, by its category) by more reps.
     */
    fun beats(kind: RecordKind, weightKg: Double, reps: Int, best: RecordBest?): Boolean = when {
        best == null -> true
        kind == RecordKind.WEIGHT -> weightKg > best.weightKg || (weightKg == best.weightKg && reps > best.reps)
        else -> reps > best.reps
    }

    /**
     * Settles one emission of [workout]. Each set of [checkedHere] (checked here, its completion not
     * seen yet) that is now done is consumed: this is the echo of that check, and the only moment
     * it can be celebrated. It is announced when it carries a record that strictly beats the best
     * of its category in [announced]. So a record already there when the screen opened (a resumed
     * workout), one inherited when another set is unchecked or removed, one kept while a done set is
     * corrected or typed into, and a first record (no `SetUi.record`, D18) say nothing; a later set
     * beating the record again says it once more, with the new value. [nameOf] gives each event's
     * [RecordEvent.exerciseName]: the screen passes the name it shows ([com.mooscles.screens.format.exerciseName]).
     */
    fun step(
        workout: WorkoutUi,
        checkedHere: Set<String>,
        announced: Map<String, RecordBest>,
        nameOf: (ExerciseUi) -> String = { it.name },
    ): RecordStep {
        if (checkedHere.isEmpty()) return RecordStep(emptySet(), emptyList(), announced)
        val consumed = HashSet<String>()
        val events = ArrayList<RecordEvent>()
        var best = announced
        for (entry in workout.exercises) {
            for (set in entry.sets) {
                if (!set.completed || set.id !in checkedHere) continue
                consumed += set.id
                val record = set.record ?: continue
                val category = category(entry, record, set)
                if (!beats(record.kind, set.weightKg, set.reps, best[category])) continue
                best = best + (category to RecordBest(set.weightKg, set.reps))
                events += RecordEvent(key(set.id, set.weightKg, set.reps, record), set.id, nameOf(entry.exercise), record, set.weightKg, set.reps)
            }
        }
        return RecordStep(consumed, events, best)
    }
}

/**
 * The record reaction of the active workout (spec D17, decision 15), put once over the top of the
 * list, under the header. Each emission goes through [RecordAnnouncer.step]; when it consumes a
 * check, a frame later (so after the tap's `SetCompleted`, which the row plays: the workout rows
 * pass `recordHaptic = false`) the screen state takes the step (saved, so a scroll, a recreation
 * or a resumed workout never plays a record again) and, with records to announce, the
 * `PersonalRecord` haptic plays once and their toasts queue.
 *
 * The [PrToast] shows "New weight record" or "New reps record" with the set and what it beat, for
 * `timing.prToast`, read politely, one record after another. A toast not shown yet waits while a
 * row is in edit, so it never covers it; one already shown that an edit hides is dropped, so it is
 * read once. A tap on it dismisses it (it never reaches the row under it). A queued toast whose set
 * left the workout is dropped. The queue is not saved: after a recreation the badge stays, and
 * nothing is shown or read again. The badge is the row's ([WorkoutSetRow]).
 */
@Composable
internal fun RecordReaction(workout: WorkoutUi, pounds: Boolean, screen: ActiveWorkoutScreenState, modifier: Modifier = Modifier) {
    val haptics = MTheme.haptics
    val portuguese = catalogInPortuguese()
    val nameOf: (ExerciseUi) -> String = { displayName(it, portuguese) }
    var queue by remember(workout.id) { mutableStateOf(emptyList<RecordEvent>()) }
    LaunchedEffect(workout) {
        val ids = workout.exercises.flatMapTo(HashSet()) { e -> e.sets.map { it.id } }
        if (queue.any { it.setId !in ids }) queue = queue.filter { it.setId in ids }
        if (RecordAnnouncer.step(workout, screen.checkedHere, screen.announced).consumed.isEmpty()) return@LaunchedEffect
        // The row plays the check's haptic from its own effect; this one comes after it. Nothing
        // suspends after the frame, so a newer emission cannot cut the step in half.
        withFrameNanos { }
        val step = RecordAnnouncer.step(workout, screen.checkedHere, screen.announced, nameOf)
        screen.settleRecords(step)
        if (step.announce.isNotEmpty()) {
            haptics.perform(HapticEvent.PersonalRecord)
            queue = queue + step.announce
        }
    }
    val toast = queue.firstOrNull() ?: return
    val dismiss = { if (queue.firstOrNull() == toast) queue = queue.drop(1) }
    // Whether this toast has been on screen: then an edit drops it instead of holding it.
    val displayed = remember(toast) { booleanArrayOf(false) }
    if (screen.editing != null) {
        if (displayed[0]) LaunchedEffect(toast) { dismiss() }
        return
    }
    SideEffect { displayed[0] = true }
    // One PrToast per record, so each starts its own entrance and hold, even with the same text.
    key(toast) { Box(
        modifier
            .testTag(RecordToastTag)
            // Read before the cards it covers: first in the list's traversal group (ActiveWorkoutScreen).
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
            .widthIn(max = MTheme.sizes.contentMaxWidth)
            .fillMaxWidth()
            .pointerInput(toast) { detectTapGestures { dismiss() } },
    ) {
        PrToast(
            title = recordTitle(toast.record.kind),
            detail = recordDetail(toast, pounds),
            visible = true,
            onDismiss = dismiss,
        )
    } }
}

/** "New weight record" or "New reps record". */
@Composable
private fun recordTitle(kind: RecordKind): String = stringResource(
    if (kind == RecordKind.WEIGHT) Res.string.workout_record_weight_title else Res.string.workout_record_reps_title,
)

/**
 * The set and the record it beat: "87,5 kg × 6 · before 85 kg" (the history keeps only the best
 * load) or "80 kg × 8 · before 80 kg × 6" (the most reps at that load), in the chosen unit.
 */
@Composable
private fun recordDetail(event: RecordEvent, pounds: Boolean): String {
    val locale = currentNumberLocale()
    val unit = unitSymbol(pounds)
    val load: (Double) -> String = { kg -> MoosclesNumbers.weight(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), unit, locale) }
    val record = event.record
    val previousKg = record.previousKg
    val previousReps = record.previousReps
    return when {
        record.kind == RecordKind.WEIGHT && previousKg != null ->
            stringResource(Res.string.workout_record_weight_detail, load(event.weightKg), event.reps, load(previousKg))
        record.kind == RecordKind.REPS && previousReps != null ->
            stringResource(Res.string.workout_record_reps_detail, load(event.weightKg), event.reps, previousReps)
        // The domain never sends a record without what it beat; should one come, the set alone.
        else -> MoosclesNumbers.setText(MoosclesNumbers.plain(WeightUnits.fromKg(event.weightKg, pounds)), unit, event.reps, locale)
    }
}
