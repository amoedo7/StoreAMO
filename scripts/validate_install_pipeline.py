from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
seed = (root / "bootstrap/src/main/java/com/desarrollamo/storeamo/bootstrap/MainActivity.java").read_text(encoding="utf-8")
seed_manifest = (root / "bootstrap/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
seed_gradle = (root / "bootstrap/build.gradle").read_text(encoding="utf-8")

required_installer = [
    "downloadFailureReason",
    "directDownload",
    "HttpURLConnection",
    'setRequestProperty("User-Agent", "StoreAMO/${com.desarrollamo.storeamo.BuildConfig.VERSION_NAME}")',
    "verifySha256",
    "fun preflightProblem",
]
missing = [marker for marker in required_installer if marker not in installer]
assert not missing, f"Missing verification markers: {missing}"

required_flow = [
    "startDirectFallback",
    "downloadFailureReason",
    "Verificación HTTPS",
    "Intent.ACTION_VIEW",
    "Intent.CATEGORY_BROWSABLE",
    "openVerifiedUrl",
    "APK verificado",
    "StoreAMO no instala APK por sí misma",
    "targetInstalled",
]
missing = [marker for marker in required_flow if marker not in flow]
assert not missing, f"Missing safe handoff markers: {missing}"

assert "DownloadInstaller.openInstallPermission" not in flow
assert "DownloadInstaller.installWithSession(this, apkFile)" not in flow
assert "DownloadInstaller.openSystemInstaller(this, apkFile)" not in flow
assert "android.permission.REQUEST_INSTALL_PACKAGES" not in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" not in manifest
assert 'android:name=".InstallFlowActivity"' in manifest

# Canonical seed contract: one immutable StoreAMO 0.0.1 whose only role is
# installing/updating the full StoreAMO through Android's visible installer.
assert 'USER_AGENT = "StoreAMO-Seed/0.0.1"' in seed
assert '"Actualizaciones"' in seed
assert '"StoreAMO 0.0.1"' in seed
assert 'requestInstallPermission' in seed
assert 'canRequestPackageInstalls' in seed
assert 'PackageInstaller.SessionParams.USER_ACTION_REQUIRED' in seed
assert 'try (PackageInstaller.Session session = installer.openSession(sessionId)) {' in seed
assert 'try (InputStream input = new FileInputStream(apk);' in seed
assert 'session.fsync(output);\n            }\n\n            Intent result' in seed
assert seed.index('session.fsync(output);') < seed.index('session.commit(pending.getIntentSender());')
assert "versionCode 1" in seed_gradle
assert "versionName '0.0.1'" in seed_gradle
assert "applicationId 'com.desarrollamo.storeamo.seed'" in seed_gradle
assert 'android:label="StoreAMO 0.0.1"' in seed_manifest
assert 'android.permission.INTERNET' in seed_manifest
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in seed_manifest
assert 'REQUEST_DELETE_PACKAGES' not in seed_manifest
assert 'QUERY_ALL_PACKAGES' not in seed_manifest
assert 'SYSTEM_ALERT_WINDOW' not in seed_manifest
assert 'BIND_ACCESSIBILITY_SERVICE' not in seed_manifest

patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 81, f"Play Protect safe handoff requires patch >= 81, got {patch}"

print("STOREAMO_INSTALL_PIPELINE_04381_PLAY_PROTECT_SAFE_OK")
print("STOREAMO_SEED_001_CONTRACT_OK")
