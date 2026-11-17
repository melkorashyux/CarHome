package dev.carhome.launcher.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.carhome.launcher.time.TimeSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneOffset

data class GpsState(
    /** True while fixes are arriving. */
    val hasFix: Boolean = false,
    /** Satellites the receiver is using for the fix right now. */
    val satellitesUsed: Int = 0,
    /** Satellites it can hear at all. 0 for long usually means an antenna problem. */
    val satellitesSeen: Int = 0,
    /** The latest real fix, or the position saved when the car was last parked. */
    val last: Location? = null,
    /** True when [last] is a fresh fix, false when it's the saved parking position. */
    val lastIsLive: Boolean = false,
    /** For Diagnostics: the time Android stamped on the last fix (wrong on some units). */
    val fixStampMs: Long? = null,
    /** For Diagnostics: the satellite UTC time from the chip's own messages, when last read. */
    val satelliteTimeMs: Long? = null,
)

/**
 * Keeps the GPS receiver on from the moment Car Home starts, for the whole drive.
 *
 * A receiver that's switched on and off keeps losing the satellites it found, and without
 * internet each new start can take minutes. Keeping it on means one search per drive.
 * It also remembers where the car was last, so the map opens on your position straight away
 * while the receiver is still searching.
 */
object GpsTracker {
    private const val PREFS = "gps"
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()

    private var running = false
    private var appContext: Context? = null
    private var lastSavedAt = 0L

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (_state.value.last == null) loadSaved(app)?.let { _state.value = _state.value.copy(last = it) }
        if (running) return
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = app.getSystemService(LocationManager::class.java) ?: return
        if (!lm.allProviders.contains(LocationManager.GPS_PROVIDER)) return
        try {
            // The receiver's own last fix, if it's newer than what we saved.
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { known ->
                val saved = _state.value.last
                if (saved == null || known.time > saved.time) _state.value = _state.value.copy(last = known)
            }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            lm.registerGnssStatusCallback(gnssCallback, main)
            try {
                lm.addNmeaListener(nmeaListener, main)
            } catch (e: Exception) {
                // No NMEA on this unit; fix timestamps are still used.
            }
            running = true
        } catch (e: Exception) {
            // No GPS on this unit.
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.value = _state.value.copy(hasFix = true, last = location, lastIsLive = true, fixStampMs = location.time)
            TimeSync.onGpsFix(location)
            // Remember where we are about every 30 s, so the next start opens here.
            val now = SystemClock.elapsedRealtime()
            if (now - lastSavedAt > 30_000L) {
                lastSavedAt = now
                appContext?.let { save(it, location) }
            }
        }

        @Deprecated("Required on Android 10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            _state.value = _state.value.copy(hasFix = false, satellitesUsed = 0, satellitesSeen = 0)
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _state.value = _state.value.copy(
                satellitesUsed = used,
                satellitesSeen = status.satelliteCount,
                hasFix = used >= 3,
            )
        }

        override fun onStopped() {
            _state.value = _state.value.copy(hasFix = false, satellitesUsed = 0)
        }
    }

    /**
     * The GPS chip's own text messages (NMEA). The RMC message carries the satellite UTC date
     * and time, independent of the unit's clock: "$GNRMC,hhmmss.ss,A,...,ddmmyy,...".
     * "A" means the receiver has a valid fix, so its time is trustworthy.
     */
    private val nmeaListener = OnNmeaMessageListener { message, _ ->
        val utc = parseRmc(message) ?: return@OnNmeaMessageListener
        _state.value = _state.value.copy(satelliteTimeMs = utc)
        TimeSync.onSatelliteTime(utc)
    }

    /** UTC milliseconds from an RMC sentence with a valid fix, or null. */
    private fun parseRmc(sentence: String): Long? {
        if (sentence.length < 7 || !sentence.startsWith("$") || sentence.substring(3, 6) != "RMC") return null
        val f = sentence.substringBefore('*').split(',')
        if (f.size < 10 || f[2] != "A") return null
        val time = f[1]
        val date = f[9]
        if (time.length < 6 || date.length != 6) return null
        return try {
            val hh = time.substring(0, 2).toInt()
            val mi = time.substring(2, 4).toInt()
            val ss = time.substring(4, 6).toInt()
            val millis = time.substringAfter('.', "").padEnd(3, '0').take(3).toIntOrNull() ?: 0
            val dd = date.substring(0, 2).toInt()
            val mo = date.substring(2, 4).toInt()
            val yy = 2000 + date.substring(4, 6).toInt()
            LocalDateTime.of(yy, mo, dd, hh, mi, ss, millis * 1_000_000)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun save(context: Context, l: Location) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("lat", l.latitude.toFloat())
            .putFloat("lon", l.longitude.toFloat())
            .putFloat("bearing", l.bearing)
            .apply()
    }

    private fun loadSaved(context: Context): Location? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("lat")) return null
        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = p.getFloat("lat", 0f).toDouble()
            longitude = p.getFloat("lon", 0f).toDouble()
            bearing = p.getFloat("bearing", 0f)
            time = 0L // marks it as saved, not live
        }
    }
}
