#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt').read_text(encoding='utf-8')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
news = (root / 'app/src/main/java/com/desarrollamo/storeamo/GoodNewsActivity.kt').read_text(encoding='utf-8')
repo = (root / 'app/src/main/java/com/desarrollamo/storeamo/data/NewsRepository.kt').read_text(encoding='utf-8')
props = (root / 'gradle.properties').read_text(encoding='utf-8')

assert 'storeamo.versionPatch=65' in props
assert 'GoodNewsActivity' in manifest
assert 'Buenas Nuevas' in main
assert 'Intent.ACTION_DELETE' in main
assert 'Desinstalar' in main
assert 'storeamo.news.v1' in repo
assert 'Buenas Nuevas' in news
assert 'Sin tener que entrar a GitHub' in news
print('STOREAMO_04365_FEATURES_OK')
