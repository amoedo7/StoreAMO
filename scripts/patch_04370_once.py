from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt"
text = main.read_text(encoding="utf-8")


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
                        Text("ⓘ abre Info. de la aplicación de Android, como acceso de respaldo para administrar o desinstalar.", color = AmoMuted, fontSize = 9.sp)
                    }
''',
"app sheet management buttons",
)

main.write_text(text, encoding="utf-8")

props = root / "gradle.properties"
p = props.read_text(encoding="utf-8")
if "storeamo.versionPatch=69" not in p:
    raise SystemExit("gradle.properties is not at patch 69")
props.write_text(p.replace("storeamo.versionPatch=69", "storeamo.versionPatch=70", 1), encoding="utf-8")

workflow = root / ".github/workflows/android-ci.yml"
w = workflow.read_text(encoding="utf-8")
if 'test "$PATCH" = "69"' not in w:
    raise SystemExit("android-ci does not expect patch 69")
w = w.replace('test "$PATCH" = "69"', 'test "$PATCH" = "70"', 1)
needle = "          python scripts/validate_install_pipeline.py\n"
if needle not in w:
    raise SystemExit("android-ci validation marker missing")
w = w.replace(needle, needle + "          python scripts/validate_uninstall_04370.py\n", 1)
old_notes = "StoreAMO ${VERSION}: los errores de instalación ahora quedan fijos en una pantalla de diagnóstico hasta que el usuario toque Reintentar o Volver. Los fallos de PackageInstaller incluyen código Android, sesión, paquete, versión y detalle; los fallos del instalador visible también dejan un diagnóstico estático para poder enviarlo mediante captura."
new_notes = "StoreAMO ${VERSION}: restaura la administración de apps instaladas. Desinstalar usa el desinstalador oficial de Android y, si el flujo se cancela, falla o no está disponible, abre automáticamente Info. de la aplicación. Cada app instalada también muestra un botón ⓘ que abre directamente esa pantalla del sistema."
if old_notes not in w:
    raise SystemExit("release notes marker missing")
w = w.replace(old_notes, new_notes, 1)
workflow.write_text(w, encoding="utf-8")

validation = root / "scripts/validate_uninstall_04370.py"
validation.write_text(r'''from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/UninstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

for marker in [
    "UninstallFlowActivity.launch(context, packageName)",
    "UninstallFlowActivity.openInfo(context, packageName)",
    "onInfo = { openAppInfoV3(context, artifact.applicationId) }",
    'Text("ⓘ"',
    'Text("Desinstalar"',
    "Info. de la aplicación de Android",
]:
    assert marker in main, f"missing UI marker: {marker}"

for marker in [
    "Intent.ACTION_UNINSTALL_PACKAGE",
    "Intent.ACTION_DELETE",
    "Intent.EXTRA_RETURN_RESULT",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "openApplicationInfo()",
    "onActivityResult",
]:
    assert marker in flow, f"missing uninstall-flow marker: {marker}"

assert '.UninstallFlowActivity' in manifest
assert 'storeamo.versionPatch=70' in props
print("STOREAMO_UNINSTALL_04370_OK")
''', encoding="utf-8")

# One-shot helper files delete themselves so they never land in main.
(root / "scripts/patch_04370_once.py").unlink(missing_ok=True)
(root / ".github/workflows/prepare-04370.yml").unlink(missing_ok=True)

print("STOREAMO_04370_PATCHED")
