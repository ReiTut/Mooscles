package com.mooscles.screens.routines

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.NumericField
import com.mooscles.designsystem.components.NumericKind
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.editor_set_bodyweight
import com.mooscles.screens.resources.editor_set_follows_record
import com.mooscles.screens.resources.editor_set_label
import com.mooscles.screens.resources.editor_set_no_load
import com.mooscles.screens.resources.editor_set_warmup_label
import com.mooscles.screens.resources.editor_set_warmup_short
import com.mooscles.screens.resources.editor_warmup_load_of_set
import com.mooscles.screens.resources.editor_warmup_reps_of_set
import com.mooscles.screens.resources.workout_load_of_set
import com.mooscles.screens.resources.workout_reps_of_set
import com.mooscles.screens.workout.FieldTapGuard
import com.mooscles.screens.workout.unitSymbol
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.SetField
import org.jetbrains.compose.resources.stringResource

/** From this font scale on a planned set is always two lines: the label on top, load and reps below (as the workout's set row). */
private const val TWO_LINE_FONT_SCALE = 1.5f

/** What a blank number of repetitions shows. */
private const val BLANK_REPS = "—"

/**
 * One planned set of the routine editor: its label (the number of a working set, "W"/"A" for a warm-up; it opens the
 * set's menu), the record icon when it [RoutineSetUi.inherited] follows the exercise's record, and its load and
 * repetitions as [NumericField]s, with the stepper of the field in edit under the row.
 *
 * Editing (as the workout's rows, D12): the first tap on a field puts the row in edit with that field's stepper under
 * it and the keyboard closed; a second tap on it, or the keyboard button, opens the keyboard. Every valid text typed is
 * one write ([onWrite]); a run of steps writes once, when it ends. A write makes the set manual (the repository does it;
 * the icon goes when the state shows it). Blank load is "No load" (or "Bodyweight" for an exercise logged with the
 * body's weight), blank reps are "—" and stand for 0. The row in edit is brought into view, above the keyboard too.
 *
 * [number] numbers the set among its kind (working sets and warm-ups are counted apart), and names its fields. The row is not one node: its label and fields stay their own, so a
 * screen reader reaches and edits them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannedSetRow(
    set: RoutineSetUi,
    entry: PlannedExerciseUi,
    number: Int,
    pounds: Boolean,
    drafts: PlannedSetDrafts,
    editing: PlannedEditing,
    onWrite: (PlannedSave) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editingThis = editing.isEditing(set.id)
    val target = if (editingThis) editing.field else null

    // The state shows this set (after our write or a change from elsewhere), then the drafts that only repeat it go.
    LaunchedEffect(set) { drafts.acknowledge(set) }
    LaunchedEffect(set, editingThis) { if (!editingThis) drafts.settle(set.id) }
    // A run of steps cut short by the row leaving composition is still the person's last step: it is written.
    DisposableEffect(drafts, set.id) {
        onDispose {
            drafts.release(set.id)?.let(onWrite)
            editing.rowAreas.remove(set.id)
        }
    }

    val focusManager = LocalFocusManager.current
    val loadFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focused = remember { arrayOfNulls<SetField>(1) }
    val keyboardField = if (editingThis && editing.keyboard) editing.field else null
    LaunchedEffect(editingThis, editing.field, editing.keyboard) {
        if (keyboardField != null) {
            if (focused[0] != keyboardField) (if (keyboardField == SetField.LOAD) loadFocus else repsFocus).requestFocus()
        } else if (focused[0] != null) {
            focusManager.clearFocus()
        }
    }
    val focusChanged: (SetField, Boolean) -> Unit = { field, has ->
        if (has != (focused[0] == field)) {
            val here = editing.isEditing(set.id) && editing.field == field
            if (has) {
                focused[0] = field
                if (!here || !editing.keyboard) editing.edit(set.id, field, keyboard = true)
            } else {
                focused[0] = null
                if (here && editing.keyboard) editing.keyboard = false
            }
        }
    }
    val closeKeyboard: () -> Unit = {
        if (editing.isEditing(set.id)) editing.keyboard = false
        focusManager.clearFocus()
    }
    // A field's first tap edits without the keyboard; with the keyboard open, the row's other field takes it at once.
    val armed: (SetField) -> Boolean = { field -> !(editingThis && (editing.field == field || editing.keyboard)) }

    // The row in edit, with its stepper, stays in view: when it enters the edit, when the panel grows and when the
    // keyboard (or anything at the bottom) changes the space.
    val bringIntoView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    LaunchedEffect(editingThis) {
        if (editingThis) snapshotFlow { ime.getBottom(density) to height }.collect { bringIntoView.bringIntoView() }
    }

    val loadText = drafts.shown(set.id, SetField.LOAD)
    val repsText = drafts.shown(set.id, SetField.REPS)
    val noLoad = stringResource(
        if (LoadConvention.fromLabelOrName(entry.exercise.convention) == LoadConvention.BODYWEIGHT) {
            Res.string.editor_set_bodyweight
        } else {
            Res.string.editor_set_no_load
        },
    )
    val motion = MTheme.motion
    val background by animateColorAsState(
        if (editingThis) MTheme.colors.bgInset else Color.Transparent,
        if (MTheme.reducedMotion) snap() else tween(motion.base, easing = motion.easeOut),
    )
    val resize = if (MTheme.reducedMotion) snap<IntSize>() else tween(motion.base, easing = motion.easeOut)

    val label: @Composable () -> Unit = { SetLabel(set, number, onMenu) }
    // On one line the icon's place is kept on every row, so the load and reps of a card's sets stand in one column
    // whether a set follows the record or not (a warm-up never does).
    val record: @Composable (keepPlace: Boolean) -> Unit = { keepPlace ->
        if (set.inherited && !set.warmup) {
            MIcon(
                MIcons.PlateRecord,
                contentDescription = stringResource(Res.string.editor_set_follows_record),
                size = MTheme.sizes.icon20,
                tint = MTheme.colors.accentText,
                modifier = Modifier.testTag(RoutineEditorTags.setRecord(set.id)),
            )
        } else if (keepPlace) {
            Spacer(Modifier.size(MTheme.sizes.icon20))
        }
    }
    val loadField: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.CenterVertically) {
            FieldTapGuard(
                armed = armed(SetField.LOAD),
                onTap = { editing.edit(set.id, SetField.LOAD) },
                modifier = Modifier.onFocusChanged { focusChanged(SetField.LOAD, it.hasFocus) },
            ) {
                NumericField(
                    value = loadText,
                    onValueChange = { text -> drafts.edit(set.id, SetField.LOAD, text)?.let(onWrite) },
                    kind = NumericKind.Load,
                    // The placeholder is not read by itself: a blank load is named in the field's label.
                    accessibilityLabel = stringResource(if (set.warmup) Res.string.editor_warmup_load_of_set else Res.string.workout_load_of_set, number).let { if (loadText.isEmpty()) "$it, $noLoad" else it },
                    modifier = Modifier.testTag(RoutineEditorTags.setLoad(set.id)),
                    suggested = noLoad,
                    selected = target == SetField.LOAD,
                    imeAction = ImeAction.Next,
                    onImeAction = { repsFocus.requestFocus() },
                    focusRequester = loadFocus,
                )
            }
            MText(unitSymbol(pounds), MTheme.type.caption, color = MTheme.colors.textSecondary)
        }
    }
    val repsField: @Composable () -> Unit = {
        FieldTapGuard(
            armed = armed(SetField.REPS),
            onTap = { editing.edit(set.id, SetField.REPS) },
            modifier = Modifier.onFocusChanged { focusChanged(SetField.REPS, it.hasFocus) },
        ) {
            NumericField(
                value = repsText,
                onValueChange = { text -> drafts.edit(set.id, SetField.REPS, text)?.let(onWrite) },
                kind = NumericKind.Reps,
                accessibilityLabel = stringResource(if (set.warmup) Res.string.editor_warmup_reps_of_set else Res.string.workout_reps_of_set, number),
                modifier = Modifier.testTag(RoutineEditorTags.setReps(set.id)),
                suggested = BLANK_REPS,
                selected = target == SetField.REPS,
                imeAction = ImeAction.Done,
                onImeAction = closeKeyboard,
                focusRequester = repsFocus,
            )
        }
    }

    Column(
        modifier
            .testTag(RoutineEditorTags.set(set.id))
            .onGloballyPositioned { editing.rowAreas[set.id] = it }
            .focusGroup()
            .onSizeChanged { height = it.height }
            .bringIntoViewRequester(bringIntoView)
            .background(background, RoundedCornerShape(MTheme.radius.md))
            .animateContentSize(resize),
    ) {
        if (LocalDensity.current.fontScale >= TWO_LINE_FONT_SCALE) {
            // Large text: the label on top, load and reps below, each whole.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
                label()
                record(false)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                loadField()
                repsField()
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.CenterVertically) {
                    label()
                    record(true)
                }
                loadField()
                repsField()
            }
        }
        if (target != null) {
            PlannedStepperPanel(set, number, target, drafts, editing, pounds, onWrite)
        }
    }
}

/** The set's label, a 48 dp button that opens its menu: "3" for the third working set, "A" for a warm-up. */
@Composable
private fun SetLabel(set: RoutineSetUi, number: Int, onMenu: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MTheme.radius.md)
    val text = if (set.warmup) stringResource(Res.string.editor_set_warmup_short) else number.toString()
    val spoken = if (set.warmup) stringResource(Res.string.editor_set_warmup_label, number) else stringResource(Res.string.editor_set_label, number)
    Box(
        Modifier
            .testTag(RoutineEditorTags.setLabel(set.id))
            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min)
            .mFocusRing(shape, source)
            .semantics { contentDescription = spoken }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onMenu),
        contentAlignment = Alignment.Center,
    ) {
        MText(text, MTheme.type.numMd, color = MTheme.colors.textSecondary)
    }
}
