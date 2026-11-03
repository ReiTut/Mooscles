package com.mooscles.screens.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.editor_set_copy_all
import com.mooscles.screens.resources.editor_set_follow_record
import com.mooscles.screens.resources.editor_set_label
import com.mooscles.screens.resources.editor_set_last
import com.mooscles.screens.resources.editor_set_normal
import com.mooscles.screens.resources.editor_set_remove
import com.mooscles.screens.resources.editor_set_type
import com.mooscles.screens.resources.editor_set_warmup
import com.mooscles.screens.resources.editor_set_warmup_label
import com.mooscles.screens.workout.MenuItem
import com.mooscles.ui.RoutineSetUi
import org.jetbrains.compose.resources.stringResource

/** The test tags of a planned set's menu. */
internal object PlannedSetMenuTags {
    const val LIST = "routine-set-menu-list"
    const val NORMAL = "routine-set-menu-normal"
    const val WARMUP = "routine-set-menu-warmup"
    const val FOLLOW = "routine-set-menu-follow"
    const val COPY_ALL = "routine-set-menu-copy-all"
    const val REMOVE = "routine-set-menu-remove"
    const val LAST_REASON = "routine-set-menu-last"
}

/**
 * The menu of a planned set, a bottom sheet under its name ("Set 3", "Warm-up set"). "Set type" lists Normal and
 * Warm-up as items of their own with the current one marked, so marking a warm-up is the label and one more tap.
 * "Follow the record" is only for a working set that is manual ([RoutineSetUi.inherited] false). "Copy to all sets"
 * is only for a working set whose exercise has another working set ([copyToAll]). "Remove set" is
 * destructive and last; off for the only set of the exercise ([last]), with the reason under it. The items scroll under
 * the title when the sheet is shorter than they are (large text, landscape). Each closes the menu and then does its part.
 */
@Composable
internal fun PlannedSetMenuSheet(
    set: RoutineSetUi,
    number: Int,
    last: Boolean,
    onNormal: () -> Unit,
    onWarmup: () -> Unit,
    onFollowRecord: () -> Unit,
    copyToAll: Boolean,
    onCopyToAll: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MBottomSheet(onDismiss = onDismiss) {
        SheetTitle(if (set.warmup) stringResource(Res.string.editor_set_warmup_label, number) else stringResource(Res.string.editor_set_label, number))
        Column(Modifier.testTag(PlannedSetMenuTags.LIST).verticalScroll(rememberScrollState())) {
            MText(
                stringResource(Res.string.editor_set_type),
                MTheme.type.caption,
                color = MTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4).semantics { heading() },
            )
            MenuItem(MIcons.Dumbbell, stringResource(Res.string.editor_set_normal), PlannedSetMenuTags.NORMAL, selected = !set.warmup, onClick = onNormal)
            MenuItem(MIcons.Timer, stringResource(Res.string.editor_set_warmup), PlannedSetMenuTags.WARMUP, selected = set.warmup, onClick = onWarmup)
            if (!set.warmup && !set.inherited) {
                MenuItem(MIcons.PlateRecord, stringResource(Res.string.editor_set_follow_record), PlannedSetMenuTags.FOLLOW, onClick = onFollowRecord)
            }
            if (!set.warmup && copyToAll) {
                MenuItem(MIcons.Copy, stringResource(Res.string.editor_set_copy_all), PlannedSetMenuTags.COPY_ALL, onClick = onCopyToAll)
            }
            MenuItem(
                MIcons.Trash2, stringResource(Res.string.editor_set_remove), PlannedSetMenuTags.REMOVE,
                enabled = !last, destructive = true, onClick = onRemove,
            )
            if (last) {
                MText(
                    stringResource(Res.string.editor_set_last),
                    MTheme.type.caption,
                    color = MTheme.colors.textSecondary,
                    modifier = Modifier.testTag(PlannedSetMenuTags.LAST_REASON).fillMaxWidth().padding(horizontal = MTheme.space.s4),
                )
            }
            Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}
