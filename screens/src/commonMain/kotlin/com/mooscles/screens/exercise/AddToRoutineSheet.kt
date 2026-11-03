package com.mooscles.screens.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.data.RoutineFolder
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.SettingsRow
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.folderName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.exercise_add_no_routines
import com.mooscles.screens.resources.exercise_add_routine
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiState
import org.jetbrains.compose.resources.stringResource

/** The test tags of [AddToRoutineSheet]. */
internal object AddToRoutineSheetTags {
    const val ROOT = "add-to-routine-sheet"
    const val EMPTY = "add-to-routine-empty"

    /** The row of the routine whose id is [routineId]. */
    fun routine(routineId: String) = "add-to-routine-$routineId"

    /** The header of the folder whose id is [folderId]. */
    fun folder(folderId: String) = "add-to-routine-folder-$folderId"
}

/**
 * "Add to routine" (spec "Detalhe do exercício"): a bottom sheet with the routines grouped by
 * folder, the folders and the routines in their saved order, each folder under its [folderName]
 * (a folder without routines is left out; a routine whose folder is not in the list goes with the
 * default folder). A tap on a routine calls [MoosclesActions.addRoutineExercise] once (a second tap
 * before the sheet is gone does nothing) and hands the routine to [onAdded], whose caller closes
 * the sheet; it is the caller that waits for the state to show the exercise before saying it was
 * added. Without routines, a line says where to create one.
 */
@Composable
internal fun AddToRoutineSheet(
    exerciseId: String,
    state: UiState,
    actions: MoosclesActions,
    onAdded: (routineId: String, routineName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(false) }
    val groups = remember(state.routineFolders, state.routines) { routineGroups(state.routineFolders, state.routines) }
    MBottomSheet(onDismiss = onDismiss, modifier = Modifier.testTag(AddToRoutineSheetTags.ROOT)) {
        MText(
            stringResource(Res.string.exercise_add_routine),
            MTheme.type.titleMd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MTheme.space.s4)
                .padding(bottom = MTheme.space.s2)
                .semantics { heading() },
        )
        if (groups.isEmpty()) {
            MText(
                stringResource(Res.string.exercise_add_no_routines),
                MTheme.type.bodyLg,
                color = MTheme.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AddToRoutineSheetTags.EMPTY)
                    .padding(horizontal = MTheme.space.s4)
                    .padding(top = MTheme.space.s1, bottom = MTheme.space.s6),
            )
            return@MBottomSheet
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            for ((folder, routines) in groups) {
                SectionHeader(folderName(folder), Modifier.testTag(AddToRoutineSheetTags.folder(folder.id)))
                for (routine in routines) {
                    SettingsRow(
                        title = routine.name,
                        modifier = Modifier.testTag(AddToRoutineSheetTags.routine(routine.id)),
                        onClick = {
                            if (!chosen) {
                                chosen = true
                                actions.addRoutineExercise(routine.id, exerciseId)
                                onAdded(routine.id, routine.name)
                            }
                        },
                        trailing = { MIcon(MIcons.Plus, contentDescription = null, size = MTheme.sizes.icon20, tint = MTheme.colors.textTertiary) },
                    )
                }
            }
            Spacer(Modifier.height(MTheme.space.s4))
        }
    }
}

/**
 * The folders that hold routines, in their order, each with its routines in theirs. A routine whose
 * folder is not in [folders] goes with the default folder; without any folder (none known yet),
 * every routine goes under the default folder, so none disappears (final review, item 3).
 */
internal fun routineGroups(folders: List<RoutineFolderUi>, routines: List<RoutineUi>): List<Pair<RoutineFolderUi, List<RoutineUi>>> {
    val shown = folders.ifEmpty { listOf(RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)) }
    val known = shown.mapTo(HashSet()) { it.id }
    val home = shown.firstOrNull { it.id == RoutineFolder.DEFAULT_ID } ?: shown.first()
    return shown.mapNotNull { folder ->
        val inside = routines.filter { it.folderId == folder.id || (folder == home && it.folderId !in known) }
        if (inside.isEmpty()) null else folder to inside
    }
}
