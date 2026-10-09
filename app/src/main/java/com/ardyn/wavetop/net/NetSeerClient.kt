package com.ardyn.wavetop.net

import com.ardyn.wavetop.prefs.NetSeerLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object NetSeerAddress {
    const val DEFAULT_PORT = 47331

    /**
     * Over a USB cable the phone talks to its own loopback; `adb reverse tcp:47331 tcp:<NetSeer's port>`
     * on the computer tunnels that port to NetSeer, which by default only listens on 127.0.0.1 anyway.
     * Current NetSeer sets the tunnel up itself for plugged-in phones.
     */
    const val USB_BASE_URL = "http://127.0.0.1:$DEFAULT_PORT"

    /**
     * Turns what the user typed — `192.168.1.20`, `192.168.1.20:8000`, `http://netseer.lan/` —
     * into a base URL, or null if it isn't a usable host.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        val scheme = when {
            s.startsWith("http://", ignoreCase = true) -> "http"
            s.startsWith("https://", ignoreCase = true) -> "https"
            else -> null
        }
        if (scheme != null) s = s.substringAfter("://")
        s = s.substringBefore('/').trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        val hasPort = if (s.startsWith("[")) s.contains("]:") else s.count { it == ':' } == 1
        val hostPort = if (hasPort) s else "$s:$DEFAULT_PORT"
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return "${scheme ?: "http"}://$hostPort"
    }
}

/** What `GET /api/v1/info` says about a NetSeer. */
data class NetSeerInfo(val product: String, val version: String, val formats: List<String>)

/**
 * NetSeer's integration API (NetSeer `docs/integration-api.md`):
 *  1. `GET /api/v1/info` — is it there, and which version.
 *  2. `POST /api/v1/pair {code, device_name}` — trade the 6-digit code NetSeer shows under
 *     Settings → Integrations for a long-lived device token.
 *  3. `POST /api/v1/ingest/capture` with `Authorization: Bearer <token>` — push a capture; NetSeer
 *     reads it and opens the map by itself.
 * Every call runs on the IO dispatcher and returns a Result instead of throwing.
 */
object NetSeerClient {
    private const val TIMEOUT_MS = 6_000

    suspend fun info(baseUrl: String): Result<NetSeerInfo> = call(baseUrl) {
        val json = JSONObject(request("$baseUrl/api/v1/info", "GET"))
        val formats = json.optJSONArray("formats")
        NetSeerInfo(
            product = json.optString("product", "NetSeer"),
            version = json.optString("version", "?"),
            formats = if (formats == null) emptyList() else (0 until formats.length()).map { formats.optString(it) },
        )
    }

    suspend fun pair(baseUrl: String, code: String, deviceName: String): Result<NetSeerLink> = call(baseUrl) {
        val body = JSONObject().put("code", code.trim().uppercase()).put("device_name", deviceName).toString()
        val json = JSONObject(request("$baseUrl/api/v1/pair", "POST", body = body.toByteArray(), contentType = "application/json"))
        val token = json.optString("token").ifBlank { throw IOException("NetSeer didn't return a token.") }
        NetSeerLink(baseUrl = baseUrl, token = token, deviceName = deviceName, pairedAtMs = System.currentTimeMillis())
    }

    /**
     * Pushes a capture file. [format] is NetSeer's `X-NetSeer-Format` (`csv` covers WiGLE CSV,
     * which NetSeer recognises by its header). @return a short confirmation for the UI.
     */
    suspend fun pushCapture(link: NetSeerLink, file: File, name: String, format: String): Result<String> =
        call(link.baseUrl) {
            val json = JSONObject(
                request(
                    "${link.baseUrl}/api/v1/ingest/capture",
                    "POST",
                    file = file,
                    contentType = "application/octet-stream",
                    headers = mapOf(
                        "Authorization" to "Bearer ${link.token}",
                        "X-NetSeer-API" to "1",
                        "X-NetSeer-Format" to format,
                        "X-NetSeer-Source" to "WaveTop",
                        "X-NetSeer-Name" to name,
                    ),
                ),
            )
            "NetSeer is reading \"${json.optString("name", name)}\"; the map opens there by itself."
        }

    private suspend fun <T> call(baseUrl: String, block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).recoverCatching { throw IOException(describe(it, baseUrl)) }
    }

    private fun request(
        url: String,
        method: String,
        body: ByteArray? = null,
        file: File? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS * 10
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (body != null || file != null) {
                conn.doOutput = true
                if (file != null) conn.setFixedLengthStreamingMode(file.length()) else conn.setFixedLengthStreamingMode(body!!.size)
                conn.outputStream.use { out ->
                    if (file != null) file.inputStream().use { it.copyTo(out) } else out.write(body!!)
                }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, errorMessage(text))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private class HttpError(val code: Int, val detail: String?) : IOException(detail ?: "HTTP $code")

    /** NetSeer answers `{"detail": "..."}` (FastAPI) or `{"error": {"message": "..."}}`. */
    private fun errorMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optString("detail").ifBlank { null }
            ?: json.optJSONObject("error")?.optString("message")?.ifBlank { null }
    }.getOrNull()

    private fun describe(e: Throwable, baseUrl: String): String = when (e) {
        is HttpError -> when (e.code) {
            401, 403 -> "NetSeer no longer recognises this phone. Pair it again under Settings → NetSeer."
            404 -> "That NetSeer doesn't have the integration API. Update NetSeer to the latest version."
            else -> e.detail ?: "NetSeer answered HTTP ${e.code}."
        }
        is java.net.ConnectException -> "Nothing answered at $baseUrl. " +
            if (baseUrl == NetSeerAddress.USB_BASE_URL) {
                "Is NetSeer running, the phone plugged in and unlocked, and USB debugging allowed for this computer? " +
                    "(Older NetSeer also needs the adb reverse command from Settings → NetSeer.)"
            } else {
                "Is NetSeer running with \"Allow devices on my network\" turned on?"
            }
        is java.net.SocketTimeoutException -> "Timed out talking to $baseUrl."
        is java.net.UnknownHostException -> "Unknown host in $baseUrl."
        else -> e.message ?: e.javaClass.simpleName
    }
}
