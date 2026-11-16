package dev.carhome.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.carhome.launcher.R
import kotlin.math.min

// Colors from the Figma file
val Fg = Color(0xFFF3F3F3)
val Muted = Color(0xFF9A9A9A)
val Placeholder = Color(0xFF707070)
val Fallback = Color(0xFF121212)

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Every text style sits on a 24dp line, per the 8-point grid. */
private fun style(weight: FontWeight, size: Int, color: Color = Fg, line: Int = 24) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

object Type {
    val clock = style(FontWeight.Medium, 20)
    val tabActive = style(FontWeight.Bold, 16)
    val tabInactive = style(FontWeight.Normal, 16, Muted)
    val songTitle = style(FontWeight.SemiBold, 18)
    val artist = style(FontWeight.Normal, 18)
    val button = style(FontWeight.Medium, 18)
    val buttonLarge = style(FontWeight.Medium, 20)
    val body = style(FontWeight.Normal, 16, Muted)
    val rowTitle = style(FontWeight.Medium, 18)
    val sectionHeader = style(FontWeight.Medium, 16, Muted)
    val pageTitle = style(FontWeight.Bold, 24, line = 32)
}

/**
 * Lays everything out in Figma units (1 unit = 1dp) on a canvas of at least 1024×600,
 * scaled to fill the real screen edge to edge whatever density or shape it reports.
 * On a wider screen the canvas just gets wider (the bottom buttons stretch to fill);
 * on exactly 1024×600 it matches the Figma frame 1:1.
 */
@Composable
fun DesignCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Scale so the canvas is never smaller than 1024×600 in either direction.
        val scale = min(w / 1024f, h / 600f)
        CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
