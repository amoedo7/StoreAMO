package com.desarrollamo.storeamo.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact

/** Runtime facts used by the visible update flow. */
object UpdateEnvironment {
    fun currentNetwork(context: Context): NetworkKind {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkKind.OFFLINE
        val network = manager.activeNetwork ?: return NetworkKind.OFFLINE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkKind.OFFLINE
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        return classifyNetwork(hasInternet = hasInternet, isWifi = isWifi)
    }

    fun installedVersionCode(context: Context, packageName: String?): Long? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrNull()
    }

    fun classifyNetwork(hasInternet: Boolean, isWifi: Boolean): NetworkKind = when {
        !hasInternet -> NetworkKind.OFFLINE
        isWifi -> NetworkKind.WIFI
        else -> NetworkKind.METERED
    }
}

/**
 * Converts catalog artifacts into policy candidates without weakening validation.
 * Invalid metadata is represented as non-downloadable so UpdateBatchPolicy blocks it.
 */
object UpdateCatalogAdapter {
    data class CandidateBinding(
        val app: StoreApp,
        val artifact: StoreArtifact,
        val candidate: UpdateCandidate,
    )

    fun bind(app: StoreApp, artifact: StoreArtifact, installedVersionCode: Long): CandidateBinding {
        val targetVersionCode = artifact.versionCode.toLongOrNull() ?: Long.MIN_VALUE
        val downloadable = isDownloadable(artifact, targetVersionCode)
        return CandidateBinding(
            app = app,
            artifact = artifact,
            candidate = UpdateCandidate(
                appId = app.id,
                installedVersionCode = installedVersionCode,
                targetVersionCode = targetVersionCode,
                verified = artifact.verified,
                downloadable = downloadable,
            ),
        )
    }

    fun bindInstalled(context: Context, app: StoreApp, artifact: StoreArtifact): CandidateBinding? {
        val installed = UpdateEnvironment.installedVersionCode(context, artifact.applicationId) ?: return null
        return bind(app = app, artifact = artifact, installedVersionCode = installed)
    }

    private fun isDownloadable(artifact: StoreArtifact, targetVersionCode: Long): Boolean =
        targetVersionCode > 0L &&
            artifact.applicationId?.isNotBlank() == true &&
            artifact.url.startsWith("https://") &&
            artifact.sha256.matches(Regex("^[A-Fa-f0-9]{64}$")) &&
            (artifact.sizeBytes == null || artifact.sizeBytes > 0L)
}
