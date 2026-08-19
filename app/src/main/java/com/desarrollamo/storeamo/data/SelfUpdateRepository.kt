package com.desarrollamo.storeamo.data

import com.desarrollamo.storeamo.BuildConfig
import com.desarrollamo.storeamo.model.StoreArtifact
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object SelfUpdateRepository {
    private const val RELEASES_URL = "https://api.github.com/repos/amoedo7/StoreAMO/releases?per_page=10"
    private val tagRegex = Regex("^v?0\\.4\\.3\\.(\\d+)$")
    private val shaRegex = Regex("^[0-9a-fA-F]{64}$")

    fun fetchLatest(): StoreArtifact? {
        val releases = JSONArray(getText(RELEASES_URL, "application/vnd.github+json"))
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue
            val tag = release.optString("tag_name")
            val match = tagRegex.matchEntire(tag) ?: continue
            val runNumber = match.groupValues[1].toIntOrNull() ?: continue
            val versionCode = 430000 + runNumber
            if (versionCode <= BuildConfig.VERSION_CODE) return null

            val assets = release.optJSONArray("assets") ?: continue
            var apkIndex = -1
            var sumsIndex = -1
            for (j in 0 until assets.length()) {
                val name = assets.getJSONObject(j).optString("name")
                if (name.startsWith("StoreAMO-") && name.endsWith(".apk")) apkIndex = j
                if (name == "SHA256SUMS.txt") sumsIndex = j
            }
            if (apkIndex < 0) continue

            val apk = assets.getJSONObject(apkIndex)
            val apkName = apk.optString("name")
            val apkUrl = apk.optString("browser_download_url")
            var sha = apk.optString("digest").removePrefix("sha256:").lowercase()
            if (!shaRegex.matches(sha) && sumsIndex >= 0) {
                val sums = assets.getJSONObject(sumsIndex)
                val body = getText(sums.optString("browser_download_url"), "text/plain")
                sha = body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.endsWith(apkName) }
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.lowercase()
                    .orEmpty()
            }
            if (!shaRegex.matches(sha) || !apkUrl.startsWith("https://")) continue

            return StoreArtifact(
                platform = "android",
                arch = "universal",
                format = "apk",
                version = tag.removePrefix("v"),
                versionCode = versionCode.toString(),
                url = apkUrl,
                sha256 = sha,
                sizeBytes = apk.optLong("size").takeIf { it > 0 },
                verified = true,
                applicationId = BuildConfig.APPLICATION_ID,
                verificationReport = release.optString("html_url").ifBlank { null },
            )
        }
        return null
    }

    private fun getText(url: String, accept: String): String {
        require(url.startsWith("https://"))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "StoreAMO/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val status = connection.responseCode
            require(status in 200..299) { "HTTP $status" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
