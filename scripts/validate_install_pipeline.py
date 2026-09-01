from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
main_v4 = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
seed = (root / "bootstrap/src/main/java/com/desarrollamo/storeamo/bootstrap/MainActivity.java").read_text(encoding="utf-8")
seed_manifest = (root / "bootstrap/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
seed_gradle = (root / "bootstrap/build.gradle").read_text(encoding="utf-8")

required_installer = [
    "downloadFailureReason", "directDownload", "HttpURLConnection", "verifySha256",
    "fun preflightProblem", "fun canInstallPackages", "fun openInstallPermission", "fun openSystemInstaller",
]
missing = [marker for marker in required_installer if marker not in installer]
assert not missing, f"Missing installer markers: {missing}"

required_flow = [
    "startDirectFallback", "Descarga HTTPS", "DownloadInstaller.openInstallPermission(this)",
    "DownloadInstaller.openSystemInstaller(this, apkFile)", "Autorizar instalaciones",
    "APK verificado", "sin salir a GitHub", "targetInstalled", "Reintentar instalación",
]
missing = [marker for marker in required_flow if marker not in flow]
assert not missing, f"Missing in-app flow markers: {missing}"

assert "openVerifiedUrl" not in flow
assert "Intent.CATEGORY_BROWSABLE" not in flow
assert "android.permission.REQUEST_INSTALL_PACKAGES" in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" not in manifest
assert 'android:name=".InstallFlowActivity"' in manifest

# StoreAMO must update through exactly the same verified in-app path used by catalog APKs.
assert 'DownloadInstaller.start(context, "StoreAMO", artifact)' in main_v4
assert "openStoreAmoUpdateExternallyV4" not in main_v4
assert "descarga y verifica dentro de StoreAMO" in main_v4
assert "Actualizar dentro de StoreAMO" in main_v4

assert 'USER_AGENT = "StoreAMO-Seed/0.0.1"' in seed
assert 'requestInstallPermission' in seed
assert 'canRequestPackageInstalls' in seed
assert 'PackageInstaller.SessionParams.USER_ACTION_REQUIRED' in seed
assert "versionCode 1" in seed_gradle
assert "versionName '0.0.1'" in seed_gradle
assert "applicationId 'com.desarrollamo.storeamo.seed'" in seed_gradle
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in seed_manifest
assert 'REQUEST_DELETE_PACKAGES' not in seed_manifest
assert 'QUERY_ALL_PACKAGES' not in seed_manifest

patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 84, f"In-app self-update requires patch >= 84, got {patch}"

print("STOREAMO_INSTALL_PIPELINE_04384_IN_APP_OK")
print("STOREAMO_SELF_UPDATE_USES_IN_APP_INSTALL_FLOW_OK")
print("STOREAMO_SEED_001_CONTRACT_OK")
