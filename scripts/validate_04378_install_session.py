#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
receiver = (root / "app/src/main/java/com/desarrollamo/storeamo/util/InstallResultReceiver.kt").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 78, f"0.4.3.78 installer regression requires patch >= 78, got {patch}"
assert "DownloadInstaller.installWithSession(this, apkFile)" in flow
assert "PackageInstaller del sistema" in flow
assert "recoverCatching { sessionError ->" in flow
assert "DownloadInstaller.openSystemInstaller(this, apkFile)" in flow
assert "SYSTEM_INSTALLER_RETURNED_WITHOUT_INSTALL" not in flow
assert "1_500L" not in flow
assert "if (!usingSessionInstaller && System.currentTimeMillis() - installStartedAt > 75_000L)" in flow
assert "No inferimos fracaso por un onResume" in flow
assert "BuildConfig.VERSION_NAME" in receiver
print(f"STOREAMO_INSTALL_SESSION_04378_PLUS_OK patch={patch}")
