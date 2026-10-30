package com.mooscles.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.mooscles.domain.RestDuration

private data class WorkoutRestOverride(val exerciseId: String, val seconds: Int)

private data class WorkoutSecondaryGeometry(val pounds: Boolean,
    val entries: List<Triple<String, String?, List<RecordUi>>>)

/** Focus bookkeeping is not observable state and never causes list recomposition. */
private class WorkoutInputAnchor {
    private val focusedFields = mutableMapOf<String, String>()
    var secondary: WorkoutSecondaryGeometry? = null
    val focusedSetKey: String? get() = focusedFields.values.lastOrNull()
    fun focus(setKey: String, field: String, focused: Boolean) {
        val fieldKey = "$setKey:$field"
        if (focused) focusedFields[fieldKey] = setKey else focusedFields.remove(fieldKey)
    }
}

private fun workoutSetListIndex(workout: WorkoutUi, key: String): Int? {
    var index = 3 // Heading, totals and movements.
    workout.exercises.forEach { entry ->
        index++ // Exercise heading.
        entry.sets.forEach { set ->
            if ("set:${entry.id}:${set.id}" == key) return index
            index++
        }
        index += 2 // Footer and secondary information.
    }
    return null
}

@Composable
fun ActiveWorkoutScreen(state: UiState, actions: MoosclesActions, onRoutines: () -> Unit, onHistory: (String) -> Unit, onExerciseHistory: (String) -> Unit) {
    ActiveWorkoutScreen(state.active, state.exercises, state.preferences, state.history, state.error,
        actions, onRoutines, onHistory, onExerciseHistory, state.startingWorkout, state.discardingWorkout)
}

/** The scrolling route deliberately receives no ticking rest-timer state. */
@Composable
fun ActiveWorkoutScreen(
    workout: WorkoutUi?, exercises: List<ExerciseUi>, preferences: PreferencesUi,
    history: List<WorkoutUi>, error: String?, actions: MoosclesActions,
    onRoutines: () -> Unit, onHistory: (String) -> Unit, onExerciseHistory: (String) -> Unit,
    startingWorkout: Boolean = false,
    discardingWorkout: Boolean = false,
    showStartingState: Boolean = true,
) {
    var picker by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var finishingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(history, workout, finishingId) {
        finishingId?.let { id -> if (history.any { it.id == id }) { finishingId = null; onHistory(id) } }
    }
    LaunchedEffect(error) { if (error != null) finishingId = null }
    if (workout == null) {
        if (startingWorkout) {
            if (showStartingState) StartingWorkoutState() else Spacer(Modifier.fillMaxSize())
            return
        }
        Column(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { PageHeading("Your training space", "Ready to move?", "Every rep is a step forward.") }
                item { EmptyState(Icons.Outlined.FitnessCenter, "A fresh start", "Start with a routine or build today’s session as you go.", "Choose a routine", onRoutines) }
                item { Button(onClick = { actions.startWorkout(null) }, enabled = !startingWorkout, modifier = Modifier.fillMaxWidth()) {
                    if (startingWorkout) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp)); Text(if (startingWorkout) "Starting workout…" else "Start empty workout")
                } }
            }
        }
        return
    }
    var restEditorId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var restEditorExerciseId by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    val pendingRests = remember(workout.id) { mutableStateMapOf<String, WorkoutRestOverride>() }
    LaunchedEffect(workout.exercises) {
        pendingRests.keys.toList().forEach { id ->
            val entry = workout.exercises.find { it.id == id }
            val pending = pendingRests[id]
            if (entry == null || pending == null || pending.exerciseId != entry.exercise.id || pending.seconds == entry.restSeconds)
                pendingRests.remove(id)
        }
        if (restEditorId != null && workout.exercises.none { it.id == restEditorId && it.exercise.id == restEditorExerciseId }) {
            restEditorId = null
            restEditorExerciseId = null
        }
    }
    fun withRestOverride(entry: WorkoutExerciseUi): WorkoutExerciseUi {
        val pending = pendingRests[entry.id]?.takeIf { it.exerciseId == entry.exercise.id }
        return if (pending == null) entry else entry.copy(restSeconds = pending.seconds)
    }
    val totals = remember(workout) { workoutTotals(workout) }
    val listState = rememberSaveable(workout.id, saver = LazyListState.Saver) { LazyListState() }
    val inputAnchor = remember(workout.id) { WorkoutInputAnchor() }
    val secondary = remember(workout.exercises, preferences.pounds) {
        WorkoutSecondaryGeometry(preferences.pounds, workout.exercises.map { Triple(it.id, it.previous, it.records) })
    }
    // Stable lazy keys retain the first visible item. If that item is an earlier
    // exercise's changing metadata, retain the focused set's offset instead.
    // Read layout only on enrichment, without subscribing the screen to scrolls.
    val pendingAnchor = if (inputAnchor.secondary != null && inputAnchor.secondary != secondary) {
        inputAnchor.focusedSetKey?.let { key -> Snapshot.withoutReadObservation {
            if (listState.isScrollInProgress) null else {
                val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                val index = workoutSetListIndex(workout, key)
                if (visible == null || index == null) null else index to -visible.offset
            }
        } }
    } else null
    SideEffect {
        inputAnchor.secondary = secondary
        pendingAnchor?.let { (index, offset) -> listState.requestScrollToItem(index, offset) }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
    LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("active-workout-list"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp)) {
        item(key = "workout-heading", contentType = "heading") { SpacedWorkoutItem { PageHeading("In your element", workout.name, trailing = { IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename workout") } }) } }
        item(key = "workout-totals", contentType = "totals") {
            SpacedWorkoutItem {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WorkoutElapsedMetric(workout.startedAt)
                Metric(totals.completedSets.toString(), "Sets done")
                Metric(weight(totals.volumeKg, preferences.pounds), "Weight moved")
            }
            }
        }
        item(key = "workout-movements", contentType = "section") { SpacedWorkoutItem { SectionTitle("Today’s movements", "Add", { picker = true }) } }
        if (workout.exercises.isEmpty()) item(key = "workout-empty", contentType = "empty") { SpacedWorkoutItem { EmptyState(Icons.Outlined.AddCircleOutline, "Make the first move", "Add an exercise to begin logging sets.", "Add exercise", { picker = true }) } }
        // An exercise is a group of independently lazy rows. Making the whole card
        // one item would still compose every TextField in a large single exercise.
        workout.exercises.forEachIndexed { index, entry ->
            item(key = "exercise-heading:${entry.id}", contentType = "exercise-heading") {
                WorkoutCardSegment(first = true) {
                    WorkoutExerciseHeading(entry.exercise, entry.id, index, workout.exercises.size,
                        null, emptyList(), preferences.pounds, actions, editable = true,
                        onReplace = { replace = entry.id; picker = true },
                        onHistory = { onExerciseHistory(entry.exercise.id) })
                }
            }
            itemsIndexed(entry.sets, key = { _, set -> "set:${entry.id}:${set.id}" }, contentType = { _, _ -> "working-set" }) { setIndex, set ->
                WorkoutCardSegment {
                    SetEditor(set, setIndex, preferences.pounds, entry.exercise.convention == "Assistance", actions,
                        onInputFocusChanged = { field, focused ->
                            inputAnchor.focus("set:${entry.id}:${set.id}", field, focused)
                        })
                }
            }
            item(key = "exercise-footer:${entry.id}", contentType = "exercise-footer") {
                WorkoutCardSegment {
                    WorkoutExerciseFooter(entry.id, entry.exercise.name, withRestOverride(entry).restSeconds,
                        actions, onEditRest = { restEditorId = entry.id; restEditorExerciseId = entry.exercise.id })
                }
            }
            // History and badges can arrive after typing has started. Keeping
            // their expanding content after the editable rows prevents it from
            // pushing this exercise's focused input beneath the timer/keyboard.
            item(key = "exercise-metadata:${entry.id}", contentType = "exercise-metadata") {
                WorkoutCardSegment(last = true) {
                    WorkoutExerciseSecondaryInfo(entry.previous, entry.records, preferences.pounds,
                        editable = true, modifier = Modifier.testTag("exercise-metadata-${entry.id}"))
                }
            }
        }
        item(key = "workout-add", contentType = "button") { SpacedWorkoutItem { OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add exercise") } } }
        item(key = "workout-finish", contentType = "finish") {
            SpacedWorkoutItem {
            Column {
            Button(onClick = { finish = true }, enabled = totals.completedSets > 0 && finishingId == null && !discardingWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("finish-workout")) {
                if (finishingId != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.CheckCircleOutline, null)
                Spacer(Modifier.width(8.dp)); Text(if (finishingId == null) "Finish workout" else "Saving workout…")
            }
            if (totals.completedSets == 0) Text("Complete at least one set to finish your workout.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
        item(key = "workout-discard", contentType = "button") {
            TextButton(onClick = { discard = true }, enabled = !discardingWorkout && finishingId == null,
                modifier = Modifier.fillMaxWidth().testTag("discard-workout")) {
                if (discardingWorkout) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (discardingWorkout) "Discarding workout…" else "Discard workout", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    }
    // Keep the modal outside lazy items: its originating card may leave the
    // viewport when the keyboard opens or while the workout list scrolls.
    val editingRest = restEditorId?.let { id ->
        workout.exercises.find { it.id == id && it.exercise.id == restEditorExerciseId }?.let(::withRestOverride)
    }
    if (editingRest != null) WorkoutRestDialog(editingRest, editingRest.restSeconds,
        onDismiss = { restEditorId = null; restEditorExerciseId = null }, onSave = { seconds ->
            if (seconds != editingRest.restSeconds) {
                pendingRests[editingRest.id] = WorkoutRestOverride(editingRest.exercise.id, seconds)
                actions.updateWorkoutExerciseRest(editingRest.id, seconds)
            }
            restEditorId = null
            restEditorExerciseId = null
        })
    if (picker) ExercisePicker(exercises, onDismiss = { picker = false; replace = null }, onSelect = { id -> replace?.let { actions.replaceWorkoutExercise(it, id) } ?: actions.addWorkoutExercise(workout.id, id) })
    if (rename) NameDialog("Name this workout", workout.name, onDismiss = { rename = false }, onSave = { actions.renameWorkout(workout.id, it) })
    if (discard) ConfirmDialog("Discard this workout?", "All sets from this active session will be removed. This cannot be undone.", "Discard", onDismiss = { discard = false }, onConfirm = {
        discard = false
        actions.discardWorkout()
    })
    if (finish) AlertDialog(onDismissRequest = { finish = false }, icon = { Icon(Icons.Outlined.CheckCircleOutline, null) }, title = { Text("Ready to call it?") },
        text = { Text("Save ${totals.completedSets} completed sets across ${totals.completedExercises} exercises. Your records and session breakdown will be ready next.") },
        confirmButton = { TextButton(onClick = { finishingId = workout.id; finish = false; actions.finishWorkout() }) { Text("Finish & save") } }, dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep training") } })
}

/** The only animated scope while opening; it never observes or changes workout data. */
@Composable
internal fun StartingWorkoutState(animationsEnabled: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .testTag("starting-workout").pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }.safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            paneTitle = "Starting workout"
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            StartingWorkoutProgress(animationsEnabled)
            Spacer(Modifier.height(16.dp))
            Text("Starting workout…", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("Loading your routine in the background.", style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartingWorkoutProgress(animationsEnabled: Boolean) {
    if (!animationsEnabled) {
        Icon(Icons.Outlined.FitnessCenter, null,
            Modifier.size(32.dp).testTag("starting-workout-static-progress").clearAndSetSemantics {},
            tint = MaterialTheme.colorScheme.primary)
        return
    }
    CircularProgressIndicator(Modifier.size(32.dp)
        .testTag("starting-workout-progress").clearAndSetSemantics {}, strokeWidth = 3.dp)
}

private data class WorkoutTotals(val completedSets: Int, val completedExercises: Int, val volumeKg: Double)

private fun workoutTotals(workout: WorkoutUi): WorkoutTotals {
    var completedSets = 0
    var completedExercises = 0
    var volumeKg = 0.0
    workout.exercises.forEach { entry ->
        var hasCompletedSet = false
        var exerciseVolumeKg = 0.0
        entry.sets.forEach { set ->
            if (set.completed) {
                completedSets++
                hasCompletedSet = true
                if (entry.exercise.convention != "Assistance" && !set.assistance) exerciseVolumeKg += set.weightKg * set.reps
            }
        }
        volumeKg += exerciseVolumeKg
        if (hasCompletedSet) completedExercises++
    }
    return WorkoutTotals(completedSets, completedExercises, volumeKg)
}

@Composable
private fun WorkoutElapsedMetric(startedAt: Long) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Metric(durationText((now - startedAt) / 1000), "Elapsed")
}

@Composable
private fun SpacedWorkoutItem(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { content() }
}

/** Adjacent surfaces retain the card appearance without an unbounded Column. */
@Composable
private fun WorkoutCardSegment(first: Boolean = false, last: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = if (last) 16.dp else 0.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerLow,
            RoundedCornerShape(topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp,
                bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp))) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = if (first) 16.dp else 0.dp,
            bottom = if (last) 16.dp else 12.dp)) { content() }
    }
}

/** A single measured dock above the app footer, independent of the scrolling route. */
@Composable
internal fun WorkoutTimerDock(state: UiState, actions: MoosclesActions) {
    if (state.active == null) return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("active-rest-timer")) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TimerPanel(state.timer, actions, state.preferences.defaultRestSeconds, compact = true)
        }
    }
}

@Composable
fun WorkoutExerciseCard(entry: WorkoutExerciseUi, index: Int, count: Int, pounds: Boolean, actions: MoosclesActions, editable: Boolean, onReplace: () -> Unit, onHistory: () -> Unit, onEditRest: (() -> Unit)? = null) {
    var restEditor by rememberSaveable(entry.id, entry.exercise.id) { mutableStateOf(false) }
    var displayedRest by remember(entry.id, entry.exercise.id) { mutableIntStateOf(entry.restSeconds.coerceAtLeast(0)) }
    val pendingRests = remember(entry.id, entry.exercise.id) { mutableListOf<Int>() }
    val rest = if (onEditRest == null) displayedRest else entry.restSeconds.coerceAtLeast(0)
    LaunchedEffect(entry.restSeconds) {
        val incoming = entry.restSeconds.coerceAtLeast(0)
        val acknowledged = pendingRests.indexOf(incoming)
        if (acknowledged >= 0) pendingRests.subList(0, acknowledged + 1).clear()
        if (pendingRests.isEmpty()) displayedRest = incoming
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutExerciseHeading(entry.exercise, entry.id, index, count, entry.previous, entry.records,
                pounds, actions, editable, onReplace, onHistory)
            val visibleSets = if (editable) entry.sets else entry.sets.filter { it.completed }
            if (visibleSets.isEmpty()) Text("No completed sets", style = MaterialTheme.typography.bodySmall)
            visibleSets.forEachIndexed { setIndex, set ->
                if (editable) SetEditor(set, setIndex, pounds, entry.exercise.convention == "Assistance", actions)
                else CompletedSetRow(set, setIndex, pounds, entry.exercise.convention == "Assistance")
            }
            if (editable) WorkoutExerciseFooter(entry.id, entry.exercise.name, rest, actions,
                onEditRest = { onEditRest?.invoke() ?: run { restEditor = true } })
        }
    }
    if (restEditor && editable && onEditRest == null) WorkoutRestDialog(entry, displayedRest,
        onDismiss = { restEditor = false }, onSave = { seconds ->
            if (seconds != displayedRest) {
                displayedRest = seconds
                pendingRests += seconds
                actions.updateWorkoutExerciseRest(entry.id, seconds)
            }
            restEditor = false
        })
}

@Composable
private fun WorkoutExerciseHeading(
    exercise: ExerciseUi, entryId: String, index: Int, count: Int, previous: String?, records: List<RecordUi>,
    pounds: Boolean, actions: MoosclesActions, editable: Boolean, onReplace: () -> Unit, onHistory: () -> Unit,
) {
    var menu by remember(entryId) { mutableStateOf(false) }
    var remove by rememberSaveable(entryId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium)
                Text("${human(exercise.equipment)} · ${exercise.convention}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Exercise options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Performance history") }, onClick = { menu = false; onHistory() })
                    if (editable) {
                        DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { menu = false; onReplace() })
                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; actions.moveWorkoutExercise(entryId, -1) })
                        DropdownMenuItem(text = { Text("Move down") }, enabled = index < count - 1, onClick = { menu = false; actions.moveWorkoutExercise(entryId, 1) })
                        DropdownMenuItem(text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; remove = true })
                    }
                }
            }
        }
        Text(conventionExplanation(exercise.convention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkoutExerciseSecondaryInfo(previous, records, pounds, editable)
    }
    if (remove) ConfirmDialog("Remove ${exercise.name}?", "This removes this exercise and all of its sets from the workout.", "Remove",
        onDismiss = { remove = false }, onConfirm = { actions.removeWorkoutExercise(entryId) })
}

/** Full secondary information is retained; no fixed-height clipping or shortened history. */
@Composable
private fun WorkoutExerciseSecondaryInfo(previous: String?, records: List<RecordUi>, pounds: Boolean,
    editable: Boolean, modifier: Modifier = Modifier) {
    if (previous == null && records.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        previous?.let {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.History, null, Modifier.size(17.dp)); Text("Previous: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        records.forEach { record ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(18.dp))
                    Text("${record.label}${if (record.firstRecord) " · first record" else ""} · ${recordAchievementText(record, pounds)}${if (editable) " · provisional" else ""}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseFooter(entryId: String, exerciseName: String, rest: Int, actions: MoosclesActions, onEditRest: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { actions.addSet(entryId) }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("Add set") }
        TextButton(onClick = onEditRest, modifier = Modifier.testTag("workout-rest-edit-$entryId")
            .semantics { contentDescription = "Edit rest duration for $exerciseName" }) {
            Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp)); Text("Rest ${RestDuration.format(rest)} min")
        }
    }
}

/** A draft edit changes only this exercise's future intervals, never the live countdown. */
@Composable
private fun WorkoutRestDialog(entry: WorkoutExerciseUi, seconds: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var draft by rememberSaveable(entry.id, entry.exercise.id) { mutableIntStateOf(seconds.coerceIn(0, RestDuration.MAX_SECONDS)) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Timer, null) },
        title = { Text("Edit rest timer") }, text = {
            // The dialog has its own composition and keyboard controller. Request
            // focus here, after that composition attaches its text field.
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleSmall)
                RestDurationField(draft, { draft = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    testTag = "workout-rest-${entry.id}")
                Text("For this workout's next rests. The current countdown keeps its own duration.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        }, confirmButton = {
            TextButton(onClick = { onSave(draft) },
                modifier = Modifier.testTag("workout-rest-save-${entry.id}")) { Text("Save") }
        }, dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("workout-rest-cancel-${entry.id}")) { Text("Cancel") }
        })
}

fun conventionExplanation(convention: String): String = when (convention) {
    "Per hand" -> "Log one dumbbell’s load and reps. Volume is per hand; never doubled."
    "One side" -> "Log the total load used on one side and reps on that side. Volume is never doubled."
    "Added weight" -> "Log added external weight only. Leave weight blank for bodyweight; body mass is excluded."
    "Assistance" -> "Log assistance supplied by the machine. Assistance is excluded from weight PRs and weight moved."
    else -> "Log the external load, or leave weight blank for bodyweight. Volume = external load × reps."
}

/** One observable/saveable value for a lazy editor, including writes awaiting an echo. */
@Immutable
private data class WorkoutSetDraft(
    val reps: String,
    val referenceLoad: String,
    val referenceWeightKg: Double,
    val load: String = referenceLoad,
    val draftPounds: Boolean,
    val completed: Boolean,
    val warmup: Boolean,
    val assistance: Boolean,
    val pendingSignature: String? = null,
    val pendingWeightKg: Double? = null,
    val pendingReps: Int? = null,
    val acknowledgedReps: Int,
    val acknowledgedWeightKg: Double,
    val submittedRepsText: String = reps,
    val submittedLoadText: String = load,
    val touched: Boolean = false,
) {
    fun synchronize(set: SetUi, pounds: Boolean): WorkoutSetDraft {
        if (pendingSignature == null && set.reps == acknowledgedReps && set.weightKg == acknowledgedWeightKg &&
            set.completed == completed && set.warmup == warmup && set.assistance == assistance && draftPounds == pounds)
            return this
        var next = this
        if (pendingSignature == null || pendingSignature == setEditSignature(set.reps, set.weightKg, set.completed, set.warmup, set.assistance)) {
            // A matching echo preserves entered text (including comma decimals).
            // External edits refresh only fields without newer unsubmitted input.
            val refreshReps = pendingSignature == null && set.reps != acknowledgedReps && reps == submittedRepsText
            val refreshLoad = pendingSignature == null && set.weightKg != acknowledgedWeightKg && load == submittedLoadText
            val nextReps = if (refreshReps) repsText(set.reps) else reps
            val nextLoad = if (refreshLoad) loadText(set.weightKg, pounds) else load
            next = copy(reps = nextReps, load = nextLoad,
                referenceLoad = if (refreshLoad) nextLoad else referenceLoad,
                referenceWeightKg = if (refreshLoad) set.weightKg else referenceWeightKg,
                acknowledgedReps = set.reps, acknowledgedWeightKg = set.weightKg,
                submittedRepsText = if (refreshReps) nextReps else submittedRepsText,
                submittedLoadText = if (refreshLoad) nextLoad else submittedLoadText,
                completed = set.completed, warmup = set.warmup, assistance = set.assistance,
                pendingSignature = null, pendingWeightKg = null, pendingReps = null)
        }
        // A row may restore a saved draft written in the former display unit.
        // Reformat from exact kg, never from the rounded field text.
        if (next.draftPounds != pounds) {
            val kg = next.pendingWeightKg ?: set.weightKg
            val text = loadText(kg, pounds)
            next = next.copy(referenceWeightKg = kg, referenceLoad = text, load = text,
                submittedLoadText = text, draftPounds = pounds)
        }
        return next
    }

    companion object {
        private fun repsText(reps: Int) = if (reps > 0) reps.toString() else ""
        private fun loadText(kg: Double, pounds: Boolean) = if (kg == 0.0) "" else number(kgToDisplay(kg, pounds))
        fun initial(set: SetUi, pounds: Boolean) = WorkoutSetDraft(
            reps = repsText(set.reps), referenceLoad = loadText(set.weightKg, pounds), referenceWeightKg = set.weightKg,
            draftPounds = pounds, completed = set.completed, warmup = set.warmup, assistance = set.assistance,
            acknowledgedReps = set.reps, acknowledgedWeightKg = set.weightKg)

        val Saver = listSaver<WorkoutSetDraft, Any?>(
            save = { listOf(it.reps, it.referenceLoad, it.referenceWeightKg, it.load, it.draftPounds,
                it.completed, it.warmup, it.assistance, it.pendingSignature, it.pendingWeightKg, it.pendingReps,
                it.acknowledgedReps, it.acknowledgedWeightKg, it.submittedRepsText, it.submittedLoadText, it.touched) },
            restore = { WorkoutSetDraft(it[0] as String, it[1] as String, it[2] as Double, it[3] as String,
                it[4] as Boolean, it[5] as Boolean, it[6] as Boolean, it[7] as Boolean, it[8] as String?,
                it[9] as Double?, it[10] as Int?, it[11] as Int, it[12] as Double,
                it[13] as String, it[14] as String, it[15] as Boolean) })
    }
}

@Composable
private fun SetEditor(set: SetUi, index: Int, pounds: Boolean, assistanceConvention: Boolean, actions: MoosclesActions,
    onInputFocusChanged: (String, Boolean) -> Unit = { _, _ -> }) {
    // A single saved snapshot replaces sixteen registry entries per lazy row.
    // Keep text, exact kg and pending acknowledgments together across disposal.
    var draft by rememberSaveable(set.id, stateSaver = WorkoutSetDraft.Saver) {
        mutableStateOf(WorkoutSetDraft.initial(set, pounds))
    }
    LaunchedEffect(set, pounds) { draft = draft.synchronize(set, pounds) }
    var flags by remember { mutableStateOf(false) }
    fun parsedReps() = if (draft.reps.isBlank()) 0 else draft.reps.toIntOrNull()
    fun parsedLoad() = if (draft.load.isBlank()) 0.0 else draft.load.replace(',', '.').toDoubleOrNull()
    fun exactKg(text: String, parsed: Double) = if (text == draft.referenceLoad) draft.referenceWeightKg else displayToKg(parsed, pounds)
    val repsValue = parsedReps()
    val loadValue = parsedLoad()
    val validReps = repsValue != null && repsValue in 1..9999
    val showRepsError = !validReps && (draft.touched || draft.reps.isNotBlank() || draft.completed)
    val validLoad = loadValue != null && loadValue.isFinite() && exactKg(draft.load, loadValue) in 0.0..100000.0
    fun submit(nextReps: Int, kg: Double, nextCompleted: Boolean, nextWarmup: Boolean, nextAssistance: Boolean) {
        val effectiveAssistance = nextAssistance || assistanceConvention
        draft = draft.copy(completed = nextCompleted, warmup = nextWarmup, assistance = effectiveAssistance,
            pendingSignature = setEditSignature(nextReps, kg, nextCompleted, nextWarmup, effectiveAssistance),
            pendingWeightKg = kg, pendingReps = nextReps,
            submittedRepsText = if (parsedReps() == nextReps) draft.reps else draft.submittedRepsText,
            submittedLoadText = if (parsedLoad()?.let { it.isFinite() && exactKg(draft.load, it) == kg } == true)
                draft.load else draft.submittedLoadText)
        actions.updateSet(set.id, nextReps, kg, nextCompleted, nextWarmup, effectiveAssistance)
    }
    fun save(nextCompleted: Boolean = draft.completed, nextWarmup: Boolean = draft.warmup, nextAssistance: Boolean = draft.assistance) {
        // Read the current drafts inside the callback, rather than the values
        // captured by the preceding composition while another edit is pending.
        val currentReps = parsedReps()
        val currentLoad = parsedLoad()
        val loadIsValid = currentLoad != null && currentLoad.isFinite() && exactKg(draft.load, currentLoad) in 0.0..100000.0
        if (nextCompleted && (currentReps == null || currentReps !in 1..9999 || !loadIsValid)) return
        submit(currentReps?.takeIf { it in 0..9999 } ?: draft.pendingReps ?: set.reps,
            if (loadIsValid) exactKg(draft.load, currentLoad!!) else draft.pendingWeightKg ?: set.weightKg, nextCompleted, nextWarmup, nextAssistance)
    }
    Column(Modifier.testTag("workout-set-${set.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (draft.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) { Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge) }
            TrainingNumberField(draft.load, { next ->
                draft = draft.copy(load = next)
                val parsed = if (next.isBlank()) 0.0 else next.replace(',', '.').toDoubleOrNull()
                val currentReps = parsedReps()
                if (parsed != null && parsed.isFinite() && exactKg(next, parsed) in 0.0..100000.0 && (!draft.completed || currentReps != null && currentReps in 1..9999))
                    submit(currentReps?.takeIf { it in 0..9999 } ?: draft.pendingReps ?: set.reps, exactKg(next, parsed), draft.completed, draft.warmup, draft.assistance)
            }, label = if (pounds) "lb" else "kg", isError = !validLoad, decimal = true, modifier = Modifier.weight(1f).testTag("set-load-$index")
                .onFocusChanged { onInputFocusChanged("load", it.isFocused) })
            TrainingNumberField(draft.reps, { next ->
                draft = draft.copy(reps = next)
                val parsed = if (next.isBlank()) 0 else next.toIntOrNull()
                val currentLoad = parsedLoad()
                if (parsed != null && parsed in 0..9999 && currentLoad != null && currentLoad.isFinite() && exactKg(draft.load, currentLoad) in 0.0..100000.0 && (!draft.completed || parsed > 0))
                    submit(parsed, exactKg(draft.load, currentLoad), draft.completed, draft.warmup, draft.assistance)
            }, label = "Reps", isError = showRepsError, modifier = Modifier.weight(1f).testTag("set-reps-$index")
                .onFocusChanged { onInputFocusChanged("reps", it.isFocused) })
            IconToggleButton(checked = draft.completed, onCheckedChange = { draft = draft.copy(touched = true); save(nextCompleted = it) }, modifier = Modifier.testTag("set-complete-$index"), colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary, checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(if (draft.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (draft.completed) "Undo set completion" else "Complete set") }
        }
        if (!validLoad) Text("Enter a valid weight of 0 or more.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (validLoad && loadValue == 0.0 && !draft.assistance && !assistanceConvention) Text("Bodyweight · no added load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showRepsError) Text("Enter reps between 1 and 9999 to complete this set.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { flags = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(if (draft.warmup) "Warm-up" else if (draft.assistance || assistanceConvention) "Assistance" else "Working set", style = MaterialTheme.typography.labelSmall); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                DropdownMenu(flags, { flags = false }) {
                    DropdownMenuItem(text = { Text("Working set") }, onClick = { flags = false; save(nextWarmup = false, nextAssistance = assistanceConvention) })
                    DropdownMenuItem(text = { Text("Warm-up · excluded from PRs") }, onClick = { flags = false; save(nextWarmup = true) })
                    if (!assistanceConvention) DropdownMenuItem(text = { Text("Assistance only · separate reps records") }, onClick = { flags = false; save(nextWarmup = false, nextAssistance = true) })
                }
            }
            IconButton(onClick = { actions.removeSet(set.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Remove set ${index + 1}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun setEditSignature(reps: Int, kg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) =
    "$reps:${kg.toBits()}:$completed:$warmup:$assistance"

@Composable
fun CompletedSetRow(set: SetUi, index: Int, pounds: Boolean, assistanceConvention: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SET ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(62.dp))
        Text("${if (set.weightKg == 0.0 && !set.assistance && !assistanceConvention) "Bodyweight" else weight(set.weightKg, pounds)} × ${set.reps}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (set.warmup || set.assistance || assistanceConvention) Text(if (set.warmup) "Warm-up" else "Assistance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Icon(Icons.Outlined.Check, "Completed", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
