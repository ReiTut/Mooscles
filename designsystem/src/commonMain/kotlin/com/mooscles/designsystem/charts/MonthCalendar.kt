package com.mooscles.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_calendar_collapse
import com.mooscles.designsystem.resources.ds_calendar_expand
import com.mooscles.designsystem.resources.ds_calendar_next
import com.mooscles.designsystem.resources.ds_calendar_previous
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val MonthCalendarTag = "month-calendar"
const val CalendarPreviousTag = "calendar-previous"
const val CalendarNextTag = "calendar-next"
const val CalendarCollapseTag = "calendar-collapse"
const val CalendarSummaryTag = "calendar-summary"
fun calendarDayTag(day: Int): String = "calendar-day-$day"

/** A marked day. [label] is the caller's localized full date, workout count and, if appropriate, name. */
@Immutable
data class CalendarDayMark(val day: Int, val workouts: Int, val label: String)

/**
 * The Phase 5B monthly calendar (D12), independent of calendar/date libraries and the workout domain.
 *
 * [weekDays] are already ordered by the user's first weekday. Only [marks] are buttons; unmarked dates and padding
 * are not screen-reader stops. The caller owns saved month/day/collapse state and supplies nullable navigation
 * callbacks at the first/current month. Horizontal dragging changes at most one month, with no animation, including
 * when reduced motion is on. The date cells are the documented 40 dp target exception; rows grow with large text.
 *
 * The grid uses the full available width: a screen with 16 dp side margins leaves 288 dp at 320 dp, enough for seven
 * cells above 40 dp. Do not nest another horizontal padding inside it. [interactive] supports S2's visual-only
 * fallback, where the host provides an equivalent day filter; [summary] remains available to accessibility.
 */
@Composable
fun MonthCalendar(
    monthLabel: String,
    summary: String,
    weekDays: List<String>,
    daysInMonth: Int,
    firstDayOffset: Int,
    marks: List<CalendarDayMark>,
    today: Int?,
    selectedDay: Int?,
    onDay: (Int) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    require(weekDays.size == 7)
    val cells = remember(daysInMonth, firstDayOffset) { CalendarMonthGrid.cells(daysInMonth, firstDayOffset) }
    val marked = remember(marks, daysInMonth) {
        require(marks.map { it.day }.distinct().size == marks.size)
        require(marks.all { it.day in 1..daysInMonth && it.workouts > 0 && it.label.isNotBlank() })
        marks.associateBy(CalendarDayMark::day)
    }
    val threshold = with(LocalDensity.current) { MTheme.sizes.calendarSwipeDistance.toPx() }
    val swipe = if (collapsed) Modifier else Modifier.pointerInput(monthLabel, onPrevious, onNext, threshold) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                if (distance >= threshold) onPrevious?.invoke()
                else if (distance <= -threshold) onNext?.invoke()
                distance = 0f
            },
        ) { change, amount ->
            change.consume()
            distance += amount
        }
    }
    Column(
        modifier.fillMaxWidth().testTag(MonthCalendarTag).semantics { isTraversalGroup = true }
            .background(MTheme.colors.bgSurface, RoundedCornerShape(MTheme.radius.lg))
            .then(swipe).padding(vertical = MTheme.space.s2),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).testTag(CalendarSummaryTag).clearAndSetSemantics { contentDescription = summary }) {
                MText(monthLabel, MTheme.type.titleSm)
            }
            if (onPrevious != null) {
                MIconButton(MIcons.ChevronRight, stringResource(Res.string.ds_calendar_previous), onPrevious,
                    Modifier.testTag(CalendarPreviousTag).rotate(180f))
            }
            if (onNext != null) {
                MIconButton(MIcons.ChevronRight, stringResource(Res.string.ds_calendar_next), onNext,
                    Modifier.testTag(CalendarNextTag))
            }
            MIconButton(if (collapsed) MIcons.ChevronDown else MIcons.ChevronUp,
                stringResource(if (collapsed) Res.string.ds_calendar_expand else Res.string.ds_calendar_collapse),
                onToggleCollapsed, Modifier.testTag(CalendarCollapseTag))
        }
        if (!collapsed) {
            Row(Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Box(Modifier.weight(1f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        MText(day, MTheme.type.caption, color = MTheme.colors.textSecondary)
                    }
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    week.forEach { day ->
                        if (day == null) Spacer(Modifier.weight(1f).fillMaxHeight())
                        else CalendarCell(day, marked[day], day == today, day == selectedDay, interactive, onDay,
                            Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: Int,
    mark: CalendarDayMark?,
    today: Boolean,
    selected: Boolean,
    interactive: Boolean,
    onDay: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MTheme.colors
    val target = interactive && mark != null
    val source = remember { MutableInteractionSource() }
    val click = if (target) Modifier.clickable(interactionSource = source, indication = null,
        role = Role.Button, onClick = { onDay(day) }) else Modifier
    val semantics = Modifier.clearAndSetSemantics {
        if (target) {
            contentDescription = mark!!.label
            role = Role.Button
            this.selected = selected
            onClick { onDay(day); true }
        }
    }
    val background = if (mark != null) Modifier.background(colors.accentSubtle, CircleShape) else Modifier
    val outline = when {
        selected -> Modifier.border(MTheme.sizes.borderSelected, colors.accentText, CircleShape)
        today -> Modifier.border(MTheme.sizes.border, colors.borderStrong, CircleShape)
        else -> Modifier
    }
    Box(
        modifier.heightIn(min = MTheme.sizes.calendarCellMin).testTag(calendarDayTag(day))
            .then(click).then(semantics).then(background).then(outline)
            .then(if (target) Modifier.mFocusRing(CircleShape, source) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(vertical = MTheme.space.s1), horizontalAlignment = Alignment.CenterHorizontally) {
            MText(day.toString(), MTheme.type.numSm.copy(fontWeight = if (mark != null) FontWeight.SemiBold else FontWeight.Medium),
                softWrap = false, maxLines = 1)
            Box(
                Modifier.size(MTheme.sizes.calendarMultipleDot)
                    .then(if ((mark?.workouts ?: 0) > 1) Modifier.background(colors.accentText, CircleShape) else Modifier),
            )
        }
    }
}
