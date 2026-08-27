package com.desarrollamo.storeamo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
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
 * Normal 0.4.3.x builds route to the current StoreAMO UI. The bootstrap seed
 * behaves like a first-run installer: it asks Android for the one-time
 * "install unknown apps" authorization before downloading anything, then
 * discovers the current stable StoreAMO and hands the verified APK to the
 * StoreAMO install pipeline.
 */
class BootstrapActivity : ComponentActivity() {
    private lateinit var seedStatus: TextView
    private lateinit var seedProgress: ProgressBar
    private lateinit var seedAction: Button
    private var bootstrapUpdateStarted = false

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

    override fun onResume() {
        super.onResume()
        if (BuildConfig.BOOTSTRAP_SEED && ::seedStatus.isInitialized) {
            refreshBootstrapState()
        }
    }

    private fun startBootstrapSeed() {
        val title = TextView(this).apply {
            text = "StoreAMO"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 12)
        }
        val intro = TextView(this).apply {
            text = "Instalador de DesarrollAMO\n\nLa primera vez Android necesita autorizar a StoreAMO para instalar y actualizar aplicaciones. Se hace una sola vez."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 12, 48, 24)
        }
        seedStatus = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
        }
        seedProgress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        seedAction = Button(this).apply {
            text = "Autorizar StoreAMO"
            visibility = View.GONE
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            addView(title)
            addView(intro)
            addView(seedProgress)
            addView(seedStatus)
            addView(seedAction)
        }
        setContentView(layout)
        refreshBootstrapState()
    }

    private fun refreshBootstrapState() {
        if (!DownloadInstaller.canInstallPackages(this)) {
            bootstrapUpdateStarted = false
            seedProgress.visibility = View.GONE
            seedStatus.text = "1 de 3 · Falta una autorización de Android\n\nActivá “Permitir desde esta fuente” y volvé. StoreAMO continuará solo."
            seedAction.apply {
                text = "Autorizar StoreAMO"
                visibility = View.VISIBLE
                setOnClickListener {
                    runCatching { DownloadInstaller.openInstallPermission(this@BootstrapActivity) }
                        .onFailure { error ->
                            seedStatus.text = "Android no pudo abrir la autorización.\n${error.message.orEmpty()}"
                        }
                }
            }
            return
        }

        seedAction.visibility = View.GONE
        if (bootstrapUpdateStarted) return
        bootstrapUpdateStarted = true
        seedProgress.visibility = View.VISIBLE
        seedStatus.text = "✓ 1 de 3 · StoreAMO autorizado\n2 de 3 · Buscando la versión actual…"

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SelfUpdateRepository.fetchLatest() }
                    ?: error("No se encontró una versión estable más nueva")
            }.onSuccess { artifact ->
                seedStatus.text = "✓ 1 de 3 · StoreAMO autorizado\n✓ 2 de 3 · Versión ${artifact.version} encontrada\n3 de 3 · Descargando y verificando…"
                runCatching { DownloadInstaller.start(this@BootstrapActivity, "StoreAMO", artifact) }
                    .onFailure { error -> showRetry(error.message.orEmpty()) }
            }.onFailure { error ->
                showRetry(error.message.orEmpty())
            }
        }
    }

    private fun showRetry(detail: String) {
        bootstrapUpdateStarted = false
        seedProgress.visibility = View.GONE
        seedStatus.text = "No pude completar la preparación.\n${detail.ifBlank { "Android no informó un detalle." }}"
        seedAction.apply {
            text = "Reintentar"
            visibility = View.VISIBLE
            setOnClickListener { refreshBootstrapState() }
        }
    }

    private companion object {
        const val MIGRATION_KEY = "candidate_downloads_migration_v1"
    }
}
