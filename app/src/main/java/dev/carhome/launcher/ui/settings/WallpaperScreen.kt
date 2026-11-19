package dev.carhome.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.carhome.launcher.data.ImportState
import dev.carhome.launcher.data.Imports
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.ui.apps.ScreenHeader
import dev.carhome.launcher.ui.common.ContentTop
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.Rounded8
import dev.carhome.launcher.ui.glass.pressable
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Type
import dev.carhome.launcher.wallpaper.Wallpaper

/** The screen's shape, so previews look exactly like the home screen behind them. */
private const val SCREEN_ASPECT = 1024f / 600f

/**
 * Pick a wallpaper: the built-in one, My wallpapers (your own photos, kept only on the unit),
 * or add photos from a USB stick. Tapping a preview switches straight away; the × removes
 * one of your photos.
 */
@Composable
fun WallpaperScreen(prefs: Prefs, onBack: () -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val choice by prefs.wallpaperChoice.collectAsState()
    val version by prefs.wallpaperVersion.collectAsState()
    val importState by Imports.wallpaper.collectAsState()

    val builtIns = remember { Wallpaper.builtIns(context) }
    var thumbs by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(Unit) { thumbs = builtIns.map { Wallpaper.thumbnail(context, it) } }

    // My wallpapers: re-read whenever one is added, removed or chosen.
    val photos = remember(version) { Wallpaper.photos(context) }
    val photoThumbs by produceState<Map<String, ImageBitmap?>>(emptyMap(), photos) {
        value = photos.associate { it.name to Wallpaper.photoThumbnail(it) }
    }

    val selectedPhoto = Wallpaper.photoFor(context, choice)
    val selectedBuiltIn = if (selectedPhoto != null) -1 else Wallpaper.builtInIndex(context, choice)

    // Starts below the clock bar, like the home screen.
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = ContentTop, bottom = 24.dp)) {
        ScreenHeader("Wallpaper", onBack)
        Spacer(Modifier.height(24.dp))

        // Panel: 976 wide; 3 previews of 304 (the map card's width) with 16 gaps and 16 padding.
        GlassSurface(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(builtIns) { i, _ ->
                    Preview(
                        image = thumbs.getOrNull(i),
                        label = if (builtIns.size == 1) "Default" else "Built-in ${i + 1}",
                        selected = i == selectedBuiltIn,
                    ) { prefs.setWallpaperChoice(Wallpaper.builtInChoice(i)) }
                }
                itemsIndexed(photos, key = { _, f -> f.name }) { i, photo ->
                    Preview(
                        image = photoThumbs[photo.name],
                        label = "My wallpaper ${i + 1}",
                        selected = photo.name == selectedPhoto?.name,
                        onRemove = {
                            Wallpaper.remove(context, photo)
                            // Removing the one in use goes back to the default.
                            if (photo.name == selectedPhoto?.name) {
                                prefs.setWallpaperChoice(Wallpaper.builtInChoice(0))
                            } else {
                                prefs.bumpWallpaper()
                            }
                        },
                    ) { prefs.setWallpaperChoice(Wallpaper.photoChoice(photo)) }
                }
                item {
                    val label = when (val s = importState) {
                        is ImportState.Copying -> "Loading…"
                        is ImportState.Error -> s.message
                        ImportState.Idle -> "Add from USB or storage"
                    }
                    Preview(image = null, label = label, selected = false, icon = Icons.Rounded.Add, onClick = onChoosePhoto)
                }
            }
        }
    }
}

@Composable
private fun Preview(
    image: ImageBitmap?,
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().pressable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SCREEN_ASPECT)
                .clip(Rounded8)
                .background(Fg.copy(alpha = 0.08f))
                .then(if (selected) Modifier.border(3.dp, Fg, Rounded8) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (icon != null) {
                Icon(icon, null, tint = Fg, modifier = Modifier.size(32.dp))
            }
            // My wallpapers can be removed: a small × in the top-left corner.
            if (onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pressable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = if (selected) Type.rowTitle else Type.body, maxLines = 1)
    }
}
