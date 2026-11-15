package dev.carhome.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.time.TimeSync
import dev.carhome.launcher.ui.CarHomeRoot
import dev.carhome.launcher.ui.Screen

class MainActivity : ComponentActivity() {

    private val screen = mutableStateOf<Screen>(Screen.Home)

    /** Bumped when location is granted, so the map can turn on its position marker. */
    private val locationGranted = mutableStateOf(false)

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted.value = granted
        if (granted) TimeSync.start(this, prefs().internetTime.value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val prefs = prefs()

        locationGranted.value = hasLocation()
        if (!locationGranted.value) askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        setContent { CarHomeRoot(prefs, screen, locationGranted.value) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        // Picks up a newly granted notification access and any new players.
        MediaRepository.refresh()
        locationGranted.value = hasLocation()
        TimeSync.start(this, prefs().internetTime.value)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Pressing the unit's Home button while we're open returns to the home screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen.value = Screen.Home
    }

    private fun prefs() = (application as CarHomeApp).prefs

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
