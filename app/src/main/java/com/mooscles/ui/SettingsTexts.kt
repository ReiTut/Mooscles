package com.mooscles.ui

import androidx.compose.runtime.Composable
import com.mooscles.backup.ImportSummary
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.settings_custom_exercises
import com.mooscles.designsystem.resources.settings_import_add
import com.mooscles.designsystem.resources.settings_import_counts
import com.mooscles.designsystem.resources.settings_import_prefs
import com.mooscles.designsystem.resources.settings_routines
import com.mooscles.designsystem.resources.settings_updates_size_kb
import com.mooscles.designsystem.resources.settings_updates_size_mb
import com.mooscles.designsystem.resources.settings_updates_status_approval_required
import com.mooscles.designsystem.resources.settings_updates_status_available
import com.mooscles.designsystem.resources.settings_updates_status_cancelled
import com.mooscles.designsystem.resources.settings_updates_status_checking
import com.mooscles.designsystem.resources.settings_updates_status_deferred
import com.mooscles.designsystem.resources.settings_updates_status_downloading
import com.mooscles.designsystem.resources.settings_updates_status_failed
import com.mooscles.designsystem.resources.settings_updates_status_idle
import com.mooscles.designsystem.resources.settings_updates_status_installing
import com.mooscles.designsystem.resources.settings_updates_status_permission_required
import com.mooscles.designsystem.resources.settings_updates_status_ready
import com.mooscles.designsystem.resources.settings_updates_status_succeeded
import com.mooscles.designsystem.resources.settings_updates_status_verifying
import com.mooscles.designsystem.resources.settings_updates_status_waiting_for_wifi
import com.mooscles.designsystem.resources.settings_workouts
import com.mooscles.designsystem.resources.settings_workouts_kept
import org.jetbrains.compose.resources.stringResource

/**
 * Body of the import confirmation dialog. In English it equals `BackupTexts.summary`; the
 * language comes from the Compose resources, and zero stays plural in every language.
 */
@Composable
fun importSummaryText(summary: ImportSummary): String {
    val counts = stringResource(
        Res.string.settings_import_counts,
        pluralCount(Res.plurals.settings_workouts, summary.workoutsAdded),
        pluralCount(Res.plurals.settings_routines, summary.routinesAdded),
        pluralCount(Res.plurals.settings_custom_exercises, summary.customExercisesAdded),
    )
    return buildString {
        append(stringResource(Res.string.settings_import_add, counts))
        if (summary.workoutsKept > 0) {
            append(' ').append(pluralCount(Res.plurals.settings_workouts_kept, summary.workoutsKept))
        }
        if (summary.restoresPreferences) append(' ').append(stringResource(Res.string.settings_import_prefs))
    }
}

/** Title of the update status block; the English titles are the ones the app shipped before. */
@Composable
fun updateStatusTitleText(phase: UpdatePhase): String = stringResource(
    when (phase) {
        UpdatePhase.IDLE -> Res.string.settings_updates_status_idle
        UpdatePhase.CHECKING -> Res.string.settings_updates_status_checking
        UpdatePhase.AVAILABLE -> Res.string.settings_updates_status_available
        UpdatePhase.WAITING_FOR_WIFI -> Res.string.settings_updates_status_waiting_for_wifi
        UpdatePhase.DOWNLOADING -> Res.string.settings_updates_status_downloading
        UpdatePhase.VERIFYING -> Res.string.settings_updates_status_verifying
        UpdatePhase.READY -> Res.string.settings_updates_status_ready
        UpdatePhase.DEFERRED -> Res.string.settings_updates_status_deferred
        UpdatePhase.PERMISSION_REQUIRED -> Res.string.settings_updates_status_permission_required
        UpdatePhase.APPROVAL_REQUIRED -> Res.string.settings_updates_status_approval_required
        UpdatePhase.INSTALLING -> Res.string.settings_updates_status_installing
        UpdatePhase.SUCCEEDED -> Res.string.settings_updates_status_succeeded
        UpdatePhase.FAILED -> Res.string.settings_updates_status_failed
        UpdatePhase.CANCELLED -> Res.string.settings_updates_status_cancelled
    },
)

/**
 * Download size: one decimal in MB from 1 MiB up (separator by language), whole KB below.
 * The rounding is half up, as `%.1f` did.
 */
@Composable
fun updateSizeText(bytes: Long): String {
    if (bytes >= BYTES_PER_MB) {
        val tenths = (bytes * 10.0 / BYTES_PER_MB + 0.5).toLong()
        val plain = "${tenths / 10}.${tenths % 10}"
        return stringResource(Res.string.settings_updates_size_mb, MoosclesNumbers.localize(plain, currentNumberLocale()))
    }
    return stringResource(Res.string.settings_updates_size_kb, (bytes.coerceAtLeast(0) / 1024).toString())
}

private const val BYTES_PER_MB = 1_048_576L
