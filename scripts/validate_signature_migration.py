#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")

legacy = "653719d327467463b0081e52c330e1859d91876160e82fbd5068aef3bbb6f7b6"
canonical = "2b7ab55c5337735cad89c8006d94bf929244db21277fc9b7bd053f6ab8c5f685"

assert legacy in installer
assert canonical in installer
assert 'DEPOSITAMO_PACKAGE = "com.desarrollamo.depositamo"' in installer
assert "knownOneTimeSignatureMigration" in installer
assert "StoreAMO no reemplaza firmas desconocidas" in installer
assert "Intent.CATEGORY_BROWSABLE" in flow
assert "StoreAMO no instala APK por sí misma" in flow
assert "ELIMINAR VERSIÓN ANTERIOR Y CONTINUAR" not in flow
assert "awaitingSignatureMigration" not in flow
assert "DownloadInstaller.requestOfficialUninstall" not in flow
assert "DownloadInstaller.installWithSession(this, apkFile)" not in flow

patch = int(next(line.split("=", 1)[1] for line in props.splitlines() if line.startswith("storeamo.versionPatch=")))
assert patch >= 82
assert 'PATCH="$(sed -n \'s/^storeamo.versionPatch=//p\' gradle.properties)"' in ci
assert 'test "$PATCH" -ge 82' in ci
assert "assembleRelease" in ci
assert "com.desarrollamo.storeamo' versionCode=" in ci
assert "com.desarrollamo.storeamo.debug" not in ci
print("STOREAMO_PRODUCTION_IDENTITY_04382_PLUS_OK")
