#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt').read_text(encoding='utf-8')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
bootstrap = (root / 'app/src/main/java/com/desarrollamo/storeamo/BootstrapActivity.kt').read_text(encoding='utf-8')
news = (root / 'app/src/main/java/com/desarrollamo/storeamo/GoodNewsActivity.kt').read_text(encoding='utf-8')
repo = (root / 'app/src/main/java/com/desarrollamo/storeamo/data/NewsRepository.kt').read_text(encoding='utf-8')
props = (root / 'gradle.properties').read_text(encoding='utf-8')
uninstall_flow_path = root / 'app/src/main/java/com/desarrollamo/storeamo/UninstallFlowActivity.kt'
uninstall_flow = uninstall_flow_path.read_text(encoding='utf-8') if uninstall_flow_path.exists() else ''

patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 65
assert 'GoodNewsActivity' in manifest
assert 'BootstrapActivity' in manifest
assert 'candidate_downloads_migration_v1' in bootstrap
assert '.putBoolean("verified_only", false)' in bootstrap
assert 'MainActivityV3::class.java' in bootstrap
assert 'Buenas Nuevas' in main
# 0.4.3.65 introduced uninstall; from 0.4.3.70 the Android-owned flow lives
# in UninstallFlowActivity so it can return to App Info when an OEM fails/cancels.
assert 'Intent.ACTION_DELETE' in main or 'Intent.ACTION_DELETE' in uninstall_flow
assert 'Desinstalar' in main
assert 'storeamo.news.v1' in repo
assert 'Buenas Nuevas' in news
# 0.4.3.72 reorganizes the home and turns Buenas Nuevas into a filterable feed.
# Validate product behavior/identity instead of pinning one exact marketing sentence.
assert 'R.drawable.ic_storeamo' in main
assert 'AmoAmber' in main
assert 'StoreSymbolStoryV3' in main
assert 'sin tener que navegar GitHub' in main
assert 'NewsWindow.WEEK' in news
assert 'selectedApp' in news
assert 'NewsKind.DEVELOPMENT' in news
assert 'NewsKind.PUBLISHED' in news
assert 'NewsKind.IMPROVEMENTS' in news
print('STOREAMO_04365_PLUS_FEATURES_OK')
