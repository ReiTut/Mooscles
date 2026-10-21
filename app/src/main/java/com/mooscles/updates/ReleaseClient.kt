package com.mooscles.updates

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class CachedResponse(val etag: String?, val body: String)

data class ReleaseCache(val responses: Map<String, CachedResponse> = emptyMap()) {
    fun toJson(): String = JSONObject().apply {
        responses.forEach { (url, response) ->
            put(url, JSONObject().put("etag", response.etag).put("body", response.body))
        }
    }.toString()

    companion object {
        fun fromJson(json: String?): ReleaseCache = runCatching {
            if (json.isNullOrEmpty()) return ReleaseCache()
            val objectValue = JSONObject(json)
            val values = mutableMapOf<String, CachedResponse>()
            val keys = objectValue.keys()
            while (keys.hasNext() && values.size < 101) {
                val key = keys.next()
                if (!ReleaseUrls.isAllowedConnection(key)) continue
                val value = objectValue.getJSONObject(key)
                values[key] = CachedResponse(value.optString("etag").takeIf { it.isNotEmpty() && it != "null" },
                    value.requiredString("body", allowEmpty = true))
            }
            ReleaseCache(values)
        }.getOrElse { ReleaseCache() }
    }
}

data class ReleaseCheckResult(
    val available: AvailableUpdate?,
    val cache: ReleaseCache,
    val diagnostics: List<String> = emptyList(),
)

class ReleaseCheckException(
    message: String,
    val retryAtMillis: Long? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

data class HttpResponse(val status: Int, val headers: Map<String, String> = emptyMap(), val body: String = "") {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(url: String, headers: Map<String, String>): HttpResponse
}

/** A deliberately unauthenticated transport: no GitHub credential is bundled with the app. */
class GitHubHttpTransport : HttpTransport {
    override fun execute(url: String, headers: Map<String, String>): HttpResponse {
        var destination = url
        repeat(6) { redirect ->
            if (!ReleaseUrls.isAllowedConnection(destination)) throw IOException("Update URL is not a trusted GitHub address")
            val connection = URL(destination).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                val status = connection.responseCode
                val responseHeaders = connection.headerFields.filterKeys { it != null }
                    .mapValues { it.value.firstOrNull().orEmpty() }
                if (status in listOf(301, 302, 303, 307, 308)) {
                    if (redirect == 5) throw IOException("Too many update redirects")
                    val location = connection.getHeaderField("Location") ?: throw IOException("Update redirect has no destination")
                    destination = URI(destination).resolve(location).toString()
                } else {
                    val limit = if (url == ReleaseUrls.API_URL) 2 * 1024 * 1024 else 64 * 1024
                    val body = if (status == 200) connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            if (total > limit) throw IOException("Update response exceeds its size limit")
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } else ""
                    return HttpResponse(status, responseHeaders, body)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Unable to follow update redirect")
    }
}

/** Scans the latest 100 published entries; versionCode, never tag order, chooses an update. */
class ReleaseClient(
    private val transport: HttpTransport = GitHubHttpTransport(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun check(
        applicationId: String,
        installedVersionCode: Long,
        sdkInt: Int,
        includePrereleases: Boolean = false,
        cache: ReleaseCache = ReleaseCache(),
    ): ReleaseCheckResult {
        val responses = cache.responses.toMutableMap()
        val usedUrls = mutableSetOf<String>()
        val diagnostics = mutableListOf<String>()
        val listBody = fetch(ReleaseUrls.API_URL, responses, usedUrls)
        val releases = try { JSONArray(listBody) } catch (error: Exception) {
            throw ReleaseCheckException("GitHub returned an invalid release list", nowMillis() + 15 * 60_000, error)
        }
        val candidates = mutableListOf<AvailableUpdate>()
        for (index in 0 until minOf(releases.length(), 100)) {
            val release = releases.optJSONObject(index) ?: continue
            val draft = release.opt("draft") as? Boolean ?: continue
            val prerelease = release.opt("prerelease") as? Boolean ?: continue
            val publishedAt = release.opt("published_at") as? String
            if (draft || (prerelease && !includePrereleases) || publishedAt.isNullOrBlank()) continue
            val releaseUrl = release.optString("html_url")
            if (!ReleaseUrls.isRelease(releaseUrl)) continue
            val assets = release.optJSONArray("assets") ?: continue
            val metadataAssets = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name") == "update.json" && it.optString("state") == "uploaded" }
            if (metadataAssets.size != 1) {
                diagnostics += "A published release has no unique update.json asset and was skipped."
                continue
            }
            val metadataAsset = metadataAssets.single()
            val metadataUrl = metadataAsset.optString("browser_download_url")
            val metadataSize = runCatching { metadataAsset.requiredLong("size") }.getOrNull()
            if (!ReleaseUrls.isReleaseAsset(metadataUrl) || metadataSize == null || metadataSize !in 1..65536) {
                diagnostics += "A release contains invalid update metadata details and was skipped."
                continue
            }
            val metadataBody = try { fetch(metadataUrl, responses, usedUrls) } catch (error: ReleaseCheckException) {
                if (error.retryAtMillis != null) throw error
                diagnostics += "A release's update metadata could not be retrieved and was skipped."
                continue
            }
            val metadata = UpdateJson.decodeMetadata(metadataBody)
            if (metadata == null) {
                diagnostics += "A release's update.json is malformed and was skipped."
                continue
            }
            if (metadata.applicationId != applicationId || metadata.minSdk > sdkInt || metadata.versionCode <= installedVersionCode) continue
            val matchingApks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name") == metadata.apkFileName && it.optString("state") == "uploaded" }
            val apk = matchingApks.singleOrNull()
            val apkSize = apk?.let { runCatching { it.requiredLong("size") }.getOrNull() }
            val apkUrl = apk?.optString("browser_download_url").orEmpty()
            if (apk == null || apkSize != metadata.apkSize || !ReleaseUrls.isApkAsset(apkUrl)) {
                diagnostics += "A release's APK does not match update.json and was skipped."
                continue
            }
            val digest = apk.optString("digest").takeIf { it.isNotEmpty() && it != "null" }
            if (digest != null && !digest.equals("sha256:${metadata.sha256}", ignoreCase = true)) {
                diagnostics += "A release's APK checksum conflicts with update.json and was skipped."
                continue
            }
            candidates += AvailableUpdate(metadata, apkUrl, releaseUrl, release.opt("body") as? String ?: "", prerelease)
        }
        val selected = candidates.maxWithOrNull(compareBy<AvailableUpdate> { it.metadata.versionCode }.thenBy { !it.prerelease })
        return ReleaseCheckResult(selected, ReleaseCache(responses.filterKeys { it in usedUrls }), diagnostics.distinct())
    }

    private fun fetch(url: String, cache: MutableMap<String, CachedResponse>, usedUrls: MutableSet<String>): String {
        usedUrls += url
        val previous = cache[url]
        val headers = mutableMapOf(
            "Accept" to if (url == ReleaseUrls.API_URL) "application/vnd.github+json" else "application/json",
            "User-Agent" to "Mooscles-Android-Updater",
            "X-GitHub-Api-Version" to "2026-03-10",
        )
        previous?.etag?.let { headers["If-None-Match"] = it }
        val response = try { transport.execute(url, headers) } catch (error: IOException) {
            throw ReleaseCheckException("Cannot reach GitHub. Your workouts remain available offline.", nowMillis() + 60_000, error)
        }
        if (response.status == 304) return previous?.body
            ?: throw ReleaseCheckException("GitHub returned an empty update cache", nowMillis() + 60_000)
        if (response.status == 200) {
            val limit = if (url == ReleaseUrls.API_URL) 2 * 1024 * 1024 else 64 * 1024
            if (response.body.toByteArray(Charsets.UTF_8).size > limit) {
                throw ReleaseCheckException("GitHub returned update details that are too large")
            }
            cache[url] = CachedResponse(response.header("ETag"), response.body)
            return response.body
        }
        val now = nowMillis()
        if (response.status == 403 || response.status == 429) {
            val retryAfter = response.header("Retry-After")?.let { value ->
                value.toLongOrNull()?.takeIf { it >= 0 }?.let { now + it.coerceAtMost(7 * 24 * 3600) * 1000 }
                    ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            }
            val reset = if (response.header("X-RateLimit-Remaining") == "0")
                response.header("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000) else null
            throw ReleaseCheckException("GitHub has limited update requests. The app will retry later.", maxOf(now + 60_000, retryAfter ?: 0, reset ?: 0))
        }
        if (response.status in 500..599 || response.status == 408) {
            throw ReleaseCheckException("GitHub is temporarily unavailable. The app will retry later.", now + 60_000)
        }
        if (url == ReleaseUrls.API_URL && response.status == 404) {
            throw ReleaseCheckException("Public GitHub releases are unavailable for this repository.", now + 6 * 60 * 60_000)
        }
        throw ReleaseCheckException("GitHub update request failed (HTTP ${response.status})")
    }
}
