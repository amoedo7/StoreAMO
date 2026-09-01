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
    "openInstallPermission",
    "openSystemInstaller",
]
missing = [item for item in required_installer if item not in installer]
assert not missing, f"Missing verification/install markers: {missing}"

required_flow = [
    "Descargando dentro de StoreAMO · $percent%",
    "APK verificado",
    "DownloadInstaller.openInstallPermission(this)",
    "DownloadInstaller.openSystemInstaller(this, apkFile)",
    "targetInstalled()",
    "SHA-256 no coincide",
    "sin salir a GitHub",
]
missing = [item for item in required_flow if item not in flow]
assert not missing, f"Missing in-app install markers: {missing}"

assert 'android:name=".InstallFlowActivity"' in manifest
assert 'android:exported="false"' in manifest
assert "android.permission.REQUEST_INSTALL_PACKAGES" in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" not in manifest
assert "openVerifiedUrl" not in flow
assert "Intent.CATEGORY_BROWSABLE" not in flow

print("STOREAMO_ONE_TAP_IN_APP_INSTALL_OK")
