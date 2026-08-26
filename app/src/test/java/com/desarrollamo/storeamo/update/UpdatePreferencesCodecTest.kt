package com.desarrollamo.storeamo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePreferencesCodecTest {
    @Test fun emptyStorageUsesConservativeDefaults() {
        val value = UpdatePreferencesCodec.decode(emptyMap())
        assertEquals(UpdateCheckFrequency.MANUAL, value.frequency)
        assertEquals(UpdateChannel.STABLE, value.channel)
        assertFalse(value.autoUpdateEnabled)
        assertTrue(value.wifiOnly)
        assertTrue(value.notificationsEnabled)
    }

    @Test fun roundTripPreservesAllUserChoices() {
        val original = StoredUpdatePreferences(
            frequency = UpdateCheckFrequency.DAILY,
            channel = UpdateChannel.BETA,
            autoUpdateEnabled = true,
            wifiOnly = false,
            notificationsEnabled = false,
        )
        assertEquals(original, UpdatePreferencesCodec.decode(UpdatePreferencesCodec.encode(original)))
    }

    @Test fun corruptStorageFailsBackToSafeValues() {
        val value = UpdatePreferencesCodec.decode(
            mapOf(
                "frequency" to "EVERY_SECOND",
                "channel" to "UNKNOWN",
                "auto_update" to "yes",
                "wifi_only" to "0",
                "notifications" to "maybe",
            )
        )
        assertEquals(UpdateCheckFrequency.MANUAL, value.frequency)
        assertEquals(UpdateChannel.STABLE, value.channel)
        assertFalse(value.autoUpdateEnabled)
        assertTrue(value.wifiOnly)
        assertTrue(value.notificationsEnabled)
    }

    @Test fun policyProjectionKeepsInstallationControls() {
        val policy = StoredUpdatePreferences(
            channel = UpdateChannel.BETA,
            autoUpdateEnabled = true,
            wifiOnly = false,
        ).toPolicyPreferences()
        assertEquals(UpdateChannel.BETA, policy.channel)
        assertTrue(policy.autoUpdateEnabled)
        assertFalse(policy.wifiOnly)
    }
}
