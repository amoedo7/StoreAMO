#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing expected snippet in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Version bump.
replace("gradle.properties", "storeamo.versionPatch=76", "storeamo.versionPatch=77")
replace("app/build.gradle", "project.findProperty('storeamo.versionPatch') ?: '76'", "project.findProperty('storeamo.versionPatch') ?: '77'")

# Make the uninstall request behave as an Activity transition when possible,
# and always fall back to Android's App info screen rather than a dead end.
installer_old = '''    fun requestOfficialUninstall(context: Context, packageName: String) {
        require(packageName.isNotBlank()) { "Paquete inválido" }
        val uri = Uri.parse("package:$packageName")
        val intents = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri),
            Intent(Intent.ACTION_DELETE, uri),
        )
        var last: Throwable? = null
        for (candidate in intents) {
            try {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return
            } catch (t: Throwable) {
                last = t
            }
        }
        throw IllegalStateException("Android no pudo abrir el desinstalador oficial", last)
    }
'''
installer_new = '''    fun requestOfficialUninstall(context: Context, packageName: String) {
        require(packageName.isNotBlank()) { "Paquete inválido" }
        val uri = Uri.parse("package:$packageName")
        val intents = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri),
            Intent(Intent.ACTION_DELETE, uri),
        )
        for (candidate in intents) {
            try {
                if (context !is Activity) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return
            } catch (_: Throwable) {
                // Probamos la siguiente superficie oficial de Android.
            }
        }
        openApplicationDetails(context, packageName)
    }

    fun openApplicationDetails(context: Context, packageName: String) {
        require(packageName.isNotBlank()) { "Paquete inválido" }
        val primary = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        if (context !is Activity) {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(primary)
        } catch (primaryError: Throwable) {
            try {
                context.startActivity(fallback)
            } catch (fallbackError: Throwable) {
                throw IllegalStateException(
                    "Android no pudo abrir ni el desinstalador ni la información de la app",
                    fallbackError,
                ).also { it.addSuppressed(primaryError) }
            }
        }
    }
'''
replace("app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt", installer_old, installer_new)

# Keep migration state across the OEM/system uninstall screen. If Android comes
# back without removing the package, open App info automatically; only after
# that fallback returns without uninstalling do we show the pending screen.
replace(
    "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt",
    "    private var awaitingSignatureMigration = false\n    private var installing = false",
    "    private var awaitingSignatureMigration = false\n    private var signatureMigrationSettingsFallbackOpened = false\n    private var installing = false",
)

resume_old = '''        if (awaitingSignatureMigration) {
            val packageName = applicationId
            awaitingSignatureMigration = false
            if (packageName != null && !DownloadInstaller.isPackageInstalled(this, packageName)) {
                persistentErrorVisible = false
                status.text = "Versión anterior eliminada"
                detail.text = "Continuando automáticamente con $appName $targetVersion."
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                startInstall()
            } else {
                showSignatureMigration(cancelled = true)
            }
            return
        }
'''
resume_new = '''        if (awaitingSignatureMigration) {
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
'''
replace("app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt", resume_old, resume_new)

replace(
    "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt",
    "            awaitingSignatureMigration = true\n            retry.visibility = View.GONE",
    "            awaitingSignatureMigration = true\n            signatureMigrationSettingsFallbackOpened = false\n            retry.visibility = View.GONE",
)

replace(
    "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt",
    "            append(\"Podés resolverlo sin salir a buscar la app en Ajustes: StoreAMO abrirá el desinstalador oficial de Android. \")\n            append(\"Cuando confirmes la eliminación y vuelvas, StoreAMO continuará automáticamente con $appName $targetVersion.\\n\\n\")",
    "            append(\"Podés resolverlo desde este botón: StoreAMO abrirá el desinstalador oficial de Android. Si el fabricante no completa ese flujo, abrirá directamente Info. de la aplicación, donde Android muestra Desinstalar. \")\n            append(\"Cuando confirmes la eliminación y vuelvas, StoreAMO continuará automáticamente con $appName $targetVersion.\\n\\n\")",
)

# CI/version text.
ci = ROOT / ".github/workflows/android-ci.yml"
text = ci.read_text(encoding="utf-8")
text = text.replace("StoreAMO-CI/0.4.3.76", "StoreAMO-CI/0.4.3.77")
text = text.replace('test "$PATCH" = "76"', 'test "$PATCH" = "77"')
text = text.replace(
    "StoreAMO ${VERSION} · Corrige el flujo de actualización cuando una app instalada usa una firma anterior distinta. Después de verificar el APK por SHA-256, StoreAMO muestra ELIMINAR VERSIÓN ANTERIOR Y CONTINUAR, abre el desinstalador oficial de Android y al volver continúa automáticamente con la instalación nueva. Mantiene la migración legacy de DepositAMO y el resto de validaciones de seguridad.",
    "StoreAMO ${VERSION} · Hace robusta la migración por cambio de firma: el botón abre el desinstalador oficial sin forzar una tarea separada; si Android/OEM no elimina la app, StoreAMO abre automáticamente Info. de la aplicación en esa app para dejar el botón Desinstalar a mano. Al volver, continúa con la instalación verificada. Mantiene las validaciones de integridad, identidad, firma y permisos.",
)
ci.write_text(text, encoding="utf-8")

# Regression checks for the exact behavior requested.
validator = ROOT / "scripts/validate_signature_migration.py"
text = validator.read_text(encoding="utf-8")
text = text.replace(
    'assert "requestOfficialUninstall" in installer\n',
    'assert "requestOfficialUninstall" in installer\nassert "openApplicationDetails" in installer\nassert "if (context !is Activity) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)" in installer\n',
)
text = text.replace(
    'assert "awaitingSignatureMigration" in flow\n',
    'assert "awaitingSignatureMigration" in flow\nassert "signatureMigrationSettingsFallbackOpened" in flow\nassert "Info. de la aplicación" in flow\nassert "DownloadInstaller.openApplicationDetails" in flow\n',
)
validator.write_text(text, encoding="utf-8")

print("STOREAMO_04377_PATCH_APPLIED")
