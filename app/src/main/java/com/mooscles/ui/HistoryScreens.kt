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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mooscles.R
import com.mooscles.data.ExerciseNames
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.conventionLabel

@Composable
fun HistoryScreen(state: UiState, actions: MoosclesActions, onDetail: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val portuguese = catalogInPortuguese()
    val history = state.history.filter { workout -> search.isBlank() || (workout.name + " " + dateText(workout.startedAt, "yyyy MMMM d") + " " + workout.exercises.joinToString { legacyExerciseName(it.exercise, portuguese) }).contains(search.trim(), true) }.sortedByDescending { it.startedAt }
    LazyColumn(modifier = Modifier.testTag("history-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PageHeading(stringResource(R.string.legacy_history_eyebrow), stringResource(R.string.legacy_history_title), stringResource(R.string.legacy_history_subtitle)) }
        item { SearchField(search, { search = it }, stringResource(R.string.legacy_history_search)) }
        if (state.history.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(state.history.size.toString(), stringResource(R.string.legacy_workouts))
                    Metric(state.history.sumOf { it.completedSets }.toString(), stringResource(R.string.legacy_completed_sets))
                    Metric(state.history.sumOf { it.prExerciseCount }.toString(), stringResource(R.string.legacy_exercise_prs))
                }
            }
        }
        if (history.isEmpty()) item { EmptyState(Icons.Outlined.History, if (search.isBlank()) stringResource(R.string.legacy_history_empty_title) else stringResource(R.string.legacy_history_no_results), if (search.isBlank()) stringResource(R.string.legacy_history_empty_body) else stringResource(R.string.legacy_history_search_hint)) }
        items(history, key = { it.id }) { workout -> WorkoutHistoryCard(workout, state.preferences.pounds, onClick = { onDetail(workout.id) }) }
    }
}

@Composable
private fun WorkoutHistoryCard(workout: WorkoutUi, pounds: Boolean, onClick: () -> Unit) {
    val portuguese = catalogInPortuguese()
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
                Text(stringResource(R.string.legacy_exercise_count, workout.completedExercises), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.legacy_set_count, workout.completedSets), style = MaterialTheme.typography.bodySmall)
                Text(durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1000), style = MaterialTheme.typography.bodySmall)
            }
            Text(workout.exercises.filter { entry -> entry.sets.any { it.completed } }.take(3).joinToString(" · ") { legacyExerciseName(it.exercise, portuguese) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val prExercises = workout.prExerciseCount
            val first = workout.firstRecordExerciseCount
            if (prExercises > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary); Text(stringResource(if (prExercises == 1) R.string.legacy_prs_exercise_one else R.string.legacy_prs_exercise_other, prExercises) + if (first > 0) stringResource(R.string.legacy_first_records_suffix, first) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
fun WorkoutDetailScreen(workout: WorkoutUi, state: UiState, actions: MoosclesActions, onDeleted: () -> Unit, onExerciseHistory: (String) -> Unit) {
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
        item { PageHeading(if (editing) stringResource(R.string.legacy_editing_session) else stringResource(R.string.legacy_session_breakdown), workout.name) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(25.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(dateText(workout.startedAt, "EEEE, MMMM d, yyyy"), style = MaterialTheme.typography.titleMedium)
                    Text("${dateText(workout.startedAt, "HH:mm")} – ${dateText(workout.endedAt ?: workout.startedAt, "HH:mm")} · " + stringResource(R.string.legacy_elapsed, durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1000)), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .15f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(workout.completedExercises.toString(), stringResource(R.string.legacy_exercises))
                        Metric(workout.completedSets.toString(), stringResource(R.string.legacy_sets))
                        Metric(workout.prExerciseCount.toString(), stringResource(R.string.legacy_exercises_with_prs), Modifier.testTag("workout-pr-exercise-count"))
                    }
                    Metric(weight(workout.volumeKg, state.preferences.pounds), stringResource(R.string.legacy_weight_moved))
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
                SectionTitle(stringResource(R.string.legacy_new_milestones))
                beaten.forEach { record -> RecordMilestone(record, state.preferences.pounds, false) }
                if (first.isNotEmpty()) Text(stringResource(R.string.legacy_first_records, first.size, if (first.size != 1) "s" else ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                first.forEach { record -> RecordMilestone(record, state.preferences.pounds, true) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (editing) editing = false else confirmEdit = true }, modifier = Modifier.weight(1f)) { Icon(if (editing) Icons.Outlined.Check else Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (editing) stringResource(R.string.legacy_done_editing) else stringResource(R.string.legacy_edit_session)) }
                if (editing) OutlinedButton(onClick = { rename = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.legacy_rename)) }
                else OutlinedButton(onClick = { delete = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.legacy_delete_workout), tint = MaterialTheme.colorScheme.error) }
            }
        }
        item { SectionTitle(stringResource(R.string.legacy_every_rep), if (editing) stringResource(R.string.legacy_add_exercise) else null, { picker = true }) }
        itemsIndexed(workout.exercises, key = { _, it -> it.id }) { index, exercise ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkoutExerciseCard(exercise, index, workout.exercises.size, state.preferences.pounds, actions, editable = editing, onReplace = { replace = exercise.id; picker = true }, onHistory = { onExerciseHistory(exercise.exercise.id) })
                val volume = if (exercise.exercise.convention == "Assistance") 0.0 else exercise.sets.filter { it.completed && !it.assistance }.sumOf { it.weightKg * it.reps }
                Text(stringResource(R.string.legacy_volume_convention, weight(volume, state.preferences.pounds), conventionLabel(exercise.exercise.convention).lowercase()), Modifier.padding(horizontal = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (editing) item { TextButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.legacy_delete_this_workout), color = MaterialTheme.colorScheme.error) } }
        item { Text(stringResource(R.string.legacy_record_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (confirmEdit) AlertDialog(onDismissRequest = { confirmEdit = false }, title = { Text(stringResource(R.string.legacy_edit_saved_workout)) }, text = { Text(stringResource(R.string.legacy_edit_saved_workout_body)) }, confirmButton = { TextButton(onClick = { confirmEdit = false; editing = true }) { Text(stringResource(R.string.legacy_edit_workout)) } }, dismissButton = { TextButton(onClick = { confirmEdit = false }) { Text(stringResource(R.string.legacy_cancel)) } })
    if (delete) ConfirmDialog(stringResource(R.string.legacy_delete_workout_question), stringResource(R.string.legacy_delete_workout_body), onDismiss = { delete = false }, onConfirm = { actions.deleteWorkout(workout.id); onDeleted() })
    if (rename) NameDialog(stringResource(R.string.legacy_rename_workout), workout.name, onDismiss = { rename = false }, onSave = { actions.renameWorkout(workout.id, it) })
    if (picker) ExercisePicker(state.exercises, onDismiss = { picker = false; replace = null }, onSelect = { id -> replace?.let { actions.replaceWorkoutExercise(it, id) } ?: actions.addWorkoutExercise(workout.id, id) })
    if (share) WorkoutSharePreview(workout.id, state.preferences.pounds, onDismiss = { share = false })
}

@Composable
private fun RecordMilestone(record: RecordUi, pounds: Boolean, first: Boolean) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (first) Icons.Outlined.AutoAwesome else Icons.Outlined.EmojiEvents, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exerciseName(record.exercise), style = MaterialTheme.typography.titleSmall)
                Text(recordKindLabel(record) + if (first) " · " + stringResource(R.string.legacy_first_record) else "", style = MaterialTheme.typography.labelLarge)
                Text(localizedRecordAchievementText(record, pounds), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun legacyExerciseName(exercise: ExerciseUi, portuguese: Boolean): String =
    ExerciseNames.display(exercise.id, exercise.name, exercise.custom, portuguese)

@Composable
internal fun recordKindLabel(record: RecordUi): String =
    stringResource(if (record.kind == RecordKind.WEIGHT) R.string.legacy_record_weight else R.string.legacy_record_reps)

/** Localized UI equivalent of the unchanged pure English formatter used by compatibility tests. */
@Composable
internal fun localizedRecordAchievementText(record: RecordUi, pounds: Boolean): String {
    val load = when {
        record.assistance -> stringResource(R.string.legacy_assistance_value, weight(record.weightKg, pounds))
        record.weightKg == 0.0 -> stringResource(R.string.legacy_bodyweight)
        else -> weight(record.weightKg, pounds)
    }
    return if (record.kind == RecordKind.REPS) {
        stringResource(R.string.legacy_record_reps_value, record.previousReps?.let { "$it → " } ?: "", record.reps, load)
    } else "${record.previousKg?.let { "${weight(it, pounds)} → " } ?: ""}$load × ${record.reps}"
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
