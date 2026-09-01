#!/usr/bin/env bash
set -euo pipefail

REPO="amoedo7/StoreAMO"
DIR="${HOME}/.storeamo-signing"
KS="${DIR}/storeamo-release.jks"
RECOVERY="${DIR}/RECOVERY.txt"
EXPECTED="7BD60FD751723041CF2802BEA01FBD83700410A1FD2DD019CACBCD739CB72DCF"
OLD="48F3671ED5F30D065EE4BAAC5CB509C4B59D3FF116D21E6CA81B21029862AB51"

need(){ command -v "$1" >/dev/null 2>&1; }
for cmd in gh keytool openssl sed tr; do
  need "$cmd" || { echo "Falta $cmd" >&2; exit 1; }
done

gh auth status >/dev/null 2>&1 || { echo "gh no está autenticado" >&2; exit 1; }
test -s "$KS" || { echo "Falta $KS" >&2; exit 1; }
test -s "$RECOVERY" || { echo "Falta $RECOVERY" >&2; exit 1; }
chmod 700 "$DIR"
chmod 600 "$KS" "$RECOVERY"

PASS="$(sed -n 's/^Store password: //p' "$RECOVERY" | head -1)"
KEY_PASS="$(sed -n 's/^Key password: //p' "$RECOVERY" | head -1)"
ALIAS="$(sed -n 's/^Alias: //p' "$RECOVERY" | head -1)"
test -n "$PASS"
test -n "$KEY_PASS"
test -n "$ALIAS"

keytool -list -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" >/dev/null
CERT="$(keytool -list -v -keystore "$KS" -storepass "$PASS" -alias "$ALIAS" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"

if [ "$CERT" != "$EXPECTED" ]; then
  echo "BLOQUEADO: la clave local no es la identidad de migración aprobada." >&2
  echo "Local:    $CERT" >&2
  echo "Esperada: $EXPECTED" >&2
  exit 1
fi

printf '%s' "$OLD" > "${DIR}/PREVIOUS-CERT-0.4.3.82.txt"
printf '%s\n' "$CERT" > "${DIR}/ACTIVE-CERT-0.4.3.83-PLUS.txt"
chmod 600 "${DIR}/PREVIOUS-CERT-0.4.3.82.txt" "${DIR}/ACTIVE-CERT-0.4.3.83-PLUS.txt"

# gh secret set consumes stdin when --body is omitted. Nunca usar --body -.
openssl base64 -A -in "$KS" | gh secret set STOREAMO_RELEASE_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$PASS" | gh secret set STOREAMO_RELEASE_STORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_PASS" | gh secret set STOREAMO_RELEASE_KEY_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set STOREAMO_RELEASE_KEY_ALIAS --repo "$REPO"

echo "✓ Identidad local verificada: $CERT"
echo "✓ GitHub Actions Secrets reparados"
echo "✓ La firma anterior 0.4.3.82 queda registrada sólo como frontera de migración"

echo "Disparando StoreAMO 0.4.3.83…"
gh workflow run production-update.yml --repo "$REPO" --ref main
sleep 3
RUN_ID="$(gh run list --repo "$REPO" --workflow production-update.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
test -n "$RUN_ID"
gh run watch "$RUN_ID" --repo "$REPO" --exit-status

echo
cat <<'EOF'
✓ StoreAMO 0.4.3.83 publicada con la nueva identidad permanente.
IMPORTANTE: Android no puede actualizar en sitio desde 0.4.3.82 porque la firma anterior se perdió.
Una sola vez: desinstalá StoreAMO 0.4.3.82 (NO StoreAMO Install), abrí StoreAMO Install y reinstalá la Store estable.
Desde 0.4.3.83 en adelante se conserva la identidad 7BD60FD7…
EOF
