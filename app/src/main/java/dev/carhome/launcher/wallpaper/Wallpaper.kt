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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object Wallpaper {
    /** The design canvas is 1024×600, so every wallpaper is cropped to that shape. */
    private const val ASPECT = 1024f / 600f

    fun file(context: Context) = File(context.filesDir, "wallpaper.jpg")

    /** Copies a picked photo into the app, upright, cropped and resized. */
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

            val cropped = centerCrop(upright)
            FileOutputStream(file(context)).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.let(::centerCrop)
    }

    /**
     * The frosted version used behind every glass panel. It's made once at a small size
     * (cheap) and stretched to full screen when drawn, which also smooths it further.
     * [intensity] 0..1 is the Settings slider.
     */
    suspend fun blurred(source: Bitmap, intensity: Float): ImageBitmap = withContext(Dispatchers.Default) {
        val small = Bitmap.createScaledBitmap(source, 256, 150, true)
        Blur.boxBlur(small, 2 + (intensity * 10).roundToInt()).asImageBitmap()
    }

    private fun centerCrop(b: Bitmap): Bitmap {
        val aspect = b.width.toFloat() / b.height
        return if (aspect > ASPECT) {
            val w = (b.height * ASPECT).roundToInt().coerceAtMost(b.width)
            Bitmap.createBitmap(b, (b.width - w) / 2, 0, w, b.height)
        } else {
            val h = (b.width / ASPECT).roundToInt().coerceAtMost(b.height)
            Bitmap.createBitmap(b, 0, (b.height - h) / 2, b.width, h)
        }
    }
}
