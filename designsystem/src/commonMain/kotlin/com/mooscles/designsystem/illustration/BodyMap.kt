package com.mooscles.designsystem.illustration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_body_map_primary
import com.mooscles.designsystem.resources.ds_body_map_secondary
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The 17 regions of the body map (spec D6), with the same names as `MuscleRegion` in `:shared`,
 * which the design system does not know: the screens map one to the other by name.
 */
enum class BodyRegion {
    ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST, FOREARMS, GLUTES, HAMSTRINGS,
    LATS, LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS,
}

const val BodyMapTag = "body-map"
const val BodyMapLegendTag = "body-map-legend"
const val BodyMapSwatchTag = "body-map-swatch"

/** Width over height of the body map: the two views of [BodyMapPaths] side by side. */
internal const val BodyMapAspectRatio: Float = 2f * BodyMapPaths.VIEW_WIDTH / BodyMapPaths.VIEW_HEIGHT

/** The path data of [region] in the front ([front] true) or back view: none when the view does not show it. */
internal fun bodyMapPathData(front: Boolean, region: BodyRegion): List<String> {
    val view = if (front) BodyMapPaths.front else BodyMapPaths.back
    return view[BodyMapGeometry.drawnAs(region)].orEmpty()
}

/** One parsed view: the neutral silhouette and each region's paths, in the view's own units. */
internal class BodyMapView(val outline: List<Path>, val regions: Map<BodyRegion, List<Path>>)

/** Both views of the map, parsed. Built once per process and never changed afterwards. */
internal class BodyMapGeometry private constructor(val front: BodyMapView, val back: BodyMapView) {
    companion object {
        /** The source has no neck surface (spike decision): the neck is drawn with the trapezius paths. */
        fun drawnAs(region: BodyRegion): BodyRegion = if (region == BodyRegion.NECK) BodyRegion.TRAPS else region

        // Parsing the 60 paths takes about 0.1 s the first time in a fresh process (spike), so it runs
        // off the main thread (load) and the result is kept.
        private val parsed = lazy {
            BodyMapGeometry(
                front = view(BodyMapPaths.frontOutline, BodyMapPaths.front),
                back = view(BodyMapPaths.backOutline, BodyMapPaths.back),
            )
        }

        /** The geometry when it is already parsed, so a map drawn again shows it in its first frame. */
        fun cachedOrNull(): BodyMapGeometry? = if (parsed.isInitialized()) parsed.value else null

        /** The geometry, parsed on [Dispatchers.Default] the first time. */
        suspend fun load(): BodyMapGeometry = cachedOrNull() ?: withContext(Dispatchers.Default) { parsed.value }

        private fun view(outline: List<String>, regions: Map<BodyRegion, List<String>>) =
            BodyMapView(outline.map(::parse), regions.mapValues { (_, paths) -> paths.map(::parse) })

        private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()
    }
}

/**
 * Parses the body map's paths off the main thread, if they are not parsed yet. A screen that will
 * show a [BodyMap] can call it early (when the library opens, say) so the first map draws at once;
 * a test that reads the map's pixels calls it before setting the content.
 */
suspend fun prepareBodyMap() {
    BodyMapGeometry.load()
}

/** The four colors of the body map. */
@Immutable
internal data class BodyMapPalette(val outline: Color, val neutral: Color, val primary: Color, val secondary: Color)

/**
 * The spike's choice (Task 1): the outline and the secondary regions in `border.strong`, the rest in
 * `bg.inset`, the primary regions in `accent.solid`. Primary and secondary differ in hue (green and
 * grey) and the legend names them, so the map never tells them apart by color alone.
 */
internal fun bodyMapPalette(colors: MoosclesColors) = BodyMapPalette(
    outline = colors.borderStrong,
    neutral = colors.bgInset,
    primary = colors.accentSolid,
    secondary = colors.borderStrong,
)

/**
 * The body map (spec D6), adapted from MuscleMap (MIT; see the licenses screen): the front and
 * the back view side by side, in one image. Every region and the silhouette are filled with the
 * neutral `bg.inset` and outlined with the `illustration.bodyMapStroke` hairline in
 * `border.strong`; the [primary] regions are filled with `accent.solid` and the [secondary] ones
 * with `border.strong` (a region in both is primary). [BodyRegion.NECK] lights the trapezius.
 *
 * The map has no label of its own: it is an image ([Role.Image]) that a screen reader skips, and
 * the screen shows the equivalent text below it ("Primary: …. Secondary: …."), with a
 * [BodyMapLegend]. Give it a width with [modifier]: the height follows the views' aspect ratio,
 * and with both given the views are centered in the box.
 *
 * The paths are parsed off the main thread the first time a map is shown in the process; until
 * then the map draws nothing (its size is already final). [prepareBodyMap] parses them ahead.
 */
@Composable
fun BodyMap(primary: Set<BodyRegion>, secondary: Set<BodyRegion>, modifier: Modifier = Modifier) {
    val geometry by produceState(BodyMapGeometry.cachedOrNull()) {
        if (value == null) value = BodyMapGeometry.load()
    }
    val palette = bodyMapPalette(MTheme.colors)
    val strokeWidth = MTheme.illustration.bodyMapStroke
    val lit = remember(primary) { primary.mapTo(HashSet(), BodyMapGeometry::drawnAs) }
    val dim = remember(secondary, lit) { secondary.mapTo(HashSet(), BodyMapGeometry::drawnAs) - lit }
    val shown = geometry
    val draw = remember(shown, lit, dim, palette, strokeWidth) {
        if (shown == null) Modifier else Modifier.drawWithCache {
            val viewWidth = BodyMapPaths.VIEW_WIDTH
            val viewHeight = BodyMapPaths.VIEW_HEIGHT
            val factor = min(size.width / (2f * viewWidth), size.height / viewHeight)
            if (factor <= 0f) return@drawWithCache onDrawBehind {}
            val left = (size.width - 2f * viewWidth * factor) / 2f
            val top = (size.height - viewHeight * factor) / 2f
            // The paths are scaled down by factor, so the stroke is drawn 1 / factor wide to end up a hairline.
            val contour = Stroke(width = strokeWidth.toPx() / factor, join = StrokeJoin.Round)
            onDrawBehind {
                listOf(shown.front, shown.back).forEachIndexed { index, view ->
                    translate(left = left + index * viewWidth * factor, top = top) {
                        scale(factor, factor, pivot = Offset.Zero) {
                            for (path in view.outline) {
                                drawPath(path, palette.neutral)
                                drawPath(path, palette.outline, style = contour)
                            }
                            for ((region, paths) in view.regions) {
                                val fill = when (region) {
                                    in lit -> palette.primary
                                    in dim -> palette.secondary
                                    else -> palette.neutral
                                }
                                for (path in paths) {
                                    drawPath(path, fill)
                                    drawPath(path, palette.outline, style = contour)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(
        modifier
            .aspectRatio(BodyMapAspectRatio)
            .testTag(BodyMapTag)
            .semantics { role = Role.Image }
            .then(draw),
    )
}

/**
 * The legend of a [BodyMap]: a swatch and "Primary", a swatch and "Secondary", in the map's own
 * colors and outline. The two items wrap onto two lines when the text is large.
 */
@Composable
fun BodyMapLegend(modifier: Modifier = Modifier) {
    val palette = bodyMapPalette(MTheme.colors)
    val space = MTheme.space
    FlowRow(
        modifier.testTag(BodyMapLegendTag),
        horizontalArrangement = Arrangement.spacedBy(space.s4),
        verticalArrangement = Arrangement.spacedBy(space.s2),
    ) {
        LegendItem(palette.primary, palette.outline, stringResource(Res.string.ds_body_map_primary))
        LegendItem(palette.secondary, palette.outline, stringResource(Res.string.ds_body_map_secondary))
    }
}

@Composable
private fun LegendItem(fill: Color, outline: Color, label: String) {
    val shape = RoundedCornerShape(MTheme.radius.xs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        Box(
            Modifier
                .size(MTheme.sizes.icon16)
                .testTag(BodyMapSwatchTag)
                .background(fill, shape)
                .border(MTheme.illustration.bodyMapStroke, outline, shape),
        )
        MText(label, MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
    }
}
