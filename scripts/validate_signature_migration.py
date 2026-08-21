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
assert "requestOfficialUninstall" in installer
assert "StoreAMO no reemplaza firmas desconocidas" in installer
assert "MIGRACIÓN ÚNICA DE FIRMA" in flow
assert "MIGRAR UNA VEZ Y ACTUALIZAR" in flow
assert "awaitingSignatureMigration" in flow
assert "Después, las versiones siguientes se actualizan normalmente sin desinstalar" in flow
assert "BuildConfig.VERSION_NAME" in flow
assert "storeamo.versionPatch=71" in props
assert 'test "$PATCH" = "71"' in ci
print("STOREAMO_SIGNATURE_MIGRATION_04371_OK")
