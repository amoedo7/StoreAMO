package com.desarrollamo.storeamo.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

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
                    Toast.makeText(context, "Android requiere confirmación para instalar, pero no entregó la pantalla de confirmación.", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Instalación completada.", Toast.LENGTH_SHORT).show()
            }

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
                val suffix = if (message.isBlank()) "" else ": $message"
                Toast.makeText(context, "$readable$suffix", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.desarrollamo.storeamo.INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
