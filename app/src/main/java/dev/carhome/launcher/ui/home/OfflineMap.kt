package dev.carhome.launcher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.carhome.launcher.map.MapStore
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
 * The live offline map: dark style, your position as an arrow, heading-up, following you.
 * It's a display, not something to drag around while driving, so gestures are off and
 * a tap opens your navigation app instead.
 */
@Composable
fun OfflineMap(modifier: Modifier, locationGranted: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnTap = rememberUpdatedState(onTap)

    val mapView = remember {
        // Texture mode lets the map be clipped to the rounded corners like everything else.
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply {
            onCreate(null)
            getMapAsync { map -> setUp(context, map, locationGranted) { currentOnTap.value() } }
        }
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

@SuppressLint("MissingPermission")
private fun setUp(context: Context, map: MapLibreMap, locationGranted: Boolean, onTap: () -> Unit) {
    map.uiSettings.apply {
        isScrollGesturesEnabled = false
        isZoomGesturesEnabled = false
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
        isDoubleTapGesturesEnabled = false
        isCompassEnabled = false
        isLogoEnabled = false
        isAttributionEnabled = false // shown as text on the panel instead
    }
    map.addOnMapClickListener {
        onTap()
        true
    }

    // Start over Cairo until GPS has a fix.
    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(30.0444, 31.2357))
        .zoom(15.0)
        .build()

    map.setStyle(Style.Builder().fromJson(MapStore.styleJson(context))) { style ->
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
            }
        } catch (e: Exception) {
            // No GPS on this unit: the map still shows, centered on the last position.
        }
    }
}
