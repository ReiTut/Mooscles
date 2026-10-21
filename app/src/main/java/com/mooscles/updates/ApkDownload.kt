package com.mooscles.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Only update binaries travel over this connection. No training data is read or transmitted. */
class ApkDownload {
    suspend fun download(update: AvailableUpdate, destination: File, progress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        require(update.metadata.apkSize in 1..512L * 1024 * 1024) { "Update APK size is unsupported." }
        destination.parentFile?.mkdirs()
        var url = update.apkUrl
        var connection: HttpURLConnection? = null
        try {
            for (redirect in 0..5) {
                require(allowed(url)) { "Untrusted APK download address." }
                val next = URL(url).openConnection() as HttpURLConnection
                connection = next
                next.instanceFollowRedirects = false
                next.connectTimeout = 20_000; next.readTimeout = 25_000
                next.setRequestProperty("User-Agent", "Mooscles-updater")
                next.setRequestProperty("Accept", "application/octet-stream")
                val status = next.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = next.getHeaderField("Location") ?: throw IOException("Missing download redirect.")
                    url = URI(url).resolve(location).toString(); next.disconnect()
                    if (redirect == 5) throw IOException("Too many download redirects.")
                    continue
                }
                if (status != 200) throw IOException("GitHub download returned HTTP $status. Please retry later.")
                if (next.contentLengthLong > 0 && next.contentLengthLong != update.metadata.apkSize)
                    throw IOException("The APK size differs from its release metadata.")
                var bytes = 0L
                var reported = 0L
                next.inputStream.use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > update.metadata.apkSize) throw IOException("The downloaded APK is larger than expected.")
                        output.write(buffer, 0, count)
                        if (bytes - reported >= 256 * 1024 || bytes == update.metadata.apkSize) {
                            progress(bytes); reported = bytes
                        }
                    }
                    output.flush()
                } }
                if (bytes != update.metadata.apkSize) throw IOException("The APK download was incomplete.")
                return@withContext
            }
        } finally { connection?.disconnect() }
    }

    companion object {
        fun allowed(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
                uri.host in setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com")
        }.getOrDefault(false)
    }
}
