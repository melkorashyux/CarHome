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
    /** Your own photos ("My wallpapers"), kept only on the unit, never published. */
    private fun photosDir(context: Context) = File(context.filesDir, "wallpapers").apply { mkdirs() }

    /** Your photos, in the order you added them. Moves the single photo from older versions in. */
    fun photos(context: Context): List<File> {
        val legacy = File(context.filesDir, "wallpaper.jpg")
        if (legacy.exists()) legacy.renameTo(File(photosDir(context), "photo_${legacy.lastModified()}.jpg"))
        return photosDir(context).listFiles { f -> f.isFile && f.extension == "jpg" }
            ?.sortedBy { it.name }.orEmpty()
    }

    fun remove(context: Context, photo: File) {
        if (photo.parentFile == photosDir(context)) photo.delete()
    }

    /**
     * Copies a picked photo into My wallpapers, upright and resized. It isn't cropped here:
     * the screen zooms it to fill edge to edge, whatever the screen's shape.
     * Returns the choice for the new photo, or null if it couldn't be read.
     */
    suspend fun importFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 900) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val upright = if (rotation != 0) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, true)
            } else decoded

            val target = File(photosDir(context), "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(target).use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            photoChoice(target)
        } catch (e: Throwable) {
            // Includes running out of memory on a huge photo: report it instead of crashing.
            CrashLog.record(context, e)
            null
        }
    }

    /**
     * The built-in wallpapers, in order. Put them in app/src/main/res/drawable-nodpi/ as
     * wallpaper_1 … wallpaper_5 (.jpg, .jpeg or .png). The older wallpaper_default, if still
     * there, comes first. Looked up by name, so any number from 0 to 6 works.
     */
    @Suppress("DiscouragedApi")
    fun builtIns(context: Context): List<Int> =
        (listOf("wallpaper_default") + (1..5).map { "wallpaper_$it" })
            .map { context.resources.getIdentifier(it, "drawable", context.packageName) }
            .filter { it != 0 }

    const val PHOTO = "photo:"
    private const val LEGACY_CUSTOM = "custom" // 0.4.x before My wallpapers: the single photo

    fun photoChoice(photo: File) = PHOTO + photo.name

    /**
     * The photo a choice points to, or null if it's a built-in one (or the photo was removed).
     * No choice saved yet: your first photo, if you have one.
     */
    fun photoFor(context: Context, choice: String?): File? {
        val all = photos(context)
        return when {
            choice == null || choice == LEGACY_CUSTOM -> all.firstOrNull()
            choice.startsWith(PHOTO) -> all.firstOrNull { it.name == choice.removePrefix(PHOTO) }
            else -> null
        }
    }

    /** Which built-in wallpaper a choice means (0 if none or out of range). */
    fun builtInIndex(context: Context, choice: String?): Int {
        val count = builtIns(context).size
        val i = choice?.removePrefix(BUILT_IN)?.toIntOrNull() ?: 0
        return if (count == 0) 0 else i.coerceIn(0, count - 1)
    }

    const val BUILT_IN = "builtin:"
    fun builtInChoice(index: Int) = "$BUILT_IN$index"

    /** The wallpaper for a saved choice: a built-in one, or your own photo. */
    suspend fun load(context: Context, choice: String?): Bitmap? = withContext(Dispatchers.IO) {
        val photo = photoFor(context, choice)
        if (photo != null) {
            BitmapFactory.decodeFile(photo.path)
        } else {
            builtIns(context).getOrNull(builtInIndex(context, choice))?.let { decodeResource(context, it, 1600, 900) }
        }
    }

    /** A small preview for the Wallpaper screen. */
    suspend fun thumbnail(context: Context, resId: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        decodeResource(context, resId, 480, 280)?.asImageBitmap()
    }

    /** A small preview of one of your photos. */
    suspend fun photoThumbnail(photo: File): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!photo.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, 480, 280) })
            ?.asImageBitmap()
    }

    private fun decodeResource(context: Context, id: Int, minW: Int, minH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        return BitmapFactory.decodeResource(
            context.resources, id, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, minW, minH) }
        )
    }

    /** The biggest power-of-two shrink that still keeps the image at least [minW]×[minH]. */
    private fun sampleFor(bounds: BitmapFactory.Options, minW: Int, minH: Int): Int {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= minW && bounds.outHeight / (sample * 2) >= minH) sample *= 2
        return sample
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
