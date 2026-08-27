#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")

legacy = "653719d327467463b0081e52c330e1859d91876160e82fbd5068aef3bbb6f7b6"
canonical = "2b7ab55c5337735cad89c8006d94bf929244db21277fc9b7bd053f6ab8c5f685"

# The legacy verifier remains available for diagnostics, but StoreAMO 0.4.3.81+
# no longer performs package replacement itself. Android's visible installer is
# the authority for signature continuity and downgrade rejection.
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
assert patch >= 81
assert 'PATCH="$(sed -n \'s/^storeamo.versionPatch=//p\' gradle.properties)"' in ci
assert 'test "$PATCH" -ge 81' in ci
print("STOREAMO_SIGNATURE_SAFETY_SYSTEM_HANDOFF_04381_PLUS_OK")
