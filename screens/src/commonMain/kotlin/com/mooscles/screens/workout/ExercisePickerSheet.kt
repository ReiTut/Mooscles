package com.mooscles.screens.workout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MSearchField
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.photo.rememberExerciseMedia
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.library.EquipmentFilterSheet
import com.mooscles.screens.library.LibraryFilterBar
import com.mooscles.screens.library.LibraryFilters
import com.mooscles.screens.library.LibraryModel
import com.mooscles.screens.library.LibraryModelCache
import com.mooscles.screens.library.LibraryRow
import com.mooscles.screens.library.LibrarySkeletonRow
import com.mooscles.screens.library.buildOffTheMainThread
import com.mooscles.screens.library.rememberLibraryModel
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_picker_add_count
import com.mooscles.screens.resources.workout_picker_all
import com.mooscles.screens.resources.workout_picker_none
import com.mooscles.screens.resources.workout_picker_recent
import com.mooscles.screens.resources.workout_picker_search
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/** How the picker answers: [Single] reports the tapped exercise at once, [Multiple] collects a selection. */
internal enum class PickerMode { Single, Multiple }

/** The tag of the "Add N exercises" button of the picker's [PickerMode.Multiple]. */
internal const val ExercisePickerAddTag = "exercise-picker-add"

/** The tag of the picker's list. */
internal const val ExercisePickerListTag = "exercise-picker-list"

/** The tag of the row of [exerciseId] in "Recent" ([recent]) or in "All". */
internal fun exercisePickerRowTag(exerciseId: String, recent: Boolean) =
    if (recent) "exercise-picker-recent-$exerciseId" else "exercise-picker-all-$exerciseId"

/**
 * The exercise picker (spec "Seletor de exercício"): an `MBottomSheet` at 90% of the screen with
 * the library's search field ("Search exercises" as its placeholder and name, with "Clear search" once
 * typed), then, scrolling with the list, the library's filters (the
 * muscle chips and the "Equipment" chip, which opens [EquipmentFilterSheet] over the picker; no
 * "My exercises"), "Recent" (up to 8 exercises of the latest workouts in [history], each shown as
 * [exercises] has it now; an exercise no longer there is left out) and "All". The search, the
 * filters and the order are the library's ([LibraryModel]: bilingual search, alphabetical by the
 * shown name). A search or a filter lists only the matches, without "Recent", or says that nothing
 * matched; a new one starts the list from the top. Each row is the library's ([LibraryRow]): the
 * 56 dp media (the still figure, frame B, or the equipment's icon), the name and "Chest ·
 * Barbell" in the app's language. Until the model is built ([rememberLibraryModel], off the main
 * thread; at once when the library already built it) the list shows skeleton rows, while the
 * search and the filters already take input.
 *
 * A tap on a row reports the exercise to [onPick] and then closes the picker through [onDismiss],
 * once: the caller turns the pick into `addWorkoutExercise` or `replaceWorkoutExercise` and drops
 * the sheet in [onDismiss], which is also the close without a choice (the scrim, Back, a drag
 * down). The search, the filters and the open equipment sheet survive recreation. [cache] and
 * [build] are the model's seams for tests (pre-flight F19).
 *
 * In [PickerMode.Multiple] a tap on a row selects or unselects it instead, each row a checkbox, and
 * "Add N exercises" pinned under the list (off with none selected) reports the selection to
 * [onPickMany] in the order of the taps, once, and then closes through [onDismiss]; [onPick] is
 * not called. [onPickMany] never gets an empty list. The selection (a list of ids, in
 * `rememberSaveable`) outlives searches, filters and recreation. An exercise that leaves
 * [exercises] is only hidden from the selection (the count and [onPickMany] skip it); its id stays
 * in the saved list, so the exercise shows checked again if it returns.
 */
@Composable
internal fun ExercisePickerSheet(
    exercises: List<ExerciseUi>,
    history: List<WorkoutUi>,
    onPick: (ExerciseUi) -> Unit,
    onDismiss: () -> Unit,
    cache: LibraryModelCache = LibraryModelCache.process,
    build: suspend (List<ExerciseUi>, Boolean) -> LibraryModel = ::buildOffTheMainThread,
    mode: PickerMode = PickerMode.Single,
    onPickMany: (List<ExerciseUi>) -> Unit = {},
) {
    val multiple = mode == PickerMode.Multiple
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var filters by rememberSaveable(stateSaver = LibraryFilters.Saver) { mutableStateOf(LibraryFilters()) }
    var equipmentOpen by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = IdListSaver) { mutableStateOf(emptyList<String>()) }
    // Only what [exercises] still has, in the order of the taps. A vanished id is hidden, not deleted: it stays in
    // [selectedIds] and counts again if the exercise comes back.
    val selection = remember(selectedIds, exercises) {
        val byId = exercises.associateBy { it.id }
        selectedIds.mapNotNull { byId[it] }
    }
    val selectedSet = remember(selection) { selection.mapTo(HashSet()) { it.id } }
    val equipmentFocus = remember { FocusRequester() }
    var focusReturns by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val model = rememberLibraryModel(exercises, catalogInPortuguese(), cache, build)
    val searching = query.text.isNotBlank() || filters.active
    val recents = remember(model, history) { model?.recents(history).orEmpty() }
    val results = remember(model, query.text, filters) { model?.results(query.text, filters).orEmpty() }
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val pick: (ExerciseUi) -> Unit = { exercise ->
        if (multiple) {
            // Unselected goes out; a newly selected one goes to the end.
            selectedIds = if (exercise.id in selectedIds) selectedIds - exercise.id else selectedIds + exercise.id
        } else if (!picked) {
            picked = true
            onPick(exercise)
            onDismiss()
        }
    }

    // A new search or filter starts the list from the top; a recreation keeps the scroll it had.
    var resultsShown by remember { mutableStateOf(false) }
    LaunchedEffect(query.text, filters) {
        if (resultsShown) listState.scrollToItem(0) else resultsShown = true
    }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0) return@LaunchedEffect
        // Once the chip is back in the layout (the list's first item, as in the library).
        withFrameNanos { }
        runCatching { equipmentFocus.requestFocus() }
    }

    MBottomSheet(onDismiss = onDismiss) {
        // The library's search field (spec: "a mesma busca"), with its icon and its clear button.
        MSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.workout_picker_search),
            onClear = { query = TextFieldValue() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4).padding(bottom = MTheme.space.s2),
        )
        // Weighted, so the sheet keeps its full height while the matches change.
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag(ExercisePickerListTag),
            state = listState,
            // In the multiple mode the button under the list holds the inset.
            contentPadding = PaddingValues(bottom = if (multiple) MTheme.space.s4 else bottom + MTheme.space.s4),
        ) {
            item(key = "filters") {
                LibraryFilterBar(
                    filters = filters,
                    onChange = { filters = it },
                    onOpenEquipment = { equipmentOpen = true },
                    equipmentFocus = equipmentFocus,
                    modifier = Modifier.padding(top = MTheme.space.s1, bottom = MTheme.space.s2),
                    mine = false,
                )
            }
            if (model == null) {
                items(SKELETON_ROWS, key = { "skeleton-$it" }) { LibrarySkeletonRow() }
                return@LazyColumn
            }
            if (!searching && recents.isNotEmpty()) {
                item(key = "header-recent") { SectionHeader(stringResource(Res.string.workout_picker_recent)) }
                items(recents, key = { "recent-${it.id}" }) { exercise ->
                    LibraryRow(exercise, model.shownName(exercise), rememberExerciseMedia(exercise, thumbnail = true), exercisePickerRowTag(exercise.id, recent = true), { pick(exercise) }, selected = if (multiple) exercise.id in selectedSet else null)
                }
            }
            if (results.isNotEmpty()) {
                item(key = "header-all") { SectionHeader(stringResource(Res.string.workout_picker_all)) }
                items(results, key = { "all-${it.id}" }) { exercise ->
                    LibraryRow(exercise, model.shownName(exercise), rememberExerciseMedia(exercise, thumbnail = true), exercisePickerRowTag(exercise.id, recent = false), { pick(exercise) }, selected = if (multiple) exercise.id in selectedSet else null)
                }
            } else {
                item(key = "none") {
                    MText(
                        stringResource(Res.string.workout_picker_none),
                        MTheme.type.bodyMd,
                        color = MTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(MTheme.space.s4),
                    )
                }
            }
        }
        if (multiple) {
            MButton(
                text = pluralCount(Res.plurals.workout_picker_add_count, selection.size),
                onClick = {
                    if (!picked && selection.isNotEmpty()) {
                        picked = true
                        onPickMany(selection)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MTheme.space.s4, end = MTheme.space.s4, top = MTheme.space.s2, bottom = bottom + MTheme.space.s2)
                    .testTag(ExercisePickerAddTag),
                size = MButtonSize.L,
                enabled = selection.isNotEmpty() && !picked,
            )
        }
    }
    if (equipmentOpen) {
        EquipmentFilterSheet(
            selected = filters.equipment,
            onChange = { filters = filters.copy(equipment = it) },
            onDismiss = {
                equipmentOpen = false
                focusReturns++
            },
        )
    }
}

/** Saves the selected ids as a plain list (a `List` of any kind is not always saveable). */
private val IdListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/** How many skeleton rows stand in for the list while it is built. */
private const val SKELETON_ROWS = 6
