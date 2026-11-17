package dev.carhome.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.carhome.launcher.data.AppSlot
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.location.GpsState
import dev.carhome.launcher.location.GpsTracker
import dev.carhome.launcher.map.MapGuard
import dev.carhome.launcher.map.MapStore
import dev.carhome.launcher.nav.NavRepository
import dev.carhome.launcher.nav.NavState
import dev.carhome.launcher.time.TimeSync
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.Pill
import dev.carhome.launcher.ui.glass.Rounded8
import dev.carhome.launcher.ui.glass.pressable
import dev.carhome.launcher.ui.theme.DarkPalette
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.LocalPalette
import dev.carhome.launcher.ui.theme.Placeholder
import dev.carhome.launcher.ui.theme.Type
import dev.carhome.launcher.util.Launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Layout on the 1024×600 canvas (all snapped to the 8-point grid):
 *   16  top margin
 *   24  clock / date row
 *   24  gap
 *  416  map card (336 wide, map 304×384 inside); Settings (72×72) top-right
 *   24  gap
 *   72  bottom row: Connect Smartphone (336) left, Apps (72×72) right
 *   24  bottom margin
 */
@Composable
fun HomeScreen(
    prefs: Prefs,
    locationGranted: Boolean,
    onOpenMap: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPick: (AppSlot) -> Unit,
) {
    val context = LocalContext.current
    val nav by NavRepository.state.collectAsState()
    val gps by GpsTracker.state.collectAsState()
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

        // The map card on the left; the Settings button top-right, level with the card's top.
        Row(Modifier.fillMaxWidth().height(416.dp)) {
            GlassSurface(Modifier.size(336.dp, 416.dp), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    MapCard(
                        nav = nav,
                        gps = gps,
                        hasMap = hasMap,
                        mapVersion = mapVersion,
                        locationGranted = locationGranted,
                        onOpenMap = onOpenMap,
                        onAddMap = onOpenSettings,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(Icons.Outlined.Settings, "Settings", onClick = onOpenSettings)
        }

        Spacer(Modifier.height(24.dp))

        // Connect Smartphone (card width) on the left, Apps on the far right.
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassSurface(
                modifier = Modifier.size(336.dp, 72.dp),
                onClick = { open(AppSlot.Connect) },
                onLongClick = { onPick(AppSlot.Connect) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Smartphone, null, tint = Fg, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Smartphone", style = Type.buttonLarge)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(Icons.Rounded.Apps, "Apps", onClick = onOpenApps)
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

// ---------- Map card ----------

@Composable
private fun MapCard(
    nav: NavState,
    gps: GpsState,
    hasMap: Boolean,
    mapVersion: Long,
    locationGranted: Boolean,
    onOpenMap: () -> Unit,
    onAddMap: () -> Unit,
) {
    val context = LocalContext.current
    // If the map crashed last time, wait for a tap instead of crashing again.
    var mapBlocked by remember(mapVersion) { mutableStateOf(MapGuard.lastStartFailed(context)) }

    // The map is always dark, so everything drawn on it keeps light text.
    CompositionLocalProvider(LocalPalette provides DarkPalette) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(Rounded8)
            .background(Color(0xFF15171B))
    ) {
        when {
            !hasMap -> MapPlaceholder("No offline map yet\nTap to add one in Settings", onClick = onAddMap)
            mapBlocked -> MapPlaceholder("The map couldn't start\nTap to try again · details in\nSettings → Diagnostics") {
                MapGuard.reset(context)
                mapBlocked = false
            }
            else -> {
                // key() rebuilds the map when a new map file is imported or location is allowed.
                key(mapVersion, locationGranted) {
                    OfflineMap(
                        Modifier.fillMaxSize(),
                        locationGranted = locationGranted,
                        onTap = onOpenMap,
                        onFailed = { mapBlocked = true },
                    )
                }
                Text(
                    "© OpenStreetMap",
                    style = Type.body.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (locationGranted && !gps.hasFix) {
                    GpsPill(gps, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }

        // Google Maps' next turn, if it's navigating.
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
}

@Composable
private fun MapPlaceholder(text: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().pressable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Navigation, null, tint = Placeholder, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = Type.body, textAlign = TextAlign.Center)
    }
}

/** "Searching for GPS · 5 satellites": shown until the receiver has a fix. */
@Composable
fun GpsPill(gps: GpsState, modifier: Modifier = Modifier) {
    val text = when {
        gps.satellitesSeen == 0 -> "Searching for GPS…"
        else -> "Searching for GPS · ${gps.satellitesUsed}/${gps.satellitesSeen} satellites"
    }
    Text(
        text,
        style = Type.body.copy(fontSize = 13.sp, lineHeight = 16.sp, color = Fg),
        maxLines = 1,
        modifier = modifier
            .clip(Pill)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ---------- Square icon buttons (Settings, Apps) ----------

/** A 72×72 glass square with a 32 icon, as in the Figma design. */
@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.size(72.dp),
        onClick = onClick,
    ) {
        Icon(icon, label, tint = Fg, modifier = Modifier.size(32.dp))
    }
}
