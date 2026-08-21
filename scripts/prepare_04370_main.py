from pathlib import Path

root = Path(__file__).resolve().parents[1]

# 1) Robust Android-owned uninstall flow with automatic App Info fallback.
flow_path = root / "app/src/main/java/com/desarrollamo/storeamo/UninstallFlowActivity.kt"
flow_path.write_text(r'''package com.desarrollamo.storeamo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/** Android-owned uninstall with App Info fallback. */
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
        val primary = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$targetPackage"))
        val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        val opened = runCatching { startActivity(primary); true }.getOrDefault(false)
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
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun openInfo(context: Context, packageName: String?) {
            if (packageName.isNullOrBlank()) return
            val primary = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fallback = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { context.startActivity(primary); true }.getOrDefault(false)
            if (!opened) runCatching { context.startActivity(fallback) }
        }
    }
}
''', encoding="utf-8")

# 2) Register the flow.
manifest_path = root / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
activity_marker = '''        <activity
            android:name=".InstallFlowActivity"
            android:exported="false"
            android:excludeFromRecents="true" />
'''
activity_insert = activity_marker + '''
        <activity
            android:name=".UninstallFlowActivity"
            android:exported="false"
            android:excludeFromRecents="true" />
'''
if '.UninstallFlowActivity' not in manifest:
    if activity_marker not in manifest:
        raise SystemExit("InstallFlowActivity manifest marker missing")
    manifest = manifest.replace(activity_marker, activity_insert, 1)
manifest_path.write_text(manifest, encoding="utf-8")

# 3) Wire V3 buttons: direct Android uninstall + always-visible info button.
main_path = root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt"
text = main_path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, got {count}")
    text = text.replace(old, new, 1)

replace_once(
'''private fun uninstallInstalledV3(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
''',
'''private fun uninstallInstalledV3(context: Context, packageName: String?) {
    UninstallFlowActivity.launch(context, packageName)
}

private fun openAppInfoV3(context: Context, packageName: String?) {
    UninstallFlowActivity.openInfo(context, packageName)
}
''',
"uninstall helper",
)
replace_once(
'''                                onOpen = { selected = app },
                                onUninstall = { uninstallInstalledV3(context, artifact.applicationId) },
                            )
''',
'''                                onOpen = { selected = app },
                                onUninstall = { uninstallInstalledV3(context, artifact.applicationId) },
                                onInfo = { openAppInfoV3(context, artifact.applicationId) },
                            )
''',
"installed card call",
)
replace_once(
'''    installed: String,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
''',
'''    installed: String,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onInfo: () -> Unit,
) {
''',
"installed card signature",
)
replace_once(
'''            OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
            }
''',
'''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
                    Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onInfo, modifier = Modifier.width(58.dp)) {
                    Text("ⓘ", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
''',
"installed card buttons",
)
replace_once(
'''                    if (installed != null) {
                        OutlinedButton(onClick = { uninstallInstalledV3(context, artifact.applicationId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                        }
                    }
''',
'''                    if (installed != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { uninstallInstalledV3(context, artifact.applicationId) }, modifier = Modifier.weight(1f)) {
                                Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { openAppInfoV3(context, artifact.applicationId) }, modifier = Modifier.width(58.dp)) {
                                Text("ⓘ", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                        Text("ⓘ abre Info. de la aplicación de Android. Si el desinstalador directo no completa el proceso, StoreAMO también cae automáticamente en esa pantalla.", color = AmoMuted, fontSize = 9.sp)
                    }
''',
"app sheet buttons",
)
main_path.write_text(text, encoding="utf-8")

# 4) Version + release CI.
props = root / "gradle.properties"
p = props.read_text(encoding="utf-8")
if "storeamo.versionPatch=69" not in p:
    raise SystemExit("Expected StoreAMO patch 69")
props.write_text(p.replace("storeamo.versionPatch=69", "storeamo.versionPatch=70", 1), encoding="utf-8")

ci_path = root / ".github/workflows/android-ci.yml"
ci = ci_path.read_text(encoding="utf-8")
if 'test "$PATCH" = "69"' not in ci:
    raise SystemExit("android-ci expected patch 69 marker missing")
ci = ci.replace('test "$PATCH" = "69"', 'test "$PATCH" = "70"', 1)
needle = "          python scripts/validate_install_pipeline.py\n"
if "validate_uninstall_04370.py" not in ci:
    ci = ci.replace(needle, needle + "          python scripts/validate_uninstall_04370.py\n", 1)
old_notes = "StoreAMO ${VERSION}: los errores de instalación ahora quedan fijos en una pantalla de diagnóstico hasta que el usuario toque Reintentar o Volver. Los fallos de PackageInstaller incluyen código Android, sesión, paquete, versión y detalle; los fallos del instalador visible también dejan un diagnóstico estático para poder enviarlo mediante captura."
new_notes = "StoreAMO ${VERSION}: corrige la administración de apps instaladas. Desinstalar usa el desinstalador oficial de Android y si el flujo no completa abre automáticamente Info. de la aplicación. También agrega un botón ⓘ junto a Desinstalar para abrir directamente esa pantalla del sistema."
if old_notes not in ci:
    raise SystemExit("release notes 0.4.3.69 marker missing")
ci = ci.replace(old_notes, new_notes, 1)
ci_path.write_text(ci, encoding="utf-8")

# 5) Regression guard.
(root / "scripts/validate_uninstall_04370.py").write_text(r'''from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/UninstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
for marker in ["UninstallFlowActivity.launch(context, packageName)", "UninstallFlowActivity.openInfo(context, packageName)", 'Text("Desinstalar"', 'Text("ⓘ"', "Info. de la aplicación de Android"]:
    assert marker in main, marker
for marker in ["Intent.ACTION_UNINSTALL_PACKAGE", "Intent.ACTION_DELETE", "Intent.EXTRA_RETURN_RESULT", "Settings.ACTION_APPLICATION_DETAILS_SETTINGS", "onActivityResult", "openApplicationInfo()"]:
    assert marker in flow, marker
assert '.UninstallFlowActivity' in manifest
assert 'storeamo.versionPatch=70' in props
print("STOREAMO_UNINSTALL_04370_OK")
''', encoding="utf-8")

# Clean one-shot infrastructure before committing the prepared release.
for rel in ["scripts/prepare_04370_main.py", ".github/workflows/prepare-04370-main.yml", "trigger-04370-main.txt"]:
    (root / rel).unlink(missing_ok=True)

print("STOREAMO_04370_PREPARED")
