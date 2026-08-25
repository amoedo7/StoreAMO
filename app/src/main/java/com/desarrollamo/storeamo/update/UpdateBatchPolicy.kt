package com.desarrollamo.storeamo.update

enum class UpdateChannel { STABLE, BETA }
enum class UpdateTrigger { MANUAL_UPDATE_ALL, AUTO_UPDATE }
enum class NetworkKind { WIFI, METERED, OFFLINE }

data class UpdatePolicyPreferences(
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val autoUpdateEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
)

data class UpdateCandidate(
    val appId: String,
    val installedVersionCode: Long,
    val targetVersionCode: Long,
    val verified: Boolean,
    val downloadable: Boolean = true,
)

data class UpdateBatchPlan(
    val eligible: List<UpdateCandidate>,
    val blocked: Map<String, String>,
) {
    val isEmpty: Boolean get() = eligible.isEmpty()
}

/**
 * Pure policy layer for StoreAMO batch updates.
 *
 * It deliberately does not install anything: UI / workers must execute eligible
 * updates sequentially through the existing verified DownloadInstaller flow.
 */
object UpdateBatchPolicy {
    fun plan(
        candidates: List<UpdateCandidate>,
        preferences: UpdatePolicyPreferences,
        trigger: UpdateTrigger,
        network: NetworkKind,
    ): UpdateBatchPlan {
        val blocked = linkedMapOf<String, String>()
        val eligible = mutableListOf<UpdateCandidate>()

        val duplicateIds = candidates.groupingBy { it.appId }.eachCount().filterValues { it > 1 }.keys

        for (candidate in candidates.sortedBy { it.appId }) {
            val reason = when {
                candidate.appId.isBlank() -> "INVALID_APP_ID"
                candidate.appId in duplicateIds -> "DUPLICATE_APP_ID"
                !candidate.downloadable -> "ARTIFACT_NOT_DOWNLOADABLE"
                candidate.targetVersionCode <= candidate.installedVersionCode -> "NOT_AN_UPGRADE"
                preferences.channel == UpdateChannel.STABLE && !candidate.verified -> "CHANNEL_BLOCKED"
                network == NetworkKind.OFFLINE -> "NETWORK_OFFLINE"
                preferences.wifiOnly && network != NetworkKind.WIFI -> "WIFI_REQUIRED"
                trigger == UpdateTrigger.AUTO_UPDATE && !preferences.autoUpdateEnabled -> "AUTO_UPDATE_DISABLED"
                else -> null
            }
            if (reason == null) eligible += candidate else blocked[candidate.appId.ifBlank { "<blank>" }] = reason
        }

        return UpdateBatchPlan(eligible = eligible, blocked = blocked)
    }
}
