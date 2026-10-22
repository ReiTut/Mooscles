package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mooscles.domain.RestDuration

@Composable
internal fun RoutineExerciseHeading(
    planned: PlannedExerciseUi, pounds: Boolean, onRemove: () -> Unit,
    onReplace: () -> Unit, onRestChange: (Int) -> Unit,
) {
    var menu by remember(planned.id) { mutableStateOf(false) }
    var restEditor by remember(planned.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Long movement names wrap, leaving the menu at a reliable touch size.
                Text(planned.exercise.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Timer, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${RestDuration.format(planned.restSeconds.coerceAtLeast(0))} min rest",
                        modifier = Modifier.testTag("routine-${planned.id}-rest-label"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("routine-${planned.id}-menu")) {
                    Icon(Icons.Outlined.MoreVert, "Exercise actions for ${planned.exercise.name}")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit Rest Timer") }, onClick = { menu = false; restEditor = true })
                    DropdownMenuItem(text = { Text("Replace Exercise") }, onClick = { menu = false; onReplace() })
                    DropdownMenuItem(text = { Text("Remove Exercise") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SET", Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (pounds) "LB" else "KG", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("REPS", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(40.dp))
        }
    }
    if (restEditor) RoutineRestDialog(planned, onDismiss = { restEditor = false }, onChange = onRestChange)
}

@Composable
private fun RoutineRestDialog(planned: PlannedExerciseUi, onDismiss: () -> Unit, onChange: (Int) -> Unit) {
    var rest by remember(planned.id) { mutableIntStateOf(planned.restSeconds.coerceIn(0, RestDuration.MAX_SECONDS)) }
    var adjustmentRevision by remember(planned.id) { mutableIntStateOf(0) }
    var observed by remember(planned.id) { mutableIntStateOf(rest) }
    val pending = remember(planned.id) { mutableListOf<Int>() }
    LaunchedEffect(planned.restSeconds) {
        val incoming = planned.restSeconds.coerceIn(0, RestDuration.MAX_SECONDS)
        val acknowledged = pending.indexOf(incoming)
        if (acknowledged >= 0) pending.subList(0, acknowledged + 1).clear()
        else if (incoming != observed) rest = incoming
        observed = incoming
    }
    fun change(seconds: Int) {
        val bounded = seconds.coerceIn(0, RestDuration.MAX_SECONDS)
        if (bounded != rest) { rest = bounded; pending += bounded; onChange(bounded) }
    }
    fun adjust(delta: Int) {
        val adjusted = (rest + delta).coerceIn(0, RestDuration.MAX_SECONDS)
        if (adjusted != rest) {
            change(adjusted)
            // The fixed-colon field deliberately protects a focused edit from
            // persistence echoes. A button is an explicit replacement instead:
            // recreate its mask to show the new duration and clear draft errors.
            adjustmentRevision++
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Rest Timer") }, text = {
        // Dialogs have their own focus owner. Clearing the Activity's owner
        // leaves a focused timer field unable to accept the adjusted duration.
        val focus = LocalFocusManager.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(planned.exercise.name, style = MaterialTheme.typography.bodyMedium)
            key(adjustmentRevision) {
                RestDurationField(rest, ::change, modifier = Modifier.fillMaxWidth(), testTag = "routine-${planned.id}-rest")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { focus.clearFocus(); adjust(-15) }, enabled = rest > 0,
                    modifier = Modifier.weight(1f).testTag("routine-${planned.id}-rest-minus")) { Text("−0:15") }
                OutlinedButton(onClick = { focus.clearFocus(); adjust(15) }, enabled = rest < RestDuration.MAX_SECONDS,
                    modifier = Modifier.weight(1f).testTag("routine-${planned.id}-rest-plus")) { Text("+0:15") }
            }
            Text("Saved automatically for this exercise in this routine.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

private data class RoutineTargetValues(val reps: Int, val weightKg: Double?, val inherited: Boolean)
private val routineTargetValuesSaver = listSaver<RoutineTargetValues, Any>(
    save = { listOf(it.reps, it.weightKg ?: Double.NaN, it.inherited) },
    restore = { RoutineTargetValues(it[0] as Int, (it[1] as Double).takeUnless(Double::isNaN), it[2] as Boolean) },
)
private val routineTargetQueueSaver = listSaver<MutableList<RoutineTargetValues>, Any>(
    save = { queue -> queue.flatMap { listOf(it.reps, it.weightKg ?: Double.NaN, it.inherited) } },
    restore = { values -> values.chunked(3).map { RoutineTargetValues(it[0] as Int,
        (it[1] as Double).takeUnless(Double::isNaN), it[2] as Boolean) }.toMutableList() },
)

@Composable
internal fun RoutineTargetRow(
    target: RoutineSetUi, exercise: ExerciseUi, number: Int, pounds: Boolean, canRemove: Boolean,
    onSave: (Int, Double?) -> Unit, onRemove: () -> Unit, onInherit: () -> Unit,
) {
    // Every row owns its drafts. Persistence acknowledgements must not replace a
    // newer keystroke, while actual PR changes still refresh inherited targets.
    var reps by rememberSaveable(target.id, exercise.id, pounds) { mutableStateOf(target.reps.takeIf { it > 0 }?.toString() ?: "") }
    var exactWeightKg by rememberSaveable(target.id, exercise.id, pounds) { mutableStateOf(target.weightKg) }
    val originalDisplay = exactWeightKg?.let { com.mooscles.ui.number(kgToDisplay(it, pounds)) } ?: ""
    var load by rememberSaveable(target.id, exercise.id, pounds) { mutableStateOf(originalDisplay) }
    var saved by rememberSaveable(target.id, exercise.id, pounds, stateSaver = routineTargetValuesSaver) {
        mutableStateOf(RoutineTargetValues(target.reps, target.weightKg, target.inherited))
    }
    var observed by rememberSaveable(target.id, exercise.id, pounds, stateSaver = routineTargetValuesSaver) { mutableStateOf(saved) }
    val pending = rememberSaveable(target.id, exercise.id, pounds, saver = routineTargetQueueSaver) { mutableListOf<RoutineTargetValues>() }
    var inheritPending by rememberSaveable(target.id, exercise.id, pounds) { mutableStateOf(false) }
    var inheritSuperseded by rememberSaveable(target.id, exercise.id, pounds) { mutableStateOf(false) }
    val incoming = RoutineTargetValues(target.reps, target.weightKg, target.inherited)
    LaunchedEffect(incoming) {
        val acknowledged = pending.indexOf(incoming)
        if (inheritPending && incoming.inherited) {
            if (!inheritSuperseded) {
                reps = incoming.reps.takeIf { it > 0 }?.toString() ?: ""
                exactWeightKg = incoming.weightKg
                load = incoming.weightKg?.let { com.mooscles.ui.number(kgToDisplay(it, pounds)) } ?: ""
                saved = incoming
            }
            inheritPending = false
        } else if (acknowledged >= 0) pending.subList(0, acknowledged + 1).clear()
        else {
            if (incoming.reps != observed.reps) {
                if (incoming.reps != saved.reps) reps = incoming.reps.takeIf { it > 0 }?.toString() ?: ""
                saved = saved.copy(reps = incoming.reps)
            }
            if (incoming.weightKg != observed.weightKg) {
                if (incoming.weightKg != saved.weightKg) {
                    exactWeightKg = incoming.weightKg
                    load = incoming.weightKg?.let { com.mooscles.ui.number(kgToDisplay(it, pounds)) } ?: ""
                }
                saved = saved.copy(weightKg = incoming.weightKg)
            }
            if (incoming.inherited != observed.inherited) saved = saved.copy(inherited = incoming.inherited)
        }
        observed = incoming
    }
    fun parsedLoad(text: String): Double? {
        val normalized = text.replace(',', '.')
        val value = normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        // Editing repetitions must retain exact kg behind rounded lb display.
        return (if (normalized == originalDisplay) exactWeightKg else displayToKg(value, pounds))
            ?.takeIf { it.isFinite() && it in 0.0..100000.0 }
    }
    fun save(values: RoutineTargetValues) {
        val custom = values.copy(inherited = false)
        if (custom == saved) return
        if (inheritPending) inheritSuperseded = true
        saved = custom
        pending += custom
        onSave(custom.reps, custom.weightKg)
    }
    val validLoad = load.isBlank() || parsedLoad(load) != null
    val validReps = reps.isBlank() || reps.toIntOrNull()?.let { it in 1..9999 } == true
    var menu by remember(target.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().testTag("routine-set-${target.id}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number.toString(), Modifier.width(30.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            RoutineTargetField(load, { text ->
                load = text
                if (text.isBlank()) save(saved.copy(weightKg = null))
                else parsedLoad(text)?.let { save(saved.copy(weightKg = it)) }
            }, "${exercise.name}, set $number, ${if (pounds) "pounds" else "kilograms"}", !validLoad,
                Modifier.weight(1f).testTag("routine-set-${target.id}-load"), decimal = true)
            RoutineTargetField(reps, { text ->
                reps = text
                val value = if (text.isBlank()) 0 else text.toIntOrNull()
                value?.takeIf { it == 0 && text.isBlank() || it in 1..9999 }?.let { save(saved.copy(reps = it)) }
            }, "${exercise.name}, set $number, repetitions", !validReps,
                Modifier.weight(1f).testTag("routine-set-${target.id}-reps"))
            Box(Modifier.width(40.dp)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp).testTag("routine-set-${target.id}-menu")) {
                    Icon(Icons.Outlined.MoreVert, "Set $number actions for ${exercise.name}")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (saved.inherited) "Uses PR defaults" else "Custom target") }, enabled = false, onClick = {})
                    DropdownMenuItem(text = { Text("Use PR defaults") }, onClick = {
                        menu = false
                        if (target.inherited) {
                            // An inherited row may have an invalid local draft
                            // without any database change to trigger an echo.
                            reps = target.reps.takeIf { it > 0 }?.toString() ?: ""
                            exactWeightKg = target.weightKg
                            load = target.weightKg?.let { com.mooscles.ui.number(kgToDisplay(it, pounds)) } ?: ""
                            saved = RoutineTargetValues(target.reps, target.weightKg, true)
                        } else {
                            inheritPending = true
                            inheritSuperseded = false
                        }
                        onInherit()
                    })
                    DropdownMenuItem(text = { Text("Remove Set") }, enabled = canRemove, onClick = { menu = false; onRemove() })
                }
            }
        }
        if (!validLoad || !validReps) Text(
            if (!validLoad) "Enter a nonnegative weight, or leave it blank for bodyweight." else "Enter 1–9999 reps, or leave it blank.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 38.dp, end = 48.dp))
    }
}

@Composable
private fun RoutineTargetField(value: String, onChange: (String) -> Unit, description: String, error: Boolean,
    modifier: Modifier = Modifier, decimal: Boolean = false,
) {
    TextField(value, onChange, singleLine = true, isError = error,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ))
}
