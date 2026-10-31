package com.mooscles.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import com.mooscles.screens.exercise.photo.rememberStoredPhoto
import com.mooscles.screens.exercise.photo.PhotoSource
import com.mooscles.screens.exercise.photo.LocalExercisePhotos
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.data.Anatomy
import com.mooscles.data.ExerciseAnatomy
import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MInfoChip
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastKind
import com.mooscles.designsystem.components.MToastResult
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.charts.MSparkline
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.screens.progress.ProgressLoad
import com.mooscles.screens.progress.SeriesKey
import com.mooscles.screens.progress.SparklineQuery
import com.mooscles.screens.progress.rememberProgressIndex
import com.mooscles.screens.progress.rememberProgressQuery
import com.mooscles.screens.progress.valueOrNull
import com.mooscles.designsystem.illustration.AnimatedExerciseFigure
import com.mooscles.designsystem.illustration.BodyMap
import com.mooscles.designsystem.illustration.BodyMapLegend
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.figures.ExerciseFigures
import com.mooscles.screens.exercise.figures.FigureHighlights
import com.mooscles.screens.format.anatomySentence
import com.mooscles.screens.format.conventionCaption
import com.mooscles.screens.format.conventionLabel
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.exercise_add_routine
import com.mooscles.screens.resources.exercise_add_workout
import com.mooscles.screens.resources.exercise_added_to
import com.mooscles.screens.resources.exercise_added_to_workout
import com.mooscles.screens.resources.exercise_back_to_library
import com.mooscles.screens.resources.exercise_edit
import com.mooscles.screens.resources.exercise_history
import com.mooscles.screens.resources.exercise_history_best_label
import com.mooscles.screens.resources.exercise_history_full
import com.mooscles.screens.resources.exercise_history_last
import com.mooscles.screens.resources.exercise_history_last_label
import com.mooscles.screens.resources.exercise_history_none
import com.mooscles.screens.resources.exercise_history_sessions
import com.mooscles.screens.resources.exercise_how_to
import com.mooscles.screens.resources.exercise_more
import com.mooscles.screens.resources.exercise_muscles
import com.mooscles.screens.resources.exercise_not_found
import com.mooscles.screens.resources.exercise_not_found_body
import com.mooscles.screens.resources.exercise_open
import com.mooscles.screens.resources.exercise_photo_failed
import com.mooscles.screens.resources.exercise_photo_label
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.screens.workout.MenuItem
import com.mooscles.screens.workout.RecompositionProbe
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.bestSetText
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The tag of the exercise detail route's root. */
const val ExerciseInfoScreenTag = "exercise-info-screen"

/** The test tags of [ExerciseInfoRoute]. */
internal object ExerciseInfoTags {
    const val TITLE = "exercise-info-title"
    const val MENU = "exercise-info-menu"
    const val BODY = "exercise-info-body"
    const val MEDIA = "exercise-info-media"
    const val PHOTO = "exercise-info-photo"
    const val META = "exercise-info-meta"
    const val CAPTION = "exercise-info-caption"
    const val MUSCLES = "exercise-info-muscles"
    const val MUSCLE_TEXT = "exercise-info-muscle-text"
    const val CUES = "exercise-info-cues"
    const val HISTORY = "exercise-info-history"
    const val HISTORY_NONE = "exercise-info-history-none"
    const val HISTORY_LAST = "exercise-info-history-last"
    const val HISTORY_BEST = "exercise-info-history-best"
    const val HISTORY_SESSIONS = "exercise-info-history-sessions"
    const val HISTORY_FULL = "exercise-info-history-full"
    const val HISTORY_SPARKLINE = "exercise-history-sparkline"
    const val ADD_ROUTINE = "exercise-info-add-routine"
    const val ADD_WORKOUT = "exercise-info-add-workout"
    const val MENU_EDIT = "exercise-info-menu-edit"
    const val MENU_HISTORY = "exercise-info-menu-history"
    const val NOT_FOUND = "exercise-info-not-found"
    const val LOADING = "exercise-info-loading"

    /** The [index]th cue (from 0). */
    fun cue(index: Int) = "exercise-info-cue-$index"
}

/** The keys the detail reports to `RecompositionProbe`: the media slot and the rest of the body. */
internal object ExerciseInfoProbe {
    const val FIGURE = "figure"
    const val BODY = "info-body"
}

/**
 * An add that waits for its echo: the routine (or workout) [targetId] held [before] entries of the
 * exercise when it was asked for, and [failures] commands about it had failed (pre-flight F10: the wait ends when
 * that count grows). Never saved: after process death the command is lost and the count starts again, so a
 * restored wait would never end.
 */
internal data class PendingAdd(val targetId: String, val before: Int, val failures: Int)

/**
 * The exercise detail (spec "Detalhe do exercício", route `exerciseInfo/{id}`), from the top: back
 * and the menu ("Edit exercise" for a custom exercise, "View progress") in the fixed bar; then,
 * scrolling, the name (`title.md`, a heading, whole at any length: in the fixed bar a long custom
 * name at a large text size would push the rest off the screen); the media,
 * a 1:1 frame as wide as the content up to `sizes.mediaDetailMax`, centered, with the alternating
 * figure (spec D5; it stops while not [foreground], with reduced motion and off screen) or the
 * equipment's icon; the equipment, the muscle group and the logging convention as read-only chips,
 * with the convention's one-line caption when it is not the total load; the body map with its
 * legend and its text equivalent under it; the numbered cues ("How to do it"), when there are any;
 * "Your history" ([ExerciseHistorySummaries]: the last time, the best set or the record, the
 * sessions), with "View progress" ([onOpenHistory], the old screen); and "Add to routine"
 * ([AddToRoutineSheet]) and, with a workout in progress, "Add to workout".
 *
 * "Added to …" (with "Open": [onOpenRoutine] or [onOpenWorkout]) shows only once the state brings
 * the routine or the workout with the exercise once more (the echo), never on the tap; a failure
 * of that add (`UiState.subjectFailures` of the routine or the workout grows) drops the wait and its own
 * toast shows instead ([ScreenErrorToast], plan decision 14, never `UiState.error`); an error about anything
 * else changes nothing. An exercise that is not in the state (any more: a backup
 * import, say) shows "Exercise not found" with "Back to the library" ([onBack]). The open sheets,
 * the edit form is kept with `rememberSaveable`; the waits are not (a recreation drops them).
 *
 * [foreground] comes from the host's lifecycle (plan decision 15): the common code reads no
 * platform state of its own.
 */
@Composable
fun ExerciseInfoRoute(
    exerciseId: String,
    state: UiState,
    actions: MoosclesActions,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    foreground: Boolean = true,
) {
    val exercise = state.exercises.firstOrNull { it.id == exerciseId }
    val host = remember { MToastHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var routineSheet by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<PendingAdd?>(null) }
    var pendingWorkout by remember { mutableStateOf<PendingAdd?>(null) }
    val openRoutine by rememberUpdatedState(onOpenRoutine)
    val openWorkout by rememberUpdatedState(onOpenWorkout)
    val openLabel = stringResource(Res.string.exercise_open)
    val routineText = pendingRoutine?.let { pending ->
        state.routines.firstOrNull { it.id == pending.targetId }?.let { stringResource(Res.string.exercise_added_to, it.name) }
    }
    val workoutText = stringResource(Res.string.exercise_added_to_workout)
    val photoFailedText = stringResource(Res.string.exercise_photo_failed)

    // The error toast of a failed add (an error toast belongs to the screen, never to a wait). A failure about the
    // routine or the workout asked for ends its wait; any other error leaves the waits alone (pre-flight F10).
    // While the edit form is open it shows the error of its own save, and this one waits for it to close.
    ScreenErrorToast(state.errorCode.takeIf { !editing }, actions, host)
    // The photo of this exercise that its save could not keep (the form noted it just before it closed): shown here, on
    // the screen the person lands on after a creation and stays on after an edit.
    val photos = LocalExercisePhotos.current
    val photoFailures by remember(photos) { photos?.failedCommits ?: MutableStateFlow(emptySet()) }.collectAsState()
    LaunchedEffect(photoFailures, exerciseId) {
        if (photos?.takeCommitFailure(exerciseId) == true) scope.launch { host.show(photoFailedText, MToastKind.Error) }
    }
    LaunchedEffect(state.subjectFailures) {
        pendingRoutine?.let { if ((state.subjectFailures[it.targetId] ?: 0) > it.failures) pendingRoutine = null }
        pendingWorkout?.let { if ((state.subjectFailures[it.targetId] ?: 0) > it.failures) pendingWorkout = null }
    }
    LaunchedEffect(pendingRoutine, state.routines) {
        val pending = pendingRoutine ?: return@LaunchedEffect
        val routine = state.routines.firstOrNull { it.id == pending.targetId } ?: return@LaunchedEffect
        if (routine.exercises.count { it.exercise.id == exerciseId } <= pending.before) return@LaunchedEffect
        pendingRoutine = null
        val text = routineText ?: return@LaunchedEffect
        scope.launch {
            if (host.show(text, MToastKind.Info, openLabel) == MToastResult.ActionPerformed) openRoutine(routine.id)
        }
    }
    LaunchedEffect(pendingWorkout, state.active) {
        val pending = pendingWorkout ?: return@LaunchedEffect
        val workout = state.active
        if (workout == null || workout.id != pending.targetId) {
            // The workout ended meanwhile: nothing to open any more.
            pendingWorkout = null
            return@LaunchedEffect
        }
        if (workout.exercises.count { it.exercise.id == exerciseId } <= pending.before) return@LaunchedEffect
        pendingWorkout = null
        scope.launch {
            if (host.show(workoutText, MToastKind.Info, openLabel) == MToastResult.ActionPerformed) openWorkout()
        }
    }

    Box(modifier.fillMaxSize().testTag(ExerciseInfoScreenTag).background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            // The name is the scrolling column's first line, not the bar's title: a long custom name
            // at a large text size would otherwise grow the fixed bar past the screen.
            TopBar(title = null, onBack = onBack) {
                if (exercise != null) {
                    MIconButton(
                        MIcons.EllipsisVertical,
                        stringResource(Res.string.exercise_more),
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(ExerciseInfoTags.MENU),
                    )
                }
            }
            when {
                exercise != null -> InfoBody(
                    exercise = exercise,
                    state = state,
                    foreground = foreground,
                    onOpenHistory = { onOpenHistory(exercise.id) },
                    onAddRoutine = { routineSheet = true },
                    onAddWorkout = state.active?.let { active ->
                        {
                            if (pendingWorkout == null) {
                                pendingWorkout = PendingAdd(active.id, active.exercises.count { it.exercise.id == exerciseId }, state.subjectFailures[active.id] ?: 0)
                                actions.addWorkoutExercise(active.id, exerciseId)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                state.loading -> InfoLoading(Modifier.weight(1f))
                else -> EmptyState(
                    icon = MIcons.SearchX,
                    title = stringResource(Res.string.exercise_not_found),
                    body = stringResource(Res.string.exercise_not_found_body),
                    modifier = Modifier.testTag(ExerciseInfoTags.NOT_FOUND),
                    primaryAction = stringResource(Res.string.exercise_back_to_library) to onBack,
                )
            }
        }
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }

    if (exercise == null) {
        // The sheets and the form belong to an exercise that is gone: they do not come back with it.
        // Only once the state is loaded, so a restore after process death keeps an open edit form.
        if (!state.loading) {
            LaunchedEffect(Unit) {
                menuOpen = false
                routineSheet = false
                editing = false
            }
        }
        return
    }
    if (menuOpen) {
        InfoMenuSheet(
            exercise = exercise,
            onEdit = {
                menuOpen = false
                editing = true
            },
            onHistory = {
                menuOpen = false
                onOpenHistory(exercise.id)
            },
            onDismiss = { menuOpen = false },
        )
    }
    if (routineSheet) {
        AddToRoutineSheet(
            exerciseId = exerciseId,
            state = state,
            actions = actions,
            onAdded = { routineId, _ ->
                val routine = state.routines.firstOrNull { it.id == routineId }
                pendingRoutine = PendingAdd(routineId, routine?.exercises?.count { it.exercise.id == exerciseId } ?: 0, state.subjectFailures[routineId] ?: 0)
                routineSheet = false
            },
            onDismiss = { routineSheet = false },
        )
    }
    if (editing && exercise.custom) {
        CustomExerciseForm(
            initial = exercise,
            prefillName = "",
            state = state,
            actions = actions,
            onSaved = { editing = false },
            onCancel = { editing = false },
        )
    }
}

/** The detail's scrolling part, from the media to the actions. */
@Composable
private fun InfoBody(
    exercise: ExerciseUi,
    state: UiState,
    foreground: Boolean,
    onOpenHistory: () -> Unit,
    onAddRoutine: () -> Unit,
    onAddWorkout: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = exerciseName(exercise)
    val anatomy = remember(exercise.id, exercise.muscle) { ExerciseAnatomy.forExercise(exercise.id, exercise.muscle) }
    val cues = rememberExerciseCues(exercise.id)
    val summary = remember(exercise.id, state.history, state.records) {
        ExerciseHistorySummaries.of(exercise.id, state.history, state.records)
    }
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val space = MTheme.space
    Column(
        modifier
            .fillMaxWidth()
            .testTag(ExerciseInfoTags.BODY)
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottom + space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecompositionProbe(ExerciseInfoProbe.BODY)
        Column(
            Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s6),
        ) {
            // The name heads the content (`title.md`, a heading): whole at any length, since it scrolls.
            MText(
                name,
                MTheme.type.titleMd,
                color = MTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth().testTag(ExerciseInfoTags.TITLE).semantics { heading() },
            )
            InfoMedia(exercise, name, anatomy?.primary.orEmpty(), foreground)
            InfoMeta(exercise)
            InfoMuscles(anatomy)
            if (!cues.isNullOrEmpty()) InfoCues(cues)
            InfoHistory(exercise, summary, state, onOpenHistory)
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                MButton(
                    stringResource(Res.string.exercise_add_routine),
                    onClick = onAddRoutine,
                    modifier = Modifier.fillMaxWidth().testTag(ExerciseInfoTags.ADD_ROUTINE),
                    variant = MButtonVariant.Secondary,
                    size = MButtonSize.L,
                )
                if (onAddWorkout != null) {
                    MButton(
                        stringResource(Res.string.exercise_add_workout),
                        onClick = onAddWorkout,
                        modifier = Modifier.fillMaxWidth().testTag(ExerciseInfoTags.ADD_WORKOUT),
                        variant = MButtonVariant.Secondary,
                        size = MButtonSize.L,
                    )
                }
            }
        }
    }
}

/**
 * The media frame: square, as wide as the content up to `sizes.mediaDetailMax`, centered; the
 * figure alternating, or the equipment's icon for an exercise without one (spec D5). Its own
 * composable, so the figure's frames recompose only in here.
 */
@Composable
private fun InfoMedia(exercise: ExerciseUi, name: String, primary: List<MuscleRegion>, foreground: Boolean) {
    RecompositionProbe(ExerciseInfoProbe.FIGURE)
    val frames = remember(exercise.id, exercise.custom) { if (exercise.custom) null else ExerciseFigures.forExercise(exercise.id) }
    val highlights = remember(primary) { FigureHighlights.forRegions(primary) }
    // The photo of a custom exercise, full size, once it is read and decoded; without one the equipment's icon.
    val photo = if (exercise.custom) rememberStoredPhoto(LocalExercisePhotos.current, PhotoSource.Exercise(exercise.id), thumbnail = false) else null
    val photoLabel = stringResource(Res.string.exercise_photo_label, name)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = MTheme.sizes.mediaDetailMax)
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag(ExerciseInfoTags.MEDIA)
                .background(MTheme.colors.bgInset, RoundedCornerShape(MTheme.radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> Image(
                    BitmapPainter(photo),
                    contentDescription = photoLabel,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(MTheme.radius.lg)).testTag(ExerciseInfoTags.PHOTO),
                    contentScale = ContentScale.Crop,
                )
                frames != null -> AnimatedExerciseFigure(
                    frames.start, frames.end, highlights, name,
                    Modifier.fillMaxSize().padding(MTheme.space.s4),
                    foreground = foreground,
                )
                else -> MIcon(
                    EquipmentIcons.forEquipment(exercise.equipment), contentDescription = null,
                    modifier = Modifier.testTag(ExerciseEquipmentIconTag),
                    size = MTheme.sizes.media, tint = MTheme.colors.textTertiary,
                )
            }
        }
    }
}

/** The equipment, the muscle group and the convention as read-only chips, and the convention's caption. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoMeta(exercise: ExerciseUi) {
    val caption = conventionCaption(exercise.convention)
    Column(Modifier.fillMaxWidth().testTag(ExerciseInfoTags.META), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        ) {
            MInfoChip(equipmentLabel(exercise.equipment), icon = EquipmentIcons.forEquipment(exercise.equipment))
            MInfoChip(muscleLabel(exercise.muscle))
            MInfoChip(conventionLabel(exercise.convention))
        }
        if (caption != null) {
            MText(caption, MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.testTag(ExerciseInfoTags.CAPTION))
        }
    }
}

/**
 * A section's title ("Muscles", "How to do it", "Your history"): the design system's section header
 * (`title.sm`, `text.secondary`, a heading, `space.4` above and `space.2` below) without its own
 * 16 dp sides, since the detail's content column already has them; the title starts where the
 * name, the chips and the cards start.
 */
@Composable
private fun InfoSectionTitle(text: String) {
    MText(
        text,
        MTheme.type.titleSm,
        color = MTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(top = MTheme.space.s4, bottom = MTheme.space.s2).semantics { heading() },
    )
}

/** "Muscles": the body map (an image the screen reader skips), its legend and the text equivalent under it. */
@Composable
private fun InfoMuscles(anatomy: Anatomy?) {
    val primary = remember(anatomy) { anatomy?.primary.orEmpty().mapTo(LinkedHashSet()) { it.toBodyRegion() } }
    val secondary = remember(anatomy) { anatomy?.secondary.orEmpty().mapTo(LinkedHashSet()) { it.toBodyRegion() } }
    Column(Modifier.fillMaxWidth().testTag(ExerciseInfoTags.MUSCLES), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        InfoSectionTitle(stringResource(Res.string.exercise_muscles))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BodyMap(primary, secondary, Modifier.widthIn(max = MTheme.sizes.mediaDetailMax).fillMaxWidth())
        }
        BodyMapLegend()
        MText(
            anatomySentence(anatomy),
            MTheme.type.bodyLg,
            color = MTheme.colors.textPrimary,
            modifier = Modifier.fillMaxWidth().testTag(ExerciseInfoTags.MUSCLE_TEXT),
        )
    }
}

/** "How to do it": the cues, numbered, each one read as one node ("1 Keep …"). */
@Composable
private fun InfoCues(cues: List<String>) {
    val locale = currentNumberLocale()
    Column(Modifier.fillMaxWidth().testTag(ExerciseInfoTags.CUES), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        InfoSectionTitle(stringResource(Res.string.exercise_how_to))
        cues.forEachIndexed { index, cue ->
            Row(
                Modifier.fillMaxWidth().testTag(ExerciseInfoTags.cue(index)).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
            ) {
                MText(MoosclesNumbers.localize((index + 1).toString(), locale), MTheme.type.numSm, color = MTheme.colors.accentText)
                MText(cue, MTheme.type.bodyLg, color = MTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * "Your history": a card with the last time ("Oct 21 · 80 kg × 8"), the best set (with the record
 * badge when it is the weight record) and the sessions, or "You haven't done this exercise yet.";
 * then "View progress".
 */
@Composable
private fun InfoHistory(exercise: ExerciseUi, summary: HistorySummary, state: UiState, onOpenHistory: () -> Unit) {
    val pounds = state.preferences.pounds
    // The sparkline needs the progress index; while it loads (or if it failed) the card shows none, never a skeleton.
    // No index of the loading state's empty lists: it would become the cache's latest history until the real one.
    val load = if (state.loading) ProgressLoad.Loading else rememberProgressIndex(state.history, state.records)
    val spark = rememberProgressQuery(load, SparklineQuery(SeriesKey(exercise.id, exercise.convention))).valueOrNull
    val space = MTheme.space
    val colors = MTheme.colors
    val shape = RoundedCornerShape(MTheme.radius.lg)
    Column(Modifier.fillMaxWidth().testTag(ExerciseInfoTags.HISTORY), verticalArrangement = Arrangement.spacedBy(space.s3)) {
        InfoSectionTitle(stringResource(Res.string.exercise_history))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bgSurface, shape)
                .border(MTheme.sizes.border, colors.borderSubtle, shape)
                .padding(space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (summary.sessions == 0) {
                MText(
                    stringResource(Res.string.exercise_history_none),
                    MTheme.type.bodyLg,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag(ExerciseInfoTags.HISTORY_NONE),
                )
                return@Column
            }
            val lastDate = summary.lastDate?.let { shortDate(it) }
            val lastSet = setText(summary.lastWeightKg, summary.lastReps, pounds)
            val last = when {
                lastDate != null && lastSet != null -> stringResource(Res.string.exercise_history_last, lastDate, lastSet)
                else -> lastDate
            }
            if (last != null) HistoryLine(stringResource(Res.string.exercise_history_last_label), last, ExerciseInfoTags.HISTORY_LAST)
            val best = setText(summary.bestWeightKg, summary.bestReps, pounds)
            if (best != null) {
                HistoryLine(stringResource(Res.string.exercise_history_best_label), best, ExerciseInfoTags.HISTORY_BEST, record = summary.bestIsRecord)
            }
            MText(
                pluralCount(Res.plurals.exercise_history_sessions, summary.sessions, summary.sessions),
                MTheme.type.bodyMd,
                color = colors.textSecondary,
                modifier = Modifier.testTag(ExerciseInfoTags.HISTORY_SESSIONS),
            )
            if (spark != null && spark.size >= 2) {
                Box(Modifier.fillMaxWidth().testTag(ExerciseInfoTags.HISTORY_SPARKLINE)) { MSparkline(spark) }
            }
        }
        // Without a session there is nothing more to see there (the menu keeps the entry).
        if (summary.sessions > 0) {
            MButton(
                stringResource(Res.string.exercise_history_full),
                onClick = onOpenHistory,
                modifier = Modifier.testTag(ExerciseInfoTags.HISTORY_FULL),
                variant = MButtonVariant.Text,
            )
        }
    }
}

/** One line of the history card: its [label] over its [value], read as one node; the record badge after the value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryLine(label: String, value: String, tag: String, record: Boolean = false) {
    Column(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}) {
        MText(label, MTheme.type.caption, color = MTheme.colors.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            MText(value, MTheme.type.numSm, color = MTheme.colors.textPrimary)
            if (record) PrBadge()
        }
    }
}

/** "80 kg × 8", "Peso corporal × 12" for no load ([bestSetText], as the summary); null without a set. */
@Composable
private fun setText(weightKg: Double?, reps: Int?, pounds: Boolean): String? =
    if (weightKg == null || reps == null) null else bestSetText(weightKg, reps, pounds)

/** While the state loads: the media frame and two lines, as skeletons. */
@Composable
private fun InfoLoading(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag(ExerciseInfoTags.LOADING).padding(horizontal = MTheme.space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        Skeleton(
            Modifier.widthIn(max = MTheme.sizes.mediaDetailMax).fillMaxWidth().aspectRatio(1f),
            RoundedCornerShape(MTheme.radius.lg),
        )
        Skeleton(Modifier.fillMaxWidth().height(MTheme.space.s4))
        Skeleton(Modifier.fillMaxWidth().height(MTheme.space.s4))
        Spacer(Modifier.height(MTheme.space.s4))
    }
}

/** The detail's menu, a bottom sheet under the exercise's name: "Edit exercise" (custom only) and "View progress". */
@Composable
private fun InfoMenuSheet(exercise: ExerciseUi, onEdit: () -> Unit, onHistory: () -> Unit, onDismiss: () -> Unit) {
    MBottomSheet(onDismiss = onDismiss) {
        MText(
            exerciseName(exercise),
            MTheme.type.titleMd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MTheme.space.s4)
                .padding(bottom = MTheme.space.s2)
                .semantics { heading() },
        )
        if (exercise.custom) {
            MenuItem(MIcons.Pencil, stringResource(Res.string.exercise_edit), ExerciseInfoTags.MENU_EDIT, onClick = onEdit)
        }
        MenuItem(MIcons.ChartLine, stringResource(Res.string.exercise_history_full), ExerciseInfoTags.MENU_HISTORY, onClick = onHistory)
        Spacer(Modifier.height(MTheme.space.s4))
    }
}
