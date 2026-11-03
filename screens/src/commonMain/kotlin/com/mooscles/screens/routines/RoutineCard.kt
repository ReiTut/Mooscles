package com.mooscles.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderHandle
import com.mooscles.designsystem.interaction.ReorderState
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.photo.rememberExerciseMedia
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.routines_last_date
import com.mooscles.screens.resources.routines_last_days
import com.mooscles.screens.resources.routines_last_today
import com.mooscles.screens.resources.routines_last_yesterday
import com.mooscles.screens.resources.routines_more
import com.mooscles.screens.resources.routines_never
import com.mooscles.screens.resources.routines_open
import com.mooscles.screens.resources.routines_exercise_count
import com.mooscles.screens.resources.routines_resume
import com.mooscles.screens.resources.routines_set_count
import com.mooscles.screens.resources.routines_start
import com.mooscles.screens.resources.routines_summary
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RoutineUi
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/** How many exercises the card's strip shows. */
internal const val STRIP_THUMBNAILS = 4

/**
 * A routine card (spec "Treinar (aba)"): a surface with, in a row, the drag handle ([reorder], 48 dp), the
 * routine's name and summary and the options button; under it the strip of the first four exercises' media and a
 * full-width, 56 dp "Start" (or "Resume workout" when [resumes]: this routine is the one of the workout under way).
 *
 * The name, the "5 exercises · 17 sets" summary, the muscles and the last time are one node for a screen reader
 * that opens the editor ([onOpen]); the handle (which carries the four move actions), the options button and the
 * start button are stops of their own. Put [Modifier.reorderItem] on [modifier].
 */
@Composable
internal fun RoutineCard(
    routine: RoutineUi,
    reorder: ReorderState<String>,
    resumes: Boolean,
    startEnabled: Boolean,
    nowMillis: Long,
    zone: TimeZone,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MTheme.colors
    val space = MTheme.space
    val shape = RoundedCornerShape(MTheme.radius.lg)
    Column(
        modifier
            .testTag(TrainTags.card(routine.id))
            .fillMaxWidth()
            .background(colors.bgSurface, shape)
            .border(MTheme.sizes.border, colors.borderSubtle, shape),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ReorderHandle(reorder, routine.id, routine.name, Modifier.testTag(TrainTags.handle(routine.id)))
            RoutineInfo(routine, nowMillis, zone, onOpen, Modifier.weight(1f))
            MIconButton(
                MIcons.EllipsisVertical,
                contentDescription = stringResource(Res.string.routines_more, routine.name),
                onClick = onMenu,
                modifier = Modifier.testTag(TrainTags.menu(routine.id)),
            )
        }
        if (routine.exercises.isNotEmpty()) {
            Row(
                Modifier.testTag(TrainTags.strip(routine.id)).padding(horizontal = space.s4),
                horizontalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                for (planned in routine.exercises.take(STRIP_THUMBNAILS)) {
                    key(planned.id) { StripThumbnail(planned.exercise) }
                }
            }
        }
        MButton(
            stringResource(if (resumes) Res.string.routines_resume else Res.string.routines_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(space.s4).testTag(TrainTags.start(routine.id)),
            variant = MButtonVariant.Tonal,
            size = MButtonSize.L,
            icon = MIcons.Play,
            enabled = startEnabled,
        )
    }
}

@Composable
private fun StripThumbnail(exercise: ExerciseUi) {
    ExerciseThumbnail(
        rememberExerciseMedia(exercise, thumbnail = true),
        EquipmentIcons.forEquipment(exercise.equipment),
        MTheme.sizes.mediaStrip,
        Modifier.testTag(TrainTags.THUMB),
    )
}

/** The name, summary, muscles and last time of [routine], one button that opens it. */
@Composable
private fun RoutineInfo(routine: RoutineUi, nowMillis: Long, zone: TimeZone, onOpen: () -> Unit, modifier: Modifier) {
    val colors = MTheme.colors
    val exercises = RoutineSummaries.exerciseCount(routine)
    val sets = RoutineSummaries.setCount(routine)
    val summary = stringResource(
        Res.string.routines_summary,
        pluralCount(Res.plurals.routines_exercise_count, exercises),
        pluralCount(Res.plurals.routines_set_count, sets),
    )
    val muscles = RoutineSummaries.topMuscles(routine).map { muscleLabel(it) }.joinToString(", ")
    val last = lastDoneText(RoutineSummaries.lastDone(routine.lastFinishedAt, nowMillis, zone), zone)
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MTheme.radius.md)
    Column(
        modifier
            .testTag(TrainTags.info(routine.id))
            .padding(vertical = MTheme.space.s2)
            .mFocusRing(shape, source)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.routines_open),
                onClick = onOpen,
            )
            .padding(vertical = MTheme.space.s1),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        MText(routine.name, MTheme.type.titleMd, color = colors.textPrimary, maxLines = NAME_LINES, overflow = TextOverflow.Ellipsis)
        MText(summary, MTheme.type.bodyMd, color = colors.textSecondary)
        if (muscles.isNotEmpty()) MText(muscles, MTheme.type.bodyMd, color = colors.textSecondary)
        MText(last, MTheme.type.caption, color = colors.textSecondary)
    }
}

/** A long name stops at three lines on the card; the editor shows it whole. */
private const val NAME_LINES = 3

@Composable
private fun lastDoneText(last: LastDone, zone: TimeZone): String = when (last) {
    LastDone.Never -> stringResource(Res.string.routines_never)
    LastDone.Today -> stringResource(Res.string.routines_last_today)
    LastDone.Yesterday -> stringResource(Res.string.routines_last_yesterday)
    is LastDone.DaysAgo -> pluralCount(Res.plurals.routines_last_days, last.n)
    is LastDone.On -> stringResource(Res.string.routines_last_date, shortDate(last.epochMillis, zone))
}

/** A card-shaped placeholder while the state loads. */
@Composable
internal fun RoutineCardSkeleton(modifier: Modifier = Modifier) {
    val colors = MTheme.colors
    val space = MTheme.space
    val shape = RoundedCornerShape(MTheme.radius.lg)
    Column(
        modifier
            .testTag(TrainTags.SKELETON)
            .fillMaxWidth()
            .background(colors.bgSurface, shape)
            .border(MTheme.sizes.border, colors.borderSubtle, shape)
            .padding(space.s4),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        Skeleton(Modifier.fillMaxWidth(NAME_BAR).height(space.s6))
        Skeleton(Modifier.fillMaxWidth(SUMMARY_BAR).height(space.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            repeat(STRIP_THUMBNAILS) { Skeleton(Modifier.size(MTheme.sizes.mediaStrip)) }
        }
        Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.buttonL))
    }
}

private const val NAME_BAR = 0.5f
private const val SUMMARY_BAR = 0.7f
