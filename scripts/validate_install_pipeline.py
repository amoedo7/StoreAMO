from pathlib import Path

root = Path(__file__).resolve().parents[1]
installer = (root / "app/src/main/java/com/desarrollamo/storeamo/util/DownloadInstaller.kt").read_text(encoding="utf-8")
flow = (root / "app/src/main/java/com/desarrollamo/storeamo/InstallFlowActivity.kt").read_text(encoding="utf-8")
receiver = (root / "app/src/main/java/com/desarrollamo/storeamo/util/InstallResultReceiver.kt").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
props = (root / "gradle.properties").read_text(encoding="utf-8")

required_installer = [
    "downloadFailureReason",
    "directDownload",
    "HttpURLConnection",
    'setRequestProperty("User-Agent", "StoreAMO/${com.desarrollamo.storeamo.BuildConfig.VERSION_NAME}")',
    "FileProvider.getUriForFile",
    "Intent.ACTION_INSTALL_PACKAGE",
    "Intent.ACTION_VIEW",
    "FLAG_GRANT_READ_URI_PERMISSION",
    "openSystemInstaller",
    "installWithSession",
    "PackageInstaller.SessionParams",
    "fun preflightProblem",
    "fun install(context: Context, file: File)",
    "EXTRA_PACKAGE_NAME",
    "EXTRA_VERSION_NAME",
]
missing = [marker for marker in required_installer if marker not in installer]
assert not missing, f"Missing resilient installer markers: {missing}"

required_flow = [
    "startDirectFallback",
    "downloadFailureReason",
    "Descarga HTTPS directa",
    "preflightProblem",
    "openSystemInstaller",
    "installWithSession",
    "PackageInstaller del sistema",
    "Esperando confirmación de Android",
    "No inferimos fracaso por un onResume",
    "SHA-256 correcto",
    "ERROR DE INSTALACIÓN · CAPTURA ESTA PANTALLA",
    "Este error queda fijo. No desaparece solo.",
    "showPersistentInstallError",
    "consumePersistedInstallError",
]
missing = [marker for marker in required_flow if marker not in flow]
assert not missing, f"Missing install-flow markers: {missing}"
assert "SYSTEM_INSTALLER_RETURNED_WITHOUT_INSTALL" not in flow, "Do not infer installer failure from onResume timing"

required_receiver = [
    "showStaticError",
    "InstallFlowActivity.showPersistentInstallError",
    "Código Android",
    "Detalle Android",
    "PACKAGE_INSTALLER_FAILURE",
]
missing = [marker for marker in required_receiver if marker not in receiver]
assert not missing, f"Missing persistent receiver diagnostics: {missing}"

assert "Toast.makeText" not in receiver, "Install failures must not disappear as Toasts"
assert 'android.intent.action.INSTALL_PACKAGE' in manifest
assert 'android.intent.action.VIEW' in manifest
assert manifest.count('application/vnd.android.package-archive') >= 2
assert 'androidx.core.content.FileProvider' in manifest
patch = int(next(line.split('=', 1)[1] for line in props.splitlines() if line.startswith('storeamo.versionPatch=')))
assert patch >= 78, f"Authoritative PackageInstaller flow requires patch >= 78, got {patch}"

# Legacy screens may still call install(), but it must route through the robust paths.
assert "runCatching { openSystemInstaller(context, file) }" in installer
assert ".getOrElse { installWithSession(context, file) }" in installer

print("STOREAMO_INSTALL_PIPELINE_04378_PLUS_AUTHORITATIVE_STATUS_OK")
