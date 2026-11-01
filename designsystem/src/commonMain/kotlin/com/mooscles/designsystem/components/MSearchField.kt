package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_search_clear
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the search field's box (border and background), which grows with the text. */
const val SearchFieldBoxTag = "search-field-box"

/** The tag of the drawn placeholder, present only while the field is empty. */
const val SearchFieldPlaceholderTag = "search-field-placeholder"

/**
 * A one-line search field (plan decision 12): the `Search` icon at the start, [placeholder] in
 * `text.secondary` while it is empty, and, only while it has text, a 48 dp clear button
 * ("Clear search") at the end, 8 dp after the field, which calls [onClear] and keeps the focus in the field. The
 * keyboard shows Search, which only closes it: the caller filters as the text changes.
 *
 * It has no label above it, unlike [MTextField]: [placeholder] is its accessible name, drawn while
 * empty and kept as the name (not drawn) once it has text. The box is at least the 48 dp target
 * tall and grows with the text (a long placeholder at 200% wraps instead of being cut).
 * [focusRequester] lets the caller move the focus into the field.
 */
@Composable
fun MSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(MTheme.radius.md)
    val empty = value.text.isEmpty()
    val textStyle = MTheme.type.bodyLg
    val clearLabel = stringResource(Res.string.ds_search_clear)

    // The clear button sits beside the text field, not inside its decoration box, so its taps never
    // reach the field; the box around both draws the border and the focus ring of the field.
    Row(
        modifier
            .testTag(SearchFieldBoxTag)
            .heightIn(min = MTheme.targets.min)
            .mFocusRing(shape, source)
            .background(colors.bgSurface, shape)
            .border(MTheme.sizes.border, colors.borderStrong, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors(colors, focused)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(requester)
                    // Once typed, the drawn placeholder is gone; it stays the field's name.
                    .semantics { if (!empty) text = AnnotatedString(placeholder) },
                textStyle = textStyle.copy(color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                singleLine = true,
                interactionSource = source,
                cursorBrush = SolidColor(colors.accentText),
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MTheme.targets.min)
                            .padding(start = MTheme.space.s3, end = if (empty) MTheme.space.s4 else MTheme.space.s1),
                        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MIcon(MIcons.Search, contentDescription = null, size = MTheme.sizes.icon20, tint = colors.textSecondary)
                        Box(Modifier.weight(1f).padding(vertical = MTheme.space.s2), contentAlignment = Alignment.CenterStart) {
                            if (empty) {
                                MText(placeholder, textStyle, color = colors.textSecondary, modifier = Modifier.testTag(SearchFieldPlaceholderTag))
                            }
                            inner()
                        }
                    }
                },
            )
        }
        if (!empty) {
            // 8 dp between the field's touch area and the button's (two targets side by side).
            Spacer(Modifier.width(MTheme.space.s2))
            MIconButton(
                MIcons.X,
                contentDescription = clearLabel,
                onClick = {
                    onClear()
                    requester.requestFocus()
                },
            )
        }
    }
}
