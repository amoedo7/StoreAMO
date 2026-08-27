package com.desarrollamo.storeamo.bootstrap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private TextView status;
    private Button action;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 64, 48, 64);
        root.setBackgroundColor(Color.rgb(7, 17, 31));

        TextView title = new TextView(this);
        title.setText("StoreAMO");
        title.setTextSize(32f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("Instalador oficial de DesarrollAMO\n\nEsta app no instala ni elimina aplicaciones. Sólo abre la descarga oficial de la Store actual en Android.");
        status.setTextSize(17f);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 36, 0, 28);
        root.addView(status, sp);

        progress = new ProgressBar(this);
        progress.setVisibility(ProgressBar.GONE);
        root.addView(progress);

        action = new Button(this);
        action.setText("Continuar");
        action.setOnClickListener(v -> discoverStableRelease());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 28, 0, 0);
        root.addView(action, bp);
        setContentView(root);
    }

    private void discoverStableRelease() {
        action.setEnabled(false);
        progress.setVisibility(ProgressBar.VISIBLE);
        status.setText("Buscando la versión estable oficial…");
        new Thread(() -> {
            try {
                URL api = new URL("https://api.github.com/repos/amoedo7/StoreAMO/releases/latest");
                HttpURLConnection c = (HttpURLConnection) api.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "StoreAMO-Bootstrap/0.0.4");
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
                StringBuilder json = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line; while ((line = r.readLine()) != null) json.append(line);
                }
                Matcher m = Pattern.compile("\\\"browser_download_url\\\":\\\"(https://github\\.com/amoedo7/StoreAMO/releases/download/[^\\\"]+/StoreAMO-[^\\\"]+\\.apk)\\\"").matcher(json);
                if (!m.find()) throw new IllegalStateException("APK estable no encontrado");
                String apk = m.group(1);
                runOnUiThread(() -> openOfficialDownload(apk));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(ProgressBar.GONE);
                    action.setEnabled(true);
                    status.setText("No pude localizar la Store oficial. Comprobá tu conexión y volvé a intentar.");
                });
            }
        }).start();
    }

    private void openOfficialDownload(String apk) {
        progress.setVisibility(ProgressBar.GONE);
        Uri uri = Uri.parse(apk);
        if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || !uri.getPath().startsWith("/amoedo7/StoreAMO/releases/download/")) {
            action.setEnabled(true);
            status.setText("La descarga fue rechazada porque no pertenece al canal oficial.");
            return;
        }
        status.setText("Android abrirá la descarga oficial. Cuando termine, tocá el APK y seguí el instalador del sistema.");
        Intent browser = new Intent(Intent.ACTION_VIEW, uri);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(browser);
        action.setEnabled(true);
    }
}
