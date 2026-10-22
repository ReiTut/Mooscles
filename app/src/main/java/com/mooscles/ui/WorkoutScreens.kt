package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.mooscles.domain.RestDuration

@Composable
fun ActiveWorkoutScreen(state: UiState, actions: MoosclesActions, onRoutines: () -> Unit, onHistory: (String) -> Unit, onExerciseHistory: (String) -> Unit) {
    val workout = state.active
    var picker by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var finishingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.history, state.active, finishingId) {
        finishingId?.let { id -> if (state.history.any { it.id == id }) { finishingId = null; onHistory(id) } }
    }
    LaunchedEffect(state.error) { if (state.error != null) finishingId = null }
    if (workout == null) {
        Column(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { PageHeading("Your training space", "Ready to move?", "Every rep is a step forward.") }
                item { EmptyState(Icons.Outlined.FitnessCenter, "A fresh start", "Start with a routine or build today’s session as you go.", "Choose a routine", onRoutines) }
                item { Button(onClick = { actions.startWorkout(null) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Start empty workout") } }
            }
        }
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(workout.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Column(Modifier.fillMaxSize().imePadding()) {
    LazyColumn(modifier = Modifier.weight(1f).testTag("active-workout-list"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("In your element", workout.name, trailing = { IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename workout") } }) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(durationText((now - workout.startedAt) / 1000), "Elapsed")
                Metric(workout.completedSets.toString(), "Sets done")
                Metric(weight(workout.volumeKg, state.preferences.pounds), "Weight moved")
            }
        }
        item { SectionTitle("Today’s movements", "Add", { picker = true }) }
        if (workout.exercises.isEmpty()) item { EmptyState(Icons.Outlined.AddCircleOutline, "Make the first move", "Add an exercise to begin logging sets.", "Add exercise", { picker = true }) }
        itemsIndexed(workout.exercises, key = { _, it -> it.id }) { index, entry ->
            WorkoutExerciseCard(entry, index, workout.exercises.size, state.preferences.pounds, actions, editable = true,
                onReplace = { replace = entry.id; picker = true }, onHistory = { onExerciseHistory(entry.exercise.id) })
        }
        item { OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add exercise") } }
        item {
            Button(onClick = { finish = true }, enabled = workout.completedSets > 0 && finishingId == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("finish-workout")) {
                if (finishingId != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.CheckCircleOutline, null)
                Spacer(Modifier.width(8.dp)); Text(if (finishingId == null) "Finish workout" else "Saving workout…")
            }
            if (workout.completedSets == 0) Text("Complete at least one set to finish your workout.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { TextButton(onClick = { discard = true }, modifier = Modifier.fillMaxWidth()) { Text("Discard workout", color = MaterialTheme.colorScheme.error) } }
    }
    }
    if (picker) ExercisePicker(state.exercises, onDismiss = { picker = false; replace = null }, onSelect = { id -> replace?.let { actions.replaceWorkoutExercise(it, id) } ?: actions.addWorkoutExercise(workout.id, id) })
    if (rename) NameDialog("Name this workout", workout.name, onDismiss = { rename = false }, onSave = { actions.renameWorkout(workout.id, it) })
    if (discard) ConfirmDialog("Discard this workout?", "All sets from this active session will be removed. This cannot be undone.", "Discard", onDismiss = { discard = false }, onConfirm = actions::discardWorkout)
    if (finish) AlertDialog(onDismissRequest = { finish = false }, icon = { Icon(Icons.Outlined.CheckCircleOutline, null) }, title = { Text("Ready to call it?") },
        text = { Text("Save ${workout.completedSets} completed sets across ${workout.completedExercises} exercises. Your records and session breakdown will be ready next.") },
        confirmButton = { TextButton(onClick = { finishingId = workout.id; finish = false; actions.finishWorkout() }) { Text("Finish & save") } }, dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep training") } })
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
fun WorkoutExerciseCard(entry: WorkoutExerciseUi, index: Int, count: Int, pounds: Boolean, actions: MoosclesActions, editable: Boolean, onReplace: () -> Unit, onHistory: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${entry.exercise.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${human(entry.exercise.equipment)} · ${entry.exercise.convention}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Exercise options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Performance history") }, onClick = { menu = false; onHistory() })
                        if (editable) {
                            DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { menu = false; onReplace() })
                            DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; actions.moveWorkoutExercise(entry.id, -1) })
                            DropdownMenuItem(text = { Text("Move down") }, enabled = index < count - 1, onClick = { menu = false; actions.moveWorkoutExercise(entry.id, 1) })
                            DropdownMenuItem(text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; remove = true })
                        }
                    }
                }
            }
            Text(conventionExplanation(entry.exercise.convention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.previous?.let { previous ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Outlined.History, null, Modifier.size(17.dp)); Text("Previous: $previous", style = MaterialTheme.typography.bodySmall) }
                }
            }
            entry.records.forEach { record ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(18.dp)); Text("${record.label}${if (record.firstRecord) " · first record" else ""} · ${recordAchievementText(record, pounds)}${if (editable) " · provisional" else ""}", style = MaterialTheme.typography.labelMedium) }
                }
            }
            val visibleSets = if (editable) entry.sets else entry.sets.filter { it.completed }
            if (editable) RestDurationField(entry.restSeconds,
                { actions.updateWorkoutExerciseRest(entry.id, it) }, Modifier.fillMaxWidth(),
                testTag = "workout-rest-${entry.id}",
                help = "For your next rest. Use the timer controls to adjust rest in progress.")
            if (visibleSets.isEmpty()) Text("No completed sets", style = MaterialTheme.typography.bodySmall)
            visibleSets.forEachIndexed { setIndex, set ->
                if (editable) SetEditor(set, setIndex, pounds, entry.exercise.convention == "Assistance", actions)
                else CompletedSetRow(set, setIndex, pounds, entry.exercise.convention == "Assistance")
            }
            if (editable) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { actions.addSet(entry.id) }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("Add set") }
                val rest = entry.restSeconds.coerceIn(0, RestDuration.MAX_SECONDS)
                TextButton(onClick = { actions.startTimer(entry.exercise.name, rest) }) { Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Rest ${RestDuration.format(rest)} min") }
            }
        }
    }
    if (remove) ConfirmDialog("Remove ${entry.exercise.name}?", "This removes this exercise and all of its sets from the workout.", "Remove", onDismiss = { remove = false }, onConfirm = { actions.removeWorkoutExercise(entry.id) })
}

fun conventionExplanation(convention: String): String = when (convention) {
    "Per hand" -> "Log one dumbbell’s load and reps. Volume is per hand; never doubled."
    "One side" -> "Log the total load used on one side and reps on that side. Volume is never doubled."
    "Added weight" -> "Log added external weight only. Leave weight blank for bodyweight; body mass is excluded."
    "Assistance" -> "Log assistance supplied by the machine. Assistance is excluded from weight PRs and weight moved."
    else -> "Log the external load, or leave weight blank for bodyweight. Volume = external load × reps."
}

@Composable
private fun SetEditor(set: SetUi, index: Int, pounds: Boolean, assistanceConvention: Boolean, actions: MoosclesActions) {
    var reps by remember(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    val originalLoad = remember(set.id, pounds) { if (set.weightKg == 0.0) "" else number(kgToDisplay(set.weightKg, pounds)) }
    val originalWeightKg = remember(set.id, pounds) { set.weightKg }
    var load by remember(set.id, pounds) { mutableStateOf(originalLoad) }
    var touched by remember(set.id) { mutableStateOf(false) }
    var flags by remember { mutableStateOf(false) }
    val repsValue = if (reps.isBlank()) 0 else reps.toIntOrNull()
    val loadValue = if (load.isBlank()) 0.0 else load.replace(',', '.').toDoubleOrNull()
    fun exactKg(text: String, parsed: Double) = if (text == originalLoad) originalWeightKg else displayToKg(parsed, pounds)
    val validReps = repsValue != null && repsValue in 1..9999
    val showRepsError = !validReps && (touched || reps.isNotBlank() || set.completed)
    val validLoad = loadValue != null && loadValue.isFinite() && exactKg(load, loadValue) in 0.0..100000.0
    fun save(completed: Boolean = set.completed, warmup: Boolean = set.warmup, assistance: Boolean = set.assistance) {
        if (completed && (!validReps || !validLoad)) return
        actions.updateSet(set.id, repsValue?.takeIf { it in 0..9999 } ?: set.reps, if (validLoad) exactKg(load, loadValue!!) else set.weightKg, completed, warmup, assistance || assistanceConvention)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (set.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) { Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge) }
            TrainingNumberField(load, { next ->
                load = next
                val parsed = if (next.isBlank()) 0.0 else next.replace(',', '.').toDoubleOrNull()
                if (parsed != null && parsed.isFinite() && exactKg(next, parsed) in 0.0..100000.0 && (!set.completed || validReps)) actions.updateSet(set.id, repsValue?.takeIf { it in 0..9999 } ?: set.reps, exactKg(next, parsed), set.completed, set.warmup, set.assistance || assistanceConvention)
            }, label = if (pounds) "lb" else "kg", isError = !validLoad, decimal = true, modifier = Modifier.weight(1f).testTag("set-load-$index"))
            TrainingNumberField(reps, { next ->
                reps = next
                val parsed = if (next.isBlank()) 0 else next.toIntOrNull()
                if (parsed != null && parsed in 0..9999 && validLoad && (!set.completed || parsed > 0)) actions.updateSet(set.id, parsed, exactKg(load, loadValue!!), set.completed, set.warmup, set.assistance || assistanceConvention)
            }, label = "Reps", isError = showRepsError, modifier = Modifier.weight(1f).testTag("set-reps-$index"))
            IconToggleButton(checked = set.completed, onCheckedChange = { touched = true; save(completed = it) }, modifier = Modifier.testTag("set-complete-$index"), colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary, checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(if (set.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (set.completed) "Undo set completion" else "Complete set") }
        }
        if (!validLoad) Text("Enter a valid weight of 0 or more.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (validLoad && loadValue == 0.0 && !set.assistance && !assistanceConvention) Text("Bodyweight · no added load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showRepsError) Text("Enter reps between 1 and 9999 to complete this set.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { flags = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(if (set.warmup) "Warm-up" else if (set.assistance || assistanceConvention) "Assistance" else "Working set", style = MaterialTheme.typography.labelSmall); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                DropdownMenu(flags, { flags = false }) {
                    DropdownMenuItem(text = { Text("Working set") }, onClick = { flags = false; save(warmup = false, assistance = assistanceConvention) })
                    DropdownMenuItem(text = { Text("Warm-up · excluded from PRs") }, onClick = { flags = false; save(warmup = true) })
                    if (!assistanceConvention) DropdownMenuItem(text = { Text("Assistance only · separate reps records") }, onClick = { flags = false; save(warmup = false, assistance = true) })
                }
            }
            IconButton(onClick = { actions.removeSet(set.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Remove set ${index + 1}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun CompletedSetRow(set: SetUi, index: Int, pounds: Boolean, assistanceConvention: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SET ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(62.dp))
        Text("${if (set.weightKg == 0.0 && !set.assistance && !assistanceConvention) "Bodyweight" else weight(set.weightKg, pounds)} × ${set.reps}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (set.warmup || set.assistance || assistanceConvention) Text(if (set.warmup) "Warm-up" else "Assistance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Icon(Icons.Outlined.Check, "Completed", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
