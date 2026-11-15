package dev.carhome.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    /** False until "Notification access" is granted to Car Home. */
    val hasAccess: Boolean = false,
    /** False when nothing is loaded: shows "The Sound of Silence". */
    val hasTrack: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
)

/**
 * Follows every app that plays media (Spotify, YouTube Music, the unit's Bluetooth
 * music if it reports itself) and exposes the one that matters right now.
 */
object MediaRepository {
    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    /** Sessions found only through a player's notification, keyed by that notification. */
    private val fromNotifications = linkedMapOf<String, MediaController>()
    private var current: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> setControllers(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = select()
        override fun onMetadataChanged(metadata: MediaMetadata?) = select()
        override fun onSessionDestroyed() = refresh()
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    fun refresh() {
        val ctx = appContext ?: return
        if (!hasAccess(ctx)) {
            _state.value = MediaState(hasAccess = false)
            return
        }
        try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(ctx, MediaListenerService::class.java)
            if (manager == null) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                manager = msm
            }
            setControllers(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            _state.value = MediaState(hasAccess = false)
        }
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun playPause() {
        val c = current
        if (c == null) {
            // Nothing active: ask Android to resume whatever played last.
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return
        }
        if (_state.value.playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        current?.transportControls?.skipToPrevious()
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        // Merge in sessions found through notifications, without duplicates.
        val merged = list + fromNotifications.values.filter { n -> list.none { it.sessionToken == n.sessionToken } }
        controllers = merged
        controllers.forEach { it.registerCallback(callback, main) }
        select()
    }

    /**
     * Some players (often a head unit's own Bluetooth music app) don't list their session
     * with Android but do attach it to their notification. Car Home picks it up from there.
     */
    fun addFromNotification(context: Context, key: String, token: MediaSession.Token) {
        if (fromNotifications[key]?.sessionToken == token) return
        try {
            fromNotifications[key] = MediaController(context.applicationContext, token)
            refresh()
        } catch (e: Exception) {
            // Not a usable session.
        }
    }

    fun removeFromNotification(key: String) {
        if (fromNotifications.remove(key) != null) refresh()
    }

    /** For Settings → Diagnostics: every player Car Home can see right now. */
    fun describePlayers(): List<String> = controllers.map { c ->
        val state = when (c.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> "playing"
            PlaybackState.STATE_PAUSED -> "paused"
            PlaybackState.STATE_BUFFERING -> "buffering"
            PlaybackState.STATE_STOPPED -> "stopped"
            null -> "no state"
            else -> "state ${c.playbackState?.state}"
        }
        val title = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "no title"
        val via = if (fromNotifications.values.any { it.sessionToken == c.sessionToken }) " (via notification)" else ""
        "${c.packageName} · $state · $title$via"
    }

    /** Prefer the player that's actually playing, otherwise keep the last one. */
    private fun select() {
        val playing = controllers.firstOrNull { it.playbackState?.isActive() == true }
        val keep = controllers.firstOrNull { it.sessionToken == current?.sessionToken }
        current = playing ?: keep ?: controllers.firstOrNull()
        publish()
    }

    private fun publish() {
        val c = current
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        _state.value = MediaState(
            hasAccess = true,
            hasTrack = !title.isNullOrBlank(),
            title = title,
            artist = artist,
            art = art?.asImageBitmap(),
            playing = c?.playbackState?.isActive() == true,
        )
    }

    private fun PlaybackState.isActive(): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun sendKey(code: Int) {
        val am = appContext?.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
