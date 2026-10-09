package com.ardyn.wavetop.model

import kotlin.math.abs

enum class PhyFilter(val label: String) {
    All("ALL"),
    Wifi("WI-FI"),
    Bluetooth("BLUETOOTH");

    fun matches(device: TrackedDevice): Boolean = when (this) {
        All -> true
        Wifi -> device.phy == Phy.Wifi
        Bluetooth -> device.phy == Phy.Bluetooth
    }
}

/** Device table columns that can be sorted, as in Kismet's device list. */
enum class DeviceSort(val label: String, val defaultDescending: Boolean) {
    Name("NAME", false),
    Type("TYPE", false),
    Crypto("CRYPTO", false),
    Signal("SIGNAL", true),
    Channel("CH", false),
    LastSeen("SEEN", true);

    fun comparator(): Comparator<TrackedDevice> = when (this) {
        Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        Type -> compareBy { it.type }
        Crypto -> compareBy { it.crypto }
        Signal -> compareBy { it.rssi ?: Int.MIN_VALUE }
        Channel -> compareBy { it.channel ?: Int.MAX_VALUE }
        LastSeen -> compareBy { it.lastSeenMs }
    }
}

object DeviceViews {
    fun visible(
        devices: List<TrackedDevice>,
        filter: PhyFilter,
        sort: DeviceSort,
        descending: Boolean,
    ): List<TrackedDevice> {
        // Ties fall back to MAC so rows don't swap places between identical scans.
        val primary = if (descending) sort.comparator().reversed() else sort.comparator()
        return devices.filter(filter::matches).sortedWith(primary.thenBy { it.mac })
    }
}

data class ChannelBar(val channel: Int, val count: Int, val strongestDbm: Int?)

enum class WifiBand(val label: String) {
    Band2g("2.4 GHz"),
    Band5g("5 GHz"),
    Band6g("6 GHz");

    companion object {
        fun of(frequencyMhz: Int): WifiBand? = when (RfChannel.band(frequencyMhz)) {
            "2.4 GHz" -> Band2g
            "5 GHz" -> Band5g
            "6 GHz" -> Band6g
            else -> null
        }
    }
}

object ChannelUsage {
    /**
     * Access points per channel for one band, like Kismet's channel view. 2.4 GHz always
     * lists channels 1–13 so overlap is visible; 5/6 GHz list only channels in use.
     */
    fun bars(devices: List<TrackedDevice>, band: WifiBand): List<ChannelBar> {
        val onBand = devices.filter {
            it.phy == Phy.Wifi && it.channel != null && it.frequencyMhz?.let(WifiBand::of) == band
        }
        val byChannel = onBand.groupBy { it.channel!! }
        val channels = if (band == WifiBand.Band2g) {
            ((1..13) + byChannel.keys).distinct().sorted()
        } else {
            byChannel.keys.sorted()
        }
        return channels.map { ch ->
            val list = byChannel[ch].orEmpty()
            ChannelBar(ch, list.size, list.mapNotNull { it.rssi }.maxOrNull())
        }
    }
}

/**
 * Radar placement. A phone can't measure bearing, so distance from the centre encodes
 * signal strength and the angle is just a stable per-device hash that keeps dots from
 * jumping between scans.
 */
object RadarMath {
    const val STRONG_DBM = -30
    const val WEAK_DBM = -100

    /** 0 = centre (strongest) … 1 = outer ring (weakest). */
    fun radiusFraction(dbm: Int): Float {
        val clamped = dbm.coerceIn(WEAK_DBM, STRONG_DBM)
        val t = (STRONG_DBM - clamped).toFloat() / (STRONG_DBM - WEAK_DBM)
        // Keep the strongest dots off the "you" marker.
        return 0.08f + 0.92f * t
    }

    /** Degrees in [0, 360). */
    fun angleDegrees(key: String): Float = abs(key.hashCode() % 3600) / 10f
}
