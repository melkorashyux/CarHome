package dev.carhome.launcher.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.carhome.launcher.R
import dev.carhome.launcher.data.Prefs

/**
 * The soft tick played when you tap a button. It's loaded once and plays instantly.
 * It plays on the unit's "system sounds" volume, mixed over your music without pausing it,
 * and can be turned off in Settings → Display → Button sounds.
 */
object ClickSound {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var prefs: Prefs? = null

    fun init(context: Context, prefs: Prefs) {
        this.prefs = prefs
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build().apply {
            setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
            soundId = load(context.applicationContext, R.raw.click, 1)
        }
    }

    fun play() {
        if (prefs?.buttonSounds?.value == false || !loaded) return
        pool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
