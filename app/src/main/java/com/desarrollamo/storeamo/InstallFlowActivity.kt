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
 * Los errores permanecen visibles hasta una acción del usuario. Si el APK ya
 * fue verificado por StoreAMO pero Android detecta una firma distinta en una
 * versión anterior del mismo paquete, StoreAMO ofrece una migración guiada:
 * abre el desinstalador oficial y continúa automáticamente al volver.
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
    private var awaitingSignatureMigration = false
    private var signatureMigrationSettingsFallbackOpened = false
    private var installing = false
    private var usingSessionInstaller = false
    private var installStartedAt = 0L
    private var persistentErrorVisible = false

    private val pollDownload = object : Runnable {
        override fun run() {
            if (!polling || isFinishing || persistentErrorVisible) return
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
            if (!installing || isFinishing || persistentErrorVisible) return
            if (targetInstalled()) {
                installing = false
                success()
                return
            }
            if (!usingSessionInstaller && System.currentTimeMillis() - installStartedAt > 75_000L) {
                installing = false
                showStaticInstallError(
                    "Android no confirmó la instalación dentro del tiempo esperado.\n\n" +
                        diagnosticContext("TIMEOUT")
                )
                return
            }
            handler.postDelayed(this, 700)
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
            consumePersistedInstallError()
            showStaticInstallError(intent.getStringExtra(EXTRA_STATIC_ERROR_MESSAGE).orEmpty())
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
            consumePersistedInstallError()
            showStaticInstallError(intent.getStringExtra(EXTRA_STATIC_ERROR_MESSAGE).orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()

        consumePersistedInstallError()?.let { persisted ->
            showStaticInstallError(persisted)
            return
        }

        if (awaitingSignatureMigration) {
            val packageName = applicationId
            if (packageName != null && !DownloadInstaller.isPackageInstalled(this, packageName)) {
                awaitingSignatureMigration = false
                signatureMigrationSettingsFallbackOpened = false
                persistentErrorVisible = false
                status.text = "Versión anterior eliminada"
                detail.text = "Continuando automáticamente con $appName $targetVersion."
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                startInstall()
            } else if (packageName != null && !signatureMigrationSettingsFallbackOpened) {
                signatureMigrationSettingsFallbackOpened = true
                persistentErrorVisible = false
                status.text = "Abrí Desinstalar en Android"
                detail.text = "El desinstalador directo no completó la eliminación. StoreAMO abre ahora Info. de la aplicación para que tengas el botón Desinstalar delante, sin buscarla en Ajustes. Al volver, continuará automáticamente."
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                runCatching { DownloadInstaller.openApplicationDetails(this, packageName) }
                    .onFailure {
                        awaitingSignatureMigration = false
                        signatureMigrationSettingsFallbackOpened = false
                        showSignatureMigration(cancelled = true)
                    }
            } else {
                awaitingSignatureMigration = false
                signatureMigrationSettingsFallbackOpened = false
                showSignatureMigration(cancelled = true)
            }
            return
        }

        if (persistentErrorVisible) return

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
        } else if (installing) {
            // No inferimos fracaso por un onResume: varios instaladores OEM vuelven a
            // enfocar StoreAMO mientras la confirmación del sistema todavía está viva.
            status.text = "Esperando a Android"
            detail.text = "La instalación sigue en manos de Android. Confirmá Actualizar/Instalar cuando aparezca el diálogo."
        }
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startPolling() {
        persistentErrorVisible = false
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
        if (fallbackDownloading || verifying || isFinishing || persistentErrorVisible) return
        fallbackDownloading = true
        status.text = "Probando descarga alternativa"
        detail.text = "$originalReason. StoreAMO cambia automáticamente a HTTPS directo."
        progress.visibility = View.VISIBLE
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
                        if (isFinishing || persistentErrorVisible) return@runOnUiThread
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
                    if (persistentErrorVisible) return@onSuccess
                    progress.isIndeterminate = false
                    progress.progress = 100
                    detail.text = "Descarga alternativa completa · verificando SHA-256"
                    verifyAndContinue()
                }.onFailure { error ->
                    showStaticInstallError(
                        "Fallaron ambos métodos de descarga. ${error.message.orEmpty()}\n\n" +
                            diagnosticContext("DOWNLOAD_FAILED")
                    )
                }
            }
        }.start()
    }

    private fun verifyAndContinue() {
        if (verifying || fallbackDownloading || persistentErrorVisible) return
        verifying = true
        status.text = "Verificando integridad"
        detail.text = "Comparando el APK con el SHA-256 publicado por StoreAMO-Catalog."
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true

        Thread {
            val valid = runCatching { DownloadInstaller.verifySha256(apkFile, expectedSha256) }.getOrDefault(false)
            runOnUiThread {
                verifying = false
                if (persistentErrorVisible) return@runOnUiThread
                if (!valid) {
                    runCatching { apkFile.delete() }
                    showStaticInstallError(
                        "StoreAMO bloqueó la instalación: el SHA-256 no coincide con el catálogo.\n\n" +
                            diagnosticContext("SHA256_MISMATCH")
                    )
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
        if (installing || isFinishing || persistentErrorVisible) return
        if (targetInstalled()) {
            success()
            return
        }

        val migration = DownloadInstaller.knownOneTimeSignatureMigration(this, apkFile)
        if (migration != null) {
            showSignatureMigration(cancelled = false)
            return
        }

        val preflight = DownloadInstaller.preflightProblem(this, apkFile)
        if (preflight != null) {
            if (preflight.startsWith("Actualización bloqueada: la firma no coincide")) {
                showSignatureMigration(cancelled = false)
            } else {
                showStaticInstallError("$preflight\n\n${diagnosticContext("PREFLIGHT_BLOCKED")}")
            }
            return
        }

        status.text = "Abriendo instalador de Android"
        detail.text = "APK verificado · Android puede pedir una confirmación de seguridad."
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        retry.visibility = View.GONE
        close.visibility = View.GONE
        usingSessionInstaller = true

        val route = runCatching {
            status.text = "Esperando confirmación de Android"
            detail.text = "APK verificado · preparando una sesión oficial de instalación."
            DownloadInstaller.installWithSession(this, apkFile)
            "PackageInstaller del sistema"
        }.recoverCatching { sessionError ->
            usingSessionInstaller = false
            status.text = "Abriendo instalador compatible"
            detail.text = "La sesión oficial no pudo iniciarse (${sessionError.message.orEmpty()}). Abriendo el instalador visible de Android…"
            DownloadInstaller.openSystemInstaller(this, apkFile)
        }

        route.onFailure { error ->
            installing = false
            showStaticInstallError(
                "No pude abrir ningún instalador de Android: ${error.message.orEmpty()}\n\n" +
                    diagnosticContext("NO_INSTALLER_AVAILABLE")
            )
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
        persistentErrorVisible = false
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 100
        status.text = "$appName está al día"
        detail.text = "Versión $targetVersion instalada correctamente."
        retry.visibility = View.GONE
        close.visibility = View.GONE
        handler.postDelayed({ if (!isFinishing) finish() }, 900)
    }

    private fun showSignatureMigration(cancelled: Boolean) {
        polling = false
        fallbackDownloading = false
        verifying = false
        awaitingPermission = false
        installing = false
        persistentErrorVisible = true
        handler.removeCallbacks(pollDownload)
        handler.removeCallbacks(checkInstalled)

        progress.isIndeterminate = false
        progress.visibility = View.GONE
        status.text = if (cancelled) "ELIMINACIÓN PENDIENTE" else "VERSIÓN ANTERIOR INCOMPATIBLE"
        detail.text = buildString {
            if (cancelled) append("La eliminación de la versión anterior no se completó.\n\n")
            append("StoreAMO verificó el APK nuevo, pero Android detectó que la versión instalada de $appName usa una firma distinta. ")
            append("Android no permite actualizar una app cuando cambia su firma.\n\n")
            append("Podés resolverlo desde este botón: StoreAMO abrirá el desinstalador oficial de Android. Si el fabricante no completa ese flujo, abrirá directamente Info. de la aplicación, donde Android muestra Desinstalar. ")
            append("Cuando confirmes la eliminación y vuelvas, StoreAMO continuará automáticamente con $appName $targetVersion.\n\n")
            append("Este paso sólo hace falta al cambiar de una línea de firma antigua a la actual. Después, las versiones siguientes se actualizan normalmente sin desinstalar.\n\n")
            append("Importante: Android puede borrar los datos privados de la versión anterior al desinstalarla.\n\n")
            append(diagnosticContext("SIGNATURE_MIGRATION_REQUIRED"))
        }

        retry.visibility = View.VISIBLE
        retry.text = "ELIMINAR VERSIÓN ANTERIOR Y CONTINUAR"
        retry.setOnClickListener {
            val packageName = applicationId
            if (packageName.isNullOrBlank()) {
                showStaticInstallError(
                    "No hay package válido para completar la migración.\n\n" +
                        diagnosticContext("MIGRATION_PACKAGE_MISSING")
                )
                return@setOnClickListener
            }
            persistentErrorVisible = false
            awaitingSignatureMigration = true
            signatureMigrationSettingsFallbackOpened = false
            retry.visibility = View.GONE
            close.visibility = View.GONE
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = true
            status.text = "Esperando confirmación de Android"
            detail.text = "Android va a pedir confirmar la eliminación de la versión anterior. Al volver, StoreAMO continuará solo con la instalación nueva."
            runCatching { DownloadInstaller.requestOfficialUninstall(this, packageName) }
                .onFailure { error ->
                    awaitingSignatureMigration = false
                    showStaticInstallError(
                        "No se pudo abrir el desinstalador oficial: ${error.message.orEmpty()}\n\n" +
                            diagnosticContext("MIGRATION_UNINSTALLER_FAILED")
                    )
                }
        }
        close.visibility = View.VISIBLE
    }

    private fun showStaticInstallError(message: String) {
        polling = false
        fallbackDownloading = false
        verifying = false
        awaitingPermission = false
        awaitingSignatureMigration = false
        installing = false
        persistentErrorVisible = true
        handler.removeCallbacks(pollDownload)
        handler.removeCallbacks(checkInstalled)

        progress.isIndeterminate = false
        progress.visibility = View.GONE
        status.text = "ERROR DE INSTALACIÓN · CAPTURA ESTA PANTALLA"
        detail.text = message.ifBlank { "Android no informó un detalle adicional." } +
            "\n\nEste error queda fijo. No desaparece solo. Sacá una captura y enviala antes de tocar un botón."

        val canRetry = ::apkFile.isInitialized && ::expectedSha256.isInitialized &&
            expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$")) && apkFile.isFile
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
        retry.text = "Reintentar instalación"
        if (canRetry) {
            retry.setOnClickListener {
                persistentErrorVisible = false
                progress.visibility = View.VISIBLE
                if (DownloadInstaller.verifySha256(apkFile, expectedSha256)) {
                    startInstall()
                } else {
                    startDirectFallback("Reintentando sin depender de DownloadManager")
                }
            }
        }
        close.visibility = View.VISIBLE
    }

    private fun diagnosticContext(code: String): String = buildString {
        append("Código StoreAMO: ").append(code)
        append("\nApp: ").append(appName)
        if (targetVersion.isNotBlank()) append("\nVersión: ").append(targetVersion)
        applicationId?.let { append("\nPaquete: ").append(it) }
        append("\nStoreAMO: ").append(BuildConfig.VERSION_NAME)
    }

    private fun consumePersistedInstallError(): String? {
        val prefs = getSharedPreferences(PREFS_INSTALL_ERRORS, Context.MODE_PRIVATE)
        val message = prefs.getString(PREF_LAST_INSTALL_ERROR, null) ?: return null
        prefs.edit().remove(PREF_LAST_INSTALL_ERROR).apply()
        return message
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
