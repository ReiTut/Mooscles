package com.mooscles.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.ds_reorder_bottom
import com.mooscles.designsystem.resources.ds_reorder_down
import com.mooscles.designsystem.resources.ds_reorder_top
import com.mooscles.designsystem.resources.ds_reorder_up
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.folderName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.routines_active_body
import com.mooscles.screens.resources.routines_active_title
import com.mooscles.screens.resources.routines_cancel
import com.mooscles.screens.resources.routines_current_folder
import com.mooscles.screens.resources.routines_delete
import com.mooscles.screens.resources.routines_delete_body
import com.mooscles.screens.resources.routines_delete_title
import com.mooscles.screens.resources.routines_duplicate
import com.mooscles.screens.resources.routines_edit
import com.mooscles.screens.resources.routines_move_to_folder
import com.mooscles.screens.resources.routines_rename
import com.mooscles.screens.resources.routines_resume
import com.mooscles.screens.workout.MenuItem
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DesignRes

/**
 * The menu of a routine card, a bottom sheet under the routine's name: Edit, Rename, Duplicate, Move to
 * folder, Move up, Move down, Move to top, Move to bottom and Delete (destructive, last). The four moves are
 * the screen reader's alternatives to dragging as buttons of their own; "up" and "top" are off for the first
 * routine of its folder ([index] 0) and "down" and "bottom" for the last of [count]. Each item closes the menu
 * (the caller takes it out of composition) and then does its part. The items scroll, under the title, when the sheet
 * is shorter than they are (large text, landscape). "Duplicate" is off while [duplicateEnabled] is false.
 */
@Composable
internal fun RoutineMenuSheet(
    routine: RoutineUi,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    duplicateEnabled: Boolean,
    onMoveToFolder: () -> Unit,
    onMoveTo: (position: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = index <= 0
    val last = index >= count - 1
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(routine.name)
        Column(Modifier.testTag(TrainTags.MENU_LIST).verticalScroll(rememberScrollState())) {
        MenuItem(MIcons.Pencil, stringResource(Res.string.routines_edit), TrainTags.MENU_EDIT, onClick = onEdit)
        MenuItem(MIcons.Pencil, stringResource(Res.string.routines_rename), TrainTags.MENU_RENAME, onClick = onRename)
        MenuItem(MIcons.Copy, stringResource(Res.string.routines_duplicate), TrainTags.MENU_DUPLICATE, enabled = duplicateEnabled, onClick = onDuplicate)
        MenuItem(MIcons.FolderInput, stringResource(Res.string.routines_move_to_folder), TrainTags.MENU_MOVE, onClick = onMoveToFolder)
        MenuItem(
            MIcons.ArrowUp, stringResource(DesignRes.string.ds_reorder_up), TrainTags.MENU_UP,
            enabled = !first, onClick = { onMoveTo(index - 1) },
        )
        MenuItem(
            MIcons.ArrowDown, stringResource(DesignRes.string.ds_reorder_down), TrainTags.MENU_DOWN,
            enabled = !last, onClick = { onMoveTo(index + 1) },
        )
        MenuItem(
            MIcons.ArrowUpToLine, stringResource(DesignRes.string.ds_reorder_top), TrainTags.MENU_TOP,
            enabled = !first, onClick = { onMoveTo(0) },
        )
        MenuItem(
            MIcons.ArrowDownToLine, stringResource(DesignRes.string.ds_reorder_bottom), TrainTags.MENU_BOTTOM,
            enabled = !last, onClick = { onMoveTo(count - 1) },
        )
        MenuItem(
            MIcons.Trash2, stringResource(Res.string.routines_delete), TrainTags.MENU_DELETE,
            destructive = true, onClick = onDelete,
        )
        Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}

/**
 * "Move to folder": the folders in their saved order, one button each (the whole row), the routine's own folder
 * marked with a check that says "Current folder". Choosing it closes the sheet without a write.
 */
@Composable
internal fun MoveToFolderSheet(
    routine: RoutineUi,
    folders: List<RoutineFolderUi>,
    onMove: (folderId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(Res.string.routines_move_to_folder))
        Column(Modifier.testTag(TrainTags.MOVE_LIST).verticalScroll(rememberScrollState())) {
            for (folder in folders) {
                FolderChoice(folderName(folder), current = folder.id == routine.folderId, tag = TrainTags.moveTo(folder.id)) {
                    if (folder.id == routine.folderId) onDismiss() else onMove(folder.id)
                }
            }
            Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}

/** "Delete Treino A?": the destructive confirmation, which says the history stays. */
@Composable
internal fun DeleteRoutineDialog(routine: RoutineUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MDialog(
        title = stringResource(Res.string.routines_delete_title, routine.name),
        text = stringResource(Res.string.routines_delete_body),
        confirmLabel = stringResource(Res.string.routines_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.routines_cancel),
        destructive = true,
    )
}

/** "A workout is in progress": starting another is refused; "Resume workout" opens the one there is. */
@Composable
internal fun ActiveWorkoutDialog(activeName: String, onResume: () -> Unit, onDismiss: () -> Unit) {
    MDialog(
        title = stringResource(Res.string.routines_active_title),
        text = stringResource(Res.string.routines_active_body, activeName),
        confirmLabel = stringResource(Res.string.routines_resume),
        onConfirm = onResume,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.routines_cancel),
    )
}

@Composable
internal fun SheetTitle(text: String) {
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

/** One folder of the move sheet: a 56 dp button; the routine's folder shows a check read as "Current folder". */
@Composable
private fun FolderChoice(label: String, current: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .padding(horizontal = MTheme.space.s2)
            .mFocusRing(shape, source)
            .background(if (pressed) colors.bgInset else Color.Transparent, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .sizeIn(minHeight = MTheme.sizes.rowMin)
            .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MText(label, MTheme.type.bodyLg, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (current) {
            MIcon(MIcons.Check, stringResource(Res.string.routines_current_folder), size = MTheme.sizes.icon20, tint = colors.accentText)
        } else {
            Spacer(Modifier.size(MTheme.sizes.icon20))
        }
    }
}
