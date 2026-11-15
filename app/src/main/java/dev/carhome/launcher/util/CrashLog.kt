package dev.carhome.launcher.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last error on the unit so it can be read in Settings → Diagnostics
 * (and photographed), instead of needing a computer and cable to see what went wrong.
 * Stays on the unit; nothing is sent anywhere.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_error.txt")

    /** Catches any crash, saves it, then lets Android show its usual "keeps stopping" box. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable) {
        try {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "?"
            }
            file(context).writeText(
                "Car Home $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "$time (unit clock)\n\n" +
                    trace.take(6_000)
            )
        } catch (e: Exception) {
            // Never let the crash logger crash.
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}
