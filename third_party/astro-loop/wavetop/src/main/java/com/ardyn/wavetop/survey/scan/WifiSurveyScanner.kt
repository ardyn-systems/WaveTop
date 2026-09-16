package com.ardyn.wavetop.survey.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.ardyn.wavetop.survey.model.RfChannel
import com.ardyn.wavetop.survey.model.WifiApRecord
import com.ardyn.wavetop.survey.model.WifiSecurity
import com.ardyn.wavetop.survey.oui.OuiLookup

class WifiSurveyScanner(
    private val context: Context,
    private val oui: OuiLookup,
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var listener: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            listener?.invoke()
        }
    }

    fun available(): Boolean = wifi != null

    fun radioOn(): Boolean = wifi?.isWifiEnabled == true

    fun register(onChange: () -> Unit) {
        listener = onChange
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerExported(context, receiver, filter)
    }

    fun unregister() {
        listener = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

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
            manufacturer = oui.manufacturerFor(bssid),
            lastSeenEpochMs = nowMs,
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
