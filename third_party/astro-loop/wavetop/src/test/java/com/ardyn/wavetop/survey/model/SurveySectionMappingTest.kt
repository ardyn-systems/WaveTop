package com.ardyn.wavetop.survey.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveySectionMappingTest {
    @Test
    fun `wifi cards expose the five survey sections in order`() {
        val ids = WifiApRecord(
            ssid = "lab",
            bssid = "00:00:0C:00:00:01",
            rssiDbm = -40,
            frequencyMhz = 2437,
            channel = 6,
            band = "2.4 GHz",
            channelWidthMhz = 20,
            phy = "802.11n (Wi-Fi 4)",
            encryption = "WPA2 (CCMP)",
            manufacturer = "Cisco Systems, Inc",
            lastSeenEpochMs = 0L,
        ).sections().map { it.id }
        assertEquals(
            listOf("rssi", "identity", "encryption", "channel_frequency", "manufacturer"),
            ids,
        )
    }

    @Test
    fun `bluetooth uses the same section ids`() {
        val sections = BluetoothDeviceRecord(
            name = "Pixel Buds",
            address = "00:1A:11:00:00:02",
            rssiDbm = -60,
            phy = "LE 1M",
            kind = "BLE",
            encryption = "Not advertised",
            manufacturer = "Google, Inc.",
            lastSeenEpochMs = 0L,
        ).sections()
        assertTrue(sections[0] is SurveySection.Rssi)
        assertEquals("Name", (sections[1] as SurveySection.Identity).nameLabel)
        assertTrue(sections.any { it is SurveySection.Encryption })
        assertTrue(sections.any { it is SurveySection.ChannelFrequency })
        assertTrue(sections.any { it is SurveySection.Manufacturer })
    }
}
