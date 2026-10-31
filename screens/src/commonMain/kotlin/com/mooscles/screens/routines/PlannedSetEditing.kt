package com.mooscles.screens.routines

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.NumericInput
import com.mooscles.designsystem.components.Stepper
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.format.spokenWeight
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.ds_reps_count
import com.mooscles.designsystem.resources.ds_stepper_decrease
import com.mooscles.designsystem.resources.ds_stepper_increase
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.editor_editing_warmup_load
import com.mooscles.screens.resources.editor_editing_warmup_reps
import com.mooscles.screens.resources.workout_editing_load
import com.mooscles.screens.resources.workout_editing_reps
import com.mooscles.screens.resources.workout_field_load
import com.mooscles.screens.resources.workout_field_reps
import com.mooscles.screens.resources.workout_open_keyboard
import com.mooscles.screens.resources.workout_step
import com.mooscles.screens.workout.MAX_LOAD_KG
import com.mooscles.screens.workout.MAX_REPS
import com.mooscles.screens.workout.loadStep
import com.mooscles.screens.workout.stepperPlain
import com.mooscles.screens.workout.unitSymbol
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.SetField
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/** The most planned sets an exercise of a routine can have (`EditRules.MAX_ROUTINE_SETS`). */
internal const val MAX_PLANNED_SETS = 50

/** Wide values a planned set's stepper may reach, for its arrangement ([Stepper]'s `widest`). */
private const val WIDEST_LOAD_KG = 997.5
private const val WIDEST_LOAD_LB = 1995.0
private const val WIDEST_REPS = "999"

/** The one write a valid edit asks for: [reps] (0: none planned) and [weightKg] (null: no load, or bodyweight). */
internal data class PlannedSave(val setId: String, val reps: Int, val weightKg: Double?)

/**
 * What the planned set rows have typed or stepped and not yet seen come back (plan decision 16). A row shows its
 * draft ([shown]) or else the saved value (read through [saved], the set as the state holds it now). The rules:
 *
 * - each valid [edit] or [step] is one write ([PlannedSave]), returned for the caller to send; an invalid draft
 *   (`82,`, `-`, over the bounds) is never written and the value standing for it is the last written or saved one;
 *   a blank load is no load (null) and blank reps are 0;
 * - kilograms stay exact behind pounds rounded for the eye: a text that is the rounded shown value of the exact kg
 *   means that kg, and a write that does not touch the load sends the exact kg it already had;
 * - a write pins both fields as drafts, so a late [acknowledge] of an older write (or of the record, after the person
 *   went on editing) cannot replace what the person sees; an acknowledgement that matches a written value ends the
 *   writes up to it, and one that arrives with nothing in flight is a change from elsewhere (a record refresh, a
 *   type change) that clears every draft of the set, an invalid one included;
 * - a [step] holds its write for [release], so a hold of the stepper writes once (the last value) when it ends.
 *
 * It lives in `remember`, never in saved state, and is keyed on the unit and the number format.
 */
internal class PlannedSetDrafts(
    private val pounds: Boolean,
    private val locale: NumberLocale,
    private val saved: (String) -> RoutineSetUi?,
) {
    /** What a field holds: its [text] and the exact [value] of it (kg or reps), null when the text is not valid. */
    private class Draft(val text: String, val value: Double?)

    private val drafts = mutableStateMapOf<Pair<String, SetField>, Draft>()

    /** The writes sent and not yet seen in the state, oldest first. */
    private val sent = HashMap<String, MutableList<PlannedSave>>()

    /** The write of a stepper run that has not been sent yet. */
    private val held = HashMap<String, PlannedSave>()

    private fun norm(kg: Double?): Double? = kg?.takeIf { it != 0.0 }

    /** [kg] as a field shows it in the unit of the editor; empty for no load. */
    fun loadText(kg: Double?): String =
        if (norm(kg) == null) "" else MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(kg!!, pounds)), locale)

    private fun repsText(reps: Int): String = if (reps == 0) "" else reps.toString()

    private fun savedText(set: RoutineSetUi, field: SetField): String =
        if (field == SetField.LOAD) loadText(set.weightKg) else repsText(set.reps)

    /** What the state holds for [set] once every write in flight lands. */
    private fun expected(set: RoutineSetUi): PlannedSave = sent[set.id]?.lastOrNull() ?: PlannedSave(set.id, set.reps, norm(set.weightKg))

    private fun expectedValue(set: RoutineSetUi, field: SetField): Double =
        expected(set).let { if (field == SetField.LOAD) it.weightKg ?: 0.0 else it.reps.toDouble() }

    /** The text [field] of [setId] shows: its draft or else the saved value. */
    fun shown(setId: String, field: SetField): String =
        drafts[setId to field]?.text ?: saved(setId)?.let { savedText(it, field) }.orEmpty()

    /** The exact value of [text] for [field] of [set] (kg or reps), or null when it is not valid. */
    private fun parse(set: RoutineSetUi, field: SetField, text: String): Double? = when {
        text.isEmpty() -> 0.0
        field == SetField.REPS -> NumericInput.parseInteger(text, MAX_REPS)?.toDouble()
        else -> {
            val standing = expected(set).weightKg
            if (standing != null && text == loadText(standing)) {
                standing
            } else {
                NumericInput.parseDecimal(text, max = WeightUnits.fromKg(MAX_LOAD_KG, pounds))?.let { WeightUnits.toKg(it, pounds) }
            }
        }
    }

    /** What [field] stands for now: its draft when valid, else the written or saved value. */
    private fun current(set: RoutineSetUi, field: SetField): Double = drafts[set.id to field]?.value ?: expectedValue(set, field)

    /** [text] typed in [field]: held as the draft and, when valid and new, the write to send. */
    fun edit(setId: String, field: SetField, text: String): PlannedSave? {
        val set = saved(setId) ?: return null
        drafts[setId to field] = Draft(text, parse(set, field, text))
        held.remove(setId)
        return if (drafts.getValue(setId to field).value == null) null else write(set)
    }

    /** Where the stepper of [field] stands, in the field's unit: the load as shown, or the reps. */
    fun stepperValue(setId: String, field: SetField): Double {
        val set = saved(setId) ?: return 0.0
        val value = current(set, field)
        return if (field == SetField.LOAD) MoosclesNumbers.plain(WeightUnits.fromKg(value, pounds)).toDouble() else value
    }

    /**
     * [delta] steps (2.5 kg, 5 lb or 1 rep each) from where the field stands, within 0 and the bound: held as the
     * draft and, when it changes the value, the write it stands for. The write is also held for [release].
     */
    fun step(setId: String, field: SetField, delta: Int): PlannedSave? {
        val set = saved(setId) ?: return null
        val base = stepperValue(setId, field)
        val load = field == SetField.LOAD
        val size = if (load) loadStep(pounds) else 1.0
        val top = if (load) MoosclesNumbers.plain(WeightUnits.fromKg(MAX_LOAD_KG, pounds)).toDouble() else MAX_REPS.toDouble()
        val next = (base + delta * size).coerceIn(0.0, top)
        if (next == base) return null
        drafts[setId to field] = if (load) {
            // The value of the text shown (hundredths), not of the sum, which may carry float noise.
            val plain = MoosclesNumbers.plain(next)
            Draft(MoosclesNumbers.localize(plain, locale), WeightUnits.toKg(plain.toDouble(), pounds))
        } else {
            Draft(next.toInt().toString(), next)
        }
        return write(set)?.also { held[setId] = it }
    }

    /** The write a run of steps left unsent, once: the end of the run asks for it. */
    fun release(setId: String): PlannedSave? = held.remove(setId)

    /** The write for the drafts as they stand, recorded as sent; null when the state will already hold it. */
    private fun write(set: RoutineSetUi): PlannedSave? {
        val standing = expected(set)
        val save = PlannedSave(set.id, current(set, SetField.REPS).toInt(), norm(current(set, SetField.LOAD)))
        if (save == standing) return null
        // From now on both fields are drafts: an acknowledgement of something older cannot change what is shown.
        for (field in SetField.entries) {
            if (drafts[set.id to field] == null) {
                drafts[set.id to field] = Draft(savedText(set, field), expectedValue(set, field))
            }
        }
        sent.getOrPut(set.id) { mutableListOf() } += save
        return save
    }

    /**
     * The state shows [set] (after a write of ours, or a change from elsewhere). A write it matches ends the writes
     * up to that one; with nothing in flight, the change is not ours and no draft of the set outlives it; with writes
     * in flight that it does not match it is older than them (or about something else) and changes nothing here.
     */
    fun acknowledge(set: RoutineSetUi) {
        val writes = sent[set.id]
        if (writes.isNullOrEmpty()) {
            dropDrafts(set.id)
            return
        }
        val index = writes.indexOfFirst { matches(it, set) }
        if (index >= 0) writes.subList(0, index + 1).clear()
        // Echoes the database folded into one (8, 80, 8 coming back as 8) leave the last write equal to what is saved.
        if (writes.isNotEmpty() && matches(writes.last(), set)) writes.clear()
    }

    private fun matches(write: PlannedSave, set: RoutineSetUi) = write.reps == set.reps && write.weightKg == norm(set.weightKg)

    /**
     * A write failed (an error arrived): nothing in flight will be echoed. The drafts stay (the person sees what was
     * typed until the row leaves the edit and [settle]s), but no write is waited for, so the next edit writes again
     * and the saved values are the truth. As the workout's `dropPendingWrites`.
     */
    fun forgetWrites() {
        sent.clear()
        held.clear()
    }

    /** The row left the edit: with nothing in flight its drafts only repeat the saved values (or are invalid) and go. */
    fun settle(setId: String) {
        val writes = sent[setId]
        val set = saved(setId)
        if (!writes.isNullOrEmpty() && set != null && matches(writes.last(), set)) writes.clear()
        if (writes.isNullOrEmpty()) dropDrafts(setId)
    }

    /** The set as "Undo" puts it back: the values as written (or in flight) and manual when a write is in flight. */
    fun snapshot(set: RoutineSetUi): RoutineSetUi {
        val standing = expected(set)
        val pending = !sent[set.id].isNullOrEmpty()
        return set.copy(reps = standing.reps, weightKg = standing.weightKg, inherited = set.inherited && !pending)
    }

    /** Forgets everything about [setId]: its drafts and its writes in flight. */
    fun drop(setId: String) {
        dropDrafts(setId)
        sent.remove(setId)
        held.remove(setId)
    }

    /** Forgets every set not in [ids] (removed here or elsewhere). */
    fun retain(ids: Set<String>) {
        (drafts.keys.map { it.first } + sent.keys + held.keys).distinct().filter { it !in ids }.forEach(::drop)
    }

    private fun dropDrafts(setId: String) {
        for (field in SetField.entries) drafts.remove(setId to field)
    }
}

/**
 * The set row being edited and what its field has: the field in edit, the keyboard on it, and where the rows are for
 * [endsPlannedEditingOnOutsideTap]. Only the set and the field are saved with the screen; the keyboard, the stepper's
 * focus request and the row areas are not.
 */
internal class PlannedEditing(setId: String? = null, field: SetField = SetField.LOAD) {
    var setId: String? by mutableStateOf(setId)
        private set
    var field: SetField by mutableStateOf(field)
        private set

    /** True while the field has the keyboard (a second tap, the keyboard button or a screen reader focusing it). */
    var keyboard: Boolean by mutableStateOf(false)

    /** True when the edit asks the stepper to take the focus, as a screen reader's action does. */
    var stepperFocus: Boolean by mutableStateOf(false)
        private set
    var stepperFocusRequests: Int by mutableIntStateOf(0)
        private set

    internal val rowAreas = HashMap<String, LayoutCoordinates>()
    internal var scopeArea: LayoutCoordinates? = null

    fun isEditing(id: String): Boolean = setId == id

    fun edit(id: String, field: SetField, keyboard: Boolean = false) {
        setId = id
        this.field = field
        this.keyboard = keyboard
        stepperFocus = false
    }

    fun editFromReader(id: String, field: SetField) {
        setId = id
        this.field = field
        keyboard = false
        stepperFocus = true
        stepperFocusRequests++
    }

    fun end() {
        setId = null
        keyboard = false
        stepperFocus = false
    }
}

@Composable
internal fun rememberPlannedEditing(): PlannedEditing = rememberSaveable(
    saver = listSaver(
        save = { listOf(it.setId.orEmpty(), it.field.name) },
        restore = { PlannedEditing(it[0].takeIf { id -> id.isNotEmpty() }, SetField.valueOf(it[1])) },
    ),
) { PlannedEditing() }

/**
 * The editing tools under a planned set in edit: the [Stepper] of the field in edit (2.5 kg, 5 lb or 1 rep, with
 * "step of 2.5 kg" under it) and the keyboard button (48 dp). Each step is held as a draft ([PlannedSetDrafts.step]);
 * the end of a run of steps ([PlannedSetDrafts.release]) writes once. It takes the focus when a screen reader asked.
 */
@Composable
internal fun PlannedStepperPanel(
    set: RoutineSetUi,
    number: Int,
    field: SetField,
    drafts: PlannedSetDrafts,
    editing: PlannedEditing,
    pounds: Boolean,
    onWrite: (PlannedSave) -> Unit,
    modifier: Modifier = Modifier,
) {
    val load = field == SetField.LOAD
    val locale = currentNumberLocale()
    val unit = unitSymbol(pounds)
    val step = if (load) loadStep(pounds) else 1.0
    val value = drafts.stepperValue(set.id, field)
    val stepPlain = MoosclesNumbers.plain(step)
    val stepText = if (load) MoosclesNumbers.weight(stepPlain, unit, locale) else stepPlain
    val stepSpoken = if (load) {
        spokenWeight(stepPlain, pounds, locale)
    } else {
        pluralCount(DsRes.plurals.ds_reps_count, 1)
    }
    val name = stringResource(if (load) Res.string.workout_field_load else Res.string.workout_field_reps)
    val requester = remember { FocusRequester() }
    LaunchedEffect(editing.setId, editing.field, editing.stepperFocusRequests) { if (editing.stepperFocus) requester.requestFocus() }
    Row(
        modifier.fillMaxWidth().padding(horizontal = MTheme.space.s2).padding(bottom = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Stepper(
                value = value,
                onValueChange = { next -> drafts.step(set.id, field, if (next > value) 1 else -1) },
                step = step,
                min = 0.0,
                decreaseLabel = stringResource(DsRes.string.ds_stepper_decrease, name, stepSpoken),
                increaseLabel = stringResource(DsRes.string.ds_stepper_increase, name, stepSpoken),
                numberLocale = locale,
                display = if (load) MoosclesNumbers.weight(stepperPlain(value), unit, locale) else value.toInt().toString(),
                max = if (load) MoosclesNumbers.plain(WeightUnits.fromKg(MAX_LOAD_KG, pounds)).toDouble() else MAX_REPS.toDouble(),
                onValueChangeFinished = { drafts.release(set.id)?.let(onWrite) },
                focusRequester = requester,
                contentDescription = stringResource(
                    when {
                        set.warmup && load -> Res.string.editor_editing_warmup_load
                        set.warmup -> Res.string.editor_editing_warmup_reps
                        load -> Res.string.workout_editing_load
                        else -> Res.string.workout_editing_reps
                    },
                    number,
                ),
                widest = if (load) MoosclesNumbers.weight(stepperPlain(if (pounds) WIDEST_LOAD_LB else WIDEST_LOAD_KG), unit, locale) else WIDEST_REPS,
            )
            MText(stringResource(Res.string.workout_step, stepText), MTheme.type.caption, color = MTheme.colors.textSecondary)
        }
        MIconButton(MIcons.Keyboard, stringResource(Res.string.workout_open_keyboard), onClick = { editing.edit(set.id, field, keyboard = true) })
    }
}

/**
 * Put around the list of planned sets: a tap outside the row in edit (and its stepper) ends the edit, and so closes the
 * keyboard. It watches without consuming, so the tap still does what it does there. A press that moves past touch slop
 * is a scroll, not a tap.
 */
internal fun Modifier.endsPlannedEditingOnOutsideTap(editing: PlannedEditing): Modifier = this
    .onGloballyPositioned { editing.scopeArea = it }
    .pointerInput(editing) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val id = editing.setId ?: return@awaitEachGesture
            val scope = editing.scopeArea
            val row = editing.rowAreas[id]
            val inside = scope != null && row != null && scope.isAttached && row.isAttached &&
                scope.localBoundingBoxOf(row).contains(down.position)
            if (!inside && awaitTap(down) && editing.setId == id) editing.end()
        }
    }

/** True when the press of [down] lifts within touch slop, without the pointer moving away first. */
private suspend fun AwaitPointerEventScope.awaitTap(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.fastAny { it.id == down.id && (it.position - down.position).getDistance() > slop }) return false
        if (event.changes.fastAll { !it.pressed }) return true
    }
}
