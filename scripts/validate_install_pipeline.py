from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
bootstrap = (root / "bootstrap/src/main/java/com/desarrollamo/storeamo/bootstrap/MainActivity.java").read_text(encoding="utf-8")
bootstrap_gradle = (root / "bootstrap/build.gradle").read_text(encoding="utf-8")

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

# Bootstrap 0.0.6 regression: PackageInstaller rejects commit() while any
# Session.openWrite() stream is still open (Android reports "Files still open").
assert 'USER_AGENT = "StoreAMO-Install/0.0.6"' in bootstrap
assert 'try (PackageInstaller.Session session = installer.openSession(sessionId)) {' in bootstrap
assert 'try (InputStream input = new FileInputStream(apk);' in bootstrap
assert 'session.fsync(output);\n            }\n\n            Intent result' in bootstrap
assert bootstrap.index('session.fsync(output);') < bootstrap.index('session.commit(pending.getIntentSender());')
assert "versionCode 6" in bootstrap_gradle
assert "versionName '0.0.6'" in bootstrap_gradle
assert "applicationId 'com.desarrollamo.storeamo.installer'" in bootstrap_gradle

patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 81, f"Play Protect safe handoff requires patch >= 81, got {patch}"

print("STOREAMO_INSTALL_PIPELINE_04381_PLAY_PROTECT_SAFE_OK")
print("STOREAMO_BOOTSTRAP_006_STREAM_LIFECYCLE_OK")
