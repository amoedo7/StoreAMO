package com.desarrollamo.storeamo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Feedback comunitario de StoreAMO.
 *
 * Usa únicamente la clave publishable de Supabase, que está diseñada para
 * clientes públicos. Las tablas no son accesibles directamente: la app llama
 * RPCs acotados que validan los datos y no exponen device_id.
 */
object FeedbackRepository {
    private const val BASE_URL = "https://ydmnavyadpztydontaqh.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_vWWs5BbvZvAOB6AgoWsSuQ_z8OnjDPZ"
    private const val PREFS = "storeamo_feedback"
    private const val KEY_DEVICE_ID = "installation_id"

    data class RatingStats(
        val average: Double?,
        val ratingCount: Long,
        val feedbackCount: Long = 0,
    )

    data class PublicComment(
        val id: String,
        val kind: String,
        val body: String,
        val createdAt: String,
    )

    fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { existing ->
            runCatching { UUID.fromString(existing) }.getOrNull()?.let { return existing }
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun fetchOverview(): Map<String, RatingStats> {
        val body = rpc("storeamo_ratings_overview", JSONObject())
        val array = JSONArray(body.ifBlank { "[]" })
        return buildMap {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val appId = row.optString("app_id")
                if (appId.isBlank()) continue
                put(
                    appId,
                    RatingStats(
                        average = row.optDoubleOrNull("avg_rating"),
                        ratingCount = row.optLong("rating_count", 0L),
                    )
                )
            }
        }
    }

    fun fetchSummary(appId: String): RatingStats {
        val body = rpc("storeamo_feedback_summary", JSONObject().put("p_app_id", appId))
        val array = JSONArray(body.ifBlank { "[]" })
        if (array.length() == 0) return RatingStats(null, 0L, 0L)
        val row = array.getJSONObject(0)
        return RatingStats(
            average = row.optDoubleOrNull("avg_rating"),
            ratingCount = row.optLong("rating_count", 0L),
            feedbackCount = row.optLong("feedback_count", 0L),
        )
    }

    fun fetchPublicComments(appId: String, limit: Int = 3): List<PublicComment> {
        val body = rpc(
            "storeamo_public_comments",
            JSONObject().put("p_app_id", appId).put("p_limit", limit.coerceIn(0, 10)),
        )
        val array = JSONArray(body.ifBlank { "[]" })
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                add(
                    PublicComment(
                        id = row.optString("id"),
                        kind = row.optString("kind"),
                        body = row.optString("body"),
                        createdAt = row.optString("created_at"),
                    )
                )
            }
        }
    }

    fun submitRating(context: Context, appId: String, rating: Int) {
        require(rating in 1..5)
        rpc(
            "storeamo_submit_rating",
            JSONObject()
                .put("p_app_id", appId)
                .put("p_device_id", installationId(context))
                .put("p_rating", rating),
        )
    }

    fun submitFeedback(context: Context, appId: String, kind: String, body: String) {
        require(kind in setOf("idea", "mejora", "error"))
        val clean = body.trim()
        require(clean.length in 5..800)
        rpc(
            "storeamo_submit_feedback",
            JSONObject()
                .put("p_app_id", appId)
                .put("p_device_id", installationId(context))
                .put("p_kind", kind)
                .put("p_body", clean),
        )
    }

    private fun rpc(function: String, payload: JSONObject): String {
        val connection = (URL("$BASE_URL/rest/v1/rpc/$function").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            useCaches = false
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "StoreAMO/${com.desarrollamo.storeamo.BuildConfig.VERSION_NAME}")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Servidor de feedback respondió HTTP $code" })
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }
}
