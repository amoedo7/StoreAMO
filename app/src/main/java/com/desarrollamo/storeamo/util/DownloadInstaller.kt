package com.desarrollamo.storeamo.util

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.desarrollamo.storeamo.InstallFlowActivity
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
        val pending = Pending(dm.enqueue(request), file, artifact)

        // Un toque significa un flujo completo. La pantalla de progreso se abre ahora,
        // verifica el APK y entrega automáticamente la instalación a Android.
        InstallFlowActivity.launch(context, appName, pending)
        return pending
    }

    fun status(context: Context, id: Long): Int? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    fun progressPercent(context: Context, id: Long): Int? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (downloaded < 0L || total <= 0L) return null
            return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
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

    /**
     * Instala mediante PackageInstaller.Session.
     *
     * Antes usábamos ACTION_VIEW con application/vnd.android.package-archive. En Android con
     * varios handlers registrados eso abre el selector "Abrir con" (Termux, instalador, etc.).
     * PackageInstaller entrega el APK directamente al instalador de paquetes del sistema y
     * conserva la confirmación de seguridad que Android requiera, sin hacer elegir una app.
     */
    fun install(context: Context, file: File) {
        val problem = preflightProblem(context, file)
        if (problem != null) {
            Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            return
        }

        val pm = context.packageManager
        val archive = archiveInfo(pm, file)
        if (archive == null) {
            Toast.makeText(context, "Instalación bloqueada: Android no reconoce este APK.", Toast.LENGTH_LONG).show()
            return
        }

        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(archive.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION_INSTALL_STATUS
                    putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                }
                val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusReceiver = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
                )
                session.commit(statusReceiver.intentSender)
            }
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    private fun preflightProblem(context: Context, file: File): String? {
        if (!file.isFile) return "Instalación bloqueada: el APK ya no existe."
        val pm = context.packageManager
        val archive = archiveInfo(pm, file)
            ?: return "Instalación bloqueada: Android no reconoce este APK."
        val packageName = archive.packageName
        if (packageName.isBlank()) return "Instalación bloqueada: el APK no declara un paquete válido."

        val flags = signingFlags()
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
            return "Actualización bloqueada: la firma no coincide con la app instalada. Esta instalación necesita una migración única."
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

    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? {
        val flags = signingFlags()
        @Suppress("DEPRECATION")
        return pm.getPackageArchiveInfo(file.absolutePath, flags)
    }

    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
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
