package com.desarrollamo.storeamo.update

import android.content.Context

/** Persisted user policy for StoreAMO update checks and installations. */
enum class UpdateCheckFrequency { MANUAL, DAILY, WEEKLY }

data class StoredUpdatePreferences(
    val frequency: UpdateCheckFrequency = UpdateCheckFrequency.MANUAL,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val autoUpdateEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val notificationsEnabled: Boolean = true,
) {
    fun toPolicyPreferences() = UpdatePolicyPreferences(
        channel = channel,
        autoUpdateEnabled = autoUpdateEnabled,
        wifiOnly = wifiOnly,
    )
}

/**
 * Pure codec kept separate from Android storage so policy persistence can be
 * regression-tested on the JVM. Unknown/corrupt values fall back to safe defaults.
 */
object UpdatePreferencesCodec {
    private const val FREQUENCY = "frequency"
    private const val CHANNEL = "channel"
    private const val AUTO_UPDATE = "auto_update"
    private const val WIFI_ONLY = "wifi_only"
    private const val NOTIFICATIONS = "notifications"

    fun encode(value: StoredUpdatePreferences): Map<String, String> = mapOf(
        FREQUENCY to value.frequency.name,
        CHANNEL to value.channel.name,
        AUTO_UPDATE to value.autoUpdateEnabled.toString(),
        WIFI_ONLY to value.wifiOnly.toString(),
        NOTIFICATIONS to value.notificationsEnabled.toString(),
    )

    fun decode(raw: Map<String, String?>): StoredUpdatePreferences {
        val defaults = StoredUpdatePreferences()
        return StoredUpdatePreferences(
            frequency = raw[FREQUENCY]?.let { runCatching { UpdateCheckFrequency.valueOf(it) }.getOrNull() }
                ?: defaults.frequency,
            channel = raw[CHANNEL]?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() }
                ?: defaults.channel,
            autoUpdateEnabled = strictBoolean(raw[AUTO_UPDATE]) ?: defaults.autoUpdateEnabled,
            wifiOnly = strictBoolean(raw[WIFI_ONLY]) ?: defaults.wifiOnly,
            notificationsEnabled = strictBoolean(raw[NOTIFICATIONS]) ?: defaults.notificationsEnabled,
        )
    }

    private fun strictBoolean(value: String?): Boolean? = when (value) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

class UpdatePreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): StoredUpdatePreferences = UpdatePreferencesCodec.decode(
        mapOf(
            "frequency" to prefs.getString("frequency", null),
            "channel" to prefs.getString("channel", null),
            "auto_update" to prefs.getString("auto_update", null),
            "wifi_only" to prefs.getString("wifi_only", null),
            "notifications" to prefs.getString("notifications", null),
        )
    )

    fun save(value: StoredUpdatePreferences) {
        val encoded = UpdatePreferencesCodec.encode(value)
        prefs.edit()
            .putString("frequency", encoded.getValue("frequency"))
            .putString("channel", encoded.getValue("channel"))
            .putString("auto_update", encoded.getValue("auto_update"))
            .putString("wifi_only", encoded.getValue("wifi_only"))
            .putString("notifications", encoded.getValue("notifications"))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "storeamo_update_preferences_v1"
    }
}
