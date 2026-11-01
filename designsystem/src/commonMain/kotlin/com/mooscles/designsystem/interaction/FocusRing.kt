package com.mooscles.designsystem.interaction

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.theme.MTheme

/** Whether the focus ring is drawn; exposed so tests can read it. */
val FocusRingVisible = SemanticsPropertyKey<Boolean>("FocusRingVisible")
private var SemanticsPropertyReceiver.focusRingVisible by FocusRingVisible

/**
 * Draws the `focus.ring` outline (`focusRingWidth`, kept `focusRingOffset` outside the control)
 * while [interactionSource] reports focus, or always with [alwaysVisible] (a control shown as
 * selected without focus). The control itself must be focusable with the same source.
 */
fun Modifier.mFocusRing(shape: Shape, interactionSource: MutableInteractionSource, alwaysVisible: Boolean = false): Modifier = composed {
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val focused = hasFocus || alwaysVisible
    val color = MTheme.colors.focusRing
    val width = MTheme.sizes.focusRingWidth
    val offset = MTheme.sizes.focusRingOffset
    semantics { focusRingVisible = focused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val strokePx = width.toPx()
                // The stroke is centered on its path: push the path out by offset plus half the stroke.
                val grow = offset.toPx() + strokePx / 2f
                val outline = shape.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
                translate(left = -grow, top = -grow) {
                    drawOutline(outline, color, style = Stroke(width = strokePx))
                }
            }
        }
}
