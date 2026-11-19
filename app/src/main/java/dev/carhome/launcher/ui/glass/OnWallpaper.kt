package dev.carhome.launcher.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import dev.carhome.launcher.ui.theme.DarkPalette
import dev.carhome.launcher.ui.theme.LightPalette
import dev.carhome.launcher.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * For text drawn straight on the wallpaper, with no glass behind it (the clock and date,
 * page titles, section labels). Looks at the photo right behind [content] and picks white
 * or dark text, whichever reads better, so a bright sky gets dark text and a night photo
 * keeps white. Worked out once per spot, not every frame.
 */
@Composable
fun OnWallpaper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rel = position - backdrop.origin
    val dark = remember(
        backdrop.blurred, backdrop.imageOffset, backdrop.imageSize,
        rel.x.roundToInt() / 8, rel.y.roundToInt() / 8, size,
    ) {
        AdaptiveGlass.prefersDarkText(backdrop, rel.x, rel.y, size.width, size.height)
    }
    Box(
        modifier.onGloballyPositioned {
            position = it.positionInWindow()
            size = it.size
        }
    ) {
        CompositionLocalProvider(LocalPalette provides if (dark) LightPalette else DarkPalette) {
            content()
        }
    }
}
