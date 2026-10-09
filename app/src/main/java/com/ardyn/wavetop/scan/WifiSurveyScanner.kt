package com.ardyn.wavetop.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.ardyn.wavetop.model.RfChannel
import com.ardyn.wavetop.model.WifiApRecord
import com.ardyn.wavetop.model.WifiSecurity
import com.ardyn.wavetop.oui.OuiLookup

class WifiSurveyScanner(
    private val context: Context,
    private val oui: OuiLookup,
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var onResults: (() -> Unit)? = null
    private var onRadioChanged: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> onResults?.invoke()
                WifiManager.WIFI_STATE_CHANGED_ACTION -> onRadioChanged?.invoke()
            }
        }
    }

    fun available(): Boolean = wifi != null

    fun radioOn(): Boolean = wifi?.isWifiEnabled == true

    /**
     * [onResults] fires when a scan finishes (ours, or one another app or the system ran);
     * [onRadioChanged] when Wi-Fi is switched on or off.
     */
    fun register(onResults: () -> Unit, onRadioChanged: () -> Unit) {
        this.onResults = onResults
        this.onRadioChanged = onRadioChanged
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerExported(context, receiver, filter)
    }

    fun unregister() {
        onResults = null
        onRadioChanged = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    /**
     * Asks the platform for a fresh scan. False means it was refused — on Android 9+ a
     * foreground app gets 4 scans per 2 minutes — and no results broadcast will follow.
     */
    @Suppress("DEPRECATION")
    fun requestScan(): Boolean {
        val mgr = wifi ?: return false
        return try {
            mgr.startScan()
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun snapshot(nowMs: Long): List<WifiApRecord> {
        val mgr = wifi ?: return emptyList()
        val results = try {
            mgr.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        }
        return results
            .map { it.toRecord(nowMs) }
            .sortedByDescending { it.rssiDbm }
    }

    private fun ScanResult.toRecord(nowMs: Long): WifiApRecord {
        val ssid = surveySsid()
        val bssid = BSSID.orEmpty()
        val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            RfChannel.widthMhz(channelWidth)
        } else {
            null
        }
        val phy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            RfChannel.wifiStandardLabel(wifiStandard)
        } else {
            null
        }
        return WifiApRecord(
            ssid = ssid,
            bssid = bssid,
            rssiDbm = level,
            frequencyMhz = frequency,
            channel = RfChannel.channelNumber(frequency),
            band = RfChannel.band(frequency),
            channelWidthMhz = width,
            phy = phy,
            encryption = WifiSecurity.describe(capabilities),
            capabilities = capabilities.orEmpty(),
            manufacturer = oui.manufacturerFor(bssid),
            // scanResults includes cached entries; timestamp (µs since boot) is when this AP was
            // actually heard, which is what tells a fresh sighting from a stale one.
            lastSeenEpochMs = nowMs - (SystemClock.elapsedRealtime() - timestamp / 1000).coerceAtLeast(0),
        )
    }

    private fun ScanResult.surveySsid(): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString()?.trim('"') ?: @Suppress("DEPRECATION") SSID
        } else {
            @Suppress("DEPRECATION")
            SSID
        }
        if (raw.isNullOrBlank() || raw == "<unknown ssid>") return ""
        return raw
    }
}
