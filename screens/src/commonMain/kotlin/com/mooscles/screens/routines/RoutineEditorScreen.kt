package com.mooscles.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.data.newId
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.designsystem.components.MToastResult
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderAnnouncer
import com.mooscles.designsystem.interaction.ReorderState
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.rememberReorderState
import com.mooscles.designsystem.interaction.reorderMover
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.editor_about
import com.mooscles.screens.resources.editor_add_exercises
import com.mooscles.screens.resources.editor_delete
import com.mooscles.screens.resources.editor_empty
import com.mooscles.screens.resources.editor_empty_body
import com.mooscles.screens.resources.editor_more
import com.mooscles.screens.resources.editor_remove_body
import com.mooscles.screens.resources.editor_remove_confirm
import com.mooscles.screens.resources.editor_remove_exercise
import com.mooscles.screens.resources.editor_remove_title
import com.mooscles.screens.resources.editor_rest_note
import com.mooscles.screens.resources.editor_replace
import com.mooscles.screens.resources.editor_replace_body
import com.mooscles.screens.resources.editor_replace_confirm
import com.mooscles.screens.resources.editor_replace_title
import com.mooscles.screens.resources.editor_set_copied
import com.mooscles.screens.resources.editor_set_limit
import com.mooscles.screens.resources.editor_warmup_removed
import com.mooscles.screens.resources.editor_start
import com.mooscles.screens.resources.workout_set_removed
import com.mooscles.screens.resources.workout_undo
import com.mooscles.screens.resources.routines_cancel
import com.mooscles.screens.resources.routines_copy_name
import com.mooscles.screens.resources.routines_duplicate
import com.mooscles.screens.resources.routines_duplicated
import com.mooscles.screens.resources.routines_move_to_folder
import com.mooscles.screens.resources.routines_name_field
import com.mooscles.screens.resources.routines_open
import com.mooscles.screens.resources.routines_rename
import com.mooscles.screens.resources.routines_resume
import com.mooscles.screens.workout.ExercisePickerSheet
import com.mooscles.screens.workout.MenuItem
import com.mooscles.screens.workout.PickerMode
import com.mooscles.screens.workout.RenameSheet
import com.mooscles.screens.workout.RestSheet
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.uiErrorText
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DesignRes
import com.mooscles.designsystem.resources.ds_reorder_bottom
import com.mooscles.designsystem.resources.ds_reorder_down
import com.mooscles.designsystem.resources.ds_reorder_top
import com.mooscles.designsystem.resources.ds_reorder_up

/** The tag of the editor's list (the old screen's, kept for the shell's tests). */
const val RoutineEditorTag = "routine-editor-list"

/** The test tags of the routine editor. */
internal object RoutineEditorTags {
    const val START = "routine-editor-start"
    const val NAME = "routine-editor-name"
    const val MENU = "routine-editor-menu"
    const val ADD = "routine-editor-add"
    const val EMPTY = "routine-editor-empty"
    const val SKELETON = "routine-editor-skeleton"

    /** The scrolling items of the top menu and of an exercise's menu. */
    const val MENU_LIST = "routine-editor-menu-list"
    const val MENU_RENAME = "routine-editor-menu-rename"
    const val MENU_DUPLICATE = "routine-editor-menu-duplicate"
    const val MENU_MOVE = "routine-editor-menu-move"
    const val MENU_DELETE = "routine-editor-menu-delete"
    val MENU_ITEMS = listOf(MENU_RENAME, MENU_DUPLICATE, MENU_MOVE, MENU_DELETE)

    const val EXERCISE_MENU_LIST = "routine-exercise-menu-list"
    const val EXERCISE_REPLACE = "routine-exercise-menu-replace"
    const val EXERCISE_ABOUT = "routine-exercise-menu-about"
    const val EXERCISE_UP = "routine-exercise-menu-up"
    const val EXERCISE_DOWN = "routine-exercise-menu-down"
    const val EXERCISE_TOP = "routine-exercise-menu-top"
    const val EXERCISE_BOTTOM = "routine-exercise-menu-bottom"
    const val EXERCISE_REMOVE = "routine-exercise-menu-remove"
    val EXERCISE_ITEMS = listOf(EXERCISE_REPLACE, EXERCISE_ABOUT, EXERCISE_UP, EXERCISE_DOWN, EXERCISE_TOP, EXERCISE_BOTTOM, EXERCISE_REMOVE)

    fun header(entryId: String) = "routine-exercise-$entryId-header"
    fun handle(entryId: String) = "routine-exercise-$entryId-handle"
    fun menu(entryId: String) = "routine-exercise-$entryId-menu"
    fun rest(entryId: String) = "routine-exercise-$entryId-rest"
    fun footer(entryId: String) = "routine-exercise-$entryId-footer"
    fun set(setId: String) = "routine-set-$setId"
    fun setLoad(setId: String) = "routine-set-$setId-load"
    fun setReps(setId: String) = "routine-set-$setId-reps"
    fun setLabel(setId: String) = "routine-set-$setId-label"
    fun setRecord(setId: String) = "routine-set-$setId-record"
    fun addSet(entryId: String) = "routine-exercise-$entryId-add-set"
}

// Keys of the list's items that are not exercises: none can equal an entry id, which the reorder state uses as its keys.
private const val KEY_NAME = "editor:name"
private const val KEY_EMPTY = "editor:empty"
private const val KEY_ADD = "editor:add"
private const val KEY_SKELETON = "editor:skeleton"

/** Stands for the number of a removed set in the toast's text, which is read before the number is known. */
private const val SET_NUMBER_TOKEN = "\u0000"

/** What a copy or an addition is waiting to see in the state. */
private enum class WaitKind { COPY, ADD }

/**
 * A command whose echo the screen waits for: [subject] is the id its failures are counted under, [failures] the count
 * at the send, and what the state must hold to show the echo: a copy, the routine with id [subject]; an addition,
 * [expected] entries whose ids are not in [before], the entry ids at the send.
 */
private class EditorWait(
    val kind: WaitKind,
    val subject: String,
    val failures: Int,
    val expected: Int = 0,
    val before: Set<String> = emptySet(),
)

/**
 * The routine editor (spec "Editor de rotina"): the bar holds "Back" and the routine's options (Rename, Duplicate,
 * Move to folder, Delete routine), the routine's name is the first line of the scrolling list (tapping it renames),
 * then one card per exercise ([routineExerciseItems]), "Add exercises" at the end and a fixed "Start workout" under
 * the list. Every change writes at once; nothing here is saved by a button.
 *
 * A long press on a card's handle lifts it and drags it; the cards then show only their headers (plan decision 14)
 * and one `reorderRoutineExercises` writes the order on release. The four moves are also actions of the handle and
 * items of the card's menu, which hold the new order and announce it. The menu has Replace exercise (asking first when
 * the exercise has manual targets, which a replacement sets back to following the record), About the exercise
 * ([onExerciseInfo]), the moves and Remove from routine (asking first, and naming the sets that go).
 *
 * "Start workout" follows the card's rules (plan decision 9): with no workout under way it starts the routine and
 * opens the workout ([onOpenWorkout]); the routine the workout under way came from shows "Resume workout" and only
 * opens it; any other routine opens the dialog "A workout is in progress", which starts nothing.
 *
 * Waits (F10): "Add exercises" is busy until the routine holds the added exercises, and a duplicate until its copy
 * is in the state (then "Routine duplicated"). Each ends too when a command about its subject fails
 * ([UiState.subjectFailures] grows past what it was at the send) or after `timing.saveEchoWait`, and none is kept
 * across recreation. The addition's subject is the routine's own id, which other commands about it share: a failure
 * of an earlier one may release the wait early, which only frees the button. Deleting the routine needs no wait of
 * its own: the screen leaves when the routine is gone from the state, which is also how it leaves for a routine
 * deleted elsewhere ([onGone], once, and never while [UiState.loading], when the routines may not have come yet).
 * Errors show in the app's language ([ScreenErrorToast]).
 */
@Composable
fun RoutineEditorRoute(
    routineId: String,
    state: UiState,
    actions: MoosclesActions,
    onBack: () -> Unit,
    onGone: () -> Unit,
    onOpenWorkout: () -> Unit,
    onExerciseInfo: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRoutine: ((routineId: String) -> Unit)? = null,
) {
    val routine = remember(state.routines, routineId) { state.routines.firstOrNull { it.id == routineId } }
    val entries = routine?.exercises.orEmpty()
    val ids = remember(entries) { entries.map { it.id } }
    val byId = remember(entries) { entries.associateBy { it.id } }
    val pounds = state.preferences.pounds
    val active = state.active

    // The routine is gone (deleted here or elsewhere): leave, once. While the state loads, an empty list means nothing.
    val gone = !state.loading && routine == null
    val leave by rememberUpdatedState(onGone)
    LaunchedEffect(gone) { if (gone) leave() }

    // What is open: ids and flags, kept across recreation. A wait for an echo is not (below).
    var topMenu by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var restFor by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReplace by rememberSaveable { mutableStateOf<String?>(null) }
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    // The planned set whose menu is open (its id), and the set in edit (its id and field; the keyboard is not kept).
    var setMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = rememberPlannedEditing()
    // The id of the workout the "in progress" dialog was opened for: it closes when that workout is not the one under way.
    var blockedFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.id) { if (blockedFor != active?.id) blockedFor = null }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, ids, onDrop = { actions.reorderRoutineExercises(routineId, it) })
    val shown = remember(reorder.order, byId) { reorder.order.mapNotNull { byId[it] } }
    val host = remember { MToastHostState() }
    val toastScope = rememberCoroutineScope()
    val duplicatedText = stringResource(Res.string.routines_duplicated)
    val openText = stringResource(Res.string.routines_open)
    val openRoutine by rememberUpdatedState(onOpenRoutine)
    val currentActions by rememberUpdatedState(actions)
    val failedText = uiErrorText(UiError.SAVE_FAILED)
    val echoWaitMs = MTheme.timing.saveEchoWait

    // The echo of a copy or an addition: the state shows it, or the failures counted about its subject grow past the
    // count at the send. A failure about another id changes nothing. The toast comes from [toastScope], which outlives
    // this effect (it ends with the wait).
    var wait by remember { mutableStateOf<EditorWait?>(null) }
    LaunchedEffect(wait, state.routines, state.subjectFailures) {
        val pending = wait ?: return@LaunchedEffect
        val echoed = when (pending.kind) {
            WaitKind.COPY -> state.routines.any { it.id == pending.subject }
            WaitKind.ADD -> entries.count { it.id !in pending.before } >= pending.expected
        }
        if (routine == null || echoed) {
            wait = null
            if (echoed && pending.kind == WaitKind.COPY) {
                toastScope.launch {
                    val open = openRoutine
                    if (open == null) {
                        host.show(duplicatedText, MToastKind.Info)
                    } else if (host.show(duplicatedText, MToastKind.Info, openText) == MToastResult.ActionPerformed) {
                        open(pending.subject)
                    }
                }
            }
        } else if ((state.subjectFailures[pending.subject] ?: 0) > pending.failures) {
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

    // The drafts of the planned sets: in memory only (a value nobody confirmed is never saved by a recreation), keyed
    // on the unit and the number format, and reading the sets from the state as it is now.
    val locale = currentNumberLocale()
    val sets = remember(entries) { entries.flatMap { it.setTargets }.associateBy { it.id } }
    val currentSets by rememberUpdatedState(sets)
    val drafts = remember(pounds, locale) { PlannedSetDrafts(pounds, locale) { currentSets[it] } }
    // Sets that are gone (removed here or elsewhere) take their drafts and their edit with them; so does a replaced
    // exercise (its sets keep their ids but are another movement's: nothing typed for the old one may be written).
    val exerciseOf = remember { HashMap<String, String>() }
    LaunchedEffect(entries, drafts) {
        if (state.loading) return@LaunchedEffect
        drafts.retain(sets.keys)
        for (entry in entries) {
            val before = exerciseOf.put(entry.id, entry.exercise.id)
            if (before != null && before != entry.exercise.id) entry.setTargets.forEach { drafts.drop(it.id) }
        }
        editing.setId?.let { if (it !in sets) editing.end() }
    }
    val removedTemplate = stringResource(Res.string.workout_set_removed, SET_NUMBER_TOKEN)
    val warmupRemovedTemplate = stringResource(Res.string.editor_warmup_removed, SET_NUMBER_TOKEN)
    val undoText = stringResource(Res.string.workout_undo)
    var undoJob by remember { mutableStateOf<Job?>(null) }
    // The entry and exercise the pending undo belongs to: once the entry holds another exercise (a replacement keeps
    // the entry and set ids), the set would come back into a different movement, so the offer is withdrawn.
    var undoFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The sets a pending copy changed: once one of them is edited or follows the record again, the copy's undo would
    // wipe that out, so the offer is withdrawn (the toast closes).
    var copyTargets by remember { mutableStateOf<Set<String>>(emptySet()) }
    val withdrawCopyUndo: (String) -> Unit = { setId ->
        if (setId in copyTargets) {
            undoJob?.cancel()
            copyTargets = emptySet()
        }
    }
    LaunchedEffect(entries) {
        val (entryId, exerciseId) = undoFor ?: return@LaunchedEffect
        val entry = entries.firstOrNull { it.id == entryId }
        if (!state.loading && entry != null && entry.exercise.id != exerciseId) {
            undoJob?.cancel()
            undoFor = null
        }
    }
    // A write failed: none in flight will be echoed. The rows not in edit show the saved values again at once; the one
    // in edit does when it is left.
    val failedCode = state.errorCode?.takeUnless { it == UiError.SET_LIMIT || it == UiError.SET_NOT_RESTORED }
    LaunchedEffect(failedCode) {
        if (failedCode != null) {
            drafts.forgetWrites()
            sets.keys.filterNot { editing.isEditing(it) }.forEach(drafts::settle)
        }
    }
    val callbacks = remember(drafts) {
        RoutineExerciseCallbacks(
            onMenu = { menuFor = it.id },
            onRest = { restFor = it.id },
            onAddSet = { currentActions.addRoutineSet(it.id) },
            drafts = drafts,
            editing = editing,
            onWrite = {
                withdrawCopyUndo(it.setId)
                currentActions.updateRoutineSet(it.setId, it.reps, it.weightKg)
            },
            onSetMenu = { _, setId ->
                editing.end()
                setMenuFor = setId
            },
        )
    }
    // "Set 3 removed" with "Undo", which puts the set back as it stood (F15). Launched from the screen's own scope, not
    // from an effect, and a newer removal takes the toast: the previous one closes.
    val removeSet: (PlannedExerciseUi, RoutineSetUi, Int, Int) -> Unit = { entry, set, index, number ->
        val snapshot = drafts.snapshot(set)
        drafts.drop(set.id)
        if (editing.isEditing(set.id)) editing.end()
        currentActions.removeRoutineSet(set.id)
        undoJob?.cancel()
        copyTargets = emptySet()
        undoFor = entry.id to entry.exercise.id
        val template = if (set.warmup) warmupRemovedTemplate else removedTemplate
        undoJob = toastScope.launch {
            val shown = host.show(template.replace(SET_NUMBER_TOKEN, number.toString()), MToastKind.Info, undoText)
            if (shown == MToastResult.ActionPerformed) currentActions.restoreRoutineSet(entry.id, index, snapshot)
        }
    }
    // "Copied to 2 sets" with "Undo" (2026-10-31, owner's request): the source's load and reps on the exercise's other
    // working sets, in one command, and the undo puts each back as it stood, in one command too. The toast comes from
    // the screen's own scope and takes the place of a removal's (and the other way round).
    val currentEntries by rememberUpdatedState(entries)
    val currentDrafts by rememberUpdatedState(drafts)
    val notRestoredText = uiErrorText(UiError.SET_NOT_RESTORED)
    val copySet: (PlannedExerciseUi, RoutineSetUi, String) -> Unit = { entry, source, copiedText ->
        val targets = entry.setTargets.filter { !it.warmup && it.id != source.id }
        // What "Undo" puts back: each set as the database holds it when the copy runs, the writes in flight included.
        val previous = targets.map(drafts::snapshot)
        // Nothing typed for a target is written after the copy or shown in place of it.
        targets.forEach { drafts.drop(it.id) }
        editing.setId?.let { id -> if (targets.any { it.id == id }) editing.end() }
        currentActions.copyRoutineSetToAll(entry.id, source.id)
        // The entry as the copy found it: the undo is only for the same sets of the same exercise.
        val shape = entry.setTargets.map { it.id to it.warmup }
        val exerciseId = entry.exercise.id
        undoJob?.cancel()
        // A replaced exercise withdraws the offer, as for a removal ([undoFor]); an edit of a copied set does too.
        undoFor = entry.id to exerciseId
        val changed: Set<String> = targets.mapTo(HashSet()) { it.id }
        copyTargets = changed
        undoJob = toastScope.launch {
            val chosen = try {
                host.show(copiedText, MToastKind.Info, undoText)
            } finally {
                // Only this copy's: a newer one may already have put its own sets here before this job ends.
                if (copyTargets === changed) copyTargets = emptySet()
            }
            if (chosen == MToastResult.ActionPerformed) {
                val now = currentEntries.firstOrNull { it.id == entry.id }
                if (now != null && now.exercise.id == exerciseId && now.setTargets.map { it.id to it.warmup } == shape) {
                    // A safety belt: an edit of a target withdrew the offer, so no draft of one should be left here.
                    previous.forEach { currentDrafts.drop(it.id) }
                    currentActions.restoreRoutineSetTargets(entry.id, exerciseId, previous)
                } else {
                    host.show(notRestoredText, MToastKind.Error)
                }
            }
        }
    }
    val space = MTheme.space
    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase).endsPlannedEditingOnOutsideTap(editing)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                title = null,
                onBack = onBack,
                actions = {
                    Spacer(Modifier.weight(1f))
                    if (routine != null) {
                        MIconButton(
                            MIcons.EllipsisVertical,
                            contentDescription = stringResource(Res.string.editor_more),
                            onClick = { topMenu = true },
                            modifier = Modifier.testTag(RoutineEditorTags.MENU),
                        )
                    }
                },
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(RoutineEditorTag),
                state = listState,
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s2, bottom = space.s4),
                // While one is dragged the cards are headers alone and the list spaces them (the lifted header is the
                // lazy item's root, so nothing wraps it); open, the segments of a card touch.
                verticalArrangement = if (reorder.dragging != null) Arrangement.spacedBy(space.s3) else Arrangement.Top,
            ) {
                if (routine == null) {
                    item(key = KEY_SKELETON) { EditorSkeleton() }
                    return@LazyColumn
                }
                item(key = KEY_NAME) { RoutineName(routine.name, onRename = { renaming = true }) }
                if (entries.isEmpty()) {
                    item(key = KEY_EMPTY) {
                        EmptyState(
                            icon = MIcons.Dumbbell,
                            title = stringResource(Res.string.editor_empty),
                            body = stringResource(Res.string.editor_empty_body),
                            modifier = Modifier.testTag(RoutineEditorTags.EMPTY),
                        )
                    }
                } else {
                    routineExerciseItems(
                        entries = shown,
                        reorder = reorder,
                        // One item per exercise while one is dragged (decision 14), so the reorderable items touch.
                        collapsed = reorder.dragging != null,
                        pounds = pounds,
                        callbacks = callbacks,
                    )
                }
                item(key = KEY_ADD) {
                    MButton(
                        stringResource(Res.string.editor_add_exercises),
                        onClick = { if (wait == null) adding = true },
                        modifier = Modifier.fillMaxWidth().testTag(RoutineEditorTags.ADD),
                        variant = MButtonVariant.Secondary,
                        size = MButtonSize.L,
                        icon = MIcons.Plus,
                        loading = wait != null,
                    )
                }
            }
            if (routine != null) {
                val resumes = active?.sourceRoutineId == routine.id
                MButton(
                    stringResource(if (resumes) Res.string.routines_resume else Res.string.editor_start),
                    onClick = {
                        when {
                            resumes -> onOpenWorkout()
                            active != null -> blockedFor = active.id
                            else -> {
                                actions.startWorkout(routine.id)
                                onOpenWorkout()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(space.s4).testTag(RoutineEditorTags.START),
                    variant = MButtonVariant.Primary,
                    size = MButtonSize.L,
                    icon = MIcons.Play,
                    enabled = !state.startingWorkout,
                )
            }
        }
        ReorderAnnouncer(reorder)
        // The 51st set coming back says this screen's limit (50), not the workout's.
        val setLimit = state.errorCode == UiError.SET_LIMIT
        ScreenErrorToast(state.errorCode.takeUnless { setLimit }, actions, host)
        val setLimitText = stringResource(Res.string.editor_set_limit)
        LaunchedEffect(setLimit) {
            if (setLimit) {
                actions.clearError()
                toastScope.launch { host.show(setLimitText, MToastKind.Error) }
            }
        }
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
    }

    if (routine != null) {
        if (topMenu) {
            val copyTemplate = stringResource(Res.string.routines_copy_name, RoutineNames.NAME_TOKEN)
            EditorMenuSheet(
                routineName = routine.name,
                duplicateEnabled = wait == null,
                onRename = {
                    topMenu = false
                    renaming = true
                },
                onDuplicate = {
                    topMenu = false
                    if (wait == null) {
                        val copyId = newId()
                        wait = EditorWait(WaitKind.COPY, copyId, state.subjectFailures[copyId] ?: 0)
                        actions.duplicateRoutine(routine.id, RoutineNames.copyName(routine.name, copyTemplate), copyId)
                    }
                },
                onMoveToFolder = {
                    topMenu = false
                    moving = true
                },
                onDelete = {
                    topMenu = false
                    deleting = true
                },
                onDismiss = { topMenu = false },
            )
        }
        if (renaming) {
            RenameSheet(
                name = routine.name,
                onRename = { actions.renameRoutine(routine.id, it) },
                onDismiss = { renaming = false },
                title = stringResource(Res.string.routines_rename),
                fieldLabel = stringResource(Res.string.routines_name_field),
            )
        }
        if (moving) {
            MoveToFolderSheet(
                routine = routine,
                folders = state.routineFolders,
                onMove = {
                    moving = false
                    actions.moveRoutineToFolder(routine.id, it)
                },
                onDismiss = { moving = false },
            )
        }
        if (deleting) {
            DeleteRoutineDialog(
                routine = routine,
                onConfirm = {
                    deleting = false
                    actions.deleteRoutine(routine.id)
                },
                onDismiss = { deleting = false },
            )
        }
        if (adding) {
            ExercisePickerSheet(
                exercises = state.exercises,
                history = state.history,
                onPick = {},
                onDismiss = { adding = false },
                mode = PickerMode.Multiple,
                onPickMany = { picked ->
                    // The picker never reports an empty selection, and nothing is sent for one.
                    if (picked.isNotEmpty() && wait == null) {
                        wait = EditorWait(
                            WaitKind.ADD, routine.id, state.subjectFailures[routine.id] ?: 0,
                            expected = picked.size, before = entries.mapTo(HashSet()) { it.id },
                        )
                        actions.addRoutineExercises(routine.id, picked.map { it.id })
                    }
                },
            )
        }
    }

    setMenuFor?.let { id ->
        val entry = entries.firstOrNull { e -> e.setTargets.any { it.id == id } }
        val set = entry?.setTargets?.firstOrNull { it.id == id }
        if (entry == null || set == null) {
            if (!state.loading) LaunchedEffect(id) { setMenuFor = null }
        } else {
            val index = entry.setTargets.indexOf(set)
            val number = entry.setTargets.take(index + 1).count { it.warmup == set.warmup }
            val others = entry.setTargets.count { !it.warmup && it.id != set.id }
            val copiedText = pluralCount(Res.plurals.editor_set_copied, others)
            PlannedSetMenuSheet(
                set = set,
                number = number,
                last = entry.setTargets.size <= 1,
                onNormal = {
                    setMenuFor = null
                    if (set.warmup) actions.setRoutineSetWarmup(set.id, false)
                },
                onWarmup = {
                    setMenuFor = null
                    if (!set.warmup) actions.setRoutineSetWarmup(set.id, true)
                },
                onFollowRecord = {
                    setMenuFor = null
                    withdrawCopyUndo(set.id)
                    // The person's own edits of this set give way to the record, and a late echo of them must not bring them back.
                    drafts.drop(set.id)
                    actions.inheritRoutineSet(set.id)
                },
                copyToAll = others > 0,
                onCopyToAll = copy@{
                    // A second tap before the sheet leaves would copy again and take the undo's snapshot anew,
                    // without the writes in flight the first one dropped.
                    if (setMenuFor != id) return@copy
                    setMenuFor = null
                    copySet(entry, set, copiedText)
                },
                onRemove = {
                    setMenuFor = null
                    removeSet(entry, set, index, number)
                },
                onDismiss = { setMenuFor = null },
            )
        }
    }

    ExerciseOverlays(
        menuFor = menuFor,
        restFor = restFor,
        removing = removing,
        confirmReplace = confirmReplace,
        replacing = replacing,
        entries = byId,
        reorder = reorder,
        state = state,
        actions = actions,
        onMenuFor = { menuFor = it },
        onRestFor = { restFor = it },
        onRemoving = { removing = it },
        onConfirmReplace = { confirmReplace = it },
        onReplacing = { replacing = it },
        onExerciseInfo = onExerciseInfo,
    )

    if (routine != null && blockedFor != null && active != null && active.id == blockedFor) {
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
 * The sheets and dialogs about one exercise of the routine: its menu, rest sheet, the two confirmations and the
 * picker of a replacement. Each looks its entry up in [entries] and, once the entry is gone (removed here or elsewhere),
 * closes by itself.
 */
@Composable
private fun ExerciseOverlays(
    menuFor: String?,
    restFor: String?,
    removing: String?,
    confirmReplace: String?,
    replacing: String?,
    entries: Map<String, PlannedExerciseUi>,
    reorder: ReorderState<String>,
    state: UiState,
    actions: MoosclesActions,
    onMenuFor: (String?) -> Unit,
    onRestFor: (String?) -> Unit,
    onRemoving: (String?) -> Unit,
    onConfirmReplace: (String?) -> Unit,
    onReplacing: (String?) -> Unit,
    onExerciseInfo: (String) -> Unit,
) {
    menuFor?.let { id ->
        val entry = entries[id]
        if (entry == null) {
            if (!state.loading) LaunchedEffect(id) { onMenuFor(null) }
        } else {
            val order = reorder.order
            val moveTo = reorderMover(reorder, id, exerciseName(entry.exercise))
            ExerciseMenuSheet(
                entry = entry,
                index = order.indexOf(id),
                count = order.size,
                onReplace = {
                    onMenuFor(null)
                    // A replacement sends manual working sets back to following the record and empties every warm-up
                    // (they stay warm-ups, but without values): ask when either would lose something.
                    if (entry.setTargets.any { if (it.warmup) it.reps > 0 || it.weightKg != null else !it.inherited }) {
                        onConfirmReplace(id)
                    } else {
                        onReplacing(id)
                    }
                },
                onAbout = {
                    onMenuFor(null)
                    onExerciseInfo(entry.exercise.id)
                },
                onMoveTo = { to ->
                    onMenuFor(null)
                    moveTo(to)
                },
                onRemove = {
                    onMenuFor(null)
                    onRemoving(id)
                },
                onDismiss = { onMenuFor(null) },
            )
        }
    }
    restFor?.let { id ->
        val entry = entries[id]
        if (entry == null) {
            if (!state.loading) LaunchedEffect(id) { onRestFor(null) }
        } else {
            RestSheet(
                exerciseName = exerciseName(entry.exercise),
                restSeconds = entry.restSeconds,
                key = entry.id,
                onSave = { actions.updateRoutineExerciseRest(entry.id, it) },
                onStartNow = null,
                note = stringResource(Res.string.editor_rest_note),
                onDismiss = { onRestFor(null) },
            )
        }
    }
    removing?.let { id ->
        val entry = entries[id]
        if (entry == null) {
            if (!state.loading) LaunchedEffect(id) { onRemoving(null) }
        } else {
            MDialog(
                title = stringResource(Res.string.editor_remove_title, exerciseName(entry.exercise)),
                text = pluralCount(Res.plurals.editor_remove_body, entry.sets, entry.sets),
                confirmLabel = stringResource(Res.string.editor_remove_confirm),
                onConfirm = {
                    onRemoving(null)
                    actions.removeRoutineExercise(entry.id)
                },
                onDismiss = { onRemoving(null) },
                dismissLabel = stringResource(Res.string.routines_cancel),
                destructive = true,
            )
        }
    }
    confirmReplace?.let { id ->
        val entry = entries[id]
        if (entry == null) {
            if (!state.loading) LaunchedEffect(id) { onConfirmReplace(null) }
        } else {
            MDialog(
                title = stringResource(Res.string.editor_replace_title),
                text = stringResource(Res.string.editor_replace_body),
                confirmLabel = stringResource(Res.string.editor_replace_confirm),
                onConfirm = {
                    onConfirmReplace(null)
                    onReplacing(id)
                },
                onDismiss = { onConfirmReplace(null) },
                dismissLabel = stringResource(Res.string.routines_cancel),
                destructive = true,
            )
        }
    }
    replacing?.let { id ->
        val entry = entries[id]
        if (entry == null) {
            if (!state.loading) LaunchedEffect(id) { onReplacing(null) }
        } else {
            ExercisePickerSheet(
                exercises = state.exercises,
                history = state.history,
                onPick = { actions.replaceRoutineExercise(entry.id, it.id) },
                onDismiss = { onReplacing(null) },
            )
        }
    }
}

/** The routine's name as the first line of the list, whole (any length), one button that renames. */
@Composable
private fun RoutineName(name: String, onRename: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MTheme.radius.md)
    MText(
        name,
        MTheme.type.titleMd,
        color = MTheme.colors.textPrimary,
        modifier = Modifier
            .testTag(RoutineEditorTags.NAME)
            .fillMaxWidth()
            .padding(bottom = MTheme.space.s3)
            .mFocusRing(shape, source)
            .semantics { heading() }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.routines_rename),
                onClick = onRename,
            )
            .heightIn(min = MTheme.targets.min)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

/** Bars where the name and the cards will be, while the state loads. */
@Composable
private fun EditorSkeleton() {
    val space = MTheme.space
    Column(Modifier.testTag(RoutineEditorTags.SKELETON).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.s3)) {
        Skeleton(Modifier.fillMaxWidth(NAME_BAR).height(space.s6))
        Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.buttonL))
        Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.buttonL))
    }
}

private const val NAME_BAR = 0.5f

/**
 * The routine's options, a bottom sheet under its name: Rename, Duplicate (off while [duplicateEnabled] is false: a
 * copy is on its way), Move to folder and Delete routine (destructive, last). The items scroll under the title when the
 * sheet is shorter than they are (large text, landscape). Each closes the menu and then does its part.
 */
@Composable
private fun EditorMenuSheet(
    routineName: String,
    duplicateEnabled: Boolean,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(routineName)
        Column(Modifier.testTag(RoutineEditorTags.MENU_LIST).verticalScroll(rememberScrollState())) {
            MenuItem(MIcons.Pencil, stringResource(Res.string.routines_rename), RoutineEditorTags.MENU_RENAME, onClick = onRename)
            MenuItem(
                MIcons.Copy, stringResource(Res.string.routines_duplicate), RoutineEditorTags.MENU_DUPLICATE,
                enabled = duplicateEnabled, onClick = onDuplicate,
            )
            MenuItem(MIcons.FolderInput, stringResource(Res.string.routines_move_to_folder), RoutineEditorTags.MENU_MOVE, onClick = onMoveToFolder)
            MenuItem(
                MIcons.Trash2, stringResource(Res.string.editor_delete), RoutineEditorTags.MENU_DELETE,
                destructive = true, onClick = onDelete,
            )
            Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}

/**
 * The menu of an exercise card, a bottom sheet under the exercise's name: Replace exercise, About the exercise, Move up,
 * Move down, Move to top, Move to bottom and Remove from routine (destructive, last). "Up" and "top" are off for the
 * first exercise ([index] 0), "down" and "bottom" for the last of [count]. The items scroll under the title.
 */
@Composable
private fun ExerciseMenuSheet(
    entry: PlannedExerciseUi,
    index: Int,
    count: Int,
    onReplace: () -> Unit,
    onAbout: () -> Unit,
    onMoveTo: (position: Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = index <= 0
    val last = index >= count - 1
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(exerciseName(entry.exercise))
        Column(Modifier.testTag(RoutineEditorTags.EXERCISE_MENU_LIST).verticalScroll(rememberScrollState())) {
            MenuItem(MIcons.ArrowLeftRight, stringResource(Res.string.editor_replace), RoutineEditorTags.EXERCISE_REPLACE, onClick = onReplace)
            MenuItem(MIcons.Dumbbell, stringResource(Res.string.editor_about), RoutineEditorTags.EXERCISE_ABOUT, onClick = onAbout)
            MenuItem(
                MIcons.ArrowUp, stringResource(DesignRes.string.ds_reorder_up), RoutineEditorTags.EXERCISE_UP,
                enabled = !first, onClick = { onMoveTo(index - 1) },
            )
            MenuItem(
                MIcons.ArrowDown, stringResource(DesignRes.string.ds_reorder_down), RoutineEditorTags.EXERCISE_DOWN,
                enabled = !last, onClick = { onMoveTo(index + 1) },
            )
            MenuItem(
                MIcons.ArrowUpToLine, stringResource(DesignRes.string.ds_reorder_top), RoutineEditorTags.EXERCISE_TOP,
                enabled = !first, onClick = { onMoveTo(0) },
            )
            MenuItem(
                MIcons.ArrowDownToLine, stringResource(DesignRes.string.ds_reorder_bottom), RoutineEditorTags.EXERCISE_BOTTOM,
                enabled = !last, onClick = { onMoveTo(count - 1) },
            )
            MenuItem(
                MIcons.Trash2, stringResource(Res.string.editor_remove_exercise), RoutineEditorTags.EXERCISE_REMOVE,
                destructive = true, onClick = onRemove,
            )
            Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}
