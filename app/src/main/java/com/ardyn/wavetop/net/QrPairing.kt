package com.ardyn.wavetop.net

import java.net.URLDecoder

/**
 * A scanned NetSeer pairing QR. NetSeer encodes (see its `docs/integration-api.md`):
 *
 *     netseer://pair?v=1&code=<6 digits>&host=<addr:port>[&host=...][&name=<percent-encoded>]
 *
 * `host` is repeatable and ordered remote (Tailscale) first, then LAN; WaveTop tries each in turn.
 * `code` is an opaque 6-digit string (leading zeros preserved). `name` is percent-encoded free text.
 */
data class QrPairPayload(val code: String, val hosts: List<String>, val name: String?)

object QrPairing {
    private const val SCHEME = "netseer://pair"

    /** Parses a scanned string, or null if it isn't a usable v1 NetSeer pairing QR. */
    fun parse(raw: String?): QrPairPayload? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith(SCHEME, ignoreCase = true)) return null
        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return null

        var version: String? = null
        var code: String? = null
        var name: String? = null
        val hosts = mutableListOf<String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=')
            val value = decode(pair.substringAfter('=', ""))
            when (key.lowercase()) {
                "v" -> version = value
                "code" -> code = value
                "host" -> if (value.isNotBlank()) hosts.add(value)
                "name" -> name = value.ifBlank { null }
            }
        }
        if (version != "1") return null
        val c = code?.trim().orEmpty()
        if (c.isEmpty() || hosts.isEmpty()) return null
        return QrPairPayload(code = c, hosts = hosts, name = name)
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
