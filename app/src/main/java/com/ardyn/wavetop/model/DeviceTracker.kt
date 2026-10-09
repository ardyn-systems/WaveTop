package com.ardyn.wavetop.model

import kotlin.math.abs

/**
 * One fresh sighting of a device — what a wardrive records. [fix] is set only when a
 * location good enough to geotag it was available at that moment (see [DeviceTracker]).
 */
data class Observation(
    val timeMs: Long,
    val phy: Phy,
    val mac: String,
    val name: String,
    /** Short type as in the table: AP, BLE, BT, BT+LE. */
    val type: String,
    val crypto: String,
    val capabilities: String,
    val channel: Int?,
    val frequencyMhz: Int?,
    val manufacturer: String,
    val rssi: Int?,
    val fix: GeoFix?,
)

class TrackerUpdate(
    /** Devices seen for the first time. */
    val newDevices: List<TrackedDevice>,
    /** Every fresh sighting in this update, new device or not. */
    val observations: List<Observation>,
)

/**
 * Merges scan snapshots into long-lived [TrackedDevice]s, Kismet-style: a device
 * stays in the table after it drops out of a scan, and each fresh observation
 * (a newer last-seen time) adds a signal sample and may move its best location.
 *
 * Location tagging is strict on purpose. A sighting is only geotagged with a fix that is
 * accurate to [maxPinAccuracyM] and was taken within [maxFixSkewMs] of the sighting —
 * otherwise a stale "last known" location or a coarse cell-tower fix pins devices in
 * places the phone never was, and cached Wi-Fi results get pinned wherever you are when
 * they're read rather than where they were heard.
 *
 * Plain Kotlin with no Android types so it can be unit tested; the caller owns threading.
 */
class DeviceTracker(
    private val historySize: Int = 30,
    private val expireAfterMs: Long = 10 * 60_000L,
    private val maxPinAccuracyM: Float = 50f,
    private val maxFixSkewMs: Long = 15_000L,
) {
    private val devices = LinkedHashMap<String, TrackedDevice>()

    fun devices(): List<TrackedDevice> = devices.values.toList()

    operator fun get(key: String): TrackedDevice? = devices[key]

    fun updateWifi(records: List<WifiApRecord>, fix: GeoFix?): TrackerUpdate =
        collect(records.filter { it.bssid.isNotBlank() }) { r ->
            val obs = Observation(
                timeMs = r.lastSeenEpochMs,
                phy = Phy.Wifi,
                mac = r.bssid,
                name = r.ssid.ifBlank { HIDDEN },
                type = "AP",
                crypto = r.encryption,
                capabilities = r.capabilities,
                channel = r.channel,
                frequencyMhz = r.frequencyMhz,
                manufacturer = r.manufacturer,
                rssi = r.rssiDbm,
                fix = fix?.takeIf { usable(it, r.lastSeenEpochMs) },
            )
            obs to r.sections()
        }

    fun updateBluetooth(records: List<BluetoothDeviceRecord>, fix: GeoFix?): TrackerUpdate =
        collect(records.filter { it.address.isNotBlank() }) { r ->
            val obs = Observation(
                timeMs = r.lastSeenEpochMs,
                phy = Phy.Bluetooth,
                mac = r.address,
                name = r.name.ifBlank { UNKNOWN_BT },
                type = shortBluetoothType(r.kind),
                crypto = r.encryption,
                capabilities = "",
                channel = null,
                frequencyMhz = null,
                manufacturer = r.manufacturer,
                rssi = r.rssiDbm,
                fix = fix?.takeIf { usable(it, r.lastSeenEpochMs) },
            )
            obs to r.sections()
        }

    /** Replays a recorded sighting, e.g. when opening a saved wardrive. Its fix is trusted as recorded. */
    fun replay(obs: Observation) {
        merge(obs, sectionsFor(obs))
    }

    /** Drops devices not seen for [expireAfterMs] — BLE addresses rotate, so the table would only grow. */
    fun expire(nowMs: Long) {
        devices.values.removeAll { nowMs - it.lastSeenMs > expireAfterMs }
    }

    private fun usable(fix: GeoFix, seenMs: Long): Boolean =
        fix.accuracyM <= maxPinAccuracyM && abs(seenMs - fix.timeMs) <= maxFixSkewMs

    private inline fun <R> collect(
        records: List<R>,
        toObservation: (R) -> Pair<Observation, List<SurveySection>>,
    ): TrackerUpdate {
        val fresh = ArrayList<TrackedDevice>()
        val observations = ArrayList<Observation>()
        for (r in records) {
            val (obs, sections) = toObservation(r)
            when (merge(obs, sections)) {
                Merge.New -> {
                    fresh += devices.getValue(keyOf(obs.phy, obs.mac))
                    observations += obs
                }
                Merge.Updated -> observations += obs
                Merge.Stale -> Unit
            }
        }
        return TrackerUpdate(fresh, observations)
    }

    private enum class Merge { New, Updated, Stale }

    private fun merge(obs: Observation, sections: List<SurveySection>): Merge {
        val key = keyOf(obs.phy, obs.mac)
        val old = devices[key]
        // Platforms re-report cached results; only a newer timestamp is a new observation.
        if (old != null && obs.timeMs <= old.lastSeenMs) return Merge.Stale
        val sample = obs.rssi
        val history = if (sample != null) {
            ((old?.history ?: emptyList()) + sample).takeLast(historySize)
        } else {
            old?.history ?: emptyList()
        }
        val loudest = sample != null && (old?.maxRssi == null || sample >= old.maxRssi)
        devices[key] = TrackedDevice(
            key = key,
            phy = obs.phy,
            name = obs.name,
            mac = obs.mac,
            type = obs.type,
            crypto = obs.crypto,
            capabilities = obs.capabilities.ifBlank { old?.capabilities.orEmpty() },
            channel = obs.channel,
            frequencyMhz = obs.frequencyMhz,
            manufacturer = obs.manufacturer,
            rssi = sample ?: old?.rssi,
            history = history,
            minRssi = listOfNotNull(old?.minRssi, sample).minOrNull(),
            maxRssi = listOfNotNull(old?.maxRssi, sample).maxOrNull(),
            firstSeenMs = old?.firstSeenMs ?: obs.timeMs,
            lastSeenMs = obs.timeMs,
            // Pin on the first usable fix, then move the pin whenever the signal peaks.
            bestFix = if (obs.fix != null && (loudest || old?.bestFix == null)) obs.fix else old?.bestFix,
            sections = sections,
        )
        return if (old == null) Merge.New else Merge.Updated
    }

    companion object {
        const val HIDDEN = "Hidden network"
        const val UNKNOWN_BT = "Unknown device"

        fun keyOf(phy: Phy, mac: String): String = when (phy) {
            Phy.Wifi -> "wifi:${mac.lowercase()}"
            Phy.Bluetooth -> "bt:${mac.lowercase()}"
        }

        fun shortBluetoothType(kind: String): String = when (kind) {
            "BLE" -> "BLE"
            "Classic" -> "BT"
            else -> "BT+LE"
        }

        /** Rebuilds the detail-view sections for a recorded sighting. */
        fun sectionsFor(obs: Observation): List<SurveySection> = when (obs.phy) {
            Phy.Wifi -> WifiApRecord(
                ssid = if (obs.name == HIDDEN) "" else obs.name,
                bssid = obs.mac,
                rssiDbm = obs.rssi ?: -100,
                frequencyMhz = obs.frequencyMhz ?: 0,
                channel = obs.channel,
                band = obs.frequencyMhz?.let(RfChannel::band).orEmpty(),
                channelWidthMhz = null,
                phy = null,
                encryption = obs.crypto,
                manufacturer = obs.manufacturer,
                lastSeenEpochMs = obs.timeMs,
                capabilities = obs.capabilities,
            ).sections()
            Phy.Bluetooth -> BluetoothDeviceRecord(
                name = if (obs.name == UNKNOWN_BT) "" else obs.name,
                address = obs.mac,
                rssiDbm = obs.rssi,
                phy = null,
                kind = when (obs.type) {
                    "BLE" -> "BLE"
                    "BT" -> "Classic"
                    else -> "Classic + BLE"
                },
                encryption = obs.crypto,
                manufacturer = obs.manufacturer,
                lastSeenEpochMs = obs.timeMs,
            ).sections()
        }
    }
}
