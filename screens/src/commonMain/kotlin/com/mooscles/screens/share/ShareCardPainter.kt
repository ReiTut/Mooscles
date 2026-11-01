package com.mooscles.screens.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesSpace
import com.mooscles.designsystem.tokens.rememberMoosclesTypography
import kotlin.math.ceil
import kotlin.math.max

/**
 * The density the card is drawn at on every device and platform: the Pixel 8's (420 dpi), so the
 * 1080 px card is 411 dp wide, a phone screen, and the type scale reads at its usual size. Font
 * scale 1: the card is an image, the same for whoever receives it.
 */
val ShareCardDensity: Density = Density(density = 2.625f, fontScale = 1f)

/** The text styles of the card, from the design system's type scale (fonts resolved in a composition). */
class ShareCardStyles(
    /** "MOOSCLES", Barlow Condensed. */
    val brand: TextStyle,
    val title: TextStyle,
    val date: TextStyle,
    /** Duration, sets and volume, Barlow Condensed. */
    val statValue: TextStyle,
    val statLabel: TextStyle,
    val section: TextStyle,
    val rowName: TextStyle,
    /** The best set and a record's load, Barlow Condensed. */
    val rowValue: TextStyle,
    val rowCaption: TextStyle,
    val badge: TextStyle,
    val footer: TextStyle,
)

/** The card's styles from the type scale; call once in a composition and pass them to the painter. */
@Composable
fun rememberShareCardStyles(): ShareCardStyles {
    val type = rememberMoosclesTypography()
    return remember(type) {
        ShareCardStyles(
            brand = type.numLg.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = null),
            title = type.titleLg,
            date = type.bodyLg,
            statValue = type.numMd,
            statLabel = type.labelCaps,
            section = type.labelCaps,
            rowName = type.titleSm,
            rowValue = type.numSm,
            rowCaption = type.caption,
            badge = type.labelMd,
            footer = type.labelMd,
        )
    }
}

/** One part of a body row on a page: lines [nameStart, nameEnd) of the name and [valueStart, valueEnd) of the value. */
class ShareCardSlice internal constructor(
    val kind: Kind,
    /** Index in [ShareCardData.records] or [ShareCardData.rows]; -1 for a section title or a spacer. */
    val index: Int,
    /** The first part of its row: the one with the badge and the caption. */
    val first: Boolean,
    val nameStart: Int,
    val nameEnd: Int,
    val valueStart: Int,
    val valueEnd: Int,
    /** All the name's lines, across every part. */
    val nameLineCount: Int,
    val height: Int,
    internal val block: Block,
) {
    enum class Kind { Section, Record, Exercise, Empty, Spacer }

    /** The last part of its row. */
    val last: Boolean get() = nameEnd == nameLineCount && valueEnd == block.valueLines
}

/** One page of the card, measured and ready to paint. */
class ShareCardPage internal constructor(
    val index: Int,
    val count: Int,
    val height: Int,
    val slices: List<ShareCardSlice>,
    internal val header: Header,
    internal val bodyTop: Int,
    internal val counter: TextLayoutResult?,
) {
    /** Where "MOOSCLES" is drawn, in page pixels. */
    val brandBounds: IntRect get() = header.brandBounds
}

/**
 * Draws a [ShareCardData] as PNG-ready pages of [WIDTH] px, each at most [MAX_PAGE_HEIGHT] px
 * (the rule of the Android card it replaced): the header (brand, name, date and the three
 * statistics) on every page, then the records and the exercises, a row continued on the next page
 * at a line boundary when it cannot fit whole. Always on the dark graphite of the brand, whatever
 * the app theme. Common code (`CanvasDrawScope`, `TextMeasurer`); call it off the main thread, one
 * card at a time per [TextMeasurer].
 */
class ShareCardPainter(private val styles: ShareCardStyles, private val density: Density = ShareCardDensity) {
    private val colors = MoosclesColors.Dark
    private val space = MoosclesSpace()
    private val sizes = MoosclesSizes()
    private fun px(dp: Dp): Int = with(density) { dp.roundToPx() }

    private val margin = px(space.s8)
    private val inner = WIDTH - 2 * margin
    private val rowGap = px(space.s4)

    /** Every page of [card]. Keeps them all in memory: for many pages, [layout] and paint one at a time. */
    fun paint(card: ShareCardData, measurer: TextMeasurer): List<ImageBitmap> = layout(card, measurer).map(::paint)

    /** Measures [card] into pages without drawing them. */
    fun layout(card: ShareCardData, measurer: TextMeasurer): List<ShareCardPage> {
        val measure = Measure(measurer)
        val header = header(card, measure)
        val footer = px(space.s4) + measure("0 / 0", styles.footer).size.height + px(space.s6)
        val capacity = MAX_PAGE_HEIGHT - header.height - footer
        require(capacity >= MIN_BODY) { "The workout title is too long to fit in a share image" }

        val blocks = blocks(card, measure)
        val pages = mutableListOf<MutableList<ShareCardSlice>>(mutableListOf())
        var used = 0
        fun newPage() {
            pages += mutableListOf<ShareCardSlice>()
            used = 0
        }
        blocks.forEachIndexed { i, block ->
            val whole = slice(block, 0, block.nameLines, 0, block.valueLines, first = true)
            if (block.kind == ShareCardSlice.Kind.Spacer) {
                // A spacer neither starts a page nor pushes one.
                if (used > 0 && used + whole.height <= capacity) {
                    pages.last() += whole
                    used += whole.height
                }
                return@forEachIndexed
            }
            // A section title is never left alone at the bottom: it moves on with its first row.
            val keepWith = if (block.kind == ShareCardSlice.Kind.Section) {
                blocks.getOrNull(i + 1)?.let { next ->
                    minOf(capacity - whole.height, slice(next, 0, next.nameLines, 0, next.valueLines, first = true).height)
                } ?: 0
            } else 0
            if (whole.height <= capacity) {
                if (used + whole.height + keepWith > capacity && pages.last().isNotEmpty()) newPage()
                pages.last() += whole
                used += whole.height
                return@forEachIndexed
            }
            // A very long name or value continues on the next page at a line boundary, never
            // truncated. It starts on a new page, under its section title when that came just before.
            if (pages.last().isNotEmpty() && pages.last().last().kind != ShareCardSlice.Kind.Section) newPage()
            var nameLine = 0
            var valueLine = 0
            var first = true
            while (nameLine < block.nameLines || valueLine < block.valueLines) {
                val room = capacity - used
                val available = room - block.padding - if (block.rule) px(sizes.border) else 0
                val nameEnd = fittingEnd(block.name, nameLine, available - if (first) block.nameExtra else 0)
                val valueEnd = fittingEnd(block.value, valueLine, available - if (first) block.valueExtra else 0)
                val part = slice(block, nameLine, nameEnd, valueLine, valueEnd, first)
                check(part.height <= room && (nameEnd > nameLine || valueEnd > valueLine)) {
                    "A card row exceeds the page height"
                }
                pages.last() += part
                used += part.height
                nameLine = nameEnd
                valueLine = valueEnd
                first = false
                if (nameLine < block.nameLines || valueLine < block.valueLines) newPage()
            }
        }
        val filled = pages.filter { it.isNotEmpty() }.ifEmpty { listOf<List<ShareCardSlice>>(emptyList()) }
        return filled.mapIndexed { index, slices ->
            val body = slices.sumOf { it.height }
            val counter = if (filled.size > 1) measure("${index + 1} / ${filled.size}", styles.footer) else null
            ShareCardPage(index, filled.size, header.height + body + footer, slices, header, header.height, counter)
        }
    }

    /** Draws one page measured by [layout]. */
    fun paint(page: ShareCardPage): ImageBitmap {
        val bitmap = ImageBitmap(WIDTH, page.height)
        val size = Size(WIDTH.toFloat(), page.height.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(colors.bgBase, size = size)
            drawHeader(page.header)
            var top = page.bodyTop.toFloat()
            for (slice in page.slices) {
                drawSlice(slice, top)
                top += slice.height
            }
            val counter = page.counter
            if (counter != null) {
                drawText(counter, colors.textSecondary,
                    Offset((WIDTH - margin - counter.size.width).toFloat(), top + px(space.s4)))
            }
        }
        return bitmap
    }

    /** Lays out text at the card's density, [width] px wide at most. */
    private inner class Measure(private val measurer: TextMeasurer) {
        operator fun invoke(text: String, style: TextStyle, width: Int = inner, maxLines: Int = Int.MAX_VALUE): TextLayoutResult =
            measurer.measure(text, style, maxLines = maxLines, constraints = Constraints(maxWidth = width.coerceAtLeast(1)),
                density = density, layoutDirection = LayoutDirection.Ltr)
    }

    /** Brand, workout name, date, then the three statistics and a rule: the top of every page. */
    private fun header(card: ShareCardData, measure: Measure): Header {
        val brandTop = px(space.s6)
        val brandM = measure(BRAND_FIRST, styles.brand, inner)
        val brandRest = measure(BRAND_REST, styles.brand, inner)
        val fontPx = with(density) { styles.brand.fontSize.toPx() }
        // The plates run from the baseline to the cap height (0.7 em of Barlow Condensed).
        val plateScale = fontPx * CAP_HEIGHT_EM / PLATE_RUN
        val brandGap = fontPx * BRAND_GAP_EM
        val brandWidth = brandM.size.width + brandGap * 2 + PLATES_WIDTH * plateScale + brandRest.size.width
        val brandBounds = IntRect(margin, brandTop, margin + ceil(brandWidth).toInt(), brandTop + brandM.size.height)

        val titleTop = brandBounds.bottom + px(space.s4)
        val title = measure(card.workoutName, styles.title, inner)
        val dateTop = titleTop + title.size.height + px(space.s1)
        val date = measure(card.dateText, styles.date, inner)

        val statsTop = dateTop + date.size.height + px(space.s5)
        val gap = px(space.s4)
        val statWidth = (inner - 2 * gap) / 3
        val pairs = listOf(
            card.durationText to card.labels.duration,
            card.setsText to card.labels.sets,
            card.volumeText to card.labels.volume,
        )
        // Each value stays on one line: a value wider than its column ("10,283.75 lb") shrinks all three alike.
        var valueStyle = styles.statValue
        for (attempt in 1..FIT_ATTEMPTS) {
            val widest = pairs.maxOf { (value, _) -> measure(value, valueStyle, Int.MAX_VALUE, maxLines = 1).size.width }
            if (widest <= statWidth) break
            val fit = statWidth.toFloat() / (widest + 1)
            valueStyle = valueStyle.copy(fontSize = valueStyle.fontSize * fit, lineHeight = valueStyle.lineHeight * fit)
        }
        val stats = pairs.map { (value, label) ->
            measure(value, valueStyle, statWidth, maxLines = 1) to measure(label.uppercase(), styles.statLabel, statWidth)
        }
        val statsHeight = stats.maxOf { (value, label) -> value.size.height + px(space.s1) + label.size.height }
        val ruleY = statsTop + statsHeight + px(space.s5)
        val height = ruleY + px(sizes.border) + px(space.s4)
        return Header(height, brandM, brandRest, brandTop, plateScale, brandGap, brandBounds, title, titleTop, date,
            dateTop, stats, statsTop, statWidth + gap, ruleY)
    }

    private fun DrawScope.drawHeader(header: Header) {
        val x = margin.toFloat()
        val top = header.brandTop.toFloat()
        drawText(header.brandM, colors.textPrimary, Offset(x, top))
        val platesX = x + header.brandM.size.width + header.brandGap
        val baseline = top + header.brandM.firstBaseline
        drawPlates(Offset(platesX, baseline - PLATE_BASELINE * header.plateScale), header.plateScale)
        drawText(header.brandRest, colors.textPrimary,
            Offset(platesX + PLATES_WIDTH * header.plateScale + header.brandGap, top))
        drawText(header.title, colors.textPrimary, Offset(x, header.titleTop.toFloat()))
        drawText(header.date, colors.textSecondary, Offset(x, header.dateTop.toFloat()))
        header.stats.forEachIndexed { i, (value, label) ->
            val sx = x + i * header.statWidth
            drawText(value, colors.textPrimary, Offset(sx, header.statsTop.toFloat()))
            drawText(label, colors.textSecondary, Offset(sx, (header.statsTop + value.size.height + px(space.s1)).toFloat()))
        }
        drawRect(colors.borderSubtle, Offset(x, header.ruleY.toFloat()), Size(inner.toFloat(), px(sizes.border).toFloat()))
    }

    /** The "OO" of the brand: two chamfered plates joined by a handle with collars (the phase 2 board). */
    private fun DrawScope.drawPlates(topLeft: Offset, scale: Float) {
        translate(topLeft.x, topLeft.y) {
            scale(scale, scale, pivot = Offset.Zero) {
                val grip = colors.textSecondary
                drawRoundRect(grip, Offset(90f, 63.83f), Size(30f, 14.04f), CornerRadius(3f))
                drawRoundRect(grip, Offset(92f, 55.64f), Size(6f, 30.43f), CornerRadius(2f))
                drawRoundRect(grip, Offset(112f, 55.64f), Size(6f, 30.43f), CornerRadius(2f))
                for ((outer, ring, hole) in plates) {
                    drawPath(outer, colors.accentSolid)
                    drawPath(ring, colors.bgBase, alpha = RING_ALPHA, style = Stroke(width = RING_STROKE))
                    drawOval(colors.bgBase, Offset(hole.x - HOLE_RX, hole.y - HOLE_RY), Size(HOLE_RX * 2, HOLE_RY * 2))
                }
            }
        }
    }

    /** The body: sections, records and exercises, as blocks that can be sliced at line boundaries. */
    private fun blocks(card: ShareCardData, measure: Measure): List<Block> {
        val result = mutableListOf<Block>()
        // Rows are dense, as in a table: an exercise row is as tall as two lines of its name, which
        // also hold its best set with the completed sets below it.
        val pad = px(space.s1)
        val icon = px(sizes.icon16)
        val iconGap = px(space.s2)
        val right = margin + inner
        if (card.records.isNotEmpty()) {
            result += section(card.labels.records, null, colors.prText, measure)
            val label = measure(card.labels.recordBadge, styles.badge, inner)
            val badge = Badge(label, px(space.s2) * 2 + icon + px(space.s1) + label.size.width,
                max(icon, label.size.height) + px(space.s1) * 2)
            card.records.forEachIndexed { index, record ->
                val value = measure(record.detail, styles.rowValue, inner * VALUE_MAX_PERCENT / 100)
                val nameX = margin + badge.width + px(space.s3)
                val name = measure(record.name, styles.rowName, right - rowGap - value.size.width - nameX)
                // The badge is taller than one line of the name: the first part makes room for it.
                result += Block(ShareCardSlice.Kind.Record, index, name, nameX, colors.textPrimary, value, right,
                    colors.prText, badge = badge, padTop = pad, padBottom = pad,
                    nameExtra = max(0, badge.height - lineHeight(name, 0, 1)))
            }
            result += spacer(px(space.s6))
        }
        result += section(card.labels.exercises, card.labels.bestSet, colors.textSecondary, measure)
        if (card.rows.isEmpty()) {
            result += Block(ShareCardSlice.Kind.Empty, -1, measure(card.labels.noCompletedSets, styles.date, inner),
                margin, colors.textSecondary, null, right, colors.textSecondary, padTop = pad, padBottom = pad)
        }
        card.rows.forEachIndexed { index, row ->
            val value = measure(row.bestSet, styles.rowValue, inner * VALUE_MAX_PERCENT / 100)
            val sets = fillTemplate(if (row.completedSets == 1) card.labels.setCountOne else card.labels.setCountOther,
                row.completedSets)
            val caption = measure(sets, styles.rowCaption, inner * VALUE_MAX_PERCENT / 100)
            val iconWidth = if (row.hasRecord) icon + iconGap else 0
            val valueColumn = max(value.size.width + iconWidth, caption.size.width)
            val name = measure(row.name, styles.rowName, inner - rowGap - valueColumn)
            result += Block(ShareCardSlice.Kind.Exercise, index, name, margin, colors.textPrimary, value, right,
                if (row.hasRecord) colors.prText else colors.textPrimary, caption = caption, valueIcon = row.hasRecord,
                padTop = pad, padBottom = pad, rule = index < card.rows.lastIndex, valueExtra = caption.size.height)
        }
        return result
    }

    private fun section(left: String, right: String?, color: Color, measure: Measure): Block {
        val value = right?.let { measure(it.uppercase(), styles.section, inner / 2) }
        val name = measure(left.uppercase(), styles.section, inner - (value?.size?.width ?: 0) - rowGap)
        return Block(ShareCardSlice.Kind.Section, -1, name, margin, color, value, margin + inner, color,
            padBottom = px(space.s2))
    }

    private fun spacer(height: Int) = Block(ShareCardSlice.Kind.Spacer, -1, null, 0, Color.Unspecified, null, 0,
        Color.Unspecified, fixedHeight = height)

    private fun lineHeight(layout: TextLayoutResult?, start: Int, end: Int): Int =
        if (layout == null || start >= end) 0
        else (layout.getLineBottom(end - 1) - layout.getLineTop(start)).roundUp()

    private fun Float.roundUp(): Int = ceil(this).toInt()

    private fun slice(block: Block, nameStart: Int, nameEnd: Int, valueStart: Int, valueEnd: Int, first: Boolean): ShareCardSlice {
        val nameHeight = lineHeight(block.name, nameStart, nameEnd) + if (first) block.nameExtra else 0
        val valueHeight = lineHeight(block.value, valueStart, valueEnd) + if (first) block.valueExtra else 0
        val height = block.fixedHeight + block.padding + max(nameHeight, valueHeight) +
            if (block.rule) px(sizes.border) else 0
        return ShareCardSlice(block.kind, block.index, first, nameStart, nameEnd, valueStart, valueEnd,
            block.nameLines, height, block)
    }

    private fun fittingEnd(layout: TextLayoutResult?, start: Int, available: Int): Int {
        if (layout == null) return 0
        var end = start
        while (end < layout.lineCount && (layout.getLineBottom(end) - layout.getLineTop(start)) <= available) end++
        return end
    }

    private fun DrawScope.drawSlice(slice: ShareCardSlice, top: Float) {
        val block = slice.block
        val y = top + block.padTop
        val name = block.name
        if (slice.first && block.badge != null && name != null) {
            val firstLine = name.getLineBottom(0) - name.getLineTop(0)
            drawBadge(block.badge, Offset(margin.toFloat(), y + (firstLine - block.badge.height) / 2f))
        }
        if (name != null) drawLines(name, block.nameX.toFloat(), y, slice.nameStart, slice.nameEnd, block.nameColor)
        val value = block.value
        if (value != null) {
            val x = (block.valueRight - value.size.width).toFloat()
            val height = drawLines(value, x, y, slice.valueStart, slice.valueEnd, block.valueColor)
            if (slice.first && block.valueIcon) {
                val icon = px(sizes.icon16).toFloat()
                val firstLine = value.getLineBottom(0) - value.getLineTop(0)
                drawRecordIcon(Offset(x - px(space.s2) - icon, y + (firstLine - icon) / 2f), icon, colors.prSolid)
            }
            val caption = block.caption
            if (slice.first && caption != null) {
                drawText(caption, colors.textSecondary,
                    Offset((block.valueRight - caption.size.width).toFloat(), y + height))
            }
        }
        if (block.rule && slice.last) {
            val border = px(sizes.border).toFloat()
            drawRect(colors.borderSubtle, Offset(margin.toFloat(), top + slice.height - border), Size(inner.toFloat(), border))
        }
    }

    /** Draws lines [start, end) of [layout] with their top at [y]; returns their height. */
    private fun DrawScope.drawLines(layout: TextLayoutResult, x: Float, y: Float, start: Int, end: Int, color: Color): Float {
        if (start >= end) return 0f
        val lineTop = layout.getLineTop(start)
        val height = layout.getLineBottom(end - 1) - lineTop
        clipRect(x, y, x + layout.size.width, y + height) {
            drawText(layout, color, Offset(x, y - lineTop))
        }
        return height
    }

    /** The record badge of the design system (`PrBadge`): a `pr.solid` pill with the record icon and "PR". */
    private fun DrawScope.drawBadge(badge: Badge, topLeft: Offset) {
        drawRoundRect(colors.prSolid, topLeft, Size(badge.width.toFloat(), badge.height.toFloat()),
            CornerRadius(badge.height / 2f))
        val icon = px(sizes.icon16).toFloat()
        val iconX = topLeft.x + px(space.s2)
        drawRecordIcon(Offset(iconX, topLeft.y + (badge.height - icon) / 2f), icon, colors.prOn)
        drawText(badge.label, colors.prOn, Offset(iconX + icon + px(space.s1),
            topLeft.y + (badge.height - badge.label.size.height) / 2f))
    }

    /** The design system's `PlateRecord` icon (a plate with an arrow up), on its 24 grid. */
    private fun DrawScope.drawRecordIcon(topLeft: Offset, size: Float, color: Color) {
        val unit = size / ICON_GRID
        val stroke = Stroke(width = ICON_STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(topLeft.x + x * unit, topLeft.y + y * unit)
        drawCircle(color, 6.5f * unit, at(9f, 15f), style = stroke)
        drawCircle(color, 2.5f * unit, at(9f, 15f), style = stroke)
        drawLine(color, at(19f, 11f), at(19f, 3f), stroke.width, StrokeCap.Round)
        drawLine(color, at(16f, 6f), at(19f, 3f), stroke.width, StrokeCap.Round)
        drawLine(color, at(19f, 3f), at(22f, 6f), stroke.width, StrokeCap.Round)
    }

    companion object {
        const val WIDTH = 1080
        const val MAX_PAGE_HEIGHT = 4096

        /** The least body a page must hold for the card to be drawn at all. */
        private const val MIN_BODY = 400
        /** The widest a best set or a record's load may be before it wraps, in percent of the content width. */
        private const val VALUE_MAX_PERCENT = 45
        /** Shrinking a statistic converges in one or two steps (glyph widths do not scale exactly). */
        private const val FIT_ATTEMPTS = 4

        private const val BRAND_FIRST = "M"
        private const val BRAND_REST = "SCLES"
        private const val CAP_HEIGHT_EM = 0.7f
        private const val BRAND_GAP_EM = 6f / 132f

        // The plates in the coordinates of the board's SVG (viewBox 210 x 127.66).
        private const val PLATES_WIDTH = 210f
        /** From the top of the plates (21.7) to their bottom on the baseline (120). */
        private const val PLATE_RUN = 120f - 21.7f
        private const val PLATE_BASELINE = 120f
        private const val RING_ALPHA = 0.35f
        private const val RING_STROKE = 3f
        private const val HOLE_RX = 10f
        private const val HOLE_RY = 11.7f
        private const val ICON_GRID = 24f
        private const val ICON_STROKE = 2f

        private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

        private val plates: List<Triple<Path, Path, Offset>> by lazy {
            listOf(
                Triple(
                    path("M17.75 25.50Q21.00 21.70 26.00 21.70L70.00 21.70Q75.00 21.70 78.25 25.50L86.75 35.45Q90.00 39.25 90.00 44.25L90.00 97.45Q90.00 102.45 86.75 106.25L78.25 116.20Q75.00 120.00 70.00 120.00L26.00 120.00Q21.00 120.00 17.75 116.20L9.25 106.25Q6.00 102.45 6.00 97.45L6.00 44.25Q6.00 39.25 9.25 35.45Z"),
                    path("M25.73 38.40Q28.00 35.74 31.50 35.74L64.50 35.74Q68.00 35.74 70.27 38.40L75.73 44.79Q78.00 47.45 78.00 50.95L78.00 90.76Q78.00 94.26 75.73 96.92L70.27 103.30Q68.00 105.96 64.50 105.96L31.50 105.96Q28.00 105.96 25.73 103.30L20.27 96.92Q18.00 94.26 18.00 90.76L18.00 50.95Q18.00 47.45 20.27 44.79Z"),
                    Offset(48f, 70.85f),
                ),
                Triple(
                    path("M131.75 25.50Q135.00 21.70 140.00 21.70L184.00 21.70Q189.00 21.70 192.25 25.50L200.75 35.45Q204.00 39.25 204.00 44.25L204.00 97.45Q204.00 102.45 200.75 106.25L192.25 116.20Q189.00 120.00 184.00 120.00L140.00 120.00Q135.00 120.00 131.75 116.20L123.25 106.25Q120.00 102.45 120.00 97.45L120.00 44.25Q120.00 39.25 123.25 35.45Z"),
                    path("M139.73 38.40Q142.00 35.74 145.50 35.74L178.50 35.74Q182.00 35.74 184.27 38.40L189.73 44.79Q192.00 47.45 192.00 50.95L192.00 90.76Q192.00 94.26 189.73 96.92L184.27 103.30Q182.00 105.96 178.50 105.96L145.50 105.96Q142.00 105.96 139.73 103.30L134.27 96.92Q132.00 94.26 132.00 90.76L132.00 50.95Q132.00 47.45 134.27 44.79Z"),
                    Offset(162f, 70.85f),
                ),
            )
        }
    }
}

/** The measured header of a card, the same on every page. */
internal class Header(
    val height: Int,
    val brandM: TextLayoutResult,
    val brandRest: TextLayoutResult,
    val brandTop: Int,
    val plateScale: Float,
    val brandGap: Float,
    val brandBounds: IntRect,
    val title: TextLayoutResult,
    val titleTop: Int,
    val date: TextLayoutResult,
    val dateTop: Int,
    val stats: List<Pair<TextLayoutResult, TextLayoutResult>>,
    val statsTop: Int,
    val statWidth: Int,
    val ruleY: Int,
)

/** One body row of the card before it is split into page parts. */
internal class Block(
    val kind: ShareCardSlice.Kind,
    val index: Int,
    val name: TextLayoutResult?,
    val nameX: Int,
    val nameColor: Color,
    val value: TextLayoutResult?,
    val valueRight: Int,
    val valueColor: Color,
    /** Under the value, on the first part only (the sets of an exercise). */
    val caption: TextLayoutResult? = null,
    /** The badge left of the name, on the first part only (a record). */
    val badge: Badge? = null,
    /** The record icon left of the value, on the first part only. */
    val valueIcon: Boolean = false,
    val padTop: Int = 0,
    val padBottom: Int = 0,
    val rule: Boolean = false,
    val fixedHeight: Int = 0,
    /** Height the first part adds under the name's lines (room for a badge taller than a line). */
    val nameExtra: Int = 0,
    /** Height the first part adds under the value's lines (the caption). */
    val valueExtra: Int = 0,
) {
    val nameLines get() = name?.lineCount ?: 0
    val valueLines get() = value?.lineCount ?: 0
    val padding get() = padTop + padBottom
}

internal class Badge(val label: TextLayoutResult, val width: Int, val height: Int)
