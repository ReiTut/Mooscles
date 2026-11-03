package com.mooscles.screens.exercise.photo

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The in-memory cache of decoded photos: by source, size and version, at most 64 thumbnails and 4 full images. */
class PhotoCacheTest {
    private fun source(n: Int) = PhotoSource.Exercise("e$n")

    @Test
    fun aVersionIsDecodedOnce() = runTest {
        val cache = PhotoCache<String>()
        var decodes = 0
        suspend fun get(version: Long) = cache.imageOf(source(1), true, version) { decodes++; "v$version" }
        assertEquals("v1", get(1))
        assertEquals("v1", get(1))
        assertEquals(1, decodes)
        // A replaced photo has a new version, and the other size is another entry.
        assertEquals("v2", get(2))
        cache.imageOf(source(1), false, 1) { decodes++; "full" }
        assertEquals(3, decodes)
        // A photo that cannot be decoded is not kept, so the next read tries again.
        assertNull(cache.imageOf(source(9), true, 1) { null })
        assertEquals("ok", cache.imageOf(source(9), true, 1) { "ok" })
    }

    @Test
    fun thumbnailsStopAt64AndDropTheLeastRecentlyUsed() = runTest {
        val cache = PhotoCache<String>()
        for (n in 0 until 64) cache.imageOf(source(n), true, 1) { "t$n" }
        assertEquals(64, cache.thumbnailCount)
        // Reading the first again makes the second the oldest.
        assertEquals("t0", cache.imageOf(source(0), true, 1) { error("a hit decodes nothing") })
        cache.imageOf(source(64), true, 1) { "t64" }
        assertEquals(64, cache.thumbnailCount)
        var redecoded = false
        cache.imageOf(source(1), true, 1) { redecoded = true; "t1" }
        assertEquals(true, redecoded)
        var zeroDecoded = false
        cache.imageOf(source(0), true, 1) { zeroDecoded = true; "t0" }
        assertEquals(false, zeroDecoded)
    }

    @Test
    fun fullImagesStopAtFour() = runTest {
        val cache = PhotoCache<String>()
        for (n in 0 until 6) cache.imageOf(source(n), false, 1) { "f$n" }
        assertEquals(4, cache.fullCount)
        var first = false
        cache.imageOf(source(0), false, 1) { first = true; "f0" }
        assertEquals(true, first)
        var last = false
        cache.imageOf(source(5), false, 1) { last = true; "f5" }
        assertEquals(false, last)
    }

    @Test
    fun thumbnailsAndFullImagesDoNotCrowdEachOther() = runTest {
        val cache = PhotoCache<String>()
        for (n in 0 until 10) cache.imageOf(source(n), true, 1) { "t$n" }
        for (n in 0 until 10) cache.imageOf(source(n), false, 1) { "f$n" }
        assertEquals(10, cache.thumbnailCount)
        assertEquals(4, cache.fullCount)
    }
}
