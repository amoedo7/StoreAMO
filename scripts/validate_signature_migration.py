#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")

legacy = "653719d327467463b0081e52c330e1859d91876160e82fbd5068aef3bbb6f7b6"
canonical = "2b7ab55c5337735cad89c8006d94bf929244db21277fc9b7bd053f6ab8c5f685"

# Preserve the explicit DepositAMO legacy migration while also supporting the
# general StoreAMO UX for any verified APK whose installed predecessor has a
# different signature.
assert legacy in installer
assert canonical in installer
assert 'DEPOSITAMO_PACKAGE = "com.desarrollamo.depositamo"' in installer
assert "knownOneTimeSignatureMigration" in installer
assert "requestOfficialUninstall" in installer
assert "openApplicationDetails" in installer
assert "if (context !is Activity) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)" in installer
assert "StoreAMO no reemplaza firmas desconocidas" in installer
assert 'preflight.startsWith("Actualización bloqueada: la firma no coincide")' in flow
assert "VERSIÓN ANTERIOR INCOMPATIBLE" in flow
assert "ELIMINAR VERSIÓN ANTERIOR Y CONTINUAR" in flow
assert "awaitingSignatureMigration" in flow
assert "signatureMigrationSettingsFallbackOpened" in flow
assert "Info. de la aplicación" in flow
assert "DownloadInstaller.openApplicationDetails" in flow
assert "StoreAMO continuará automáticamente" in flow
assert "Después, las versiones siguientes se actualizan normalmente sin desinstalar" in flow
assert "SIGNATURE_MIGRATION_REQUIRED" in flow
assert "BuildConfig.VERSION_NAME" in flow

patch = int(next(line.split("=", 1)[1] for line in props.splitlines() if line.startswith("storeamo.versionPatch=")))
assert patch >= 76
assert f'test "$PATCH" = "{patch}"' in ci
print("STOREAMO_GENERIC_SIGNATURE_MIGRATION_04376_PLUS_OK")
