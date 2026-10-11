package com.ardyn.wavetop.scan

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.ardyn.wavetop.model.GeoFix

/**
 * Follows the device's position with the platform LocationManager (GPS plus network),
 * so pins work without Play services. Callbacks arrive on the main thread.
 *
 * Only fixes taken in the last [FRESH_MS] are ever handed out. The platform's "last known
 * location" can be hours old and from somewhere else entirely, so it is used only if it
 * is itself fresh.
 */
class LocationTracker(context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var onFix: ((GeoFix) -> Unit)? = null
    private var best: Location? = null

    /** The current position, or null when there's no fix from the last few seconds. */
    val latest: GeoFix?
        get() = best?.takeIf { ageMs(it) <= FRESH_MS }?.toFix()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (ageMs(location) > FRESH_MS || !isBetter(location, best)) return
            best = location
            onFix?.invoke(location.toFix())
        }

        // Required on API < 30, where these have no default implementation.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start(onFix: (GeoFix) -> Unit) {
        val lm = manager ?: return
        stop()
        this.onFix = onFix
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (!lm.isProviderEnabled(provider)) continue
                // Ignored by the listener unless it's from the last few seconds.
                lm.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                lm.requestLocationUpdates(provider, UPDATE_MS, 0f, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
                // Provider missing on this device.
            }
        }
    }

    fun stop() {
        onFix = null
        try {
            manager?.removeUpdates(listener)
        } catch (_: SecurityException) {
        }
    }

    /** Prefer the newer fix unless it is much less accurate than a recent one. */
    private fun isBetter(candidate: Location, current: Location?): Boolean {
        if (current == null || ageMs(current) > FRESH_MS) return true
        val newerByMs = (candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos) / 1_000_000
        if (newerByMs <= 0) return false
        return candidate.accuracy <= current.accuracy * 2 || candidate.provider == current.provider
    }

    /** Age by the monotonic clock: Location.time is wall-clock and can be wrong on some fixes. */
    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun Location.toFix(): GeoFix =
        GeoFix(
            latitude, longitude, accuracy, System.currentTimeMillis() - ageMs(this),
            // Course over ground, but only while actually moving — a stationary GPS bearing is noise.
            bearing = if (hasBearing() && (!hasSpeed() || speed >= MIN_SPEED_MS)) bearing else null,
        )

    private companion object {
        const val UPDATE_MS = 2_000L
        const val FRESH_MS = 10_000L
        const val MIN_SPEED_MS = 0.7f // ~2.5 km/h; below this the heading isn't trustworthy.
    }
}
