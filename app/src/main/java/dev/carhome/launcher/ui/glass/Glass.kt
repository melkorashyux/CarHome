package dev.carhome.launcher.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.carhome.launcher.ui.theme.DarkPalette
import dev.carhome.launcher.ui.theme.LightPalette
import dev.carhome.launcher.ui.theme.LocalPalette
import dev.carhome.launcher.util.ClickSound
import kotlin.math.roundToInt

val Pill = RoundedCornerShape(percent = 50)
val Rounded8 = RoundedCornerShape(8.dp)

/**
 * What every glass panel needs to draw itself: the frosted wallpaper, and where the
 * wallpaper sits on screen ([imageOffset]/[imageSize], relative to [origin]) so the
 * piece behind each panel lines up exactly with the photo around it.
 */
data class Backdrop(
    val wallpaper: ImageBitmap? = null,
    val blurred: ImageBitmap? = null,
    val tint: Float = 0.2f,
    /** Light glass: white instead of black, at the same strength. */
    val light: Boolean = false,
    val origin: Offset = Offset.Zero,
    val imageOffset: IntOffset = IntOffset.Zero,
    val imageSize: IntSize = IntSize.Zero,
)

/**
 * Where an image of [imageW]×[imageH] goes to cover a [boxW]×[boxH] area completely
 * (zooming in and centering, like ContentScale.Crop).
 */
fun coverRect(imageW: Int, imageH: Int, boxW: Int, boxH: Int): Pair<IntOffset, IntSize> {
    if (imageW <= 0 || imageH <= 0) return IntOffset.Zero to IntSize(boxW, boxH)
    val scale = maxOf(boxW.toFloat() / imageW, boxH.toFloat() / imageH)
    val w = (imageW * scale).roundToInt()
    val h = (imageH * scale).roundToInt()
    return IntOffset((boxW - w) / 2, (boxH - h) / 2) to IntSize(w, h)
}

val LocalBackdrop = compositionLocalOf { Backdrop() }

enum class GlassStyle {
    /** Cards and buttons sitting on the wallpaper: frosted wallpaper + black tint + light edge. */
    Panel,
    /** Elements inside a panel (tab bar, album circle, media buttons): a faint lift + light edge. */
    Inset,
}

// Figma's "Light 45°": brightest at the top-left, fading, with a softer return at the bottom-right.
private val PanelEdge = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.35f),
    0.5f to Color.White.copy(alpha = 0.06f),
    1f to Color.White.copy(alpha = 0.16f),
)
private val InsetEdge = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.28f),
    0.5f to Color.White.copy(alpha = 0.04f),
    1f to Color.White.copy(alpha = 0.12f),
)

fun Modifier.glass(shape: Shape, style: GlassStyle = GlassStyle.Panel): Modifier = composed {
    val backdrop = LocalBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }

    val filled = when (style) {
        GlassStyle.Panel -> this
            .onGloballyPositioned { position = it.positionInWindow() }
            .clip(shape)
            .drawBehind {
                val img = backdrop.blurred
                if (img != null && backdrop.imageSize.width > 0) {
                    // Draw the whole frosted wallpaper exactly where the sharp one is,
                    // shifted so the part behind this panel lines up.
                    val rel = position - backdrop.origin
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(
                            backdrop.imageOffset.x - rel.x.roundToInt(),
                            backdrop.imageOffset.y - rel.y.roundToInt(),
                        ),
                        dstSize = backdrop.imageSize,
                        filterQuality = FilterQuality.Medium,
                    )
                }
                drawRect((if (backdrop.light) Color.White else Color.Black).copy(alpha = backdrop.tint))
            }

        GlassStyle.Inset -> this
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
    }
    filled.border(1.dp, if (style == GlassStyle.Inset) InsetEdge else PanelEdge, shape)
}

/**
 * A glass shape that can be tapped. Pressing follows Apple's feel:
 * it shrinks a little, lightens, and springs back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Rounded8,
    style: GlassStyle = GlassStyle.Panel,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )

    var m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    if (onClick != null) {
        m = m.combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { long -> { ClickSound.play(); long() } },
            onClick = { ClickSound.play(); onClick() },
        )
    }
    // A panel sets the text color for everything on it: dark text on Light glass,
    // light text on Dark glass. Inset parts inherit their panel's choice.
    val backdrop = LocalBackdrop.current
    val palette = if (style == GlassStyle.Panel) {
        if (backdrop.light) LightPalette else DarkPalette
    } else {
        LocalPalette.current
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            modifier = m
                .glass(shape, style)
                .then(if (pressed) Modifier.background(Color.White.copy(alpha = 0.08f)) else Modifier),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** The same press feel for things that aren't glass (map, app icons). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { long -> { ClickSound.play(); long() } },
            onClick = { ClickSound.play(); onClick() },
        )
}

/** Settings rows: highlight on press instead of shrinking, like iOS lists. */
fun Modifier.rowPress(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    this
        .background(if (pressed) Color.White.copy(alpha = 0.06f) else Color.Transparent)
        .clickable(interactionSource = source, indication = null, onClick = { ClickSound.play(); onClick() })
}
