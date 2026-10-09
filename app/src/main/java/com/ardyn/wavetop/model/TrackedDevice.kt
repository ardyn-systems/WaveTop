package com.ardyn.wavetop.model

enum class Phy { Wifi, Bluetooth }

/**
 * A location fix. [accuracyM] is the platform's 68% radius; [timeMs] is when the fix was
 * taken (epoch ms), which is what decides whether it can tag a sighting.
 */
data class GeoFix(val lat: Double, val lon: Double, val accuracyM: Float, val timeMs: Long)

/**
 * One device as Kismet tracks it: the latest scan record plus what has been
 * accumulated across scans — signal history, first/last seen, and where it was
 * heard loudest.
 */
data class TrackedDevice(
    /** Stable across scans: phy + MAC. */
    val key: String,
    val phy: Phy,
    val name: String,
    val mac: String,
    /** Short type for the table: AP, BLE, BT, BT+LE. */
    val type: String,
    val crypto: String,
    /** Wi-Fi capabilities exactly as the platform reported them, e.g. `[WPA2-PSK-CCMP][ESS]`. */
    val capabilities: String,
    val channel: Int?,
    val frequencyMhz: Int?,
    val manufacturer: String,
    val rssi: Int?,
    /** Recent RSSI samples, oldest first, one per fresh observation. */
    val history: List<Int>,
    val minRssi: Int?,
    val maxRssi: Int?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    /** Where we were standing when this device's signal was strongest. */
    val bestFix: GeoFix?,
    /** The same stacked sections the survey cards use, for the detail view. */
    val sections: List<SurveySection>,
)
