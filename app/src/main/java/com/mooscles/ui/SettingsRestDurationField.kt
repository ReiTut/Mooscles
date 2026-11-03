package com.mooscles.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.settings_rest_duration
import com.mooscles.designsystem.resources.settings_rest_error
import com.mooscles.designsystem.resources.settings_rest_help
import com.mooscles.designsystem.resources.settings_rest_suffix
import org.jetbrains.compose.resources.stringResource

/**
 * The rest duration field of Settings, drawn with the design system. It edits exactly like
 * [RestDurationField] (same editor state, same accepted input, same calls), with the texts
 * in resources.
 */
@Composable
fun SettingsRestDurationField(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.settings_rest_duration),
    testTag: String = "rest-duration",
) {
    val editor = rememberRestDurationEditor(seconds, onSecondsChange, testTag)
    MTextField(
        value = editor.value,
        onValueChange = editor::onValueChange,
        label = label,
        modifier = modifier.testTag(testTag).onFocusChanged { editor.onFocusChange(it.isFocused) },
        help = stringResource(Res.string.settings_rest_help),
        error = if (editor.rejected) stringResource(Res.string.settings_rest_error) else null,
        // One line with Done, as the field before it: Enter never types a line break.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        singleLine = true,
        suffix = stringResource(Res.string.settings_rest_suffix),
    )
}
