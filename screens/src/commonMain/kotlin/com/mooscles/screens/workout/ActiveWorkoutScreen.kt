package com.mooscles.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_add_exercise
import com.mooscles.screens.resources.workout_choose_routine
import com.mooscles.screens.resources.workout_empty_workout
import com.mooscles.screens.resources.workout_finish_workout
import com.mooscles.screens.resources.workout_minimize
import com.mooscles.screens.resources.workout_none_body
import com.mooscles.screens.resources.workout_none_title
import com.mooscles.screens.resources.workout_opening
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiError
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The test tags of the screen's parts. */
internal object ActiveWorkoutTags {
    const val ROUTE = "workout-route"
    const val LIST = "workout-list"
    const val END = "workout-end"
    const val ADD_EXERCISE = "workout-add-exercise"
    const val FINISH_WORKOUT = "workout-finish-workout"
    const val FINISH_NOTE = "workout-finish-note"
    const val SKELETON = "workout-skeleton"
    const val SKELETON_CARD = "workout-skeleton-card"
    const val NO_WORKOUT = "workout-none"
}

/** The exercise picker's request for adding an exercise; any other value is the entry being swapped. */
private const val PICK_TO_ADD = ""

/**
 * The active workout (spec "Tela do treino ativo"): the fixed [WorkoutHeader] and, under it, the
 * list of exercise cards ([exerciseCards]) with "Add exercise" and "Finish workout" at its end,
 * at most `contentMaxWidth` wide and centered, with `space.4` at the sides. The list has no
 * spacing of its own (the cards make their gaps) and keeps clear of the keyboard ([imeInsets]).
 *
 * Finishing without a done set vibrates `Rejected` and shows "Complete at least one set to
 * finish." on [host], with no dialog; with one, it asks ([FinishDialog]), and the confirmation
 * closes the undo toast, ends the edit and calls [onConfirmFinish] (the route then waits for the
 * summary). Discarding asks ([DiscardDialog]), closes the undo toast and calls `discardWorkout`.
 * Beside the list: the card advance, the set type sheet, the rest sheet, the exercise picker
 * (adding, or swapping from a card's menu), "How to log" and the rename sheet. The sheets that
 * look an entry up are put only once [loaded]: before, an empty list would close them. While
 * [finishing] or [discarding], nothing else can be started.
 *
 * It takes the parts of the state it shows, not the whole `UiState`, so an emission that changes
 * something else (the updater, the backup) does not recompose it. [catalog] and [history] are
 * the exercise picker's. Under the list, [WorkoutTimerLayer] holds the rest timer, the only part
 * that reads [timer], and the keyboard's room: the list ends above both. When the expanded timer
 * closes after the rest, the list goes back to the next set ([returnToSet]); [onTimerHeight]
 * gets the timer's height, so the route's toasts sit above it. A card menu's "About the exercise"
 * calls [onExerciseInfo] with the catalog exercise's id, "Exercise history" [onExerciseHistory].
 */
@Composable
internal fun ActiveWorkoutScreen(
    workout: WorkoutUi,
    catalog: List<ExerciseUi>,
    history: List<WorkoutUi>,
    pounds: Boolean,
    loaded: Boolean,
    discarding: Boolean,
    screen: ActiveWorkoutScreenState,
    host: MToastHostState,
    actions: MoosclesActions,
    finishing: Boolean,
    onConfirmFinish: () -> Unit,
    onMinimize: () -> Unit,
    onExerciseHistory: (exerciseId: String) -> Unit,
    imeInsets: WindowInsets,
    nowMillis: () -> Long,
    timer: State<TimerUi>,
    onTimerHeight: (Int) -> Unit = {},
    onExerciseInfo: (exerciseId: String) -> Unit = {},
) {
    val space = MTheme.space
    val haptics = MTheme.haptics
    val busy = finishing || discarding
    val scope = rememberCoroutineScope()
    val needsDone = uiErrorText(UiError.NEEDS_COMPLETED_SET)

    var finishDialog by rememberSaveable(workout.id) { mutableStateOf(false) }
    var discardDialog by rememberSaveable(workout.id) { mutableStateOf(false) }
    var renameSheet by rememberSaveable(workout.id) { mutableStateOf(false) }
    var picker by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var restEntryId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var howToLogId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    // The note of the last refused finish, while it is up. Not read in composition.
    var rejection by remember { mutableStateOf<Job?>(null) }

    // Done as drawn, so the header and the end of the list follow a check at once; read only by them.
    val progress: State<SetProgress> = remember(screen, workout) {
        derivedStateOf {
            SetProgress(workout.exercises.sumOf { e -> e.sets.count { screen.completedOf(it) } }, workout.exercises.sumOf { it.sets.size })
        }
    }
    val requestFinish: () -> Unit = {
        if (WorkoutCounts.of(workout, screen).doneSets > 0) {
            finishDialog = true
        } else {
            haptics.perform(HapticEvent.Rejected)
            // One note at a time: taps while it is up do not queue more.
            if (rejection?.isActive != true) rejection = scope.launch { host.show(needsDone) }
        }
    }
    val exerciseHistory by rememberUpdatedState(onExerciseHistory)
    val exerciseInfo by rememberUpdatedState(onExerciseInfo)
    val callbacks = remember(screen, actions, workout.id) {
        ExerciseCallbacks(
            onRestChip = { restEntryId = it.id },
            onSwap = { picker = it.id },
            onAbout = { exerciseInfo(it) },
            onHistory = { exerciseHistory(it) },
            onHowToLog = { howToLogId = it.id },
            onChangeType = { set, _ -> screen.openTypeSheet(set.id) },
            onRemoveSet = { screen.removeSet(it, actions) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            WorkoutHeader(
                name = workout.name,
                startedAt = workout.startedAt,
                nowMillis = nowMillis,
                progress = progress,
                finishing = finishing,
                finishEnabled = !discarding,
                menuEnabled = !busy,
                onMinimize = onMinimize,
                onFinish = requestFinish,
                onRename = { renameSheet = true },
                onDiscard = { discardDialog = true },
                modifier = Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth(),
            )
        }
        val listState = rememberLazyListState()
        // A traversal group of its own, so a screen reader reads the record toast (first in it,
        // RecordReaction) before the cards, between the header and the rest timer.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { isTraversalGroup = true },
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = MTheme.sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .testTag(ActiveWorkoutTags.LIST),
                state = listState,
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s3, bottom = space.s6),
            ) {
                // The cards from index 0: CardAdvance scrolls by that.
                exerciseCards(workout.exercises, pounds, screen, actions, callbacks)
                item(key = ActiveWorkoutTags.END, contentType = ActiveWorkoutTags.END) {
                    WorkoutListEnd(
                        hasExercises = workout.exercises.isNotEmpty(),
                        progress = progress,
                        finishing = finishing,
                        enabled = !busy,
                        onAddExercise = { picker = PICK_TO_ADD },
                        onFinish = requestFinish,
                    )
                }
            }
            // Over the top of the list, under the header: the record toast (D17).
            RecordReaction(workout, pounds, screen, Modifier.align(Alignment.TopCenter))
        }
        // Under the list: the rest timer and the keyboard's room. After the rest, back to the next set.
        val reduced = MTheme.reducedMotion
        val exercises by rememberUpdatedState(workout.exercises)
        WorkoutTimerLayer(
            timer, workout, pounds, actions, imeInsets,
            onRestOver = { setId -> scope.launch { returnToSet(setId, exercises, screen, listState, reduced, firstIndex = 0) } },
            onHeightChanged = onTimerHeight,
        )
        CardAdvance(workout.exercises, screen, listState, firstIndex = 0)
    }

    if (loaded) {
        SetTypeSheetHost(workout.exercises, pounds, screen, actions)
        RestSheetHost(workout.exercises, restEntryId, actions, onClose = { restEntryId = null })
        howToLogId?.let { id ->
            val entry = workout.exercises.firstOrNull { it.id == id }
            if (entry == null) {
                // Its exercise left the workout (removed here or elsewhere): nothing to explain.
                LaunchedEffect(id) { if (howToLogId == id) howToLogId = null }
            } else {
                HowToLogSheet(entry) { howToLogId = null }
            }
        }
    }
    picker?.let { request ->
        ExercisePickerSheet(
            exercises = catalog,
            history = history,
            onPick = { exercise ->
                if (request == PICK_TO_ADD) {
                    actions.addWorkoutExercise(workout.id, exercise.id)
                } else {
                    // The removed set of this entry cannot come back into the new exercise.
                    screen.closeUndoFor(request)
                    actions.replaceWorkoutExercise(request, exercise.id)
                }
            },
            onDismiss = { picker = null },
        )
    }
    if (renameSheet) {
        RenameSheet(workout.name, onRename = { actions.renameWorkout(workout.id, it) }, onDismiss = { renameSheet = false })
    }
    if (finishDialog) {
        FinishDialog(
            workout = workout,
            counts = WorkoutCounts.of(workout, screen),
            nowMillis = nowMillis,
            onConfirm = {
                finishDialog = false
                screen.closeUndo()
                screen.editing = null
                onConfirmFinish()
            },
            onDismiss = { finishDialog = false },
        )
    }
    if (discardDialog) {
        DiscardDialog(
            counts = WorkoutCounts.of(workout, screen),
            onConfirm = {
                discardDialog = false
                screen.closeUndo()
                screen.editing = null
                actions.discardWorkout()
            },
            onDismiss = { discardDialog = false },
        )
    }
}

/**
 * The end of the list: "Add exercise" (secondary L) and "Finish workout" (primary L, the screen's
 * only `accent.solid`), with "Complete at least one set to finish." under it while no set is done
 * ([progress], read here only). A workout with no exercise shows only "Add exercise" (Review
 * Focus 4); its header's "Finish" still refuses with the note.
 */
@Composable
private fun WorkoutListEnd(
    hasExercises: Boolean,
    progress: State<SetProgress>,
    finishing: Boolean,
    enabled: Boolean,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
) {
    val space = MTheme.space
    val sets by progress
    Column(
        Modifier.fillMaxWidth().padding(top = space.s3).testTag(ActiveWorkoutTags.END),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        MButton(
            stringResource(Res.string.workout_add_exercise),
            onClick = onAddExercise,
            modifier = Modifier.fillMaxWidth().testTag(ActiveWorkoutTags.ADD_EXERCISE),
            variant = MButtonVariant.Secondary,
            size = MButtonSize.L,
            icon = MIcons.Plus,
            enabled = enabled,
        )
        if (hasExercises) {
            MButton(
                stringResource(Res.string.workout_finish_workout),
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().testTag(ActiveWorkoutTags.FINISH_WORKOUT),
                variant = MButtonVariant.Primary,
                size = MButtonSize.L,
                icon = MIcons.Check,
                enabled = enabled || finishing,
                loading = finishing,
            )
            if (sets.done == 0) {
                MText(
                    uiErrorText(UiError.NEEDS_COMPLETED_SET),
                    MTheme.type.bodyMd,
                    modifier = Modifier.fillMaxWidth().testTag(ActiveWorkoutTags.FINISH_NOTE),
                    color = MTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The screen while the workout opens (spec "Abrindo o treino"): the minimize button and [cards]
 * placeholders shaped like exercise cards (a 64 dp thumbnail, two lines and two set rows), read
 * once as "Opening the workout".
 */
@Composable
internal fun WorkoutSkeleton(cards: Int, onMinimize: () -> Unit) {
    val space = MTheme.space
    val opening = stringResource(Res.string.workout_opening)
    Column(Modifier.fillMaxSize().testTag(ActiveWorkoutTags.SKELETON)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MTheme.sizes.barMin).padding(horizontal = space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MIconButton(
                MIcons.ChevronDown,
                stringResource(Res.string.workout_minimize),
                onClick = onMinimize,
                modifier = Modifier.testTag(WorkoutHeaderTags.MINIMIZE),
            )
        }
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = MTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = space.s4, vertical = space.s3)
                .semantics(mergeDescendants = true) { contentDescription = opening },
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            repeat(cards.coerceAtLeast(1)) { SkeletonCard() }
        }
    }
}

/** One placeholder card. The fractions are the lengths of the name and the meta line. */
@Composable
private fun SkeletonCard() {
    val space = MTheme.space
    val colors = MTheme.colors
    val shape = RoundedCornerShape(MTheme.radius.lg)
    val inner = RoundedCornerShape(MTheme.radius.md)
    Column(
        Modifier
            .testTag(ActiveWorkoutTags.SKELETON_CARD)
            .fillMaxWidth()
            .background(colors.bgSurface, shape)
            .border(MTheme.sizes.border, colors.borderSubtle, shape)
            .padding(space.s3),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(MTheme.sizes.media), inner)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                Skeleton(Modifier.fillMaxWidth(NAME_FRACTION).height(space.s4))
                Skeleton(Modifier.fillMaxWidth(META_FRACTION).height(space.s3))
            }
        }
        repeat(SKELETON_ROWS) { Skeleton(Modifier.fillMaxWidth().height(MTheme.targets.workout), inner) }
    }
}

private const val NAME_FRACTION = 0.7f
private const val META_FRACTION = 0.5f
private const val SKELETON_ROWS = 2

/**
 * No workout in progress (spec "Sem treino ativo"), as after a discard or from a restored state:
 * "No workout in progress", "Choose routine" ([onChooseRoutine], to Train) and "Empty workout"
 * ([onEmptyWorkout]).
 */
@Composable
internal fun NoWorkout(onChooseRoutine: () -> Unit, onEmptyWorkout: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag(ActiveWorkoutTags.NO_WORKOUT), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = MIcons.Dumbbell,
            title = stringResource(Res.string.workout_none_title),
            body = stringResource(Res.string.workout_none_body),
            modifier = Modifier.widthIn(max = MTheme.sizes.contentMaxWidth),
            primaryAction = stringResource(Res.string.workout_choose_routine) to onChooseRoutine,
            secondaryAction = stringResource(Res.string.workout_empty_workout) to onEmptyWorkout,
        )
    }
}
