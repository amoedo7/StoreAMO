package com.desarrollamo.storeamo.bootstrap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
    private static final String USER_AGENT = "StoreAMO-Install/0.0.6";
    private static final String PREFS = "storeamo_install";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;

    private TextView status;
    private Button installButton;
    private Button openButton;
    private ProgressBar progress;
    private boolean waitingForSourcePermission;
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForSourcePermission && canInstallPackages()) {
            waitingForSourcePermission = false;
            installLatestStore();
            return;
        }
        refreshUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 64);
        root.setBackgroundColor(Color.rgb(7, 17, 31));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = text("DESARROLLAMO · INSTALADOR OFICIAL", 13f, Color.rgb(112, 190, 255));
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, matchWrap());

        TextView title = text("StoreAMO Install 0.0.6", 30f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, 14, 0, 18);
        root.addView(title, titleParams);

        TextView intro = text(
                "Instala StoreAMO desde cero. Descarga únicamente la release estable oficial, " +
                "comprueba SHA-256 y entrega el APK al instalador de paquetes de Android.",
                17f,
                Color.LTGRAY);
        intro.setGravity(Gravity.CENTER);
        root.addView(intro, matchWrap());

        TextView steps = text(
                "1  Autorizar este instalador como fuente\n" +
                "2  Descargar StoreAMO oficial\n" +
                "3  Verificar SHA-256\n" +
                "4  Confirmar la instalación en Android",
                16f,
                Color.WHITE);
        LinearLayout.LayoutParams stepsParams = matchWrap();
        stepsParams.setMargins(0, 34, 0, 20);
        root.addView(steps, stepsParams);

        status = text("Preparando instalador…", 16f, Color.rgb(186, 200, 214));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, 16, 0, 16);
        root.addView(status, statusParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, 4, 0, 20);
        root.addView(progress, progressParams);

        installButton = new Button(this);
        installButton.setText("INSTALAR STOREAMO");
        installButton.setAllCaps(false);
        installButton.setOnClickListener(v -> beginInstallFlow());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.setMargins(0, 8, 0, 12);
        root.addView(installButton, buttonParams);

        openButton = new Button(this);
        openButton.setText("ABRIR STOREAMO");
        openButton.setAllCaps(false);
        openButton.setOnClickListener(v -> openStore());
        root.addView(openButton, matchWrap());

        TextView safety = text(
                "Android conserva siempre la confirmación final. Este instalador no desactiva ni elude Play Protect.",
                13f,
                Color.rgb(145, 160, 176));
        safety.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams safetyParams = matchWrap();
        safetyParams.setMargins(0, 28, 0, 0);
        root.addView(safety, safetyParams);

        setContentView(scroll);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void beginInstallFlow() {
        if (busy) return;
        if (!canInstallPackages()) {
            waitingForSourcePermission = true;
            status.setText("Autorizá ‘Permitir desde esta fuente’ y volvé. La descarga continuará automáticamente.");
            try {
                Intent settings = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settings);
            } catch (Exception e) {
                waitingForSourcePermission = false;
                status.setText("Abrí Ajustes → Apps → Acceso especial → Instalar apps desconocidas y autorizá StoreAMO Install.");
            }
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

                runOnUiThread(() -> status.setText("APK verificado. Preparando instalador de Android…"));
                commitPackageInstall(apk);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("No se pudo preparar StoreAMO: " + safeMessage(e) + ". No se instaló ningún APK.");
                });
            }
        }, "storeamo-install").start();
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
        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
        }

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            // Android requires every stream returned by Session.openWrite() to be
            // fully closed before Session.commit(). 0.0.5 committed inside the
            // stream try-with-resources block, which produced "Files still open".
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
                // Best effort cleanup only.
            }
            throw e;
        }

        runOnUiThread(() -> {
            setBusy(false);
            status.setText("Android recibió el APK verificado. Confirmá la instalación cuando aparezca la pantalla del sistema.");
        });
    }

    private boolean isStoreInstalled() {
        try {
            getPackageManager().getPackageInfo(STORE_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
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
        boolean installed = isStoreInstalled();
        openButton.setVisibility(installed ? View.VISIBLE : View.GONE);
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_STATUS, "");
        if (!saved.isEmpty()) {
            status.setText(saved);
        } else if (!canInstallPackages()) {
            status.setText("Paso 1 pendiente: autorizá StoreAMO Install como fuente.");
        } else if (installed) {
            status.setText("StoreAMO ya está instalada. Podés abrirla o reinstalar la release estable.");
        } else {
            status.setText("Listo para descargar y verificar StoreAMO.");
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        installButton.setEnabled(!value);
        openButton.setEnabled(!value);
    }

    private String safeMessage(Exception e) {
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
