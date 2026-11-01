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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

/**
 * The editing state of a rest duration typed as m:ss, shared by [RestDurationField] and
 * [SettingsRestDurationField]. Typing goes through `RestDuration.edit`; a rejected keystroke
 * keeps the text and sets [rejected]. A new duration is reported once through
 * `onSecondsChange`, and an outside change of `seconds` replaces the text while unfocused.
 */
@Stable
class RestDurationEditorState internal constructor(
    private val text: MutableState<TextFieldValue>,
    private val lastEmitted: MutableIntState,
    private val focused: MutableState<Boolean>,
    private val rejectedState: MutableState<Boolean>,
    private val emit: (Int) -> Unit,
) {
    val value: TextFieldValue get() = text.value
    val rejected: Boolean get() = rejectedState.value

    fun onValueChange(next: TextFieldValue) {
        val current = text.value
        val previous = RestDuration.EditorValue(current.text, current.selection.start, current.selection.end)
        val edited = RestDuration.edit(previous,
            RestDuration.EditorValue(next.text, next.selection.start, next.selection.end))
        rejectedState.value = next.text != current.text && edited == previous
        text.value = TextFieldValue(edited.text, TextRange(edited.selectionStart, edited.selectionEnd))
        RestDuration.parse(edited.text)?.let { duration ->
            if (duration != lastEmitted.intValue) { lastEmitted.intValue = duration; emit(duration) }
        }
    }

    /** Focusing selects the minutes digits, ready to be typed over. */
    fun onFocusChange(isFocused: Boolean) {
        if (isFocused && !focused.value) text.value = text.value.copy(selection = TextRange(0, 4))
        focused.value = isFocused
    }

    internal fun sync(seconds: Int) {
        if (!focused.value && RestDuration.parse(text.value.text) != seconds.coerceIn(0, RestDuration.MAX_SECONDS)) {
            text.value = TextFieldValue(RestDuration.editor(seconds).text, TextRange(0, 4))
            lastEmitted.intValue = seconds.coerceIn(0, RestDuration.MAX_SECONDS)
        }
    }
}

@Composable
fun rememberRestDurationEditor(seconds: Int, onSecondsChange: (Int) -> Unit, key: String): RestDurationEditorState {
    val initial = RestDuration.editor(seconds)
    val text = rememberSaveable(key, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial.text, TextRange(0, 4)))
    }
    val lastEmitted = rememberSaveable(key) { mutableIntStateOf(seconds.coerceIn(0, RestDuration.MAX_SECONDS)) }
    val focused = remember(key) { mutableStateOf(false) }
    val rejected = remember(key) { mutableStateOf(false) }
    val currentOnSecondsChange by rememberUpdatedState(onSecondsChange)
    val state = remember(text, lastEmitted, focused, rejected) {
        RestDurationEditorState(text, lastEmitted, focused, rejected) { currentOnSecondsChange(it) }
    }
    LaunchedEffect(seconds) { state.sync(seconds) }
    return state
}

@Composable
fun RestDurationField(
    seconds: Int, onSecondsChange: (Int) -> Unit, modifier: Modifier = Modifier,
    label: String = "Rest duration", testTag: String = "rest-duration",
    help: String = "Minutes, then seconds · maximum 5:00",
) {
    val editor = rememberRestDurationEditor(seconds, onSecondsChange, testTag)
    TextField(editor.value, editor::onValueChange, label = { Text(label) }, suffix = { Text("min") }, singleLine = true,
        modifier = modifier.testTag(testTag).onFocusChanged { editor.onFocusChange(it.isFocused) },
        shape = RoundedCornerShape(14.dp), isError = editor.rejected,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text(if (editor.rejected) "Use 0:00 to 5:00, with seconds from 00 to 59." else help) },
        colors = trainingFieldColors())
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
