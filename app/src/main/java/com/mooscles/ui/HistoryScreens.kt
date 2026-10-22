package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mooscles.R

@Composable
fun HistoryScreen(state: UiState, actions: MoosclesActions, onDetail: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val history = state.history.filter { workout -> search.isBlank() || (workout.name + " " + dateText(workout.startedAt, "yyyy MMMM d") + " " + workout.exercises.joinToString { it.exercise.name }).contains(search.trim(), true) }.sortedByDescending { it.startedAt }
    LazyColumn(modifier = Modifier.testTag("history-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PageHeading("Look how far you’ve come", "Your history", "Every session tells part of the story.") }
        item { SearchField(search, { search = it }, "Search workouts, exercises, dates") }
        if (state.history.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(state.history.size.toString(), "Workouts")
                    Metric(state.history.sumOf { it.completedSets }.toString(), "Completed sets")
                    Metric(state.history.sumOf { it.prExerciseCount }.toString(), "Exercise PRs")
                }
            }
        }
        if (history.isEmpty()) item { EmptyState(Icons.Outlined.History, if (search.isBlank()) "The story starts today" else "No sessions found", if (search.isBlank()) "Finished workouts appear here with every set and record." else "Try an exercise, date, or workout name.") }
        items(history, key = { it.id }) { workout -> WorkoutHistoryCard(workout, state.preferences.pounds, onClick = { onDetail(workout.id) }) }
    }
}

@Composable
private fun WorkoutHistoryCard(workout: WorkoutUi, pounds: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dateText(workout.startedAt).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(5.dp)); Text(workout.name, style = MaterialTheme.typography.titleLarge)
                }
                Icon(Icons.Outlined.ChevronRight, null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${workout.completedExercises} exercises", style = MaterialTheme.typography.bodySmall)
                Text("${workout.completedSets} sets", style = MaterialTheme.typography.bodySmall)
                Text(durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1000), style = MaterialTheme.typography.bodySmall)
            }
            Text(workout.exercises.filter { entry -> entry.sets.any { it.completed } }.take(3).joinToString(" · ") { it.exercise.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val prExercises = workout.prExerciseCount
            val first = workout.firstRecordExerciseCount
            if (prExercises > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary); Text("$prExercises exercise${if (prExercises != 1) "s" else ""} with PRs${if (first > 0) " · $first with first records" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
fun WorkoutDetailScreen(workout: WorkoutUi, state: UiState, actions: MoosclesActions, summary: Boolean, onDeleted: () -> Unit, onExerciseHistory: (String) -> Unit) {
    var editing by remember(workout.id) { mutableStateOf(false) }
    var confirmEdit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<String?>(null) }
    var share by remember(workout.id) { mutableStateOf(false) }
    val beaten = workout.records.filterNot { it.firstRecord }
    val first = workout.records.filter { it.firstRecord }
    LazyColumn(modifier = Modifier.testTag("workout-details"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeading(if (editing) "Editing saved session" else if (summary) "Workout complete" else "Session breakdown", if (summary && !editing) "Strong work." else workout.name, if (summary && !editing) workout.name else null) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(25.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(dateText(workout.startedAt, "EEEE, MMMM d, yyyy"), style = MaterialTheme.typography.titleMedium)
                    Text("${dateText(workout.startedAt, "HH:mm")} – ${dateText(workout.endedAt ?: workout.startedAt, "HH:mm")} · ${durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1000)} elapsed", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .15f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(workout.completedExercises.toString(), "Exercises")
                        Metric(workout.completedSets.toString(), "Sets")
                        Metric(workout.prExerciseCount.toString(), "Exercises with PRs", Modifier.testTag("workout-pr-exercise-count"))
                    }
                    Metric(weight(workout.volumeKg, state.preferences.pounds), "Total weight moved · logged load × reps")
                }
            }
        }
        if (!editing && workout.endedAt != null) item {
            FilledTonalButton(onClick = { share = true }, modifier = Modifier.fillMaxWidth().testTag("share-workout")) {
                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_workout))
            }
        }
        if (workout.records.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("New milestones")
                beaten.forEach { record -> RecordMilestone(record, state.preferences.pounds, false) }
                if (first.isNotEmpty()) Text("${first.size} first record${if (first.size != 1) "s" else ""} · separate from records beaten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                first.forEach { record -> RecordMilestone(record, state.preferences.pounds, true) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (editing) editing = false else confirmEdit = true }, modifier = Modifier.weight(1f)) { Icon(if (editing) Icons.Outlined.Check else Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (editing) "Done editing" else "Edit session") }
                if (editing) OutlinedButton(onClick = { rename = true }, modifier = Modifier.weight(1f)) { Text("Rename") }
                else OutlinedButton(onClick = { delete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete workout", tint = MaterialTheme.colorScheme.error) }
            }
        }
        item { SectionTitle("Every rep, recorded", if (editing) "Add exercise" else null, { picker = true }) }
        itemsIndexed(workout.exercises, key = { _, it -> it.id }) { index, exercise ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkoutExerciseCard(exercise, index, workout.exercises.size, state.preferences.pounds, actions, editable = editing, onReplace = { replace = exercise.id; picker = true }, onHistory = { onExerciseHistory(exercise.exercise.id) })
                val volume = if (exercise.exercise.convention == "Assistance") 0.0 else exercise.sets.filter { it.completed && !it.assistance }.sumOf { it.weightKg * it.reps }
                Text("${weight(volume, state.preferences.pounds)} moved · ${exercise.exercise.convention.lowercase()} convention", Modifier.padding(horizontal = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (editing) item { TextButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete this workout", color = MaterialTheme.colorScheme.error) } }
        item { Text("Weight PRs reward a heavier working set. Reps PRs compare repetitions at the same external load, including bodyweight. Warm-ups and incomplete sets are excluded; assisted sets are tracked separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (confirmEdit) AlertDialog(onDismissRequest = { confirmEdit = false }, title = { Text("Edit saved workout?") }, text = { Text("Changes save immediately and recalculate this exercise’s records across your history. Finish with Done editing when you’re ready.") }, confirmButton = { TextButton(onClick = { confirmEdit = false; editing = true }) { Text("Edit workout") } }, dismissButton = { TextButton(onClick = { confirmEdit = false }) { Text("Cancel") } })
    if (delete) ConfirmDialog("Delete this workout?", "This permanently removes this session and recalculates affected personal records.", onDismiss = { delete = false }, onConfirm = { actions.deleteWorkout(workout.id); onDeleted() })
    if (rename) NameDialog("Rename workout", workout.name, onDismiss = { rename = false }, onSave = { actions.renameWorkout(workout.id, it) })
    if (picker) ExercisePicker(state.exercises, onDismiss = { picker = false; replace = null }, onSelect = { id -> replace?.let { actions.replaceWorkoutExercise(it, id) } ?: actions.addWorkoutExercise(workout.id, id) })
    if (share) WorkoutSharePreview(workout.id, state.preferences.pounds, onDismiss = { share = false })
}

@Composable
private fun RecordMilestone(record: RecordUi, pounds: Boolean, first: Boolean) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (first) Icons.Outlined.AutoAwesome else Icons.Outlined.EmojiEvents, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.exercise.name, style = MaterialTheme.typography.titleSmall)
                Text("${record.label}${if (first) " · First record" else ""}", style = MaterialTheme.typography.labelLarge)
                Text(recordAchievementText(record, pounds), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** One description is shared by the session milestones and the per-exercise badges. */
fun recordAchievementText(record: RecordUi, pounds: Boolean): String {
    val load = when {
        record.assistance -> "${weight(record.weightKg, pounds)} assistance"
        record.weightKg == 0.0 -> "Bodyweight"
        else -> weight(record.weightKg, pounds)
    }
    return if (record.kind == RecordKind.REPS) {
        "${record.previousReps?.let { "$it → " } ?: ""}${record.reps} reps · $load"
    } else {
        "${record.previousKg?.let { "${weight(it, pounds)} → " } ?: ""}$load × ${record.reps}"
    }
}

@Composable
fun RecordsScreen(state: UiState, onExercise: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val records = state.records.filter { search.isBlank() || it.exercise.name.contains(search.trim(), true) }.sortedBy { it.exercise.name }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("Proof of your progress", "Personal records", "Weight and repetition records for each unique movement and load.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null)
                    Text("Every kind of progress.", style = MaterialTheme.typography.titleLarge)
                    Text("105 kg × 3 beats 100 kg × 10 for a Weight PR. More reps at the same load earns a Reps PR, including bodyweight.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { SearchField(search, { search = it }, "Find a movement’s record") }
        if (records.isEmpty()) item { EmptyState(Icons.Outlined.EmojiEvents, if (search.isBlank()) "Your next milestone awaits" else "No matching records", "Complete working sets to build your weight and repetition records.") }
        items(records, key = { it.exercise.id + it.exercise.convention + it.kind.name + it.weightKg + it.assistance }) { record ->
            Card(onClick = { onExercise(record.exercise.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(record.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight, null) }
                    Text(record.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    Text(recordAchievementText(record, state.preferences.pounds), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${record.exercise.convention} · ${dateText(record.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ExerciseHistoryScreen(exercise: ExerciseUi, state: UiState, actions: MoosclesActions, onWorkout: (String) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    val sessions = state.history.filter { workout -> workout.exercises.any { it.exercise.id == exercise.id && it.sets.any { set -> set.completed } } }.sortedByDescending { it.startedAt }
    val record = state.records.filter { it.exercise.id == exercise.id }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Movement history", exercise.name, "${human(exercise.muscle)} · ${human(exercise.equipment)}", trailing = if (exercise.custom) ({ IconButton(onClick = { edit = true }) { Icon(Icons.Outlined.Edit, "Edit custom exercise") } }) else null) }
        item { Text(conventionExplanation(exercise.convention), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (record.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PERSONAL BEST", style = MaterialTheme.typography.labelSmall)
                    record.forEach { pr ->
                        Text(pr.label, style = MaterialTheme.typography.labelLarge)
                        Text(recordAchievementText(pr, state.preferences.pounds), style = MaterialTheme.typography.titleLarge)
                    }
                    Text("${sessions.size} recorded sessions", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { SectionTitle("Performance timeline") }
        if (sessions.isEmpty()) item { EmptyState(Icons.Outlined.Insights, "A clean slate", "Log this exact movement in a workout to see its progress here.") }
        items(sessions, key = { it.id }) { workout ->
            val entries = workout.exercises.filter { it.exercise.id == exercise.id }
            Card(onClick = { onWorkout(workout.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(dateText(workout.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(workout.name, style = MaterialTheme.typography.titleMedium) }; Icon(Icons.Outlined.ChevronRight, null) }
                    entries.forEach { entry ->
                        Text("Logged as ${entry.exercise.name} · ${entry.exercise.convention}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        entry.sets.filter { it.completed }.forEachIndexed { index, set -> CompletedSetRow(set, index, state.preferences.pounds, entry.exercise.convention == "Assistance") }
                    }
                    val volume = entries.filter { it.exercise.convention != "Assistance" }.sumOf { entry -> entry.sets.filter { it.completed && !it.assistance }.sumOf { it.weightKg * it.reps } }
                    Text("${weight(volume, state.preferences.pounds)} moved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (edit) CustomExerciseDialog(exercise, state.exercises, actions, onDismiss = { edit = false })
}
