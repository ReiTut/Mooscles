package com.mooscles.updates

import android.content.pm.PackageInstaller
import org.junit.Assert.*
import org.junit.Test

class InstallOutcomeTest {
    @Test fun approvalRemainsPendingUntilTheUserOpensConfirmation() {
        val outcome = InstallOutcomePolicy.fromStatus(PackageInstaller.STATUS_PENDING_USER_ACTION, null, "intent:approval")
        assertEquals(InstallOutcome.ApprovalRequired("intent:approval"), outcome)
        assertTrue(InstallOutcomePolicy.fromStatus(PackageInstaller.STATUS_PENDING_USER_ACTION, null, null) is InstallOutcome.Failed)
    }

    @Test fun successAndUserCancellationAreDistinct() {
        assertEquals(InstallOutcome.Succeeded, InstallOutcomePolicy.fromStatus(PackageInstaller.STATUS_SUCCESS, null, null))
        assertEquals(InstallOutcome.Cancelled, InstallOutcomePolicy.fromStatus(PackageInstaller.STATUS_FAILURE_ABORTED, "User declined", null))
    }

    @Test fun supportedFailuresProduceActionableMessages() {
        for (status in listOf(
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE,
        )) {
            val outcome = InstallOutcomePolicy.fromStatus(status, null, null)
            assertTrue(outcome is InstallOutcome.Failed)
            assertTrue((outcome as InstallOutcome.Failed).message.isNotBlank())
        }
    }
}
