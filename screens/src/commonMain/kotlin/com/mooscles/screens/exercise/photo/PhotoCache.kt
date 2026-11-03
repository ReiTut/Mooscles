package com.mooscles.screens.exercise.photo

/**
 * The decoded photos kept in memory, by source, size and version: up to [thumbnailLimit] thumbnails and [fullLimit]
 * full-size images, the least recently used one going first. Only the main thread touches it.
 */
internal class PhotoCache<T : Any>(private val thumbnailLimit: Int = 64, private val fullLimit: Int = 4) {
    private data class Key(val source: PhotoSource, val thumbnail: Boolean, val version: Long)

    private val thumbnails = LinkedHashMap<Key, T>()
    private val full = LinkedHashMap<Key, T>()

    val thumbnailCount: Int get() = thumbnails.size
    val fullCount: Int get() = full.size

    /** The cached image, or the one [decode] gives (kept unless null). */
    suspend fun imageOf(source: PhotoSource, thumbnail: Boolean, version: Long, decode: suspend () -> T?): T? {
        val key = Key(source, thumbnail, version)
        val map = if (thumbnail) thumbnails else full
        map.remove(key)?.let {
            map[key] = it
            return it
        }
        val decoded = decode() ?: return null
        map[key] = decoded
        val limit = if (thumbnail) thumbnailLimit else fullLimit
        while (map.size > limit) map.remove(map.keys.first())
        return decoded
    }
}
