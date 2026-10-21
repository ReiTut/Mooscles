package com.mooscles.updates

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ApkVerifierTest {
    @get:Rule val temporary = TemporaryFolder()
    private val originalKey = ApkSigningIdentity(setOf("original"))
    private val metadata = UpdateMetadata("com.mooscles", 6L, "1.0.3", 26, "Mooscles-v1.0.3.apk", 3, "0".repeat(64))
    private val installed = ApkIdentity("com.mooscles", 5, "1.0.2", 26, originalKey)
    private val candidate = ApkIdentity("com.mooscles", 6, "1.0.3", 26, originalKey)

    @Test fun newerVersionWithMatchingIdentityIsAccepted() {
        ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate, 35)
    }

    @Test fun equalAndOlderMetadataCannotInstall() {
        for (versionCode in listOf(4L, 5L)) {
            rejects { ApkVerificationPolicy.verifyIdentity(metadata.copy(versionCode = versionCode), installed, candidate, 35) }
        }
    }

    @Test fun aDifferentPackageIsRejectedEvenWithTheSameSigningKey() {
        rejects { ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate.copy(applicationId = "other.package"), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(metadata.copy(applicationId = "other.package"), installed, candidate, 35) }
    }

    @Test fun apkVersionCodeAndNameMustMatchMetadata() {
        rejects { ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate.copy(versionCode = 7), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate.copy(versionName = "different"), 35) }
    }

    @Test fun androidRequirementMustBeCompatibleAndMatchMetadata() {
        rejects { ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate.copy(minSdk = 27), 35) }
        rejects { ApkVerificationPolicy.verifyIdentity(metadata.copy(minSdk = 36), installed, candidate.copy(minSdk = 36), 35) }
    }

    @Test fun aDifferentSigningKeyIsRejected() {
        rejects {
            ApkVerificationPolicy.verifyIdentity(metadata, installed, candidate.copy(signing = ApkSigningIdentity(setOf("unknown"))), 35)
        }
    }

    @Test fun verifiedForwardKeyRotationIsAcceptedButReverseAndSiblingRotationsAreRejected() {
        val rotated = ApkSigningIdentity(setOf("new"), listOf("original", "new"))
        assertTrue(ApkVerificationPolicy.signaturesCompatible(originalKey, rotated))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(rotated, originalKey))
        val sibling = ApkSigningIdentity(setOf("sibling"), listOf("original", "sibling"))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(rotated, sibling))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(originalKey, rotated.copy(history = listOf("new", "original"))))
    }

    @Test fun multipleSignersRequireTheEntireMatchingSet() {
        val multiple = ApkSigningIdentity(setOf("first", "second"))
        assertTrue(ApkVerificationPolicy.signaturesCompatible(multiple, multiple.copy(currentSigners = setOf("second", "first"))))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(multiple, ApkSigningIdentity(setOf("first"))))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(multiple, ApkSigningIdentity(setOf("first", "third"))))
        assertFalse(ApkVerificationPolicy.signaturesCompatible(ApkSigningIdentity(emptySet()), ApkSigningIdentity(emptySet())))
    }

    @Test fun matchingFileSizeAndSha256AreAcceptedIncludingUppercaseHex() {
        val file = temporary.newFile("update.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        ApkVerificationPolicy.verifyContents(file, 3, hash.uppercase())
    }

    @Test fun missingTruncatedCorruptAndMalformedChecksumsAreRejected() {
        val file = temporary.newFile("update.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        rejects { ApkVerificationPolicy.verifyContents(file, 4, "0".repeat(64)) }
        rejects { ApkVerificationPolicy.verifyContents(file, 3, "not-a-sha256") }
        rejects { ApkVerificationPolicy.verifyContents(file, 3, "0".repeat(64)) }
        assertTrue(file.delete())
        rejects { ApkVerificationPolicy.verifyContents(file, 3, "0".repeat(64)) }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("An incompatible or corrupt update was accepted")
        } catch (_: ApkVerificationException) { }
    }
}
