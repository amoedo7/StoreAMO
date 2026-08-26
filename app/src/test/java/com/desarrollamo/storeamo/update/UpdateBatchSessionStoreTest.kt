package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateBatchSessionStoreTest {
    @Test fun roundTripPreservesOrderAndInFlight() {
        val original = UpdateBatchSession(
            pending = listOf(UpdateBatchEntry("a", 2), UpdateBatchEntry("b", 3)),
            inFlightAppId = "a",
        )
        assertEquals(original, UpdateBatchSessionCodec.decode(UpdateBatchSessionCodec.encode(original)))
    }

    @Test fun duplicateAppsAreDeduplicatedOnDecode() {
        val decoded = UpdateBatchSessionCodec.decode(setOf("P|0|a|2", "P|1|a|3", "P|2|b|4"))
        assertEquals(listOf("a", "b"), decoded.pending.map { it.appId })
    }

    @Test fun orphanInFlightFailsClosed() {
        val decoded = UpdateBatchSessionCodec.decode(setOf("P|0|a|2", "I|missing"))
        assertNull(decoded.inFlightAppId)
    }

    @Test fun corruptRowsAreIgnored() {
        val decoded = UpdateBatchSessionCodec.decode(setOf("garbage", "P|x|a|2", "P|0|b|-1", "P|1|c|5"))
        assertEquals(listOf(UpdateBatchEntry("c", 5)), decoded.pending)
    }
}
