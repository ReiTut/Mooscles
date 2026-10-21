package com.mooscles.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkVerificationException(message: String) : IllegalArgumentException(message)

/** Certificate digests, including the platform-verified, oldest-to-newest rotation lineage. */
data class ApkSigningIdentity(
    val currentSigners: Set<String>,
    val history: List<String> = currentSigners.toList(),
    val multipleSigners: Boolean = currentSigners.size > 1,
)

data class ApkIdentity(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val signing: ApkSigningIdentity,
)

data class VerifiedApk(val file: File, val identity: ApkIdentity)

/** Kept separate from PackageManager so malformed identity and signing cases are testable. */
object ApkVerificationPolicy {
    fun signaturesCompatible(installed: ApkSigningIdentity, candidate: ApkSigningIdentity): Boolean {
        if (installed.currentSigners.isEmpty() || candidate.currentSigners.isEmpty()) return false
        if (installed.currentSigners == candidate.currentSigners) return true
        // Multiple signers must all match; sharing one signer or an ancestor is insufficient.
        if (installed.multipleSigners || candidate.multipleSigners ||
            installed.currentSigners.size != 1 || candidate.currentSigners.size != 1
        ) return false
        val candidateCurrent = candidate.currentSigners.single()
        val installedCurrent = installed.currentSigners.single()
        // A forward rotation is allowed only when Android proved the new APK's lineage.
        // An old signing certificate, or a sibling rotation sharing an ancestor, is rejected.
        return candidate.history.lastOrNull() == candidateCurrent &&
            candidate.history.distinct().size == candidate.history.size &&
            installedCurrent in candidate.history.dropLast(1)
    }

    fun verifyIdentity(
        metadata: UpdateMetadata,
        installed: ApkIdentity,
        candidate: ApkIdentity,
        deviceSdk: Int,
    ) {
        check(metadata.applicationId == installed.applicationId && candidate.applicationId == installed.applicationId,
            "The update belongs to a different application.")
        check(metadata.versionCode > installed.versionCode, "This update is not newer than the installed app.")
        check(candidate.versionCode == metadata.versionCode, "The APK version does not match update.json.")
        check(candidate.versionName == metadata.versionName, "The APK version name does not match update.json.")
        check(candidate.minSdk == metadata.minSdk, "The APK Android requirement does not match update.json.")
        check(candidate.minSdk in 1..deviceSdk, "This update requires a newer Android version.")
        check(signaturesCompatible(installed.signing, candidate.signing),
            "The update has a different signing identity. Obtain an APK signed with the existing compatible key.")
    }

    fun verifyContents(file: File, expectedSize: Long, expectedSha256: String) {
        check(expectedSize > 0 && file.isFile && file.length() == expectedSize,
            "The APK download is incomplete or its size does not match update.json.")
        check(expectedSha256.matches(Regex("[a-fA-F0-9]{64}")), "The APK checksum in update.json is invalid.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expectedSha256, ignoreCase = true), "The APK checksum does not match update.json.")
    }

    private fun check(condition: Boolean, message: String) {
        if (!condition) throw ApkVerificationException(message)
    }
}

class ApkVerifier(context: Context) {
    private val context = context.applicationContext

    /** Perform off the main thread. PackageInstaller performs its own final signature validation. */
    fun verify(file: File, metadata: UpdateMetadata): VerifiedApk {
        ApkVerificationPolicy.verifyContents(file, metadata.apkSize, metadata.sha256)
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val installedInfo = manager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        val candidateInfo = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw ApkVerificationException("The downloaded file is not a readable, signed APK.")
        val installed = identity(installedInfo)
        val candidate = identity(candidateInfo)
        ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate, Build.VERSION.SDK_INT)
        return VerifiedApk(file, candidate)
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val appInfo = info.applicationInfo
            ?: throw ApkVerificationException("The APK application information is missing.")
        val signing = if (Build.VERSION.SDK_INT >= 28) {
            val signatures = info.signingInfo
                ?: throw ApkVerificationException("The APK signing information is missing.")
            val current = signatures.apkContentsSigners.orEmpty().map(::certificateDigest).toSet()
            val history = if (signatures.hasMultipleSigners()) current.toList()
                else signatures.signingCertificateHistory.orEmpty().map(::certificateDigest)
            ApkSigningIdentity(current, history, signatures.hasMultipleSigners())
        } else {
            ApkSigningIdentity(info.signatures.orEmpty().map(::certificateDigest).toSet())
        }
        return ApkIdentity(
            info.packageName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.versionName.orEmpty(),
            appInfo.minSdkVersion,
            signing,
        )
    }

    private fun certificateDigest(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
}
