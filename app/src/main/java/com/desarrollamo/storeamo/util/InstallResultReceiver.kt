package com.desarrollamo.storeamo.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.desarrollamo.storeamo.BuildConfig
import com.desarrollamo.storeamo.InstallFlowActivity

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                } else {
                    showStaticError(
                        context = context,
                        packageName = packageName,
                        versionName = versionName,
                        sessionId = sessionId,
                        status = status,
                        title = "Android pidió confirmación pero no entregó la pantalla",
                        androidMessage = message,
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> {
                val readable = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalación cancelada"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Instalación bloqueada por Android"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicto de paquete o firma"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible con este dispositivo"
                    PackageInstaller.STATUS_FAILURE_INVALID -> "APK inválido"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente"
                    else -> "No se pudo instalar"
                }
                showStaticError(
                    context = context,
                    packageName = packageName,
                    versionName = versionName,
                    sessionId = sessionId,
                    status = status,
                    title = readable,
                    androidMessage = message,
                )
            }
        }
    }

    private fun showStaticError(
        context: Context,
        packageName: String,
        versionName: String,
        sessionId: Int,
        status: Int,
        title: String,
        androidMessage: String,
    ) {
        val diagnostic = buildString {
            append(title)
            append("\n\nCódigo Android: ").append(status)
            append("\nSesión: ").append(sessionId)
            if (packageName.isNotBlank()) append("\nPaquete: ").append(packageName)
            if (versionName.isNotBlank()) append("\nVersión: ").append(versionName)
            append("\nDetalle Android: ")
            append(androidMessage.ifBlank { "Android no entregó texto adicional." })
            append("\nCódigo StoreAMO: PACKAGE_INSTALLER_FAILURE")
            append("\nStoreAMO: ").append(BuildConfig.VERSION_NAME)
        }

        InstallFlowActivity.showPersistentInstallError(
            context = context,
            appLabel = packageName,
            version = versionName,
            applicationId = packageName,
            message = diagnostic,
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.desarrollamo.storeamo.INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_VERSION_NAME = "version_name"
    }
}
