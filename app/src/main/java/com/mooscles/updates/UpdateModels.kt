package com.mooscles.updates

import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class UpdateMetadata(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkFileName: String,
    val apkSize: Long,
    val sha256: String,
)

data class AvailableUpdate(
    val metadata: UpdateMetadata,
    val apkUrl: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val prerelease: Boolean,
)

object UpdateJson {
    fun encodeMetadata(metadata: UpdateMetadata): String = metadataObject(metadata).toString()

    fun decodeMetadata(json: String): UpdateMetadata? = runCatching { metadata(JSONObject(json)) }.getOrNull()

    fun encodeAvailable(update: AvailableUpdate): String = JSONObject()
        .put("metadata", metadataObject(update.metadata))
        .put("apkUrl", update.apkUrl)
        .put("releaseUrl", update.releaseUrl)
        .put("releaseNotes", update.releaseNotes)
        .put("prerelease", update.prerelease)
        .toString()

    fun decodeAvailable(json: String): AvailableUpdate? = runCatching {
        val value = JSONObject(json)
        val details = metadata(value.getJSONObject("metadata"))
        val apkUrl = value.requiredString("apkUrl")
        val releaseUrl = value.requiredString("releaseUrl")
        require(ReleaseUrls.isApkAsset(apkUrl))
        require(ReleaseUrls.isRelease(releaseUrl))
        AvailableUpdate(details, apkUrl, releaseUrl, value.requiredString("releaseNotes", allowEmpty = true),
            value.get("prerelease") as Boolean)
    }.getOrNull()

    private fun metadataObject(value: UpdateMetadata): JSONObject = JSONObject()
        .put("applicationId", value.applicationId)
        .put("versionCode", value.versionCode)
        .put("versionName", value.versionName)
        .put("minSdk", value.minSdk)
        .put("apkFileName", value.apkFileName)
        .put("apkSize", value.apkSize)
        .put("sha256", value.sha256.lowercase(Locale.ROOT))

    private fun metadata(value: JSONObject): UpdateMetadata {
        val applicationId = value.requiredString("applicationId")
        require(applicationId.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        val versionCode = value.requiredLong("versionCode")
        require(versionCode in 1..2_100_000_000L)
        val name = value.requiredString("versionName")
        require(name.length <= 200)
        val minSdk = value.requiredLong("minSdk")
        require(minSdk in 1..Int.MAX_VALUE.toLong())
        val apkName = value.requiredString("apkFileName")
        require(apkName.length <= 255 && apkName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk", RegexOption.IGNORE_CASE)))
        val apkSize = value.requiredLong("apkSize")
        require(apkSize in 1..2_147_483_647L)
        val hash = value.requiredString("sha256")
        require(hash.matches(Regex("[0-9a-fA-F]{64}")))
        return UpdateMetadata(applicationId, versionCode, name, minSdk.toInt(), apkName, apkSize, hash.lowercase(Locale.ROOT))
    }
}

internal fun JSONObject.requiredString(key: String, allowEmpty: Boolean = false): String {
    val value = get(key) as? String ?: error("$key must be text")
    require(allowEmpty || value.isNotBlank())
    return value
}

internal fun JSONObject.requiredLong(key: String): Long {
    val value = get(key)
    // JSON floating point values and numeric strings must not silently truncate into a version or size.
    require(value is Long || value is Int)
    return (value as Number).toLong()
}

/** Public GitHub assets only. Signing verification remains mandatory after a download. */
object ReleaseUrls {
    const val REPOSITORY = "ReiTut/Mooscles"
    const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"
    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"

    fun isRelease(value: String): Boolean = validUri(value)?.let {
        it.host.equals("github.com", ignoreCase = true) &&
            it.path.startsWith("/$REPOSITORY/releases/tag/") &&
            it.rawQuery == null && it.rawFragment == null
    } ?: false

    fun isApkAsset(value: String): Boolean = isReleaseAsset(value) &&
        (validUri(value)?.path?.endsWith(".apk", ignoreCase = true) == true)

    fun isReleaseAsset(value: String): Boolean = validUri(value)?.let {
        it.host.equals("github.com", ignoreCase = true) &&
            it.path.startsWith("/$REPOSITORY/releases/download/") &&
            it.rawQuery == null && it.rawFragment == null
    } ?: false

    fun isAllowedConnection(value: String): Boolean = validUri(value)?.let {
        when (it.host.lowercase(Locale.ROOT)) {
            "api.github.com" -> it.path == "/repos/$REPOSITORY/releases"
            "github.com" -> isReleaseAsset(value)
            "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com" -> true
            else -> false
        }
    } ?: false

    private fun validUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && it.host != null &&
            it.userInfo == null && (it.port == -1 || it.port == 443)
    }
}
