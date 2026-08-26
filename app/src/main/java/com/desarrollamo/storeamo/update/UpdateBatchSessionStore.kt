package com.desarrollamo.storeamo.update

import android.content.Context

data class UpdateBatchEntry(
    val appId: String,
    val targetVersionCode: Long,
)

data class UpdateBatchSession(
    val pending: List<UpdateBatchEntry> = emptyList(),
    val inFlightAppId: String? = null,
) {
    val isDone: Boolean get() = pending.isEmpty() && inFlightAppId == null
}

object UpdateBatchSessionCodec {
    fun encode(session: UpdateBatchSession): Set<String> = buildSet {
        session.pending.forEachIndexed { index, entry ->
            add("P|$index|${entry.appId}|${entry.targetVersionCode}")
        }
        session.inFlightAppId?.let { add("I|$it") }
    }

    fun decode(raw: Set<String>?): UpdateBatchSession {
        if (raw.isNullOrEmpty()) return UpdateBatchSession()
        val pending = mutableListOf<Pair<Int, UpdateBatchEntry>>()
        var inFlight: String? = null
        for (row in raw) {
            val parts = row.split('|')
            when {
                parts.size == 4 && parts[0] == "P" -> {
                    val index = parts[1].toIntOrNull() ?: continue
                    val code = parts[3].toLongOrNull() ?: continue
                    val appId = parts[2].trim()
                    if (appId.isNotEmpty() && code > 0) pending += index to UpdateBatchEntry(appId, code)
                }
                parts.size == 2 && parts[0] == "I" && parts[1].isNotBlank() -> inFlight = parts[1]
            }
        }
        val ordered = pending.sortedBy { it.first }.map { it.second }.distinctBy { it.appId }
        if (inFlight != null && ordered.none { it.appId == inFlight }) inFlight = null
        return UpdateBatchSession(ordered, inFlight)
    }
}

class UpdateBatchSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): UpdateBatchSession = UpdateBatchSessionCodec.decode(prefs.getStringSet(KEY, null))

    fun save(session: UpdateBatchSession) {
        if (session.isDone) prefs.edit().remove(KEY).apply()
        else prefs.edit().putStringSet(KEY, UpdateBatchSessionCodec.encode(session)).apply()
    }

    fun begin(entries: List<UpdateBatchEntry>): UpdateBatchSession {
        val clean = entries.filter { it.appId.isNotBlank() && it.targetVersionCode > 0 }.distinctBy { it.appId }
        return UpdateBatchSession(pending = clean).also(::save)
    }

    fun markStarted(appId: String): UpdateBatchSession {
        val current = load()
        if (current.inFlightAppId != null || current.pending.firstOrNull()?.appId != appId) return current
        return current.copy(inFlightAppId = appId).also(::save)
    }

    fun markCompleted(appId: String): UpdateBatchSession {
        val current = load()
        if (current.inFlightAppId != appId) return current
        return UpdateBatchSession(
            pending = current.pending.filterNot { it.appId == appId },
            inFlightAppId = null,
        ).also(::save)
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val PREFS = "storeamo_update_batch_v1"
        private const val KEY = "session"
    }
}
