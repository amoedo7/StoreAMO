from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

required_installer = [
    "downloadFailureReason",
    "directDownload",
    "HttpURLConnection",
    'User-Agent", "StoreAMO/0.4.3.68"',
    "FileProvider.getUriForFile",
    "Intent.ACTION_INSTALL_PACKAGE",
    "Intent.ACTION_VIEW",
    "FLAG_GRANT_READ_URI_PERMISSION",
    "openSystemInstaller",
    "installWithSession",
    "PackageInstaller.SessionParams",
    "fun preflightProblem",
]
missing = [marker for marker in required_installer if marker not in installer]
assert not missing, f"Missing resilient installer markers: {missing}"

required_flow = [
    "startDirectFallback",
    "downloadFailureReason",
    "Fallaron ambos métodos de descarga",
    "Descarga HTTPS directa",
    "preflightProblem",
    "openSystemInstaller",
    "installWithSession",
    "SHA-256 correcto",
]
missing = [marker for marker in required_flow if marker not in flow]
assert not missing, f"Missing install-flow markers: {missing}"

assert 'android.intent.action.INSTALL_PACKAGE' in manifest
assert 'android.intent.action.VIEW' in manifest
assert manifest.count('application/vnd.android.package-archive') >= 2
assert 'androidx.core.content.FileProvider' in manifest
assert 'storeamo.versionPatch=68' in props

# Preflight errors must be propagated to the visible flow, not swallowed as a Toast + Unit return.
assert "fun install(context: Context, file: File)" not in installer
assert "Toast.makeText(context, problem" not in installer

print("STOREAMO_INSTALL_PIPELINE_04368_OK")
