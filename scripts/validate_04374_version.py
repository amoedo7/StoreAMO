#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
props = (root / "gradle.properties").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")

assert "storeamo.versionPatch=74" in props
assert "?: '74'" in build
assert 'test "$PATCH" = "74"' in ci
print("STOREAMO_04374_VERSION_OK")
