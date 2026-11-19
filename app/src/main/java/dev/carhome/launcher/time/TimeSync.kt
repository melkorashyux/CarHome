package dev.carhome.launcher.time

import android.content.Context
import android.location.Location
import android.os.SystemClock
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
import dev.carhome.launcher.location.GpsTracker
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
 *     fully offline. (GPS itself stays on for the map; see GpsTracker.)
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
    private var internetAllowed = false
    private var gpsHelped = false
    /** Set by "Sync now": take the next GPS fix even though we're already synced. */
    private var wantFresh = false

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
        // GPS stays on for the whole drive (GpsTracker); its first fix sets the clock.
        GpsTracker.start(context)
        // With Internet time on, also hand the correct time to the GPS chip once per drive,
        // so it isn't searching with the unit's wrong date.
        if (allowInternet && !gpsHelped) {
            gpsHelped = GpsTracker.sendTimeAndAssistance(context)
        }
        if (synced) return
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
        wantFresh = true
        GpsTracker.start(context)
        if (internetAllowed) scope.launch { syncFromInternet() }
    }

    /**
     * Called by GpsTracker for every fix. The first one after the car starts sets the time
     * (GPS time is the satellite time of the fix; adjust for how old the fix is).
     */
    fun onGpsFix(location: Location) {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        // Some units stamp fixes with their own (wrong) clock; then this is rejected as too
        // old, and the satellite time from the chip's NMEA messages is used instead.
        onSatelliteTime(location.time + ageMs)
    }

    /**
     * The real UTC time, straight from the GPS chip (see GpsTracker's NMEA reader), as of now.
     * The first good one after the car starts sets the clock.
     */
    fun onSatelliteTime(utcNowMs: Long) {
        if (synced && !wantFresh) return
        if (utcNowMs > SANE_AFTER_MS) {
            wantFresh = false
            publish(utcNowMs - System.currentTimeMillis(), "GPS")
            internetJob?.cancel()
        }
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
