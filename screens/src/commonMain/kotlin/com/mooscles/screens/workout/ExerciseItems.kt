package com.mooscles.screens.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.ExerciseCardHeader
import com.mooscles.designsystem.components.ExerciseCardSegment
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SetRowColumns
import com.mooscles.designsystem.components.setsDoneOf
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.photo.rememberExerciseMedia
import com.mooscles.screens.format.conventionCaption
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.format.targetLine
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_card_meta
import com.mooscles.screens.resources.workout_card_meta_short
import com.mooscles.screens.resources.workout_col_kg_assisted
import com.mooscles.screens.resources.workout_col_lb_assisted
import com.mooscles.screens.resources.workout_col_previous
import com.mooscles.screens.resources.workout_col_reps
import com.mooscles.screens.resources.workout_col_set
import com.mooscles.screens.resources.workout_first_time
import com.mooscles.screens.resources.workout_previous_date
import com.mooscles.screens.resources.workout_rest_chip
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetSuggestions
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource

/**
 * What an exercise card asks the screen to do, beyond the writes it sends itself:
 *
 * - [onRestChip]: the "Rest 2:00" chip, which opens the exercise's rest sheet;
 * - [onSwap]: "Swap exercise", after the confirmation when the exercise has done sets; opens the picker;
 * - [onAbout]: "About the exercise", with the catalog exercise's id (the library's detail);
 * - [onHistory]: "Exercise history", with the catalog exercise's id;
 * - [onHowToLog]: "How to log", which opens [HowToLogSheet];
 * - [onChangeType]: a set's number or "change type" action (`screen.openTypeSheet(set.id)`);
 * - [onRemoveSet]: a set's removal, with what "Undo" puts back (`screen.removeSet(removed, actions)`).
 */
data class ExerciseCallbacks(
    val onRestChip: (WorkoutExerciseUi) -> Unit,
    val onSwap: (WorkoutExerciseUi) -> Unit,
    val onAbout: (exerciseId: String) -> Unit,
    val onHistory: (exerciseId: String) -> Unit,
    val onHowToLog: (WorkoutExerciseUi) -> Unit,
    val onChangeType: (SetUi, WorkoutExerciseUi) -> Unit,
    val onRemoveSet: (RemovedSet) -> Unit,
)

/** The test tag of the header of the card of [entryId]. */
internal fun exerciseHeaderTag(entryId: String): String = "exercise-header-$entryId"

/** The test tag of the column header of the card of [entryId]. */
internal fun exerciseColumnsTag(entryId: String): String = "exercise-columns-$entryId"

/** The test tag of the footer ("Add set") of the card of [entryId]. */
internal fun exerciseFooterTag(entryId: String): String = "exercise-footer-$entryId"

/** The test tag of the rest chip of the card of [entryId]. */
internal fun exerciseRestTag(entryId: String): String = "exercise-rest-$entryId"

/**
 * Every card of [exercises] with [exerciseItems], each open or collapsed as [CardExpansion]
 * says. Put [CardAdvance] beside the list once, so the cards advance on their own.
 */
internal fun LazyListScope.exerciseCards(
    exercises: List<WorkoutExerciseUi>,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
    callbacks: ExerciseCallbacks,
) {
    exercises.forEachIndexed { index, entry ->
        exerciseItems(entry, index, exercises.size, pounds, screen, actions, callbacks, CardExpansion.isOpen(entry.id, exercises, screen))
    }
}

/**
 * The card of [entry], the [index]-th of [count] exercises, as lazy list items that draw one
 * card: the header (key `header-<id>`) and, when [open], the column header (`cols-<id>`), one
 * [WorkoutSetRow] per set (`row-<setId>`) and the "Add set" footer (`footer-<id>`). Only the rows
 * on screen are composed, so an exercise of 100 sets does not compose 100 rows. The items enter
 * and move with [setRowItemMotion]; the list must have no spacing of its own, or the card splits.
 *
 * The header shows the media (the still figure, frame B, of a catalog exercise that has one, with
 * the equipment's small icon beside the meta; otherwise the equipment icon), the name, "Barbell ·
 * Chest · set 4 of 5" (the first set not done, only while open), the convention caption when the
 * load is not the total load, the rest chip and "Previous: Oct 21" (or "First time"). Collapsed, it
 * is the whole card, with "3 of 3 sets" and a check when all are done. A tap on it opens or closes
 * the card by hand ([ActiveWorkoutScreenState.toggleCard]); its menu is [ExerciseMenuSheet].
 */
internal fun LazyListScope.exerciseItems(
    entry: WorkoutExerciseUi,
    index: Int,
    count: Int,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
    callbacks: ExerciseCallbacks,
    open: Boolean,
) {
    item(key = "header-${entry.id}", contentType = "exercise-header") {
        ExerciseHeaderItem(entry, index, count, open, pounds, screen, actions, callbacks, setRowItemMotion())
    }
    if (!open) return
    item(key = "cols-${entry.id}", contentType = "exercise-columns") {
        ColumnTitles(entry, pounds, screen, setRowItemMotion().testTag(exerciseColumnsTag(entry.id)))
    }
    val suggestions = SetSuggestions.forExercise(entry.sets, entry.previousSets, entry.exercise.convention, screen.cleared)
    val previous = SetSuggestions.pairPrevious(entry.sets, entry.previousSets)
    itemsIndexed(entry.sets, key = { _, set -> "row-${set.id}" }, contentType = { _, _ -> "set-row" }) { i, set ->
        ExerciseCardSegment(setRowItemMotion()) {
            WorkoutSetRow(
                set = set,
                number = i + 1,
                total = entry.sets.size,
                entry = entry,
                suggestion = suggestions[i],
                previous = previous[i],
                pounds = pounds,
                screen = screen,
                actions = actions,
                onChangeType = { callbacks.onChangeType(it, entry) },
                onRemove = { snapshot, position -> callbacks.onRemoveSet(RemovedSet(entry.id, position, snapshot, position + 1)) },
                onCells = if (i == 0) ({ cells -> screen.reportColumns(entry.id, cells) }) else null,
            )
        }
    }
    item(key = "footer-${entry.id}", contentType = "exercise-footer") {
        AddSetFooter(
            entry, actions,
            modifier = setRowItemMotion().testTag(exerciseFooterTag(entry.id)).padding(bottom = MTheme.space.s3),
        )
    }
}

@Composable
private fun ExerciseHeaderItem(
    entry: WorkoutExerciseUi,
    index: Int,
    count: Int,
    open: Boolean,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
    callbacks: ExerciseCallbacks,
    modifier: Modifier,
) {
    RecompositionProbe("card-${entry.id}")
    // Done as drawn, so the progress follows a check at once; through derived states, so a check
    // in another exercise does not recompose this header.
    val done by remember(screen, entry) { derivedStateOf { entry.sets.count { screen.completedOf(it) } } }
    val firstPending by remember(screen, entry) { derivedStateOf { entry.sets.indexOfFirst { !screen.completedOf(it) } } }
    var menu by rememberSaveable(entry.id) { mutableStateOf(false) }
    var confirm by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }

    val total = entry.sets.size
    val equipment = equipmentLabel(entry.exercise.equipment)
    val muscle = muscleLabel(entry.exercise.muscle)
    val meta = if (open && firstPending >= 0) {
        stringResource(Res.string.workout_card_meta, equipment, muscle, firstPending + 1, total)
    } else {
        stringResource(Res.string.workout_card_meta_short, equipment, muscle)
    }
    // The photo of a custom exercise, else the still figure (frame B) of a catalog exercise that has one, else the
    // equipment's icon (spec D5).
    val media = rememberExerciseMedia(entry.exercise, thumbnail = true)
    val reduced = MTheme.reducedMotion
    val snappy = MTheme.motion.springSnappy
    ExerciseCardHeader(
        name = exerciseName(entry.exercise),
        meta = meta,
        media = media,
        equipmentIcon = EquipmentIcons.forEquipment(entry.exercise.equipment),
        onMenu = { menu = true },
        collapsed = !open,
        onToggle = { screen.toggleCard(entry.id, open) },
        modifier = modifier
            .testTag(exerciseHeaderTag(entry.id))
            // The gap under a collapsed card animates with the card.
            .animateContentSize(if (reduced) snap() else spring<IntSize>(snappy.dampingRatio, snappy.stiffness))
            .then(if (open) Modifier else Modifier.padding(bottom = MTheme.space.s3)),
        progress = setsDoneOf(done, total),
        done = total > 0 && done == total,
        caption = conventionCaption(entry.exercise.convention),
        support = { SupportLine(entry, callbacks) },
        subline = targetLine(entry.target, pounds),
    )

    if (menu) {
        ExerciseMenuSheet(
            entry = entry,
            index = index,
            count = count,
            onAbout = {
                menu = false
                callbacks.onAbout(entry.exercise.id)
            },
            onHistory = {
                menu = false
                callbacks.onHistory(entry.exercise.id)
            },
            onSwap = {
                menu = false
                // As drawn: a check the database has not echoed yet counts too.
                if (entry.sets.any { screen.completedOf(it) }) confirm = ExerciseConfirm.SWAP.name else callbacks.onSwap(entry)
            },
            onMove = { direction ->
                menu = false
                actions.moveWorkoutExercise(entry.id, direction)
            },
            onHowToLog = {
                menu = false
                callbacks.onHowToLog(entry)
            },
            onRemove = {
                menu = false
                confirm = ExerciseConfirm.REMOVE.name
            },
            onDismiss = { menu = false },
        )
    }
    confirm?.let { name ->
        val kind = ExerciseConfirm.valueOf(name)
        ExerciseConfirmDialog(
            confirm = kind,
            entry = entry,
            onConfirm = {
                confirm = null
                when (kind) {
                    ExerciseConfirm.REMOVE -> {
                        // A removed set of this exercise has nowhere to go back to.
                        screen.closeUndoFor(entry.id)
                        actions.removeWorkoutExercise(entry.id)
                    }
                    ExerciseConfirm.SWAP -> callbacks.onSwap(entry)
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

/** The rest chip ("Rest 2:00") and, at the end, "Previous: Oct 21" or "First time". */
@Composable
private fun RowScope.SupportLine(entry: WorkoutExerciseUi, callbacks: ExerciseCallbacks) {
    MButton(
        stringResource(Res.string.workout_rest_chip, MoosclesNumbers.clock(entry.restSeconds)),
        onClick = { callbacks.onRestChip(entry) },
        modifier = Modifier.testTag(exerciseRestTag(entry.id)),
        variant = MButtonVariant.Secondary,
        size = MButtonSize.S,
        icon = MIcons.Timer,
    )
    val previous = entry.previousDate?.let { stringResource(Res.string.workout_previous_date, shortDate(it)) }
        ?: stringResource(Res.string.workout_first_time)
    MText(
        previous,
        MTheme.type.bodyMd.copy(textAlign = TextAlign.End),
        color = MTheme.colors.textSecondary,
        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
    )
}

/** The load column's title: "kg" or "lb", and "kg assist." or "lb assist." in the Assistance convention. */
@Composable
internal fun columnUnit(convention: String, pounds: Boolean): String = when {
    convention != LoadConvention.ASSISTANCE.label -> unitSymbol(pounds)
    pounds -> stringResource(Res.string.workout_col_lb_assisted)
    else -> stringResource(Res.string.workout_col_kg_assisted)
}

/**
 * The column titles of the card of [entry] (Set · Previous · kg (lb; "kg assist." in the Assistance
 * convention) · Reps), over the cells of its first set row as that row was laid out
 * ([ActiveWorkoutScreenState.columns]). Only over a one-line row: with large text or a narrow
 * screen the row is two lines, and then the item draws nothing (0 tall). Hidden from screen
 * readers ([SetRowColumns]).
 */
@Composable
private fun ColumnTitles(entry: WorkoutExerciseUi, pounds: Boolean, screen: ActiveWorkoutScreenState, modifier: Modifier) {
    val cells = screen.columns[entry.id]?.takeIf { it.oneLine && entry.sets.isNotEmpty() }
    Box(modifier.fillMaxWidth()) {
        if (cells != null) {
            ExerciseCardSegment {
                SetRowColumns(
                    set = stringResource(Res.string.workout_col_set),
                    previous = stringResource(Res.string.workout_col_previous),
                    load = columnUnit(entry.exercise.convention, pounds),
                    reps = stringResource(Res.string.workout_col_reps),
                    cells = cells,
                )
            }
        }
    }
}
