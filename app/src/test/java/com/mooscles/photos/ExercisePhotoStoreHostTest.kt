package com.mooscles.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Android photo store on the pipeline the phase 4B spike chose: sdk 26 runs the BitmapFactory path alone, sdk 35
 * the same path with the ImageDecoder fallback available. Images are generated here (hand-made EXIF table).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 35])
class ExercisePhotoStoreHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sources = File(context.cacheDir, "photo-test-sources")
    private val root = File(context.filesDir, "exercise_photos")
    private lateinit var store: AndroidExercisePhotoStore

    @Before fun setup() {
        root.deleteRecursively()
        sources.deleteRecursively()
        store = AndroidExercisePhotoStore(context, Dispatchers.Unconfined)
    }

    @After fun cleanup() {
        root.deleteRecursively()
        sources.deleteRecursively()
    }

    private fun source(name: String, w: Int = 1600, h: Int = 1200, orientation: Int = 1, detail: PhotoTestImages.Detail = PhotoTestImages.Detail.SHAPES): String =
        Uri.fromFile(PhotoTestImages.write(File(sources, name), w, h, orientation, detail = detail)).toString()

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    @Test fun stageResizesTheLongSideTo1080AndWritesA192Thumb() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        assertEquals(1080 to 810, size(store.draft("d1", false)!!.bytes))
        assertEquals(192 to 192, size(store.draft("d1", true)!!.bytes))
    }

    @Test fun exifRotationIsApplied() = runBlocking {
        assertTrue(store.stage("d1", source("o6.jpg", orientation = 6)))
        val photo = store.draft("d1", false)!!.bytes
        assertEquals(810 to 1080, size(photo))
        assertTrue(PhotoTestImages.upright(photo))
    }

    @Test fun outputIsUnder300KB() = runBlocking {
        // Every pixel random is JPEG's worst case: 85 passes the cap and the quality steps down.
        assertTrue(store.stage("d1", source("noise.jpg", 1440, 1080, detail = PhotoTestImages.Detail.FULL_NOISE)))
        val bytes = store.draft("d1", false)!!.bytes
        assertTrue("${bytes.size} bytes", bytes.size <= 300 * 1024)
        assertEquals(1080 to 810, size(bytes))
    }

    @Test fun undecodableSourceFailsWithoutFiles() = runBlocking {
        val bad = Uri.fromFile(File(sources.apply { mkdirs() }, "bad.jpg").apply { writeBytes(ByteArray(4096) { it.toByte() }) }).toString()
        assertFalse(store.stage("d1", bad))
        assertFalse(store.stage("d2", Uri.fromFile(File(sources, "missing.jpg")).toString()))
        assertNull(store.draft("d1", false))
        assertEquals(emptyList<String>(), root.walkTopDown().filter { it.isFile }.map { it.name }.toList())
        assertEquals(0L, store.revision.value)
    }

    @Test fun commitReplacesAtomically() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg", orientation = 1)))
        assertTrue(store.commit("d1", "ex"))
        val first = store.read("ex", false)!!
        // A commit without a draft changes nothing: the old photo stays whole.
        assertFalse(store.commit("missing", "ex"))
        assertArrayEquals(first.bytes, store.read("ex", false)!!.bytes)
        assertEquals(first.version, store.read("ex", false)!!.version)
        // A replacement: new bytes, a different version, the draft gone and no temporary left over.
        assertTrue(store.stage("d2", source("b.jpg", orientation = 6)))
        assertTrue(store.commit("d2", "ex"))
        val second = store.read("ex", false)!!
        assertFalse(first.bytes.contentEquals(second.bytes))
        assertNotEquals(first.version, second.version)
        assertNull(store.draft("d2", false))
        assertEquals(emptyList<String>(), root.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.toList())
    }

    @Test fun twoCommitsInTheSameClockTickGetDifferentVersions() = runBlocking {
        val versions = mutableListOf<Long>()
        repeat(3) { i ->
            assertTrue(store.stage("d$i", source("p$i.jpg", orientation = if (i % 2 == 0) 1 else 3)))
            // Whatever the file system's granularity, give every draft the same timestamp (rename keeps it).
            listOf(File(root, "drafts/d$i.jpg"), File(root, "drafts/thumbs/d$i.jpg")).forEach { it.setLastModified(1_700_000_000_000L) }
            assertTrue(store.commit("d$i", "ex"))
            versions += store.read("ex", false)!!.version
        }
        assertEquals(versions.size, versions.toSet().size)
        assertEquals(versions, versions.sorted())
    }

    @Test fun discardDeletesTheDraft() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        store.discard("d1")
        assertNull(store.draft("d1", false))
        assertNull(store.draft("d1", true))
    }

    @Test fun removeDeletesBoth() = runBlocking {
        assertTrue(store.stage("d1", source("a.jpg")))
        assertTrue(store.commit("d1", "ex"))
        assertNotNull(store.read("ex", true))
        store.remove("ex")
        assertNull(store.read("ex", false))
        assertNull(store.read("ex", true))
    }

    @Test fun oldDraftsGoFreshDraftsStay() = runBlocking {
        assertTrue(store.stage("old", source("a.jpg")))
        assertTrue(store.stage("fresh", source("b.jpg")))
        val day = 86_400_000L
        File(root, "drafts/old.jpg").setLastModified(System.currentTimeMillis() - 2 * day)
        File(root, "drafts/thumbs/old.jpg").setLastModified(System.currentTimeMillis() - 2 * day)
        store.removeOrphans(emptySet(), day)
        assertNull(store.draft("old", false))
        assertNull(store.draft("old", true))
        assertNotNull(store.draft("fresh", false))
        assertNotNull(store.draft("fresh", true))
    }

    @Test fun staleTemporariesGoFreshOnesStay() = runBlocking {
        val day = 86_400_000L
        val stale = File(root, "drafts/photo1.tmp").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - 2 * day) }
        val fresh = File(root, "drafts/photo2.tmp").apply { writeBytes(byteArrayOf(1)) }
        val staleCommitted = File(root, "photo3.tmp").apply { writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - 2 * day) }
        store.removeOrphans(emptySet(), day)
        assertFalse(stale.exists())
        assertFalse(staleCommitted.exists())
        assertTrue(fresh.exists())
    }

    @Test fun orphanPhotosGoCustomOnesStay() = runBlocking {
        for (id in listOf("keep", "gone")) {
            assertTrue(store.stage("d-$id", source("$id.jpg")))
            assertTrue(store.commit("d-$id", id))
        }
        store.removeOrphans(setOf("keep"))
        assertNotNull(store.read("keep", false))
        assertNotNull(store.read("keep", true))
        assertNull(store.read("gone", false))
        assertNull(store.read("gone", true))
    }

    @Test fun revisionBumpsOnEveryChange() = runBlocking {
        var last = store.revision.value
        fun assertBumped(step: String) { assertTrue("$step did not bump", store.revision.value > last); last = store.revision.value }
        assertTrue(store.stage("d1", source("a.jpg"))); assertBumped("stage")
        assertTrue(store.commit("d1", "ex")); assertBumped("commit")
        assertTrue(store.stage("d2", source("b.jpg"))); assertBumped("stage again")
        store.discard("d2"); assertBumped("discard")
        store.removeOrphans(emptySet()); assertBumped("removeOrphans")
        assertTrue(store.stage("d3", source("c.jpg"))); last = store.revision.value
        assertTrue(store.commit("d3", "ex2")); last = store.revision.value
        store.remove("ex2"); assertBumped("remove")
    }

    @Test fun namesThatCouldEscapeTheFolderAreRefused() = runBlocking {
        assertFalse(store.stage("../x", source("a.jpg")))
        assertTrue(store.stage("d1", source("a.jpg")))
        assertFalse(store.commit("d1", "../../escape"))
        assertNull(store.read("a/b", false))
    }

    @Test fun everyExifOrientationComesOutUpright() = runBlocking {
        for (orientation in 1..8) {
            assertTrue(store.stage("o$orientation", source("o$orientation.jpg", 1200, 900, orientation)))
            val photo = store.draft("o$orientation", false)!!.bytes
            assertTrue("orientation $orientation", PhotoTestImages.upright(photo))
            assertEquals("orientation $orientation", if (orientation >= 5) 810 to 1080 else 1080 to 810, size(photo))
        }
    }

    @Test fun transparentPngComesOutWithAWhiteBackground() = runBlocking {
        val png = Uri.fromFile(PhotoTestImages.write(File(sources, "alpha.png"), 1200, 900, 1, png = true)).toString()
        assertTrue(store.stage("png", png))
        val center = PhotoTestImages.center(store.draft("png", false)!!.bytes)
        // JPEG is lossy: near white, not exactly white.
        for (channel in listOf(android.graphics.Color.red(center), android.graphics.Color.green(center), android.graphics.Color.blue(center))) {
            assertTrue("centre $center", channel >= 245)
        }
    }

    @Test fun anExifBlockThatThrowsIsShownAsIs() {
        val file = File(sources, "e.jpg").also { PhotoTestImages.write(it, 1200, 900, 6) }
        var opens = 0
        // Pass 1 is the bounds, pass 2 the EXIF: the second one throws an unchecked exception when read.
        val processed = PhotoProcessing.process({
            if (++opens == 2) object : java.io.InputStream() { override fun read(): Int = throw IllegalStateException("malformed") }
            else file.inputStream()
        }, android.os.Build.VERSION.SDK_INT)
        // Chosen behaviour: the orientation falls back to 1 and the photo is still saved (here left sideways).
        assertNotNull(processed)
        assertEquals(1080 to 810, size(processed!!.full))
    }

    @Test fun failedSecondRenameKeepsTheOldPairAndTheDraft() = runBlocking {
        assertTrue(store.stage("a", source("a.jpg", orientation = 1)))
        assertTrue(store.commit("a", "ex"))
        val oldFull = store.read("ex", false)!!
        val oldThumb = store.read("ex", true)!!
        assertTrue(store.stage("b", source("b.jpg", orientation = 3)))
        val draftBytes = store.draft("b", false)!!.bytes
        val revision = store.revision.value
        store.moveFile = { from, to ->
            if (to.parentFile!!.name == "thumbs" && to.parentFile!!.parentFile == root) throw java.io.IOException("disk full")
            to.parentFile?.mkdirs()
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        assertFalse(store.commit("b", "ex"))
        // The reader sees the old pair, whole; the draft is still there for a retry; nothing changed, so no bump.
        assertArrayEquals(oldFull.bytes, store.read("ex", false)!!.bytes)
        assertArrayEquals(oldThumb.bytes, store.read("ex", true)!!.bytes)
        assertEquals(oldFull.version, store.read("ex", false)!!.version)
        assertArrayEquals(draftBytes, store.draft("b", false)!!.bytes)
        assertNotNull(store.draft("b", true))
        assertEquals(revision, store.revision.value)
        assertEquals(emptyList<File>(), root.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.toList())
        // A retry once the disk has room.
        store.moveFile = { from, to ->
            to.parentFile?.mkdirs()
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        assertTrue(store.commit("b", "ex"))
        assertArrayEquals(draftBytes, store.read("ex", false)!!.bytes)
    }

    @Test fun aThumbnailWithoutItsPhotoIsHiddenAndSwept() = runBlocking {
        assertTrue(store.stage("a", source("a.jpg")))
        assertTrue(store.commit("a", "ex"))
        File(root, "ex.jpg").delete()
        assertNull(store.read("ex", true))
        store.removeOrphans(setOf("ex"))
        assertFalse(File(root, "thumbs/ex.jpg").exists())
    }
}
