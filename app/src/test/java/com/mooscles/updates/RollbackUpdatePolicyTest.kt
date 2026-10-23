package com.mooscles.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A restored display version remains an Android upgrade because the actual build increases. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RollbackUpdatePolicyTest {
    private val signing = ApkSigningIdentity(setOf(
        "0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35",
    ))
    private val restored = UpdateMetadata(
        "com.mooscles", 13, "1.4.1", 26, "Mooscles-v1.4.1-build13-debug.apk", 2000, "ab".repeat(32),
    )
    private val build12 = restored.copy(versionCode = 12, versionName = "1.4.2", apkFileName = "Mooscles-v1.4.2-debug.apk")
    private val restoredTag = "v1.4.1-build13"

    @Test fun installedBuildsDiscoverRestorationWithLowerDisplayVersionAndTag() {
        // Put the higher semantic version first to prove API order and tag sorting cannot choose it.
        val client = client("v1.4.2" to build12, restoredTag to restored)
        for (installed in listOf(5L, 11L, 12L)) {
            for (includePrereleases in listOf(false, true)) {
                val available = client.check("com.mooscles", installed, 35, includePrereleases).available
                assertNotNull("Installed build $installed must discover restoration", available)
                assertEquals(restored, available!!.metadata)
                assertEquals("https://github.com/ReiTut/Mooscles/releases/tag/$restoredTag", available.releaseUrl)
                assertFalse(available.prerelease)
            }
        }
    }

    @Test fun installedRestorationAndNewerBuildsAreNotOfferedAnOlderApk() {
        val client = client("v1.4.2" to build12, restoredTag to restored)
        for (installed in listOf(13L, 14L)) {
            assertNull(client.check("com.mooscles", installed, 35).available)
            assertNull(client.check("com.mooscles", installed, 35, includePrereleases = true).available)
        }
    }

    @Test fun restoredBuildCanDiscoverFutureHigherBuildWithTheSameDisplayVersion() {
        val future = restored.copy(versionCode = 14, apkFileName = "Mooscles-v1.4.1-build14-debug.apk")
        val client = client("v1.4.2" to build12, restoredTag to restored, "v1.4.1-build14" to future)
        assertEquals(future, client.check("com.mooscles", 13, 35).available!!.metadata)
    }

    @Test fun candidateWithLowerDisplayVersionAndHigherActualBuildPassesIdentityPolicy() {
        val installed = ApkIdentity("com.mooscles", 12, "1.4.2", 26, signing)
        val candidate = ApkIdentity("com.mooscles", 13, "1.4.1", 26, signing)
        ApkVerificationPolicy.verifyIdentity(restored, installed, candidate, 35)
    }

    @Test fun actualApkBuildAndDisplayVersionMustStillMatchRestorationMetadata() {
        val installed = ApkIdentity("com.mooscles", 12, "1.4.2", 26, signing)
        val candidate = ApkIdentity("com.mooscles", 13, "1.4.1", 26, signing)
        rejects { ApkVerificationPolicy.verifyIdentity(restored, installed, candidate.copy(versionCode = 11), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(restored, installed, candidate.copy(versionName = "1.4.2"), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(restored, installed, candidate.copy(signing = ApkSigningIdentity(setOf("other"))), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(restored.copy(versionCode = 12), installed, candidate, 35) }
    }

    private fun client(vararg releases: Pair<String, UpdateMetadata>): ReleaseClient {
        val responses = mutableMapOf<String, HttpResponse>()
        val entries = JSONArray()
        releases.forEach { (tag, metadata) ->
            val base = "https://github.com/ReiTut/Mooscles/releases/download/$tag/"
            val body = UpdateJson.encodeMetadata(metadata)
            responses[base + "update.json"] = HttpResponse(200, body = body)
            fun asset(name: String, size: Long) = JSONObject()
                .put("name", name).put("state", "uploaded").put("size", size)
                .put("browser_download_url", base + name)
            entries.put(JSONObject().put("tag_name", tag)
                .put("html_url", "https://github.com/ReiTut/Mooscles/releases/tag/$tag")
                .put("draft", false).put("prerelease", false).put("published_at", "2026-10-23T12:00:00Z")
                .put("body", "Restore v1.4.1 functionality; actual build ${metadata.versionCode}")
                .put("assets", JSONArray().put(asset("update.json", body.toByteArray().size.toLong()))
                    .put(asset(metadata.apkFileName, metadata.apkSize).put("digest", "sha256:${metadata.sha256}"))))
        }
        responses[ReleaseUrls.API_URL] = HttpResponse(200, body = entries.toString())
        return ReleaseClient(HttpTransport { url, _ -> responses[url] ?: HttpResponse(404) })
    }

    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("Incompatible candidate was accepted")
        } catch (_: ApkVerificationException) { }
    }
}
