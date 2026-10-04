package com.ardyn.wavetop.survey.model

object WifiSecurity {
    // Display order, strongest first.
    private val ORDER = listOf(
        "WPA3",
        "WPA3-Enterprise",
        "Enhanced Open (OWE)",
        "WPA2",
        "WPA2-Enterprise",
        "WPA",
        "WPA-Enterprise",
        "WEP",
    )

    /**
     * Android writes one bracket group per protocol, e.g. `[RSN-SAE-CCMP]`,
     * `[WPA2-PSK+SAE-CCMP]` or `[WPA-EAP-TKIP]`. The protocol prefix (RSN/WPA2)
     * says nothing about WPA2 vs WPA3 — the key-management part after it does,
     * so classify each group by its AKM list rather than by substring.
     */
    fun describe(capabilities: String?): String {
        if (capabilities.isNullOrBlank()) return "Unknown"
        val c = capabilities.uppercase()
        val modes = mutableSetOf<String>()
        Regex("\\[([^\\]]*)\\]").findAll(c).forEach { match ->
            val group = match.groupValues[1]
            val protocol = group.substringBefore('-')
            val akm = group.substringAfter('-', "")
            when (protocol) {
                "RSN", "WPA2", "WPA3" -> {
                    val found = mutableSetOf<String>()
                    if ("SAE" in akm) found += "WPA3"
                    if ("SUITE-B" in akm) found += "WPA3-Enterprise"
                    else if ("EAP" in akm) found += "WPA2-Enterprise"
                    if ("PSK" in akm) found += "WPA2"
                    if ("OWE" in akm) found += "Enhanced Open (OWE)"
                    // Unrecognised AKM: fall back to what the protocol prefix implies.
                    if (found.isEmpty()) found += if (protocol == "WPA3") "WPA3" else "WPA2"
                    modes += found
                }
                "WPA" -> modes += if ("EAP" in akm) "WPA-Enterprise" else "WPA"
                "WEP" -> modes += "WEP"
            }
        }
        if (modes.isEmpty()) {
            return if ("ESS" in c || "IBSS" in c) "Open" else "Unknown"
        }
        val cipher = when {
            "GCMP-256" in c -> "GCMP-256"
            "GCMP" in c -> "GCMP"
            "CCMP" in c -> "CCMP"
            "TKIP" in c -> "TKIP"
            else -> null
        }
        val label = ORDER.filter { it in modes }.joinToString(" / ")
        return if (cipher != null) "$label ($cipher)" else label
    }
}
