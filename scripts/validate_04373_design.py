#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
colors = (root / "app/src/main/java/com/desarrollamo/storeamo/theme/Color.kt").read_text(encoding="utf-8")
theme = (root / "app/src/main/java/com/desarrollamo/storeamo/theme/Theme.kt").read_text(encoding="utf-8")
icon = (root / "app/src/main/res/drawable/ic_storeamo.xml").read_text(encoding="utf-8")
android_theme = (root / "app/src/main/res/values/themes.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

assert "storeamo.versionPatch=73" in props
assert 'AESTHETIC("aesthetic", "Solar AMO")' in colors
assert "0xFFFFB84D" in colors
assert "tertiary = palette.amber" in theme
assert "StoreAmoShapes" in theme
assert "StoreAmoTypography" in theme
assert 'android:strokeColor="#FFB84D"' in icon
assert 'android:fillColor="#7B75B9"' in icon
assert '<item name="android:statusBarColor">#07111F</item>' in android_theme
assert '<item name="android:navigationBarColor">#07111F</item>' in android_theme
print("STOREAMO_04373_SOLAR_CALM_OK")
