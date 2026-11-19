package dev.carhome.launcher.ui.glass

import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.pow

/**
 * Adaptive glass: keeps text readable on any wallpaper.
 *
 * For each panel, Car Home looks at the frosted wallpaper right behind it and works out how
 * strong the glass must be for its text to reach a comfortable contrast. Your setting
 * (20% at the default Glass intensity) is the minimum: where the wallpaper already allows
 * it, the glass stays exactly that clear. Only where the photo is too dark (Light glass) or
 * too bright (Dark glass) does it add more white or black, just enough, up to 85%.
 */
object AdaptiveGlass {
    /** Target contrast. 4.5:1 is the accessibility standard; a little extra covers busy photos. */
    private const val TARGET = 5.0
    private const val MAX_ALPHA = 0.85f

    private const val DARK_TEXT = 0xFF1C1C1E.toInt() // text on Light glass
    private const val LIGHT_TEXT = 0xFFF3F3F3.toInt() // text on Dark glass
    private const val FALLBACK_BG = 0xFF121212.toInt() // no wallpaper

    /** How strong the glass behind a panel at ([x], [y]) sized [w]×[h] (root pixels) should be. */
    fun alphaFor(b: Backdrop, x: Float, y: Float, w: Int, h: Int): Float {
        val behind = averageBehind(b, x, y, w, h) ?: FALLBACK_BG
        val overlay = if (b.light) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val text = if (b.light) DARK_TEXT else LIGHT_TEXT
        var alpha = b.tint
        while (alpha < MAX_ALPHA) {
            if (contrast(mix(behind, overlay, alpha), text) >= TARGET) return alpha
            alpha += 0.01f
        }
        return MAX_ALPHA
    }

    /** The average color of the frosted wallpaper behind a rectangle, or null if there isn't one. */
    private fun averageBehind(b: Backdrop, x: Float, y: Float, w: Int, h: Int): Int? {
        val img = b.blurred ?: return null
        if (b.imageSize.width <= 0 || b.imageSize.height <= 0 || w <= 0 || h <= 0) return null
        val bmp = img.asAndroidBitmap()
        val sx = bmp.width.toFloat() / b.imageSize.width
        val sy = bmp.height.toFloat() / b.imageSize.height
        val left = ((x - b.imageOffset.x) * sx).toInt().coerceIn(0, bmp.width - 1)
        val top = ((y - b.imageOffset.y) * sy).toInt().coerceIn(0, bmp.height - 1)
        val right = ((x + w - b.imageOffset.x) * sx).toInt().coerceIn(left + 1, bmp.width)
        val bottom = ((y + h - b.imageOffset.y) * sy).toInt().coerceIn(top + 1, bmp.height)
        val pw = right - left
        val ph = bottom - top
        val pixels = IntArray(pw * ph)
        return try {
            bmp.getPixels(pixels, 0, pw, left, top, pw, ph)
            var r = 0L
            var g = 0L
            var bl = 0L
            for (p in pixels) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                bl += p and 0xFF
            }
            val n = pixels.size
            (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun mix(base: Int, over: Int, a: Float): Int {
        fun ch(c: Int, s: Int) = (((c shr s) and 0xFF) * (1 - a) + ((over shr s) and 0xFF) * a).toInt()
        return (0xFF shl 24) or (ch(base, 16) shl 16) or (ch(base, 8) shl 8) or ch(base, 0)
    }

    private fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
