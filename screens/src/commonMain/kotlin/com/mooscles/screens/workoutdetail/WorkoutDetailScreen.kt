package com.mooscles.screens.workoutdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.ExerciseCardHeader
import com.mooscles.designsystem.components.ExerciseCardFooter
import com.mooscles.designsystem.components.ExerciseCardSegment
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.RestDuration
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.exercise.photo.rememberExerciseMedia
import com.mooscles.screens.format.conventionCaption
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.summaryDateRange
import com.mooscles.screens.format.durationText
import com.mooscles.screens.resources.*
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.screens.workout.ExercisePickerSheet
import com.mooscles.screens.workout.MenuItem
import com.mooscles.screens.workout.MAX_SETS
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.SetRowEditor
import com.mooscles.screens.workout.SetTypeSheet
import com.mooscles.screens.workout.WorkoutSetRow
import com.mooscles.screens.workout.bestSetText
import com.mooscles.screens.workout.endsSetEditingOnOutsideTap
import com.mooscles.screens.workout.loadSpoken
import com.mooscles.screens.workout.rememberActiveWorkoutScreenState
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Stable tags retained where the old Android detail's integration tests already used them. */
object WorkoutDetailTags {
    const val LIST = "workout-details"
    const val EDIT = "detail-edit"
    const val DONE = "detail-done"
    const val MENU = "detail-menu"
    const val DELETE = "detail-delete"
    const val RENAME = "detail-rename"
    const val ADD_EXERCISE = "detail-add-exercise"
    const val MISSING = "detail-missing"
    const val SETS = "workout-set-count"
    const val PRS = "workout-pr-exercise-count"
    const val RECORDS = "detail-records"
    const val FIRST = "detail-first-records"
    fun exercise(id: String) = "detail-exercise-$id"
    fun rest(id: String) = "detail-rest-$id"
    fun progress(id: String) = "detail-progress-$id"
    fun readingSet(id: String) = "detail-set-$id"
}

/**
 * Shared saved-workout reading/editing. Every command uses the saved workout/entry/set identity; [UiState.active]
 * is deliberately never read here. Editing reuses the exact-value/focus/draft machinery of the active set rows,
 * with no suggestions, previous result, timer dock, record reaction or undo-removal host.
 *
 * The Android host installs system Back with [WorkoutDetailScreenState.editing]; Back and the top-bar back action
 * leave editing first, preserving immediate writes. [onShare] delegates Android export/permissions unchanged.
 */
@Composable
fun WorkoutDetailRoute(
    workoutId: String,
    ui: UiState,
    actions: MoosclesActions,
    onBack: () -> Unit,
    onExerciseProgress: (String) -> Unit,
    onRoutine: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    screen: WorkoutDetailScreenState = rememberWorkoutDetailScreenState(workoutId),
) {
    val workout = ui.history.firstOrNull { it.id == workoutId }
    val current by rememberUpdatedState(workout)
    val sets = rememberActiveWorkoutScreenState("saved:$workoutId")
    val knownSets = remember(workoutId) { mutableSetOf<String>() }
    LaunchedEffect(workout) {
        if (workout != null) {
            val present = workout.exercises.flatMap { entry -> entry.sets.map { it.id } }.toSet()
            val held = knownSets + sets.drafts.keys.map { it.first } + sets.cleared.map { it.first } + sets.optimistic.keys
            (held - present).forEach(sets::forget)
            knownSets.clear()
            knownSets.addAll(present)
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(screen.editing) {
        if (!screen.editing) {
            // The Android host's system Back changes the public mode directly, so it must clear the private row
            // target too. Otherwise entering edit again can request the old field and keyboard immediately.
            sets.editing = null
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    var confirmEdit by rememberSaveable(workoutId) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(workoutId) { mutableStateOf(false) }
    var renaming by rememberSaveable(workoutId) { mutableStateOf(false) }
    var menu by rememberSaveable(workoutId) { mutableStateOf(false) }
    var exerciseMenu by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var restEntry by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var setType by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var removeSet by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var removeEntry by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var confirmReplace by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var replacing by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }
    var picker by rememberSaveable(workoutId) { mutableStateOf(false) }
    // Waiting is intentionally not saveable: a recreated process must not retain a disabled control.
    var deleting by remember(workoutId) { mutableStateOf<Int?>(null) }
    var deleteRequested by remember(workoutId) { mutableStateOf<Int?>(null) }
    var leftAfterDelete by remember(workoutId) { mutableStateOf(false) }
    val saveWait = MTheme.timing.saveEchoWait
    LaunchedEffect(deleteRequested, workout, ui.loading, ui.subjectFailures) {
        val failureAtSend = deleteRequested ?: return@LaunchedEffect
        if (!ui.loading && workout == null && !leftAfterDelete) {
            leftAfterDelete = true
            deleting = null
            deleteRequested = null
            onBack()
        } else if ((ui.subjectFailures[workoutId] ?: 0) > failureAtSend) {
            deleting = null
            deleteRequested = null
        }
    }
    LaunchedEffect(deleting) {
        val pending = deleting ?: return@LaunchedEffect
        delay(saveWait)
        if (deleting == pending) deleting = null
    }
    // Existing counters predate this screen (or its recreation); only a later failure can reject a pending write.
    val failuresSeen = remember(workoutId) { ui.subjectFailures.toMutableMap() }
    LaunchedEffect(ui.subjectFailures) {
        val ownSetIds = workout?.exercises.orEmpty().flatMap { entry -> entry.sets.map { it.id } }.toSet()
        ui.subjectFailures.forEach { (id, count) ->
            if (id in ownSetIds && count > (failuresSeen[id] ?: 0)) {
                // Saved set writes are subject-keyed. A failure in another workout, row or command must not
                // discard this row's outstanding values or completion. Keep drafts so the person can retry.
                sets.dropOptimistic(id)
                sets.dropPendingWrites(id)
                sets.uncheckedHere(id)
            }
        }
        failuresSeen.clear()
        failuresSeen.putAll(ui.subjectFailures)
    }
    val back: () -> Unit = {
        if (screen.editing) { screen.finishEditing(); sets.editing = null } else onBack()
    }
    val padding = MTheme.space.s4
    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(
                // Keep the editor viewport usable when the localized Done action needs most of a
                // narrow, large-text bar. The design-system pattern puts a long title in the list.
                if (screen.editing) null else stringResource(Res.string.detail_title),
                onBack = back,
            ) {
                if (screen.editing) MButton(
                    stringResource(Res.string.detail_done), { screen.finishEditing(); sets.editing = null },
                    Modifier.testTag(WorkoutDetailTags.DONE), variant = MButtonVariant.Text,
                )
                MIconButton(MIcons.EllipsisVertical, stringResource(Res.string.detail_menu), { menu = true },
                    Modifier.testTag(WorkoutDetailTags.MENU), enabled = workout != null && deleting == null)
            }
            when {
                workout == null && ui.loading -> Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(padding)) {
                    repeat(3) { Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.media)) }
                }
                workout == null -> EmptyState(MIcons.History, stringResource(Res.string.detail_missing),
                    stringResource(Res.string.detail_missing_body), Modifier.testTag(WorkoutDetailTags.MISSING),
                    primaryAction = stringResource(Res.string.detail_back_history) to onBack)
                else -> {
                    val listState = rememberLazyListState()
                    val stats = remember(workout) { DetailStats.from(workout) }
                    val records = remember(workout.records) { DetailRecordSections.from(workout) }
                    LazyColumn(
                        Modifier.fillMaxSize().testTag(WorkoutDetailTags.LIST).imePadding().endsSetEditingOnOutsideTap(sets),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = padding, vertical = MTheme.space.s3),
                    ) {
                        item("heading", "detail-heading") {
                            Column(Modifier.detailWidth().padding(bottom = padding), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                                if (screen.editing) MText(stringResource(Res.string.detail_editing), MTheme.type.titleLg,
                                    Modifier.semantics { heading() })
                                MText(workout.name, MTheme.type.titleMd, Modifier.semantics { heading() })
                                MText(summaryDateRange(workout.startedAt, workout.endedAt ?: workout.startedAt), MTheme.type.bodyMd,
                                    color = MTheme.colors.textSecondary)
                                ui.routines.firstOrNull { it.id == workout.sourceRoutineId }?.let { routine ->
                                    MChip(stringResource(Res.string.detail_routine, routine.name), false, { onRoutine(routine.id) }, kind = MChipKind.Menu)
                                }
                                DetailStatistics(workout, stats, ui.preferences.pounds)
                            }
                        }
                        if (records.beaten.isNotEmpty()) item("records", "detail-records") {
                            DetailRecords(stringResource(Res.string.summary_records), records.beaten, false, ui.preferences.pounds, WorkoutDetailTags.RECORDS)
                        }
                        if (records.first.isNotEmpty()) item("first", "detail-records") {
                            DetailRecords(stringResource(Res.string.summary_first_records), records.first, true, ui.preferences.pounds, WorkoutDetailTags.FIRST)
                        }
                        workout.exercises.forEach { entry ->
                            item("entry:${entry.id}", "detail-exercise-header") {
                                ExerciseCardHeader(
                                    name = exerciseName(entry.exercise),
                                    meta = "${muscleLabel(entry.exercise.muscle)} · ${equipmentLabel(entry.exercise.equipment)}",
                                    media = rememberExerciseMedia(entry.exercise, thumbnail = true),
                                    equipmentIcon = EquipmentIcons.forEquipment(entry.exercise.equipment),
                                    onMenu = { exerciseMenu = entry.id }, collapsed = false,
                                    modifier = Modifier.detailWidth().testTag(WorkoutDetailTags.exercise(entry.id)),
                                    caption = conventionCaption(entry.exercise.convention),
                                    support = if (screen.editing) ({
                                        MChip(stringResource(Res.string.detail_rest, RestDuration.format(entry.restSeconds)), false,
                                            { restEntry = entry.id }, Modifier.testTag(WorkoutDetailTags.rest(entry.id)), MChipKind.Menu)
                                    }) else null,
                                    menuTag = "detail-exercise-menu-${entry.id}",
                                    menuLabel = stringResource(Res.string.detail_exercise_menu, exerciseName(entry.exercise)),
                                )
                            }
                            if (entry.sets.isEmpty()) item("empty:${entry.id}", "detail-set") {
                                ExerciseCardSegment(Modifier.detailWidth()) { MText(stringResource(Res.string.detail_no_sets), MTheme.type.bodyMd) }
                            }
                            itemsIndexed(entry.sets, key = { _, set -> "set:${set.id}" }, contentType = { _, _ -> "detail-set" }) { index, set ->
                                ExerciseCardSegment(Modifier.detailWidth()) {
                                    if (screen.editing) WorkoutSetRow(
                                        set, index + 1, entry.sets.size, entry, SetSuggestion(null, null), null,
                                        ui.preferences.pounds, sets, actions,
                                        onChangeType = { setType = it.id }, onRemove = { removed, _ -> removeSet = removed.id },
                                    ) else DetailSetReading(set, index + 1, ui.preferences.pounds)
                                }
                            }
                            item("footer:${entry.id}", "detail-exercise-footer") {
                                Column(Modifier.detailWidth()) {
                                    if (screen.editing) ExerciseCardFooter(
                                        onAddSet = { actions.addSet(entry.id) }, addEnabled = entry.sets.size < MAX_SETS,
                                        addTag = "detail-add-set-${entry.id}",
                                    )
                                    else Column(
                                        Modifier.fillMaxWidth().background(MTheme.colors.bgSurface,
                                            RoundedCornerShape(bottomStart = MTheme.radius.lg, bottomEnd = MTheme.radius.lg)).padding(padding),
                                    ) {
                                        MText(detailWeight(detailExerciseVolume(entry), ui.preferences.pounds), MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
                                        MButton(stringResource(Res.string.detail_progress), { onExerciseProgress(entry.exercise.id) },
                                            Modifier.testTag(WorkoutDetailTags.progress(entry.id)), MButtonVariant.Text, icon = MIcons.ChartLine)
                                    }
                                    Spacer(Modifier.height(padding))
                                }
                            }
                        }
                        if (!screen.editing) item("actions", "detail-actions") {
                            Column(Modifier.detailWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                                MButton(stringResource(Res.string.detail_edit), { confirmEdit = true },
                                    Modifier.fillMaxWidth().testTag(WorkoutDetailTags.EDIT), MButtonVariant.Secondary, MButtonSize.L, MIcons.Pencil)
                                MButton(stringResource(Res.string.detail_share), { onShare(workout.id) },
                                    Modifier.fillMaxWidth().testTag("share-workout"), MButtonVariant.Secondary, MButtonSize.L, MIcons.Share2)
                                MButton(stringResource(Res.string.detail_delete), { confirmDelete = true },
                                    Modifier.testTag(WorkoutDetailTags.DELETE), MButtonVariant.Destructive, icon = MIcons.Trash2,
                                    enabled = deleting == null, loading = deleting != null)
                            }
                        }
                        if (screen.editing) item("add", "detail-add") {
                            MButton(stringResource(Res.string.detail_add_exercise), { replacing = null; picker = true },
                                Modifier.detailWidth().testTag(WorkoutDetailTags.ADD_EXERCISE), MButtonVariant.Secondary, MButtonSize.L, MIcons.Plus)
                        }
                    }
                }
            }
        }
        ScreenErrorToast(ui.errorCode, actions, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmEdit && workout != null) MDialog(
        stringResource(Res.string.detail_confirm_edit), stringResource(Res.string.detail_confirm_edit_body),
        stringResource(Res.string.detail_edit), { confirmEdit = false; screen.beginEditing() }, { confirmEdit = false },
        dismissLabel = stringResource(Res.string.detail_cancel),
    )
    if (confirmDelete && workout != null) MDialog(
        stringResource(Res.string.detail_confirm_delete), stringResource(Res.string.detail_confirm_delete_body),
        stringResource(Res.string.detail_delete), {
            // The bounded wait protects duplicate taps. After it expires, allow an explicit retry while
            // retaining deleteRequested so a late successful removal still navigates back.
            if (deleting == null) {
                confirmDelete = false
                deleting = ui.subjectFailures[workoutId] ?: 0
                deleteRequested = deleting
                actions.deleteWorkout(workoutId)
            }
        }, { confirmDelete = false }, stringResource(Res.string.detail_cancel), destructive = true,
    )
    if (renaming && workout != null) DetailRenameSheet(workout.name, { renaming = false }) {
        actions.renameWorkout(workoutId, it); renaming = false
    }
    if (menu && workout != null) MBottomSheet(onDismiss = { menu = false }) {
        MenuItem(MIcons.Pencil, stringResource(Res.string.detail_rename), WorkoutDetailTags.RENAME) { menu = false; renaming = true }
        MenuItem(MIcons.Share2, stringResource(Res.string.detail_share), "detail-menu-share") { menu = false; onShare(workoutId) }
        MenuItem(MIcons.Trash2, stringResource(Res.string.detail_delete), "detail-menu-delete", destructive = true) { menu = false; confirmDelete = true }
    }
    val selectedEntry = workout?.exercises?.firstOrNull { it.id == exerciseMenu }
    if (selectedEntry != null) MBottomSheet(onDismiss = { exerciseMenu = null }) {
        MenuItem(MIcons.ChartLine, stringResource(Res.string.detail_progress), "detail-menu-progress") {
            exerciseMenu = null; onExerciseProgress(selectedEntry.exercise.id)
        }
        if (screen.editing) {
            val entries = workout?.exercises.orEmpty()
            val position = entries.indexOf(selectedEntry)
            MenuItem(MIcons.ArrowLeftRight, stringResource(Res.string.detail_replace), "detail-exercise-replace") {
                exerciseMenu = null; confirmReplace = selectedEntry.id
            }
            MenuItem(MIcons.ArrowUp, stringResource(Res.string.detail_move_up), "detail-exercise-up", enabled = position > 0) {
                exerciseMenu = null; actions.moveWorkoutExercise(selectedEntry.id, -1)
            }
            MenuItem(MIcons.ArrowDown, stringResource(Res.string.detail_move_down), "detail-exercise-down", enabled = position < entries.lastIndex) {
                exerciseMenu = null; actions.moveWorkoutExercise(selectedEntry.id, 1)
            }
            MenuItem(MIcons.Trash2, stringResource(Res.string.detail_remove), "detail-exercise-remove", destructive = true) {
                exerciseMenu = null; removeEntry = selectedEntry.id
            }
        }
    }
    val resting = workout?.exercises?.firstOrNull { it.id == restEntry }
    if (screen.editing && resting != null) DetailRestSheet(resting, actions, { restEntry = null })
    val typeEntry = workout?.exercises?.firstOrNull { entry -> entry.sets.any { it.id == setType } }
    val typedSet = typeEntry?.sets?.firstOrNull { it.id == setType }
    val locale = currentNumberLocale()
    if (screen.editing && typeEntry != null && typedSet != null) SetTypeSheet(
        typedSet, typeEntry.exercise.convention,
        onChoose = { warmup, assistance ->
            val latest = current?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == typedSet.id }
            if (latest != null && (latest.warmup != warmup || latest.assistance != assistance)) {
                SetRowEditor(latest, ui.preferences.pounds, SetSuggestion(null, null), sets, actions, locale).changeType(warmup, assistance)
            }
            setType = null
        },
        onRemove = { setType = null; removeSet = typedSet.id }, onDismiss = { setType = null },
    )
    val removingEntry = workout?.exercises?.firstOrNull { entry -> entry.sets.any { it.id == removeSet } }
    val removingNumber = removingEntry?.sets?.indexOfFirst { it.id == removeSet }?.plus(1)
    if (screen.editing && removingNumber != null) MDialog(
        stringResource(Res.string.detail_remove_set, removingNumber), stringResource(Res.string.detail_remove_set_body),
        stringResource(Res.string.detail_remove), {
            // Keep drafts until the saved session actually loses the set; a failed command must leave them intact.
            removeSet?.let(actions::removeSet); removeSet = null
        }, { removeSet = null }, stringResource(Res.string.detail_cancel), destructive = true,
    )
    val removing = workout?.exercises?.firstOrNull { it.id == removeEntry }
    if (screen.editing && removing != null) MDialog(
        stringResource(Res.string.detail_remove_exercise, exerciseName(removing.exercise)), stringResource(Res.string.detail_remove_exercise_body),
        stringResource(Res.string.detail_remove), {
            if (removeEntry == removing.id) { removeEntry = null; actions.removeWorkoutExercise(removing.id) }
        },
        { removeEntry = null }, stringResource(Res.string.detail_cancel), destructive = true,
    )
    val swapping = workout?.exercises?.firstOrNull { it.id == confirmReplace }
    if (screen.editing && swapping != null) MDialog(
        stringResource(Res.string.detail_replace_exercise, exerciseName(swapping.exercise)), stringResource(Res.string.detail_replace_exercise_body),
        stringResource(Res.string.detail_replace), { replacing = swapping.id; confirmReplace = null; picker = true },
        { confirmReplace = null }, stringResource(Res.string.detail_cancel),
    )
    if (screen.editing && picker && workout != null) ExercisePickerSheet(
        ui.exercises, ui.history,
        onPick = { exercise ->
            val entryId = replacing
            if (entryId == null) actions.addWorkoutExercise(workoutId, exercise.id)
            else if (current?.exercises?.any { it.id == entryId } == true) actions.replaceWorkoutExercise(entryId, exercise.id)
        },
        onDismiss = { picker = false; replacing = null },
    )
}

@Composable
private fun Modifier.detailWidth(): Modifier = widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth()

private fun detailExerciseVolume(entry: WorkoutExerciseUi): Double =
    if (entry.exercise.convention == "Assistance") 0.0
    else entry.sets.filter { it.completed && !it.assistance }.sumOf { it.weightKg * it.reps }

@Composable
private fun detailWeight(kg: Double, pounds: Boolean): String = MoosclesNumbers.weight(
    MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), if (pounds) "lb" else "kg", currentNumberLocale(),
)

@Composable
private fun DetailStatistics(workout: WorkoutUi, stats: DetailStats, pounds: Boolean) {
    val oneColumn = LocalDensity.current.fontScale >= 1.5f
    val gap = MTheme.space.s4
    val prDescription = pluralCount(Res.plurals.summary_pr_count, stats.prs)
    Column(Modifier.fillMaxWidth().background(MTheme.colors.bgSurface, RoundedCornerShape(MTheme.radius.lg)).padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap)) {
        DetailMetric(stringResource(Res.string.summary_duration), durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1000L))
        if (oneColumn) {
            DetailMetric(stringResource(Res.string.summary_sets), stats.sets.toString(), Modifier.testTag(WorkoutDetailTags.SETS))
            DetailMetric(stringResource(Res.string.summary_prs), stats.prs.toString(), Modifier.testTag(WorkoutDetailTags.PRS), prDescription)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            DetailMetric(stringResource(Res.string.summary_sets), stats.sets.toString(), Modifier.weight(1f).testTag(WorkoutDetailTags.SETS))
            DetailMetric(stringResource(Res.string.summary_prs), stats.prs.toString(), Modifier.weight(1f).testTag(WorkoutDetailTags.PRS), prDescription)
        }
        DetailMetric(stringResource(Res.string.summary_volume), detailWeight(stats.volumeKg, pounds))
        DetailMetric(stringResource(Res.string.summary_exercises), stats.exercises.toString())
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier, spoken: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) { if (spoken != null) contentDescription = spoken }) {
        MText(label, MTheme.type.labelCaps, color = MTheme.colors.textSecondary)
        MText(value, MTheme.type.numMd)
    }
}

@Composable
private fun DetailSetReading(set: SetUi, number: Int, pounds: Boolean) {
    val type = stringResource(when {
        set.warmup -> Res.string.workout_type_warmup
        set.assistance -> Res.string.workout_type_assisted
        else -> Res.string.workout_type_normal
    })
    val complete = stringResource(if (set.completed) Res.string.detail_set_completed else Res.string.detail_set_not_completed)
    val description = stringResource(Res.string.detail_set_reading, number,
        spokenLoadByReps(loadSpoken(set.weightKg, pounds), set.reps), "$type · $complete")
    Row(Modifier.fillMaxWidth().testTag(WorkoutDetailTags.readingSet(set.id)).semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3), verticalAlignment = Alignment.CenterVertically) {
        MText(number.toString(), MTheme.type.numSm, color = MTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            MText(bestSetText(set.weightKg, set.reps, pounds), MTheme.type.bodyLg,
                color = if (set.completed) MTheme.colors.textPrimary else MTheme.colors.textDisabled)
            if (set.warmup || set.assistance || !set.completed) MText("$type · $complete", MTheme.type.caption, color = MTheme.colors.textSecondary)
        }
        if (set.completed && set.record != null) PrBadge()
    }
}

@Composable
private fun DetailRecords(title: String, records: List<RecordUi>, first: Boolean, pounds: Boolean, tag: String) {
    Column(Modifier.detailWidth().testTag(tag).padding(bottom = MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        MText(title, MTheme.type.titleSm, Modifier.semantics { heading() })
        records.forEach { record ->
            val load = when {
                record.assistance -> stringResource(Res.string.summary_assistance_load, detailWeight(record.weightKg, pounds))
                record.weightKg == 0.0 -> stringResource(Res.string.summary_bodyweight)
                else -> detailWeight(record.weightKg, pounds)
            }
            val previousKg = record.previousKg
            val previousReps = record.previousReps
            val detail = when {
                record.kind == RecordKind.WEIGHT && previousKg != null -> stringResource(Res.string.summary_weight_record,
                    load, record.reps, detailWeight(previousKg, pounds))
                record.kind == RecordKind.REPS && previousReps != null -> stringResource(Res.string.summary_reps_record,
                    load, record.reps, previousReps)
                else -> stringResource(Res.string.summary_set, load, record.reps)
            }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalAlignment = Alignment.CenterVertically) {
                PrBadge(soft = first)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
                    MText(stringResource(if (record.kind == RecordKind.WEIGHT) Res.string.detail_weight_pr else Res.string.detail_reps_pr),
                        MTheme.type.labelMd, color = MTheme.colors.textSecondary)
                    MText(stringResource(Res.string.summary_record_line, exerciseName(record.exercise), detail), MTheme.type.bodyMd)
                }
            }
        }
    }
}

@Composable
private fun DetailRenameSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
    MBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
            MText(stringResource(Res.string.detail_rename), MTheme.type.titleMd, Modifier.semantics { heading() })
            MTextField(name, { name = it }, stringResource(Res.string.detail_name), Modifier.fillMaxWidth().testTag("detail-name"), singleLine = true)
            MButton(stringResource(Res.string.detail_save), { onSave(name.text.trim()) }, Modifier.fillMaxWidth(), enabled = name.text.isNotBlank())
            MButton(stringResource(Res.string.detail_cancel), onDismiss, Modifier.fillMaxWidth(), MButtonVariant.Text)
        }
    }
}
