package dev.carhome.launcher

import android.app.Application
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.util.ClickSound
import dev.carhome.launcher.util.CrashLog
import org.maplibre.android.MapLibre

class CarHomeApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        prefs = Prefs(this)
        ClickSound.init(this, prefs)
        MediaRepository.start(this)
        MapLibre.getInstance(this)
    }
}
