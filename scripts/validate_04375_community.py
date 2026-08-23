#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
props = (root / "gradle.properties").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
ci = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
bootstrap = (root / "app/src/main/java/com/desarrollamo/storeamo/BootstrapActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")
panel = (root / "app/src/main/java/com/desarrollamo/storeamo/FeedbackPanelV3.kt").read_text(encoding="utf-8")
repo = (root / "app/src/main/java/com/desarrollamo/storeamo/data/FeedbackRepository.kt").read_text(encoding="utf-8")

assert "storeamo.versionPatch=75" in props
assert "?: '75'" in build
assert 'test "$PATCH" = "75"' in ci
assert "MainActivityV4::class.java" in bootstrap
assert 'android:name=".MainActivityV4"' in manifest

for rpc in (
    "storeamo_submit_rating",
    "storeamo_submit_feedback",
    "storeamo_feedback_summary",
    "storeamo_public_comments",
    "storeamo_ratings_overview",
):
    assert rpc in repo

assert "sb_publishable_" in repo
assert "service_role" not in repo.lower()
assert "Somos desarrolladores independientes" in panel
assert "modificación, una mejora o viste algo que no funciona" in panel
assert '"idea"' in panel and '"mejora"' in panel and '"error"' in panel
assert "Mejor valoradas" in main
assert "FeedbackPanelV3" in main
assert "Migrar una vez y actualizar" in main
assert 'CLIMA_LEGACY_VERSION = "0.2.0"' in main
assert 'CLIMA_PACKAGE = "com.desarrollamo.climaamo"' in main
print("STOREAMO_04375_COMMUNITY_OK")
