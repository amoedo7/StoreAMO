#!/usr/bin/env python3
"""Regression gate for StoreAMO's verified install/update path."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"UPDATE_SAFETY_FAIL: {label}: missing {needle!r}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"UPDATE_SAFETY_FAIL: {label}: forbidden {needle!r}")


def main() -> None:
    installer = read("app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt")
    receiver = read("app/src/main/java/com/desarrollamo/storeamo/util/InstallResultReceiver.kt")
    ui = read("app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt")
    manifest = read("app/src/main/AndroidManifest.xml")
    docs = read("docs/UPDATES_SCRIPTS_AND_DISCOVERY.md")

    # Artifact integrity + transport.
    require(installer, 'require(artifact.url.startsWith("https://"))', "https-only download")
    require(installer, "verifySha256", "sha256 verification")
    require(installer, "artifact.sizeBytes", "expected-size verification")

    # Android in-place update path and user-confirmation handling.
    require(installer, "PackageInstaller.SessionParams.MODE_FULL_INSTALL", "PackageInstaller full-install/update session")
    require(installer, "setAppPackageName(archive.packageName)", "stable package identity")
    require(receiver, "PackageInstaller.STATUS_PENDING_USER_ACTION", "pending-user-action handling")
    require(receiver, "Intent.EXTRA_INTENT", "Android confirmation intent")

    # Fail closed on identity / rollback hazards.
    require(installer, "archiveSigners != installedSigners", "signature continuity gate")
    require(installer, "incomingCode < installedCode", "downgrade comparison")
    require(installer, "APK es más antiguo que la versión instalada", "downgrade rejection")
    require(installer, "incomingCode == installedCode", "same-version rejection")

    # Store UI must discover updates from installed apps vs catalog.
    require(ui, "TabV4.UPDATES", "updates screen")
    require(ui, 'return "Actualizar"', "update action")
    require(ui, "val updates = installedApps.filter", "installed-vs-catalog update list")
    require(ui, "DownloadInstaller.start", "verified installer entrypoint")

    # Termux discovery must remain narrow. Comments may mention QUERY_ALL_PACKAGES;
    # only the actual Android permission is forbidden.
    require(manifest, '<package android:name="com.termux" />', "specific Termux visibility")
    forbid(manifest, "android.permission.QUERY_ALL_PACKAGES", "broad package visibility permission")

    # Architecture contract must keep the same security invariants documented.
    require(docs, "Comprueba package/application id y continuidad de firma", "documented signature continuity")
    require(docs, "Actualizar todo", "documented batch-update UX")
    require(docs, "Nunca descargar un APK arbitrario", "documented untrusted-artifact rule")

    print("STOREAMO_UPDATE_SAFETY_OK")


if __name__ == "__main__":
    main()
