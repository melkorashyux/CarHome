package dev.carhome.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.carhome.launcher.util.CrashLog
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object Wallpaper {
    fun file(context: Context) = File(context.filesDir, "wallpaper.jpg")

    /**
     * Copies a picked photo into the app, upright and resized. It isn't cropped here:
     * the screen zooms it to fill edge to edge, whatever the screen's shape.
     */
    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 900) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext false

            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val upright = if (rotation != 0) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, true)
            } else decoded

            FileOutputStream(file(context)).use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (e: Throwable) {
            // Includes running out of memory on a huge photo: report it instead of crashing.
            CrashLog.record(context, e)
            false
        }
    }

    /** Your chosen photo if there is one, otherwise the built-in default wallpaper. */
    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context)
        val bitmap = if (f.exists()) {
            BitmapFactory.decodeFile(f.path)
        } else {
            defaultResId(context)?.let { id ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, id, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 900) sample *= 2
                BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
        bitmap
    }

    /**
     * The default wallpaper: app/src/main/res/drawable-nodpi/wallpaper_default.jpg
     * Looked up by name so the app still builds if the file isn't there yet.
     */
    @Suppress("DiscouragedApi")
    fun defaultResId(context: Context): Int? =
        context.resources.getIdentifier("wallpaper_default", "drawable", context.packageName).takeIf { it != 0 }

    fun hasCustom(context: Context) = file(context).exists()

    /** Forgets your chosen photo so the default wallpaper shows again. */
    suspend fun resetToDefault(context: Context) = withContext(Dispatchers.IO) {
        file(context).delete()
    }

    /**
     * The frosted version used behind every glass panel. It's made once at a small size
     * (cheap) and stretched to full screen when drawn, which also smooths it further.
     * [intensity] 0..1 is the Settings slider.
     */
    suspend fun blurred(source: Bitmap, intensity: Float): ImageBitmap = withContext(Dispatchers.Default) {
        val height = (256f * source.height / source.width).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, 256, height, true)
        Blur.boxBlur(small, 2 + (intensity * 10).roundToInt()).asImageBitmap()
    }

}
