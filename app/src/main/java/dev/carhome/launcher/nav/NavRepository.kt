package dev.carhome.launcher.nav

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavState(
    val active: Boolean = false,
    /** Usually the distance to the next turn, e.g. "200 m". */
    val title: String? = null,
    /** Usually the street, e.g. "toward Morris St". */
    val text: String? = null,
    /** The turn arrow Google Maps draws. */
    val icon: ImageBitmap? = null,
)

/** Reads Google Maps' ongoing navigation notification to show the next turn. */
object NavRepository {
    const val GOOGLE_MAPS = "com.google.android.apps.maps"

    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state.asStateFlow()
    private var key: String? = null

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName != GOOGLE_MAPS || !sbn.isOngoing) return
        key = sbn.key
        val n = sbn.notification
        val extras = n.extras
        val icon = try {
            n.getLargeIcon()?.loadDrawable(context)?.toBitmap()?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        _state.value = NavState(
            active = true,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            icon = icon,
        )
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.key == key) {
            key = null
            _state.value = NavState()
        }
    }
}
