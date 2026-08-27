#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/desarrollamo/storeamo/BootstrapActivity.kt").read_text(encoding="utf-8")

assert "bootstrapSeed ? 2 :" in gradle
assert "bootstrapSeed ? '0.0.2'" in gradle
assert "android.permission.REQUEST_INSTALL_PACKAGES" in manifest
assert "android.permission.REQUEST_DELETE_PACKAGES" in manifest
assert "DownloadInstaller.canInstallPackages(this)" in activity
assert "DownloadInstaller.openInstallPermission(this@BootstrapActivity)" in activity
assert "override fun onResume()" in activity
assert "Permitir desde esta fuente" in activity
assert "StoreAMO continuará solo" in activity
assert "SelfUpdateRepository.fetchLatest()" in activity
assert "DownloadInstaller.start(this@BootstrapActivity, \"StoreAMO\", artifact)" in activity
assert activity.index("DownloadInstaller.canInstallPackages(this)") < activity.index("SelfUpdateRepository.fetchLatest()")

print("STOREAMO_BOOTSTRAP_002_OK permission-first update-flow delete-request")
