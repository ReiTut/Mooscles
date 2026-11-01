package com.mooscles.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.illustration.ExerciseFigure
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_add_set
import com.mooscles.designsystem.resources.ds_card_collapse
import com.mooscles.designsystem.resources.ds_card_expand
import com.mooscles.designsystem.resources.ds_exercise_menu
import com.mooscles.designsystem.resources.ds_media_offline
import com.mooscles.designsystem.resources.ds_sets_done_of
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.tabular
import org.jetbrains.compose.resources.stringResource

const val ExerciseCardTag = "exercise-card"
const val ExerciseMenuTag = "exercise-menu"
const val ExerciseAddSetTag = "exercise-add-set"
const val ExerciseEquipmentIconTag = "exercise-equipment-icon"
const val ExerciseOfflineIconTag = "exercise-offline-icon"
const val ExerciseOfflineTextTag = "exercise-offline-text"
const val ExerciseSetsTag = "exercise-sets"
const val ExerciseMediaPlaceholderTag = "exercise-media-placeholder"
const val ExerciseMediaFigureTag = "exercise-media-figure"
const val ExerciseCardHeaderTag = "exercise-card-header"
const val ExerciseCardSegmentTag = "exercise-card-segment"
const val ExerciseCardFooterTag = "exercise-card-footer"
const val ExerciseHeaderToggleTag = "exercise-header-toggle"
const val ExerciseCardDoneTag = "card-done"
const val ExerciseCardProgressTag = "card-progress"
const val ExerciseCardCaptionTag = "card-caption"
const val ExerciseCardSublineTag = "card-subline"

/**
 * Test-facing: the shape a piece of the segmented exercise card draws its surface with. The header
 * is rounded on top (all round when collapsed, as the whole card), a segment has no corners and the
 * footer is rounded at the bottom, so header, segments and footer stacked with no gap draw one card.
 */
val CardSegmentShape = SemanticsPropertyKey<Shape>("CardSegmentShape")

/** What the 64 dp thumbnail of an exercise shows. */
@Immutable
sealed interface ExerciseMedia {
    /** The exercise has no image: only the equipment icon is drawn. */
    data object None : ExerciseMedia

    /** The image exists but is not on the device yet: an "image off" icon and a short note. */
    data object Offline : ExerciseMedia

    /** An image; until its [painter] draws (while it loads), the equipment icon shows through. */
    data class Image(val painter: Painter) : ExerciseMedia

    /** Phase 4A: a still figure (the end frame of the exercise), with its muscle [highlights]. */
    data class Figure(val pose: FigurePose, val highlights: Set<FigureSegment>) : ExerciseMedia
}

/**
 * "3 of 3 sets" / "3 de 3 séries": [done] of [total], plural on [total] ("0 of 1 set"), zero
 * plural. The collapsed header's [ExerciseCardHeader] `progress` and the workout header use it.
 */
@Composable
fun setsDoneOf(done: Int, total: Int): String = pluralCount(Res.plurals.ds_sets_done_of, total, done, total)

/**
 * An exercise in the workout: the thumbnail ([media], 64 dp), [name], [meta] (muscle, equipment),
 * the options menu and, below, the set rows given by [sets] and a 56 dp "Add set" button. With
 * [collapsed] only the header remains. Without an image the thumbnail is the equipment icon, and
 * an image is drawn over that icon, so a tile still loading shows it; with an image (or the offline
 * note) the equipment icon also sits beside [meta]. It is the [ExerciseCardHeader],
 * [ExerciseCardSegment] and [ExerciseCardFooter] stacked; a lazy list uses those pieces directly.
 */
@Composable
fun ExerciseCard(
    name: String,
    meta: String,
    media: ExerciseMedia,
    equipmentIcon: ImageVector,
    onMenu: () -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    sets: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.testTag(ExerciseCardTag).fillMaxWidth()) {
        CardHeader(
            name, meta, media, equipmentIcon, onMenu, collapsed,
            onToggle = null, modifier = Modifier, progress = null, done = false, caption = null, support = null, columns = null,
            handle = null, subline = null,
        )
        if (!collapsed) {
            ExerciseCardSegment {
                Column(Modifier.testTag(ExerciseSetsTag), verticalArrangement = Arrangement.spacedBy(MTheme.targets.gap)) {
                    sets()
                }
            }
            ExerciseCardFooter(onAddSet)
        }
    }
}

/**
 * The top of a segmented exercise card (a lazy list item of its own): thumbnail, [name], [meta],
 * the menu and, open, the [caption] (a load convention note), the [support] line (rest chip,
 * previous date) and the [columns] header of the set rows. Tapping the header (not the menu) calls
 * [onToggle]. [collapsed], it is the whole card: rounded all round, with [progress] ("3 of 3
 * sets", see [setsDoneOf]) under [meta] and, with [done], a check before it.
 *
 * Phase 4B: [handle] (a [com.mooscles.designsystem.interaction.ReorderHandle]) comes before the
 * thumbnail, outside the toggle, so holding it never opens or closes the card; [subline] is a styled
 * line under [meta], open or collapsed (the routine target, "Target 3×8 · 80 kg"). A null [onToggle] makes the
 * header no button (the routine editor's cards do not collapse by hand); [menuTag] and [menuLabel] name the menu
 * button of a list that has one per card ("Options for Bench press").
 */
@Composable
fun ExerciseCardHeader(
    name: String,
    meta: String,
    media: ExerciseMedia,
    equipmentIcon: ImageVector,
    onMenu: () -> Unit,
    collapsed: Boolean,
    onToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    progress: String? = null,
    done: Boolean = false,
    caption: String? = null,
    support: (@Composable RowScope.() -> Unit)? = null,
    columns: (@Composable () -> Unit)? = null,
    handle: (@Composable () -> Unit)? = null,
    subline: AnnotatedString? = null,
    menuTag: String = ExerciseMenuTag,
    menuLabel: String? = null,
) = CardHeader(
    name, meta, media, equipmentIcon, onMenu, collapsed, onToggle, modifier, progress, done, caption, support, columns, handle, subline,
    menuTag, menuLabel,
)

/** The middle of a segmented exercise card, one set row for a lazy list: the card's sides, no corners. */
@Composable
fun ExerciseCardSegment(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val space = MTheme.space
    Box(
        modifier
            .testTag(ExerciseCardSegmentTag)
            .fillMaxWidth()
            .cardSurface(RectangleShape, openTop = true, openBottom = true)
            // Half the row gap above and below: rows in their own segments sit `targets.gap` apart.
            .padding(horizontal = space.s3, vertical = space.s1),
    ) {
        content()
    }
}

/**
 * The bottom of a segmented exercise card: a full-width, 56 dp tonal "Add set", disabled without [addEnabled]. [addTag]
 * is the button's test tag; [note] is a line under it (why the button is disabled), in `text.secondary`.
 */
@Composable
fun ExerciseCardFooter(
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
    addEnabled: Boolean = true,
    addTag: String = ExerciseAddSetTag,
    note: String? = null,
) {
    val space = MTheme.space
    val radius = MTheme.radius.lg
    Column(
        modifier
            .testTag(ExerciseCardFooterTag)
            .fillMaxWidth()
            .cardSurface(RoundedCornerShape(bottomStart = radius, bottomEnd = radius), openTop = true, openBottom = false)
            .padding(start = space.s3, end = space.s3, top = space.s2, bottom = space.s3),
        verticalArrangement = Arrangement.spacedBy(space.s2),
    ) {
        MButton(
            stringResource(Res.string.ds_add_set), onAddSet,
            modifier = Modifier.fillMaxWidth().testTag(addTag),
            variant = MButtonVariant.Tonal,
            size = MButtonSize.L,
            icon = MIcons.Plus,
            enabled = addEnabled,
        )
        if (note != null) MText(note, MTheme.type.caption, color = MTheme.colors.textSecondary)
    }
}

@Composable
private fun CardHeader(
    name: String,
    meta: String,
    media: ExerciseMedia,
    equipmentIcon: ImageVector,
    onMenu: () -> Unit,
    collapsed: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier,
    progress: String?,
    done: Boolean,
    caption: String?,
    support: (@Composable RowScope.() -> Unit)?,
    columns: (@Composable () -> Unit)?,
    handle: (@Composable () -> Unit)?,
    subline: AnnotatedString?,
    menuTag: String = ExerciseMenuTag,
    menuLabel: String? = null,
) {
    val colors = MTheme.colors
    val space = MTheme.space
    val shape = if (collapsed) RoundedCornerShape(MTheme.radius.lg) else topRoundedShape()
    Column(
        modifier
            .testTag(ExerciseCardHeaderTag)
            .fillMaxWidth()
            .cardSurface(shape, openTop = false, openBottom = !collapsed)
            // Open, `space.2` here and the first segment's `space.1` keep the header `space.3` from the sets.
            .padding(start = space.s3, end = space.s3, top = space.s3, bottom = if (collapsed) space.s3 else space.s2),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        val toggle = if (onToggle != null) {
            val source = remember { MutableInteractionSource() }
            val label = stringResource(if (collapsed) Res.string.ds_card_expand else Res.string.ds_card_collapse)
            Modifier
                .testTag(ExerciseHeaderToggleTag)
                // indication = null: the press shows as the design system's scale, as on a button.
                .mPressScale(source, enabled = true)
                .clickable(interactionSource = source, indication = null, onClickLabel = label, role = Role.Button, onClick = onToggle)
                .mFocusRing(RoundedCornerShape(MTheme.radius.md), source)
        } else {
            Modifier
        }
        val main: @Composable RowScope.() -> Unit = {
            ExerciseThumbnail(media, equipmentIcon, MTheme.sizes.media)
            Column(Modifier.weight(1f)) {
                MText(name, MTheme.type.titleMd, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(space.s1), verticalAlignment = Alignment.CenterVertically) {
                    if (media != ExerciseMedia.None) {
                        MIcon(equipmentIcon, null, Modifier.testTag(ExerciseEquipmentIconTag), size = MTheme.sizes.icon16, tint = colors.textTertiary)
                    }
                    MText(meta, MTheme.type.bodyMd, color = colors.textSecondary)
                }
                if (subline != null) {
                    BasicText(
                        subline,
                        Modifier.testTag(ExerciseCardSublineTag),
                        style = MTheme.type.bodyMd.copy(color = colors.textSecondary),
                    )
                }
                if (media == ExerciseMedia.Offline) {
                    MText(
                        stringResource(Res.string.ds_media_offline),
                        MTheme.type.caption,
                        modifier = Modifier.testTag(ExerciseOfflineTextTag),
                        color = colors.textSecondary,
                    )
                }
                if (!collapsed && caption != null) {
                    MText(caption, MTheme.type.caption, modifier = Modifier.testTag(ExerciseCardCaptionTag), color = colors.textSecondary)
                }
                if (collapsed && progress != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s1), verticalAlignment = Alignment.CenterVertically) {
                        if (done) {
                            MIcon(MIcons.CircleCheck, null, Modifier.testTag(ExerciseCardDoneTag), size = MTheme.sizes.icon16, tint = colors.accentText)
                        }
                        MText(
                            progress,
                            MTheme.type.labelMd.tabular(),
                            modifier = Modifier.testTag(ExerciseCardProgressTag),
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            MIconButton(MIcons.EllipsisVertical, menuLabel ?: stringResource(Res.string.ds_exercise_menu), onMenu, Modifier.testTag(menuTag))
        }
        if (handle == null) {
            Row(toggle, horizontalArrangement = Arrangement.spacedBy(space.s3), verticalAlignment = Alignment.CenterVertically, content = main)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                handle()
                Row(
                    Modifier.weight(1f).then(toggle),
                    horizontalArrangement = Arrangement.spacedBy(space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    content = main,
                )
            }
        }
        if (!collapsed) {
            if (support != null) {
                // A flow, so the chips wrap under each other with large text instead of being cut.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.s2),
                    verticalArrangement = Arrangement.spacedBy(space.s1),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    support()
                }
            }
            columns?.invoke()
        }
    }
}

/**
 * The square tile of an exercise, [size] wide, as the card header draws it: the equipment icon
 * without media, the image over that icon, the offline icon, or the still figure (decorative: the
 * exercise's name is beside it). Lists, the picker and the editor use it on their own.
 */
@Composable
fun ExerciseThumbnail(media: ExerciseMedia, equipmentIcon: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    val colors = MTheme.colors
    Box(
        modifier.size(size).clip(RoundedCornerShape(MTheme.radius.md)).background(colors.bgInset),
        contentAlignment = Alignment.Center,
    ) {
        when (media) {
            is ExerciseMedia.Image -> {
                // The placeholder under the image: a painter that has not loaded yet draws
                // nothing, and the tile must not be an empty square meanwhile.
                MIcon(
                    equipmentIcon, null, Modifier.testTag(ExerciseMediaPlaceholderTag),
                    size = MTheme.sizes.icon28, tint = colors.textTertiary,
                )
                Image(
                    media.painter,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop,
                )
            }
            is ExerciseMedia.Figure -> ExerciseFigure(
                media.pose, media.highlights, contentDescription = null,
                Modifier.testTag(ExerciseMediaFigureTag).size(size),
            )
            ExerciseMedia.Offline -> MIcon(
                MIcons.ImageOff, null, Modifier.testTag(ExerciseOfflineIconTag),
                size = MTheme.sizes.icon24, tint = colors.textTertiary,
            )
            ExerciseMedia.None -> MIcon(
                equipmentIcon, null, Modifier.testTag(ExerciseEquipmentIconTag),
                size = MTheme.sizes.icon28, tint = colors.textTertiary,
            )
        }
    }
}

/**
 * The card's surface (`bg.surface` with a `border.subtle` hairline) in [shape], for one piece of
 * it. An open edge ([openTop], [openBottom]) draws no line: the outline is extended past it by the
 * line width and clipped, so the pieces join into one card with only its outer edge drawn.
 */
@Composable
private fun Modifier.cardSurface(shape: Shape, openTop: Boolean, openBottom: Boolean): Modifier {
    val fill = MTheme.colors.bgSurface
    val line = MTheme.colors.borderSubtle
    val width = MTheme.sizes.border
    return semantics { this[CardSegmentShape] = shape }.drawBehind { drawCardSurface(shape, openTop, openBottom, fill, line, width) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardSurface(
    shape: Shape,
    openTop: Boolean,
    openBottom: Boolean,
    fill: Color,
    line: Color,
    width: Dp,
) {
    val stroke = width.toPx()
    val above = if (openTop) stroke else 0f
    val below = if (openBottom) stroke else 0f
    val extended = Size(size.width, size.height + above + below)
    clipRect {
        translate(top = -above) {
            drawOutline(shape.createOutline(extended, layoutDirection, this), fill)
            // The line inside the edge, as Modifier.border draws it.
            translate(left = stroke / 2f, top = stroke / 2f) {
                val inner = Size(extended.width - stroke, extended.height - stroke)
                drawOutline(shape.createOutline(inner, layoutDirection, this), line, style = Stroke(stroke))
            }
        }
    }
}
