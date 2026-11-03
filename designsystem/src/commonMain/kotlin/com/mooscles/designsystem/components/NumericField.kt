package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_error_prefix
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the visible box of a [NumericField]. */
const val NumericFieldBoxTag = "numeric-field-box"

/**
 * A number entered during a workout: 72 x 48 dp (at least; it grows with the number and the
 * text size, never clipping it), `num.md`, a decimal or number keyboard by [kind]. The text area
 * is exactly as wide as the value (or [suggested]) plus the cursor, within the 72 dp: a bare
 * `BasicTextField` is at least ten "H" wide, which would stretch the box to ~150 dp. The target is
 * 56 dp tall. While empty it shows [suggested] in `text.secondary`. The label is
 * [accessibilityLabel] on the field's own semantics (there is no visible label); an [error] adds
 * a 2 dp danger border, the alert icon and the message, and is also reported as `error`.
 * Text that cannot lead to a number of [kind] is not passed to [onValueChange].
 *
 * [selected] draws the focus ring without the field having focus: the field a row is editing with
 * its stepper, while the keyboard stays closed. The keyboard shows [imeAction]; pressing it calls
 * [onImeAction], and with [ImeAction.Done] the keyboard also closes (the system default). Other
 * actions do only [onImeAction], so with [ImeAction.Next] the caller moves the focus where it
 * wants. [focusRequester] lets the caller focus the field (and so open the keyboard).
 */
@Composable
fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    kind: NumericKind,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    suggested: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MTheme.radius.md)
    val hasError = error != null
    val errorPrefix = stringResource(Res.string.ds_error_prefix)
    val textColor = if (enabled) colors.textPrimary else colors.textDisabled
    val textStyle = MTheme.type.numMd.copy(color = textColor, textAlign = TextAlign.Center)
    val padding = MTheme.space.s2
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val textWidth = with(density) {
        fun width(text: String) =
            measurer.measure(text, textStyle, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width.let { kotlin.math.ceil(it.toDouble()).toInt() + 1 }.toDp()
        val shown = width(value)
        maxOf(shown, suggested?.let(::width) ?: shown) + MTheme.sizes.textCursor
    }
    val areaWidth = maxOf(MTheme.sizes.numericFieldWidth - padding * 2, textWidth)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = { if (NumericInput.isTypable(it, kind)) onValueChange(it) },
            modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).semantics {
                contentDescription = accessibilityLabel
                if (error != null) error("$errorPrefix: $error")
            },
            enabled = enabled,
            singleLine = true,
            maxLines = 1,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (kind == NumericKind.Load) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = {
                onImeAction()
                if (imeAction == ImeAction.Done) defaultKeyboardAction(ImeAction.Done)
            }),
            interactionSource = source,
            cursorBrush = SolidColor(colors.accentText),
            decorationBox = { inner ->
                // The outer box is the 56 dp target; the drawn box inside it is 48 dp tall.
                Box(
                    Modifier.sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.targets.workout),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .testTag(NumericFieldBoxTag)
                            .sizeIn(minWidth = MTheme.sizes.numericFieldWidth, minHeight = MTheme.sizes.numericFieldHeight)
                            .mFocusRing(shape, source, alwaysVisible = selected)
                            .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
                            .border(
                                if (hasError) MTheme.sizes.borderError else MTheme.sizes.border,
                                if (hasError) colors.dangerText else colors.borderStrong,
                                shape,
                            )
                            .padding(horizontal = padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isEmpty() && suggested != null) {
                            MText(suggested, MTheme.type.numMd, color = colors.textSecondary, softWrap = false, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                        }
                        // A fixed width overrides the text field's own minimum (ten "H"); it is never narrower than the text.
                        Box(Modifier.width(areaWidth), contentAlignment = Alignment.Center) { inner() }
                    }
                }
            },
        )
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1), verticalAlignment = Alignment.Top) {
                MIcon(MIcons.CircleAlert, contentDescription = null, size = MTheme.sizes.icon16, tint = colors.dangerText)
                MText(error, MTheme.type.caption, color = colors.dangerText)
            }
        }
    }
}
