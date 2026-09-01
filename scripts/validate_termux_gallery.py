#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import re
import urllib.request
from pathlib import Path


EXPECTED = {
    "MiDispositivo": "MiDispositivo",
    "MiRed": "MiRed",
    "MiSistema": "MiSistema",
    "MiArchivos": "MiArchivos",
    "MiAPI": "MiAPI",
    "DiagnosticoAMO": "DiagnosticoAMO",
}


def field(block: str, name: str) -> str:
    match = re.search(rf'\b{name}\s*=\s*"([^"]*)"', block)
    if not match:
        raise AssertionError(f"missing {name}")
    return match.group(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--remote", action="store_true")
    args = parser.parse_args()

    source = Path("app/src/main/java/com/desarrollamo/storeamo/TermuxScriptGallery.kt").read_text(encoding="utf-8")
    blocks = re.findall(r"AmoTermuxScript\((.*?)\n\s*\),", source, re.S)
    entries = {field(block, "name"): block for block in blocks if 'source = "https://' in block}
    assert set(entries) == set(EXPECTED), sorted(entries)
    assert "IdeAMO" not in source
    assert source.index("sha256sum") < source.index('"${\'$\'}PY" "${\'$\'}TMP"')
    assert "BLOQUEADO: SHA-256 no coincide" in source

    for name, repo in EXPECTED.items():
        block = entries[name]
        url = field(block, "source")
        digest = field(block, "sha256")
        match = re.fullmatch(
            rf"https://raw\.githubusercontent\.com/amoedo7/{repo}/([0-9a-f]{{40}})/[^/]+\.py",
            url,
        )
        assert match, url
        assert re.fullmatch(r"[0-9a-f]{64}", digest), digest
        if args.remote:
            request = urllib.request.Request(url, headers={"User-Agent": "StoreAMO-CI/0.4.3.86"})
            with urllib.request.urlopen(request, timeout=20) as response:
                body = response.read()
            assert hashlib.sha256(body).hexdigest() == digest, name
            compile(body, url, "exec")

    print("STOREAMO_TERMUX_GALLERY_OK", len(entries), "remote" if args.remote else "static")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
