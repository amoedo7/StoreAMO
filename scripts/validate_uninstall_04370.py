from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/UninstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

for marker in [
    "UninstallFlowActivity.launch(context, packageName)",
    "UninstallFlowActivity.openInfo(context, packageName)",
    "onInfo = { openAppInfoV3(context, artifact.applicationId) }",
    'Text("Desinstalar"',
    'Text("ⓘ"',
    "Info. de la aplicación de Android",
]:
    assert marker in main, f"missing UI marker: {marker}"

for marker in [
    "Intent.ACTION_UNINSTALL_PACKAGE",
    "Intent.ACTION_DELETE",
    "Intent.EXTRA_RETURN_RESULT",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "onActivityResult",
    "openApplicationInfo()",
]:
    assert marker in flow, f"missing uninstall-flow marker: {marker}"

assert '.UninstallFlowActivity' in manifest
patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 70, f"Uninstall flow requires patch >= 70, got {patch}"
print("STOREAMO_UNINSTALL_04370_PLUS_OK")
