package com.ardyn.wavetop.net

import com.ardyn.wavetop.model.GeoFix
import com.ardyn.wavetop.model.Observation
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.prefs.NetSeerLink
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Where a live stream is in its lifecycle, for the UI. */
enum class LiveState { Idle, Connecting, Live, Reconnecting, Error, Closed }

/**
 * Turns WaveTop's [Observation]s into NetSeer's "observation delta" messages (NetSeer
 * `docs/integration-api.md`). The live schema is Wi-Fi/wireless-centric, so only Wi-Fi access points
 * are streamed; Bluetooth still rides along in the saved survey and the end-of-survey file push.
 */
object LiveSerializer {
    private val iso: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /** NetSeer's `band` from a frequency, or null if it isn't one of the Wi-Fi bands. */
    fun band(frequencyMhz: Int?): String? = when (frequencyMhz) {
        null -> null
        in 2400..2500 -> "2.4"
        in 4900..5895 -> "5"
        in 5925..7125 -> "6"
        else -> null
    }

    /** WaveTop's human crypto label (e.g. "WPA2 (CCMP)") to NetSeer's token, or null when unknown. */
    fun security(crypto: String): String? {
        val c = crypto.uppercase(Locale.US)
        return when {
            "OWE" in c -> "owe"
            "WPA3" in c -> "wpa3"
            "WPA2" in c -> "wpa2"
            "WPA" in c -> "wpa"
            "WEP" in c -> "wep"
            c.startsWith("OPEN") -> "open"
            else -> null
        }
    }

    /** One observation delta, or null when there's no Wi-Fi AP to report (position-only is a no-op). */
    fun message(observations: List<Observation>, fix: GeoFix?): String? {
        val devices = JSONArray()
        val stamp = iso
        for (o in observations) {
            if (o.phy != Phy.Wifi) continue
            val d = JSONObject()
                .put("mac", o.mac)
                .put("kind", "ap")
                .put("seen_at", stamp.format(Date(o.timeMs)))
            if (o.name.isNotBlank()) d.put("ssid", o.name)
            o.channel?.let { d.put("channel", it) }
            band(o.frequencyMhz)?.let { d.put("band", it) }
            security(o.crypto)?.let { d.put("security", it) }
            o.rssi?.let { d.put("signal_dbm", it) }
            if (o.manufacturer.isNotBlank()) d.put("vendor", o.manufacturer)
            o.fix?.let { d.put("gps", JSONObject().put("lat", it.lat).put("lon", it.lon)) }
            devices.put(d)
        }
        if (devices.length() == 0) return null
        val msg = JSONObject()
            .put("type", "observation")
            .put("ts", stamp.format(Date(System.currentTimeMillis())))
            .put("devices", devices)
        fix?.let { msg.put("position", JSONObject().put("lat", it.lat).put("lon", it.lon)) }
        return msg.toString()
    }
}

/**
 * Streams one survey to NetSeer over a WebSocket: opens a live session with the paired token, sends
 * observation deltas as they come, and closes the session at the end (NetSeer live-view API).
 *
 * Send-only — NetSeer merges each delta into its own map and never messages back. A dropped socket
 * ends the session on NetSeer's side, so a reconnect transparently opens a fresh session.
 *
 * [onState] may be called on an OkHttp thread; the caller marshals it to where it needs it.
 */
class NetSeerLiveSession(
    private val link: NetSeerLink,
    private val onState: (LiveState) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val lock = Any()
    private val retryExec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "netseer-live-retry").apply { isDaemon = true }
    }
    private val pending = ArrayDeque<String>()
    @Volatile private var ws: WebSocket? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var state = LiveState.Idle
    @Volatile private var stopped = false
    private var attempts = 0

    val currentState: LiveState get() = state

    /** Open the session and connect. Safe to call once. */
    fun start() {
        setState(LiveState.Connecting)
        openSession()
    }

    /** Queue one delta; it's sent now if connected, else buffered for when the socket opens. */
    fun offer(observations: List<Observation>, fix: GeoFix?) {
        if (stopped) return
        val msg = LiveSerializer.message(observations, fix) ?: return
        synchronized(lock) {
            val w = ws
            if (w != null && state == LiveState.Live) {
                w.send(msg)
            } else {
                pending.addLast(msg)
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
        }
    }

    /** End the stream and the NetSeer session. */
    fun stop() {
        if (stopped) return
        stopped = true
        synchronized(lock) { pending.clear() }
        ws?.close(NORMAL_CLOSE, null)
        ws = null
        deleteSession()
        retryExec.shutdownNow()
        setState(LiveState.Closed)
    }

    private fun openSession() {
        if (stopped) return
        val req = Request.Builder()
            .url("${link.baseUrl}/api/v1/live/sessions")
            .header("Authorization", "Bearer ${link.token}")
            .header("X-NetSeer-API", "1")
            .post(ByteArray(0).toRequestBody())
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = retryOrFail()
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) return retryOrFail()
                    val wsPath = runCatching { JSONObject(body) }.getOrNull()?.let { j ->
                        sessionId = j.optString("session_id").ifBlank { null }
                        j.optString("ws_url").ifBlank { null }
                    }
                    if (wsPath == null || sessionId == null) return retryOrFail()
                    connect(wsPath)
                }
            }
        })
    }

    private fun connect(wsPath: String) {
        if (stopped) return
        val wsBase = when {
            link.baseUrl.startsWith("https://") -> "wss://" + link.baseUrl.removePrefix("https://")
            link.baseUrl.startsWith("http://") -> "ws://" + link.baseUrl.removePrefix("http://")
            else -> link.baseUrl
        }
        val req = Request.Builder()
            .url(wsBase.trimEnd('/') + wsPath)
            .header("Authorization", "Bearer ${link.token}")
            .build()
        client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    ws = webSocket
                    attempts = 0
                    setState(LiveState.Live)
                    while (pending.isNotEmpty()) webSocket.send(pending.removeFirst())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (ws === webSocket) ws = null
                retryOrFail()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (ws === webSocket) ws = null
                if (!stopped) retryOrFail()
            }
        })
    }

    /** A dropped socket ends NetSeer's session, so each retry opens a fresh one, with backoff. */
    private fun retryOrFail() {
        if (stopped) return
        if (attempts >= MAX_ATTEMPTS) {
            setState(LiveState.Error)
            return
        }
        attempts++
        setState(LiveState.Reconnecting)
        sessionId = null
        val delay = (BACKOFF_MS * attempts).coerceAtMost(MAX_BACKOFF_MS)
        runCatching { retryExec.schedule({ openSession() }, delay, TimeUnit.MILLISECONDS) }
    }

    private fun deleteSession() {
        val id = sessionId ?: return
        val req = Request.Builder()
            .url("${link.baseUrl}/api/v1/live/sessions/$id")
            .header("Authorization", "Bearer ${link.token}")
            .delete()
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun setState(next: LiveState) {
        state = next
        onState(next)
    }

    private companion object {
        const val NORMAL_CLOSE = 1000
        const val MAX_PENDING = 500
        const val MAX_ATTEMPTS = 4
        const val BACKOFF_MS = 1_500L
        const val MAX_BACKOFF_MS = 8_000L
    }
}
