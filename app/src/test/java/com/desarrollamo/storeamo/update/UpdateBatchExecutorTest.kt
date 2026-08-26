package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchExecutorTest {
    private fun plan(vararg ids: String) = UpdateBatchPlan(
        eligible = ids.mapIndexed { index, id ->
            UpdateCandidate(
                appId = id,
                installedVersionCode = index.toLong() + 1,
                targetVersionCode = index.toLong() + 2,
                verified = true,
            )
        },
        blocked = emptyMap(),
    )

    @Test
    fun `starts exactly one update and advances sequentially`() {
        var state = UpdateBatchExecutor.begin(plan("a", "b"))
        assertEquals(UpdateBatchExecutionAction.Start("a"), UpdateBatchExecutor.nextAction(state))

        state = UpdateBatchExecutor.markStarted(state, "a")
        assertEquals(UpdateBatchExecutionAction.Reconcile("a"), UpdateBatchExecutor.nextAction(state))

        state = UpdateBatchExecutor.recordResult(state, "a", success = true)
        assertEquals(UpdateBatchExecutionAction.Start("b"), UpdateBatchExecutor.nextAction(state))

        state = UpdateBatchExecutor.markStarted(state, "b")
        state = UpdateBatchExecutor.recordResult(state, "b", success = true)
        assertEquals(UpdateBatchExecutionAction.Complete, UpdateBatchExecutor.nextAction(state))
        assertTrue(state.isFinished)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot start a second app while one is active`() {
        var state = UpdateBatchExecutor.begin(plan("a", "b"))
        state = UpdateBatchExecutor.markStarted(state, "a")
        UpdateBatchExecutor.markStarted(state, "b")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects result for non active app`() {
        var state = UpdateBatchExecutor.begin(plan("a", "b"))
        state = UpdateBatchExecutor.markStarted(state, "a")
        UpdateBatchExecutor.recordResult(state, "b", success = true)
    }

    @Test
    fun `failed install is terminal for that app and batch continues`() {
        var state = UpdateBatchExecutor.begin(plan("a", "b"))
        state = UpdateBatchExecutor.markStarted(state, "a")
        state = UpdateBatchExecutor.recordResult(state, "a", success = false, error = "USER_CANCELLED")

        val failed = state.items.first { it.appId == "a" }
        assertEquals(UpdateBatchItemStatus.FAILED, failed.status)
        assertEquals("USER_CANCELLED", failed.lastError)
        assertEquals(UpdateBatchExecutionAction.Start("b"), UpdateBatchExecutor.nextAction(state))
    }

    @Test
    fun `recovery never retries an in-flight update blindly`() {
        var state = UpdateBatchExecutor.begin(plan("a", "b"))
        state = UpdateBatchExecutor.markStarted(state, "a")

        assertEquals(UpdateBatchExecutionAction.Reconcile("a"), UpdateBatchExecutor.nextAction(state))
        state = UpdateBatchExecutor.reconcile(state, "a", targetInstalled = true)

        assertEquals(UpdateBatchItemStatus.SUCCEEDED, state.items.first { it.appId == "a" }.status)
        assertEquals(UpdateBatchExecutionAction.Start("b"), UpdateBatchExecutor.nextAction(state))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `begin rejects duplicate eligible ids even if caller bypasses policy`() {
        UpdateBatchExecutor.begin(plan("a", "a"))
    }
}
