package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchPolicyTest {
    private fun candidate(
        id: String = "app",
        installed: Long = 1,
        target: Long = 2,
        verified: Boolean = true,
        downloadable: Boolean = true,
    ) = UpdateCandidate(id, installed, target, verified, downloadable)

    @Test fun manualStableAllowsVerifiedUpgradeOnWifi() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate()),
            UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = true),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.WIFI,
        )
        assertEquals(listOf("app"), plan.eligible.map { it.appId })
        assertTrue(plan.blocked.isEmpty())
    }

    @Test fun stableBlocksCandidateRelease() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate(verified = false)),
            UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.METERED,
        )
        assertEquals("CHANNEL_BLOCKED", plan.blocked["app"])
    }

    @Test fun betaAllowsCandidateRelease() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate(verified = false)),
            UpdatePolicyPreferences(channel = UpdateChannel.BETA, wifiOnly = false),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.METERED,
        )
        assertEquals(1, plan.eligible.size)
    }

    @Test fun wifiOnlyBlocksMeteredNetwork() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate()),
            UpdatePolicyPreferences(wifiOnly = true),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.METERED,
        )
        assertEquals("WIFI_REQUIRED", plan.blocked["app"])
    }

    @Test fun autoUpdateMustBeExplicitlyEnabled() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate()),
            UpdatePolicyPreferences(autoUpdateEnabled = false, wifiOnly = false),
            UpdateTrigger.AUTO_UPDATE,
            NetworkKind.METERED,
        )
        assertEquals("AUTO_UPDATE_DISABLED", plan.blocked["app"])
    }

    @Test fun downgradeAndSameVersionAreBlocked() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate(id = "same", installed = 2, target = 2), candidate(id = "down", installed = 3, target = 2)),
            UpdatePolicyPreferences(wifiOnly = false),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.METERED,
        )
        assertEquals("NOT_AN_UPGRADE", plan.blocked["same"])
        assertEquals("NOT_AN_UPGRADE", plan.blocked["down"])
    }

    @Test fun duplicateIdsFailClosed() {
        val plan = UpdateBatchPolicy.plan(
            listOf(candidate(id = "dup"), candidate(id = "dup", target = 3)),
            UpdatePolicyPreferences(wifiOnly = false),
            UpdateTrigger.MANUAL_UPDATE_ALL,
            NetworkKind.METERED,
        )
        assertTrue(plan.eligible.isEmpty())
        assertEquals("DUPLICATE_APP_ID", plan.blocked["dup"])
    }
}
