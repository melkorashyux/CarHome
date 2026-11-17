package dev.carhome.launcher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.carhome.launcher.location.GpsTracker
import dev.carhome.launcher.map.MapGuard
import dev.carhome.launcher.map.MapStore
import dev.carhome.launcher.util.CrashLog
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * The offline map: dark style, your position as an arrow, heading-up, following you.
 *
 * [interactive] false (the home card): a display only; a tap calls [onTap].
 * [interactive] true (full-screen map): pan, pinch and rotate; [onReady] hands over the map
 * so the screen's buttons can zoom and re-center.
 *
 * It opens on the last known position (where the car was parked) straight away,
 * then follows live GPS as soon as there's a fix.
 */
@Composable
fun OfflineMap(
    modifier: Modifier,
    locationGranted: Boolean,
    interactive: Boolean = false,
    onTap: () -> Unit = {},
    onReady: (MapLibreMap) -> Unit = {},
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnReady = rememberUpdatedState(onReady)

    val mapView = remember {
        // Mark the start, so a crash here can't turn into a crash loop (see MapGuard).
        MapGuard.markStarting(context)
        try {
            // Texture mode lets the map be clipped to the rounded corners like everything else.
            val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
            MapView(context, options).apply {
                onCreate(null)
                getMapAsync { map ->
                    try {
                        setUp(context, map, locationGranted, interactive) { currentOnTap.value() }
                        currentOnReady.value(map)
                    } catch (e: Exception) {
                        CrashLog.record(context, e)
                    }
                }
            }
        } catch (e: Throwable) {
            CrashLog.record(context, e)
            null
        }
    }

    if (mapView == null) {
        LaunchedEffect(Unit) { onFailed() }
        return
    }

    // Keep the map's own lifecycle in step with the screen's.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Back to following your position, heading-up (the full-screen map's re-center button). */
@SuppressLint("MissingPermission")
fun MapLibreMap.followMe() {
    try {
        if (locationComponent.isLocationComponentActivated) {
            locationComponent.cameraMode = CameraMode.TRACKING_GPS
            locationComponent.zoomWhileTracking(16.0)
        }
    } catch (e: Exception) {
        // Location not available.
    }
}

@SuppressLint("MissingPermission")
private fun setUp(context: Context, map: MapLibreMap, locationGranted: Boolean, interactive: Boolean, onTap: () -> Unit) {
    map.uiSettings.apply {
        isScrollGesturesEnabled = interactive
        isZoomGesturesEnabled = interactive
        isRotateGesturesEnabled = interactive
        isTiltGesturesEnabled = false
        isDoubleTapGesturesEnabled = interactive
        isCompassEnabled = false
        isLogoEnabled = false
        isAttributionEnabled = false // shown as text on screen instead
    }
    if (!interactive) {
        map.addOnMapClickListener {
            onTap()
            true
        }
    }

    // Open on the last known position (or Cairo the very first time).
    val start = GpsTracker.state.value.last
    map.cameraPosition = CameraPosition.Builder()
        .target(if (start != null) LatLng(start.latitude, start.longitude) else LatLng(30.0444, 31.2357))
        .zoom(if (start != null) 16.0 else 12.0)
        .bearing(start?.bearing?.toDouble() ?: 0.0)
        .build()

    map.setStyle(Style.Builder().fromJson(MapStore.styleJson(context))) { style ->
        // Style loaded: give it a few seconds of drawing, then call the start a success.
        Handler(Looper.getMainLooper()).postDelayed({ MapGuard.markStarted(context) }, 4_000L)
        if (!locationGranted) return@setStyle
        try {
            map.locationComponent.apply {
                activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, style)
                        .useDefaultLocationEngine(true)
                        .build()
                )
                isLocationComponentEnabled = true
                cameraMode = CameraMode.TRACKING_GPS // follows you, heading-up
                renderMode = RenderMode.GPS          // navigation arrow
                zoomWhileTracking(16.0)
                // Show the arrow at the last known position until GPS has a fix.
                start?.let { forceLocationUpdate(it) }
            }
        } catch (e: Exception) {
            // No GPS on this unit: the map still shows, centered on the last position.
        }
    }
}
