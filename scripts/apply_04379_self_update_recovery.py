#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace(path: str, old: str, new: str, count: int = 1) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing expected snippet in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

# Version bump.
replace("gradle.properties", "storeamo.versionPatch=78", "storeamo.versionPatch=79")
replace("app/build.gradle", "project.findProperty('storeamo.versionPatch') ?: '78'", "project.findProperty('storeamo.versionPatch') ?: '79'")

main = "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt"

# Add a dedicated self-update route that does not make StoreAMO replace its own
# package while it is also managing the PackageInstaller session. On affected
# MIUI builds that lifecycle hand-off is unreliable. Let the browser/download
# surface own this one special hand-off; normal app installs still use StoreAMO.
anchor = '''private fun openUrlV4(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
'''
helper = '''private fun openUrlV4(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openStoreAmoUpdateExternallyV4(context: Context, artifact: StoreArtifact) {
    require(artifact.url.startsWith("https://")) { "URL de actualización no segura" }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(artifact.url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
'''
replace(main, anchor, helper)

old_updates = '''                        SelfUpdateCardV4(selfUpdate, selfLoading) { artifact ->
                            runCatching { DownloadInstaller.start(context, "StoreAMO", artifact) }
                                .onFailure { notice = "No pude iniciar la actualización de StoreAMO: ${it.message.orEmpty()}" }
                        }
'''
new_updates = '''                        SelfUpdateCardV4(selfUpdate, selfLoading) { artifact ->
                            runCatching { openStoreAmoUpdateExternallyV4(context, artifact) }
                                .onFailure { notice = "No pude abrir la descarga externa de StoreAMO: ${it.message.orEmpty()}" }
                        }
'''
replace(main, old_updates, new_updates)

old_settings = '''                    item { SelfUpdateCardV4(selfUpdate, selfLoading) { artifact -> DownloadInstaller.start(context, "StoreAMO", artifact) } }
'''
new_settings = '''                    item {
                        SelfUpdateCardV4(selfUpdate, selfLoading) { artifact ->
                            runCatching { openStoreAmoUpdateExternallyV4(context, artifact) }
                                .onFailure { notice = "No pude abrir la descarga externa de StoreAMO: ${it.message.orEmpty()}" }
                        }
                    }
'''
replace(main, old_settings, new_settings)

old_card = '''                latest != null -> { Text("Disponible · ${latest.version}", color = AmoAmber, fontWeight = FontWeight.Black); Button(onClick = { onUpdate(latest) }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar StoreAMO") } }
'''
new_card = '''                latest != null -> {
                    Text("Disponible · ${latest.version}", color = AmoAmber, fontWeight = FontWeight.Black)
                    Text("La actualización propia se abre fuera de StoreAMO para evitar fallos del instalador al reemplazar la app que lo está controlando.", color = AmoMuted, fontSize = 10.sp)
                    Button(onClick = { onUpdate(latest) }, modifier = Modifier.fillMaxWidth()) { Text("Descargar actualización") }
                }
'''
replace(main, old_card, new_card)

# New regression.
validator = ROOT / "scripts/validate_04379_self_update_recovery.py"
validator.write_text('''#!/usr/bin/env python3\nfrom pathlib import Path\n\nroot = Path(__file__).resolve().parents[1]\nmain = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")\nprops = (root / "gradle.properties").read_text(encoding="utf-8")\n\nassert "storeamo.versionPatch=79" in props\nassert "openStoreAmoUpdateExternallyV4" in main\nassert "Intent.CATEGORY_BROWSABLE" in main\nassert 'require(artifact.url.startsWith("https://"))' in main\nassert 'DownloadInstaller.start(context, "StoreAMO", artifact)' not in main\nassert "Descargar actualización" in main\nassert "actualización propia se abre fuera de StoreAMO" in main\nprint("STOREAMO_04379_SELF_UPDATE_RECOVERY_OK")\n''', encoding="utf-8")

# CI knows about 0.4.3.79 and verifies the new invariant.
ci = ROOT / ".github/workflows/android-ci.yml"
text = ci.read_text(encoding="utf-8")
text = text.replace("          python scripts/validate_04378_install_session.py\n", "          python scripts/validate_04378_install_session.py\n          python scripts/validate_04379_self_update_recovery.py\n", 1)
text = text.replace("'User-Agent': 'StoreAMO-CI/0.4.3.78'", "'User-Agent': 'StoreAMO-CI/0.4.3.79'", 1)
text = text.replace('test "$PATCH" = "78"', 'test "$PATCH" = "79"', 1)
old_notes = 'StoreAMO ${VERSION} · Corrige el falso error visto al autoactualizar: PackageInstaller es ahora la ruta primaria y StoreAMO espera el estado oficial de Android en vez de asumir fracaso por un onResume a los 1,5 s. Mantiene el instalador visible como fallback, la migración por cambio de firma y las validaciones de integridad, identidad, firma y permisos.'
new_notes = 'StoreAMO ${VERSION} · Corrige la autoactualización en dispositivos donde Android/MIUI falla al reemplazar StoreAMO mientras la propia app controla PackageInstaller. Las actualizaciones de StoreAMO se entregan ahora mediante una descarga externa HTTPS y el instalador de Android, mientras las demás apps mantienen el flujo verificado interno. Conserva firma canónica, SHA-256 y regresiones de instalación.'
if old_notes not in text:
    raise SystemExit("missing release notes snippet")
text = text.replace(old_notes, new_notes, 1)
ci.write_text(text, encoding="utf-8")

print("STOREAMO_04379_PATCH_APPLIED")
