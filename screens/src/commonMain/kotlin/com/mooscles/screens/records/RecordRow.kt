package com.mooscles.screens.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.displayName
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.progress.recordKindLabel
import com.mooscles.screens.progress.recordSpoken
import com.mooscles.screens.progress.recordWasText
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.records_current
import com.mooscles.screens.resources.records_open_workout
import com.mooscles.screens.workout.RecompositionProbe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * One record of the timeline (spec D11), one button and one node for a screen reader ([recordSpoken]: "Supino reto
 * com barra, Carga: 87,5 quilos por 6, antes 85 quilos, 28 de outubro, Atual"): the record badge ([PrBadge], soft on
 * a first record) before the name in the screen's language ([portuguese], `title.sm`, wrapping whole); the set and
 * what it beat ("87,5 kg × 6 · antes 85 kg", "Peso corporal × 12 · antes 10 repetições"); then the kind ("Carga",
 * "Repetições"), the short date and, on a record that still holds, "Atual" written out. Each part wraps to a line of its
 * own rather than being cut. A tap is [RowActions.open]; the accessibility action "Open workout" is
 * [RowActions.openWorkout].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordRow(
    event: RecordEvent,
    portuguese: Boolean,
    pounds: Boolean,
    zone: TimeZone,
    today: LocalDate,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    val tag = RecordsTags.row(RecordTimelines.key(event))
    RecompositionProbe(tag)
    val record = event.record
    val colors = MTheme.colors
    val name = displayName(record.exercise, portuguese)
    val first = record.firstRecord
    val kind = recordKindLabel(record.kind)
    val value = recordWasText(record.kind, record.weightKg, record.reps, record.previousKg, record.previousReps, pounds)
    val date = shortDate(record.date, zone)
    val spoken = recordSpoken(
        name, record.kind, record.weightKg, record.reps, record.previousKg, record.previousReps, pounds,
        record.date, zone, today, current = event.current, first = first,
    )
    val openWorkoutLabel = stringResource(Res.string.records_open_workout)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MTheme.targets.min)
            .testTag(tag)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { actions.open(event) }
            .semantics {
                contentDescription = spoken
                customActions = listOf(CustomAccessibilityAction(openWorkoutLabel) { actions.openWorkout(event); true })
            }
            .mFocusRing(shape, source)
            .background(if (pressed) colors.bgInset else Color.Transparent, shape)
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s3),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // The badge's own words are in the row's description: kept out of the merged node.
            Box(Modifier.clearAndSetSemantics { testTag = if (first) RecordsTags.BADGE_FIRST else RecordsTags.BADGE }) {
                PrBadge(soft = first)
            }
            MText(name, MTheme.type.titleSm, color = colors.textPrimary, modifier = Modifier.testTag(RecordsTags.ROW_NAME))
        }
        MText(value, MTheme.type.bodyMd, color = colors.textPrimary, modifier = Modifier.testTag(RecordsTags.ROW_VALUE))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            MText(kind, MTheme.type.labelMd, color = colors.textSecondary, modifier = Modifier.testTag(RecordsTags.ROW_KIND))
            MText(date, MTheme.type.bodyMd, color = colors.textSecondary, modifier = Modifier.testTag(RecordsTags.ROW_DATE))
            if (event.current) CurrentLabel()
        }
    }
}

/**
 * "Atual", written: `accent.text` on `accent.subtle` (the selected chip's pair), never color alone. Not a node of its
 * own: the row reads it in its description.
 */
@Composable
private fun CurrentLabel() {
    val colors = MTheme.colors
    MText(
        stringResource(Res.string.records_current),
        MTheme.type.labelMd,
        color = colors.accentText,
        modifier = Modifier
            .testTag(RecordsTags.ROW_CURRENT)
            .background(colors.accentSubtle, MTheme.radius.full)
            .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s1),
    )
}

/** A placeholder shaped like [RecordRow]: the badge and the name, the set, the kind and the date. */
@Composable
internal fun RecordSkeletonRow(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s3),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
    ) {
        Skeleton(Modifier.fillMaxWidth(NAME_BLOCK_FRACTION).height(MTheme.space.s4))
        Skeleton(Modifier.fillMaxWidth(VALUE_BLOCK_FRACTION).height(MTheme.space.s3))
        Skeleton(Modifier.fillMaxWidth(META_BLOCK_FRACTION).height(MTheme.space.s3))
    }
}

private const val NAME_BLOCK_FRACTION = 0.7f
private const val VALUE_BLOCK_FRACTION = 0.5f
private const val META_BLOCK_FRACTION = 0.3f
