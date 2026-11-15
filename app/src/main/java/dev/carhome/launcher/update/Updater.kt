package dev.carhome.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer Car Home, downloads the APK and hands it to
 * Android's installer. The update installs over the current app and keeps all settings,
 * as long as it's signed with the same key.
 */
object Updater {

    /**
     * Where releases are published, as "owner/repository".
     * The repository must be PUBLIC: GitHub hides private releases from the app.
     */
    const val REPO = "melkorashyux/CarHome"

    sealed interface Check {
        data class Available(val version: String, val url: String) : Check
        data class UpToDate(val version: String) : Check
        data class Failed(val message: String) : Check
    }

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            when (conn.responseCode) {
                200 -> Unit
                404 -> return@withContext Check.Failed("No releases found")
                else -> return@withContext Check.Failed("GitHub error ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val latest = json.getString("tag_name").trimStart('v', 'V')

            var apkUrl: String? = null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            val current = currentVersion(context)
            when {
                !isNewer(latest, current) -> Check.UpToDate(current)
                apkUrl == null -> Check.Failed("Release $latest has no APK attached")
                else -> Check.Available(latest, apkUrl)
            }
        } catch (e: IOException) {
            Check.Failed("No internet connection")
        } catch (e: Exception) {
            Check.Failed("Couldn't read the latest release")
        }
    }

    /** Downloads the APK into the app's cache. Returns null if it fails. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "update.apk")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                val total = conn.contentLengthLong
                var lastPercent = -1
                conn.inputStream.use { input ->
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
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
                out
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Opens Android's installer for the downloaded APK. The first time, Android asks
     * you to allow Car Home to install apps; after allowing, tap Install again.
     */
    fun install(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Allow Car Home to install updates, then tap Install again", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't open that setting on this unit", Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't start the installer", Toast.LENGTH_SHORT).show()
        }
    }

    /** "0.10" is newer than "0.9"; compares each number in turn. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.').map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
