package com.mooscles.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.data.RoutineFolder
import com.mooscles.data.newId
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.designsystem.components.MToastResult
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderAnnouncer
import com.mooscles.designsystem.interaction.rememberReorderState
import com.mooscles.designsystem.interaction.reorderMover
import com.mooscles.designsystem.interaction.reorderItem
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.format.folderName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.routines_copy_name
import com.mooscles.screens.resources.routines_create
import com.mooscles.screens.resources.routines_duplicated
import com.mooscles.screens.resources.routines_empty_folder
import com.mooscles.screens.resources.routines_empty_folder_body
import com.mooscles.screens.resources.routines_empty_workout
import com.mooscles.screens.resources.routines_folders
import com.mooscles.screens.resources.routines_name_field
import com.mooscles.screens.resources.routines_new
import com.mooscles.screens.resources.routines_new_name
import com.mooscles.screens.resources.routines_open
import com.mooscles.screens.resources.routines_rename
import com.mooscles.screens.resources.routines_resume
import com.mooscles.screens.resources.routines_settings
import com.mooscles.screens.resources.routines_title
import com.mooscles.screens.workout.RenameSheet
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.uiErrorText
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/** The tag of the Train tab's list (the old screen's, kept for the shell's tests). */
const val TrainScreenTag = "routines-list"

/** The test tags of the Train tab. */
internal object TrainTags {
    const val FOLDER_ROW = "routine-folder-selector"
    const val MANAGE_FOLDERS = "train-folders"
    const val SETTINGS = "train-settings"
    const val NEW_ROUTINE = "train-new-routine"
    const val EMPTY_WORKOUT = "train-empty-workout"
    const val EMPTY = "train-empty"
    const val SKELETON = "train-skeleton"

    /** A thumbnail of a card's strip. */
    const val THUMB = "routine-thumb"

    const val MENU_EDIT = "routine-menu-edit"
    const val MENU_RENAME = "routine-menu-rename"
    const val MENU_DUPLICATE = "routine-menu-duplicate"
    const val MENU_MOVE = "routine-menu-move"
    const val MENU_UP = "routine-menu-up"
    const val MENU_DOWN = "routine-menu-down"
    const val MENU_TOP = "routine-menu-top"
    const val MENU_BOTTOM = "routine-menu-bottom"
    const val MENU_DELETE = "routine-menu-delete"
    /** The scrolling list of items of the card menu, and of the move-to-folder sheet. */
    const val MENU_LIST = "routine-menu-list"
    const val MOVE_LIST = "move-to-folder-list"
    val MENU_ITEMS = listOf(
        MENU_EDIT, MENU_RENAME, MENU_DUPLICATE, MENU_MOVE, MENU_UP, MENU_DOWN, MENU_TOP, MENU_BOTTOM, MENU_DELETE,
    )

    fun folder(id: String) = "train-folder-$id"
    fun start(id: String) = "routine-$id-start"
    fun menu(id: String) = "routine-$id-menu"
    fun handle(id: String) = "routine-$id-handle"
    fun info(id: String) = "routine-$id-info"
    fun card(id: String) = "routine-$id-card"
    fun strip(id: String) = "routine-$id-strip"
    fun moveTo(folderId: String) = "move-to-folder-$folderId"
}

// Keys of the list's items that are not routines: none can equal a routine's id, because the ids are never prefixed.
private const val KEY_FOLDERS = "train:folders"
private const val KEY_EMPTY = "train:empty"
private const val KEY_ACTIONS = "train:actions"
private const val SKELETON_CARDS = 3

/** A creation or duplication whose routine has not come back in the state yet. */
private class EchoWait(val id: String, val baseline: Int, val duplicate: Boolean)

/**
 * The Train tab (spec "Treinar (aba)"): "Train" with the Settings shortcut ([onOpenSettings]); a row of folder
 * chips in their saved order, the selected one highlighted ([MoosclesActions.selectRoutineFolder]), and "Folders"
 * at its end; the cards of the selected folder's routines in their saved order ([RoutineCard]), which a long press
 * on the handle lifts and drags ([MoosclesActions.reorderRoutines], one write on release, with the four move actions
 * on the handle and in the card menu for the ones who cannot drag); and, at the end of the list, "New routine" and
 * "Empty workout". An empty folder shows an empty state offering "New routine"; while [UiState.loading] the list is
 * skeleton cards.
 *
 * "Start" goes through [actions]: with no workout under way it starts the routine and opens the workout
 * ([onOpenWorkout]); the routine the workout under way came from shows "Resume workout" and just opens it; any other
 * routine opens the dialog "A workout is in progress", which starts nothing. "Empty workout" starts an empty one
 * (or resumes the one under way). A card opens the editor ([onOpenRoutine]).
 *
 * "New routine" asks for a name (the default "New routine", selected, with the keyboard open) and creates the routine
 * in the selected folder under an id the screen chooses, then opens the editor once the routine is in the state; the
 * wait ends too when a command about that id fails ([UiState.subjectFailures] grows past what it was at the send)
 * or after `timing.saveEchoWait`, and is not kept across recreation (a recreated view model starts empty).
 * "Duplicate" waits the same way, then shows "Routine duplicated" with "Open". Errors show in the app's language
 * ([ScreenErrorToast]), never [UiState.error]. [now] is the clock of "Last done".
 */
@Composable
fun TrainRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenRoutine: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Long = ::currentTimeMillis,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    // Read when the routines change, not on every recomposition: the cards do not all redraw for a menu or a toast.
    val nowMillis = remember(state.routines) { now() }
    val folders = state.routineFolders
    val folder = folders.firstOrNull { it.id == state.preferences.selectedRoutineFolderId }
        ?: folders.firstOrNull { it.id == RoutineFolder.DEFAULT_ID }
        ?: folders.firstOrNull()
        ?: RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)
    val routines = remember(state.routines, folder.id) { state.routines.filter { it.folderId == folder.id } }
    val byId = remember(routines) { routines.associateBy { it.id } }
    val ids = remember(routines) { routines.map { it.id } }
    val active = state.active

    // What is open: ids of routines, kept across recreation. The wait for an echo is not (below).
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // The id of the workout the "in progress" dialog was opened for: it closes when that workout is not the one under way.
    var blockedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var managingFolders by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active?.id) { if (blockedFor != active?.id) blockedFor = null }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, ids, onDrop = { actions.reorderRoutines(folder.id, it) })
    val host = remember { MToastHostState() }
    val toastScope = rememberCoroutineScope()
    val openRoutine by rememberUpdatedState(onOpenRoutine)
    val duplicatedText = stringResource(Res.string.routines_duplicated)
    val openText = stringResource(Res.string.routines_open)
    val failedText = uiErrorText(UiError.SAVE_FAILED)
    val echoWaitMs = MTheme.timing.saveEchoWait

    // The echo of a creation or a duplication: the routine comes in the state (a creation opens the editor, a copy
    // shows its toast), or the failures counted about its id grow past the count at the send. A failure about another
    // id changes nothing. The toast comes from [toastScope], which outlives this effect (it ends with the wait).
    var wait by remember { mutableStateOf<EchoWait?>(null) }
    LaunchedEffect(wait, state.routines, state.subjectFailures) {
        val pending = wait ?: return@LaunchedEffect
        if (state.routines.any { it.id == pending.id }) {
            wait = null
            if (pending.duplicate) {
                toastScope.launch {
                    if (host.show(duplicatedText, MToastKind.Info, openText) == MToastResult.ActionPerformed) openRoutine(pending.id)
                }
            } else {
                openRoutine(pending.id)
            }
        } else if ((state.subjectFailures[pending.id] ?: 0) > pending.baseline) {
            wait = null
        }
    }
    // A command that was lost must not hold the screen for good.
    LaunchedEffect(wait) {
        val pending = wait ?: return@LaunchedEffect
        delay(echoWaitMs)
        if (wait === pending) {
            wait = null
            toastScope.launch { host.show(failedText, MToastKind.Error) }
        }
    }

    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val space = MTheme.space
    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(
                title = stringResource(Res.string.routines_title),
                actions = {
                    MIconButton(
                        MIcons.Settings,
                        contentDescription = stringResource(Res.string.routines_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(TrainTags.SETTINGS),
                    )
                },
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(TrainScreenTag),
                state = listState,
                contentPadding = PaddingValues(top = space.s2, bottom = bottom + space.s6),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                item(key = KEY_FOLDERS) {
                    FolderChips(folders, folder.id, actions::selectRoutineFolder, onManage = { managingFolders = true })
                }
                if (state.loading) {
                    items(SKELETON_CARDS, key = { "train:skeleton-$it" }) { RoutineCardSkeleton(Modifier.padding(horizontal = space.s4)) }
                    return@LazyColumn
                }
                if (ids.isEmpty()) {
                    item(key = KEY_EMPTY) {
                        EmptyState(
                            icon = MIcons.Dumbbell,
                            title = stringResource(Res.string.routines_empty_folder),
                            body = stringResource(Res.string.routines_empty_folder_body),
                            modifier = Modifier.testTag(TrainTags.EMPTY),
                            primaryAction = if (wait == null) stringResource(Res.string.routines_new) to { creating = true } else null,
                        )
                    }
                } else {
                    items(reorder.order, key = { it }) { id ->
                        val routine = byId[id] ?: return@items
                        RoutineCard(
                            routine = routine,
                            reorder = reorder,
                            resumes = active?.sourceRoutineId == routine.id,
                            startEnabled = !state.startingWorkout,
                            nowMillis = nowMillis,
                            zone = zone,
                            onOpen = { onOpenRoutine(routine.id) },
                            onStart = {
                                when {
                                    active?.sourceRoutineId == routine.id -> onOpenWorkout()
                                    active != null -> blockedFor = active.id
                                    else -> {
                                        actions.startWorkout(routine.id)
                                        onOpenWorkout()
                                    }
                                }
                            },
                            onMenu = { menuFor = routine.id },
                            modifier = Modifier.reorderItem(reorder, routine.id).padding(horizontal = space.s4),
                        )
                    }
                }
                item(key = KEY_ACTIONS) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = space.s4), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        // While a creation or a copy is on its way, "New routine" shows it (also in an empty folder, whose
                        // empty state then has no button of its own), and nothing opens a second sheet.
                        if (ids.isNotEmpty() || wait != null) {
                            MButton(
                                stringResource(Res.string.routines_new),
                                onClick = { if (wait == null) creating = true },
                                modifier = Modifier.fillMaxWidth().testTag(TrainTags.NEW_ROUTINE),
                                variant = MButtonVariant.Secondary,
                                size = MButtonSize.L,
                                icon = MIcons.Plus,
                                loading = wait != null,
                            )
                        }
                        MButton(
                            stringResource(if (active != null) Res.string.routines_resume else Res.string.routines_empty_workout),
                            onClick = {
                                if (active == null) actions.startWorkout(null)
                                onOpenWorkout()
                            },
                            modifier = Modifier.fillMaxWidth().testTag(TrainTags.EMPTY_WORKOUT),
                            variant = MButtonVariant.Text,
                            size = MButtonSize.L,
                            enabled = !state.startingWorkout,
                        )
                    }
                }
            }
        }
        ReorderAnnouncer(reorder)
        // While the folder manager is open it shows the errors itself (its window covers this one).
        ScreenErrorToast(if (managingFolders) null else state.errorCode, actions, host)
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }

    if (managingFolders) FolderManagerSheet(state, actions, onDismiss = { managingFolders = false })
    val newName = stringResource(Res.string.routines_new_name)
    if (creating) {
        RenameSheet(
            name = newName,
            onRename = { name ->
                if (wait == null) {
                    val id = newId()
                    wait = EchoWait(id, state.subjectFailures[id] ?: 0, duplicate = false)
                    actions.createRoutine(name, folder.id, id)
                }
            },
            onDismiss = { creating = false },
            title = stringResource(Res.string.routines_new),
            confirmLabel = stringResource(Res.string.routines_create),
            fieldLabel = stringResource(Res.string.routines_name_field),
            selectAll = true,
            submitUnchanged = true,
        )
    }
    menuFor?.let { id ->
        state.routines.firstOrNull { it.id == id }?.let { routine ->
            val order = reorder.order
            val copyTemplate = stringResource(Res.string.routines_copy_name, RoutineNames.NAME_TOKEN)
            val moveTo = reorderMover(reorder, id, routine.name)
            RoutineMenuSheet(
                routine = routine,
                index = order.indexOf(id),
                count = order.size,
                onEdit = {
                    menuFor = null
                    onOpenRoutine(id)
                },
                onRename = {
                    menuFor = null
                    renaming = id
                },
                duplicateEnabled = wait == null,
                onDuplicate = {
                    menuFor = null
                    if (wait == null) {
                        val copyId = newId()
                        wait = EchoWait(copyId, state.subjectFailures[copyId] ?: 0, duplicate = true)
                        actions.duplicateRoutine(id, RoutineNames.copyName(routine.name, copyTemplate), copyId)
                    }
                },
                onMoveToFolder = {
                    menuFor = null
                    moving = id
                },
                onMoveTo = { to ->
                    menuFor = null
                    moveTo(to)
                },
                onDelete = {
                    menuFor = null
                    deleting = id
                },
                onDismiss = { menuFor = null },
            )
        }
    }
    renaming?.let { id ->
        state.routines.firstOrNull { it.id == id }?.let { routine ->
            RenameSheet(
                name = routine.name,
                onRename = { actions.renameRoutine(id, it) },
                onDismiss = { renaming = null },
                title = stringResource(Res.string.routines_rename),
                fieldLabel = stringResource(Res.string.routines_name_field),
            )
        }
    }
    moving?.let { id ->
        state.routines.firstOrNull { it.id == id }?.let { routine ->
            MoveToFolderSheet(
                routine = routine,
                folders = folders,
                onMove = {
                    moving = null
                    actions.moveRoutineToFolder(id, it)
                },
                onDismiss = { moving = null },
            )
        }
    }
    deleting?.let { id ->
        state.routines.firstOrNull { it.id == id }?.let { routine ->
            DeleteRoutineDialog(
                routine = routine,
                onConfirm = {
                    deleting = null
                    actions.deleteRoutine(id)
                },
                onDismiss = { deleting = null },
            )
        }
    }
    if (blockedFor != null && active != null && active.id == blockedFor) {
        ActiveWorkoutDialog(
            activeName = active.name,
            onResume = {
                blockedFor = null
                onOpenWorkout()
            },
            onDismiss = { blockedFor = null },
        )
    }
}

/**
 * The folders as a scrolling row of chips, one choice of a group, in their saved order, and "Folders" at its end,
 * which opens the folder manager.
 */
@Composable
private fun FolderChips(folders: List<RoutineFolderUi>, selectedId: String, onSelect: (String) -> Unit, onManage: () -> Unit) {
    val space = MTheme.space
    LazyRow(
        Modifier.fillMaxWidth().testTag(TrainTags.FOLDER_ROW),
        contentPadding = PaddingValues(horizontal = space.s4),
        horizontalArrangement = Arrangement.spacedBy(space.s2),
    ) {
        items(folders, key = { it.id }) { folder ->
            MChip(
                folderName(folder),
                selected = folder.id == selectedId,
                onClick = { if (folder.id != selectedId) onSelect(folder.id) },
                modifier = Modifier.testTag(TrainTags.folder(folder.id)),
                kind = MChipKind.SetType,
            )
        }
        item(key = "train:manage") {
            MChip(
                stringResource(Res.string.routines_folders),
                selected = false,
                onClick = onManage,
                modifier = Modifier.testTag(TrainTags.MANAGE_FOLDERS),
                kind = MChipKind.Menu,
            )
        }
    }
}
