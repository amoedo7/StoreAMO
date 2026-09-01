package com.desarrollamo.storeamo

import android.app.Activity
import android.app.DownloadManager
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
 * In-app verified installation flow for StoreAMO.
 *
 * StoreAMO downloads the selected APK itself, verifies its SHA-256 against the
 * catalog and only then hands the local verified file to Android's visible
 * package installer. StoreAMO never installs silently: Android keeps the final
 * user confirmation and the per-source install authorization.
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
    private lateinit var action: Button
    private lateinit var close: Button

    private var polling = false
    private var fallbackDownloading = false
    private var verifying = false
    private var artifactVerified = false
    private var awaitingInstallPermission = false
    private var permissionScreenOpened = false
    private var installerOpened = false
    private var persistentErrorVisible = false

    private val pollDownload = object : Runnable {
        override fun run() {
            if (!polling || isFinishing || persistentErrorVisible) return
            val percent = DownloadInstaller.progressPercent(this@InstallFlowActivity, downloadId)
            if (percent != null) {
                progress.isIndeterminate = false
                progress.progress = percent
                detail.text = "Descargando dentro de StoreAMO · $percent%"
            } else {
                progress.isIndeterminate = true
                detail.text = "StoreAMO está obteniendo el APK…"
            }

            when (DownloadInstaller.status(this@InstallFlowActivity, downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    polling = false
                    progress.isIndeterminate = false
                    progress.progress = 100
                    detail.text = "Descarga completa · verificando SHA-256"
                    verifyAndContinue()
                }
                DownloadManager.STATUS_FAILED -> {
                    polling = false
                    startDirectFallback(DownloadInstaller.downloadFailureReason(this@InstallFlowActivity, downloadId))
                }
                else -> handler.postDelayed(this, 350)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_STATIC_ERROR, false)) {
            appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "Aplicación" }
            targetVersion = intent.getStringExtra(EXTRA_VERSION).orEmpty()
            artifactUrl = ""
            expectedSha256 = ""
            applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID)?.ifBlank { null }
            buildUi()
            showStaticError(intent.getStringExtra(EXTRA_STATIC_ERROR_MESSAGE).orEmpty())
            return
        }

        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        expectedSha256 = intent.getStringExtra(EXTRA_SHA256).orEmpty()
        appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "Aplicación" }
        targetVersion = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID)?.ifBlank { null }
        artifactUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        artifactSizeBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, -1L).takeIf { it > 0L }

        if (
            downloadId < 0 || filePath.isBlank() ||
            !expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$")) ||
            !artifactUrl.startsWith("https://")
        ) {
            finish()
            return
        }

        apkFile = File(filePath)
        buildUi()
        startPolling()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_STATIC_ERROR, false)) {
            showStaticError(intent.getStringExtra(EXTRA_STATIC_ERROR_MESSAGE).orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        if (persistentErrorVisible) return

        if (targetInstalled()) {
            success()
            return
        }

        if (awaitingInstallPermission) {
            if (DownloadInstaller.canInstallPackages(this)) {
                awaitingInstallPermission = false
                permissionScreenOpened = false
                openVerifiedInstaller()
            } else if (permissionScreenOpened) {
                showInstallPermissionRequired(autoOpen = false)
            }
            return
        }

        if (installerOpened) {
            progress.visibility = View.GONE
            status.text = "Completá la instalación en Android"
            detail.text = "El APK ya fue descargado y verificado por StoreAMO. Android requiere tu confirmación final para instalarlo."
            action.apply {
                text = "Reintentar instalación"
                visibility = View.VISIBLE
                setOnClickListener { openVerifiedInstaller() }
            }
            close.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startPolling() {
        persistentErrorVisible = false
        artifactVerified = false
        awaitingInstallPermission = false
        permissionScreenOpened = false
        installerOpened = false
        status.text = "Descargando $appName"
        detail.text = "La descarga y la verificación ocurren dentro de StoreAMO. No hace falta abrir GitHub."
        action.visibility = View.GONE
        close.visibility = View.GONE
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        polling = true
        handler.post(pollDownload)
    }

    private fun startDirectFallback(originalReason: String) {
        if (fallbackDownloading || verifying || persistentErrorVisible) return
        fallbackDownloading = true
        status.text = "Descarga segura alternativa"
        detail.text = "$originalReason. Probando HTTPS directo dentro de StoreAMO."
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        action.visibility = View.GONE
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
                        if (isFinishing || persistentErrorVisible) return@runOnUiThread
                        if (percent == null) {
                            progress.isIndeterminate = true
                            detail.text = "Descarga HTTPS directa en curso…"
                        } else {
                            progress.isIndeterminate = false
                            progress.progress = percent
                            detail.text = "Descarga HTTPS · $percent%"
                        }
                    }
                }
            }
            runOnUiThread {
                fallbackDownloading = false
                result.onSuccess { verifyAndContinue() }
                    .onFailure { error ->
                        showStaticError("No pude descargar el APK dentro de StoreAMO: ${error.message.orEmpty()}")
                    }
            }
        }.start()
    }

    private fun verifyAndContinue() {
        if (verifying || fallbackDownloading || persistentErrorVisible) return
        verifying = true
        status.text = "Verificando integridad"
        detail.text = "Comparando SHA-256 con el catálogo oficial."
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true

        Thread {
            val valid = runCatching { DownloadInstaller.verifySha256(apkFile, expectedSha256) }.getOrDefault(false)
            runOnUiThread {
                verifying = false
                if (!valid) {
                    runCatching { apkFile.delete() }
                    showStaticError("StoreAMO bloqueó la descarga: el SHA-256 no coincide con el catálogo.")
                    return@runOnUiThread
                }

                artifactVerified = true
                progress.isIndeterminate = false
                progress.progress = 100
                status.text = "APK verificado"
                detail.text = "SHA-256 correcto. Abriendo el instalador de Android sin salir a GitHub."
                continueToInstaller()
            }
        }.start()
    }

    private fun continueToInstaller() {
        if (!artifactVerified || persistentErrorVisible) return
        if (!DownloadInstaller.canInstallPackages(this)) {
            showInstallPermissionRequired(autoOpen = true)
            return
        }
        openVerifiedInstaller()
    }

    private fun showInstallPermissionRequired(autoOpen: Boolean) {
        awaitingInstallPermission = true
        progress.visibility = View.GONE
        status.text = "Autorizar instalaciones"
        detail.text = "Android necesita que autorices a StoreAMO como fuente de instalación. Es un permiso del sistema que se concede una sola vez y no elimina la confirmación final de cada APK."
        action.apply {
            text = "Autorizar en Android"
            visibility = View.VISIBLE
            setOnClickListener { openInstallPermission() }
        }
        close.visibility = View.VISIBLE
        if (autoOpen && !permissionScreenOpened) openInstallPermission()
    }

    private fun openInstallPermission() {
        runCatching {
            DownloadInstaller.openInstallPermission(this)
            permissionScreenOpened = true
        }.onFailure { error ->
            showStaticError("Android no pudo abrir el permiso de instalación: ${error.message.orEmpty()}")
        }
    }

    private fun openVerifiedInstaller() {
        if (!artifactVerified || !::apkFile.isInitialized) return
        persistentErrorVisible = false
        awaitingInstallPermission = false
        permissionScreenOpened = false
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 100
        status.text = "Listo para instalar"
        detail.text = "StoreAMO ya verificó el APK. Android mostrará ahora su confirmación de instalación."
        action.visibility = View.GONE
        close.visibility = View.GONE

        runCatching {
            DownloadInstaller.openSystemInstaller(this, apkFile)
            installerOpened = true
        }.onFailure { error ->
            installerOpened = false
            showStaticError("No pude abrir el instalador de Android: ${error.message.orEmpty()}")
        }
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
        persistentErrorVisible = false
        awaitingInstallPermission = false
        installerOpened = false
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 100
        status.text = "$appName está al día"
        detail.text = "Versión $targetVersion instalada correctamente."
        action.visibility = View.GONE
        close.visibility = View.VISIBLE
    }

    private fun showStaticError(message: String) {
        polling = false
        fallbackDownloading = false
        verifying = false
        awaitingInstallPermission = false
        permissionScreenOpened = false
        persistentErrorVisible = true
        handler.removeCallbacksAndMessages(null)

        progress.visibility = View.GONE
        status.text = "No pude completar la instalación"
        detail.text = message.ifBlank { "Android no informó un detalle adicional." }
        action.apply {
            text = if (artifactVerified) "Reintentar instalación" else "Reintentar descarga"
            visibility = if (::apkFile.isInitialized && artifactUrl.startsWith("https://")) View.VISIBLE else View.GONE
            setOnClickListener {
                persistentErrorVisible = false
                if (artifactVerified && apkFile.isFile) {
                    continueToInstaller()
                } else {
                    startDirectFallback("Reintentando descarga")
                }
            }
        }
        close.visibility = View.VISIBLE
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
            text = "STOREAMO · INSTALACIÓN SEGURA"
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
        action = Button(this).apply {
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
        root.addView(action, matchWrap())
        root.addView(space(dp(8)))
        root.addView(close, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

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
        private const val EXTRA_STATIC_ERROR = "static_install_error"
        private const val EXTRA_STATIC_ERROR_MESSAGE = "static_install_error_message"
        private const val PREFS_INSTALL_ERRORS = "storeamo_install_errors"
        private const val PREF_LAST_INSTALL_ERROR = "last_install_error"

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

        fun showPersistentInstallError(
            context: Context,
            appLabel: String,
            version: String,
            applicationId: String,
            message: String,
        ) {
            context.getSharedPreferences(PREFS_INSTALL_ERRORS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_INSTALL_ERROR, message)
                .apply()

            val intent = Intent(context, InstallFlowActivity::class.java).apply {
                putExtra(EXTRA_STATIC_ERROR, true)
                putExtra(EXTRA_STATIC_ERROR_MESSAGE, message)
                putExtra(EXTRA_APP_NAME, appLabel.ifBlank { applicationId.ifBlank { "Aplicación" } })
                putExtra(EXTRA_VERSION, version)
                putExtra(EXTRA_APPLICATION_ID, applicationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
