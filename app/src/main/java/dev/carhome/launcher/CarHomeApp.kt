package dev.carhome.launcher

import android.app.Application
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.media.MediaRepository
import org.maplibre.android.MapLibre

class CarHomeApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        MediaRepository.start(this)
        MapLibre.getInstance(this)
    }
}
