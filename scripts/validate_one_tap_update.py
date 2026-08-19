from pathlib import Path

installer = Path("app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = Path("app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

required_installer = [
    "InstallFlowActivity.launch(context, appName, pending)",
    "progressPercent",
    "COLUMN_BYTES_DOWNLOADED_SO_FAR",
    "COLUMN_TOTAL_SIZE_BYTES",
    "verifySha256",
    "PackageInstaller.SessionParams",
]
missing = [item for item in required_installer if item not in installer]
assert not missing, f"Missing installer markers: {missing}"

required_flow = [
    "Descargando · $percent%",
    "Descarga verificada",
    "openInstallPermission",
    "startInstall()",
    "targetInstalled()",
    "SHA-256 no coincide",
]
missing = [item for item in required_flow if item not in flow]
assert not missing, f"Missing flow markers: {missing}"

assert 'android:name=".InstallFlowActivity"' in manifest
assert 'android:exported="false"' in manifest
assert "volvé a tocar Instalar" not in flow

print("STOREAMO_ONE_TAP_UPDATE_OK")
