package dev.carhome.launcher.media

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * For Settings → Diagnostics: the ongoing notifications Car Home can see, so we can tell
 * how a player (like the unit's Bluetooth music app) reports what's playing.
 *
 * Only ongoing notifications are listed (players, navigation, connections), not
 * messages. Kept in memory only; nothing is saved or sent.
 */
object NotificationDiagnostics {
    private val entries = linkedMapOf<String, String>()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun posted(sbn: StatusBarNotification, hasMediaSession: Boolean) {
        if (!sbn.isOngoing) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(40)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(40)
        val media = if (hasMediaSession) " · media session" else ""
        entries[sbn.key] = "${sbn.packageName}$media\n   \"$title\" · \"$text\""
        _lines.value = entries.values.toList()
    }

    fun removed(sbn: StatusBarNotification) {
        if (entries.remove(sbn.key) != null) _lines.value = entries.values.toList()
    }
}
