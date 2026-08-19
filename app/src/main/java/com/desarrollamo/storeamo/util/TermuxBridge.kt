package com.desarrollamo.storeamo.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Narrow integration point with Termux.
 *
 * StoreAMO intentionally does not scan every installed package. It only asks
 * Android about the specific integration declared in AndroidManifest.xml.
 * Running scripts is a separate, explicit user action and must only be enabled
 * for StoreAMO-verified script artifacts.
 */
object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun open(context: Context): Boolean {
        val intent: Intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
