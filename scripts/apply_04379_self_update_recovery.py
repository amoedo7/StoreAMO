#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace(path: str, old: str, new: str, count: int = 1) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing expected snippet in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

replace("gradle.properties", "storeamo.versionPatch=78", "storeamo.versionPatch=79")
replace("app/build.gradle", "project.findProperty('storeamo.versionPatch') ?: '78'", "project.findProperty('storeamo.versionPatch') ?: '79'")

main = "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt"

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

validator = ROOT / "scripts/validate_04379_self_update_recovery.py"
validator.write_text('''#!/usr/bin/env python3\nfrom pathlib import Path\n\nroot = Path(__file__).resolve().parents[1]\nmain = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")\nprops = (root / "gradle.properties").read_text(encoding="utf-8")\n\nassert "storeamo.versionPatch=79" in props\nassert "openStoreAmoUpdateExternallyV4" in main\nassert "Intent.CATEGORY_BROWSABLE" in main\nassert 'require(artifact.url.startsWith("https://"))' in main\nassert 'DownloadInstaller.start(context, "StoreAMO", artifact)' not in main\nassert "Descargar actualización" in main\nassert "actualización propia se abre fuera de StoreAMO" in main\nprint("STOREAMO_04379_SELF_UPDATE_RECOVERY_OK")\n''', encoding="utf-8")

print("STOREAMO_04379_PATCH_APPLIED")
