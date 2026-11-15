package dev.carhome.launcher.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object Launch {

    /** Opens an app. Returns false if no app is set or it isn't installed. */
    fun app(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent, showError = true)
    }

    /** Opens a system settings screen, falling back to the main settings. */
    fun settings(context: Context, action: String) {
        val opened = start(context, Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), showError = false)
        if (!opened) {
            start(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), showError = true)
        }
    }

    fun label(context: Context, pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun start(context: Context, intent: Intent, showError: Boolean): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        if (showError) Toast.makeText(context, "Couldn't open that on this unit", Toast.LENGTH_SHORT).show()
        false
    }
}
