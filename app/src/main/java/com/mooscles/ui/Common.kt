package com.mooscles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.domain.RestDuration

/**
 * Saveable fixed-colon editor state used by [SettingsRestDurationField]. A rejected keystroke
 * keeps the last accepted value. Changes are reported once; an outside update replaces unfocused text.
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
