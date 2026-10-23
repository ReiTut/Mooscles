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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun RoutinesScreen(state: UiState, actions: MoosclesActions, onEdit: (String) -> Unit, onWorkout: () -> Unit) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<RoutineUi?>(null) }
    var delete by remember { mutableStateOf<RoutineUi?>(null) }
    var blockedStart by remember { mutableStateOf<RoutineUi?>(null) }
    LaunchedEffect(state.active?.id) { blockedStart = null }
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
            val resumesThisRoutine = state.active?.sourceRoutineId == routine.id
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
                        FilledTonalButton(onClick = {
                            when {
                                resumesThisRoutine -> onWorkout()
                                state.active != null -> blockedStart = routine
                                else -> { actions.startWorkout(routine.id); onWorkout() }
                            }
                        }, modifier = Modifier.weight(1f).testTag("routine-${routine.id}-start")) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                            Text(if (resumesThisRoutine) "Resume active" else "Start")
                        }
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
    blockedStart?.let { target -> state.active?.let { active ->
        ActiveWorkoutConflict(active.name, target.name, onDismiss = { blockedStart = null },
            onResume = { blockedStart = null; onWorkout() })
    } }
}

@Composable
fun RoutineEditor(routine: RoutineUi, state: UiState, actions: MoosclesActions, onWorkout: () -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<String?>(null) }
    var blockedStart by remember { mutableStateOf(false) }
    var ordering by rememberSaveable(routine.id) { mutableStateOf(false) }
    LaunchedEffect(state.active?.id) { blockedStart = false }
    val resumesThisRoutine = state.active?.sourceRoutineId == routine.id
    LazyColumn(modifier = Modifier.fillMaxSize().imePadding().testTag("routine-editor-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeading("Make a plan", routine.name, "Changes save automatically and apply to future sessions. Completed workouts keep their original details.", trailing = { IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename routine") } }) }
        item { Button(onClick = {
            when {
                resumesThisRoutine -> onWorkout()
                state.active != null -> blockedStart = true
                else -> { actions.startWorkout(routine.id); onWorkout() }
            }
        }, modifier = Modifier.fillMaxWidth().testTag("routine-editor-start")) {
            Icon(Icons.Outlined.PlayArrow, null)
            Text(if (resumesThisRoutine) "Resume active workout" else "Start this routine")
        } }
        item { Text("Exercise plan", style = MaterialTheme.typography.titleLarge) }
        item { ExercisePlanActions(onAdd = { picker = true }, onReorder = { ordering = true }, canReorder = routine.exercises.size > 1) }
        if (routine.exercises.isEmpty()) item { EmptyState(Icons.Outlined.FitnessCenter, "Your plan starts here", "Add movements and set your targets.", "Add exercise", { picker = true }) }
        routine.exercises.forEach { planned ->
            item(key = "heading-${planned.id}") {
                RoutineExerciseHeading(planned, state.preferences.pounds,
                    onRemove = { actions.removeRoutineExercise(planned.id) },
                    onReplace = { replace = planned.id; picker = true },
                    onRestChange = { actions.updateRoutineExerciseRest(planned.id, it) })
            }
            itemsIndexed(planned.setTargets, key = { _, target -> "set-${target.id}" }) { setIndex, target ->
                RoutineTargetRow(target, planned.exercise, setIndex + 1, state.preferences.pounds,
                    canRemove = planned.setTargets.size > 1,
                    onSave = { reps, weight -> actions.updateRoutineSet(target.id, reps, weight) },
                    onRemove = { actions.removeRoutineSet(target.id) },
                    onInherit = { actions.inheritRoutineSet(target.id) })
            }
            item(key = "footer-${planned.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = { actions.addRoutineSet(planned.id) }, enabled = planned.setTargets.size < 50,
                        modifier = Modifier.fillMaxWidth().testTag("routine-${planned.id}-add-set")) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add Set")
                    }
                    Text("Blank weight = bodyweight · Logging: ${planned.exercise.convention}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 2.dp, bottom = 9.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (rename) NameDialog("Rename routine", routine.name, onDismiss = { rename = false }, onSave = { actions.renameRoutine(routine.id, it) })
    if (picker) ExercisePicker(state.exercises, onDismiss = { picker = false; replace = null }, onSelect = { exerciseId -> replace?.let { actions.replaceRoutineExercise(it, exerciseId) } ?: actions.addRoutineExercise(routine.id, exerciseId) })
    if (ordering) ExerciseOrderEditor(routine.exercises.map { OrderExerciseUi(it.id, it.exercise.name) },
        onSave = { ids -> actions.reorderRoutineExercises(routine.id, ids); ordering = false },
        onCancel = { ordering = false })
    if (blockedStart) state.active?.let { active ->
        ActiveWorkoutConflict(active.name, routine.name, onDismiss = { blockedStart = false },
            onResume = { blockedStart = false; onWorkout() })
    }
}

@Composable
private fun ActiveWorkoutConflict(activeName: String, targetName: String, onDismiss: () -> Unit, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("A workout is already in progress") },
        text = { Text("Finish or discard $activeName before starting $targetName. Your current session is saved.") },
        confirmButton = { TextButton(onClick = onResume) { Text("Resume current workout") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
