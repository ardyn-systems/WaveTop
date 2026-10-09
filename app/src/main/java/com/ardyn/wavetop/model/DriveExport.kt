package com.ardyn.wavetop.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns a saved wardrive into formats other tools read:
 *  - Kismet netxml: Wi-Fi networks with GPS, which NetSeer maps directly.
 *  - WiGLE CSV 1.4: every geotagged Wi-Fi and Bluetooth sighting, the common wardriving format.
 */
object DriveExport {

    /** Who wrote the file, for the WiGLE header line. */
    data class DeviceInfo(val appVersion: String, val model: String, val release: String, val device: String, val brand: String)

    fun kismetNetxml(drive: ParsedDrive): String {
        val wifi = drive.observations.filter { it.phy == Phy.Wifi }.groupBy { it.mac.uppercase() }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<!DOCTYPE detection-run SYSTEM "http://kismetwireless.net/kismet-3.1.0.dtd">""").append('\n')
        sb.append("""<detection-run kismet-version="WaveTop" start-time="${kismetTime(drive.meta.startedMs)}">""").append('\n')
        sb.append("  <!-- ").append(xml(drive.meta.name).replace("--", "- -")).append(" -->\n")
        var number = 0
        for ((bssid, sightings) in wifi) {
            number++
            val latest = sightings.maxBy { it.timeMs }
            val signals = sightings.mapNotNull { it.rssi }
            val fixes = sightings.filter { it.fix != null }
            val hidden = latest.name == DeviceTracker.HIDDEN
            sb.append("""  <wireless-network number="$number" type="infrastructure" """)
                .append("""first-time="${kismetTime(sightings.minOf { it.timeMs })}" last-time="${kismetTime(latest.timeMs)}">""")
                .append('\n')
            sb.append("    <SSID>\n")
            sb.append("      <type>Beacon</type>\n")
            sb.append("      <encryption>").append(xml(kismetEncryption(latest.crypto))).append("</encryption>\n")
            sb.append("""      <essid cloaked="$hidden">""").append(if (hidden) "" else xml(latest.name)).append("</essid>\n")
            sb.append("    </SSID>\n")
            sb.append("    <BSSID>").append(xml(bssid)).append("</BSSID>\n")
            if (latest.manufacturer.isNotBlank()) sb.append("    <manuf>").append(xml(latest.manufacturer)).append("</manuf>\n")
            latest.channel?.let { sb.append("    <channel>").append(it).append("</channel>\n") }
            latest.frequencyMhz?.let { sb.append("    <freqmhz>").append(it).append("</freqmhz>\n") }
            if (signals.isNotEmpty()) {
                sb.append("    <snr-info>\n")
                latest.rssi?.let { sb.append("      <last_signal_dbm>").append(it).append("</last_signal_dbm>\n") }
                sb.append("      <min_signal_dbm>").append(signals.min()).append("</min_signal_dbm>\n")
                sb.append("      <max_signal_dbm>").append(signals.max()).append("</max_signal_dbm>\n")
                sb.append("    </snr-info>\n")
            }
            if (fixes.isNotEmpty()) {
                val lats = fixes.map { it.fix!!.lat }
                val lons = fixes.map { it.fix!!.lon }
                val peak = fixes.maxBy { it.rssi ?: Int.MIN_VALUE }.fix!!
                sb.append("    <gps-info>\n")
                sb.append("      <min-lat>").append(fmt(lats.min())).append("</min-lat>\n")
                sb.append("      <min-lon>").append(fmt(lons.min())).append("</min-lon>\n")
                sb.append("      <max-lat>").append(fmt(lats.max())).append("</max-lat>\n")
                sb.append("      <max-lon>").append(fmt(lons.max())).append("</max-lon>\n")
                sb.append("      <peak-lat>").append(fmt(peak.lat)).append("</peak-lat>\n")
                sb.append("      <peak-lon>").append(fmt(peak.lon)).append("</peak-lon>\n")
                // NetSeer places the node at avg-*; the strongest sighting is the better estimate
                // of where the AP actually is, so that's what goes here.
                sb.append("      <avg-lat>").append(fmt(peak.lat)).append("</avg-lat>\n")
                sb.append("      <avg-lon>").append(fmt(peak.lon)).append("</avg-lon>\n")
                sb.append("    </gps-info>\n")
            }
            sb.append("  </wireless-network>\n")
        }
        sb.append("</detection-run>\n")
        return sb.toString()
    }

    fun wigleCsv(drive: ParsedDrive, device: DeviceInfo): String {
        val sb = StringBuilder()
        sb.append("WigleWifi-1.4,appRelease=").append(device.appVersion)
            .append(",model=").append(device.model)
            .append(",release=").append(device.release)
            .append(",device=").append(device.device)
            .append(",display=WaveTop,board=,brand=").append(device.brand).append('\n')
        sb.append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
        // WiGLE rows are geolocated sightings; one without a fix can't be placed, so it's left out.
        for (o in drive.observations) {
            val fix = o.fix ?: continue
            val (auth, type) = when (o.phy) {
                Phy.Wifi -> o.capabilities.ifBlank { "[ESS]" } to "WIFI"
                Phy.Bluetooth -> (if (o.type == "BLE") "Misc [LE]" else "Misc [BT]") to (if (o.type == "BLE") "BLE" else "BT")
            }
            val ssid = when {
                o.phy == Phy.Wifi && o.name == DeviceTracker.HIDDEN -> ""
                o.phy == Phy.Bluetooth && o.name == DeviceTracker.UNKNOWN_BT -> ""
                else -> o.name
            }
            sb.append(
                Csv.join(
                    listOf(
                        o.mac.lowercase(),
                        ssid,
                        auth,
                        wigleTime(o.timeMs),
                        (o.channel ?: 0).toString(),
                        (o.rssi ?: 0).toString(),
                        fmt(fix.lat),
                        fmt(fix.lon),
                        "0",
                        String.format(Locale.US, "%.1f", fix.accuracyM),
                        type,
                    ),
                ),
            ).append('\n')
        }
        return sb.toString()
    }

    /** Kismet writes privacy as e.g. "WPA+PSK" / "None"; NetSeer shows it as text. */
    private fun kismetEncryption(crypto: String): String =
        if (crypto.startsWith("Open")) "None" else crypto

    private fun fmt(v: Double) = String.format(Locale.US, "%.7f", v)

    private fun xml(s: String) = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // XML 1.0 forbids most control characters; SSIDs can carry them.
                else -> if (c < ' ' && c != '\t') append(' ') else append(c)
            }
        }
    }

    private fun kismetTime(ms: Long): String =
        SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US).format(Date(ms))

    private fun wigleTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
}
