package dev.carhome.launcher.map

import android.content.Context

/**
 * Stops a broken map from crashing Car Home over and over.
 *
 * Just before the map engine starts we write "starting"; once the map has drawn,
 * we clear it. If Car Home is opened and the mark is still there, the last start
 * crashed, so the home screen shows a "Map couldn't start" message instead of
 * trying again on its own. Tapping it (or importing a new map) gives it another try.
 *
 * Written with commit() rather than apply(), so the mark is on disk before the
 * engine starts even if the app then dies straight away.
 */
object MapGuard {
    private const val PREFS = "map_guard"
    private const val KEY = "starting"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True if the previous attempt to start the map never finished. */
    fun lastStartFailed(context: Context) = prefs(context).getBoolean(KEY, false)

    fun markStarting(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).commit()
    }

    fun markStarted(context: Context) {
        prefs(context).edit().putBoolean(KEY, false).commit()
    }

    fun reset(context: Context) = markStarted(context)
}
