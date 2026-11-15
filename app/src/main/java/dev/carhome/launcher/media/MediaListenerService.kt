package dev.carhome.launcher.media

import android.app.Notification
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.carhome.launcher.nav.NavRepository

/**
 * Android only shares media sessions and notifications with an app that has
 * "Notification access". This service is what that permission is granted to.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        MediaRepository.start(applicationContext)
        activeNotifications?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { handle(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NavRepository.onRemoved(sbn)
        MediaRepository.removeFromNotification(sbn.key)
        NotificationDiagnostics.removed(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        NavRepository.onPosted(this, sbn)
        @Suppress("DEPRECATION")
        val token = sbn.notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) MediaRepository.addFromNotification(this, sbn.key, token)
        NotificationDiagnostics.posted(sbn, token != null)
    }
}
