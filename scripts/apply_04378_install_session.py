#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing expected snippet in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Version bump.
replace("gradle.properties", "storeamo.versionPatch=77", "storeamo.versionPatch=78")
replace("app/build.gradle", "project.findProperty('storeamo.versionPatch') ?: '77'", "project.findProperty('storeamo.versionPatch') ?: '78'")

flow = "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt"

# The screenshots showed a real race: onResume could fire while Android's installer
# UI was still resolving, and StoreAMO declared failure after only 1.5 seconds.
# Remove that heuristic entirely. PackageInstaller gives us an authoritative status.
old_resume = '''        } else if (installing && targetInstalled()) {
            installing = false
            success()
        } else if (installing && !usingSessionInstaller && installStartedAt > 0L) {
            handler.postDelayed({
                if (installing && !persistentErrorVisible && !targetInstalled()) {
                    installing = false
                    showStaticInstallError(
                        "Android volvió del instalador sin completar la instalación. " +
                            "Si el instalador mostró un mensaje fugaz, este diagnóstico queda fijo para poder capturarlo.\\n\\n" +
                            diagnosticContext("SYSTEM_INSTALLER_RETURNED_WITHOUT_INSTALL")
                    )
                }
            }, 1_500L)
        }
'''
new_resume = '''        } else if (installing && targetInstalled()) {
            installing = false
            success()
        } else if (installing) {
            // No inferimos fracaso por un onResume: varios instaladores OEM vuelven a
            // enfocar StoreAMO mientras la confirmación del sistema todavía está viva.
            status.text = "Esperando a Android"
            detail.text = "La instalación sigue en manos de Android. Confirmá Actualizar/Instalar cuando aparezca el diálogo."
        }
'''
replace(flow, old_resume, new_resume)

# PackageInstaller is now the primary route. It returns explicit status through
# InstallResultReceiver and opens Android's official confirmation UI when needed.
old_route = '''        usingSessionInstaller = false

        val route = runCatching {
            DownloadInstaller.openSystemInstaller(this, apkFile)
        }.recoverCatching { primaryError ->
            usingSessionInstaller = true
            status.text = "Usando instalador alternativo"
            detail.text = "El instalador visible no abrió (${primaryError.message.orEmpty()}). Probando PackageInstaller…"
            DownloadInstaller.installWithSession(this, apkFile)
            "PackageInstaller del sistema"
        }
'''
new_route = '''        usingSessionInstaller = true

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
'''
replace(flow, old_route, new_route)

# A PackageInstaller session has its own callback; don't turn a slow user
# confirmation into a false timeout. Keep timeout only for the compatibility route.
replace(
    flow,
    "            if (System.currentTimeMillis() - installStartedAt > 75_000L) {",
    "            if (!usingSessionInstaller && System.currentTimeMillis() - installStartedAt > 75_000L) {",
)

# Keep diagnostic version truthful.
receiver = ROOT / "app/src/main/java/com/desarrollamo/storeamo/util/InstallResultReceiver.kt"
text = receiver.read_text(encoding="utf-8")
if "import com.desarrollamo.storeamo.BuildConfig\n" not in text:
    text = text.replace(
        "import android.os.Build\n",
        "import android.os.Build\nimport com.desarrollamo.storeamo.BuildConfig\n",
        1,
    )
text = text.replace('append("\\nStoreAMO: 0.4.3.69")', 'append("\\nStoreAMO: ").append(BuildConfig.VERSION_NAME)')
receiver.write_text(text, encoding="utf-8")

validator = ROOT / "scripts/validate_04378_install_session.py"
validator.write_text('''#!/usr/bin/env python3\nfrom pathlib import Path\n\nroot = Path(__file__).resolve().parents[1]\nflow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")\nreceiver = (root / "app/src/main/java/com/desarrollamo/storeamo/util/InstallResultReceiver.kt").read_text(encoding="utf-8")\nprops = (root / "gradle.properties").read_text(encoding="utf-8")\n\nassert "storeamo.versionPatch=78" in props\nassert "DownloadInstaller.installWithSession(this, apkFile)" in flow\nassert "PackageInstaller del sistema" in flow\nassert "recoverCatching { sessionError ->" in flow\nassert "DownloadInstaller.openSystemInstaller(this, apkFile)" in flow\nassert "SYSTEM_INSTALLER_RETURNED_WITHOUT_INSTALL" not in flow\nassert "1_500L" not in flow\nassert "if (!usingSessionInstaller && System.currentTimeMillis() - installStartedAt > 75_000L)" in flow\nassert "No inferimos fracaso por un onResume" in flow\nassert "BuildConfig.VERSION_NAME" in receiver\nprint("STOREAMO_INSTALL_SESSION_04378_OK")\n''', encoding="utf-8")

print("STOREAMO_04378_PATCH_APPLIED")
