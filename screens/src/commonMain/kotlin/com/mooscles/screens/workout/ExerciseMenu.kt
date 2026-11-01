package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.conventionExplanation
import com.mooscles.screens.format.conventionLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_keep_training
import com.mooscles.screens.resources.workout_menu_about
import com.mooscles.screens.resources.workout_menu_history
import com.mooscles.screens.resources.workout_menu_how_to_log
import com.mooscles.screens.resources.workout_menu_move_down
import com.mooscles.screens.resources.workout_menu_move_up
import com.mooscles.screens.resources.workout_menu_remove
import com.mooscles.screens.resources.workout_menu_swap
import com.mooscles.screens.resources.workout_remove
import com.mooscles.screens.resources.workout_remove_exercise_text
import com.mooscles.screens.resources.workout_swap
import com.mooscles.screens.resources.workout_swap_exercise_text
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource

/** The test tags of the exercise menu's items, in their order. */
internal object ExerciseMenuTags {
    const val ABOUT = "exercise-menu-about"
    const val HISTORY = "exercise-menu-history"
    const val SWAP = "exercise-menu-swap"
    const val MOVE_UP = "exercise-menu-move-up"
    const val MOVE_DOWN = "exercise-menu-move-down"
    const val HOW_TO_LOG = "exercise-menu-how-to-log"
    const val REMOVE = "exercise-menu-remove"
    val all = listOf(ABOUT, HISTORY, SWAP, MOVE_UP, MOVE_DOWN, HOW_TO_LOG, REMOVE)
}

/** What the exercise menu asks to confirm before doing it. */
internal enum class ExerciseConfirm { REMOVE, SWAP }

/**
 * The menu of an exercise card, a bottom sheet under the exercise's name: About the exercise (the
 * library's detail of the catalog exercise), Exercise history, Swap exercise, Move up, Move down,
 * How to log and Remove exercise (destructive, last). Moving up is disabled for the first exercise
 * ([index] 0) and down for the last of [count]. Each item closes the menu (the caller takes it out
 * of composition) and then does its part.
 */
@Composable
internal fun ExerciseMenuSheet(
    entry: WorkoutExerciseUi,
    index: Int,
    count: Int,
    onAbout: () -> Unit,
    onHistory: () -> Unit,
    onSwap: () -> Unit,
    onMove: (direction: Int) -> Unit,
    onHowToLog: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(exerciseName(entry.exercise))
        MenuItem(MIcons.Dumbbell, stringResource(Res.string.workout_menu_about), ExerciseMenuTags.ABOUT, onClick = onAbout)
        MenuItem(MIcons.ChartLine, stringResource(Res.string.workout_menu_history), ExerciseMenuTags.HISTORY, onClick = onHistory)
        MenuItem(MIcons.ArrowLeftRight, stringResource(Res.string.workout_menu_swap), ExerciseMenuTags.SWAP, onClick = onSwap)
        MenuItem(
            MIcons.ArrowUp, stringResource(Res.string.workout_menu_move_up), ExerciseMenuTags.MOVE_UP,
            enabled = index > 0, onClick = { onMove(-1) },
        )
        MenuItem(
            MIcons.ArrowDown, stringResource(Res.string.workout_menu_move_down), ExerciseMenuTags.MOVE_DOWN,
            enabled = index < count - 1, onClick = { onMove(1) },
        )
        MenuItem(MIcons.Info, stringResource(Res.string.workout_menu_how_to_log), ExerciseMenuTags.HOW_TO_LOG, onClick = onHowToLog)
        MenuItem(
            MIcons.Trash2, stringResource(Res.string.workout_menu_remove), ExerciseMenuTags.REMOVE,
            destructive = true, onClick = onRemove,
        )
        Spacer(Modifier.height(MTheme.space.s4))
    }
}

/**
 * "How to log" of [entry]: the name of its load convention and the full explanation, which the
 * card shows only as a one-line caption ([com.mooscles.screens.format.conventionCaption]).
 */
@Composable
internal fun HowToLogSheet(entry: WorkoutExerciseUi, onDismiss: () -> Unit) {
    val space = MTheme.space
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(Res.string.workout_menu_how_to_log))
        MText(
            conventionLabel(entry.exercise.convention),
            MTheme.type.labelLg,
            modifier = Modifier.fillMaxWidth().padding(horizontal = space.s4),
        )
        MText(
            conventionExplanation(entry.exercise.convention),
            MTheme.type.bodyLg,
            color = MTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = space.s4).padding(top = space.s1, bottom = space.s6),
        )
    }
}

/**
 * The confirmation of [confirm] for [entry], a destructive [MDialog]: removing ("Remove Bench
 * press? Its 3 sets leave this workout.") or swapping an exercise with done sets, which the swap
 * deletes. "Keep training" closes it; the confirmation calls [onConfirm].
 */
@Composable
internal fun ExerciseConfirmDialog(
    confirm: ExerciseConfirm,
    entry: WorkoutExerciseUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = exerciseName(entry.exercise)
    when (confirm) {
        ExerciseConfirm.REMOVE -> MDialog(
            title = stringResource(Res.string.workout_menu_remove),
            text = pluralCount(Res.plurals.workout_remove_exercise_text, entry.sets.size, name, entry.sets.size),
            confirmLabel = stringResource(Res.string.workout_remove),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            dismissLabel = stringResource(Res.string.workout_keep_training),
            destructive = true,
        )
        ExerciseConfirm.SWAP -> MDialog(
            title = stringResource(Res.string.workout_menu_swap),
            text = stringResource(Res.string.workout_swap_exercise_text, name),
            confirmLabel = stringResource(Res.string.workout_swap),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            dismissLabel = stringResource(Res.string.workout_keep_training),
            destructive = true,
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    MText(
        text,
        MTheme.type.titleMd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MTheme.space.s4)
            .padding(bottom = MTheme.space.s2)
            .semantics { heading() },
    )
}

/**
 * One item of a menu sheet: an icon and a label in a 56 dp row, the whole row a button. A non-null [selected] (the
 * items of a list of types) is reported as selected or not, and the chosen one is marked with a check; null says nothing.
 */
@Composable
internal fun MenuItem(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    val content = when {
        !enabled -> colors.textDisabled
        destructive -> colors.dangerText
        else -> colors.textPrimary
    }
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .padding(horizontal = MTheme.space.s2)
            .mFocusRing(shape, source)
            .background(if (pressed && enabled) colors.bgInset else Color.Transparent, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { selected?.let { this.selected = it } }
            .sizeIn(minHeight = MTheme.sizes.rowMin)
            .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.icon20, tint = if (destructive && enabled) colors.dangerText else if (enabled) colors.textSecondary else colors.textDisabled)
        MText(label, MTheme.type.bodyLg, color = content, modifier = Modifier.weight(1f))
        if (selected == true) MIcon(MIcons.Check, contentDescription = null, size = MTheme.sizes.icon20, tint = colors.accentText)
    }
}
