package com.desarrollamo.storeamo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.desarrollamo.storeamo.data.SelfUpdateRepository
import com.desarrollamo.storeamo.util.DownloadInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compatibility launcher for StoreAMO.
 *
 * Normal 0.4.3.x builds route to the current StoreAMO UI. The immutable
 * 0.0.1 bootstrap build uses the same package/signing identity, discovers the
 * latest verified StoreAMO release, downloads it through the verified install
 * flow and lets Android perform the in-place update.
 */
class BootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.BOOTSTRAP_SEED) {
            startBootstrapSeed()
            return
        }

        val prefs = getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MIGRATION_KEY, false)) {
            prefs.edit()
                .putBoolean("verified_only", false)
                .putBoolean(MIGRATION_KEY, true)
                .apply()
        }

        startActivity(Intent(this, MainActivityV4::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun startBootstrapSeed() {
        val status = TextView(this).apply {
            text = "StoreAMO 0.0.1\nBuscando la versión actual…"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }
        val progress = ProgressBar(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(progress)
            addView(status)
        }
        setContentView(layout)

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SelfUpdateRepository.fetchLatest() }
                    ?: error("No se encontró una versión estable más nueva")
            }.onSuccess { artifact ->
                status.text = "StoreAMO ${artifact.version} encontrado.\nDescargando y verificando…"
                runCatching { DownloadInstaller.start(this@BootstrapActivity, "StoreAMO", artifact) }
                    .onFailure { error ->
                        progress.visibility = android.view.View.GONE
                        status.text = "No pude iniciar la actualización.\n${error.message.orEmpty()}\n\nCerrá y abrí StoreAMO para reintentar."
                    }
            }.onFailure { error ->
                progress.visibility = android.view.View.GONE
                status.text = "No pude comprobar la versión actual.\n${error.message.orEmpty()}\n\nCerrá y abrí StoreAMO para reintentar."
            }
        }
    }

    private companion object {
        const val MIGRATION_KEY = "candidate_downloads_migration_v1"
    }
}
