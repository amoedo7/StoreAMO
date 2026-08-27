#!/usr/bin/env python3
"""Regression gate for StoreAMO's Play-Protect-safe update path."""
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
    flow = read("app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt")
    ui = read("app/src/main/java/com/desarrollamo/storeamo/MainActivityV4.kt")
    manifest = read("app/src/main/AndroidManifest.xml")
    docs = read("docs/UPDATES_SCRIPTS_AND_DISCOVERY.md")

    # Artifact integrity + transport remain mandatory before handoff.
    require(installer, 'require(artifact.url.startsWith("https://"))', "https-only download")
    require(installer, "verifySha256", "sha256 verification")
    require(installer, "artifact.sizeBytes", "expected-size verification")
    require(flow, "DownloadInstaller.verifySha256", "verified artifact handoff")

    # Play Protect safe mode: StoreAMO is not itself a sideload installer.
    forbid(manifest, "android.permission.REQUEST_INSTALL_PACKAGES", "sideload install permission")
    forbid(manifest, "android.permission.REQUEST_DELETE_PACKAGES", "package delete permission")
    require(flow, "Intent.CATEGORY_BROWSABLE", "browser handoff")
    require(flow, "StoreAMO no instala APK por sí misma", "explicit safe-install contract")
    forbid(flow, "DownloadInstaller.installWithSession(this, apkFile)", "direct PackageInstaller session")
    forbid(flow, "DownloadInstaller.openInstallPermission", "unknown-sources settings request")

    # Fail closed on identity / rollback hazards in the verifier.
    require(installer, "archiveSigners != installedSigners", "signature continuity gate")
    require(installer, "incomingCode < installedCode", "downgrade comparison")
    require(installer, "APK es más antiguo que la versión instalada", "downgrade rejection")

    # Store UI must still discover updates from installed apps vs catalog.
    require(ui, "TabV4.UPDATES", "updates screen")
    require(ui, 'return "Actualizar"', "update action")
    require(ui, "val updates = installedApps.filter", "installed-vs-catalog update list")
    require(ui, "DownloadInstaller.start", "verified handoff entrypoint")

    # Package visibility remains narrow.
    require(manifest, '<package android:name="com.termux" />', "specific Termux visibility")
    forbid(manifest, "android.permission.QUERY_ALL_PACKAGES", "broad package visibility permission")

    require(docs, "Comprueba package/application id y continuidad de firma", "documented signature continuity")
    require(docs, "Actualizar todo", "documented batch-update UX")
    require(docs, "Nunca descargar un APK arbitrario", "documented untrusted-artifact rule")

    print("STOREAMO_UPDATE_SAFETY_PLAY_PROTECT_SAFE_OK")


if __name__ == "__main__":
    main()
