package com.mooscles.screens.routines

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.ExerciseCardFooter
import com.mooscles.designsystem.components.ExerciseCardHeader
import com.mooscles.designsystem.components.ExerciseCardSegment
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderHandle
import com.mooscles.designsystem.interaction.ReorderState
import com.mooscles.designsystem.interaction.reorderItem
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.photo.rememberExerciseMedia
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.targetLine
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.editor_exercise_more
import com.mooscles.screens.resources.editor_rest_chip
import com.mooscles.screens.resources.editor_set_limit
import com.mooscles.screens.resources.workout_card_meta_short
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.RoutineTargetUi
import com.mooscles.ui.TargetSetUi
import org.jetbrains.compose.resources.stringResource

/** What an exercise card of the routine editor asks the screen to do. */
internal class RoutineExerciseCallbacks(
    /** The card's options button. */
    val onMenu: (PlannedExerciseUi) -> Unit,
    /** The "Rest 2:00" chip, which opens the rest sheet of that entry. */
    val onRest: (PlannedExerciseUi) -> Unit,
    /** The footer's "Add set". */
    val onAddSet: (PlannedExerciseUi) -> Unit,
    /** What the planned set rows have typed and not seen come back, and the set in edit. */
    val drafts: PlannedSetDrafts,
    val editing: PlannedEditing,
    /** One write of a planned set (`updateRoutineSet`). */
    val onWrite: (PlannedSave) -> Unit,
    /** The label of a set: its menu. */
    val onSetMenu: (entryId: String, setId: String) -> Unit,
)

/** The lazy key of the planned set [setId]'s line, and of the footer of [entryId]: both outside the reorder keys, the entry ids. */
internal fun plannedSetKey(setId: String) = "set:$setId"
internal fun plannedFooterKey(entryId: String) = "footer:$entryId"

private const val HEADER_TYPE = "routine-exercise-header"
private const val SET_TYPE = "routine-exercise-set"
private const val FOOTER_TYPE = "routine-exercise-footer"

/**
 * The cards of [entries] (in the order shown, which is the reorder state's) as lazy list items that draw one card each
 * (spec "Editor de rotina"): the header (lazy key = the entry's id, the one reorderable item, with the drag handle,
 * the media, the name, "Chest · Barbell", the options, the rest chip and the target line), one line per planned set
 * (key `set:<id>`: the editable [PlannedSetRow]) and the "Add set" footer (`footer:<id>`, off at [MAX_PLANNED_SETS] sets and saying why).
 * While [collapsed] (an exercise is being dragged, plan decision 14) only the headers are emitted, so the reorderable
 * items are contiguous; the list must have no spacing of its own, or the card splits. Put a [com.mooscles.designsystem.interaction.ReorderAnnouncer]
 * beside the list, and no `animateItem`: [reorderItem] animates the placement.
 */
internal fun LazyListScope.routineExerciseItems(
    entries: List<PlannedExerciseUi>,
    reorder: ReorderState<String>,
    collapsed: Boolean,
    pounds: Boolean,
    callbacks: RoutineExerciseCallbacks,
) {
    for (entry in entries) {
        item(key = entry.id, contentType = HEADER_TYPE) {
            RoutineExerciseHeader(entry, reorder, collapsed, pounds, callbacks)
        }
        if (collapsed) continue
        val numbers = ordinals(entry.setTargets)
        itemsIndexed(entry.setTargets, key = { _, set -> plannedSetKey(set.id) }, contentType = { _, _ -> SET_TYPE }) { index, set ->
            ExerciseCardSegment {
                PlannedSetRow(
                    set = set,
                    entry = entry,
                    number = numbers[index],
                    pounds = pounds,
                    drafts = callbacks.drafts,
                    editing = callbacks.editing,
                    onWrite = callbacks.onWrite,
                    onMenu = { callbacks.onSetMenu(entry.id, set.id) },
                )
            }
        }
        val full = entry.setTargets.size >= MAX_PLANNED_SETS
        item(key = plannedFooterKey(entry.id), contentType = FOOTER_TYPE) {
            ExerciseCardFooter(
                onAddSet = { callbacks.onAddSet(entry) },
                modifier = Modifier.testTag(RoutineEditorTags.footer(entry.id)).padding(bottom = MTheme.space.s3),
                addEnabled = !full,
                addTag = RoutineEditorTags.addSet(entry.id),
                note = if (full) stringResource(Res.string.editor_set_limit) else null,
            )
        }
    }
}

/** The number of each set among its kind: working sets 1, 2, 3… and warm-ups 1, 2… (each counted apart). */
private fun ordinals(sets: List<RoutineSetUi>): List<Int> {
    var working = 0
    var warmups = 0
    return sets.map { if (it.warmup) ++warmups else ++working }
}

/** The target of [entry] for [targetLine]: its working sets as planned, and how many warm-ups; null without sets. */
internal fun plannedTarget(entry: PlannedExerciseUi): RoutineTargetUi? {
    if (entry.setTargets.isEmpty()) return null
    return RoutineTargetUi(
        working = entry.setTargets.filterNot { it.warmup }.map { TargetSetUi(it.reps, it.weightKg) },
        warmups = entry.setTargets.count { it.warmup },
    )
}

@Composable
private fun RoutineExerciseHeader(
    entry: PlannedExerciseUi,
    reorder: ReorderState<String>,
    collapsed: Boolean,
    pounds: Boolean,
    callbacks: RoutineExerciseCallbacks,
) {
    val name = exerciseName(entry.exercise)
    val meta = stringResource(Res.string.workout_card_meta_short, muscleLabel(entry.exercise.muscle), equipmentLabel(entry.exercise.equipment))
    val media = rememberExerciseMedia(entry.exercise, thumbnail = true)
    // [reorderItem] is on the header, the lazy item's root, so the lifted card is raised above its siblings (zIndex);
    // the gap under a collapsed card comes from the list's spacing.
    val target = remember(entry.setTargets) { plannedTarget(entry) }
    ExerciseCardHeader(
        name = name,
        meta = meta,
        media = media,
        equipmentIcon = EquipmentIcons.forEquipment(entry.exercise.equipment),
        onMenu = { callbacks.onMenu(entry) },
        collapsed = collapsed,
        onToggle = null,
        modifier = Modifier.reorderItem(reorder, entry.id).testTag(RoutineEditorTags.header(entry.id)),
        support = { RestChip(entry, callbacks) },
        handle = { ReorderHandle(reorder, entry.id, name, Modifier.testTag(RoutineEditorTags.handle(entry.id))) },
        subline = targetLine(target, pounds),
        menuTag = RoutineEditorTags.menu(entry.id),
        menuLabel = stringResource(Res.string.editor_exercise_more, name),
    )
}

/** The "Rest 2:00" chip: a small secondary button that opens the entry's rest sheet. */
@Composable
private fun RowScope.RestChip(entry: PlannedExerciseUi, callbacks: RoutineExerciseCallbacks) {
    MButton(
        stringResource(Res.string.editor_rest_chip, MoosclesNumbers.clock(entry.restSeconds)),
        onClick = { callbacks.onRest(entry) },
        modifier = Modifier.testTag(RoutineEditorTags.rest(entry.id)),
        variant = MButtonVariant.Secondary,
        size = MButtonSize.S,
        icon = MIcons.Timer,
    )
}
