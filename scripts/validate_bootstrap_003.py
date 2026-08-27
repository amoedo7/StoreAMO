#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/desarrollamo/storeamo/BootstrapActivity.kt").read_text(encoding="utf-8")

assert "bootstrapSeed ? 3 :" in gradle
assert "bootstrapSeed ? '0.0.3'" in gradle
assert "android.permission.REQUEST_INSTALL_PACKAGES" not in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" not in manifest
assert "SelfUpdateRepository.fetchLatest()" in activity
assert "Intent.ACTION_VIEW" in activity
assert "Intent.CATEGORY_BROWSABLE" in activity
assert "OFFICIAL_RELEASE_PREFIX" in activity
assert "Esta versión inicial no pide permiso para instalar otras apps" in activity
assert "DownloadInstaller.canInstallPackages" not in activity
assert "DownloadInstaller.openInstallPermission" not in activity
assert "DownloadInstaller.start" not in activity

print("STOREAMO_BOOTSTRAP_003_PLAY_PROTECT_SAFE_OK")
