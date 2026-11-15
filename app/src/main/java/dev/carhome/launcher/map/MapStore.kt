package dev.carhome.launcher.map

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import dev.carhome.launcher.util.CrashLog

/**
 * The offline map: one .pmtiles file (OpenStreetMap data for your region) kept inside the app.
 * You make it once on your computer, copy it to a USB stick, and import it from Settings.
 */
object MapStore {

    fun file(context: Context) = File(context.filesDir, "maps/region.pmtiles")

    fun isInstalled(context: Context) = file(context).let { it.exists() && it.length() > 0 }

    fun sizeMb(context: Context): Long = file(context).length() / (1024 * 1024)

    /** The dark style with the map file plugged in. */
    fun styleJson(context: Context): String {
        val template = context.assets.open("map/style.json").bufferedReader().use { it.readText() }
        return template.replace("__PMTILES_URL__", "pmtiles://file://${file(context).absolutePath}")
    }

    sealed interface ImportResult {
        data object Ok : ImportResult
        data object NotAMap : ImportResult
        data class NoSpace(val neededMb: Long) : ImportResult
        data class Failed(val reason: String) : ImportResult
    }

    /**
     * Copies a picked .pmtiles file into the app, reporting progress 0..1 on the main thread.
     * Never throws: every problem comes back as an [ImportResult] the screen can show.
     */
    suspend fun import(context: Context, uri: Uri, sizeHint: Long = -1L, onProgress: (Float) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val target = file(context)
            val dir = target.parentFile ?: return@withContext ImportResult.Failed("No storage folder")
            val temp = File(dir, "import.tmp")
            try {
                dir.mkdirs()
                val resolver = context.contentResolver

                // Some file managers can't report a file's size; then we just copy without a percentage.
                val total = if (sizeHint > 0) sizeHint else try {
                    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                    } ?: -1L
                } catch (e: Exception) {
                    -1L
                }

                // The new file is copied next to the old one before replacing it.
                if (total > 0 && dir.usableSpace < total + 32L * 1024 * 1024) {
                    return@withContext ImportResult.NoSpace(total / (1024 * 1024))
                }

                val input = resolver.openInputStream(uri)
                    ?: return@withContext ImportResult.Failed("Couldn't open the file")
                input.use { inp ->
                    FileOutputStream(temp).use { out ->
                        val buffer = ByteArray(256 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = inp.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) { onProgress(percent / 100f) }
                                }
                            }
                        }
                    }
                }

                // A real PMTiles file starts with the bytes "PMTiles".
                val header = temp.inputStream().use { s -> ByteArray(7).also { s.read(it) } }
                if (String(header, Charsets.US_ASCII) != "PMTiles") {
                    temp.delete()
                    return@withContext ImportResult.NotAMap
                }
                target.delete()
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return@withContext ImportResult.Failed("Couldn't save the map")
                }
                MapGuard.reset(context) // a new map gets a fresh chance to start
                ImportResult.Ok
            } catch (e: Exception) {
                temp.delete()
                CrashLog.record(context, e)
                ImportResult.Failed(e.javaClass.simpleName)
            }
        }

    fun remove(context: Context) {
        file(context).delete()
        MapGuard.reset(context)
    }
}
