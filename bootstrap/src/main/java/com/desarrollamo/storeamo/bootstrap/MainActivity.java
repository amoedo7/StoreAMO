package com.desarrollamo.storeamo.bootstrap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String STORE_PACKAGE = "com.desarrollamo.storeamo";
    private static final String RELEASE_API = "https://api.github.com/repos/amoedo7/StoreAMO/releases/latest";
    private static final String RELEASE_PREFIX = "/amoedo7/StoreAMO/releases/download/";
    private static final String USER_AGENT = "StoreAMO-Seed/0.0.1";
    private static final String PREFS = "storeamo_seed";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;

    private TextView status;
    private TextView installedState;
    private Button installButton;
    private Button openButton;
    private ProgressBar progress;
    private boolean busy;
    private boolean permissionScreenOpened;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(6, 18, 32));
        getWindow().setNavigationBarColor(Color.rgb(6, 18, 32));
        buildUi();
        refreshUi();
        if (!canInstallPackages()) {
            installButton.postDelayed(this::requestInstallPermission, 350);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionScreenOpened) {
            permissionScreenOpened = false;
            if (canInstallPackages()) {
                status.setText("Permiso concedido. La semilla ya puede instalar y actualizar StoreAMO.");
            } else {
                status.setText("Falta autorizar ‘Permitir desde esta fuente’. Podés volver a pedir el permiso con el botón.");
            }
        }
        refreshUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 42, 32, 48);
        root.setBackgroundColor(Color.rgb(6, 18, 32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("StoreAMO", 30f, Color.WHITE);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(brand, matchWrap());

        TextView seed = text("DesarrollAMO · semilla oficial 0.0.1", 15f, Color.rgb(154, 170, 187));
        LinearLayout.LayoutParams seedParams = matchWrap();
        seedParams.setMargins(0, 2, 0, 44);
        root.addView(seed, seedParams);

        TextView eyebrow = text("ACTUALIZACIONES", 13f, Color.rgb(80, 203, 239));
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(eyebrow, matchWrap());

        TextView title = text("Actualizaciones", 37f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, 8, 0, 14);
        root.addView(title, titleParams);

        TextView intro = text(
                "Esta semilla sólo hace una cosa: dejar StoreAMO instalada y al día. " +
                "Descarga la release estable oficial, verifica SHA-256 y entrega el APK al instalador de Android.",
                17f,
                Color.rgb(171, 184, 199));
        root.addView(intro, matchWrap());

        LinearLayout seedCard = card();
        TextView seedTitle = text("StoreAMO 0.0.1", 24f, Color.WHITE);
        seedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        seedCard.addView(seedTitle, matchWrap());
        TextView seedBody = text("Semilla permanente · sólo Actualizaciones", 15f, Color.rgb(89, 219, 174));
        LinearLayout.LayoutParams seedBodyParams = matchWrap();
        seedBodyParams.setMargins(0, 10, 0, 0);
        seedCard.addView(seedBody, seedBodyParams);
        LinearLayout.LayoutParams seedCardParams = matchWrap();
        seedCardParams.setMargins(0, 30, 0, 22);
        root.addView(seedCard, seedCardParams);

        TextView newTitle = text("StoreAMO principal", 20f, Color.WHITE);
        newTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(newTitle, matchWrap());

        LinearLayout storeCard = card();
        installedState = text("Comprobando…", 18f, Color.WHITE);
        installedState.setTypeface(null, android.graphics.Typeface.BOLD);
        storeCard.addView(installedState, matchWrap());

        status = text("Preparando semilla…", 15f, Color.rgb(171, 184, 199));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, 12, 0, 14);
        storeCard.addView(status, statusParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, 0, 0, 10);
        storeCard.addView(progress, progressParams);

        installButton = new Button(this);
        installButton.setText("INSTALAR / ACTUALIZAR STOREAMO");
        installButton.setAllCaps(false);
        installButton.setOnClickListener(v -> beginInstallFlow());
        storeCard.addView(installButton, matchWrap());

        openButton = new Button(this);
        openButton.setText("ABRIR STOREAMO");
        openButton.setAllCaps(false);
        openButton.setOnClickListener(v -> openStore());
        LinearLayout.LayoutParams openParams = matchWrap();
        openParams.setMargins(0, 10, 0, 0);
        storeCard.addView(openButton, openParams);

        LinearLayout.LayoutParams storeCardParams = matchWrap();
        storeCardParams.setMargins(0, 14, 0, 24);
        root.addView(storeCard, storeCardParams);

        TextView permissions = text(
                "Permisos de la semilla: Internet para descargar la release oficial y autorización de Android para instalar desde esta fuente. " +
                "No necesita almacenamiento, accesibilidad, overlay, contactos ni permisos globales sobre otras apps.",
                13f,
                Color.rgb(132, 150, 170));
        root.addView(permissions, matchWrap());

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 26, 28, 26);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(17, 45, 68));
        bg.setCornerRadius(34f);
        card.setBackground(bg);
        return card;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.14f);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void requestInstallPermission() {
        if (canInstallPackages()) return;
        permissionScreenOpened = true;
        status.setText("Android necesita que autorices ‘Permitir desde esta fuente’ para que la semilla pueda instalar StoreAMO.");
        try {
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        } catch (Exception e) {
            permissionScreenOpened = false;
            status.setText("Abrí Ajustes → Apps → Acceso especial → Instalar apps desconocidas y autorizá StoreAMO 0.0.1.");
        }
    }

    private void beginInstallFlow() {
        if (busy) return;
        if (!canInstallPackages()) {
            requestInstallPermission();
            return;
        }
        installLatestStore();
    }

    private boolean canInstallPackages() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return getPackageManager().canRequestPackageInstalls();
    }

    private void installLatestStore() {
        setBusy(true);
        status.setText("Buscando la release estable oficial…");
        new Thread(() -> {
            try {
                Release release = discoverLatestRelease();
                runOnUiThread(() -> status.setText("Descargando " + release.fileName + "…"));

                File apk = new File(getCacheDir(), release.fileName);
                download(release.apkUrl, apk);

                String sums = readText(release.sumsUrl);
                String sumDigest = hashForFile(sums, release.fileName);
                String expected = release.digest;
                if (expected != null && sumDigest != null && !expected.equalsIgnoreCase(sumDigest)) {
                    throw new SecurityException("los hashes publicados no coinciden");
                }
                if (expected == null) expected = sumDigest;
                if (expected == null || !expected.matches("[0-9a-fA-F]{64}")) {
                    throw new SecurityException("la release no publica un SHA-256 verificable");
                }

                runOnUiThread(() -> status.setText("Verificando SHA-256…"));
                String actual = sha256(apk);
                if (!expected.equalsIgnoreCase(actual)) {
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                    throw new SecurityException("SHA-256 incorrecto");
                }

                runOnUiThread(() -> status.setText("APK verificado. Android pedirá la confirmación final…"));
                commitPackageInstall(apk);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("No se pudo preparar StoreAMO: " + safeMessage(e) + ". No se instaló ningún APK.");
                });
            }
        }, "storeamo-seed").start();
    }

    private Release discoverLatestRelease() throws Exception {
        JSONObject root = new JSONObject(readText(RELEASE_API));
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true)) {
            throw new SecurityException("la release latest no es estable");
        }
        String tag = root.optString("tag_name", "");
        if (!tag.startsWith("v")) throw new SecurityException("tag estable inválido");

        String apkUrl = null;
        String fileName = null;
        String digest = null;
        String sumsUrl = null;
        JSONArray assets = root.optJSONArray("assets");
        if (assets == null) throw new IllegalStateException("release sin assets");

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            String url = asset.optString("browser_download_url", "");
            if (name.matches("StoreAMO-[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+\\.apk")) {
                apkUrl = url;
                fileName = name;
                String published = asset.optString("digest", "");
                if (published.startsWith("sha256:") && published.length() == 71) {
                    digest = published.substring(7).toLowerCase(Locale.ROOT);
                }
            } else if ("SHA256SUMS.txt".equals(name)) {
                sumsUrl = url;
            }
        }

        if (apkUrl == null || fileName == null || sumsUrl == null) {
            throw new IllegalStateException("APK o SHA256SUMS faltante");
        }
        requireOfficialDownload(apkUrl, tag);
        requireOfficialDownload(sumsUrl, tag);
        return new Release(fileName, apkUrl, sumsUrl, digest);
    }

    private void requireOfficialDownload(String value, String tag) {
        Uri uri = Uri.parse(value);
        String expectedPrefix = RELEASE_PREFIX + tag + "/";
        if (!"https".equals(uri.getScheme()) ||
                !"github.com".equals(uri.getHost()) ||
                uri.getPath() == null ||
                !uri.getPath().startsWith(expectedPrefix)) {
            throw new SecurityException("origen de descarga no oficial");
        }
    }

    private String readText(String value) throws Exception {
        HttpURLConnection connection = open(value);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
            }
            return out.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void download(String value, File destination) throws Exception {
        HttpURLConnection connection = open(value);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            long length = connection.getContentLengthLong();
            if (length > MAX_APK_BYTES) throw new SecurityException("APK demasiado grande");

            long total = 0L;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new FileOutputStream(destination, false)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_APK_BYTES) throw new SecurityException("APK demasiado grande");
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
            if (!destination.isFile() || destination.length() == 0L) {
                throw new IllegalStateException("descarga vacía");
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String value) throws Exception {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new SecurityException("sólo HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private String hashForFile(String sums, String fileName) {
        for (String line : sums.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.endsWith("  " + fileName) || trimmed.endsWith(" *" + fileName)) {
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length > 0 && parts[0].matches("[0-9a-fA-F]{64}")) {
                    return parts[0].toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private void commitPackageInstall(File apk) throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(STORE_PACKAGE);
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
        }

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            // Every Session.openWrite() stream MUST be closed before commit().
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
                session.fsync(output);
            }

            Intent result = new Intent(this, InstallResultReceiver.class);
            result.setAction(InstallResultReceiver.ACTION_INSTALL_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(this, sessionId, result, flags);
            session.commit(pending.getIntentSender());
        } catch (Exception e) {
            try {
                installer.abandonSession(sessionId);
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
            throw e;
        }

        runOnUiThread(() -> {
            setBusy(false);
            status.setText("Android recibió el APK verificado. Confirmá la instalación cuando aparezca la pantalla del sistema.");
        });
    }

    private String installedStoreVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(STORE_PACKAGE, 0);
            return info.versionName == null ? "instalada" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void openStore() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(STORE_PACKAGE);
        if (launch == null) {
            status.setText("StoreAMO todavía no está instalada o Android no puede abrirla.");
            return;
        }
        startActivity(launch);
    }

    private void refreshUi() {
        if (busy) return;
        String version = installedStoreVersion();
        boolean installed = version != null;
        installedState.setText(installed ? "StoreAMO " + version : "StoreAMO todavía no instalada");
        installButton.setText(installed ? "ACTUALIZAR STOREAMO" : "INSTALAR STOREAMO");
        openButton.setVisibility(installed ? View.VISIBLE : View.GONE);

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_STATUS, "");
        if (!canInstallPackages()) {
            status.setText("Paso único pendiente: autorizá esta semilla como fuente de instalación.");
        } else if (!saved.isEmpty()) {
            status.setText(saved);
        } else if (installed) {
            status.setText("Semilla lista. Podés comprobar e instalar la release estable actual.");
        } else {
            status.setText("Semilla lista para descargar y verificar StoreAMO.");
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        installButton.setEnabled(!value);
        openButton.setEnabled(!value);
    }

    private String safeMessage(Exception e) {
        if (e instanceof SecurityException) {
            return e.getMessage() == null ? "verificación de seguridad fallida" : e.getMessage();
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class Release {
        final String fileName;
        final String apkUrl;
        final String sumsUrl;
        final String digest;

        Release(String fileName, String apkUrl, String sumsUrl, String digest) {
            this.fileName = fileName;
            this.apkUrl = apkUrl;
            this.sumsUrl = sumsUrl;
            this.digest = digest;
        }
    }
}
