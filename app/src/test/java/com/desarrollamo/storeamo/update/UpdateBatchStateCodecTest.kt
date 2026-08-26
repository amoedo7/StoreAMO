package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchStateCodecTest {
    @Test
    fun roundTripPreservesInFlightRecoveryStateAndAdmittedTarget() {
        val state = UpdateBatchExecutionState(
            items = listOf(
                UpdateBatchExecutionItem("app.a", UpdateBatchItemStatus.SUCCEEDED, targetVersionCode = 11),
                UpdateBatchExecutionItem("app.b", UpdateBatchItemStatus.RUNNING, targetVersionCode = 22),
                UpdateBatchExecutionItem("app.c", UpdateBatchItemStatus.PENDING, targetVersionCode = 33),
            ),
            activeAppId = "app.b",
        )

        assertEquals(state, UpdateBatchStateCodec.decode(UpdateBatchStateCodec.encode(state)))
    }

    @Test
    fun roundTripPreservesFailureEvidence() {
        val state = UpdateBatchExecutionState(
            items = listOf(
                UpdateBatchExecutionItem(
                    "app.a",
                    UpdateBatchItemStatus.FAILED,
                    "INSTALL_FAILED",
                    targetVersionCode = 9,
                )
            ),
        )

        assertEquals(state, UpdateBatchStateCodec.decode(UpdateBatchStateCodec.encode(state)))
    }

    @Test
    fun oldOrUnsupportedStateFailsClosed() {
        assertNull(UpdateBatchStateCodec.decode(emptyMap()))
        assertNull(
            UpdateBatchStateCodec.decode(
                mapOf(
                    "version" to "1",
                    "item_count" to "0",
                    "active_app_id" to "",
                )
            )
        )
        assertNull(
            UpdateBatchStateCodec.decode(
                mapOf(
                    "version" to "999",
                    "item_count" to "0",
                    "active_app_id" to "",
                )
            )
        )
    }

    @Test
    fun missingOrInvalidTargetFailsClosed() {
        val base = mapOf(
            "version" to "2",
            "item_count" to "1",
            "active_app_id" to "",
            "item.0.app_id" to "app.a",
            "item.0.status" to "PENDING",
            "item.0.error" to "",
        )
        assertNull(UpdateBatchStateCodec.decode(base))
        assertNull(UpdateBatchStateCodec.decode(base + ("item.0.target_version_code" to "0")))
        assertNull(UpdateBatchStateCodec.decode(base + ("item.0.target_version_code" to "not-a-number")))
    }

    @Test
    fun duplicateIdsFailClosed() {
        val raw = mapOf(
            "version" to "2",
            "item_count" to "2",
            "active_app_id" to "",
            "item.0.app_id" to "same",
            "item.0.status" to "PENDING",
            "item.0.error" to "",
            "item.0.target_version_code" to "2",
            "item.1.app_id" to "same",
            "item.1.status" to "FAILED",
            "item.1.error" to "x",
            "item.1.target_version_code" to "3",
        )

        assertNull(UpdateBatchStateCodec.decode(raw))
    }

    @Test
    fun activeAppMustMatchExactlyOneRunningItem() {
        val missingRunning = mapOf(
            "version" to "2",
            "item_count" to "1",
            "active_app_id" to "app.a",
            "item.0.app_id" to "app.a",
            "item.0.status" to "PENDING",
            "item.0.error" to "",
            "item.0.target_version_code" to "7",
        )
        assertNull(UpdateBatchStateCodec.decode(missingRunning))

        val state = UpdateBatchExecutionState(
            items = listOf(UpdateBatchExecutionItem("app.a", UpdateBatchItemStatus.RUNNING, targetVersionCode = 7)),
            activeAppId = "app.a",
        )
        assertTrue(UpdateBatchStateCodec.encode(state).isNotEmpty())
    }
}
