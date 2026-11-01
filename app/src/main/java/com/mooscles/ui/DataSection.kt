package com.mooscles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mooscles.backup.BackupTexts
import com.mooscles.backup.suggestedBackupFileName
import com.mooscles.designsystem.components.ErrorBanner
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.components.MProgressBar
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.settings_data_desc
import com.mooscles.designsystem.resources.settings_data_dismiss
import com.mooscles.designsystem.resources.settings_data_footer
import com.mooscles.designsystem.resources.settings_data_title
import com.mooscles.designsystem.resources.settings_data_export_photos_note
import com.mooscles.designsystem.resources.settings_export
import com.mooscles.designsystem.resources.settings_import
import com.mooscles.designsystem.resources.settings_import_blocked
import com.mooscles.designsystem.resources.settings_import_cancel
import com.mooscles.designsystem.resources.settings_import_confirm
import com.mooscles.designsystem.resources.settings_import_nothing_new
import com.mooscles.designsystem.resources.settings_import_ok
import com.mooscles.designsystem.resources.settings_import_title
import com.mooscles.designsystem.theme.MTheme
import java.time.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The "Your data" section of Settings: JSON export and import. Cancelling a picker (null URI)
 * does nothing. Export and import are secondary buttons: the screen keeps its one primary
 * button for the updater's current step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataSection(state: BackupUi, workoutActive: Boolean, actions: DataActions, modifier: Modifier = Modifier) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.exportData(uri.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.prepareImport(uri.toString())
    }
    val colors = MTheme.colors
    val space = MTheme.space
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(stringResource(Res.string.settings_data_title))
        Column(Modifier.fillMaxWidth().padding(horizontal = space.s4), verticalArrangement = Arrangement.spacedBy(space.s3)) {
            MText(stringResource(Res.string.settings_data_desc), MTheme.type.bodyMd, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap), verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap)) {
                MButton(
                    stringResource(Res.string.settings_export),
                    onClick = { exportLauncher.launch(suggestedBackupFileName(LocalDate.now())) },
                    variant = MButtonVariant.Secondary,
                    icon = MIcons.Upload,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("data-export"),
                )
                MButton(
                    stringResource(Res.string.settings_import),
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    variant = MButtonVariant.Secondary,
                    icon = MIcons.Download,
                    enabled = !state.busy && !workoutActive,
                    modifier = Modifier.testTag("data-import"),
                )
            }
            // Decision 18: the file carries no photos, said where the export is.
            MText(
                stringResource(Res.string.settings_data_export_photos_note),
                MTheme.type.bodyMd,
                color = colors.textSecondary,
                modifier = Modifier.testTag("data-export-photos-note"),
            )
            if (workoutActive) {
                MText(stringResource(Res.string.settings_import_blocked), MTheme.type.bodyMd, color = colors.textSecondary)
            }
            if (state.busy) {
                Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    // The operation name comes ready from the ViewModel, in English (plan decision 11).
                    state.working?.let { MText(it, MTheme.type.bodyMd, modifier = Modifier.testTag("data-working")) }
                    // The text above already names the operation; the bar is read as "Loading".
                    MProgressBar(progress = null, modifier = Modifier.testTag("data-progress"))
                }
            }
            state.message?.let { message ->
                Column(Modifier.fillMaxWidth()) {
                    if (state.messageIsError) {
                        ErrorBanner(message, Modifier.testTag("data-message-error"))
                    } else {
                        Row(
                            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(space.s2),
                            verticalAlignment = Alignment.Top,
                        ) {
                            MIcon(
                                MIcons.CircleCheck,
                                contentDescription = null,
                                size = MTheme.sizes.icon20,
                                tint = colors.accentText,
                                modifier = Modifier.testTag("data-message-success"),
                            )
                            MText(message, MTheme.type.bodyMd, modifier = Modifier.weight(1f))
                        }
                    }
                    MButton(
                        stringResource(Res.string.settings_data_dismiss),
                        onClick = actions::clearBackupMessage,
                        variant = MButtonVariant.Text,
                        modifier = Modifier.align(Alignment.End).testTag("data-message-dismiss"),
                    )
                }
            }
            MText(stringResource(Res.string.settings_data_footer), MTheme.type.bodyMd, color = colors.textSecondary)
        }
    }
    // The dialog closes as soon as the import starts; the section shows the progress.
    state.pendingImport?.takeIf { !state.busy }?.let { summary ->
        if (BackupTexts.isNothingNew(summary)) {
            MDialog(
                title = stringResource(Res.string.settings_import_title),
                text = stringResource(Res.string.settings_import_nothing_new),
                confirmLabel = stringResource(Res.string.settings_import_ok),
                onConfirm = actions::cancelImport,
                onDismiss = actions::cancelImport,
            )
        } else {
            MDialog(
                title = stringResource(Res.string.settings_import_title),
                text = importSummaryText(summary),
                confirmLabel = stringResource(Res.string.settings_import_confirm),
                onConfirm = actions::confirmImport,
                onDismiss = actions::cancelImport,
                dismissLabel = stringResource(Res.string.settings_import_cancel),
            )
        }
    }
}
