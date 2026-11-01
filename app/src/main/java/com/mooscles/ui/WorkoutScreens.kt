package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mooscles.domain.RestDuration

/**
 * The old exercise card, kept for the saved workout's detail (`WorkoutDetailScreen`): the active
 * workout is the `:screens` one since phase 3.
 */
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
    if (remove) ConfirmDialog("Remove ${exercise.name}?", "This removes this exercise and all of its sets from the workout.", "Remove",
        onDismiss = { remove = false }, onConfirm = { actions.removeWorkoutExercise(entryId) })
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

@Composable
private fun SetEditor(set: SetUi, index: Int, pounds: Boolean, assistanceConvention: Boolean, actions: MoosclesActions) {
    // Lazy rows can leave composition while a write is still in flight. Save
    // local drafts by the real set identity, including blank and invalid input.
    var reps by rememberSaveable(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    val originalLoad = remember(set.id, pounds) { if (set.weightKg == 0.0) "" else number(kgToDisplay(set.weightKg, pounds)) }
    var referenceLoad by rememberSaveable(set.id) { mutableStateOf(originalLoad) }
    var referenceWeightKg by rememberSaveable(set.id) { mutableDoubleStateOf(set.weightKg) }
    var load by rememberSaveable(set.id) { mutableStateOf(originalLoad) }
    var draftPounds by rememberSaveable(set.id) { mutableStateOf(pounds) }
    var completed by rememberSaveable(set.id) { mutableStateOf(set.completed) }
    var warmup by rememberSaveable(set.id) { mutableStateOf(set.warmup) }
    var assistance by rememberSaveable(set.id) { mutableStateOf(set.assistance) }
    var pendingSignature by rememberSaveable(set.id) { mutableStateOf<String?>(null) }
    var pendingWeightKg by rememberSaveable(set.id) { mutableStateOf<Double?>(null) }
    var pendingReps by rememberSaveable(set.id) { mutableStateOf<Int?>(null) }
    var acknowledgedReps by rememberSaveable(set.id) { mutableIntStateOf(set.reps) }
    var acknowledgedWeightKg by rememberSaveable(set.id) { mutableDoubleStateOf(set.weightKg) }
    var submittedRepsText by rememberSaveable(set.id) { mutableStateOf(reps) }
    var submittedLoadText by rememberSaveable(set.id) { mutableStateOf(load) }
    LaunchedEffect(set) {
        if (pendingSignature == null || pendingSignature == setEditSignature(set.reps, set.weightKg, set.completed, set.warmup, set.assistance)) {
            // A matching acknowledgment retains the user's text (e.g. comma
            // decimals). A subsequent external edit refreshes an untouched field,
            // but must not erase a newer invalid/unsubmitted local draft.
            if (pendingSignature == null) {
                if (set.reps != acknowledgedReps && reps == submittedRepsText) {
                    reps = if (set.reps > 0) set.reps.toString() else ""
                    submittedRepsText = reps
                }
                if (set.weightKg != acknowledgedWeightKg && load == submittedLoadText) {
                    referenceWeightKg = set.weightKg
                    referenceLoad = if (set.weightKg == 0.0) "" else number(kgToDisplay(set.weightKg, pounds))
                    load = referenceLoad
                    submittedLoadText = load
                }
            }
            acknowledgedReps = set.reps
            acknowledgedWeightKg = set.weightKg
            completed = set.completed
            warmup = set.warmup
            assistance = set.assistance
            pendingSignature = null
            pendingWeightKg = null
            pendingReps = null
        }
    }
    // A disposed row can restore a bundle written in the former display unit.
    // Convert from exact kg before interpreting or saving its restored text.
    LaunchedEffect(pounds) {
        if (draftPounds != pounds) {
            val kg = pendingWeightKg ?: set.weightKg
            referenceWeightKg = kg
            referenceLoad = if (kg == 0.0) "" else number(kgToDisplay(kg, pounds))
            load = referenceLoad
            submittedLoadText = load
            draftPounds = pounds
        }
    }
    var touched by rememberSaveable(set.id) { mutableStateOf(false) }
    var flags by remember { mutableStateOf(false) }
    fun parsedReps() = if (reps.isBlank()) 0 else reps.toIntOrNull()
    fun parsedLoad() = if (load.isBlank()) 0.0 else load.replace(',', '.').toDoubleOrNull()
    fun exactKg(text: String, parsed: Double) = if (text == referenceLoad) referenceWeightKg else displayToKg(parsed, pounds)
    val repsValue = parsedReps()
    val loadValue = parsedLoad()
    val validReps = repsValue != null && repsValue in 1..9999
    val showRepsError = !validReps && (touched || reps.isNotBlank() || completed)
    val validLoad = loadValue != null && loadValue.isFinite() && exactKg(load, loadValue) in 0.0..100000.0
    fun submit(nextReps: Int, kg: Double, nextCompleted: Boolean, nextWarmup: Boolean, nextAssistance: Boolean) {
        completed = nextCompleted
        warmup = nextWarmup
        assistance = nextAssistance || assistanceConvention
        pendingSignature = setEditSignature(nextReps, kg, completed, warmup, assistance)
        pendingWeightKg = kg
        pendingReps = nextReps
        if (parsedReps() == nextReps) submittedRepsText = reps
        parsedLoad()?.takeIf { it.isFinite() && exactKg(load, it) == kg }?.let { submittedLoadText = load }
        actions.updateSet(set.id, nextReps, kg, completed, warmup, assistance)
    }
    fun save(nextCompleted: Boolean = completed, nextWarmup: Boolean = warmup, nextAssistance: Boolean = assistance) {
        // Read the current drafts inside the callback, rather than the values
        // captured by the preceding composition while another edit is pending.
        val currentReps = parsedReps()
        val currentLoad = parsedLoad()
        val loadIsValid = currentLoad != null && currentLoad.isFinite() && exactKg(load, currentLoad) in 0.0..100000.0
        if (nextCompleted && (currentReps == null || currentReps !in 1..9999 || !loadIsValid)) return
        submit(currentReps?.takeIf { it in 0..9999 } ?: pendingReps ?: set.reps,
            if (loadIsValid) exactKg(load, currentLoad!!) else pendingWeightKg ?: set.weightKg, nextCompleted, nextWarmup, nextAssistance)
    }
    Column(Modifier.testTag("workout-set-${set.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) { Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge) }
            TrainingNumberField(load, { next ->
                load = next
                val parsed = if (next.isBlank()) 0.0 else next.replace(',', '.').toDoubleOrNull()
                val currentReps = parsedReps()
                if (parsed != null && parsed.isFinite() && exactKg(next, parsed) in 0.0..100000.0 && (!completed || currentReps != null && currentReps in 1..9999))
                    submit(currentReps?.takeIf { it in 0..9999 } ?: pendingReps ?: set.reps, exactKg(next, parsed), completed, warmup, assistance)
            }, label = if (pounds) "lb" else "kg", isError = !validLoad, decimal = true, modifier = Modifier.weight(1f).testTag("set-load-$index"))
            TrainingNumberField(reps, { next ->
                reps = next
                val parsed = if (next.isBlank()) 0 else next.toIntOrNull()
                val currentLoad = parsedLoad()
                if (parsed != null && parsed in 0..9999 && currentLoad != null && currentLoad.isFinite() && exactKg(load, currentLoad) in 0.0..100000.0 && (!completed || parsed > 0))
                    submit(parsed, exactKg(load, currentLoad), completed, warmup, assistance)
            }, label = "Reps", isError = showRepsError, modifier = Modifier.weight(1f).testTag("set-reps-$index"))
            IconToggleButton(checked = completed, onCheckedChange = { touched = true; save(nextCompleted = it) }, modifier = Modifier.testTag("set-complete-$index"), colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary, checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (completed) "Undo set completion" else "Complete set") }
        }
        if (!validLoad) Text("Enter a valid weight of 0 or more.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (validLoad && loadValue == 0.0 && !assistance && !assistanceConvention) Text("Bodyweight · no added load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showRepsError) Text("Enter reps between 1 and 9999 to complete this set.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { flags = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(if (warmup) "Warm-up" else if (assistance || assistanceConvention) "Assistance" else "Working set", style = MaterialTheme.typography.labelSmall); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
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
