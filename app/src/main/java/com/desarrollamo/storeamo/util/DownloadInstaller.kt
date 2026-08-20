package com.desarrollamo.storeamo.util

import android.app.Activity
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.desarrollamo.storeamo.InstallFlowActivity
import com.desarrollamo.storeamo.model.StoreArtifact
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object DownloadInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val MAX_REDIRECTS = 6

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

    fun downloadFailureReason(context: Context, id: Long): String {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val reason = runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return@use null
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            }
        }.getOrNull()

        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Android no pudo reanudar la descarga"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Android no encontró el almacenamiento de destino"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Ya existe un archivo incompatible en el destino"
            DownloadManager.ERROR_FILE_ERROR -> "Android tuvo un error al escribir el APK"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "La conexión HTTP se interrumpió"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "No hay espacio suficiente para descargar"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "La descarga tuvo demasiadas redirecciones"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "El servidor devolvió un código HTTP no manejado"
            DownloadManager.ERROR_UNKNOWN -> "DownloadManager devolvió un error desconocido"
            null -> "DownloadManager no informó el motivo"
            in 400..599 -> "El servidor respondió HTTP $reason"
            else -> "DownloadManager informó el código $reason"
        }
    }

    fun directDownload(artifact: StoreArtifact, file: File, onProgress: (Int?) -> Unit) {
        require(artifact.url.startsWith("https://")) { "URL no segura" }
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, "${file.name}.part")
        part.delete()
        file.delete()

        var current = URL(artifact.url)
        var completed = false
        try {
            for (hop in 0 until MAX_REDIRECTS) {
                require(current.protocol.equals("https", ignoreCase = true)) { "La descarga intentó salir de HTTPS" }
                val connection = (current.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", APK_MIME)
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "StoreAMO/0.4.3.68")
                }
                try {
                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location")
                            ?: error("Redirección sin destino")
                        val next = URL(current, location)
                        require(next.protocol.equals("https", ignoreCase = true)) { "Redirección insegura bloqueada" }
                        current = next
                        continue
                    }
                    require(code in 200..299) { "Descarga directa HTTP $code" }

                    val total = connection.contentLengthLong.takeIf { it > 0L }
                    var written = 0L
                    connection.inputStream.buffered().use { input ->
                        part.outputStream().buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count <= 0) break
                                output.write(buffer, 0, count)
                                written += count
                                val percent = total?.let { ((written * 100L) / it).toInt().coerceIn(0, 100) }
                                onProgress(percent)
                            }
                        }
                    }
                    require(part.isFile && part.length() > 0L) { "La descarga directa quedó vacía" }
                    artifact.sizeBytes?.takeIf { it > 0L }?.let { expected ->
                        require(part.length() == expected) {
                            "Tamaño inesperado: ${part.length()} bytes; se esperaban $expected"
                        }
                    }
                    if (!part.renameTo(file)) {
                        part.copyTo(file, overwrite = true)
                        part.delete()
                    }
                    require(file.isFile && file.length() > 0L) { "No pude materializar el APK descargado" }
                    onProgress(100)
                    completed = true
                    return
                } finally {
                    connection.disconnect()
                }
            }
            error("La descarga directa superó $MAX_REDIRECTS redirecciones")
        } finally {
            if (!completed) part.delete()
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

    fun openSystemInstaller(context: Context, file: File): String {
        preflightProblem(context, file)?.let { throw IllegalStateException(it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        fun prepare(intent: Intent): Intent = intent.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("StoreAMO APK", uri)
        }

        val primary = prepare(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri))
        try {
            context.startActivity(primary)
            return "Instalador del sistema"
        } catch (_: ActivityNotFoundException) {
            // Algunos fabricantes no exponen ACTION_INSTALL_PACKAGE.
        }

        val viewIntent = prepare(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME))
        val handlers = context.packageManager.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val preferred = handlers.firstOrNull { resolve ->
            val info = resolve.activityInfo?.applicationInfo ?: return@firstOrNull false
            val system = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val pkg = info.packageName.lowercase(Locale.ROOT)
            system && (pkg.contains("packageinstaller") || pkg.contains("installer") || pkg.contains("permissioncontroller"))
        } ?: handlers.firstOrNull { resolve ->
            val info = resolve.activityInfo?.applicationInfo ?: return@firstOrNull false
            (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        }
        if (preferred != null) viewIntent.setPackage(preferred.activityInfo.packageName)

        context.startActivity(viewIntent)
        return if (preferred != null) "Instalador del sistema (compatibilidad)" else "Instalador Android (compatibilidad)"
    }

    fun installWithSession(context: Context, file: File): Int {
        preflightProblem(context, file)?.let { throw IllegalStateException(it) }
        val pm = context.packageManager
        val archive = archiveInfo(pm, file)
            ?: throw IllegalStateException("Instalación bloqueada: Android no reconoce este APK.")

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
            return sessionId
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    /** Compatibilidad con pantallas legacy: nunca traga errores de preflight. */
    fun install(context: Context, file: File) {
        runCatching { openSystemInstaller(context, file) }
            .getOrElse { installWithSession(context, file) }
    }

    fun preflightProblem(context: Context, file: File): String? {
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
        }.getOrNull() ?: return null

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
