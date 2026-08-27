#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
script = (root / "scripts/setup_production_signing.sh").read_text(encoding="utf-8")

# GitHub CLI reads a secret from stdin only when --body is omitted.
assert "gh secret set STOREAMO_RELEASE_KEYSTORE_B64 --repo \"$REPO\" --body -" not in script
assert "gh secret set STOREAMO_RELEASE_STORE_PASSWORD --repo \"$REPO\" --body -" not in script
assert "gh secret set STOREAMO_RELEASE_KEY_PASSWORD --repo \"$REPO\" --body -" not in script
assert "gh secret set STOREAMO_RELEASE_KEY_ALIAS --repo \"$REPO\" --body -" not in script
assert "openssl base64 -A -in \"$KS\" | gh secret set STOREAMO_RELEASE_KEYSTORE_B64 --repo \"$REPO\"" in script

# An existing signing identity must be reused, never silently replaced.
assert 'if [ -e "$KS" ]; then' in script
assert "Reutilizando identidad de firma existente" in script
assert 'keytool -list -keystore "$KS" -storepass "$PASS" -alias "$ALIAS"' in script
assert "no se rota ni reemplaza la identidad" in script

print("STOREAMO_PRODUCTION_SIGNING_SETUP_OK")
