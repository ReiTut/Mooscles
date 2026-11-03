package com.mooscles.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_loading
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the drawn box of a button (background and outline), inside its touch target. */
const val ButtonContainerTag = "button-container"

/** The tag of a button's leading icon, present only when [MButton] has one. */
const val ButtonIconTag = "button-icon"

enum class MButtonVariant { Primary, Secondary, Tonal, Text, Destructive, Danger }

enum class MButtonSize { L, M, S }

/**
 * A button. Heights are L 56, M 48 and S 40 (the S target stays 48 tall); a long label wraps
 * instead of being cut. While [loading] a spinner replaces the label, the width does not
 * change and the button does not click.
 *
 * Usage rule: only one [MButtonVariant.Primary] (`accent.solid`) button per screen. The
 * compiler does not enforce it. [MButtonVariant.Danger] is for confirmation dialogs only.
 */
@Composable
fun MButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MButtonVariant = MButtonVariant.Primary,
    size: MButtonSize = MButtonSize.M,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val clickable = enabled && !loading
    val shape = RoundedCornerShape(MTheme.radius.md)
    val drawnHeight = when (size) {
        MButtonSize.L -> MTheme.sizes.buttonL
        MButtonSize.M -> MTheme.sizes.buttonM
        MButtonSize.S -> MTheme.sizes.buttonS
    }
    val textStyle = if (size == MButtonSize.S) MTheme.type.labelMd else MTheme.type.labelLg

    val container: Color
    val content: Color
    val outline: Color
    when {
        !enabled -> { container = colors.bgInset; content = colors.textDisabled; outline = Color.Transparent }
        else -> when (variant) {
            MButtonVariant.Primary -> {
                container = if (pressed) colors.accentSolidPressed else colors.accentSolid
                content = colors.accentOn; outline = Color.Transparent
            }
            MButtonVariant.Secondary -> { container = Color.Transparent; content = colors.textPrimary; outline = colors.borderStrong }
            MButtonVariant.Tonal -> { container = colors.accentSubtle; content = colors.accentText; outline = Color.Transparent }
            MButtonVariant.Text -> { container = Color.Transparent; content = colors.accentText; outline = Color.Transparent }
            MButtonVariant.Destructive -> { container = Color.Transparent; content = colors.dangerText; outline = colors.dangerText }
            MButtonVariant.Danger -> { container = colors.dangerSolid; content = colors.dangerOn; outline = Color.Transparent }
        }
    }
    val loadingLabel = stringResource(Res.string.ds_loading)

    Box(
        modifier = modifier
            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min)
            .mPressScale(source, clickable)
            .semantics { if (loading) contentDescription = loadingLabel }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = clickable,
                role = Role.Button,
                onClick = onClick,
            )
            // The drawn box gets the button's minimum width (all of it with fillMaxWidth), but not
            // its minimum height: an S button draws 40 dp, centered in its 48 dp target.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val height = maxOf(placeable.height, constraints.minHeight)
                layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
            },
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .testTag(ButtonContainerTag)
                .heightIn(min = drawnHeight)
                .mFocusRing(shape, source)
                .background(container, shape)
                .then(if (outline != Color.Transparent) Modifier.border(MTheme.sizes.border, outline, shape) else Modifier)
                .padding(horizontal = MTheme.space.s4),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .alpha(if (loading) 0f else 1f)
                    .then(if (loading) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(vertical = MTheme.space.s2),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The icon grows with the text (font scale), so a large label is not paired with a small icon.
                if (icon != null) {
                    MIcon(
                        icon,
                        contentDescription = null,
                        size = MTheme.sizes.icon20 * LocalDensity.current.fontScale,
                        tint = content,
                        modifier = Modifier.testTag(ButtonIconTag),
                    )
                }
                MText(
                    text,
                    style = textStyle.copy(textAlign = TextAlign.Center),
                    color = content,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (loading) MSpinner(content)
        }
    }
}

/** An indeterminate ring. Still when the system reduces motion. */
@Composable
internal fun MSpinner(color: Color, modifier: Modifier = Modifier) {
    val reduced = MTheme.reducedMotion
    val stroke = MTheme.sizes.focusRingWidth
    val angle = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(MTheme.timing.skeletonPulse.toInt(), easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "spinnerAngle",
        )
    }
    Box(
        modifier
            .size(MTheme.sizes.icon20)
            .drawBehind {
                val strokePx = stroke.toPx()
                drawArc(
                    color = color,
                    startAngle = angle?.value ?: 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            },
    )
}
