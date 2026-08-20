package com.desarrollamo.storeamo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * One-time compatibility launcher for the 0.4.3.x line.
 *
 * Older StoreAMO installs defaulted `verified_only` to true, which leaves every
 * candidate visible but disables its install button. DesarrollAMO currently
 * publishes new apps as candidates first, so an upgraded install could look as
 * if downloads were broken even when the public artifact was valid.
 *
 * This migration runs once per installation, opts the existing user into
 * candidate downloads, and then hands control to the normal StoreAMO UI. The
 * setting remains user-controllable afterwards from Ajustes.
 */
class BootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MIGRATION_KEY, false)) {
            prefs.edit()
                .putBoolean("verified_only", false)
                .putBoolean(MIGRATION_KEY, true)
                .apply()
        }

        startActivity(Intent(this, MainActivityV3::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private companion object {
        const val MIGRATION_KEY = "candidate_downloads_migration_v1"
    }
}
