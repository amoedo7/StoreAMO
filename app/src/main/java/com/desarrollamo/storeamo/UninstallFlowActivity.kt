package com.desarrollamo.storeamo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Flujo de desinstalación controlado por Android.
 *
 * StoreAMO nunca borra paquetes directamente: entrega el packageName al
 * desinstalador oficial. Si Android/OEM no puede completar o cancela el flujo,
 * abre automáticamente "Info. de la aplicación" para que el usuario conserve
 * una salida oficial con el botón Desinstalar del sistema.
 */
class UninstallFlowActivity : Activity() {
    private var targetPackage: String = ""
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (targetPackage.isBlank() || targetPackage == packageName) {
            finish()
            return
        }
        if (savedInstanceState == null) launchSystemUninstaller()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LAUNCHED, launched)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        launched = savedInstanceState.getBoolean(STATE_LAUNCHED, false)
    }

    @Suppress("DEPRECATION")
    private fun launchSystemUninstaller() {
        launched = true
        val uri = Uri.parse("package:$targetPackage")
        val intents = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri).putExtra(Intent.EXTRA_RETURN_RESULT, true),
            Intent(Intent.ACTION_DELETE, uri).putExtra(Intent.EXTRA_RETURN_RESULT, true),
        )

        val opened = intents.any { candidate ->
            runCatching {
                startActivityForResult(candidate, REQUEST_UNINSTALL)
                true
            }.getOrDefault(false)
        }
        if (!opened) openApplicationInfo()
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_UNINSTALL) return

        if (!isInstalled(targetPackage)) {
            finish()
            return
        }

        // Cancelado, bloqueado o no completado: siempre dejamos una vía oficial.
        openApplicationInfo()
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun openApplicationInfo() {
        val uri = Uri.parse("package:$targetPackage")
        val primary = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)

        val opened = runCatching {
            startActivity(primary)
            true
        }.getOrDefault(false)

        if (!opened) runCatching { startActivity(fallback) }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "target_package"
        private const val REQUEST_UNINSTALL = 7043
        private const val STATE_LAUNCHED = "launched"

        fun launch(context: Context, packageName: String?) {
            if (packageName.isNullOrBlank()) return
            context.startActivity(
                Intent(context, UninstallFlowActivity::class.java)
                    .putExtra(EXTRA_PACKAGE, packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        fun openInfo(context: Context, packageName: String?) {
            if (packageName.isNullOrBlank()) return
            val uri = Uri.parse("package:$packageName")
            val primary = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val opened = runCatching {
                context.startActivity(primary)
                true
            }.getOrDefault(false)
            if (!opened) runCatching { context.startActivity(fallback) }
        }
    }
}
