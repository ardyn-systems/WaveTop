package com.ardyn.wavetop.net

import com.ardyn.wavetop.model.GeoFix
import com.ardyn.wavetop.model.Observation
import com.ardyn.wavetop.model.Phy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSerializerTest {
    private fun wifi(
        mac: String = "AA:BB:CC:DD:EE:FF",
        name: String = "CoffeeShop",
        crypto: String = "WPA2 (CCMP)",
        channel: Int? = 6,
        freq: Int? = 2437,
        rssi: Int? = -62,
        vendor: String = "Cisco",
        fix: GeoFix? = null,
    ) = Observation(
        timeMs = 1_700_000_000_000L,
        phy = Phy.Wifi,
        mac = mac,
        name = name,
        type = "AP",
        crypto = crypto,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = channel,
        frequencyMhz = freq,
        manufacturer = vendor,
        rssi = rssi,
        fix = fix,
    )

    @Test
    fun `band comes from frequency`() {
        assertEquals("2.4", LiveSerializer.band(2437))
        assertEquals("5", LiveSerializer.band(5180))
        assertEquals("6", LiveSerializer.band(5955))
        assertNull(LiveSerializer.band(null))
        assertNull(LiveSerializer.band(1000))
    }

    @Test
    fun `security maps to NetSeer tokens`() {
        assertEquals("wpa3", LiveSerializer.security("WPA3"))
        assertEquals("wpa2", LiveSerializer.security("WPA2 (CCMP)"))
        assertEquals("wpa2", LiveSerializer.security("WPA2-Enterprise"))
        assertEquals("wpa", LiveSerializer.security("WPA"))
        assertEquals("wep", LiveSerializer.security("WEP"))
        assertEquals("owe", LiveSerializer.security("Enhanced Open (OWE)"))
        assertEquals("open", LiveSerializer.security("Open"))
        assertNull(LiveSerializer.security("Unknown"))
    }

    @Test
    fun `message carries a wifi ap in NetSeer's schema`() {
        val json = JSONObject(LiveSerializer.message(listOf(wifi()), GeoFix(40.0, -105.0, 8f, 1L))!!)
        assertEquals("observation", json.getString("type"))
        val dev = json.getJSONArray("devices").getJSONObject(0)
        assertEquals("AA:BB:CC:DD:EE:FF", dev.getString("mac"))
        assertEquals("ap", dev.getString("kind"))
        assertEquals("CoffeeShop", dev.getString("ssid"))
        assertEquals(6, dev.getInt("channel"))
        assertEquals("2.4", dev.getString("band"))
        assertEquals("wpa2", dev.getString("security"))
        assertEquals(-62, dev.getInt("signal_dbm"))
        assertEquals("Cisco", dev.getString("vendor"))
        val pos = json.getJSONObject("position")
        assertEquals(40.0, pos.getDouble("lat"), 1e-9)
        assertEquals(-105.0, pos.getDouble("lon"), 1e-9)
    }

    @Test
    fun `per-device gps is included when the sighting was geotagged`() {
        val json = JSONObject(LiveSerializer.message(listOf(wifi(fix = GeoFix(1.5, 2.5, 5f, 1L))), null)!!)
        val gps = json.getJSONArray("devices").getJSONObject(0).getJSONObject("gps")
        assertEquals(1.5, gps.getDouble("lat"), 1e-9)
        assertEquals(2.5, gps.getDouble("lon"), 1e-9)
    }

    @Test
    fun `bluetooth sightings are skipped and an all-bluetooth batch yields no message`() {
        val bt = Observation(
            timeMs = 1L, phy = Phy.Bluetooth, mac = "11:22:33:44:55:66", name = "Buds",
            type = "BLE", crypto = "", capabilities = "", channel = null, frequencyMhz = null,
            manufacturer = "Acme", rssi = -70, fix = null,
        )
        assertNull(LiveSerializer.message(listOf(bt), GeoFix(1.0, 1.0, 5f, 1L)))
        // Mixed batch keeps only the Wi-Fi device.
        val json = JSONObject(LiveSerializer.message(listOf(bt, wifi()), null)!!)
        assertEquals(1, json.getJSONArray("devices").length())
    }

    @Test
    fun `batch wraps one delta per wifi sighting, time-ordered, bluetooth skipped`() {
        val bt = Observation(
            timeMs = 500L, phy = Phy.Bluetooth, mac = "11:22:33:44:55:66", name = "Buds",
            type = "BLE", crypto = "", capabilities = "", channel = null, frequencyMhz = null,
            manufacturer = "Acme", rssi = -70, fix = null,
        )
        val later = wifi(mac = "AA:AA:AA:AA:AA:AA").copy(timeMs = 2_000L, fix = GeoFix(2.0, 2.0, 5f, 2_000L))
        val earlier = wifi(mac = "BB:BB:BB:BB:BB:BB").copy(timeMs = 1_000L, fix = GeoFix(1.0, 1.0, 5f, 1_000L))
        val json = JSONObject(LiveSerializer.batch(listOf(bt, later, earlier))!!)
        val obs = json.getJSONArray("observations")
        assertEquals(2, obs.length()) // Bluetooth dropped, two Wi-Fi kept
        // Time-ordered: the earlier sighting comes first, each delta carries its own position.
        assertEquals("BB:BB:BB:BB:BB:BB", obs.getJSONObject(0).getJSONArray("devices").getJSONObject(0).getString("mac"))
        assertEquals(1.0, obs.getJSONObject(0).getJSONObject("position").getDouble("lat"), 1e-9)
        assertEquals(2.0, obs.getJSONObject(1).getJSONObject("position").getDouble("lat"), 1e-9)
    }

    @Test
    fun `batch of only bluetooth yields null`() {
        val bt = Observation(
            timeMs = 1L, phy = Phy.Bluetooth, mac = "11:22:33:44:55:66", name = "Buds",
            type = "BLE", crypto = "", capabilities = "", channel = null, frequencyMhz = null,
            manufacturer = "Acme", rssi = -70, fix = GeoFix(1.0, 1.0, 5f, 1L),
        )
        assertNull(LiveSerializer.batch(listOf(bt)))
    }

    @Test
    fun `missing optional fields are omitted, not sent as null`() {
        val sparse = wifi(name = "", channel = null, freq = null, rssi = null, vendor = "")
        val dev = JSONObject(LiveSerializer.message(listOf(sparse), null)!!).getJSONArray("devices").getJSONObject(0)
        assertFalse(dev.has("ssid"))
        assertFalse(dev.has("channel"))
        assertFalse(dev.has("band"))
        assertFalse(dev.has("signal_dbm"))
        assertFalse(dev.has("vendor"))
        assertTrue(dev.has("mac"))
    }
}
