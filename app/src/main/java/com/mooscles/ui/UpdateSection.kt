package com.mooscles.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MProgressBar
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.SettingsRow
import com.mooscles.designsystem.components.SwitchRow
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.settings_component_gallery
import com.mooscles.designsystem.resources.settings_updates_allow_source
import com.mooscles.designsystem.resources.settings_updates_approval_note
import com.mooscles.designsystem.resources.settings_updates_approval_note_short
import com.mooscles.designsystem.resources.settings_updates_automatic
import com.mooscles.designsystem.resources.settings_updates_automatic_desc
import com.mooscles.designsystem.resources.settings_updates_build
import com.mooscles.designsystem.resources.settings_updates_cancel
import com.mooscles.designsystem.resources.settings_updates_channel_prerelease
import com.mooscles.designsystem.resources.settings_updates_channel_stable
import com.mooscles.designsystem.resources.settings_updates_check
import com.mooscles.designsystem.resources.settings_updates_checking
import com.mooscles.designsystem.resources.settings_updates_deferred_note
import com.mooscles.designsystem.resources.settings_updates_download
import com.mooscles.designsystem.resources.settings_updates_download_again
import com.mooscles.designsystem.resources.settings_updates_idle_note
import com.mooscles.designsystem.resources.settings_updates_install
import com.mooscles.designsystem.resources.settings_updates_installed
import com.mooscles.designsystem.resources.settings_updates_last_checked
import com.mooscles.designsystem.resources.settings_updates_not_checked
import com.mooscles.designsystem.resources.settings_updates_permission_note
import com.mooscles.designsystem.resources.settings_updates_prereleases
import com.mooscles.designsystem.resources.settings_updates_prereleases_desc
import com.mooscles.designsystem.resources.settings_updates_progress
import com.mooscles.designsystem.resources.settings_updates_read_all
import com.mooscles.designsystem.resources.settings_updates_reading_version
import com.mooscles.designsystem.resources.settings_updates_release_notes
import com.mooscles.designsystem.resources.settings_updates_retry
import com.mooscles.designsystem.resources.settings_updates_retry_install
import com.mooscles.designsystem.resources.settings_updates_review_install
import com.mooscles.designsystem.resources.settings_updates_schedule
import com.mooscles.designsystem.resources.settings_updates_show_less
import com.mooscles.designsystem.resources.settings_updates_succeeded_note
import com.mooscles.designsystem.resources.settings_updates_title
import com.mooscles.designsystem.resources.settings_updates_wifi_note
import com.mooscles.designsystem.resources.settings_updates_wifi_only
import com.mooscles.designsystem.resources.settings_updates_wifi_only_desc
import com.mooscles.designsystem.theme.MTheme
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource

/**
 * The "App updates" section of Settings. The whole section carries the tag `app-updates`.
 * At most one primary button shows at a time: the action of the updater's current step.
 */
@Composable
fun UpdateSection(state: UpdateUi, actions: UpdateActions, modifier: Modifier = Modifier, onOpenGallery: () -> Unit = {}) {
    val space = MTheme.space
    Column(modifier.fillMaxWidth().testTag("app-updates"), verticalArrangement = Arrangement.spacedBy(space.s2)) {
        SectionHeader(stringResource(Res.string.settings_updates_title))
        // Seven quick taps unlock the hidden design gallery (in memory only). The row is at least
        // 56 dp tall, but it is not a button: a screen reader reads it as plain text (one node,
        // no role, no click action), because a tap shows nothing until the seventh.
        SettingsRow(
            title = if (state.installedVersionName.isBlank()) stringResource(Res.string.settings_updates_reading_version)
            else stringResource(Res.string.settings_updates_installed, state.installedVersionName, state.installedVersionCode.toString()),
            modifier = Modifier
                .testTag("update-installed-version")
                .semantics(mergeDescendants = true) {}
                .pointerInput(Unit) { detectTapGestures { DeveloperGallery.registerVersionTap(SystemClock.uptimeMillis()) } },
        )
        SwitchRow(
            stringResource(Res.string.settings_updates_automatic), state.automaticUpdates, actions::setAutomaticUpdates,
            Modifier.testTag("update-automatic"), subtitle = stringResource(Res.string.settings_updates_automatic_desc),
        )
        SwitchRow(
            stringResource(Res.string.settings_updates_wifi_only), state.wifiOnly, actions::setUpdateWifiOnly,
            Modifier.testTag("update-wifi-only"), subtitle = stringResource(Res.string.settings_updates_wifi_only_desc),
        )
        SwitchRow(
            stringResource(Res.string.settings_updates_prereleases), state.includePrereleases, actions::setIncludePrereleases,
            Modifier.testTag("update-prereleases"), subtitle = stringResource(Res.string.settings_updates_prereleases_desc),
        )
        Note(
            stringResource(
                if (state.includePrereleases) Res.string.settings_updates_channel_prerelease
                else Res.string.settings_updates_channel_stable,
            ),
        )
        UpdateStatus(state, actions, Modifier.padding(horizontal = space.s4))
        if (state.releaseNotes.isNotBlank()) ReleaseNotes(state.releaseNotes)
        // The label and its value stay together; the button gets its own space below them.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = space.s4, vertical = space.s2),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            Column {
                MText(stringResource(Res.string.settings_updates_last_checked), MTheme.type.labelMd, color = MTheme.colors.textSecondary)
                MText(
                    state.lastCheckedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                        ?: stringResource(Res.string.settings_updates_not_checked),
                    MTheme.type.bodyMd,
                    modifier = Modifier.testTag("update-last-checked"),
                )
            }
            MButton(
                stringResource(if (state.phase == UpdatePhase.CHECKING) Res.string.settings_updates_checking else Res.string.settings_updates_check),
                onClick = actions::checkForUpdates,
                variant = MButtonVariant.Secondary,
                icon = MIcons.RefreshCw,
                enabled = state.phase !in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLING),
                modifier = Modifier.testTag("update-check"),
            )
        }
        // Two paragraphs: a paragraph gap between them, not the line gap of one paragraph.
        Column(Modifier.padding(top = space.s2), verticalArrangement = Arrangement.spacedBy(space.s3)) {
            Note(stringResource(Res.string.settings_updates_schedule))
            Note(stringResource(Res.string.settings_updates_approval_note))
        }
        if (DeveloperGallery.unlocked) {
            SettingsRow(
                stringResource(Res.string.settings_component_gallery),
                modifier = Modifier.testTag("open-gallery"),
                onClick = onOpenGallery,
            )
        }
    }
}

/** A secondary explanation under a group of settings. */
@Composable
private fun Note(text: String) {
    MText(text, MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4))
}

@Composable
private fun UpdateStatus(state: UpdateUi, actions: UpdateActions, modifier: Modifier = Modifier) {
    val colors = MTheme.colors
    val space = MTheme.space
    val failed = state.phase == UpdatePhase.FAILED
    val busy = state.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLING)
    Column(
        modifier
            .fillMaxWidth()
            .testTag("update-status")
            .background(if (failed) colors.dangerSubtle else colors.bgRaised, RoundedCornerShape(MTheme.radius.md))
            .padding(space.s4),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.Top) {
            // A failure is told by the icon and the title, not by the color alone.
            if (failed) MIcon(MIcons.CircleAlert, contentDescription = null, size = MTheme.sizes.icon24, tint = colors.dangerText)
            MText(updateStatusTitleText(state.phase), MTheme.type.titleSm, modifier = Modifier.weight(1f))
        }
        val available = state.availableVersionName
        if (available != null && state.phase != UpdatePhase.SUCCEEDED) {
            MText(
                state.availableVersionCode?.let { stringResource(Res.string.settings_updates_build, available, it.toString()) } ?: available,
                MTheme.type.bodyMd,
            )
        }
        // The updater's message comes ready in English (plan decision 11).
        state.message?.takeIf { it.isNotBlank() }?.let { MText(it, MTheme.type.bodyMd) }
        if (busy) MProgressBar(progress = null)
        if (state.phase == UpdatePhase.DOWNLOADING) {
            val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            MProgressBar(progress = if (state.totalBytes > 0) progress else null, modifier = Modifier.testTag("update-progress"))
            MText(
                if (state.totalBytes > 0) {
                    stringResource(
                        Res.string.settings_updates_progress,
                        "${(progress * 100).toInt()}%", updateSizeText(state.downloadedBytes), updateSizeText(state.totalBytes),
                    )
                } else {
                    updateSizeText(state.downloadedBytes)
                },
                MTheme.type.bodyMd,
                color = colors.textSecondary,
            )
        }
        when (state.phase) {
            UpdatePhase.IDLE -> StatusNote(stringResource(Res.string.settings_updates_idle_note))
            UpdatePhase.AVAILABLE -> StepButton(stringResource(Res.string.settings_updates_download), "update-download", actions::downloadUpdate)
            UpdatePhase.WAITING_FOR_WIFI -> {
                StatusNote(stringResource(Res.string.settings_updates_wifi_note))
                UpdateCancel(actions)
            }
            UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING -> UpdateCancel(actions)
            UpdatePhase.READY -> StepButton(stringResource(Res.string.settings_updates_install), "update-install", actions::installUpdate)
            UpdatePhase.DEFERRED -> StatusNote(stringResource(Res.string.settings_updates_deferred_note))
            UpdatePhase.PERMISSION_REQUIRED -> {
                StatusNote(stringResource(Res.string.settings_updates_permission_note))
                StepButton(stringResource(Res.string.settings_updates_allow_source), "update-install", actions::installUpdate)
            }
            UpdatePhase.APPROVAL_REQUIRED -> {
                StatusNote(stringResource(Res.string.settings_updates_approval_note_short))
                StepButton(stringResource(Res.string.settings_updates_review_install), "update-install", actions::installUpdate)
            }
            UpdatePhase.FAILED -> StepButton(
                stringResource(if (state.verifiedApkReady) Res.string.settings_updates_retry_install else Res.string.settings_updates_retry),
                "update-retry",
            ) {
                when {
                    state.verifiedApkReady -> actions.installUpdate()
                    state.availableVersionCode != null -> actions.downloadUpdate()
                    else -> actions.checkForUpdates()
                }
            }
            UpdatePhase.CANCELLED -> if (state.availableVersionCode != null) {
                if (state.verifiedApkReady) {
                    StepButton(stringResource(Res.string.settings_updates_retry_install), "update-install", actions::installUpdate)
                } else {
                    StepButton(stringResource(Res.string.settings_updates_download_again), "update-download", actions::downloadUpdate)
                }
            }
            UpdatePhase.SUCCEEDED -> StatusNote(stringResource(Res.string.settings_updates_succeeded_note))
            else -> Unit
        }
    }
}

@Composable
private fun StatusNote(text: String) {
    MText(text, MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
}

/** The action of the updater's current step: the one primary button of the screen. */
@Composable
private fun StepButton(text: String, tag: String, onClick: () -> Unit) {
    MButton(text, onClick = onClick, variant = MButtonVariant.Primary, modifier = Modifier.testTag(tag))
}

@Composable
private fun UpdateCancel(actions: UpdateActions) {
    MButton(
        stringResource(Res.string.settings_updates_cancel),
        onClick = actions::cancelUpdate,
        variant = MButtonVariant.Text,
        modifier = Modifier.testTag("update-cancel"),
    )
}

@Composable
private fun ReleaseNotes(notes: String) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        MText(stringResource(Res.string.settings_updates_release_notes), MTheme.type.labelLg)
        MText(if (expanded || notes.length <= 500) notes else notes.take(500).trimEnd() + "…", MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
        if (notes.length > 500) {
            MButton(
                stringResource(if (expanded) Res.string.settings_updates_show_less else Res.string.settings_updates_read_all),
                onClick = { expanded = !expanded },
                variant = MButtonVariant.Text,
                modifier = Modifier.testTag("update-read-all"),
            )
        }
    }
}
