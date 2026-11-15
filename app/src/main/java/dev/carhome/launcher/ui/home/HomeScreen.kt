package dev.carhome.launcher.ui.home

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.carhome.launcher.data.AppSlot
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.map.MapGuard
import dev.carhome.launcher.map.MapStore
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.media.MediaState
import dev.carhome.launcher.nav.NavRepository
import dev.carhome.launcher.nav.NavState
import dev.carhome.launcher.ui.glass.GlassStyle
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.Pill
import dev.carhome.launcher.ui.glass.Rounded8
import dev.carhome.launcher.ui.glass.pressable
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Muted
import dev.carhome.launcher.ui.theme.Placeholder
import dev.carhome.launcher.ui.theme.Type
import dev.carhome.launcher.time.TimeSync
import dev.carhome.launcher.util.Launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HomeTab { Navigation, NowPlaying }

/*
 * Layout on the 1024×600 canvas (all snapped to the 8-point grid):
 *   16  top margin
 *   24  clock / date row
 *   24  gap
 *  416  card (336 wide)
 *   24  gap
 *   72  bottom row (336 + 4 × 136, gaps 24)
 *   24  bottom margin
 */
@Composable
fun HomeScreen(
    prefs: Prefs,
    tab: HomeTab,
    locationGranted: Boolean,
    onTab: (HomeTab) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPick: (AppSlot) -> Unit,
) {
    val context = LocalContext.current
    val media by MediaRepository.state.collectAsState()
    val nav by NavRepository.state.collectAsState()
    val use24h by prefs.use24h.collectAsState()
    val timeZone by prefs.timeZone.collectAsState()
    val mapVersion by prefs.mapVersion.collectAsState()
    val hasMap = remember(mapVersion) { MapStore.isInstalled(context) }

    // A button opens its chosen app, or asks you to choose one the first time.
    fun open(slot: AppSlot) {
        if (!Launch.app(context, prefs.slot(slot).value)) onPick(slot)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        TopBar(use24h, timeZone)
        Spacer(Modifier.height(24.dp))

        GlassSurface(Modifier.size(336.dp, 416.dp), contentAlignment = Alignment.TopStart) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                TabBar(tab, onTab)
                Crossfade(
                    targetState = tab,
                    animationSpec = tween(250),
                    label = "tab",
                    modifier = Modifier.weight(1f),
                ) { t ->
                    when (t) {
                        HomeTab.Navigation -> Column {
                            Spacer(Modifier.height(16.dp))
                            NavPanel(nav, hasMap, mapVersion, locationGranted) { open(AppSlot.Navigation) }
                        }
                        HomeTab.NowPlaying -> NowPlayingPanel(media)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Connect Smartphone matches the card's width; the other four share the rest
        // (136 each on a 1024-wide screen, wider on wider screens).
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            GlassSurface(
                modifier = Modifier.size(336.dp, 72.dp),
                onClick = { open(AppSlot.Connect) },
                onLongClick = { onPick(AppSlot.Connect) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Smartphone, null, tint = Fg, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Smartphone", style = Type.button)
                }
            }
            SmallButton(Icons.Rounded.Bluetooth, "Bluetooth", Modifier.weight(1f),
                onLongClick = { onPick(AppSlot.Bluetooth) }) { open(AppSlot.Bluetooth) }
            SmallButton(Icons.Rounded.Apps, "Apps", Modifier.weight(1f), onClick = onOpenApps)
            SmallButton(Icons.Rounded.Radio, "Radio", Modifier.weight(1f),
                onLongClick = { onPick(AppSlot.Radio) }) { open(AppSlot.Radio) }
            SmallButton(Icons.Outlined.Settings, "Settings", Modifier.weight(1f), onClick = onOpenSettings)
        }
    }
}

// ---------- Clock and date ----------

@Composable
private fun TopBar(use24h: Boolean, timeZone: String?) {
    val now = rememberNow(timeZone)
    val time = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.ENGLISH).format(now)
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(now)
    Row(
        Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = Type.clock)
        Text(date, style = Type.clock)
    }
}

/**
 * The real time: the unit's clock corrected by GPS or internet time (see TimeSync),
 * in your chosen time zone. Ticks exactly on each new minute, and updates straight
 * away when a sync corrects it.
 */
@Composable
private fun rememberNow(timeZone: String?): ZonedDateTime {
    val sync by TimeSync.state.collectAsState()
    val zone = remember(timeZone) {
        timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
    fun current() = Instant.ofEpochMilli(TimeSync.now()).atZone(zone)
    var now by remember { mutableStateOf(current()) }
    LaunchedEffect(sync, zone) {
        while (true) {
            now = current()
            delay(60_000L - (TimeSync.now() % 60_000L))
        }
    }
    return now
}

// ---------- Tabs ----------

@Composable
private fun TabBar(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    // The active pill slides between tabs with a soft spring.
    val pillX by animateDpAsState(
        targetValue = if (tab == HomeTab.Navigation) 0.dp else 150.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pill",
    )
    GlassSurface(
        modifier = Modifier.size(304.dp, 56.dp),
        shape = Pill,
        style = GlassStyle.Inset,
        contentAlignment = Alignment.TopStart,
    ) {
        Box(Modifier.padding(4.dp)) {
            Box(
                Modifier
                    .offset(x = pillX)
                    .size(146.dp, 48.dp)
                    .clip(Pill)
                    .background(Fg.copy(alpha = 0.10f))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TabItem(Icons.Outlined.Navigation, "Navigation", tab == HomeTab.Navigation) { onTab(HomeTab.Navigation) }
                TabItem(Icons.Rounded.MusicNote, "Now Playing", tab == HomeTab.NowPlaying) { onTab(HomeTab.NowPlaying) }
            }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) Fg else Muted, label = "tabColor")
    Row(
        Modifier
            .size(146.dp, 48.dp)
            .clip(Pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = (if (active) Type.tabActive else Type.tabInactive).copy(color = color))
    }
}

// ---------- Now Playing ----------

@Composable
private fun NowPlayingPanel(media: MediaState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))

        // Album art fills the circle when the song has it; otherwise the note icon.
        GlassSurface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            style = GlassStyle.Inset,
            onClick = if (!media.hasAccess) {
                { Launch.settings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            } else null,
        ) {
            val art = media.art
            if (art != null && media.hasTrack) {
                Image(art, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(
                    imageVector = if (media.hasTrack) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                    contentDescription = null,
                    tint = Placeholder,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth().height(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                !media.hasAccess -> Text(
                    "Tap the circle to allow access\nso I can show what's playing",
                    style = Type.body, textAlign = TextAlign.Center,
                )
                // The easter egg.
                !media.hasTrack -> Text("The Sound of Silence", style = Type.songTitle.copy(color = Muted))
                else -> {
                    Text(media.title.orEmpty(), style = Type.songTitle, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(media.artist.orEmpty(), style = Type.artist, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        val dim = if (media.hasTrack) 1f else 0.35f
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ControlButton(Icons.Rounded.SkipPrevious, "Previous", media.hasTrack, dim) { MediaRepository.previous() }
            ControlButton(
                if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (media.playing) "Pause" else "Play",
                media.hasAccess, dim,
            ) { MediaRepository.playPause() }
            ControlButton(Icons.Rounded.SkipNext, "Next", media.hasTrack, dim) { MediaRepository.next() }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, enabled: Boolean, alpha: Float, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.size(64.dp).alpha(alpha),
        shape = CircleShape,
        style = GlassStyle.Inset,
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(icon, label, tint = Fg, modifier = Modifier.size(24.dp))
    }
}

// ---------- Navigation ----------

@Composable
private fun NavPanel(nav: NavState, hasMap: Boolean, mapVersion: Long, locationGranted: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier
            .size(304.dp, 312.dp)
            .clip(Rounded8)
            .background(Color(0xFF15171B))
    ) {
        val context = LocalContext.current
        // If the map crashed last time, wait for a tap instead of crashing again.
        var mapBlocked by remember(mapVersion) { mutableStateOf(MapGuard.lastStartFailed(context)) }
        if (hasMap && mapBlocked) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp).pressable(onClick = {
                    MapGuard.reset(context)
                    mapBlocked = false
                }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Navigation, null, tint = Placeholder, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "The map couldn't start\nTap to try again · details in\nSettings → Diagnostics",
                    style = Type.body, textAlign = TextAlign.Center,
                )
            }
        } else if (hasMap) {
            // key() rebuilds the map when a new map file is imported or location is allowed.
            androidx.compose.runtime.key(mapVersion, locationGranted) {
                OfflineMap(Modifier.fillMaxSize(), locationGranted, onTap = onOpen, onFailed = { mapBlocked = true })
            }
            Text(
                "© OpenStreetMap",
                style = Type.body.copy(fontSize = 10.sp, lineHeight = 12.sp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).pressable(onClick = onOpen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Navigation, null, tint = Placeholder, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (nav.active) "Navigating in Google Maps" else "No offline map yet\nAdd one in Settings",
                    style = Type.body, textAlign = TextAlign.Center,
                )
            }
        }

        if (nav.active) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(Rounded8)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                nav.icon?.let {
                    Image(it, null, Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(nav.title.orEmpty(), style = Type.songTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nav.text.orEmpty(), style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- Bottom row ----------

@Composable
private fun SmallButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier.height(72.dp),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Fg, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = Type.button)
        }
    }
}
