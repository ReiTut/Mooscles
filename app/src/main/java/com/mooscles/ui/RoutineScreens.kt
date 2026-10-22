package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun RoutinesScreen(state: UiState, actions: MoosclesActions, onEdit: (String) -> Unit, onWorkout: () -> Unit) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<RoutineUi?>(null) }
    var delete by remember { mutableStateOf<RoutineUi?>(null) }
    LazyColumn(modifier = Modifier.testTag("routines-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeading("Build your momentum", "Let’s get\nstronger.", "A little stronger, one session at a time.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.active == null) "YOUR NEXT CHAPTER" else "PICK UP WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(state.active?.name ?: "Make today count", style = MaterialTheme.typography.headlineSmall)
                        }
                        Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(42.dp))
                    }
                    Text(if (state.active == null) "Choose your split below or go with the flow." else "${state.active.completedSets} sets logged · Your session is saved as you go.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { if (state.active == null) actions.startWorkout(null); onWorkout() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (state.active == null) "Start empty workout" else "Resume workout") }
                }
            }
        }
        item { SectionTitle("Your routines", "New routine", { create = true }) }
        if (state.routines.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.EventNote, "Find your rhythm", "Build a split that fits your week.", "Create your first routine", { create = true }) }
        itemsIndexed(state.routines, key = { _, it -> it.id }) { index, routine ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onEdit(routine.id) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d".format(index + 1), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) { Text(routine.name, style = MaterialTheme.typography.titleLarge); Text("${routine.exercises.size} exercises · ${routine.exercises.sumOf { it.sets }} planned sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Routine actions") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit exercises") }, onClick = { menu = false; onEdit(routine.id) })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = routine })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; actions.duplicateRoutine(routine.id) })
                                DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; actions.moveRoutine(routine.id, -1) })
                                DropdownMenuItem(text = { Text("Move down") }, enabled = index < state.routines.lastIndex, onClick = { menu = false; actions.moveRoutine(routine.id, 1) })
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete = routine })
                            }
                        }
                    }
                    if (routine.exercises.isNotEmpty()) Text(routine.exercises.take(3).joinToString("  ·  ") { it.exercise.name } + if (routine.exercises.size > 3) "  +${routine.exercises.size - 3}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onEdit(routine.id) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                        FilledTonalButton(onClick = { if (state.active == null) actions.startWorkout(routine.id); onWorkout() }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Text(if (state.active == null) "Start" else "Resume active") }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("Built for focus. Everything works offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (create) NameDialog("Create a routine", label = "Routine name", onDismiss = { create = false }, onSave = actions::createRoutine)
    rename?.let { routine -> NameDialog("Rename routine", routine.name, onDismiss = { rename = null }, onSave = { actions.renameRoutine(routine.id, it) }) }
    delete?.let { routine -> ConfirmDialog("Delete ${routine.name}?", "This removes the routine. Your completed workout history stays saved.", onDismiss = { delete = null }, onConfirm = { actions.deleteRoutine(routine.id) }) }
}

@Composable
fun RoutineEditor(routine: RoutineUi, state: UiState, actions: MoosclesActions, onWorkout: () -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.testTag("routine-editor-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Make a plan", routine.name, "Changes save automatically and apply to future sessions. Completed workouts keep their original details.", trailing = { IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename routine") } }) }
        item { Button(onClick = { if (state.active == null) actions.startWorkout(routine.id); onWorkout() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PlayArrow, null); Text(if (state.active == null) "Start this routine" else "Resume active workout") } }
        item { SectionTitle("Exercise plan", "Add exercise", { picker = true }) }
        if (routine.exercises.isEmpty()) item { EmptyState(Icons.Outlined.FitnessCenter, "Your plan starts here", "Add movements and set your targets.", "Add exercise", { picker = true }) }
        itemsIndexed(routine.exercises, key = { _, it -> it.id }) { index, planned ->
            PlannedExerciseCard(planned, index, routine.exercises.size, state.preferences.pounds, onMove = { actions.moveRoutineExercise(planned.id, it) }, onRemove = { actions.removeRoutineExercise(planned.id) }, onReplace = { replace = planned.id; picker = true }, onSave = { sets, reps, weight, rest -> actions.updateRoutineExercise(planned.id, sets, reps, weight, rest) })
        }
        item { OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add exercise") } }
    }
    if (rename) NameDialog("Rename routine", routine.name, onDismiss = { rename = false }, onSave = { actions.renameRoutine(routine.id, it) })
    if (picker) ExercisePicker(state.exercises, onDismiss = { picker = false; replace = null }, onSelect = { exerciseId -> replace?.let { actions.replaceRoutineExercise(it, exerciseId) } ?: actions.addRoutineExercise(routine.id, exerciseId) })
}

@Composable
private fun PlannedExerciseCard(planned: PlannedExerciseUi, index: Int, count: Int, pounds: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit, onReplace: () -> Unit, onSave: (Int, Int, Double?, Int) -> Unit) {
    // Keep local editing buffers through autosave echoes, while accepting actual
    // target changes made by the repository (for example, a newly earned PR).
    var sets by remember(planned.id, planned.exercise.id) { mutableStateOf(planned.sets.toString()) }
    var reps by remember(planned.id, planned.exercise.id) { mutableStateOf(planned.reps.takeIf { it > 0 }?.toString() ?: "") }
    var originalWeightKg by remember(planned.id, planned.exercise.id, pounds) { mutableStateOf(planned.weightKg) }
    val originalLoad = originalWeightKg?.let { number(kgToDisplay(it, pounds)) } ?: ""
    var load by remember(planned.id, planned.exercise.id, pounds) { mutableStateOf(originalLoad) }
    // Each valid field saves immediately using the last valid sibling values. A
    // temporarily empty set count must not prevent the rest duration from saving.
    var saved by remember(planned.id, planned.exercise.id, pounds) { mutableStateOf(PlannedValues(planned.sets, planned.reps, planned.weightKg, planned.restSeconds)) }
    var observed by remember(planned.id, planned.exercise.id, pounds) { mutableStateOf(saved) }
    val pendingSaves = remember(planned.id, planned.exercise.id, pounds) { mutableListOf<PlannedValues>() }
    val incoming = PlannedValues(planned.sets, planned.reps, planned.weightKg, planned.restSeconds)
    LaunchedEffect(incoming) {
        val acknowledged = pendingSaves.indexOf(incoming)
        if (acknowledged >= 0) {
            // Room can conflate several quick edits. An older acknowledgement
            // must not replace text the user has already entered since then.
            pendingSaves.subList(0, acknowledged + 1).clear()
        } else {
            if (incoming.sets != observed.sets) {
                if (incoming.sets != saved.sets) sets = incoming.sets.toString()
                saved = saved.copy(sets = incoming.sets)
            }
            if (incoming.reps != observed.reps) {
                if (incoming.reps != saved.reps) reps = incoming.reps.takeIf { it > 0 }?.toString() ?: ""
                saved = saved.copy(reps = incoming.reps)
            }
            if (incoming.weightKg != observed.weightKg) {
                if (incoming.weightKg != saved.weightKg) {
                    originalWeightKg = incoming.weightKg
                    load = incoming.weightKg?.let { number(kgToDisplay(it, pounds)) } ?: ""
                }
                saved = saved.copy(weightKg = incoming.weightKg)
            }
            if (incoming.restSeconds != observed.restSeconds) saved = saved.copy(restSeconds = incoming.restSeconds)
        }
        observed = incoming
    }
    var menu by remember { mutableStateOf(false) }
    fun parseLoad(text: String): Double? {
        val normalized = text.replace(',', '.')
        val value = normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        return (if (normalized == originalLoad) originalWeightKg else displayToKg(value, pounds))
            ?.takeIf { it.isFinite() && it in 0.0..100000.0 }
    }
    val validSets = sets.toIntOrNull()?.let { it in 1..50 } == true
    val validReps = reps.isBlank() || reps.toIntOrNull()?.let { it in 0..9999 } == true
    val validLoad = load.isBlank() || parseLoad(load) != null
    fun save(values: PlannedValues) {
        if (values == saved) return
        saved = values
        pendingSaves += values
        onSave(values.sets, values.reps, values.weightKg, values.restSeconds)
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${index + 1}. ${planned.exercise.name}", style = MaterialTheme.typography.titleMedium); Text("${human(planned.exercise.muscle)} · ${human(planned.exercise.equipment)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Exercise actions") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { menu = false; onReplace() })
                        DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanField(sets, { text ->
                    sets = text
                    text.toIntOrNull()?.takeIf { it in 1..50 }?.let { save(saved.copy(sets = it)) }
                }, "Sets", !validSets, "1–50", Modifier.weight(1f).testTag("routine-${planned.id}-sets"))
                PlanField(reps, { text ->
                    reps = text
                    val value = if (text.isBlank()) 0 else text.toIntOrNull()
                    value?.takeIf { it in 0..9999 }?.let { save(saved.copy(reps = it)) }
                }, "Reps", !validReps, "Optional · 1–9999", Modifier.weight(1f).testTag("routine-${planned.id}-reps"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanField(load, { text ->
                    load = text
                    if (text.isBlank()) save(saved.copy(weightKg = null))
                    else parseLoad(text)?.let { save(saved.copy(weightKg = it)) }
                }, if (pounds) "Target lb" else "Target kg", !validLoad, "Optional · 0 or more", Modifier.weight(1f).testTag("routine-${planned.id}-load"), decimal = true)
                RestDurationField(saved.restSeconds, { save(saved.copy(restSeconds = it)) }, modifier = Modifier.weight(1f), testTag = "routine-${planned.id}-rest")
            }
            Text("Logging: ${planned.exercise.convention}. Load × reps, as entered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OrderButtons(index > 0, index < count - 1, onMove)
        }
    }
}

private data class PlannedValues(val sets: Int, val reps: Int, val weightKg: Double?, val restSeconds: Int)

@Composable
private fun PlanField(value: String, onChange: (String) -> Unit, label: String, error: Boolean, help: String, modifier: Modifier, decimal: Boolean = false) {
    TrainingNumberField(value, onChange, label, isError = error, modifier = modifier, help = help, decimal = decimal)
}
