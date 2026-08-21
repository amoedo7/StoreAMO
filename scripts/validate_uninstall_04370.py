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
assert 'storeamo.versionPatch=70' in props
print("STOREAMO_UNINSTALL_04370_OK")
