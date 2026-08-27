#!/usr/bin/env bash
set -euo pipefail

REPO="amoedo7/StoreAMO"
DIR="${HOME}/.storeamo-signing"
KS="${DIR}/storeamo-release.jks"
RECOVERY="${DIR}/RECOVERY.txt"
ALIAS="storeamo-release"

need(){ command -v "$1" >/dev/null 2>&1 || return 1; }

if ! need gh || ! need keytool || ! need openssl || ! need base64; then
  if need pkg; then
    echo "Instalando herramientas necesarias en Termux…"
    pkg install -y gh openjdk-17 openssl coreutils
  else
    echo "Faltan gh, keytool, openssl o base64. Instalalos y repetí." >&2
    exit 1
  fi
fi

gh auth status >/dev/null 2>&1 || gh auth login
mkdir -p "$DIR"
chmod 700 "$DIR"

if [ -e "$KS" ]; then
  echo "Ya existe $KS; no se reemplaza una identidad de firma existente." >&2
  echo "Usá el backup existente o movelo explícitamente si querés crear otra identidad." >&2
  exit 1
fi

PASS="$(openssl rand -hex 24)"
keytool -genkeypair -v \
  -keystore "$KS" \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
  -dname "CN=DesarrollAMO StoreAMO Release, OU=Mobile, O=DesarrollAMO, C=AR"
chmod 600 "$KS"

# OpenSSL produces a single-line RFC 4648 Base64 value consistently on
# Termux and GNU/Linux. Avoid Android/Toybox base64 wrapping/format quirks.
B64="$(openssl base64 -A -in "$KS")"
printf '%s' "$B64" | gh secret set STOREAMO_RELEASE_KEYSTORE_B64 --repo "$REPO" --body -
printf '%s' "$PASS" | gh secret set STOREAMO_RELEASE_STORE_PASSWORD --repo "$REPO" --body -
printf '%s' "$PASS" | gh secret set STOREAMO_RELEASE_KEY_PASSWORD --repo "$REPO" --body -
printf '%s' "$ALIAS" | gh secret set STOREAMO_RELEASE_KEY_ALIAS --repo "$REPO" --body -

FINGERPRINT="$(keytool -list -v -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1)"
cat > "$RECOVERY" <<EOF
STOREAMO RELEASE SIGNING BACKUP
===============================
Keystore: $KS
Alias: $ALIAS
Store password: $PASS
Key password: $PASS
SHA-256 certificate: $FINGERPRINT

GUARDÁ ESTA CARPETA EN UN BACKUP PRIVADO.
Sin esta clave no se pueden publicar actualizaciones con la misma identidad.
EOF
chmod 600 "$RECOVERY"

echo "Firma de producción creada y cargada como GitHub Actions Secrets."
echo "Backup privado: $DIR"
echo "Certificado SHA-256: $FINGERPRINT"
echo "Disparando producción StoreAMO 0.4.3.82 + Bootstrap 0.0.4…"
gh workflow run production-release.yml --repo "$REPO"
sleep 3
RUN_ID="$(gh run list --repo "$REPO" --workflow production-release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
if [ -n "$RUN_ID" ]; then
  gh run watch "$RUN_ID" --repo "$REPO" --exit-status
fi

echo "Publicación terminada."
echo "Bootstrap: https://github.com/amoedo7/StoreAMO/releases/download/bootstrap-v0.0.4/StoreAMO-Bootstrap-0.0.4.apk"
