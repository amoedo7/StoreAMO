package com.desarrollamo.storeamo.update

import android.content.Context

object UpdateBatchStateCodec {
    private const val VERSION = "2"
    private const val KEY_VERSION = "version"
    private const val KEY_COUNT = "item_count"
    private const val KEY_ACTIVE = "active_app_id"

    fun encode(state: UpdateBatchExecutionState): Map<String, String> {
        validate(state)
        val raw = linkedMapOf(
            KEY_VERSION to VERSION,
            KEY_COUNT to state.items.size.toString(),
            KEY_ACTIVE to (state.activeAppId ?: ""),
        )
        state.items.forEachIndexed { index, item ->
            raw["item.$index.app_id"] = item.appId
            raw["item.$index.status"] = item.status.name
            raw["item.$index.error"] = item.lastError ?: ""
            raw["item.$index.target_version_code"] = item.targetVersionCode.toString()
        }
        return raw
    }

    fun decode(raw: Map<String, String?>): UpdateBatchExecutionState? = runCatching {
        require(raw[KEY_VERSION] == VERSION) { "unsupported persisted batch version" }
        val count = raw[KEY_COUNT]?.toIntOrNull() ?: error("missing item count")
        require(count in 0..MAX_ITEMS) { "invalid item count" }
        val items = (0 until count).map { index ->
            val appId = raw["item.$index.app_id"]?.takeIf { it.isNotBlank() } ?: error("missing app id")
            val status = UpdateBatchItemStatus.valueOf(raw["item.$index.status"] ?: error("missing status"))
            val error = raw["item.$index.error"]?.takeIf { it.isNotEmpty() }
            val targetVersionCode = raw["item.$index.target_version_code"]?.toLongOrNull()
                ?: error("missing target version code")
            UpdateBatchExecutionItem(appId, status, error, targetVersionCode)
        }
        val active = raw[KEY_ACTIVE]?.takeIf { it.isNotBlank() }
        UpdateBatchExecutionState(items, active).also(::validate)
    }.getOrNull()

    private fun validate(state: UpdateBatchExecutionState) {
        require(state.items.size <= MAX_ITEMS) { "too many update items" }
        val ids = state.items.map { item ->
            require(item.appId.isNotBlank()) { "blank app id" }
            require(item.targetVersionCode > 0L) { "invalid target version code: ${item.appId}" }
            item.appId
        }
        require(ids.distinct().size == ids.size) { "duplicate app id" }
        val running = state.items.filter { it.status == UpdateBatchItemStatus.RUNNING }
        require(running.size <= 1) { "multiple running updates" }
        if (state.activeAppId == null) {
            require(running.isEmpty()) { "running update without active app" }
        } else {
            require(running.size == 1 && running.single().appId == state.activeAppId) { "active app does not match running item" }
        }
    }

    private const val MAX_ITEMS = 500
}

class UpdateBatchStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UpdateBatchExecutionState? {
        val raw = prefs.all.mapValues { (_, value) -> value as? String }
        if (raw.isEmpty()) return null
        return UpdateBatchStateCodec.decode(raw)
    }

    fun save(state: UpdateBatchExecutionState): Boolean {
        val encoded = UpdateBatchStateCodec.encode(state)
        val editor = prefs.edit().clear()
        encoded.forEach { (key, value) -> editor.putString(key, value) }
        return editor.commit()
    }

    fun clear(): Boolean = prefs.edit().clear().commit()

    companion object {
        private const val PREFS_NAME = "storeamo_update_batch_state_v1"
    }
}
