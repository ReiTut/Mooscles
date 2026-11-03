package com.mooscles.screens.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.data.RoutineFolder
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.interaction.reorderMover
import com.mooscles.designsystem.resources.ds_reorder_bottom
import com.mooscles.designsystem.resources.ds_reorder_down
import com.mooscles.designsystem.resources.ds_reorder_top
import com.mooscles.designsystem.resources.ds_reorder_up
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.designsystem.resources.Res as DesignRes
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderAnnouncer
import com.mooscles.designsystem.interaction.ReorderHandle
import com.mooscles.designsystem.interaction.ReorderState
import com.mooscles.designsystem.interaction.rememberReorderState
import com.mooscles.designsystem.interaction.reorderItem
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.folderName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.folders_delete
import com.mooscles.screens.resources.folders_delete_body
import com.mooscles.screens.resources.folders_delete_title
import com.mooscles.screens.resources.folders_more
import com.mooscles.screens.resources.folders_name_field
import com.mooscles.screens.resources.folders_new
import com.mooscles.screens.resources.folders_rename
import com.mooscles.screens.resources.folders_routine_count
import com.mooscles.screens.resources.folders_title
import com.mooscles.screens.resources.routines_cancel
import com.mooscles.screens.resources.routines_create
import com.mooscles.screens.workout.MenuItem
import com.mooscles.screens.workout.RenameSheet
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.UiState
import org.jetbrains.compose.resources.stringResource

/** The test tags of the folder manager. */
internal object FolderTags {
    const val LIST = "folders-list"
    const val NEW = "folders-new"
    const val MENU_RENAME = "folder-menu-rename"
    const val MENU_DELETE = "folder-menu-delete"
    const val MENU_LIST = "folder-menu-list"
    const val MENU_UP = "folder-menu-up"
    const val MENU_DOWN = "folder-menu-down"
    const val MENU_TOP = "folder-menu-top"
    const val MENU_BOTTOM = "folder-menu-bottom"

    fun row(id: String) = "folder-$id-row"
    fun handle(id: String) = "folder-$id-handle"
    fun count(id: String) = "folder-$id-count"
    fun menu(id: String) = "folder-$id-menu"
}

private const val KEY_NEW = "folders:new"

/**
 * The folder manager (spec "Treinar (aba)", "Pastas"): a bottom sheet with the folders in their saved order, each row
 * with the drag handle (a long press lifts it; [MoosclesActions.reorderRoutineFolders] is written once, on release,
 * and the handle carries the four move actions for a screen reader), the name ([folderName]), how many routines it
 * holds and an options button (Rename; Delete, which the default folder does not have). "New folder" ends the list,
 * outside the reorder keys; it opens the name sheet with "New folder" selected and creates with the name as it stands.
 * Deleting asks first, and says where the routines go (the default folder); the confirmation writes once. The list
 * scrolls under the title when the folders do not fit (many of them, large text). What is open (the options menu,
 * the name sheet, the dialog) is an id and survives recreation.
 */
@Composable
internal fun FolderManagerSheet(state: UiState, actions: MoosclesActions, onDismiss: () -> Unit) {
    val folders = state.routineFolders
    val byId = remember(folders) { folders.associateBy { it.id } }
    val ids = remember(folders) { folders.map { it.id } }
    val counts = remember(state.routines) { state.routines.groupingBy { it.folderId }.eachCount() }

    // What is open, as ids; "" is the new-folder sheet.
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val toasts = remember { MToastHostState() }
    val reorder = rememberReorderState(listState, ids, onDrop = { actions.reorderRoutineFolders(it) })
    val space = MTheme.space
    MBottomSheet(onDismiss = onDismiss) {
        MText(
            stringResource(Res.string.folders_title),
            MTheme.type.titleMd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.s4)
                .padding(bottom = space.s2)
                .semantics { heading() },
        )
        // The weight keeps the list within the sheet and leaves the announcer below it a height of its own.
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().testTag(FolderTags.LIST), state = listState) {
            items(reorder.order, key = { it }) { id ->
                val folder = byId[id] ?: return@items
                FolderRow(
                    folder = folder,
                    count = counts[id] ?: 0,
                    reorder = reorder,
                    onMenu = { menuFor = id },
                    modifier = Modifier.reorderItem(reorder, id),
                )
            }
            item(key = KEY_NEW) {
                MenuItem(MIcons.Plus, stringResource(Res.string.folders_new), FolderTags.NEW) { creating = true }
                Spacer(Modifier.height(space.s4))
            }
        }
        ReorderAnnouncer(reorder)
        // The sheet is a window of its own over the tab: an error raised while it is open shows here, not behind it.
        ScreenErrorToast(state.errorCode, actions, toasts)
        MToastHost(toasts, Modifier.fillMaxWidth())
    }

    if (creating) {
        RenameSheet(
            name = stringResource(Res.string.folders_new),
            onRename = { actions.createRoutineFolder(it) },
            onDismiss = { creating = false },
            title = stringResource(Res.string.folders_new),
            confirmLabel = stringResource(Res.string.routines_create),
            fieldLabel = stringResource(Res.string.folders_name_field),
            selectAll = true,
            submitUnchanged = true,
        )
    }
    menuFor?.let { id ->
        byId[id]?.let { folder ->
            val order = reorder.order
            val index = order.indexOf(id)
            val last = index >= order.size - 1
            val first = index <= 0
            val moveTo = reorderMover(reorder, id, folderName(folder))
            MBottomSheet(onDismiss = { menuFor = null }) {
                MText(
                    folderName(folder),
                    MTheme.type.titleMd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = space.s4)
                        .padding(bottom = space.s2)
                        .semantics { heading() },
                )
                Column(Modifier.testTag(FolderTags.MENU_LIST).verticalScroll(rememberScrollState())) {
                    MenuItem(MIcons.Pencil, stringResource(Res.string.folders_rename), FolderTags.MENU_RENAME) {
                        menuFor = null
                        renaming = id
                    }
                    MenuItem(MIcons.ArrowUp, stringResource(DesignRes.string.ds_reorder_up), FolderTags.MENU_UP, enabled = !first) {
                        menuFor = null
                        moveTo(index - 1)
                    }
                    MenuItem(MIcons.ArrowDown, stringResource(DesignRes.string.ds_reorder_down), FolderTags.MENU_DOWN, enabled = !last) {
                        menuFor = null
                        moveTo(index + 1)
                    }
                    MenuItem(MIcons.ArrowUpToLine, stringResource(DesignRes.string.ds_reorder_top), FolderTags.MENU_TOP, enabled = !first) {
                        menuFor = null
                        moveTo(0)
                    }
                    MenuItem(MIcons.ArrowDownToLine, stringResource(DesignRes.string.ds_reorder_bottom), FolderTags.MENU_BOTTOM, enabled = !last) {
                        menuFor = null
                        moveTo(order.size - 1)
                    }
                    if (id != RoutineFolder.DEFAULT_ID) {
                        MenuItem(MIcons.Trash2, stringResource(Res.string.folders_delete), FolderTags.MENU_DELETE, destructive = true) {
                            menuFor = null
                            deleting = id
                        }
                    }
                    Spacer(Modifier.height(space.s4))
                }
            }
        }
    }
    renaming?.let { id ->
        byId[id]?.let { folder ->
            RenameSheet(
                name = folderName(folder),
                onRename = { actions.renameRoutineFolder(id, it) },
                onDismiss = { renaming = null },
                title = stringResource(Res.string.folders_rename),
                fieldLabel = stringResource(Res.string.folders_name_field),
            )
        }
    }
    deleting?.let { id ->
        byId[id]?.takeIf { it.id != RoutineFolder.DEFAULT_ID }?.let { folder ->
            val home = byId[RoutineFolder.DEFAULT_ID] ?: RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)
            DeleteFolderDialog(
                folder = folder,
                count = counts[id] ?: 0,
                home = home,
                onConfirm = {
                    deleting = null
                    actions.deleteRoutineFolder(id)
                },
                onDismiss = { deleting = null },
            )
        }
    }
}

/** One folder: handle, name over the routine count, and the options button. */
@Composable
private fun FolderRow(
    folder: RoutineFolderUi,
    count: Int,
    reorder: ReorderState<String>,
    onMenu: () -> Unit,
    modifier: Modifier,
) {
    val colors = MTheme.colors
    val name = folderName(folder)
    Row(
        modifier.fillMaxWidth().testTag(FolderTags.row(folder.id)).sizeIn(minHeight = MTheme.sizes.rowMin).padding(horizontal = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReorderHandle(reorder, folder.id, name, Modifier.testTag(FolderTags.handle(folder.id)))
        Column(Modifier.weight(1f).padding(vertical = MTheme.space.s2)) {
            MText(name, MTheme.type.bodyLg, color = colors.textPrimary)
            MText(
                pluralCount(Res.plurals.folders_routine_count, count),
                MTheme.type.bodyMd,
                color = colors.textSecondary,
                modifier = Modifier.testTag(FolderTags.count(folder.id)),
            )
        }
        MIconButton(
            MIcons.EllipsisVertical,
            contentDescription = stringResource(Res.string.folders_more, name),
            onClick = onMenu,
            modifier = Modifier.testTag(FolderTags.menu(folder.id)),
        )
    }
}

/**
 * "Delete the Hipertrofia folder?": says where its routines go ([home], the default folder, by its translated name);
 * a folder with none has the title alone. The confirmation acts once, however many times it is tapped.
 */
@Composable
private fun DeleteFolderDialog(folder: RoutineFolderUi, count: Int, home: RoutineFolderUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    MDialog(
        title = stringResource(Res.string.folders_delete_title, folderName(folder)),
        text = if (count == 0) null else pluralCount(Res.plurals.folders_delete_body, count, count, folderName(home)),
        confirmLabel = stringResource(Res.string.folders_delete),
        onConfirm = {
            if (!confirmed) {
                confirmed = true
                onConfirm()
            }
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.routines_cancel),
        destructive = true,
    )
}
