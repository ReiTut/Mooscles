package com.mooscles.ui

import androidx.compose.ui.res.stringResource
import com.mooscles.R
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.muscleLabel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.legacy_clear_search)) } }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
}

@Composable
fun ChoiceFilter(label: String, selected: String, choices: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, allowAll: Boolean = true, choiceLabel: @Composable (String) -> String = { human(it) }) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            Text(if (selected.isBlank()) label else choiceLabel(selected), maxLines = 1, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            if (allowAll) DropdownMenuItem(text = { Text(stringResource(R.string.legacy_all_filter, label.lowercase())) }, onClick = { onSelect(""); expanded = false })
            choices.forEach { option -> DropdownMenuItem(text = { Text(choiceLabel(option)) }, onClick = { onSelect(option); expanded = false }) }
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
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.legacy_choose_exercise), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.legacy_close_picker)) } }
                SearchField(search, { search = it }, stringResource(R.string.legacy_find_movement))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceFilter(stringResource(R.string.legacy_muscle), muscle, exercises.map { it.muscle }.distinct().sorted(), { muscle = it }, Modifier.weight(1f), choiceLabel = { muscleLabel(it) })
                    ChoiceFilter(stringResource(R.string.legacy_equipment), equipment, exercises.map { it.equipment }.distinct().sorted(), { equipment = it }, Modifier.weight(1f), choiceLabel = { equipmentLabel(it) })
                }
                Text(stringResource(R.string.legacy_exercise_count, matches.size), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matches.isEmpty()) item { Text(stringResource(R.string.legacy_no_matching_exercises), Modifier.padding(20.dp)) }
                    items(matches, key = { it.id }) { exercise ->
                        ListItem(headlineContent = { Text(exerciseName(exercise), fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("${muscleLabel(exercise.muscle)} · ${equipmentLabel(exercise.equipment)}") }, trailingContent = { Icon(Icons.Outlined.AddCircleOutline, null) }, modifier = Modifier.clickable { onSelect(exercise.id); onDismiss() })
                    }
                }
            }
        }
    }
}
