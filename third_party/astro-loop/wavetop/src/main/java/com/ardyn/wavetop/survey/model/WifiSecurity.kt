package com.ardyn.wavetop.survey.model

object WifiSecurity {
    fun describe(capabilities: String?): String {
        if (capabilities.isNullOrBlank()) return "Unknown"
        val c = capabilities.uppercase()
        val modes = linkedSetOf<String>()
        if ("WPA3" in c || "SAE" in c) modes += "WPA3"
        if ("OWE" in c) modes += "Enhanced Open (OWE)"
        if ("WPA2" in c || "RSN" in c) modes += "WPA2"
        if ("[WPA-" in c || "[WPA]" in c) modes += "WPA"
        if ("WEP" in c) modes += "WEP"
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
        val label = modes.joinToString(" / ")
        return if (cipher != null) "$label ($cipher)" else label
    }
}
