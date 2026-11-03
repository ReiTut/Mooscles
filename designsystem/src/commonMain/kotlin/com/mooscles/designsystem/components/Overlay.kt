package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.graphics.graphicsLayer
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_dismiss
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** Calls [onClose] on Escape or a hardware Back key (the Android back button also reaches it). */
internal fun Modifier.overlayKeys(onClose: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)) {
        onClose()
        true
    } else {
        false
    }
}

/**
 * The dim layer behind a dialog or sheet; [visible] (0..1) scales the token color's alpha. A tap
 * closes. Screen readers read it as "Dismiss", after the content.
 */
@Composable
internal fun OverlayScrim(tag: String, visible: Float, onClick: () -> Unit) {
    val dismiss = stringResource(Res.string.ds_dismiss)
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .testTag(tag)
            .fillMaxSize()
            .graphicsLayer { alpha = visible }
            .background(MTheme.colors.scrim)
            .focusProperties { canFocus = false }
            // Named, as Material's scrim ("Close sheet"): a screen reader never lands on a nameless
            // control. Read after the content (traversal index 1), which comes first in the layout.
            .semantics {
                contentDescription = dismiss
                traversalIndex = 1f
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

/**
 * Elevation 3 for a sheet or dialog: a 12 dp shadow in light; in dark the surface tone does
 * the work, so there is none.
 */
@Composable
internal fun Modifier.overlayElevation(shape: Shape): Modifier =
    if (MTheme.isDark) this else shadow(MTheme.elevation.e3.shadowLight, shape)
