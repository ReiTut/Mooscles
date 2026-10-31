package com.mooscles.screens.workout

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
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
import com.mooscles.screens.resources.workout_editing_load
import com.mooscles.screens.resources.workout_editing_reps
import com.mooscles.screens.resources.workout_field_load
import com.mooscles.screens.resources.workout_field_reps
import com.mooscles.screens.resources.workout_open_keyboard
import com.mooscles.screens.resources.workout_step
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/** The bounds the database accepts for a set (`EditRules.workoutSet`); a completed set needs at least 1 rep. */
internal const val MAX_REPS = 9999
internal const val MAX_LOAD_KG = 100_000.0

/** Wide values a set's stepper may reach, for its arrangement ([com.mooscles.designsystem.components.Stepper]'s `widest`). */
private const val WIDEST_LOAD_KG = 997.5
private const val WIDEST_LOAD_LB = 1995.0
private const val WIDEST_REPS = "999"

/** The most sets an exercise can have in a workout (`EditRules.MAX_WORKOUT_SETS`). */
internal const val MAX_SETS = 100

/** One stepper step of the load: 2.5 kg, or 5 lb in pounds. */
internal fun loadStep(pounds: Boolean): Double = if (pounds) 5.0 else 2.5

/** A load in a field's unit with at least one decimal, as the stepper shows it: `85.0`, `87.5`, `82.25`. */
internal fun stepperPlain(value: Double): String = MoosclesNumbers.plain(value).let { if ('.' in it) it else "$it.0" }

/**
 * The values of one set row and every write from it, read at the moment of the call (a callback
 * can run before the recomposition that would replace it): the drafts come from [screen], the
 * saved values from [set]. [suggestion] is what the row shows in its empty fields.
 *
 * A field shows its draft or else the saved value ([shown]); a draft's value is exact ([value]):
 * the saved text is the saved kg, the text of a value written for it is that value, so "80" lb
 * stays the 36.287389 kg it came from and a converted draft keeps its kg. Each valid value typed
 * or stepped is written ([save]) unless the database will already hold it; an invalid draft is
 * never written and the last valid value stands. Reps of 0 on a done set wait as a draft, as
 * the database would refuse them, and the last valid reps are written with the other field.
 */
internal class SetRowEditor(
    val set: SetUi,
    val pounds: Boolean,
    private val suggestion: SetSuggestion,
    private val screen: ActiveWorkoutScreenState,
    private val actions: MoosclesActions,
    private val locale: NumberLocale,
) {
    private val maxLoad = WeightUnits.fromKg(MAX_LOAD_KG, pounds)

    /** [kg] as a field shows it in [pounds]: "82,5", "80". */
    fun loadText(kg: Double, pounds: Boolean = this.pounds): String =
        MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), locale)

    /** The saved value of [field]: kg or reps. */
    fun saved(field: SetField): Double = if (field == SetField.LOAD) set.weightKg else set.reps.toDouble()

    /** The saved value as a field shows it; nothing for 0. */
    fun savedText(field: SetField): String = when (field) {
        SetField.LOAD -> if (blankLoad(set.weightKg)) "" else loadText(set.weightKg)
        SetField.REPS -> if (blankReps(set.reps)) "" else set.reps.toString()
    }

    fun draft(field: SetField): String? = screen.drafts[set.id to field]

    fun shown(field: SetField): String = draft(field) ?: savedText(field)

    /** The exact value of [text] in [field], or null when it is not a valid one. The empty text is 0. */
    fun value(field: SetField, text: String): Double? = when {
        text.isEmpty() -> 0.0
        field == SetField.REPS -> NumericInput.parseInteger(text, MAX_REPS)?.toDouble()
        text == savedText(SetField.LOAD) -> set.weightKg
        else -> screen.sent[set.id to field]?.lastOrNull { loadText(it) == text }
            ?: NumericInput.parseDecimal(text, max = maxLoad)?.let { WeightUnits.toKg(it, pounds) }
    }

    /** What stands for [field] when its text is invalid: the last value written, or the saved one. */
    fun standing(field: SetField): Double = expected(field)

    /** The value of the draft of [field], null without a draft or when it is invalid. */
    fun draftValue(field: SetField): Double? = draft(field)?.let { value(field, it) }

    private fun expected(field: SetField): Double = screen.expected(set.id, field, saved(field))

    /** What [field] stands for now: the shown value, or the last valid one when the draft is invalid. */
    fun current(field: SetField): Double = value(field, shown(field)) ?: expected(field)

    /** Whether the set is drawn as done (the optimistic value first). */
    fun done(): Boolean = screen.completedOf(set)

    /** The screen reader's requests to edit, for the stepper's focus. */
    val stepperFocusRequests: Int get() = screen.stepperFocusRequests

    /** [text] typed in [field]: held as the draft and, when valid, written. */
    fun type(field: SetField, text: String) {
        val before = shown(field)
        screen.type(set.id, field, text)
        if (text.isEmpty() && before.isNotEmpty()) screen.clear(set.id, field)
        val typed = value(field, text) ?: return
        val editing = screen.editing
        if (field == SetField.REPS && typed > 0 && editing != null && editing.setId == set.id && editing.error) {
            screen.editing = editing.copy(error = false)
        }
        save()
    }

    /** A stepper step of [field] to [value] (in the field's unit): held as the draft; [save] writes it when the run of steps ends. */
    fun step(field: SetField, value: Double) {
        val text = when (field) {
            SetField.LOAD -> MoosclesNumbers.localize(MoosclesNumbers.plain(value), locale)
            SetField.REPS -> value.toInt().toString()
        }
        screen.type(set.id, field, text)
    }

    /** Where the stepper of [field] starts, in the field's unit: the shown value, the suggestion of an empty field, or 0. */
    fun stepperValue(field: SetField): Double {
        val text = shown(field)
        return when (field) {
            SetField.LOAD -> {
                val kg = if (text.isEmpty()) suggestion.weightKg ?: 0.0 else value(field, text) ?: expected(field)
                MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)).toDouble()
            }
            SetField.REPS -> if (text.isEmpty()) (suggestion.reps ?: 0).toDouble() else value(field, text) ?: expected(field)
        }
    }

    /**
     * Writes the shown values, unless the database will already hold them. A done set with no
     * reps would be refused: like an invalid draft, those reps wait and the last valid ones are
     * written, so a new load on that set is still saved (D12: the last valid value is written).
     */
    fun save() {
        val kg = current(SetField.LOAD)
        val reps = writtenReps()
        if (kg == expected(SetField.LOAD) && reps.toDouble() == expected(SetField.REPS)) return
        send(reps, kg, done())
    }

    /** The reps a write sends now: the shown ones, or on a done set showing none the last valid ones ([save]). */
    private fun writtenReps(): Int = current(SetField.REPS).toInt().let { if (it == 0 && done()) expected(SetField.REPS).toInt() else it }

    /**
     * Writes the set with the type [warmup] and [assistance], with its values as of now (the
     * drafts as [save] would write them, the done state as drawn), so the change cannot put back
     * an older value while a write is in flight.
     */
    fun changeType(warmup: Boolean, assistance: Boolean) {
        send(writtenReps(), current(SetField.LOAD), done(), warmup, assistance)
    }

    /**
     * The set as it stands now, for "Undo" to put back: its values as [save] writes them (each
     * valid draft is already written; an invalid one gives way to the last valid value) and the
     * done state as drawn, so a check or a value still in flight is not lost by the undo.
     */
    fun snapshot(): SetUi = set.copy(weightKg = current(SetField.LOAD), reps = writtenReps(), completed = done())

    /** Sends [reps] and [kg] as the set's values, noting each one that changes, so its echo is awaited. */
    fun send(reps: Int, kg: Double, completed: Boolean, warmup: Boolean = set.warmup, assistance: Boolean = set.assistance) {
        if (kg != expected(SetField.LOAD)) screen.sent(set.id, SetField.LOAD, kg)
        if (reps.toDouble() != expected(SetField.REPS)) screen.sent(set.id, SetField.REPS, reps.toDouble())
        actions.updateSet(set.id, reps, kg, completed, warmup, assistance)
    }

    /** The database shows [field] as now saved, after [previous]. */
    fun echo(field: SetField, previous: Double?) = screen.echo(set.id, field, saved(field), previous, draftValue(field))

    /** The row left the edit: a draft that only repeats the saved value goes. */
    fun settleDraft(field: SetField) = screen.settleDraft(set.id, field, saved(field), draftValue(field))

    /** Asks for the keyboard on [field]. */
    fun openKeyboard(field: SetField) {
        val editing = screen.editing
        screen.editing = SetEditTarget(set.id, field, keyboard = true, error = editing?.takeIf { it.setId == set.id && it.field == field }?.error == true)
    }
}

/**
 * The editing tools under a row in edit: the [Stepper] of [target]'s field (2.5 kg, 5 lb or 1 rep,
 * with "step of 2.5 kg" under it) and the keyboard button (48 dp). Each step changes the draft;
 * the end of a run of steps (a tap, or lifting after a hold) writes once. The stepper is named
 * "Editing load of set 4" and takes the focus when [SetEditTarget.stepperFocus] asks (D21).
 *
 * The brief's signature took the set, the unit, the screen state and the actions; they all come
 * with [editor], which every write of the row goes through, plus the set's [number].
 */
@Composable
internal fun SetEditPanel(
    target: SetEditTarget,
    number: Int,
    editor: SetRowEditor,
    modifier: Modifier = Modifier,
) {
    val field = target.field
    val load = field == SetField.LOAD
    val pounds = editor.pounds
    val locale = currentNumberLocale()
    val unit = unitSymbol(pounds)
    val step = if (load) loadStep(pounds) else 1.0
    val value = editor.stepperValue(field)
    val stepPlain = MoosclesNumbers.plain(step)
    val stepText = if (load) MoosclesNumbers.weight(stepPlain, unit, locale) else stepPlain
    val stepSpoken = if (load) {
        spokenWeight(stepPlain, pounds, locale)
    } else {
        pluralCount(DsRes.plurals.ds_reps_count, 1)
    }
    val name = stringResource(if (load) Res.string.workout_field_load else Res.string.workout_field_reps)
    val requester = remember { FocusRequester() }
    // Keyed on the requests too: the same action again moves the focus back to the stepper.
    LaunchedEffect(target, editor.stepperFocusRequests) { if (target.stepperFocus) requester.requestFocus() }
    Row(
        modifier.fillMaxWidth().padding(horizontal = MTheme.space.s2).padding(bottom = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Stepper(
                value = value,
                onValueChange = { editor.step(field, it) },
                step = step,
                // A done set needs at least 1 rep.
                min = if (!load && editor.done()) 1.0 else 0.0,
                decreaseLabel = stringResource(DsRes.string.ds_stepper_decrease, name, stepSpoken),
                increaseLabel = stringResource(DsRes.string.ds_stepper_increase, name, stepSpoken),
                numberLocale = locale,
                display = if (load) MoosclesNumbers.weight(stepperPlain(value), unit, locale) else value.toInt().toString(),
                max = if (load) MoosclesNumbers.plain(WeightUnits.fromKg(MAX_LOAD_KG, pounds)).toDouble() else MAX_REPS.toDouble(),
                onValueChangeFinished = { editor.save() },
                focusRequester = requester,
                contentDescription = stringResource(if (load) Res.string.workout_editing_load else Res.string.workout_editing_reps, number),
                // A wide value it may step to, so stepping never moves the buttons (the stepper keeps one arrangement).
                widest = if (load) MoosclesNumbers.weight(stepperPlain(if (pounds) WIDEST_LOAD_LB else WIDEST_LOAD_KG), unit, locale) else WIDEST_REPS,
            )
            MText(stringResource(Res.string.workout_step, stepText), MTheme.type.caption, color = MTheme.colors.textSecondary)
        }
        MIconButton(MIcons.Keyboard, stringResource(Res.string.workout_open_keyboard), onClick = { editor.openKeyboard(field) })
    }
}

/**
 * Over a field that is not the one in edit, takes the first tap ([onTap]) so it puts the row in
 * edit without focusing the field: the keyboard opens only on a second tap (D12). Without [armed]
 * the field gets its taps. It has no semantics: a screen reader reaches the field itself.
 */
@Composable
internal fun FieldTapGuard(armed: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier, field: @Composable () -> Unit) {
    val currentTap by rememberUpdatedState(onTap)
    Box(modifier) {
        field()
        if (armed) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { currentTap() }) })
    }
}

/**
 * Put on the screen around the set rows: a tap outside the row in edit (and its panel) ends the
 * edit, and so closes the keyboard (D12). It watches without consuming, so the tap still does
 * what it does there: a tap on another row's field puts that row in edit. A press that moves past
 * touch slop is a scroll, not a tap.
 */
internal fun Modifier.endsSetEditingOnOutsideTap(screen: ActiveWorkoutScreenState): Modifier = this
    .onGloballyPositioned { screen.scopeArea = it }
    .pointerInput(screen) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val editing = screen.editing ?: return@awaitEachGesture
            val scope = screen.scopeArea
            val row = screen.rowAreas[editing.setId]
            val inside = scope != null && row != null && scope.isAttached && row.isAttached &&
                scope.localBoundingBoxOf(row).contains(down.position)
            if (!inside && awaitTap(down) && screen.editing?.setId == editing.setId) screen.editing = null
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
