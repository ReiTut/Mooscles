package com.mooscles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mooscles.data.ExerciseSearch

@Composable
fun LibraryScreen(state: UiState, actions: MoosclesActions, onHistory: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var customOnly by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    val filtered = state.exercises.filter { exercise ->
        ExerciseSearch.matches(exercise.id, exercise.name, exercise.muscle, exercise.equipment, search) &&
            (muscle.isEmpty() || exercise.muscle == muscle) && (equipment.isEmpty() || exercise.equipment == equipment) && (!customOnly || exercise.custom)
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Know your movements", "Exercise library", "${state.exercises.size} exercises. Every variation keeps its own history.", trailing = { FilledIconButton(onClick = { create = true }) { Icon(Icons.Outlined.Add, "Create custom exercise") } }) }
        item { SearchField(search, { search = it }, "Search exercises") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceFilter("Muscle", muscle, state.exercises.map { it.muscle }.distinct().sorted(), { muscle = it }, Modifier.weight(1f))
                ChoiceFilter("Equipment", equipment, state.exercises.map { it.equipment }.distinct().sorted(), { equipment = it }, Modifier.weight(1f))
            }
            FilterChip(selected = customOnly, onClick = { customOnly = !customOnly }, label = { Text("My exercises") }, leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp)) })
        }
        item { Text("${filtered.size} movements", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (filtered.isEmpty()) item { EmptyState(Icons.Outlined.SearchOff, "No exercises found", "Try another search or create your own movement.", "Create exercise", { create = true }) }
        items(filtered, key = { it.id }) { exercise -> ExerciseCard(exercise, onClick = { onHistory(exercise.id) }) }
    }
    if (create) CustomExerciseDialog(null, state.exercises, actions, onDismiss = { create = false })
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear search") } }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
}

@Composable
fun ChoiceFilter(label: String, selected: String, choices: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, allowAll: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            Text(if (selected.isBlank()) label else human(selected), maxLines = 1, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            if (allowAll) DropdownMenuItem(text = { Text("All ${label.lowercase()}") }, onClick = { onSelect(""); expanded = false })
            choices.forEach { option -> DropdownMenuItem(text = { Text(human(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
fun ExerciseCard(exercise: ExerciseUi, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.FitnessCenter, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text("${human(exercise.muscle)} · ${human(exercise.equipment)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exercise.custom) Text("CUSTOM EXERCISE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (trailing == null) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) else trailing()
        }
    }
}

@Composable
fun ExercisePicker(exercises: List<ExerciseUi>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    val matches = exercises.filter { ExerciseSearch.matches(it.id, it.name, it.muscle, it.equipment, search) && (muscle.isBlank() || it.muscle == muscle) && (equipment.isBlank() || it.equipment == equipment) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 700.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Choose exercise", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close exercise picker") } }
                SearchField(search, { search = it }, "Find a movement")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceFilter("Muscle", muscle, exercises.map { it.muscle }.distinct().sorted(), { muscle = it }, Modifier.weight(1f))
                    ChoiceFilter("Equipment", equipment, exercises.map { it.equipment }.distinct().sorted(), { equipment = it }, Modifier.weight(1f))
                }
                Text("${matches.size} exercises", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matches.isEmpty()) item { Text("No matching exercises. Try a broader search.", Modifier.padding(20.dp)) }
                    items(matches, key = { it.id }) { exercise ->
                        ListItem(headlineContent = { Text(exercise.name, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("${human(exercise.muscle)} · ${human(exercise.equipment)}") }, trailingContent = { Icon(Icons.Outlined.AddCircleOutline, null) }, modifier = Modifier.clickable { onSelect(exercise.id); onDismiss() })
                    }
                }
            }
        }
    }
}

@Composable
fun CustomExerciseDialog(exercise: ExerciseUi?, exercises: List<ExerciseUi>, actions: MoosclesActions, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(exercise?.name ?: "") }
    val muscles = exercises.map { it.muscle }.distinct().sorted()
    val equipmentOptions = exercises.map { it.equipment }.distinct().sorted()
    val conventions = exercises.map { it.convention }.distinct().sorted()
    var muscle by remember { mutableStateOf(exercise?.muscle ?: muscles.firstOrNull().orEmpty()) }
    var equipment by remember { mutableStateOf(exercise?.equipment ?: equipmentOptions.firstOrNull().orEmpty()) }
    var convention by remember { mutableStateOf(exercise?.convention ?: conventions.firstOrNull().orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (exercise == null) "Your own movement" else "Edit exercise") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Exercise name") }, singleLine = true, isError = name.length > 100, modifier = Modifier.fillMaxWidth())
            ChoiceFilter("Muscle", muscle, muscles, { muscle = it }, allowAll = false)
            ChoiceFilter("Equipment", equipment, equipmentOptions, { equipment = it }, allowAll = false)
            Text("Logging convention", style = MaterialTheme.typography.titleSmall)
            ChoiceFilter("Convention", convention, conventions, { convention = it }, allowAll = false)
            Text("Use the load and reps exactly as logged. Dumbbells use load per hand; unilateral sets use reps per side. No silent doubling. Renaming preserves this exercise’s identity and history.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { actions.saveCustomExercise(exercise?.id, name.trim(), muscle, equipment, convention); onDismiss() }, enabled = name.trim().isNotEmpty() && name.length <= 100 && muscle.isNotEmpty() && equipment.isNotEmpty() && convention.isNotEmpty()) { Text("Save exercise") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
