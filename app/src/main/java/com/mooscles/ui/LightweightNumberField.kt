package com.mooscles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Numeric entry without the general-purpose floating-label decoration layout. */
@Composable
internal fun LightweightNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    help: String? = null,
    decimal: Boolean = false,
    cornerRadius: Dp = 14.dp,
    textAlign: TextAlign = TextAlign.Start,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val accent = if (isError) colors.error else colors.primary
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            label?.let { contentDescription = it }
            if (isError) error("Invalid input")
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = textAlign),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        interactionSource = interactions,
        cursorBrush = SolidColor(accent),
        decorationBox = { innerTextField ->
            Column {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(cornerRadius))
                        .then(if (isError) Modifier.border(1.dp, colors.error, RoundedCornerShape(cornerRadius)) else Modifier)
                        .padding(horizontal = 16.dp, vertical = if (label == null) 16.dp else 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (label != null) Text(label, style = MaterialTheme.typography.bodySmall,
                        color = if (isError || focused) accent else colors.onSurfaceVariant)
                    // Give the editor the full available width so centered text,
                    // cursor placement and selection use the same hit area.
                    Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { innerTextField() }
                }
                if (help != null) Text(help, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) colors.error else colors.onSurfaceVariant)
            }
        },
    )
}
