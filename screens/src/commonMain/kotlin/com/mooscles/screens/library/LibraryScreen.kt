package com.mooscles.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MSearchField
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.illustration.prepareBodyMap
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.CustomExerciseForm
import com.mooscles.screens.exercise.photo.rememberCustomPhotoMedia
import com.mooscles.screens.exercise.ExerciseCues
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.library_all
import com.mooscles.screens.resources.library_catalog_empty
import com.mooscles.screens.resources.library_catalog_empty_body
import com.mooscles.screens.resources.library_clear_filters
import com.mooscles.screens.resources.library_count
import com.mooscles.screens.resources.library_create
import com.mooscles.screens.resources.library_create_named
import com.mooscles.screens.resources.library_empty
import com.mooscles.screens.resources.library_empty_body
import com.mooscles.screens.resources.library_recent
import com.mooscles.screens.resources.library_search
import com.mooscles.screens.resources.library_settings
import com.mooscles.screens.resources.library_title
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The tag of the library route's root. */
const val LibraryScreenTag = "library-screen"

/** The test tags of the library (Task 15) and of the filters the picker reuses (Task 18). */
object LibraryTags {
    const val SEARCH = "library-search"
    const val LIST = "library-list"
    const val EQUIPMENT_CHIP = "library-equipment-chip"
    const val EQUIPMENT_SHEET = "library-equipment-sheet"
    const val MINE_CHIP = "library-mine-chip"
    const val CLEAR_FILTERS = "library-clear-filters"
    const val COUNT = "library-count"
    const val CREATE = "library-create"
    const val SETTINGS = "library-settings"
    const val SKELETON_ROW = "library-skeleton-row"
    const val EMPTY = "library-empty"
    const val RECENT_HEADER = "library-header-recent"
    const val ALL_HEADER = "library-header-all"

    /** A row's name text (unmerged tree). */
    const val ROW_NAME = "library-name"

    /** A custom row's "Custom" tag (unmerged tree). */
    const val ROW_CUSTOM = "library-custom-tag"

    /** The checkbox of a selectable row (the picker's multiple mode; unmerged tree). */
    const val ROW_CHECKBOX = "library-row-checkbox"

    /** The row of [exerciseId] in "All" or in the results. */
    fun row(exerciseId: String) = "library-row-$exerciseId"

    /** The row of [exerciseId] in "Recently used" (the same exercise is also in "All"). */
    fun recentRow(exerciseId: String) = "library-recent-$exerciseId"

    /** The chip of the muscle group whose English label is [label] ("Chest"). */
    fun muscleChip(label: String) = "library-chip-$label"

    /** The sheet's chip of the equipment whose English label is [label] ("Barbell"). */
    fun equipmentOption(label: String) = "library-equipment-option-$label"
}

/**
 * The seams of [LibraryRoute] for tests (pre-flight F19): the model [cache] and [build], which a
 * test holds to set "before the index is ready"; and the row [media].
 */
internal class LibrarySeams(
    val cache: LibraryModelCache = LibraryModelCache.process,
    val build: suspend (List<ExerciseUi>, Boolean) -> LibraryModel = ::buildOffTheMainThread,
    val media: (ExerciseUi) -> ExerciseMedia = ::exerciseMedia,
) {
    companion object {
        val Default = LibrarySeams()
    }
}

/**
 * The library tab (spec "Biblioteca (aba)"): "Library" with the Settings shortcut ([onOpenSettings])
 * and "Create exercise"; the search field, fixed under the top bar; and, scrolling under it, the
 * filters ([LibraryFilterBar], the equipment in [EquipmentFilterSheet]), the count ("214
 * exercises", announced politely when it changes) and the list. Without a search or a filter the
 * list is "Recently used" (up to 8, [LibraryModel.recents]) and then "All"; with either, only the
 * results, in alphabetical order of the shown name. Until the model is built ([rememberLibraryModel])
 * the list shows skeleton rows, while the search and the filters already take input, which applies
 * when the model arrives. While [UiState.loading], and while the only model is one of an empty list
 * that the state has since filled, the list is not built yet either: an empty catalog is never shown
 * for a list that is still on its way. Nothing found shows an empty state with "Create “…”" from the search and
 * "Clear filters".
 *
 * A row opens [onOpenExercise] with its id, only while the exercise is still in [UiState.exercises]
 * (for the moment a new list is being built, the rows can be the previous list's). The search, the
 * filters, the open equipment sheet and the scroll are kept with `rememberSaveable`, so they
 * survive going to the detail and back, and recreation. Errors show in the app's language over the
 * screen ([ScreenErrorToast], plan decision 14), never [UiState.error].
 *
 * "Create exercise", and "Create “…”" with the search as the name, open [CustomExerciseForm] over
 * the library. The form stays open and busy until the state brings the new exercise (plan decision 13,
 * pre-flight F10), and then the detail opens ([onOpenExercise]) with the form still over the library, which leaves
 * with the library (or after `timing.formHandoff`), so no library frame shows between them; a failed save keeps the
 * form open, with the error shown over it. The open form and its draft are
 * kept with `rememberSaveable` too, each opening in a saved state of its own that ends with it (a saved or cancelled
 * form never comes back in the next one); the wait is not saved (the form comes back not busy).
 *
 * Opening the library also reads ahead, off the main thread, what the exercise detail draws first:
 * the body map's paths ([prepareBodyMap]) and the cues of the screen's language.
 */
@Composable
fun LibraryRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenExercise: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = LibraryRoute(state, actions, onOpenExercise, onOpenSettings, modifier, LibrarySeams.Default)

/** [LibraryRoute] with its [seams]. */
@Composable
internal fun LibraryRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenExercise: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    seams: LibrarySeams,
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var filters by rememberSaveable(stateSaver = LibraryFilters.Saver) { mutableStateOf(LibraryFilters()) }
    var equipmentOpen by rememberSaveable { mutableStateOf(false) }
    // The name the open form started with; null while it is closed.
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    // Each opening of the form is a session with a saved state of its own, dropped when the session ends (saved or
    // cancelled): a form handed over to the detail is still composed when the library leaves, and its draft and id
    // would otherwise be saved with the library and come back in the next form.
    var formSession by rememberSaveable { mutableIntStateOf(0) }
    val formStates = rememberSaveableStateHolder()
    val host = remember { MToastHostState() }
    val listState = rememberLazyListState()
    val equipmentFocus = remember { FocusRequester() }
    var focusReturns by remember { mutableIntStateOf(0) }
    val portuguese = catalogInPortuguese()
    val built = rememberLibraryModel(state.exercises, portuguese, seams.cache, seams.build)
    // The loading state's empty list builds a model too; it stands for nothing, so the skeleton stays.
    val model = built?.takeIf { !state.loading && !(it.exercises.isEmpty() && state.exercises.isNotEmpty()) }
    val currentExercises by rememberUpdatedState(state.exercises)
    val openExercise by rememberUpdatedState(onOpenExercise)
    val open: (String) -> Unit = remember {
        { id -> if (currentExercises.any { it.id == id }) openExercise(id) }
    }
    val create: (String) -> Unit = remember {
        { prefill ->
            formSession++
            form = prefill
        }
    }

    // The detail's body map and cues, read ahead off the main thread: the first map of a fresh
    // process would otherwise draw empty for about 0.1 s, and the cues would arrive a frame late.
    LaunchedEffect(portuguese) {
        prepareBodyMap()
        ExerciseCues.load(portuguese)
    }
    // A new search or filter starts the list from the top; a recreation keeps the scroll it had.
    var resultsShown by remember { mutableStateOf(false) }
    LaunchedEffect(query.text, filters) {
        if (resultsShown) listState.scrollToItem(0) else resultsShown = true
    }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0) return@LaunchedEffect
        // Once the chip is back in the layout. It is the list's first item, composed while the
        // sheet covers the screen; were it scrolled away, there is no chip to give the focus to.
        withFrameNanos { }
        runCatching { equipmentFocus.requestFocus() }
    }

    Box(modifier.fillMaxSize().testTag(LibraryScreenTag).background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(
                title = stringResource(Res.string.library_title),
                actions = {
                    MIconButton(
                        MIcons.Settings,
                        contentDescription = stringResource(Res.string.library_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(LibraryTags.SETTINGS),
                    )
                    MIconButton(
                        MIcons.Plus,
                        contentDescription = stringResource(Res.string.library_create),
                        onClick = { create("") },
                        modifier = Modifier.testTag(LibraryTags.CREATE),
                    )
                },
            )
            Box(Modifier.fillMaxWidth().testTag(LibraryTags.SEARCH).padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2)) {
                MSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.library_search),
                    onClear = { query = TextFieldValue() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LibraryList(
                model = model,
                query = query.text,
                filters = filters,
                history = state.history,
                listState = listState,
                media = seams.media,
                onFilters = { filters = it },
                onOpenEquipment = { equipmentOpen = true },
                equipmentFocus = equipmentFocus,
                onOpen = open,
                onCreate = create,
                modifier = Modifier.weight(1f),
            )
        }
        // While the form is open it shows the error of its own save over itself; this one waits for it to close.
        ScreenErrorToast(state.errorCode.takeIf { form == null }, actions, host)
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }
    // A saved form is handed over to the detail: it stays over the library (busy, as it was) while the host moves to the
    // detail, and leaves with the library, so the library is never seen between them. Not saved: a recreation drops it.
    // The cap frees a host that stays.
    var handingOver by remember { mutableStateOf<String?>(null) }
    val handoff = MTheme.timing.formHandoff
    LaunchedEffect(handingOver) {
        if (handingOver != null) {
            delay(handoff)
            handingOver = null
        }
    }
    (form ?: handingOver)?.let { prefill ->
        val session = formSession
        formStates.SaveableStateProvider(session) {
            CustomExerciseForm(
                initial = null,
                prefillName = prefill,
                state = state,
                actions = actions,
                // The form closes once the state has the new exercise (it waits for it), so the detail can open at once.
                // The session ends here: the handed-over form stays composed, but nothing of it is saved any more.
                onSaved = { id ->
                    formStates.removeState(session)
                    handingOver = prefill
                    form = null
                    open(id)
                },
                onCancel = {
                    formStates.removeState(session)
                    form = null
                },
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

/** The library's scrolling part: the filters, the count and the rows, or what stands in for them. */
@Composable
private fun LibraryList(
    model: LibraryModel?,
    query: String,
    filters: LibraryFilters,
    history: List<WorkoutUi>,
    listState: LazyListState,
    media: (ExerciseUi) -> ExerciseMedia,
    onFilters: (LibraryFilters) -> Unit,
    onOpenEquipment: () -> Unit,
    equipmentFocus: FocusRequester,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searching = query.isNotBlank() || filters.active
    val results = remember(model, query, filters) { model?.results(query, filters).orEmpty() }
    val recents = remember(model, history) { model?.recents(history).orEmpty() }
    val count = if (searching) results.size else model?.exercises?.size ?: 0
    val countText = pluralCount(Res.plurals.library_count, count, count)
    val recentTitle = stringResource(Res.string.library_recent)
    val allTitle = stringResource(Res.string.library_all)
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier.fillMaxWidth().testTag(LibraryTags.LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = bottom + MTheme.space.s6),
    ) {
        item(key = "filters") {
            LibraryFilterBar(filters, onFilters, onOpenEquipment, equipmentFocus, Modifier.padding(top = MTheme.space.s1))
        }
        if (model == null) {
            items(SKELETON_ROWS, key = { "skeleton-$it" }) { LibrarySkeletonRow() }
            return@LazyColumn
        }
        item(key = "count") {
            MText(
                countText,
                MTheme.type.caption,
                color = MTheme.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LibraryTags.COUNT)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
            )
        }
        when {
            !searching && model.exercises.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = MIcons.Dumbbell,
                    title = stringResource(Res.string.library_catalog_empty),
                    body = stringResource(Res.string.library_catalog_empty_body),
                    modifier = Modifier.testTag(LibraryTags.EMPTY),
                    primaryAction = stringResource(Res.string.library_create) to { onCreate("") },
                )
            }
            !searching -> {
                if (recents.isNotEmpty()) {
                    item(key = "recent-header") { SectionHeader(recentTitle, Modifier.testTag(LibraryTags.RECENT_HEADER)) }
                    items(recents, key = { "recent-${it.id}" }) { exercise ->
                        LibraryRow(exercise, model.shownName(exercise), rowMedia(exercise, media), LibraryTags.recentRow(exercise.id), { onOpen(exercise.id) })
                    }
                }
                item(key = "all-header") { SectionHeader(allTitle, Modifier.testTag(LibraryTags.ALL_HEADER)) }
                items(model.exercises, key = { "row-${it.id}" }) { exercise ->
                    LibraryRow(exercise, model.shownName(exercise), rowMedia(exercise, media), LibraryTags.row(exercise.id), { onOpen(exercise.id) })
                }
            }
            results.isEmpty() -> item(key = "empty") { NoResults(query, filters, onFilters, onCreate) }
            else -> items(results, key = { "row-${it.id}" }) { exercise ->
                LibraryRow(exercise, model.shownName(exercise), rowMedia(exercise, media), LibraryTags.row(exercise.id), { onOpen(exercise.id) })
            }
        }
    }
}

/**
 * Nothing matches: "No exercises found", with "Create “…”" (the search as the new exercise's name)
 * when there is a search, and "Clear filters" when a filter is on.
 */
@Composable
private fun NoResults(query: String, filters: LibraryFilters, onFilters: (LibraryFilters) -> Unit, onCreate: (String) -> Unit) {
    val name = query.trim()
    val create = if (name.isNotEmpty()) stringResource(Res.string.library_create_named, name) to { onCreate(name) } else null
    val clear = if (filters.active) stringResource(Res.string.library_clear_filters) to { onFilters(LibraryFilters()) } else null
    EmptyState(
        icon = MIcons.SearchX,
        title = stringResource(Res.string.library_empty),
        body = stringResource(Res.string.library_empty_body),
        modifier = Modifier.testTag(LibraryTags.EMPTY),
        primaryAction = create ?: clear,
        secondaryAction = if (create != null) clear else null,
    )
}

/** How many skeleton rows stand in for the list while it is built. */
private const val SKELETON_ROWS = 6

/** The media of a row: a custom exercise's photo once it is ready, else what [media] gives. */
@Composable
private fun rowMedia(exercise: ExerciseUi, media: (ExerciseUi) -> ExerciseMedia): ExerciseMedia =
    rememberCustomPhotoMedia(exercise, thumbnail = true) ?: remember(exercise, media) { media(exercise) }
