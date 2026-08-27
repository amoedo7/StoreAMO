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
]
missing = [item for item in required_installer if item not in installer]
assert not missing, f"Missing verification markers: {missing}"

required_flow = [
    "Verificando descarga · $percent%",
    "APK verificado",
    "Intent.CATEGORY_BROWSABLE",
    "openVerifiedUrl()",
    "targetInstalled()",
    "SHA-256 no coincide",
    "StoreAMO no instala APK por sí misma",
]
missing = [item for item in required_flow if item not in flow]
assert not missing, f"Missing safe handoff markers: {missing}"

assert 'android:name=".InstallFlowActivity"' in manifest
assert 'android:exported="false"' in manifest
assert "android.permission.REQUEST_INSTALL_PACKAGES" not in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" not in manifest
assert "openInstallPermission" not in flow
assert "installWithSession(this, apkFile)" not in flow

print("STOREAMO_SAFE_HANDOFF_UPDATE_OK")
