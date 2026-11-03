package com.mooscles.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.ExifInterface
import java.io.File
import kotlin.random.Random

/**
 * Test images for the photo pipeline. The upright picture has four quadrant colours (red, green / blue, yellow);
 * the stored pixels are written already transformed for each EXIF orientation by a hand-made
 * table, so the pipeline's own matrix is never used to build its input.
 */
internal object PhotoTestImages {
    private const val RED = 0xFFE53935.toInt()
    private const val GREEN = 0xFF43A047.toInt()
    private const val BLUE = 0xFF1E88E5.toInt()
    private const val YELLOW = 0xFFFDD835.toInt()
    private val UPRIGHT = listOf(RED, GREEN, BLUE, YELLOW)

    /** Stored quadrant colours (TL, TR, BL, BR) for the upright TL red, TR green, BL blue, BR yellow. */
    private fun stored(orientation: Int): List<Int> = when (orientation) {
        1 -> listOf(RED, GREEN, BLUE, YELLOW)
        2 -> listOf(GREEN, RED, YELLOW, BLUE)
        3 -> listOf(YELLOW, BLUE, GREEN, RED)
        4 -> listOf(BLUE, YELLOW, RED, GREEN)
        5 -> listOf(RED, BLUE, GREEN, YELLOW)
        6 -> listOf(GREEN, YELLOW, RED, BLUE)
        7 -> listOf(YELLOW, GREEN, BLUE, RED)
        8 -> listOf(BLUE, RED, YELLOW, GREEN)
        else -> error("orientation $orientation")
    }

    /** SHAPES: drawn detail; NOISE: ±60 per channel; FULL_NOISE: every pixel random (JPEG's worst case). */
    enum class Detail { SHAPES, NOISE, FULL_NOISE }

    /**
     * Writes a stored [width] × [height] image whose EXIF says [orientation]. JPEG unless [png];
     * the PNG gets a transparent vertical band through the centre (it must come out white).
     */
    fun write(
        file: File,
        width: Int,
        height: Int,
        orientation: Int,
        png: Boolean = false,
        detail: Detail = Detail.SHAPES,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = stored(orientation)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val halfW = width / 2f
        val halfH = height / 2f
        colors.forEachIndexed { i, c ->
            paint.color = c
            val x = if (i % 2 == 0) 0f else halfW
            val y = if (i < 2) 0f else halfH
            canvas.drawRect(x, y, x + halfW, y + halfH, paint)
        }
        val random = Random(orientation * 31 + width)
        when (detail) {
            Detail.SHAPES -> repeat(2500) {
                paint.color = Color.argb(90, random.nextInt(256), random.nextInt(256), random.nextInt(256))
                paint.strokeWidth = 1f + random.nextFloat() * width / 400f
                val x = random.nextFloat() * width
                val y = random.nextFloat() * height
                when (it % 3) {
                    0 -> canvas.drawLine(x, y, random.nextFloat() * width, random.nextFloat() * height, paint)
                    1 -> canvas.drawCircle(x, y, random.nextFloat() * width / 40f, paint)
                    else -> canvas.drawRect(x, y, x + random.nextFloat() * width / 30f, y + random.nextFloat() * height / 30f, paint)
                }
            }
            Detail.FULL_NOISE -> {
                val row = IntArray(width)
                for (y in 0 until height) {
                    for (x in 0 until width) row[x] = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    bitmap.setPixels(row, 0, width, 0, y, width, 1)
                }
            }
            Detail.NOISE -> {
                val row = IntArray(width)
                for (y in 0 until height) {
                    bitmap.getPixels(row, 0, width, 0, y, width, 1)
                    for (x in 0 until width) {
                        val n = random.nextInt(-60, 61)
                        val p = row[x]
                        row[x] = Color.rgb(
                            (Color.red(p) + n).coerceIn(0, 255),
                            (Color.green(p) + random.nextInt(-60, 61)).coerceIn(0, 255),
                            (Color.blue(p) + random.nextInt(-60, 61)).coerceIn(0, 255),
                        )
                    }
                    bitmap.setPixels(row, 0, width, 0, y, width, 1)
                }
            }
        }
        // Clean disks at the four sample points so the check reads a solid colour.
        val radius = minOf(width, height) * 0.08f
        colors.forEachIndexed { i, c ->
            paint.color = c
            canvas.drawCircle(if (i % 2 == 0) width * 0.25f else width * 0.75f, if (i < 2) height * 0.25f else height * 0.75f, radius, paint)
        }
        if (png) {
            val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawRect(width * 0.45f, 0f, width * 0.55f, height.toFloat(), clear)
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 95, it) }
        bitmap.recycle()
        if (!png && orientation != 1) {
            ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        }
        return file
    }

    /** True if [jpeg] shows red, green / blue, yellow at its four quadrant centres. */
    fun upright(jpeg: ByteArray): Boolean = quadrants(jpeg) == UPRIGHT

    /** Nearest reference colour at each quadrant centre (TL, TR, BL, BR). */
    fun quadrants(jpeg: ByteArray): List<Int> {
        val b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val points = listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f)
        return points.map { (fx, fy) -> nearest(b.getPixel((b.width * fx).toInt(), (b.height * fy).toInt())) }
            .also { b.recycle() }
    }

    /** Colour at the centre, for the PNG's transparent band. */
    fun center(jpeg: ByteArray): Int {
        val b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        return b.getPixel(b.width / 2, b.height / 2).also { b.recycle() }
    }

    private fun nearest(c: Int): Int = UPRIGHT.minBy {
        val dr = Color.red(it) - Color.red(c)
        val dg = Color.green(it) - Color.green(c)
        val db = Color.blue(it) - Color.blue(c)
        dr * dr + dg * dg + db * db
    }

    fun name(c: Int): String = when (c) { RED -> "red"; GREEN -> "green"; BLUE -> "blue"; YELLOW -> "yellow"; else -> "?" }
}
