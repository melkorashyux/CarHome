package dev.carhome.launcher.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.time.TimeSync
import dev.carhome.launcher.ui.glass.OnWallpaper
import dev.carhome.launcher.ui.theme.DarkPalette
import dev.carhome.launcher.ui.theme.LocalPalette
import dev.carhome.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where screen content starts, below the clock bar: 16 margin + 24 bar + 24 gap. */
val ContentTop = 64.dp

/**
 * The time (left) and date (right), shown at the top of every screen. It sits above the
 * screens, so it stays put while they change.
 */
@Composable
fun ClockBar(prefs: Prefs, modifier: Modifier = Modifier, overMap: Boolean = false) {
    val use24h by prefs.use24h.collectAsState()
    val timeZone by prefs.timeZone.collectAsState()
    val now = rememberNow(timeZone)
    val time = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.ENGLISH).format(now)
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(now)
    val bar = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp).height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = Type.clock)
            Text(date, style = Type.clock)
        }
    }
    if (overMap) {
        // The full-screen map is always dark (with a dark fade behind the bar): keep white.
        CompositionLocalProvider(LocalPalette provides DarkPalette) { Box(modifier) { bar() } }
    } else {
        // On the wallpaper: white or dark text, whichever reads better on the photo.
        OnWallpaper(modifier.fillMaxWidth()) { bar() }
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
