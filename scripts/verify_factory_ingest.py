#!/usr/bin/env python3
"""Verify StoreAMO can ingest the live catalog and download safe canary artifacts."""
from __future__ import annotations

import hashlib
import json
import pathlib
import re
import sys
import urllib.request

CATALOG = "https://raw.githubusercontent.com/amoedo7/StoreAMO-Catalog/main/catalog.json"
ROOT = pathlib.Path(__file__).resolve().parents[1]
MAX_CANARY_BYTES = 5 * 1024 * 1024
SHA = re.compile(r"^[0-9a-f]{64}$")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "StoreAMO-Factory-Canary/1", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=30) as response:
        if response.status < 200 or response.status >= 300:
            raise RuntimeError(f"HTTP {response.status}: {url}")
        return response.read()


def artifact_candidates(apps: list[dict], verified: bool) -> list[tuple[dict, dict]]:
    out = []
    for app in apps:
        for artifact in app.get("artifacts") or []:
            if artifact.get("platform") != "android" or bool(artifact.get("verified", False)) != verified:
                continue
            size = artifact.get("size_bytes")
            if not isinstance(size, int) or size <= 0 or size > MAX_CANARY_BYTES:
                continue
            if not isinstance(artifact.get("url"), str) or not artifact["url"].startswith("https://"):
                continue
            out.append((app, artifact))
    return sorted(out, key=lambda pair: pair[1]["size_bytes"])


def verify_download(app: dict, artifact: dict) -> dict:
    expected_sha = str(artifact.get("sha256", "")).lower()
    if not SHA.fullmatch(expected_sha):
        raise AssertionError(f"{app['id']}: sha256 inválido")
    if not artifact.get("application_id"):
        raise AssertionError(f"{app['id']}: falta application_id")
    body = fetch(artifact["url"])
    if len(body) != artifact["size_bytes"]:
        raise AssertionError(f"{app['id']}: size {len(body)} != {artifact['size_bytes']}")
    actual = hashlib.sha256(body).hexdigest()
    if actual != expected_sha:
        raise AssertionError(f"{app['id']}: sha256 {actual} != {expected_sha}")
    if not body.startswith(b"PK"):
        raise AssertionError(f"{app['id']}: artifact no parece APK/ZIP")
    return {"id": app["id"], "version": artifact.get("version"), "bytes": len(body), "sha256": actual, "verified": bool(artifact.get("verified"))}


def verify_store_contract() -> None:
    source = (ROOT / "app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt").read_text(encoding="utf-8")
    repository = (ROOT / "app/src/main/java/com/desarrollamo/storeamo/data/CatalogRepository.kt").read_text(encoding="utf-8")
    required = [
        'if (verifiedOnly && !artifact.verified)',
        '"Obtener candidate"',
        'app.status != "development"',
        'DownloadInstaller.start(context, app.name, artifact)',
    ]
    for needle in required:
        if needle not in source:
            raise AssertionError(f"StoreAMO factory contract missing: {needle}")
    if 'storeamo.catalog.v1' not in repository or 'url.startsWith("https://")' not in repository:
        raise AssertionError("CatalogRepository no conserva schema/HTTPS gate")


def main() -> int:
    verify_store_contract()
    catalog = json.loads(fetch(CATALOG))
    if catalog.get("schema") != "storeamo.catalog.v1":
        raise AssertionError("live catalog schema inválido")
    apps = catalog.get("apps")
    if not isinstance(apps, list) or not apps:
        raise AssertionError("live catalog vacío")

    verified = artifact_candidates(apps, True)
    candidate = artifact_candidates(apps, False)
    if not verified:
        raise AssertionError("no hay canary Android verified pequeño")
    if not candidate:
        raise AssertionError("no hay canary Android candidate pequeño")

    result = {
        "schema": "storeamo.factory-ingest-canary.v1",
        "catalog_version": catalog.get("catalog_version"),
        "verified": verify_download(*verified[0]),
        "candidate": verify_download(*candidate[0]),
    }
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
