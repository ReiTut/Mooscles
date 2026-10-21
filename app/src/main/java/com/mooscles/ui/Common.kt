package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.mooscles.domain.RestDuration

fun kgToDisplay(kg: Double, pounds: Boolean) = if (pounds) kg * 2.2046226218487757 else kg
fun displayToKg(weight: Double, pounds: Boolean) = if (pounds) weight / 2.2046226218487757 else weight
fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
fun weight(kg: Double, pounds: Boolean) = "${number(kgToDisplay(kg, pounds))} ${if (pounds) "lb" else "kg"}"
fun dateText(time: Long, pattern: String = "EEE, MMM d") = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
fun durationText(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3600) "%d:%02d:%02d".format(safe / 3600, safe / 60 % 60, safe % 60) else "%d:%02d".format(safe / 60, safe % 60)
}
fun human(text: String) = text.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

/** A consistently sized, filled entry field shared by routine targets and logged sets. */
@Composable
fun TrainingNumberField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    isError: Boolean = false, help: String? = null, decimal: Boolean = false,
) {
    TextField(value, onValueChange, label = { Text(label) }, singleLine = true, isError = isError,
        modifier = modifier, shape = RoundedCornerShape(14.dp),
        supportingText = help?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = trainingFieldColors())
}

@Composable
private fun trainingFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
fun RestDurationField(
    seconds: Int, onSecondsChange: (Int) -> Unit, modifier: Modifier = Modifier,
    label: String = "Rest duration", testTag: String = "rest-duration",
    help: String = "Minutes:seconds · e.g. 2:00",
) {
    var text by rememberSaveable(testTag) { mutableStateOf(RestDuration.format(seconds)) }
    var lastEmitted by rememberSaveable(testTag) { mutableIntStateOf(seconds) }
    var focused by remember(testTag) { mutableStateOf(false) }
    LaunchedEffect(seconds) {
        if (!focused && RestDuration.parse(text) != seconds) {
            text = RestDuration.format(seconds)
            lastEmitted = seconds
        }
    }
    val parsed = RestDuration.parse(text)
    TextField(text, { next ->
        text = next
        RestDuration.parse(next)?.let { duration ->
            if (duration != lastEmitted) { lastEmitted = duration; onSecondsChange(duration) }
        }
    }, label = { Text(label) }, suffix = { Text("min") }, singleLine = true,
        modifier = modifier.testTag(testTag).onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) RestDuration.parse(text)?.let { duration -> text = RestDuration.format(duration) }
        }, shape = RoundedCornerShape(14.dp), isError = parsed == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        supportingText = { Text(if (parsed == null) "Enter minutes:seconds, from 0:00 to 60:00." else help) },
        colors = trainingFieldColors())
}

private fun restClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

@Composable
fun PageHeading(eyebrow: String, title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 38.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(20.dp).size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { Button(onClick = onAction) { Text(it) } }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun NameDialog(title: String, initial: String = "", label: String = "Name", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("1–80 characters") }, isError = value.length > 80)
    }, confirmButton = { TextButton(onClick = { onSave(value.trim()); onDismiss() }, enabled = value.trim().isNotEmpty() && value.length <= 80) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun OrderButtons(upEnabled: Boolean, downEnabled: Boolean, onMove: (Int) -> Unit) {
    Row {
        IconButton(onClick = { onMove(-1) }, enabled = upEnabled) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
        IconButton(onClick = { onMove(1) }, enabled = downEnabled) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
    }
}

@Composable
fun TimerPanel(timer: TimerUi, actions: MoosclesActions, defaultSeconds: Int, compact: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (timer.finished) "Rest complete" else timer.exerciseName, style = MaterialTheme.typography.titleSmall)
                    Text(if (timer.running) "Breathe. Your next set is coming." else if (timer.paused) "Timer paused" else "Ready when you are", style = MaterialTheme.typography.labelSmall)
                }
                Text("${restClock(timer.remainingSeconds)} min", style = MaterialTheme.typography.headlineSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { actions.adjustTimer(-15) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("−0:15") }
                if (timer.running) FilledTonalButton(onClick = actions::pauseTimer, modifier = Modifier.weight(2f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Outlined.Pause, null, Modifier.size(18.dp)); Text("Pause") }
                else if (timer.paused) FilledTonalButton(onClick = actions::resumeTimer, modifier = Modifier.weight(2f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Text("Resume") }
                else FilledTonalButton(onClick = { actions.startTimer(timer.exerciseName, if (timer.exerciseName == "Rest timer") defaultSeconds else timer.durationSeconds) }, modifier = Modifier.weight(2f), contentPadding = PaddingValues(6.dp)) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Text("Start") }
                TextButton(onClick = { actions.adjustTimer(15) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+0:15") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = actions::resetTimer) { Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset") }
                TextButton(onClick = actions::skipTimer) { Text("Skip rest"); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.SkipNext, null, Modifier.size(18.dp)) }
            }
        }
    }
}
