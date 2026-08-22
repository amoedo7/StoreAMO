package com.desarrollamo.storeamo.bootstrap;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BootstrapActivity extends Activity {
    private static final String RELEASES_URL = "https://api.github.com/repos/amoedo7/StoreAMO/releases?per_page=50";
    private static final String EXPECTED_PACKAGE = "com.desarrollamo.storeamo";
    private static final int MIN_STABLE_MAJOR = 0;
    private static final int MIN_STABLE_MINOR = 5;
    private static final int MIN_STABLE_PATCH = 0;
    private static final int BG = Color.rgb(6, 16, 28);
    private static final int SURFACE = Color.rgb(16, 43, 65);
    private static final int TEXT = Color.rgb(246, 248, 252);
    private static final int MUTED = Color.rgb(169, 183, 201);
    private static final int CYAN = Color.rgb(103, 210, 255);
    private static final int GREEN = Color.rgb(114, 224, 166);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView installedText;
    private TextView statusText;
    private TextView detailText;
    private Button actionButton;
    private ProgressBar progressBar;

    private UpdateRelease latest;
    private File readyApk;
    private long downloadId = -1L;
    private boolean installCommitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        checkLatest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (readyApk != null && readyApk.isFile() && canInstallPackages() && !installCommitted) {
            installReadyApk();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.desarrollamo.storeamo.bootstrap.R.drawable.ic_storeamo);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconLp.setMarginEnd(dp(14));
        brand.addView(icon, iconLp);
        TextView brandText = text("StoreAMO", 31, TEXT, true);
        brand.addView(brandText, new LinearLayout.LayoutParams(-2, -2));
        root.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        TextView eyebrow = text("VERSIONES", 13, CYAN, true);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-2, -2);
        eyebrowLp.topMargin = dp(42);
        root.addView(eyebrow, eyebrowLp);

        TextView title = text("Actualizaciones", 42, TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(18);
        root.addView(title, titleLp);

        TextView subtitle = text("Esta primera StoreAMO sólo tiene una misión: encontrar la versión completa más nueva y actualizarse.", 18, MUTED, false);
        subtitle.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(14);
        subtitleLp.bottomMargin = dp(28);
        root.addView(subtitle, subtitleLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(roundRect(SURFACE, 28));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(text("StoreAMO", 27, TEXT, true));
        installedText = text("Instalada · 0.0.1", 16, GREEN, false);
        LinearLayout.LayoutParams installedLp = new LinearLayout.LayoutParams(-1, -2);
        installedLp.topMargin = dp(16);
        card.addView(installedText, installedLp);

        statusText = text("Buscando la última versión…", 17, GREEN, true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        card.addView(statusText, statusLp);

        detailText = text("Consulta únicamente las Releases oficiales de amoedo7/StoreAMO por HTTPS.", 14, MUTED, false);
        detailText.setLineSpacing(0f, 1.3f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(10);
        card.addView(detailText, detailLp);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(8));
        progressLp.topMargin = dp(22);
        card.addView(progressBar, progressLp);

        actionButton = new Button(this);
        actionButton.setText("BUSCAR ACTUALIZACIÓN");
        actionButton.setTextColor(BG);
        actionButton.setTextSize(15);
        actionButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        actionButton.setAllCaps(false);
        actionButton.setBackground(roundRect(CYAN, 18));
        actionButton.setOnClickListener(v -> {
            if (latest == null) checkLatest();
            else startDownload();
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(54));
        buttonLp.topMargin = dp(24);
        card.addView(actionButton, buttonLp);

        TextView footer = text("0.0.1 es el punto de entrada. Después de instalar la StoreAMO completa, este código desaparece reemplazado por la aplicación real.", 14, MUTED, false);
        footer.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(28);
        root.addView(footer, footerLp);

        setContentView(scroll);
    }

    private void checkLatest() {
        latest = null;
        actionButton.setEnabled(false);
        actionButton.setText("BUSCANDO…");
        progressBar.setVisibility(View.GONE);
        status("Buscando la última versión…", "Revisando las Releases oficiales de StoreAMO.", GREEN);

        io.execute(() -> {
            try {
                UpdateRelease found = fetchLatestStableRelease();
                latest = found;
                runOnUiThread(() -> {
                    if (found == null) {
                        status("Todavía no hay una versión estable nueva", "La 0.0.1 volverá a consultar GitHub cada vez que la abras.", MUTED);
                        actionButton.setEnabled(true);
                        actionButton.setText("BUSCAR DE NUEVO");
                    } else {
                        status("Actualización disponible · " + found.version.text,
                                "Se verificará SHA-256 antes de entregar el APK al instalador de Android.", GREEN);
                        actionButton.setEnabled(true);
                        actionButton.setText("ACTUALIZAR STORE​AMO");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status("No pude consultar GitHub", safeMessage(error), Color.rgb(255, 183, 109));
                    actionButton.setEnabled(true);
                    actionButton.setText("REINTENTAR");
                });
            }
        });
    }

    private UpdateRelease fetchLatestStableRelease() throws Exception {
        JSONArray releases = new JSONArray(getText(RELEASES_URL, "application/vnd.github+json"));
        JSONObject bestRelease = null;
        JSONObject bestApk = null;
        JSONObject bestSums = null;
        ReleaseVersion bestVersion = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft", false)) continue;
            ReleaseVersion version = ReleaseVersion.parseTag(release.optString("tag_name"));
            if (version == null || !version.isAtLeast(MIN_STABLE_MAJOR, MIN_STABLE_MINOR, MIN_STABLE_PATCH)) continue;
            if (bestVersion != null && version.compareTo(bestVersion) <= 0) continue;

            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            JSONObject apk = null;
            JSONObject sums = null;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                String name = asset.optString("name");
                if (name.startsWith("StoreAMO-") && name.endsWith(".apk")) apk = asset;
                if ("SHA256SUMS.txt".equals(name)) sums = asset;
            }
            if (apk == null) continue;
            String url = apk.optString("browser_download_url");
            if (!url.startsWith("https://github.com/amoedo7/StoreAMO/releases/download/")) continue;

            bestRelease = release;
            bestApk = apk;
            bestSums = sums;
            bestVersion = version;
        }

        if (bestRelease == null || bestApk == null || bestVersion == null) return null;

        String apkName = bestApk.optString("name");
        String sha = bestApk.optString("digest").toLowerCase(Locale.ROOT);
        if (sha.startsWith("sha256:")) sha = sha.substring("sha256:".length());
        if (!isSha256(sha) && bestSums != null) {
            String sums = getText(bestSums.optString("browser_download_url"), "text/plain");
            sha = checksumFor(sums, apkName);
        }
        if (!isSha256(sha)) throw new IllegalStateException("La Release no publica un SHA-256 verificable");

        return new UpdateRelease(
                bestVersion,
                bestApk.optString("browser_download_url"),
                sha,
                bestApk.optLong("size", -1L),
                bestRelease.optString("html_url")
        );
    }

    private void startDownload() {
        UpdateRelease release = latest;
        if (release == null) return;
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new IllegalStateException("No hay almacenamiento disponible");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No pude preparar la carpeta de descargas");
            File file = new File(dir, "StoreAMO-" + release.version.text + ".apk");
            if (file.exists()) file.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.url))
                    .setTitle("StoreAMO " + release.version.text)
                    .setDescription("Descarga oficial · se verificará SHA-256")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(file));
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            downloadId = manager.enqueue(request);
            readyApk = file;
            actionButton.setEnabled(false);
            actionButton.setText("DESCARGANDO…");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            status("Descargando " + release.version.text + "…", "Podés quedarte en esta pantalla; al terminar verificamos el archivo.", GREEN);
            pollDownload();
        } catch (Exception error) {
            readyApk = null;
            status("No pude iniciar la descarga", safeMessage(error), Color.rgb(255, 183, 109));
            actionButton.setEnabled(true);
            actionButton.setText("REINTENTAR");
        }
    }

    private void pollDownload() {
        if (downloadId < 0L) return;
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        try (android.database.Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (!cursor.moveToFirst()) {
                failDownload("Android perdió el seguimiento de la descarga");
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long current = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (total > 0L && current >= 0L) {
                progressBar.setProgress((int) Math.min(100L, current * 100L / total));
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                verifyDownload();
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                failDownload("La descarga falló");
                return;
            }
        } catch (Exception error) {
            failDownload(safeMessage(error));
            return;
        }
        main.postDelayed(this::pollDownload, 700L);
    }

    private void verifyDownload() {
        final UpdateRelease release = latest;
        final File file = readyApk;
        if (release == null || file == null) return;
        status("Verificando integridad…", "Comparando SHA-256 con la Release oficial.", GREEN);
        progressBar.setIndeterminate(true);

        io.execute(() -> {
            try {
                String actual = sha256(file);
                if (!actual.equalsIgnoreCase(release.sha256)) {
                    file.delete();
                    throw new SecurityException("El SHA-256 no coincide; el APK fue bloqueado");
                }
                PackageInfo archive = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (archive == null || !EXPECTED_PACKAGE.equals(archive.packageName)) {
                    file.delete();
                    throw new SecurityException("El APK no pertenece a la identidad estable de StoreAMO");
                }
                runOnUiThread(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(100);
                    status("Descarga verificada", "SHA-256 correcto · continuando al instalador de Android.", GREEN);
                    if (canInstallPackages()) installReadyApk();
                    else requestInstallPermission();
                });
            } catch (Exception error) {
                runOnUiThread(() -> failDownload(safeMessage(error)));
            }
        });
    }

    private void requestInstallPermission() {
        actionButton.setEnabled(true);
        actionButton.setText("PERMITIR INSTALACIÓN");
        actionButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        status("Android necesita tu permiso", "Permití instalar apps desde StoreAMO. Al volver, continuamos automáticamente.", Color.rgb(255, 214, 115));
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void installReadyApk() {
        File file = readyApk;
        if (file == null || !file.isFile() || installCommitted) return;
        try {
            PackageInfo archive = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (archive == null || !EXPECTED_PACKAGE.equals(archive.packageName)) {
                throw new SecurityException("El APK no pertenece a StoreAMO");
            }

            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(EXPECTED_PACKAGE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            }
            int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId);
                 InputStream input = new FileInputStream(file);
                 java.io.OutputStream output = session.openWrite("base.apk", 0, file.length())) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
                session.fsync(output);

                Intent callback = new Intent(this, InstallResultReceiver.class)
                        .setAction(InstallResultReceiver.ACTION_INSTALL_STATUS);
                int mutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
                PendingIntent pending = PendingIntent.getBroadcast(
                        this,
                        sessionId,
                        callback,
                        PendingIntent.FLAG_UPDATE_CURRENT | mutable
                );
                installCommitted = true;
                status("Listo para instalar", "Android te mostrará su confirmación de seguridad.", GREEN);
                session.commit(pending.getIntentSender());
            }
        } catch (Exception error) {
            installCommitted = false;
            status("No pude abrir el instalador", safeMessage(error), Color.rgb(255, 183, 109));
            actionButton.setEnabled(true);
            actionButton.setText("REINTENTAR INSTALACIÓN");
            actionButton.setOnClickListener(v -> installReadyApk());
        }
    }

    private void failDownload(String message) {
        main.removeCallbacksAndMessages(null);
        downloadId = -1L;
        if (readyApk != null && readyApk.exists()) readyApk.delete();
        readyApk = null;
        progressBar.setIndeterminate(false);
        progressBar.setVisibility(View.GONE);
        status("Actualización detenida", message, Color.rgb(255, 183, 109));
        actionButton.setEnabled(true);
        actionButton.setText("REINTENTAR");
        actionButton.setOnClickListener(v -> {
            if (latest == null) checkLatest(); else startDownload();
        });
    }

    private String getText(String url, String accept) throws Exception {
        if (url == null || !url.startsWith("https://")) throw new SecurityException("URL no segura");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "StoreAMO-Bootstrap/0.0.1");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException("GitHub respondió HTTP " + code);
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String checksumFor(String sums, String fileName) {
        if (sums == null || fileName == null) return "";
        for (String raw : sums.split("\\R")) {
            String line = raw.trim();
            if (!line.endsWith(fileName)) continue;
            String[] pieces = line.split("\\s+");
            if (pieces.length > 0 && isSha256(pieces[0])) return pieces[0].toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("^[0-9a-fA-F]{64}$");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private void status(String headline, String detail, int color) {
        statusText.setText(headline);
        statusText.setTextColor(color);
        detailText.setText(detail);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Error inesperado" : message;
    }

    private static final class UpdateRelease {
        final ReleaseVersion version;
        final String url;
        final String sha256;
        final long size;
        final String releaseUrl;

        UpdateRelease(ReleaseVersion version, String url, String sha256, long size, String releaseUrl) {
            this.version = version;
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
            this.releaseUrl = releaseUrl;
        }
    }
}
