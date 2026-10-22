package com.mooscles.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A self-contained image, measured from saved workout data rather than a screen capture. */
class WorkoutCardRenderer {
    /** Call on a background dispatcher. The receiver can retain these files after the chooser closes. */
    fun export(context: Context, card: WorkoutShareCard): List<File> {
        val root = shareDirectory(context)
        check(root.isDirectory || root.mkdirs()) { "Could not create workout sharing cache" }
        cleanupObsoleteExports(context)
        val directory = File(root, "export-${UUID.randomUUID()}")
        check(directory.mkdir()) { "Could not create workout export" }
        return try {
            val pages = layout(card)
            pages.mapIndexed { index, page ->
                val bitmap = Bitmap.createBitmap(WIDTH, page.height, Bitmap.Config.ARGB_8888)
                try {
                    draw(Canvas(bitmap), card, page, index, pages.size)
                    val file = File(directory, "Mooscles-workout-${card.workoutNumber}-${index + 1}.png")
                    file.outputStream().buffered().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            throw IOException("Could not encode workout image")
                        }
                    }
                    file
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (error: Exception) {
            deleteExport(directory, root)
            throw error
        }
    }

    /** Only old, app-owned export directories are removed, never an export just handed to another app. */
    fun cleanupObsoleteExports(context: Context, nowMillis: Long = System.currentTimeMillis()): Int {
        val root = shareDirectory(context)
        val cutoff = nowMillis - RETENTION_MILLIS
        return root.listFiles().orEmpty().count { directory ->
            directory.name.startsWith("export-") && directory.isDirectory &&
                directory.lastModified() in 1 until cutoff && deleteExport(directory, root)
        }
    }

    internal fun layout(card: WorkoutShareCard): List<CardPage> {
        val title = text(card.workoutName, 48f, bold = true, width = INNER_WIDTH)
        val date = text(card.dateTime, 30f, width = INNER_WIDTH, color = MUTED)
        val rowsTop = CARD_TOP + 34 + title.height + 12 + date.height + 34 + 38 + 26
        val stats = listOf(
            statistic(card.durationText, "Duration"),
            statistic(card.volumeText, "Weight moved"),
            statistic(card.prCount.toString(), "PR exercises"),
        )
        val statsHeight = stats.maxOf { it.value.height + 10 + it.label.height }
        val footerHeight = 30 + statsHeight + 34
        val outsideFooter = 78
        val capacity = MAX_PAGE_HEIGHT - rowsTop - footerHeight - outsideFooter
        require(capacity >= 160) { "The workout title is too long to fit in a share image" }
        val pages = mutableListOf<CardPage>()
        var slices = mutableListOf<RowSlice>()
        var used = 0
        fun finishPage() {
            val bodyHeight = if (card.rows.isEmpty()) 100 else used
            pages += CardPage(title, date, rowsTop, stats, footerHeight,
                slices.toList(), bodyHeight, rowsTop + bodyHeight + footerHeight + outsideFooter)
            slices = mutableListOf()
            used = 0
        }
        card.rows.forEachIndexed { index, row ->
            val measured = MeasuredRow(index, row, text(row.exerciseName, 36f, width = NAME_WIDTH),
                text(row.bestSetText, 35f, bold = true, width = BEST_WIDTH))
            val full = slice(measured, 0, measured.name.lineCount, 0, measured.best.lineCount, first = true)
            if (full.height <= capacity) {
                if (used + full.height > capacity && slices.isNotEmpty()) finishPage()
                slices += full
                used += full.height
            } else {
                // Very long individual names/values are continued at line boundaries, without truncation.
                if (slices.isNotEmpty()) finishPage()
                var nameLine = 0
                var bestLine = 0
                var first = true
                while (nameLine < measured.name.lineCount || bestLine < measured.best.lineCount) {
                    val badge = if (first && row.hasPr) BADGE_HEIGHT + 12 else 0
                    val nameEnd = fittingEnd(measured.name, nameLine, capacity - ROW_PADDING - badge)
                    val bestEnd = fittingEnd(measured.best, bestLine, capacity - ROW_PADDING)
                    val part = slice(measured, nameLine, nameEnd, bestLine, bestEnd, first)
                    check(part.height <= capacity) { "Exercise row exceeds image height" }
                    slices += part
                    used += part.height
                    nameLine = nameEnd
                    bestLine = bestEnd
                    first = false
                    if (nameLine < measured.name.lineCount || bestLine < measured.best.lineCount) finishPage()
                }
            }
        }
        if (slices.isNotEmpty() || pages.isEmpty()) finishPage()
        return pages
    }

    private fun draw(canvas: Canvas, card: WorkoutShareCard, page: CardPage, index: Int, count: Int) {
        canvas.drawColor(BACKGROUND)
        star(canvas, WIDTH / 2f, 88f, 43f, GREEN)
        star(canvas, WIDTH / 2f - 100, 94f, 19f, GREEN)
        star(canvas, WIDTH / 2f + 100, 94f, 19f, GREEN)
        star(canvas, WIDTH / 2f - 160, 66f, 9f, MUTED)
        star(canvas, WIDTH / 2f + 162, 119f, 10f, MUTED)
        centered(canvas, "Congratulations!", 196f, 62f, WHITE, bold = true)
        centered(canvas, "WORKOUT #${card.workoutNumber}", 255f, 31f, MUTED)

        val bottom = page.rowsTop + page.bodyHeight + page.footerHeight
        val cardRect = RectF(56f, CARD_TOP.toFloat(), WIDTH - 56f, bottom.toFloat())
        canvas.drawRoundRect(cardRect, 32f, 32f, paint(PANEL))
        canvas.drawRoundRect(cardRect, 32f, 32f, paint(BORDER).apply { style = Paint.Style.STROKE; strokeWidth = 2f })
        block(canvas, page.title, INNER_X.toFloat(), CARD_TOP + 34f)
        block(canvas, page.date, INNER_X.toFloat(), CARD_TOP + 34f + page.title.height + 12)
        val columnsY = page.rowsTop - 26f - 38f
        label(canvas, "Completed sets", INNER_X.toFloat(), columnsY + 28, 29f, MUTED)
        label(canvas, "Best set", BEST_X.toFloat(), columnsY + 28, 29f, MUTED)
        canvas.drawLine(INNER_X.toFloat(), page.rowsTop - 10f, (WIDTH - INNER_X).toFloat(),
            page.rowsTop - 10f, paint(BORDER).apply { strokeWidth = 2f })
        var top = page.rowsTop.toFloat()
        page.rows.forEach { row ->
            val textY = top + 18
            if (row.first) {
                val countText = "${row.measured.row.completedSets} ×"
                val countPaint = textPaint(34f, MUTED)
                val width = countPaint.measureText(countText)
                if (width > NAME_X - INNER_X - 12) countPaint.textSize *= (NAME_X - INNER_X - 12) / width
                canvas.drawText(countText, INNER_X.toFloat(), textY + 34, countPaint)
            }
            val nameHeight = drawLines(canvas, row.measured.name, NAME_X.toFloat(), textY, row.nameStart, row.nameEnd)
            drawLines(canvas, row.measured.best, BEST_X.toFloat(), textY, row.bestStart, row.bestEnd)
            if (row.first && row.measured.row.hasPr) {
                val badgeY = textY + nameHeight + 12
                canvas.drawRoundRect(RectF(NAME_X.toFloat(), badgeY, NAME_X + 80f, badgeY + BADGE_HEIGHT),
                    15f, 15f, paint(PR_BACKGROUND))
                star(canvas, NAME_X + 17f, badgeY + BADGE_HEIGHT / 2f, 8f, GREEN)
                label(canvas, "PR", NAME_X + 33f, badgeY + 25f, 23f, GREEN, bold = true)
            }
            top += row.height
            canvas.drawLine(INNER_X.toFloat(), top, (WIDTH - INNER_X).toFloat(), top,
                paint(BORDER).apply { strokeWidth = 1f })
        }
        if (card.rows.isEmpty()) label(canvas, "No completed sets", INNER_X.toFloat(), top + 53, 32f, MUTED)
        val statsTop = page.rowsTop + page.bodyHeight + 30f
        page.stats.forEachIndexed { statIndex, statistic ->
            val x = INNER_X + statIndex * STAT_STEP
            block(canvas, statistic.value, x.toFloat(), statsTop)
            block(canvas, statistic.label, x.toFloat(), statsTop + statistic.value.height + 10)
        }
        label(canvas, "mooscles", INNER_X.toFloat(), bottom + 49f, 26f, GREEN, bold = true)
        if (count > 1) {
            val pageText = "${index + 1} / $count"
            val pagePaint = textPaint(25f, MUTED)
            canvas.drawText(pageText, WIDTH - INNER_X - pagePaint.measureText(pageText), bottom + 49f, pagePaint)
        }
    }

    private fun slice(row: MeasuredRow, nameStart: Int, nameEnd: Int, bestStart: Int, bestEnd: Int, first: Boolean): RowSlice {
        val nameHeight = lineHeight(row.name, nameStart, nameEnd)
        val badge = if (first && row.row.hasPr) BADGE_HEIGHT + 12 else 0
        return RowSlice(row, nameStart, nameEnd, bestStart, bestEnd, first,
            ROW_PADDING + max(nameHeight + badge, lineHeight(row.best, bestStart, bestEnd)))
    }

    private fun fittingEnd(layout: StaticLayout, start: Int, available: Int): Int {
        var end = start
        while (end < layout.lineCount && lineHeight(layout, start, end + 1) <= available) end++
        return end
    }

    private fun lineHeight(layout: StaticLayout, start: Int, end: Int): Int =
        if (start == end) 0 else layout.getLineBottom(end - 1) - layout.getLineTop(start)

    private fun drawLines(canvas: Canvas, layout: StaticLayout, x: Float, y: Float, start: Int, end: Int): Int {
        val height = lineHeight(layout, start, end)
        if (height > 0) {
            canvas.save()
            canvas.clipRect(x, y, x + layout.width, y + height)
            canvas.translate(x, y - layout.getLineTop(start))
            layout.draw(canvas)
            canvas.restore()
        }
        return height
    }

    private fun statistic(value: String, name: String) = Statistic(
        text(value, 39f, bold = true, width = STAT_WIDTH, color = GREEN),
        text(name, 26f, width = STAT_WIDTH, color = MUTED),
    )

    private fun text(value: String, size: Float, bold: Boolean = false, width: Int, color: Int = WHITE): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, textPaint(size, color, bold), width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(4f, 1f).setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()

    private fun block(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun label(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) =
        canvas.drawText(value, x, baseline, textPaint(size, color, bold))

    private fun centered(canvas: Canvas, value: String, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = textPaint(size, color, bold)
        canvas.drawText(value, (WIDTH - paint.measureText(value)) / 2f, baseline, paint)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        val path = Path()
        repeat(10) { point ->
            val angle = -Math.PI / 2 + point * Math.PI / 5
            val length = radius * if (point % 2 == 0) 1f else 0.45f
            val px = x + cos(angle).toFloat() * length
            val py = y + sin(angle).toFloat() * length
            if (point == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint(color))
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun shareDirectory(context: Context) = File(context.cacheDir, "workout-shares")

    private fun deleteExport(directory: File, root: File): Boolean {
        val canonicalRoot = root.canonicalFile
        if (directory.parentFile?.canonicalFile != canonicalRoot || !directory.name.startsWith("export-")) return false
        if (Files.isSymbolicLink(directory.toPath())) return directory.delete()
        fun remove(file: File): Boolean {
            if (Files.isSymbolicLink(file.toPath())) return file.delete()
            if (!file.canonicalPath.startsWith(directory.canonicalPath + File.separator) && file != directory) {
                // Delete a link itself without following it outside this managed directory.
                return file.delete()
            }
            if (file.isDirectory && !file.listFiles().orEmpty().all(::remove)) return false
            return file.delete()
        }
        return remove(directory)
    }

    internal data class MeasuredRow(val index: Int, val row: ShareExerciseRow, val name: StaticLayout, val best: StaticLayout)
    internal data class RowSlice(val measured: MeasuredRow, val nameStart: Int, val nameEnd: Int,
        val bestStart: Int, val bestEnd: Int, val first: Boolean, val height: Int)
    internal data class Statistic(val value: StaticLayout, val label: StaticLayout)
    internal data class CardPage(val title: StaticLayout, val date: StaticLayout, val rowsTop: Int,
        val stats: List<Statistic>, val footerHeight: Int, val rows: List<RowSlice>, val bodyHeight: Int, val height: Int)

    companion object {
        const val WIDTH = 1080
        const val MAX_PAGE_HEIGHT = 4096
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val CARD_TOP = 314
        private const val INNER_X = 92
        private const val INNER_WIDTH = WIDTH - INNER_X * 2
        private const val NAME_X = 178
        private const val NAME_WIDTH = 484
        private const val BEST_X = 712
        private const val BEST_WIDTH = WIDTH - INNER_X - BEST_X
        private const val STAT_STEP = 305
        private const val STAT_WIDTH = 278
        private const val ROW_PADDING = 42
        private const val BADGE_HEIGHT = 34
        private val BACKGROUND = Color.rgb(16, 23, 19)
        private val PANEL = Color.rgb(22, 33, 26)
        private val BORDER = Color.rgb(52, 68, 58)
        private val GREEN = Color.rgb(131, 219, 176)
        private val WHITE = Color.rgb(239, 245, 239)
        private val MUTED = Color.rgb(180, 195, 183)
        private val PR_BACKGROUND = Color.rgb(28, 65, 46)
    }
}
