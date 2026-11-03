package com.mooscles.sharing

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import com.mooscles.screens.share.ShareCardData
import com.mooscles.screens.share.ShareCardPainter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Writes the common share card ([ShareCardPainter]) as PNG files in the app's private cache: one
 * file per page, painted and encoded one page at a time so a long workout never holds every
 * bitmap. The images come from saved workout data, never a screen capture.
 */
class WorkoutCardRenderer {
    /**
     * Call on a background dispatcher. The receiver can retain these files after the chooser closes.
     * Cancelling the caller stops the export before the next page and removes what it wrote.
     */
    suspend fun export(context: Context, card: ShareCardData, painter: ShareCardPainter, measurer: TextMeasurer): List<File> {
        val root = shareDirectory(context)
        check(root.isDirectory || root.mkdirs()) { "Could not create workout sharing cache" }
        cleanupObsoleteExports(context)
        val directory = File(root, "export-${UUID.randomUUID()}")
        check(directory.mkdir()) { "Could not create workout export" }
        return try {
            painter.layout(card, measurer).mapIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val bitmap = painter.paint(page).asAndroidBitmap()
                try {
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

    companion object {
        const val WIDTH = ShareCardPainter.WIDTH
        const val MAX_PAGE_HEIGHT = ShareCardPainter.MAX_PAGE_HEIGHT
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
