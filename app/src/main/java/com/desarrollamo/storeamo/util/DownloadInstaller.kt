package com.desarrollamo.storeamo.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.desarrollamo.storeamo.model.StoreArtifact
import java.io.File
import java.security.MessageDigest

object DownloadInstaller {
    data class Pending(val id: Long, val file: File, val artifact: StoreArtifact)

    fun start(context: Context, appName: String, artifact: StoreArtifact): Pending {
        require(artifact.platform == "android") { "Sólo los APK Android se instalan desde la app Android" }
        require(artifact.url.startsWith("https://")) { "URL no segura" }
        require(artifact.sha256.matches(Regex("^[0-9a-f]{64}$"))) { "SHA-256 inválido" }
        val safeVersion = artifact.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "${appName.replace(Regex("[^A-Za-z0-9._-]"), "_")}-$safeVersion.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(artifact.url))
            .setTitle("$appName ${artifact.version}")
            .setDescription("StoreAMO · descarga verificada antes de instalar")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(file))
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return Pending(dm.enqueue(request), file, artifact)
    }

    fun status(context: Context, id: Long): Int? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    fun verifySha256(file: File, expected: String): Boolean {
        if (!file.isFile) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    fun canInstallPackages(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else true

    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
