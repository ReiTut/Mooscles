package com.mooscles.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only Android-aware file of this package: reads and writes documents the user picked. */
object BackupDocuments {
    suspend fun write(context: Context, uri: String, text: String) = withContext(Dispatchers.IO) {
        val document = Uri.parse(uri)
        // "wt" truncates, so replacing a longer file leaves no trailing bytes behind. Some providers reject
        // that mode; the document was just created by the picker, so plain "w" leaves nothing behind either.
        val output = try {
            context.contentResolver.openOutputStream(document, "wt")
        } catch (e: IllegalArgumentException) {
            context.contentResolver.openOutputStream(document, "w")
        } catch (e: FileNotFoundException) {
            context.contentResolver.openOutputStream(document, "w")
        } catch (e: UnsupportedOperationException) {
            context.contentResolver.openOutputStream(document, "w")
        } ?: throw IOException("Could not open the selected file.")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Best effort after a failed export: removes the empty or partial document the picker created. */
    suspend fun discard(context: Context, uri: String) = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))
        } catch (e: Exception) {
            // Not a deletable document, or the provider refused: the failure message already went out.
        }
    }

    suspend fun read(context: Context, uri: String, maxBytes: Long = 64L * 1024 * 1024): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Could not open the selected file.")
        // Decode while reading: a binary file fails on its first bad bytes and a huge one at maxBytes,
        // without buffering the whole document first.
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        InputStreamReader(LimitedInputStream(input, maxBytes), decoder).use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(16 * 1024)
            try {
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.append(buffer, 0, read)
                }
            } catch (e: CharacterCodingException) {
                throw BackupException.Corrupted("document is not valid UTF-8")
            }
            text.toString()
        }
    }

    /** Throws [BackupException.Corrupted] as soon as more than [maxBytes] bytes have been read. */
    private class LimitedInputStream(input: InputStream, private val maxBytes: Long) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it) }

        private fun count(bytes: Int) {
            total += bytes
            if (total > maxBytes) throw BackupException.Corrupted("document is larger than $maxBytes bytes")
        }
    }
}

fun suggestedBackupFileName(date: LocalDate): String = "mooscles-backup-$date.json"
