package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchStateCodecTest {
    @Test
    fun roundTripPreservesInFlightRecoveryState() {
        val state = UpdateBatchExecutionState(
            items = listOf(
                UpdateBatchExecutionItem("app.a", UpdateBatchItemStatus.SUCCEEDED),
                UpdateBatchExecutionItem("app.b", UpdateBatchItemStatus.RUNNING),
                UpdateBatchExecutionItem("app.c", UpdateBatchItemStatus.PENDING),
            ),
            activeAppId = "app.b",
        )

        assertEquals(state, UpdateBatchStateCodec.decode(UpdateBatchStateCodec.encode(state)))
    }

    @Test
    fun roundTripPreservesFailureEvidence() {
        val state = UpdateBatchExecutionState(
            items = listOf(UpdateBatchExecutionItem("app.a", UpdateBatchItemStatus.FAILED, "INSTALL_FAILED")),
        )

        assertEquals(state, UpdateBatchStateCodec.decode(UpdateBatchStateCodec.encode(state)))
    }

    @Test
    fun corruptOrUnsupportedStateFailsClosed() {
        assertNull(UpdateBatchStateCodec.decode(emptyMap()))
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
    fun duplicateIdsFailClosed() {
        val raw = mapOf(
            "version" to "1",
            "item_count" to "2",
            "active_app_id" to "",
            "item.0.app_id" to "same",
            "item.0.status" to "PENDING",
            "item.0.error" to "",
            "item.1.app_id" to "same",
            "item.1.status" to "FAILED",
            "item.1.error" to "x",
        )

        assertNull(UpdateBatchStateCodec.decode(raw))
    }

    @Test
    fun activeAppMustMatchExactlyOneRunningItem() {
        val missingRunning = mapOf(
            "version" to "1",
            "item_count" to "1",
            "active_app_id" to "app.a",
            "item.0.app_id" to "app.a",
            "item.0.status" to "PENDING",
            "item.0.error" to "",
        )
        assertNull(UpdateBatchStateCodec.decode(missingRunning))

        val state = UpdateBatchExecutionState(
            items = listOf(UpdateBatchExecutionItem("app.a", UpdateBatchItemStatus.RUNNING)),
            activeAppId = "app.a",
        )
        assertTrue(UpdateBatchStateCodec.encode(state).isNotEmpty())
    }
}
