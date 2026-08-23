package com.desarrollamo.storeamo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Compatibility launcher for the 0.4.3.x line.
 *
 * Keeps candidate downloads available for existing installs and routes the
 * launcher to the community-aware StoreAMO UI introduced in 0.4.3.75.
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

        startActivity(Intent(this, MainActivityV4::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private companion object {
        const val MIGRATION_KEY = "candidate_downloads_migration_v1"
    }
}
