package com.mooscles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateSection(state: UpdateUi, actions: UpdateActions, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("app-updates")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("App updates", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.installedVersionName.isBlank()) "Reading installed version"
                        else "Installed ${state.installedVersionName} · build ${state.installedVersionCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("update-installed-version"),
                    )
                }
            }
            UpdatePreference(
                "Automatic updates", "Download available updates automatically", state.automaticUpdates,
                actions::setAutomaticUpdates, "update-automatic",
            )
            UpdatePreference(
                "Wi-Fi-only downloads", "Wait for Wi-Fi before downloading an APK", state.wifiOnly,
                actions::setUpdateWifiOnly, "update-wifi-only",
            )
            UpdatePreference(
                "Include prereleases", "Get preview and debug releases as well as stable releases", state.includePrereleases,
                actions::setIncludePrereleases, "update-prereleases",
            )
            Text(
                if (state.includePrereleases) "Channel: stable + prerelease. Preview releases may be less tested."
                else "Channel: stable. Drafts and prereleases are excluded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            UpdateStatus(state, actions)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Last checked", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.lastCheckedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                            ?: "Not checked yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("update-last-checked"),
                    )
                }
                OutlinedButton(
                    onClick = actions::checkForUpdates,
                    enabled = state.phase !in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLING),
                    modifier = Modifier.testTag("update-check"),
                ) { Text(if (state.phase == UpdatePhase.CHECKING) "Checking…" else "Check for updates") }
            }
            Text(
                "Checks run when you open the app and about every six hours in the background. Android may delay background checks. Workout tracking stays available offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Android may ask you to approve an installation. Updates wait until your workout and rest timer finish. A phone running an older version without this updater needs this updater-enabled APK installed once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdatePreference(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun UpdateStatus(state: UpdateUi, actions: UpdateActions) {
    val failed = state.phase == UpdatePhase.FAILED
    val busy = state.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLING)
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("update-status"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(updateStatusTitle(state.phase), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.availableVersionName != null && state.phase != UpdatePhase.SUCCEEDED) {
                Text(state.availableVersionName + (state.availableVersionCode?.let { " · build $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == UpdatePhase.DOWNLOADING) {
                val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
                if (state.totalBytes > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("update-progress"))
                else LinearProgressIndicator(Modifier.fillMaxWidth().testTag("update-progress"))
                Text(
                    if (state.totalBytes > 0) "${(progress * 100).toInt()}% · ${updateSize(state.downloadedBytes)} / ${updateSize(state.totalBytes)}"
                    else updateSize(state.downloadedBytes), style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state.phase) {
                UpdatePhase.IDLE -> Text("Published updates come from ReiTut/Mooscles on GitHub.", style = MaterialTheme.typography.bodySmall)
                UpdatePhase.AVAILABLE -> Button(onClick = actions::downloadUpdate, modifier = Modifier.testTag("update-download")) { Text("Download update") }
                UpdatePhase.WAITING_FOR_WIFI -> {
                    Text("Downloading will begin when Wi-Fi is available. You can change the Wi-Fi preference above.", style = MaterialTheme.typography.bodySmall)
                    UpdateCancel(actions)
                }
                UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING -> UpdateCancel(actions)
                UpdatePhase.READY -> Button(onClick = actions::installUpdate, modifier = Modifier.testTag("update-install")) { Text("Install update") }
                UpdatePhase.DEFERRED -> Text("Your download is safe. Installation will wait until your workout is saved and your rest timer finishes.", style = MaterialTheme.typography.bodySmall)
                UpdatePhase.PERMISSION_REQUIRED -> {
                    Text("Android requires permission to install an APK from Mooscles. Enable “Allow from this source” on the next screen, then return here. Your choice is only requested when you install.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = actions::installUpdate, modifier = Modifier.testTag("update-install")) { Text("Allow from this source") }
                }
                UpdatePhase.APPROVAL_REQUIRED -> {
                    Text("Android needs your approval. Open the system confirmation when you are ready.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = actions::installUpdate, modifier = Modifier.testTag("update-install")) { Text("Review installation") }
                }
                UpdatePhase.FAILED -> Button(onClick = {
                    when {
                        state.verifiedApkReady -> actions.installUpdate()
                        state.availableVersionCode != null -> actions.downloadUpdate()
                        else -> actions.checkForUpdates()
                    }
                }, modifier = Modifier.testTag("update-retry")) { Text(if (state.verifiedApkReady) "Retry installation" else "Retry") }
                UpdatePhase.CANCELLED -> if (state.availableVersionCode != null) {
                    if (state.verifiedApkReady) {
                        Button(onClick = actions::installUpdate, modifier = Modifier.testTag("update-install")) { Text("Retry installation") }
                    } else {
                        Button(onClick = actions::downloadUpdate, modifier = Modifier.testTag("update-download")) { Text("Download again") }
                    }
                }
                UpdatePhase.SUCCEEDED -> Text("The installed version is ready. Your training data stays on this device.", style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            if (state.releaseNotes.isNotBlank()) ReleaseNotes(state.releaseNotes)
        }
    }
}

@Composable
private fun UpdateCancel(actions: UpdateActions) {
    TextButton(onClick = actions::cancelUpdate, modifier = Modifier.testTag("update-cancel")) { Text("Cancel download") }
}

@Composable
private fun ReleaseNotes(notes: String) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    Text("Release notes", style = MaterialTheme.typography.labelLarge)
    Text(if (expanded || notes.length <= 500) notes else notes.take(500).trimEnd() + "…", style = MaterialTheme.typography.bodySmall)
    if (notes.length > 500) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read all notes") }
}

internal fun updateStatusTitle(phase: UpdatePhase): String = when (phase) {
    UpdatePhase.IDLE -> "Ready to check"
    UpdatePhase.CHECKING -> "Checking GitHub Releases"
    UpdatePhase.AVAILABLE -> "Update available"
    UpdatePhase.WAITING_FOR_WIFI -> "Waiting for Wi-Fi"
    UpdatePhase.DOWNLOADING -> "Downloading update"
    UpdatePhase.VERIFYING -> "Verifying update"
    UpdatePhase.READY -> "Update ready"
    UpdatePhase.DEFERRED -> "Update ready · installation deferred"
    UpdatePhase.PERMISSION_REQUIRED -> "Installation permission needed"
    UpdatePhase.APPROVAL_REQUIRED -> "Update ready · approval needed"
    UpdatePhase.INSTALLING -> "Installing update"
    UpdatePhase.SUCCEEDED -> "Update successful"
    UpdatePhase.FAILED -> "Update could not finish"
    UpdatePhase.CANCELLED -> "Update cancelled"
}

private fun updateSize(bytes: Long): String = if (bytes >= 1_048_576) String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
else "${bytes.coerceAtLeast(0) / 1024} KB"
