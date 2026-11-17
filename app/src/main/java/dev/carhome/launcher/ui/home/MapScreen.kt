package dev.carhome.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.carhome.launcher.location.GpsTracker
import dev.carhome.launcher.map.MapGuard
import dev.carhome.launcher.ui.glass.Backdrop
import dev.carhome.launcher.ui.glass.LocalBackdrop
import dev.carhome.launcher.ui.theme.Type
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * The offline map, full screen: drag, pinch and rotate it freely. Re-center goes back
 * to following you heading-up; Back returns home. This is a map, not turn-by-turn
 * navigation (offline routing would need a routing engine and much bigger map files).
 */
@Composable
fun MapScreen(locationGranted: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val gps by GpsTracker.state.collectAsState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF15171B))) {
        if (failed) {
            Text(
                "The map couldn't start.\nDetails in Settings → Diagnostics.",
                style = Type.body, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            OfflineMap(
                Modifier.fillMaxSize(),
                locationGranted = locationGranted,
                interactive = true,
                onReady = { map = it },
                onFailed = {
                    failed = true
                    MapGuard.reset(context)
                },
            )
        }

        // Over the map the glass is plain dark (no wallpaper behind it), so it stays readable.
        CompositionLocalProvider(LocalBackdrop provides Backdrop(tint = 0.6f)) {
            // Back, top-left.
            Box(Modifier.align(Alignment.TopStart).padding(24.dp)) {
                IconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            }

            // Zoom and re-center, right side.
            Column(
                Modifier.align(Alignment.CenterEnd).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(Icons.Rounded.Add, "Zoom in") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
                IconButton(Icons.Rounded.Remove, "Zoom out") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
                IconButton(Icons.Rounded.MyLocation, "Follow me") { map?.followMe() }
            }
        }

        if (locationGranted && !gps.hasFix) {
            GpsPill(gps, Modifier.align(Alignment.BottomStart).padding(24.dp))
        }
        Text(
            "© OpenStreetMap",
            style = Type.body.copy(fontSize = 11.sp, lineHeight = 14.sp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
