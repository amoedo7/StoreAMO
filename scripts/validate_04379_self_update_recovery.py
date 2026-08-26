#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

patch_line = next(line for line in props.splitlines() if line.startswith("storeamo.versionPatch="))
patch = int(patch_line.split("=", 1)[1])
assert patch >= 79
assert "openStoreAmoUpdateExternallyV4" in main
assert "Intent.CATEGORY_BROWSABLE" in main
assert 'require(artifact.url.startsWith("https://"))' in main
assert 'DownloadInstaller.start(context, "StoreAMO", artifact)' not in main
assert "Descargar actualización" in main
assert "actualización propia se abre fuera de StoreAMO" in main
print(f"STOREAMO_SELF_UPDATE_RECOVERY_OK patch={patch}")
