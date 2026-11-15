package dev.carhome.launcher.map

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    /** Copies a picked .pmtiles file into the app, reporting progress 0..1. */
    suspend fun import(context: Context, uri: Uri, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val total = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L

            val target = file(context)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "import.tmp")
            try {
                val input = resolver.openInputStream(uri) ?: return@withContext false
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
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
                // A real PMTiles file starts with the bytes "PMTiles".
                val header = temp.inputStream().use { s -> ByteArray(7).also { s.read(it) } }
                if (String(header) != "PMTiles") {
                    temp.delete()
                    return@withContext false
                }
                target.delete()
                temp.renameTo(target)
            } catch (e: Exception) {
                temp.delete()
                false
            }
        }
}
