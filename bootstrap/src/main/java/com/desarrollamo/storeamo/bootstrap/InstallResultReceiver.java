package com.desarrollamo.storeamo.bootstrap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class InstallResultReceiver extends BroadcastReceiver {
    static final String ACTION_INSTALL_STATUS = "com.desarrollamo.storeamo.bootstrap.INSTALL_STATUS";
    private static final String PREFS = "storeamo_install";
    private static final String KEY_LAST_STATUS = "last_status";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_INSTALL_STATUS.equals(intent.getAction())) return;

        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                //noinspection deprecation
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            save(context, "Android está esperando tu confirmación final para instalar StoreAMO.");
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            String message = "StoreAMO se instaló correctamente.";
            save(context, message);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            return;
        }

        String message;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                message = "Android o Play Protect bloqueó la instalación. StoreAMO Install no desactiva ni elude esa protección.";
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                message = "La StoreAMO instalada entra en conflicto con la firma o identidad de la release oficial. Puede requerir retirar una versión antigua antes de instalar la nueva identidad estable.";
                break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                message = "Esta release de StoreAMO no es compatible con el dispositivo.";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                message = "No hay espacio suficiente para instalar StoreAMO.";
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                message = "Android rechazó el APK como inválido.";
                break;
            default:
                message = "Android no pudo instalar StoreAMO.";
                break;
        }
        if (detail != null && !detail.trim().isEmpty()) {
            message += " Detalle del sistema: " + detail.replace('\n', ' ').replace('\r', ' ');
        }
        save(context, message);
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static void save(Context context, String message) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_STATUS, message)
                .apply();
    }
}
