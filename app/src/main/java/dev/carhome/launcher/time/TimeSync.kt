package dev.carhome.launcher.time

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class TimeState(
    /** Add this to the unit's clock to get the real time. */
    val offsetMs: Long = 0L,
    /** "GPS" or "Internet", or null before the first sync. */
    val source: String? = null,
    /** SystemClock.elapsedRealtime() at the last sync. */
    val syncedAt: Long? = null,
)

/**
 * Head units often lose the date and time when the car is off. Car Home doesn't trust
 * the unit's clock: once per drive it works out the real time, then stops.
 *
 *  1. GPS first: the first satellite fix after the car starts gives the exact time,
 *     fully offline. Car Home then stops listening for time.
 *  2. Internet only if you switch "Internet time" on in Settings, and only until the
 *     first successful sync (GPS or internet), then it stops too.
 *
 * Between starts the unit's clock runs accurately, so one correction lasts the drive.
 * Android only lets system apps change the unit's own clock, so this corrects
 * Car Home's clock only; other apps still use the unit's time.
 */
object TimeSync {
    private val _state = MutableStateFlow(TimeState())
    val state: StateFlow<TimeState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var internetJob: Job? = null
    private var locationManager: LocationManager? = null
    private var gpsRegistered = false
    private var internetAllowed = false

    // Any time earlier than this is clearly wrong (protects against bad GPS modules).
    private const val SANE_AFTER_MS = 1_735_689_600_000L // 1 Jan 2025

    fun now(): Long = System.currentTimeMillis() + _state.value.offsetMs

    private val synced get() = _state.value.source != null

    /**
     * Safe to call repeatedly (every resume, or when the setting changes).
     * Does nothing once this drive's clock is already corrected.
     */
    fun start(context: Context, allowInternet: Boolean) {
        internetAllowed = allowInternet
        if (!allowInternet) {
            internetJob?.cancel()
            internetJob = null
        }
        if (synced) return
        startGps(context.applicationContext)
        if (allowInternet && internetJob?.isActive != true) {
            internetJob = scope.launch {
                // Try every 2 minutes (the unit may not be online yet) until either source syncs.
                while (!synced) {
                    if (syncFromInternet()) break
                    delay(2 * 60_000L)
                }
            }
        }
    }

    /** "Sync now" from Settings: take one fresh reading, then stop again. */
    fun syncNow(context: Context) {
        startGps(context.applicationContext)
        if (internetAllowed) scope.launch { syncFromInternet() }
    }

    @SuppressLint("MissingPermission")
    private fun startGps(context: Context) {
        if (gpsRegistered) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        if (!lm.allProviders.contains(LocationManager.GPS_PROVIDER)) return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, gpsListener, Looper.getMainLooper())
            locationManager = lm
            gpsRegistered = true
        } catch (e: Exception) {
            // GPS unavailable on this unit; internet sync still works.
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // GPS time is the satellite time of the fix; adjust for how old the fix is.
            val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
            val trueNow = location.time + ageMs
            if (trueNow > SANE_AFTER_MS) {
                publish(trueNow - System.currentTimeMillis(), "GPS")
                stopGps() // got this drive's time; the map keeps its own GPS for your position
                internetJob?.cancel()
            }
        }

        @Deprecated("Required on Android 10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private fun stopGps() {
        try {
            locationManager?.removeUpdates(gpsListener)
        } catch (e: Exception) {
            // Already stopped.
        }
        gpsRegistered = false
    }

    private suspend fun syncFromInternet(): Boolean = withContext(Dispatchers.IO) {
        for (host in listOf("time.google.com", "pool.ntp.org", "time.cloudflare.com")) {
            val offset = try {
                queryNtp(host)
            } catch (e: Exception) {
                null
            }
            if (offset != null) {
                publish(offset, "Internet")
                return@withContext true
            }
        }
        false
    }

    /** A minimal SNTP request. Returns the clock offset in ms, or null. */
    private fun queryNtp(host: String): Long? {
        DatagramSocket().use { socket ->
            socket.soTimeout = 5_000
            val request = ByteArray(48).also { it[0] = 0x1B } // version 3, client mode
            val sentAt = SystemClock.elapsedRealtime()
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName(host), 123))
            val response = DatagramPacket(ByteArray(48), 48)
            socket.receive(response)
            val receivedAt = SystemClock.elapsedRealtime()
            val wallAtReceive = System.currentTimeMillis()

            val d = response.data
            val seconds = readU32(d, 40)
            val fraction = readU32(d, 44)
            if (seconds == 0L) return null
            val serverMs = (seconds - 2_208_988_800L) * 1000 + (fraction * 1000 shr 32)
            // The server stamped its reply about halfway through the round trip.
            val trueNow = serverMs + (receivedAt - sentAt) / 2
            if (trueNow < SANE_AFTER_MS) return null
            return trueNow - wallAtReceive
        }
    }

    private fun readU32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    private fun publish(offset: Long, source: String) {
        _state.value = TimeState(offset, source, SystemClock.elapsedRealtime())
    }
}
