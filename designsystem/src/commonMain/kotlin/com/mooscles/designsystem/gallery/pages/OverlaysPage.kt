package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_dialog_cancel
import com.mooscles.designsystem.resources.gallery_dialog_confirm
import com.mooscles.designsystem.resources.gallery_dialog_destructive_confirm
import com.mooscles.designsystem.resources.gallery_dialog_destructive_text
import com.mooscles.designsystem.resources.gallery_dialog_destructive_title
import com.mooscles.designsystem.resources.gallery_dialog_text
import com.mooscles.designsystem.resources.gallery_dialog_title
import com.mooscles.designsystem.resources.gallery_open_destructive_dialog
import com.mooscles.designsystem.resources.gallery_open_dialog
import com.mooscles.designsystem.resources.gallery_open_sheet
import com.mooscles.designsystem.resources.gallery_section_overlays
import com.mooscles.designsystem.resources.gallery_sheet_text
import com.mooscles.designsystem.resources.gallery_show_action_toast
import com.mooscles.designsystem.resources.gallery_show_error_toast
import com.mooscles.designsystem.resources.gallery_show_toast
import com.mooscles.designsystem.resources.gallery_toast_action
import com.mooscles.designsystem.resources.gallery_toast_action_message
import com.mooscles.designsystem.resources.gallery_toast_error_message
import com.mooscles.designsystem.resources.gallery_toast_message
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** A dialog, a destructive dialog, a sheet and the three toast kinds, each opened by a button. */
@Composable
fun OverlaysPage() {
    var dialog by remember { mutableStateOf(false) }
    var destructive by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val toasts = remember { MToastHostState() }
    val scope = rememberCoroutineScope()
    val message = stringResource(Res.string.gallery_toast_message)
    val actionMessage = stringResource(Res.string.gallery_toast_action_message)
    val action = stringResource(Res.string.gallery_toast_action)
    val errorMessage = stringResource(Res.string.gallery_toast_error_message)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        ) {
            PageHeader(stringResource(Res.string.gallery_section_overlays))
            val wide = Modifier.fillMaxWidth()
            MButton(stringResource(Res.string.gallery_open_dialog), { dialog = true }, wide, MButtonVariant.Secondary)
            MButton(stringResource(Res.string.gallery_open_destructive_dialog), { destructive = true }, wide, MButtonVariant.Secondary)
            MButton(stringResource(Res.string.gallery_open_sheet), { sheet = true }, wide, MButtonVariant.Secondary)
            MButton(stringResource(Res.string.gallery_show_toast), { scope.launch { toasts.show(message) } }, wide, MButtonVariant.Tonal)
            MButton(
                stringResource(Res.string.gallery_show_action_toast),
                { scope.launch { toasts.show(actionMessage, actionLabel = action) } },
                wide,
                MButtonVariant.Tonal,
            )
            MButton(
                stringResource(Res.string.gallery_show_error_toast),
                { scope.launch { toasts.show(errorMessage, MToastKind.Error) } },
                wide,
                MButtonVariant.Tonal,
            )
        }
        MToastHost(toasts, Modifier.fillMaxSize())
    }
    if (dialog) {
        MDialog(
            title = stringResource(Res.string.gallery_dialog_title),
            text = stringResource(Res.string.gallery_dialog_text),
            confirmLabel = stringResource(Res.string.gallery_dialog_confirm),
            onConfirm = { dialog = false },
            onDismiss = { dialog = false },
            dismissLabel = stringResource(Res.string.gallery_dialog_cancel),
        )
    }
    if (destructive) {
        MDialog(
            title = stringResource(Res.string.gallery_dialog_destructive_title),
            text = stringResource(Res.string.gallery_dialog_destructive_text),
            confirmLabel = stringResource(Res.string.gallery_dialog_destructive_confirm),
            onConfirm = { destructive = false },
            onDismiss = { destructive = false },
            dismissLabel = stringResource(Res.string.gallery_dialog_cancel),
            destructive = true,
        )
    }
    if (sheet) {
        MBottomSheet(onDismiss = { sheet = false }) {
            MText(
                stringResource(Res.string.gallery_sheet_text),
                style = MTheme.type.bodyLg,
                modifier = Modifier.padding(MTheme.space.s6),
            )
        }
    }
}
