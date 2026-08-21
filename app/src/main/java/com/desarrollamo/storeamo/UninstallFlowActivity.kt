package com.desarrollamo.storeamo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Usa siempre superficies oficiales de Android para administrar/desinstalar apps.
 * Si el desinstalador directo no completa el proceso, abre automáticamente
 * "Info. de la aplicación" como respaldo.
 */
class UninstallFlowActivity : Activity() {
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (targetPackage.isBlank() || targetPackage == packageName) {
            finish()
            return
        }
        if (savedInstanceState == null) launchSystemUninstaller()
    }

    @Suppress("DEPRECATION")
    private fun launchSystemUninstaller() {
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

    @Deprecated("Retained for broad Android/OEM compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_UNINSTALL) return
        if (!isInstalled(targetPackage)) {
            finish()
            return
        }
        openApplicationInfo()
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun openApplicationInfo() {
        val primary = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$targetPackage"),
        )
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
            val primary = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
