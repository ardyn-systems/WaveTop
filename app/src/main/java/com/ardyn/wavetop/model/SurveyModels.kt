package com.ardyn.wavetop.model

/**
 * One stacked block on a survey card. New fields become a new subclass plus a
 * mapper line — screens iterate the list and do not need a rewrite.
 */
sealed interface SurveySection {
    val id: String

    data class Rssi(val dbm: Int?) : SurveySection {
        override val id = "rssi"
    }

    data class Identity(
        val name: String,
        val mac: String,
        val nameLabel: String,
        val addressLabel: String = "MAC",
    ) : SurveySection {
        override val id = "identity"
    }

    data class Encryption(val type: String) : SurveySection {
        override val id = "encryption"
    }

    data class ChannelFrequency(
        val channel: String,
        val frequency: String,
        val extra: String? = null,
    ) : SurveySection {
        override val id = "channel_frequency"
    }

    data class Manufacturer(val name: String) : SurveySection {
        override val id = "manufacturer"
    }
}

data class WifiApRecord(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val channelWidthMhz: Int?,
    val phy: String?,
    val encryption: String,
    val manufacturer: String,
    val lastSeenEpochMs: Long,
    /** Raw platform capabilities string, kept for exports (WiGLE AuthMode). */
    val capabilities: String = "",
) {
    fun sections(): List<SurveySection> {
        val channelLabel = buildString {
            if (channel != null) append("Ch $channel")
            else append("Ch —")
            if (band.isNotBlank()) append(" · $band")
            if (channelWidthMhz != null) append(" · ${channelWidthMhz} MHz wide")
        }
        val freqLabel = "$frequencyMhz MHz"
        return listOf(
            SurveySection.Rssi(rssiDbm),
            SurveySection.Identity(
                name = ssid.ifBlank { "Hidden network" },
                mac = bssid,
                nameLabel = "SSID",
            ),
            SurveySection.Encryption(encryption),
            SurveySection.ChannelFrequency(channelLabel, freqLabel, phy),
            SurveySection.Manufacturer(manufacturer),
        )
    }
}

data class BluetoothDeviceRecord(
    val name: String,
    val address: String,
    val rssiDbm: Int?,
    val phy: String?,
    val kind: String,
    val encryption: String,
    val manufacturer: String,
    val lastSeenEpochMs: Long,
) {
    fun sections(): List<SurveySection> {
        val channel = when {
            kind.contains("BLE", ignoreCase = true) -> "BLE adv. 37/38/39"
            else -> "AFH hopping"
        }
        val frequency = "2402–2480 MHz · 2.4 GHz"
        return listOf(
            SurveySection.Rssi(rssiDbm),
            SurveySection.Identity(
                name = name.ifBlank { "Unknown device" },
                mac = address,
                nameLabel = "Name",
            ),
            SurveySection.Encryption(encryption),
            SurveySection.ChannelFrequency(channel, frequency, phy ?: kind),
            SurveySection.Manufacturer(manufacturer),
        )
    }
}
