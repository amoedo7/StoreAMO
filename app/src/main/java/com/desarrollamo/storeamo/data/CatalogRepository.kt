package com.desarrollamo.storeamo.data

import android.content.Context
import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact
import com.desarrollamo.storeamo.model.StoreCatalog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CatalogRepository {
    const val CATALOG_URL = "https://raw.githubusercontent.com/amoedo7/StoreAMO-Catalog/main/catalog.json"
    private const val PREFS = "storeamo_catalog_cache"
    private const val CACHE_KEY = "last_known_good_catalog"

    fun fetch(): StoreCatalog = parse(fetchRemote())

    fun fetch(context: Context): StoreCatalog {
        return runCatching {
            val raw = fetchRemote()
            val parsed = parse(raw)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHE_KEY, raw)
                .apply()
            parsed
        }.getOrElse { remoteError ->
            val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CACHE_KEY, null)
                ?: throw remoteError
            parse(cached)
        }
    }

    private fun fetchRemote(): String {
        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "StoreAMO/0.4")
        connection.useCaches = false
        try {
            val status = connection.responseCode
            require(status in 200..299) { "catalog HTTP $status" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun parse(raw: String): StoreCatalog {
        val root = JSONObject(raw)
        require(root.optString("schema") == "storeamo.catalog.v1") { "schema inválido" }
        val appsJson = root.getJSONArray("apps")
        val apps = buildList {
            for (i in 0 until appsJson.length()) {
                val o = appsJson.getJSONObject(i)
                val platforms = o.optJSONArray("supported_platforms")
                val supported = buildList {
                    if (platforms != null) for (j in 0 until platforms.length()) add(platforms.getString(j))
                }
                val artifactsJson = o.optJSONArray("artifacts")
                val artifacts = buildList {
                    if (artifactsJson != null) for (j in 0 until artifactsJson.length()) {
                        val a = artifactsJson.getJSONObject(j)
                        val url = a.optString("url")
                        val sha = a.optString("sha256")
                        if (!url.startsWith("https://")) continue
                        if (sha.isNotBlank() && !Regex("^[0-9a-fA-F]{64}$").matches(sha)) continue
                        add(
                            StoreArtifact(
                                platform = a.optString("platform"),
                                arch = a.optString("arch").takeIf { it.isNotBlank() },
                                format = a.optString("format").takeIf { it.isNotBlank() },
                                version = a.optString("version"),
                                versionCode = a.opt("version_code")?.toString().orEmpty(),
                                url = url,
                                sha256 = sha.lowercase(),
                                sizeBytes = if (a.has("size_bytes") && !a.isNull("size_bytes")) a.optLong("size_bytes") else null,
                                verified = a.optBoolean("verified", false),
                                applicationId = a.optString("application_id").takeIf { it.isNotBlank() },
                                verificationReport = a.optString("verification_report").takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
                add(
                    StoreApp(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        tagline = o.optString("tagline"),
                        description = o.optString("description"),
                        category = o.optString("category", "Apps"),
                        featured = o.optBoolean("featured", false),
                        audience = o.optString("audience", "public"),
                        status = o.optString("status", "development"),
                        supportedPlatforms = supported,
                        repository = o.optString("repository").takeIf { it.startsWith("https://") },
                        artifacts = artifacts,
                    )
                )
            }
        }
        return StoreCatalog(root.getString("schema"), root.optInt("catalog_version", 1), apps)
    }
}
