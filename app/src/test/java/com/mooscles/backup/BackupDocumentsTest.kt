package com.mooscles.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupDocumentsTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun uri(file: File) = Uri.fromFile(file).toString()

    @Test fun writeThenReadRoundTrips() = runBlocking {
        val file = File(folder.root, "backup.json")
        val text = "{\"name\": \"Supino inclinado — 100 kg ✓\"}"

        BackupDocuments.write(context, uri(file), text)

        assertEquals(text, BackupDocuments.read(context, uri(file)))
    }

    @Test fun writeReplacesLongerExistingContent() = runBlocking {
        val file = File(folder.root, "backup.json")
        file.writeText("x".repeat(500))

        BackupDocuments.write(context, uri(file), "{}")

        assertEquals("{}", BackupDocuments.read(context, uri(file)))
    }

    @Test fun writeFallsBackToPlainWriteWhenTruncateModeIsRejected() = runBlocking {
        val file = File(folder.root, "created.json")
        val provider = Robolectric.buildContentProvider(NoTruncateProvider::class.java).create("com.mooscles.test.notruncate").get()
        provider.target = file

        BackupDocuments.write(context, "content://com.mooscles.test.notruncate/created.json", "{\"ok\":true}")

        assertEquals(listOf("wt", "w"), provider.modes)
        assertEquals("{\"ok\":true}", file.readText())
    }

    @Test fun readRejectsOversizedDocument() = runBlocking {
        val file = File(folder.root, "big.json").apply { writeText("x".repeat(11)) }
        try {
            BackupDocuments.read(context, uri(file), maxBytes = 10)
            fail("A document larger than maxBytes must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
        assertEquals("x".repeat(10), BackupDocuments.read(context, uri(File(folder.root, "ten.json").apply { writeText("x".repeat(10)) }), maxBytes = 10))
    }

    @Test fun readRejectsInvalidUtf8() = runBlocking {
        val file = File(folder.root, "latin1.json").apply { writeBytes(byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte())) }
        try {
            BackupDocuments.read(context, uri(file))
            fail("Invalid UTF-8 must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
        val truncated = File(folder.root, "truncated.json").apply { writeBytes(byteArrayOf('{'.code.toByte(), 0xC3.toByte())) }
        try {
            BackupDocuments.read(context, uri(truncated))
            fail("A sequence cut at the end of the file must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
    }

    @Test fun readRejectsBinaryDocument() = runBlocking {
        // A PNG header followed by 4 MiB of 0xFF: refused on the first bytes, not after buffering everything.
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val file = File(folder.root, "photo.png").apply { writeBytes(png + ByteArray(4 * 1024 * 1024) { 0xFF.toByte() }) }
        try {
            BackupDocuments.read(context, uri(file))
            fail("A binary document must be refused")
        } catch (expected: BackupException.Corrupted) {
        }
    }

    @Test fun suggestedNameUsesLocalDate() {
        assertEquals("mooscles-backup-2026-10-27.json", suggestedBackupFileName(LocalDate.of(2026, 10, 27)))
        assertEquals("mooscles-backup-2027-01-05.json", suggestedBackupFileName(LocalDate.of(2027, 1, 5)))
    }

    /** A document provider that, like some SAF providers, refuses the truncating "wt" mode. */
    class NoTruncateProvider : ContentProvider() {
        lateinit var target: File
        val modes = mutableListOf<String>()

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            modes += mode
            if (mode == "wt") throw FileNotFoundException("mode wt is not supported")
            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)
        }

        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
        override fun getType(uri: Uri): String = "application/json"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
}
