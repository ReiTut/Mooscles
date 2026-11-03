package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import com.mooscles.data.ExerciseNames
import com.mooscles.data.RoutineFolder
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.catalog_default_folder
import com.mooscles.screens.resources.catalog_names_language
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RoutineFolderUi
import org.jetbrains.compose.resources.stringResource

// Names as the screens show them (spec D11, D12). The stored names never change: the catalog
// keeps writing its English name, and the screen picks the language of the label it shows from
// the same resources as the rest of its text, so a name is never in another language than the
// screen around it (the gallery and the tests with `LocalAppLocale` included).

/** Whether the catalog texts (exercise names, cues) are Portuguese: `catalog_names_language` is "pt-BR". */
@Composable
fun catalogInPortuguese(): Boolean = stringResource(Res.string.catalog_names_language) == PORTUGUESE

/**
 * The name [exercise] shows: a catalog exercise's current catalog name in the screen's language
 * (even when an older build stored another name), a custom exercise's typed name as typed.
 */
@Composable
fun exerciseName(exercise: ExerciseUi): String = displayName(exercise, catalogInPortuguese())

/** [exerciseName] outside a composition, with the language already read ([catalogInPortuguese]). */
internal fun displayName(exercise: ExerciseUi, portuguese: Boolean): String =
    ExerciseNames.display(exercise.id, exercise.name, exercise.custom, portuguese)

/**
 * The name [folder] shows: the default folder, while its stored name is still the one the app
 * gave it ([RoutineFolder.DEFAULT_NAME]), reads "My routines" in the screen's language; a name the
 * person gave, to that folder or any other, is shown as written. The stored name never changes.
 */
@Composable
fun folderName(folder: RoutineFolderUi): String =
    if (folder.id == RoutineFolder.DEFAULT_ID && folder.name == RoutineFolder.DEFAULT_NAME) {
        stringResource(Res.string.catalog_default_folder)
    } else {
        folder.name
    }

private const val PORTUGUESE = "pt-BR"
