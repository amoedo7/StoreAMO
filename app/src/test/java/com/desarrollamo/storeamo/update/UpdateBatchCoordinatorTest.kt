package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchCoordinatorTest {
    private fun candidate(
        id: String,
        installed: Long = 1,
        target: Long = 2,
        verified: Boolean = true,
    ) = UpdateCandidate(
        appId = id,
        installedVersionCode = installed,
        targetVersionCode = target,
        verified = verified,
    )

    @Test
    fun `begin preserves policy blocks and queues only eligible apps`() {
        val state = UpdateBatchCoordinator.begin(
            candidates = listOf(
                candidate("good"),
                candidate("same", installed = 2, target = 2),
                candidate("candidate", verified = false),
            ),
            preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.METERED,
        )

        assertEquals(listOf("good"), state.execution.items.map { it.appId })
        assertEquals("NOT_AN_UPGRADE", state.plan.blocked["same"])
        assertEquals("CHANNEL_BLOCKED", state.plan.blocked["candidate"])
    }

    @Test
    fun `coordinator advances one update at a time`() {
        var state = UpdateBatchCoordinator.begin(
            candidates = listOf(candidate("one"), candidate("two")),
            preferences = UpdatePolicyPreferences(wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.METERED,
        )

        assertEquals(UpdateBatchExecutionAction.Start("one"), UpdateBatchCoordinator.nextAction(state))
        state = UpdateBatchCoordinator.markStarted(state, "one")
        assertEquals(UpdateBatchExecutionAction.Reconcile("one"), UpdateBatchCoordinator.nextAction(state))

        state = UpdateBatchCoordinator.recordInstallResult(state, "one", success = true)
        assertEquals(UpdateBatchExecutionAction.Start("two"), UpdateBatchCoordinator.nextAction(state))
    }

    @Test
    fun `reconcile requires admitted target version not merely installed package`() {
        var state = UpdateBatchCoordinator.begin(
            candidates = listOf(candidate("app", installed = 3, target = 7)),
            preferences = UpdatePolicyPreferences(wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.METERED,
        )
        state = UpdateBatchCoordinator.markStarted(state, "app")
        state = UpdateBatchCoordinator.reconcileActive(state, installedVersionCode = 6)

        val item = state.execution.items.single()
        assertEquals(UpdateBatchItemStatus.FAILED, item.status)
        assertEquals("TARGET_VERSION_NOT_INSTALLED", item.lastError)
    }

    @Test
    fun `reconcile succeeds when Android reports target or newer`() {
        var state = UpdateBatchCoordinator.begin(
            candidates = listOf(candidate("app", installed = 3, target = 7)),
            preferences = UpdatePolicyPreferences(wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.METERED,
        )
        state = UpdateBatchCoordinator.markStarted(state, "app")
        state = UpdateBatchCoordinator.reconcileActive(state, installedVersionCode = 8)

        assertEquals(UpdateBatchItemStatus.SUCCEEDED, state.execution.items.single().status)
        assertTrue(state.execution.isFinished)
    }

    @Test
    fun `failed app does not block next eligible update`() {
        var state = UpdateBatchCoordinator.begin(
            candidates = listOf(candidate("a"), candidate("b")),
            preferences = UpdatePolicyPreferences(wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.METERED,
        )
        state = UpdateBatchCoordinator.markStarted(state, "a")
        state = UpdateBatchCoordinator.recordInstallResult(state, "a", success = false, error = "USER_CANCELLED")

        assertEquals(UpdateBatchExecutionAction.Start("b"), UpdateBatchCoordinator.nextAction(state))
        val summary = UpdateBatchCoordinator.summary(state)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.pending)
        assertFalse(summary.finished)
    }

    @Test
    fun `auto update still requires explicit opt in`() {
        val state = UpdateBatchCoordinator.begin(
            candidates = listOf(candidate("app")),
            preferences = UpdatePolicyPreferences(autoUpdateEnabled = false, wifiOnly = false),
            trigger = UpdateTrigger.AUTO_UPDATE,
            network = NetworkKind.METERED,
        )

        assertTrue(state.plan.eligible.isEmpty())
        assertEquals("AUTO_UPDATE_DISABLED", state.plan.blocked["app"])
        assertEquals(UpdateBatchExecutionAction.Complete, UpdateBatchCoordinator.nextAction(state))
    }
}
