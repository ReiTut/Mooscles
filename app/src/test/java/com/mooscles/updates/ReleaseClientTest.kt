package com.mooscles.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseClientTest {
    private val sha = "ab".repeat(32)
    private val now = 1_000_000L

    private fun metadata(code: Long = 6, minSdk: Int = 26, applicationId: String = "com.mooscles") =
        UpdateMetadata(applicationId, code, "version-$code", minSdk, "Mooscles-$code.apk", 2000, sha)

    private fun asset(name: String, tag: String, size: Long, digest: String? = null): JSONObject = JSONObject()
        .put("name", name).put("size", size).put("state", "uploaded")
        .put("browser_download_url", "https://github.com/ReiTut/Mooscles/releases/download/$tag/$name")
        .apply { digest?.let { put("digest", it) } }

    private fun release(details: UpdateMetadata, tag: String = "v${details.versionCode}",
        prerelease: Boolean = false, draft: Boolean = false, includeMetadata: Boolean = true): JSONObject = JSONObject()
        .put("tag_name", tag)
        .put("html_url", "https://github.com/ReiTut/Mooscles/releases/tag/$tag")
        .put("draft", draft).put("prerelease", prerelease).put("published_at", "2026-10-21T00:00:00Z")
        .put("body", "Release notes for $tag")
        .put("assets", JSONArray().apply {
            put(asset(details.apkFileName, tag, details.apkSize, "sha256:${details.sha256}"))
            if (includeMetadata) put(asset("update.json", tag, UpdateJson.encodeMetadata(details).length.toLong()))
        })

    private class FakeTransport : HttpTransport {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val responses = mutableMapOf<String, HttpResponse>()
        var failure: IOException? = null
        override fun execute(url: String, headers: Map<String, String>): HttpResponse {
            calls += url to headers
            failure?.let { throw it }
            return responses[url] ?: HttpResponse(404)
        }
    }

    private fun fixture(vararg releases: Pair<JSONObject, UpdateMetadata>): FakeTransport = FakeTransport().apply {
        responses[ReleaseUrls.API_URL] = HttpResponse(200, mapOf("ETag" to "release-list"), JSONArray().apply {
            releases.forEach { put(it.first) }
        }.toString())
        releases.forEach { (release, details) ->
            val tag = release.getString("tag_name")
            responses["https://github.com/ReiTut/Mooscles/releases/download/$tag/update.json"] =
                HttpResponse(200, mapOf("ETag" to "metadata-$tag"), UpdateJson.encodeMetadata(details))
        }
    }

    @Test fun comparesVersionCodesAndIgnoresMisleadingTagNamesAndApiOrder() {
        val older = metadata(4)
        val newer = metadata(7)
        val mid = metadata(6)
        val transport = fixture(release(older, "v999.0") to older, release(newer, "v0.1") to newer,
            release(mid, "z-release") to mid)
        assertEquals(7L, ReleaseClient(transport).check("com.mooscles", 5, 35).available!!.metadata.versionCode)
        assertNull(ReleaseClient(transport).check("com.mooscles", 7, 35).available)
        assertNull(ReleaseClient(transport).check("com.mooscles", 8, 35).available)
    }

    @Test fun excludesDraftsAndPrereleasesUnlessChannelExplicitlyEnablesPrereleases() {
        val stable = metadata(6)
        val preview = metadata(7)
        val draft = metadata(8)
        val transport = fixture(release(stable) to stable, release(preview, prerelease = true) to preview,
            release(draft, draft = true) to draft)
        val client = ReleaseClient(transport)
        assertEquals(6L, client.check("com.mooscles", 5, 35).available!!.metadata.versionCode)
        assertEquals(7L, client.check("com.mooscles", 5, 35, includePrereleases = true).available!!.metadata.versionCode)
    }

    @Test fun betaVersionNamesUseBuildCodesAndRequireAnExplicitPrereleaseOptIn() {
        val stable = metadata(17).copy(versionName = "1.4.6", apkFileName = "Mooscles-v1.4.6-debug.apk")
        val beta = metadata(18).copy(versionName = "1.4.7-beta.1", apkFileName = "Mooscles-v1.4.7-beta.1-debug.apk")
        val transport = fixture(release(beta, "v1.4.7-beta.1", prerelease = true) to beta,
            release(stable, "v1.4.6") to stable)
        val client = ReleaseClient(transport)

        // An installed stable build must not discover a beta on its default channel.
        assertNull(client.check("com.mooscles", 17, 35).available)
        val optedIn = client.check("com.mooscles", 17, 35, includePrereleases = true).available!!
        assertEquals(beta, optedIn.metadata)
        assertTrue(optedIn.prerelease)
        assertEquals("https://github.com/ReiTut/Mooscles/releases/download/v1.4.7-beta.1/${beta.apkFileName}", optedIn.apkUrl)
        assertEquals(17L, client.check("com.mooscles", 16, 35).available!!.metadata.versionCode)
        assertNull(client.check("com.mooscles", 18, 35, includePrereleases = true).available)
    }

    @Test fun ignoresUnpublishedAndMalformedReleaseEntries() {
        val details = metadata()
        val release = release(details).put("published_at", JSONObject.NULL)
        val transport = fixture(release to details)
        transport.responses[ReleaseUrls.API_URL] = HttpResponse(200, body = JSONArray().put(release)
            .put(JSONObject().put("prerelease", false)).put("not a release").toString())
        assertNull(ReleaseClient(transport).check("com.mooscles", 5, 35).available)
    }

    @Test fun requiresCorrectPackageAndSupportedMinimumSdk() {
        val wrongPackage = metadata(8, applicationId = "other.package")
        val unsupported = metadata(7, minSdk = 36)
        val compatible = metadata(6, minSdk = 35)
        val transport = fixture(release(wrongPackage) to wrongPackage, release(unsupported) to unsupported,
            release(compatible) to compatible)
        assertEquals(6L, ReleaseClient(transport).check("com.mooscles", 5, 35).available!!.metadata.versionCode)
    }

    @Test fun missingOrMalformedMetadataDoesNotInventAnUpdateFromTagOrOtherAssets() {
        val missing = metadata(8)
        val malformed = metadata(7)
        val transport = fixture(release(missing, includeMetadata = false) to missing, release(malformed) to malformed)
        transport.responses["https://github.com/ReiTut/Mooscles/releases/download/v7/update.json"] =
            HttpResponse(200, body = "{not valid json}")
        val result = ReleaseClient(transport).check("com.mooscles", 5, 35)
        assertNull(result.available)
        assertEquals(2, result.diagnostics.size)
    }

    @Test fun rejectsMetadataWithMissingFieldsFractionalOrStringVersionCodeAndInvalidChecksums() {
        val valid = UpdateJson.encodeMetadata(metadata())
        assertNotNull(UpdateJson.decodeMetadata(valid))
        val variations = listOf(
            JSONObject(valid).apply { remove("apkSize") },
            JSONObject(valid).put("versionCode", 6.5),
            JSONObject(valid).put("versionCode", "6"),
            JSONObject(valid).put("versionCode", -1),
            JSONObject(valid).put("minSdk", 0),
            JSONObject(valid).put("apkSize", 0),
            JSONObject(valid).put("sha256", "broken"),
            JSONObject(valid).put("apkFileName", "../malicious.apk"),
        )
        variations.forEach { assertNull(it.toString(), UpdateJson.decodeMetadata(it.toString())) }
    }

    @Test fun requiresExactNamedUploadedApkWithMatchingSizeAndOptionalGithubDigest() {
        val details = metadata()
        listOf("wrong-name", "wrong-size", "wrong-digest", "source-archive", "untrusted-url", "duplicate").forEach { problem ->
            val release = release(details)
            val assets = release.getJSONArray("assets")
            val apk = assets.getJSONObject(0)
            when (problem) {
                "wrong-name" -> apk.put("name", "some-other.apk")
                "wrong-size" -> apk.put("size", 1999)
                "wrong-digest" -> apk.put("digest", "sha256:${"00".repeat(32)}")
                "source-archive" -> apk.put("browser_download_url", "https://github.com/ReiTut/Mooscles/archive/v6.zip")
                "untrusted-url" -> apk.put("browser_download_url", "https://evil.example/app.apk")
                "duplicate" -> assets.put(JSONObject(apk.toString()))
            }
            assertNull(problem, ReleaseClient(fixture(release to details)).check("com.mooscles", 5, 35).available)
        }
    }

    @Test fun cacheRoundTripsAndReusesMetadataAndReleaseBodyOn304() {
        val details = metadata()
        val transport = fixture(release(details) to details)
        val client = ReleaseClient(transport)
        val first = client.check("com.mooscles", 5, 35)
        val cache = ReleaseCache.fromJson(first.cache.toJson())
        assertEquals(first.cache, cache)
        transport.responses.keys.toList().forEach { transport.responses[it] = HttpResponse(304) }
        val repeated = client.check("com.mooscles", 5, 35, cache = cache)
        assertEquals(first.available, repeated.available)
        assertEquals("release-list", transport.calls[2].second["If-None-Match"])
        assertEquals("metadata-v6", transport.calls[3].second["If-None-Match"])
        assertTrue(transport.calls.all { (_, headers) -> headers.keys.none { it.equals("Authorization", true) } })
    }

    @Test fun corruptCacheAnd304WithoutCacheAreRecoverableFailures() {
        assertEquals(ReleaseCache(), ReleaseCache.fromJson("bad json"))
        val transport = FakeTransport().apply { responses[ReleaseUrls.API_URL] = HttpResponse(304) }
        val error = failure { ReleaseClient(transport) { now }.check("com.mooscles", 5, 35) }
        assertEquals(now + 60_000, error.retryAtMillis)
    }

    @Test fun offlineAndServerFailureHaveRetryTimesAndNoBlockingWaitLoop() {
        val offline = FakeTransport().apply { failure = IOException("Offline") }
        assertEquals(now + 60_000, failure { ReleaseClient(offline) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
        assertEquals(1, offline.calls.size)
        val server = FakeTransport().apply { responses[ReleaseUrls.API_URL] = HttpResponse(503) }
        assertEquals(now + 60_000, failure { ReleaseClient(server) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
        assertEquals(1, server.calls.size)
    }

    @Test fun obeysRateLimitResetAndRetryAfterTakingTheLaterDeadline() {
        val transport = FakeTransport().apply {
            responses[ReleaseUrls.API_URL] = HttpResponse(403, mapOf("retry-after" to "120",
                "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to ((now + 300_000) / 1000).toString()))
        }
        assertEquals(now + 300_000, failure { ReleaseClient(transport) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
        transport.responses[ReleaseUrls.API_URL] = HttpResponse(429, mapOf("Retry-After" to "120"))
        assertEquals(now + 120_000, failure { ReleaseClient(transport) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
        transport.responses[ReleaseUrls.API_URL] = HttpResponse(403)
        assertEquals(now + 60_000, failure { ReleaseClient(transport) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
    }

    @Test fun metadataNetworkFailureCannotBeReportedAsSuccessfulNoUpdateCheck() {
        val details = metadata()
        val transport = fixture(release(details) to details)
        transport.responses["https://github.com/ReiTut/Mooscles/releases/download/v6/update.json"] = HttpResponse(503)
        assertNotNull(failure { ReleaseClient(transport) { now }.check("com.mooscles", 5, 35) }.retryAtMillis)
    }

    @Test fun refusesOversizedAndNonJsonResponses() {
        val transport = FakeTransport().apply {
            responses[ReleaseUrls.API_URL] = HttpResponse(200, body = " ".repeat(2 * 1024 * 1024 + 1))
        }
        failure { ReleaseClient(transport).check("com.mooscles", 5, 35) }
        transport.responses[ReleaseUrls.API_URL] = HttpResponse(200, body = "<html>failure</html>")
        failure { ReleaseClient(transport).check("com.mooscles", 5, 35) }
    }

    @Test fun availableUpdateRoundTripsAndRejectsUntrustedPersistedUrls() {
        val update = AvailableUpdate(metadata(), "https://github.com/ReiTut/Mooscles/releases/download/v6/Mooscles-6.apk",
            "https://github.com/ReiTut/Mooscles/releases/tag/v6", "Release notes", false)
        assertEquals(update, UpdateJson.decodeAvailable(UpdateJson.encodeAvailable(update)))
        val malformed = JSONObject(UpdateJson.encodeAvailable(update)).put("apkUrl", "https://evil.example/app.apk")
        assertNull(UpdateJson.decodeAvailable(malformed.toString()))
    }

    @Test fun urlPolicyAllowsOnlySecureGithubRepositoryAssetsAndItsDownloadCdns() {
        assertTrue(ReleaseUrls.isAllowedConnection(ReleaseUrls.API_URL))
        assertTrue(ReleaseUrls.isAllowedConnection("https://release-assets.githubusercontent.com/asset?signature=abc"))
        listOf("http://github.com/ReiTut/Mooscles/releases/download/v6/a.apk",
            "https://github.com/Someone/Else/releases/download/v6/a.apk",
            "https://github.com.evil.example/ReiTut/Mooscles/releases/download/v6/a.apk",
            "https://user:password@github.com/ReiTut/Mooscles/releases/download/v6/a.apk",
            "https://github.com:444/ReiTut/Mooscles/releases/download/v6/a.apk",
            "https://api.github.com/user", "file:///tmp/a.apk").forEach { assertFalse(it, ReleaseUrls.isAllowedConnection(it)) }
    }

    private fun failure(block: () -> Unit): ReleaseCheckException = try {
        block()
        throw AssertionError("Expected a recoverable update check failure")
    } catch (error: ReleaseCheckException) { error }
}
