// The framework ExifInterface reads a stream on minSdk 26, so no androidx.exifinterface dependency is added.
@file:SuppressLint("ExifInterface")

package com.mooscles.photos

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/** Authority-relative folder (under the cache) where the camera writes before a photo is staged. */
const val PHOTO_CAPTURE_DIR = "photo-capture"

/** A processed photo: the upright [full] JPEG and its square [thumb] JPEG. */
internal class ProcessedPhoto(val full: ByteArray, val thumb: ByteArray)

/**
 * Turns a picked or captured image into the stored photo: long side 1080 px, JPEG 85 (stepping down only when the
 * file passes 300 KB), upright, white behind transparency, plus a 192 px centre-square thumbnail. One pipeline on
 * every API (phase 4B spike S4): BitmapFactory with a power-of-two sample, the EXIF orientation from the stream and
 * one Matrix. ImageDecoder is only a fallback on API 29+ for a format BitmapFactory cannot read.
 */
internal object PhotoProcessing {
    const val LONG_SIDE = 1080
    const val THUMB_SIDE = 192
    const val QUALITY = 85
    const val MAX_BYTES = 300 * 1024
    private val QUALITY_STEPS = listOf(QUALITY, 75, 65, 55)

    /**
     * [input] is reopened once per pass (bounds, EXIF, pixels), like a content URI. [sdk] is the API level that picks
     * the path, so a test can run the pre-29 path on a newer runtime. Null if the image cannot be read.
     */
    fun process(input: () -> InputStream, sdk: Int): ProcessedPhoto? = try {
        decodeWithBitmapFactory(input)
            ?: if (sdk >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) decodeWithImageDecoder(input) else null
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        // Malformed input can make the platform decoders throw unchecked exceptions; it is a photo that cannot be read.
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun decodeWithBitmapFactory(input: () -> InputStream): ProcessedPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        input().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = orientationOf(input)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= LONG_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = input().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val scale = min(1f, LONG_SIDE / max(decoded.width, decoded.height).toFloat())
        val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
        val oriented = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return finish(oriented)
    }

    private fun orientationOf(input: () -> InputStream): Int = try {
        // From the stream, not a file descriptor: the descriptor reads nothing more on API 28.
        input().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    } catch (_: RuntimeException) {
        // A malformed EXIF block makes the framework class throw unchecked exceptions on API 26-28: show it as is.
        ExifInterface.ORIENTATION_NORMAL
    }

    /** EXIF orientation as the matrix that turns the stored pixels upright. */
    internal fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }

    /** ImageDecoder reads the orientation itself; [ImageDecoder.Source] size comes out already upright. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun decodeWithImageDecoder(input: () -> InputStream): ProcessedPhoto? {
        val bytes = input().use { it.readBytes() }
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = min(1f, LONG_SIDE / max(width, height).toFloat())
                decoder.setTargetSize(maxOf(1, Math.round(width * scale)), maxOf(1, Math.round(height * scale)))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: IOException) {
            return null
        }
        return finish(bitmap)
    }

    /** Flattens alpha onto white (JPEG has none), encodes, cuts the thumbnail and recycles [bitmap]. */
    private fun finish(bitmap: Bitmap): ProcessedPhoto {
        val opaque = if (bitmap.hasAlpha()) {
            val flat = createBitmap(bitmap.width, bitmap.height)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG)) }
            bitmap.recycle()
            flat
        } else bitmap
        var full = opaque.jpeg(QUALITY)
        for (quality in QUALITY_STEPS.drop(1)) {
            if (full.size <= MAX_BYTES) break
            full = opaque.jpeg(quality)
        }
        val side = min(opaque.width, opaque.height)
        val square = Bitmap.createBitmap(opaque, (opaque.width - side) / 2, (opaque.height - side) / 2, side, side)
        val thumbBitmap = scaleDown(square, THUMB_SIDE)
        val thumb = thumbBitmap.jpeg(QUALITY)
        if (thumbBitmap !== square) thumbBitmap.recycle()
        if (square !== opaque) square.recycle()
        opaque.recycle()
        return ProcessedPhoto(full, thumb)
    }

    /** Halves with bilinear filtering until one last step reaches [target]: no skipped pixels. */
    private fun scaleDown(source: Bitmap, target: Int): Bitmap {
        var current = source
        while (current.width / 2 >= target * 2) {
            val next = current.scale(current.width / 2, current.height / 2)
            if (current !== source) current.recycle()
            current = next
        }
        if (current.width == target && current.height == target) return current
        val last = current.scale(target, target)
        if (current !== source) current.recycle()
        return last
    }

    private fun Bitmap.jpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
