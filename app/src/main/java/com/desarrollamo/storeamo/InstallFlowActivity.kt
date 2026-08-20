package com.desarrollamo.storeamo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.desarrollamo.storeamo.model.StoreArtifact
import com.desarrollamo.storeamo.util.DownloadInstaller
import java.io.File

/**
 * Flujo visible y tolerante a fallos de instalación de StoreAMO.
 *
 * OBTENER/ACTUALIZAR: DownloadManager -> fallback HTTPS directo -> SHA-256 ->
 * permiso de Android -> instalador visible del sistema -> PackageInstaller fallback.
 */
class InstallFlowActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private var downloadId = -1L
    private lateinit var apkFile: File
    private lateinit var expectedSha256: String
    private lateinit var appName: String
    private lateinit var targetVersion: String
    private lateinit var artifactUrl: String
    private var artifactSizeBytes: Long? = null
    private var applicationId: String? = null

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var retry: Button
    private lateinit var close: Button

    private var polling = false
    private var fallbackDownloading = false
    private var verifying = false
    private var awaitingPermission = false
    private var installing = false
    private var installStartedAt = 0L

    private val pollDownload = object : Runnable {
        override fun run() {
            if (!polling || isFinishing) return
            val percent = DownloadInstaller.progressPercent(this@InstallFlowActivity, downloadId)
            if (percent != null) {
                progress.isIndeterminate = false
                progress.progress = percent
                detail.text = "Descargando · $percent%"
            } else {
                progress.isIndeterminate = true
                detail.text = "Descargando con Android…"
            }

            when (DownloadInstaller.status(this@InstallFlowActivity, downloadId)) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                    polling = false
                    progress.isIndeterminate = false
                    progress.progress = 100
                    detail.text = "Descarga completa · verificando integridad"
                    verifyAndContinue()
                }

                android.app.DownloadManager.STATUS_FAILED -> {
                    polling = false
                    val reason = DownloadInstaller.downloadFailureReason(this@InstallFlowActivity, downloadId)
                    startDirectFallback(reason)
                }

                else -> handler.postDelayed(this, 350)
            }
        }
    }

    private val checkInstalled = object : Runnable {
        override fun run() {
            if (!installing || isFinishing) return
            if (targetInstalled()) {
                installing = false
                success()
                return
            }
            if (System.currentTimeMillis() - installStartedAt > 75_000L) {
                installing = false
                fail(
                    "Android no confirmó la instalación. Si la cancelaste o el instalador mostró un error, " +
                        "tocá Reintentar; StoreAMO volverá a abrir el instalador del sistema."
                )
                return
            }
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        expectedSha256 = intent.getStringExtra(EXTRA_SHA256).orEmpty()
        appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "Aplicación" }
        targetVersion = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID)?.ifBlank { null }
        artifactUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        artifactSizeBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, -1L).takeIf { it > 0L }

        if (
            downloadId < 0 || filePath.isBlank() || !expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$")) ||
            !artifactUrl.startsWith("https://")
        ) {
            finish()
            return
        }
        apkFile = File(filePath)
        buildUi()
        startPolling()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingPermission) {
            if (DownloadInstaller.canInstallPackages(this)) {
                awaitingPermission = false
                startInstall()
            } else {
                status.text = "Falta un permiso de Android"
                detail.text = "Permití instalar apps desde StoreAMO para continuar. La descarga ya está verificada."
                retry.visibility = View.VISIBLE
                retry.text = "Abrir permiso de instalación"
                retry.setOnClickListener { DownloadInstaller.openInstallPermission(this) }
            }
        } else if (installing && targetInstalled()) {
            installing = false
            success()
        }
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startPolling() {
        status.text = "Obteniendo $appName"
        detail.text = "Preparando descarga…"
        retry.visibility = View.GONE
        close.visibility = View.GONE
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        polling = true
        handler.post(pollDownload)
    }

    private fun startDirectFallback(originalReason: String) {
        if (fallbackDownloading || verifying || isFinishing) return
        fallbackDownloading = true
        status.text = "Probando descarga alternativa"
        detail.text = "$originalReason. StoreAMO cambia automáticamente a HTTPS directo."
        progress.isIndeterminate = true
        retry.visibility = View.GONE
        close.visibility = View.GONE

        val artifact = StoreArtifact(
            platform = "android",
            arch = null,
            format = "apk",
            version = targetVersion,
            versionCode = "",
            url = artifactUrl,
            sha256 = expectedSha256,
            sizeBytes = artifactSizeBytes,
            verified = false,
            applicationId = applicationId,
            verificationReport = null,
        )

        Thread {
            val result = runCatching {
                DownloadInstaller.directDownload(artifact, apkFile) { percent ->
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        if (percent == null) {
                            progress.isIndeterminate = true
                            detail.text = "Descarga HTTPS directa en curso…"
                        } else {
                            progress.isIndeterminate = false
                            progress.progress = percent
                            detail.text = "Descarga HTTPS directa · $percent%"
                        }
                    }
                }
            }
            runOnUiThread {
                fallbackDownloading = false
                result.onSuccess {
                    progress.isIndeterminate = false
                    progress.progress = 100
                    detail.text = "Descarga alternativa completa · verificando SHA-256"
                    verifyAndContinue()
                }.onFailure { error ->
                    fail("Fallaron ambos métodos de descarga. ${error.message.orEmpty()}")
                }
            }
        }.start()
    }

    private fun verifyAndContinue() {
        if (verifying || fallbackDownloading) return
        verifying = true
        status.text = "Verificando integridad"
        detail.text = "Comparando el APK con el SHA-256 publicado por StoreAMO-Catalog."
        progress.isIndeterminate = true

        Thread {
            val valid = runCatching { DownloadInstaller.verifySha256(apkFile, expectedSha256) }.getOrDefault(false)
            runOnUiThread {
                verifying = false
                if (!valid) {
                    runCatching { apkFile.delete() }
                    fail("StoreAMO bloqueó la instalación: el SHA-256 no coincide con el catálogo.")
                    return@runOnUiThread
                }
                status.text = "Descarga verificada"
                detail.text = "SHA-256 correcto · preparando el instalador de Android."
                if (DownloadInstaller.canInstallPackages(this)) {
                    startInstall()
                } else {
                    awaitingPermission = true
                    status.text = "Autorización de Android"
                    detail.text = "Permití instalar apps desde StoreAMO. La descarga ya está verificada y no se repetirá."
                    DownloadInstaller.openInstallPermission(this)
                }
            }
        }.start()
    }

    private fun startInstall() {
        if (installing || isFinishing) return
        if (targetInstalled()) {
            success()
            return
        }

        val preflight = DownloadInstaller.preflightProblem(this, apkFile)
        if (preflight != null) {
            fail(preflight)
            return
        }

        status.text = "Abriendo instalador de Android"
        detail.text = "APK verificado · Android puede pedir una confirmación de seguridad."
        progress.isIndeterminate = true
        retry.visibility = View.GONE
        close.visibility = View.GONE

        val route = runCatching {
            DownloadInstaller.openSystemInstaller(this, apkFile)
        }.recoverCatching { primaryError ->
            status.text = "Usando instalador alternativo"
            detail.text = "El instalador visible no abrió (${primaryError.message.orEmpty()}). Probando PackageInstaller…"
            DownloadInstaller.installWithSession(this, apkFile)
            "PackageInstaller del sistema"
        }

        route.onFailure { error ->
            installing = false
            fail("No pude abrir ningún instalador de Android: ${error.message.orEmpty()}")
            return
        }

        installing = true
        installStartedAt = System.currentTimeMillis()
        detail.text = "${route.getOrNull().orEmpty()} abierto · confirmá la instalación cuando Android lo pida."
        handler.post(checkInstalled)
    }

    private fun targetInstalled(): Boolean {
        val packageName = applicationId ?: return false
        val installed = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
        return installed == targetVersion
    }

    private fun success() {
        progress.isIndeterminate = false
        progress.progress = 100
        status.text = "$appName está al día"
        detail.text = "Versión $targetVersion instalada correctamente."
        retry.visibility = View.GONE
        close.visibility = View.GONE
        handler.postDelayed({ if (!isFinishing) finish() }, 900)
    }

    private fun fail(message: String) {
        polling = false
        fallbackDownloading = false
        progress.isIndeterminate = false
        status.text = "No se pudo completar"
        detail.text = message
        retry.text = "Reintentar"
        retry.visibility = View.VISIBLE
        close.visibility = View.VISIBLE
        retry.setOnClickListener {
            if (apkFile.isFile && DownloadInstaller.verifySha256(apkFile, expectedSha256)) {
                startInstall()
            } else {
                startDirectFallback("Reintentando sin depender de DownloadManager")
            }
        }
    }

    private fun buildUi() {
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val eyebrow = TextView(this).apply {
            text = "STOREAMO · INSTALACIÓN"
            setTextColor(COLOR_CYAN)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        }
        val title = TextView(this).apply {
            text = appName
            setTextColor(Color.WHITE)
            textSize = 31f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(18))
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        detail = TextView(this).apply {
            setTextColor(COLOR_MUTED)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(18))
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(COLOR_CYAN)
            progressBackgroundTintList = ColorStateList.valueOf(COLOR_SURFACE)
        }
        retry = Button(this).apply {
            text = "Reintentar"
            visibility = View.GONE
            backgroundTintList = ColorStateList.valueOf(COLOR_CYAN)
            setTextColor(COLOR_BACKGROUND)
        }
        close = Button(this).apply {
            text = "Volver a StoreAMO"
            visibility = View.GONE
            backgroundTintList = ColorStateList.valueOf(COLOR_SURFACE)
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }

        root.addView(eyebrow, matchWrap())
        root.addView(title, matchWrap())
        root.addView(status, matchWrap())
        root.addView(detail, matchWrap())
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)))
        root.addView(space(dp(22)))
        root.addView(retry, matchWrap())
        root.addView(space(dp(8)))
        root.addView(close, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_APP_NAME = "app_name"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_APPLICATION_ID = "application_id"
        private const val EXTRA_URL = "url"
        private const val EXTRA_SIZE_BYTES = "size_bytes"

        private val COLOR_BACKGROUND = Color.rgb(6, 16, 28)
        private val COLOR_SURFACE = Color.rgb(18, 45, 67)
        private val COLOR_CYAN = Color.rgb(103, 210, 255)
        private val COLOR_MUTED = Color.rgb(159, 180, 199)

        fun launch(context: Context, appName: String, pending: DownloadInstaller.Pending) {
            val intent = Intent(context, InstallFlowActivity::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, pending.id)
                putExtra(EXTRA_FILE_PATH, pending.file.absolutePath)
                putExtra(EXTRA_SHA256, pending.artifact.sha256)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_VERSION, pending.artifact.version)
                putExtra(EXTRA_APPLICATION_ID, pending.artifact.applicationId.orEmpty())
                putExtra(EXTRA_URL, pending.artifact.url)
                putExtra(EXTRA_SIZE_BYTES, pending.artifact.sizeBytes ?: -1L)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
