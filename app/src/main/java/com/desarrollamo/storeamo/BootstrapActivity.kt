package com.desarrollamo.storeamo

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compatibility launcher for StoreAMO.
 *
 * The bootstrap seed deliberately does NOT request Android's
 * REQUEST_INSTALL_PACKAGES permission. Play Protect can classify sideloaded
 * apps carrying that capability as harmful. The seed only discovers the
 * verified stable StoreAMO release and hands its official HTTPS URL to the
 * user's browser/system download surface.
 */
class BootstrapActivity : ComponentActivity() {
    private lateinit var seedStatus: TextView
    private lateinit var seedProgress: ProgressBar
    private lateinit var seedAction: Button
    private var stableUrl: String? = null
    private var externalOpened = false

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
        if (BuildConfig.BOOTSTRAP_SEED && externalOpened && ::seedStatus.isInitialized) {
            seedProgress.visibility = View.GONE
            seedStatus.text = "Descarga abierta en Android.\n\nCuando termine, tocá el APK descargado y elegí Instalar. Esta semilla no solicita permisos para instalar otras aplicaciones."
            seedAction.apply {
                text = "Abrir descarga otra vez"
                visibility = View.VISIBLE
                setOnClickListener { openStableDownload() }
            }
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
            text = "Instalador seguro de DesarrollAMO\n\nEsta versión inicial no pide permiso para instalar otras apps. Primero localiza la StoreAMO oficial y después Android completa la instalación de forma visible."
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
            visibility = View.VISIBLE
        }
        seedAction = Button(this).apply {
            text = "Descargar StoreAMO actual"
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
        discoverStable()
    }

    private fun discoverStable() {
        seedProgress.visibility = View.VISIBLE
        seedAction.visibility = View.GONE
        seedStatus.text = "1 de 2 · Comprobando la versión oficial…"

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SelfUpdateRepository.fetchLatest() }
                    ?: error("No se encontró una versión estable más nueva")
            }.onSuccess { artifact ->
                val url = artifact.url
                require(url.startsWith(OFFICIAL_RELEASE_PREFIX)) { "Origen de descarga inesperado" }
                stableUrl = url
                seedProgress.visibility = View.GONE
                seedStatus.text = "✓ 1 de 2 · StoreAMO ${artifact.version} verificada\n2 de 2 · Android abrirá la descarga oficial."
                seedAction.apply {
                    text = "Descargar StoreAMO ${artifact.version}"
                    visibility = View.VISIBLE
                    setOnClickListener { openStableDownload() }
                }
                openStableDownload()
            }.onFailure { error ->
                seedProgress.visibility = View.GONE
                seedStatus.text = "No pude localizar StoreAMO.\n${error.message.orEmpty()}"
                seedAction.apply {
                    text = "Reintentar"
                    visibility = View.VISIBLE
                    setOnClickListener { discoverStable() }
                }
            }
        }
    }

    private fun openStableDownload() {
        val url = stableUrl ?: return
        runCatching {
            require(url.startsWith(OFFICIAL_RELEASE_PREFIX))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(intent)
            externalOpened = true
        }.onFailure { error ->
            seedStatus.text = "Android no pudo abrir la descarga oficial.\n${error.message.orEmpty()}"
        }
    }

    private companion object {
        const val MIGRATION_KEY = "candidate_downloads_migration_v1"
        const val OFFICIAL_RELEASE_PREFIX = "https://github.com/amoedo7/StoreAMO/releases/download/"
    }
}
