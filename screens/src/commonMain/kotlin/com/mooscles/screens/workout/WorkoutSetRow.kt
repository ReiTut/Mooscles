package com.mooscles.screens.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import com.mooscles.designsystem.components.ExerciseCardFooter
import com.mooscles.designsystem.components.NumericField
import com.mooscles.designsystem.components.NumericKind
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowCells
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.setRowSwipe
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_enter_reps
import com.mooscles.screens.resources.workout_load_of_set
import com.mooscles.screens.resources.workout_reps_of_set
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource

/** The test tag of the [WorkoutSetRow] of [setId]. */
internal fun workoutSetRowTag(setId: String): String = "workout-set-$setId"

/**
 * One set of the active workout, drawn with the design system's [SetRow], and, while it is being
 * edited, the [SetEditPanel] under it.
 *
 * [suggestion] is what [SetSuggestions.forExercise] gives this set and [previous] its pair in the
 * previous session ([SetSuggestions.pairPrevious]); [number] is its 1-based position among the
 * [total] sets of [entry]. Every write goes through [actions], by way of a [SetRowEditor]:
 *
 * - the check, with valid reps (saved or suggested), draws the disc at once ([ActiveWorkoutScreenState.optimistic])
 *   and sends `updateSet` with the effective values, the load in the source's exact kg; without
 *   valid reps it vibrates `Rejected` and opens the reps in edit with the "Enter the reps" error;
 * - unchecking sends the same set with `completed = false`;
 * - tapping the previous result of a pending set copies it (load and reps, not done) with the
 *   `StepperStep` haptic; on a done set it is not a target;
 * - tapping the set's number (or the "change type" action) asks [onChangeType] for its type
 *   sheet ([SetTypeSheetHost]);
 * - swiping right is the check (only for a pending set); swiping left (or the "remove" action)
 *   asks [onRemove] with the set as it stands now ([SetRowEditor.snapshot]: what "Undo" puts
 *   back) and its position in [entry];
 * - each valid value typed or stepped in a field is written; emptying a field marks it in
 *   [ActiveWorkoutScreenState.cleared], so it gets no suggestion again.
 *
 * Editing (D12): the first tap on a field puts the row in edit, the field selected but not
 * focused, with that field's stepper under the row; a second tap on it, or the keyboard button,
 * focuses it and so opens the keyboard (decimal for the load, numeric for the reps). The load's
 * "Next" goes to the reps; the reps' "Done" only closes the keyboard. The check, a tap outside
 * ([endsSetEditingOnOutsideTap]) or another row ends the edit. A screen reader's "Edit set",
 * "Edit load" and "Edit reps" put the row in edit and move the focus to the stepper (D21). The row
 * in edit, with its panel, is brought into view, above the keyboard too.
 *
 * What the person typed is a draft in [ActiveWorkoutScreenState.drafts], so it survives the row
 * leaving composition and the screen's recreation: a late echo does not erase a newer draft, an
 * invalid draft is neither sent nor lost, and switching kg and lb rewrites the draft from its exact
 * kg ([ActiveWorkoutScreenState.echo], [ActiveWorkoutScreenState.useUnit]).
 *
 * The record haptic stays off: the screen plays it from its own record event ([RecordReaction]),
 * once per record, so a row scrolled away and back or recreated never plays it again. The badge
 * is the row's: `CompletedRecord` while the set is done with a `SetUi.record`, entering with
 * `spring.pr` only when this row sees the record arrive. [onCells] gets
 * where the row's cells sit after each layout, for the card's column titles ([SetRowColumns]).
 */
@Composable
internal fun WorkoutSetRow(
    set: SetUi,
    number: Int,
    total: Int,
    entry: WorkoutExerciseUi,
    suggestion: SetSuggestion,
    previous: PreviousSetUi?,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
    onChangeType: (SetUi) -> Unit,
    onRemove: (SetUi, Int) -> Unit,
    modifier: Modifier = Modifier,
    onCells: ((SetRowCells) -> Unit)? = null,
) {
    RecompositionProbe("row-${set.id}")
    val haptics = MTheme.haptics
    // Read through derived states, so a change for another set does not recompose this row.
    val optimistic by remember(screen, set.id) { derivedStateOf { screen.optimistic[set.id] } }
    val doneNow by remember(screen, set) { derivedStateOf { screen.completedOf(set) } }
    val editingThis by remember(screen, set.id) { derivedStateOf { screen.editing?.takeIf { it.setId == set.id } } }
    val loadDraft by remember(screen, set.id) { derivedStateOf { screen.drafts[set.id to SetField.LOAD] } }
    val repsDraft by remember(screen, set.id) { derivedStateOf { screen.drafts[set.id to SetField.REPS] } }
    val editing = editingThis
    val done = doneNow
    LaunchedEffect(set.id, set.completed, optimistic) {
        if (optimistic != null) screen.settle(set.id, set.completed)
    }

    val locale = currentNumberLocale()
    val editor = SetRowEditor(set, pounds, suggestion, screen, actions, locale)
    // First, so the echoes below read drafts already in the shown unit.
    LaunchedEffect(pounds, locale) { screen.useUnit(pounds) { kg, unit -> editor.loadText(kg, unit) } }
    // The values the database held before, to tell a change from elsewhere (NaN: not seen yet).
    val seen = remember(set.id) { doubleArrayOf(Double.NaN, Double.NaN) }
    LaunchedEffect(set.id, set.weightKg) {
        editor.echo(SetField.LOAD, seen[0].takeUnless { it.isNaN() })
        seen[0] = set.weightKg
    }
    LaunchedEffect(set.id, set.reps) {
        editor.echo(SetField.REPS, seen[1].takeUnless { it.isNaN() })
        seen[1] = set.reps.toDouble()
    }
    LaunchedEffect(set.id, editing == null) {
        if (editing == null) {
            editor.settleDraft(SetField.LOAD)
            editor.settleDraft(SetField.REPS)
        }
    }

    val shownLoad = loadDraft ?: editor.savedText(SetField.LOAD)
    val shownReps = repsDraft ?: editor.savedText(SetField.REPS)
    // An invalid draft is neither sent nor used: the last valid value stands.
    val loadKg = editor.value(SetField.LOAD, shownLoad) ?: editor.standing(SetField.LOAD)
    val reps = (editor.value(SetField.REPS, shownReps) ?: editor.standing(SetField.REPS)).toInt()
    // A suggestion fills only a field that shows nothing.
    val loadSuggestion = suggestion.weightKg.takeIf { shownLoad.isEmpty() }
    val repsSuggestion = suggestion.reps.takeIf { shownReps.isEmpty() }
    val effective = SetSuggestions.effective(set.copy(weightKg = loadKg, reps = reps), SetSuggestion(loadSuggestion, repsSuggestion))

    val complete: () -> Unit = {
        // Read now, not from the composition: a draft typed just before still counts.
        val loadText = editor.shown(SetField.LOAD)
        val repsText = editor.shown(SetField.REPS)
        val now = SetSuggestions.effective(
            set.copy(weightKg = editor.current(SetField.LOAD), reps = editor.current(SetField.REPS).toInt()),
            SetSuggestion(suggestion.weightKg.takeIf { loadText.isEmpty() }, suggestion.reps.takeIf { repsText.isEmpty() }),
        )
        if (now.reps in 1..MAX_REPS) {
            screen.expect(set.id, true)
            // Its completion, once the state shows it, may be celebrated ([RecordReaction]).
            screen.checkedHere(set.id)
            screen.editing = null
            // An empty or invalid draft gave way to the value sent; the echo shows that value.
            for (field in SetField.entries) {
                val text = editor.shown(field)
                if (text.isEmpty() || editor.value(field, text) == null) screen.dropDraft(set.id, field)
            }
            editor.send(now.reps, now.weightKg, completed = true)
        } else {
            haptics.perform(HapticEvent.Rejected)
            screen.editing = SetEditTarget(set.id, SetField.REPS, error = true)
        }
    }
    val uncheck: () -> Unit = {
        screen.expect(set.id, false)
        screen.uncheckedHere(set.id)
        screen.editing = null
        editor.send(editor.current(SetField.REPS).toInt(), editor.current(SetField.LOAD), completed = false)
    }
    val copyPrevious: (() -> Unit)? = if (previous != null && !done) {
        {
            haptics.perform(HapticEvent.StepperStep)
            screen.dropDraft(set.id, SetField.LOAD)
            screen.dropDraft(set.id, SetField.REPS)
            editor.send(previous.reps, previous.weightKg, completed = false)
        }
    } else {
        null
    }

    // Focus: which field of this row has it, and the keyboard follows the edit target.
    val focusManager = LocalFocusManager.current
    val loadFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focused = remember { arrayOfNulls<SetField>(1) }
    LaunchedEffect(editing) {
        val target = editing
        if (target != null && target.keyboard) {
            if (focused[0] != target.field) (if (target.field == SetField.LOAD) loadFocus else repsFocus).requestFocus()
        } else if (focused[0] != null) {
            focusManager.clearFocus()
        }
    }
    val focusChanged: (SetField, Boolean) -> Unit = { field, has ->
        if (has != (focused[0] == field)) {
            val current = screen.editing
            val here = current?.takeIf { it.setId == set.id && it.field == field }
            if (has) {
                focused[0] = field
                if (here == null || !here.keyboard) screen.editing = SetEditTarget(set.id, field, keyboard = true, error = here?.error == true)
            } else {
                focused[0] = null
                if (here != null && here.keyboard) screen.editing = here.copy(keyboard = false)
            }
        }
    }
    val closeKeyboard: () -> Unit = {
        screen.editing?.takeIf { it.setId == set.id }?.let { screen.editing = it.copy(keyboard = false) }
        focusManager.clearFocus()
    }
    // A field's first tap edits without the keyboard; with the keyboard open, the row's other field takes it at once.
    val armed: (SetField) -> Boolean = { field -> !(editing != null && (editing.field == field || editing.keyboard)) }
    val edit: (SetField) -> Unit = { field -> screen.editing = SetEditTarget(set.id, field) }
    val editFromReader: (SetField) -> Unit = { field -> screen.editFromReader(set.id, field) }

    // The row in edit, with its panel, stays in view: when it enters the edit, when the panel
    // grows and when the keyboard (or anything at the bottom) changes the space.
    val bringIntoView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    LaunchedEffect(editing != null) {
        if (editing != null) snapshotFlow { ime.getBottom(density) to height }.collect { bringIntoView.bringIntoView() }
    }
    DisposableEffect(screen, set.id) { onDispose { screen.rowAreas.remove(set.id) } }
    // A request on the row goes to its first focusable control (the set's number), never a field.
    val rowFocus = remember { FocusRequester() }
    DisposableEffect(screen, set.id) {
        screen.rowFocus[set.id] = rowFocus
        onDispose { if (screen.rowFocus[set.id] === rowFocus) screen.rowFocus.remove(set.id) }
    }

    val status = when {
        !done -> SetStatus.Pending
        set.completed && set.record != null -> SetStatus.CompletedRecord
        else -> SetStatus.Completed
    }
    val type = setTypeOf(set, entry.exercise.convention)
    val state = SetRowState(
        index = number,
        total = total,
        // Null without a pair: the design system then draws "—" and reads the row without it.
        previous = previous?.let { previousText(it, pounds) },
        previousSpoken = previous?.let { previousSpoken(it, pounds) },
        load = loadText(effective.weightKg, pounds),
        loadSpoken = loadSpoken(effective.weightKg, pounds),
        reps = effective.reps,
        type = type,
        status = status,
        loadSuggested = loadSuggestion != null,
        repsSuggested = repsSuggestion != null,
        editing = editing != null,
    )
    val position = number - 1
    val motion = MTheme.motion
    val reduced = MTheme.reducedMotion
    val resize = if (reduced) snap<IntSize>() else tween(motion.base, easing = motion.easeOut)
    val background by animateColorAsState(
        if (editing != null) MTheme.colors.bgInset else Color.Transparent,
        if (reduced) snap() else tween(motion.base, easing = motion.easeOut),
    )
    // The row and its panel share the edit background (bg.inset, F38), so they read as one block.
    Column(
        modifier
            .onGloballyPositioned { screen.rowAreas[set.id] = it }
            .focusRequester(rowFocus)
            .focusGroup()
            .onSizeChanged { height = it.height }
            .bringIntoViewRequester(bringIntoView)
            .background(background, RoundedCornerShape(MTheme.radius.md))
            .animateContentSize(resize),
    ) {
        SetRow(
            state = state,
            onCompleteChange = { if (it) complete() else uncheck() },
            onEditLoad = { editFromReader(SetField.LOAD) },
            onEditReps = { editFromReader(SetField.REPS) },
            onChangeType = { onChangeType(set) },
            onRemove = { onRemove(editor.snapshot(), position) },
            modifier = Modifier
                .testTag(workoutSetRowTag(set.id))
                .setRowSwipe(
                    completeEnabled = !done,
                    removeEnabled = true,
                    onComplete = complete,
                    onRemove = { onRemove(editor.snapshot(), position) },
                ),
            loadField = {
                FieldTapGuard(
                    armed = armed(SetField.LOAD),
                    onTap = { edit(SetField.LOAD) },
                    modifier = Modifier.onFocusChanged { focusChanged(SetField.LOAD, it.hasFocus) },
                ) {
                    NumericField(
                        value = shownLoad,
                        onValueChange = { editor.type(SetField.LOAD, it) },
                        kind = NumericKind.Load,
                        accessibilityLabel = stringResource(Res.string.workout_load_of_set, number),
                        suggested = loadSuggestion?.let { loadText(it, pounds) },
                        selected = editing?.field == SetField.LOAD,
                        imeAction = ImeAction.Next,
                        onImeAction = { repsFocus.requestFocus() },
                        focusRequester = loadFocus,
                    )
                }
            },
            repsField = {
                FieldTapGuard(
                    armed = armed(SetField.REPS),
                    onTap = { edit(SetField.REPS) },
                    modifier = Modifier.onFocusChanged { focusChanged(SetField.REPS, it.hasFocus) },
                ) {
                    NumericField(
                        value = shownReps,
                        onValueChange = { editor.type(SetField.REPS, it) },
                        kind = NumericKind.Reps,
                        accessibilityLabel = stringResource(Res.string.workout_reps_of_set, number),
                        suggested = repsSuggestion?.toString(),
                        error = if (editing?.field == SetField.REPS && editing.error) stringResource(Res.string.workout_enter_reps) else null,
                        selected = editing?.field == SetField.REPS,
                        imeAction = ImeAction.Done,
                        onImeAction = closeKeyboard,
                        focusRequester = repsFocus,
                    )
                }
            },
            onEditRow = { editFromReader(SetField.LOAD) },
            onCopyPrevious = copyPrevious,
            recordHaptic = false,
            onCells = onCells,
        )
        if (editing != null) SetEditPanel(editing, number, editor)
    }
}

/**
 * The bottom of an exercise card: "Add set", which sends `addSet` (the repository copies the
 * last set's load and assistance), disabled once the exercise has [MAX_SETS] sets.
 */
@Composable
internal fun AddSetFooter(entry: WorkoutExerciseUi, actions: MoosclesActions, modifier: Modifier = Modifier) {
    ExerciseCardFooter(
        onAddSet = { actions.addSet(entry.id) },
        modifier = modifier,
        addEnabled = entry.sets.size < MAX_SETS,
    )
}

/**
 * How a set row's list item enters and moves: a new row (an added set, a set put back) fades in
 * and the rows around it slide with `spring.snappy`; with reduced motion, a `motion.fast` fade
 * and no slide. A removed row leaves at once: the undo toast is its feedback, and a row fading
 * out would still take touches.
 */
@Composable
internal fun LazyItemScope.setRowItemMotion(): Modifier {
    val motion = MTheme.motion
    val snappy = motion.springSnappy
    return if (MTheme.reducedMotion) {
        Modifier.animateItem(fadeInSpec = tween(motion.fast, easing = motion.easeOut), placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem(
            fadeInSpec = spring(snappy.dampingRatio, snappy.stiffness),
            placementSpec = spring(snappy.dampingRatio, snappy.stiffness),
            fadeOutSpec = null,
        )
    }
}
