package com.ardyn.wavetop.model

object RfChannel {
    fun channelNumber(frequencyMhz: Int): Int? = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2483 -> ((frequencyMhz - 2412) / 5) + 1
        frequencyMhz in 5000..5895 -> (frequencyMhz - 5000) / 5
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> null
    }

    fun band(frequencyMhz: Int): String = when {
        frequencyMhz in 2400..2500 -> "2.4 GHz"
        frequencyMhz in 4900..5895 -> "5 GHz"
        frequencyMhz in 5925..7125 -> "6 GHz"
        else -> ""
    }

    fun widthMhz(channelWidthConst: Int): Int? = when (channelWidthConst) {
        0 -> 20  // ScanResult.CHANNEL_WIDTH_20MHZ
        1 -> 40
        2 -> 80
        3 -> 160
        4 -> 80  // 80+80
        5 -> 320
        else -> null
    }

    fun wifiStandardLabel(standard: Int): String? = when (standard) {
        1 -> "802.11a/b/g"
        4 -> "802.11n (Wi-Fi 4)"
        5 -> "802.11ac (Wi-Fi 5)"
        6 -> "802.11ax (Wi-Fi 6)"
        7 -> "802.11ad"
        8 -> "802.11be (Wi-Fi 7)"
        else -> null
    }
}
