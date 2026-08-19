package com.desarrollamo.storeamo.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
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

        val safeAppName = appName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeVersion = artifact.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "$safeAppName-$safeVersion.apk"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("No hay almacenamiento de descargas disponible")
        dir.mkdirs()

        // Evita que un APK antiguo quede disponible como si fuera la descarga actual.
        dir.listFiles()?.filter { it.name.startsWith("$safeAppName-") && it.extension.equals("apk", true) }
            ?.forEach { it.delete() }

        val file = File(dir, fileName)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else true

    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, file: File) {
        val problem = preflightProblem(context, file)
        if (problem != null) {
            Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    private fun preflightProblem(context: Context, file: File): String? {
        if (!file.isFile) return "Instalación bloqueada: el APK ya no existe."
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "Instalación bloqueada: Android no reconoce este APK."
        val packageName = archive.packageName
        if (packageName.isBlank()) return "Instalación bloqueada: el APK no declara un paquete válido."

        val installed = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }.getOrNull() ?: return null // primera instalación: el SHA ya fue verificado antes.

        val archiveSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            return "Instalación bloqueada: no pudimos comprobar la firma del APK."
        }
        if (archiveSigners != installedSigners) {
            return "Actualización bloqueada: la firma no coincide con la app instalada. No desinstales; esta build necesita revisión."
        }

        val incomingCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else {
            @Suppress("DEPRECATION") installed.versionCode.toLong()
        }
        if (incomingCode < installedCode) {
            return "Actualización bloqueada: el APK es más antiguo que la versión instalada."
        }
        if (incomingCode == installedCode) {
            return "Esta misma versión ya está instalada."
        }
        return null
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return emptySet()
        }
        return signatures.map { sig ->
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
