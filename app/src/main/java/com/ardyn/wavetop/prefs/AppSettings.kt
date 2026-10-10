package com.ardyn.wavetop.prefs

import android.content.Context
import android.content.SharedPreferences
import com.ardyn.wavetop.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockStyle(val id: String, val label: String) {
    Auto("auto", "Like this phone"),
    H24("24", "24-hour"),
    H12("12", "12-hour"),
}

/** How WaveTop reaches NetSeer. */
enum class NetSeerRoute(val id: String) { Usb("usb"), Network("network") }

/** A NetSeer this phone has paired with (Settings → NetSeer). The token is NetSeer's device token. */
data class NetSeerLink(val baseUrl: String, val token: String, val deviceName: String, val pairedAtMs: Long)

data class Settings(
    val theme: AppTheme = AppTheme.WaveTop,
    val clock: ClockStyle = ClockStyle.Auto,
    /** Draw device names beside the strongest pins on the maps. */
    val mapLabels: Boolean = true,
    /** Keep the screen awake while a survey is recording and WaveTop is open. */
    val keepScreenOnWhileDriving: Boolean = true,
    /** Start scanning as soon as WaveTop opens. */
    val liveOnOpen: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckMs: Long = 0L,
    val netSeerRoute: NetSeerRoute = NetSeerRoute.Usb,
    val netSeerHost: String = "",
    val netSeer: NetSeerLink? = null,
    /** First-run welcome card dismissed. */
    val welcomed: Boolean = false,
)

/**
 * Preferences, kept in SharedPreferences and published as a [StateFlow] so the UI follows changes
 * (a theme switch repaints at once). One instance per process via [get].
 */
class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wavetop", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    fun update(change: (Settings) -> Settings) {
        val next = change(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): Settings {
        val link = prefs.getString(K_NS_TOKEN, null)?.let { token ->
            NetSeerLink(
                baseUrl = prefs.getString(K_NS_URL, "").orEmpty(),
                token = token,
                deviceName = prefs.getString(K_NS_DEVICE, "").orEmpty(),
                pairedAtMs = prefs.getLong(K_NS_PAIRED, 0L),
            )
        }
        return Settings(
            theme = AppTheme.fromId(prefs.getString(K_THEME, null)),
            clock = ClockStyle.entries.firstOrNull { it.id == prefs.getString(K_CLOCK, null) } ?: ClockStyle.Auto,
            mapLabels = prefs.getBoolean(K_MAP_LABELS, true),
            keepScreenOnWhileDriving = prefs.getBoolean(K_SCREEN_ON, true),
            liveOnOpen = prefs.getBoolean(K_LIVE_ON_OPEN, true),
            autoCheckUpdates = prefs.getBoolean(K_AUTO_UPDATE, true),
            lastUpdateCheckMs = prefs.getLong(K_LAST_CHECK, 0L),
            netSeerRoute = if (prefs.getString(K_NS_ROUTE, null) == NetSeerRoute.Network.id) NetSeerRoute.Network else NetSeerRoute.Usb,
            netSeerHost = prefs.getString(K_NS_HOST, "").orEmpty(),
            netSeer = link,
            welcomed = prefs.getBoolean(K_WELCOMED, false),
        )
    }

    private fun write(s: Settings) {
        prefs.edit().apply {
            putString(K_THEME, s.theme.id)
            putString(K_CLOCK, s.clock.id)
            putBoolean(K_MAP_LABELS, s.mapLabels)
            putBoolean(K_SCREEN_ON, s.keepScreenOnWhileDriving)
            putBoolean(K_LIVE_ON_OPEN, s.liveOnOpen)
            putBoolean(K_AUTO_UPDATE, s.autoCheckUpdates)
            putLong(K_LAST_CHECK, s.lastUpdateCheckMs)
            putString(K_NS_ROUTE, s.netSeerRoute.id)
            putString(K_NS_HOST, s.netSeerHost)
            putBoolean(K_WELCOMED, s.welcomed)
            val link = s.netSeer
            if (link == null) {
                remove(K_NS_TOKEN); remove(K_NS_URL); remove(K_NS_DEVICE); remove(K_NS_PAIRED)
            } else {
                putString(K_NS_TOKEN, link.token)
                putString(K_NS_URL, link.baseUrl)
                putString(K_NS_DEVICE, link.deviceName)
                putLong(K_NS_PAIRED, link.pairedAtMs)
            }
        }.apply()
    }

    /** Forget every preference (Settings → Your data → Reset settings). */
    fun reset() {
        prefs.edit().clear().apply()
        _state.value = Settings()
    }

    companion object {
        private const val K_THEME = "theme"
        private const val K_CLOCK = "clock"
        private const val K_MAP_LABELS = "map_labels"
        private const val K_SCREEN_ON = "screen_on_driving"
        private const val K_LIVE_ON_OPEN = "live_on_open"
        private const val K_AUTO_UPDATE = "auto_update_check"
        private const val K_LAST_CHECK = "last_update_check"
        private const val K_NS_ROUTE = "netseer_route"
        private const val K_NS_HOST = "netseer_host"
        private const val K_NS_TOKEN = "netseer_token"
        private const val K_NS_URL = "netseer_url"
        private const val K_NS_DEVICE = "netseer_device"
        private const val K_NS_PAIRED = "netseer_paired"
        private const val K_WELCOMED = "welcomed"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
