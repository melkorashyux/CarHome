package dev.carhome.launcher.media

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
        activeNotifications?.forEach { NavRepository.onPosted(this, it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { NavRepository.onPosted(this, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { NavRepository.onRemoved(it) }
    }
}
