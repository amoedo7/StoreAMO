package com.desarrollamo.storeamo.data

import com.desarrollamo.storeamo.model.StoreNewsItem
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NewsRepository {
    private const val NEWS_URL = "https://raw.githubusercontent.com/amoedo7/StoreAMO-Catalog/main/news.json"

    fun fetch(): List<StoreNewsItem> {
        val separator = if (NEWS_URL.contains('?')) '&' else '?'
        val url = "$NEWS_URL${separator}t=${System.currentTimeMillis()}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "StoreAMO-BuenasNuevas")
        }
        return try {
            val status = connection.responseCode
            require(status in 200..299) { "HTTP $status" }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            require(root.optString("schema") == "storeamo.news.v1") { "Esquema de noticias no compatible" }
            val array = root.optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val appId = item.optString("app_id")
                    val appName = item.optString("app_name")
                    val title = item.optString("title")
                    val publishedAt = item.optString("published_at")
                    if (id.isBlank() || appId.isBlank() || appName.isBlank() || title.isBlank() || publishedAt.isBlank()) continue
                    add(
                        StoreNewsItem(
                            id = id,
                            appId = appId,
                            appName = appName,
                            type = item.optString("type", "activity"),
                            title = title,
                            summary = item.optString("summary"),
                            publishedAt = publishedAt,
                            status = item.optString("status", "development"),
                            sourceVisibility = item.optString("source_visibility", "public"),
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
