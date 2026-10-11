package com.ardyn.wavetop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTrackerTest {
    private fun ap(rssi: Int, seen: Long, bssid: String = "AA:BB:CC:00:00:01", ssid: String = "lab") = WifiApRecord(
        ssid = ssid,
        bssid = bssid,
        rssiDbm = rssi,
        frequencyMhz = 2437,
        channel = 6,
        band = "2.4 GHz",
        channelWidthMhz = 20,
        phy = null,
        encryption = "WPA2 (CCMP)",
        manufacturer = "Acme",
        lastSeenEpochMs = seen,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
    )

    /** A good fix taken at [time]. */
    private fun fix(lat: Double, time: Long, accuracy: Float = 5f) = GeoFix(lat, 0.0, accuracy, time)

    @Test
    fun `first sighting is reported as new, later ones are not`() {
        val t = DeviceTracker()
        assertEquals(1, t.updateWifi(listOf(ap(-60, 1_000)), null).newDevices.size)
        val second = t.updateWifi(listOf(ap(-55, 2_000)), null)
        assertTrue(second.newDevices.isEmpty())
        assertEquals(1, second.observations.size)
        assertEquals(1, t.devices().size)
    }

    @Test
    fun `re-reported cached result is neither a sample nor an observation`() {
        val t = DeviceTracker()
        t.updateWifi(listOf(ap(-60, 1_000)), null)
        val again = t.updateWifi(listOf(ap(-60, 1_000)), null)
        assertTrue(again.observations.isEmpty())
        assertEquals(listOf(-60), t.devices().single().history)
    }

    @Test
    fun `history is capped and keeps first seen`() {
        val t = DeviceTracker(historySize = 3)
        listOf(-70, -65, -60, -55).forEachIndexed { i, rssi -> t.updateWifi(listOf(ap(rssi, 1_000L + i)), null) }
        val d = t.devices().single()
        assertEquals(listOf(-65, -60, -55), d.history)
        assertEquals(1_000L, d.firstSeenMs)
        assertEquals(1_003L, d.lastSeenMs)
        assertEquals(-70, d.minRssi)
        assertEquals(-55, d.maxRssi)
    }

    @Test
    fun `the pin is a signal-weighted centroid dominated by the strongest sightings`() {
        val t = DeviceTracker()
        t.updateWifi(listOf(ap(-70, 1_000)), fix(1.0, 1_000))
        t.updateWifi(listOf(ap(-50, 2_000)), fix(2.0, 2_000)) // 100x the power of -70
        t.updateWifi(listOf(ap(-80, 3_000)), fix(3.0, 3_000))
        // The strong -50 sighting at lat 2 dominates; the far weaker ones barely nudge the pin.
        assertEquals(2.0, t.devices().single().bestFix!!.lat, 0.05)
    }

    @Test
    fun `two equal-strength sightings pin at their midpoint`() {
        val t = DeviceTracker()
        t.updateWifi(listOf(ap(-60, 1_000)), fix(0.0, 1_000))
        t.updateWifi(listOf(ap(-60, 2_000)), fix(2.0, 2_000))
        assertEquals(1.0, t.devices().single().bestFix!!.lat, 1e-9)
    }

    @Test
    fun `first usable fix pins a device even if its peak came before any fix`() {
        val t = DeviceTracker()
        t.updateWifi(listOf(ap(-40, 1_000)), null)
        t.updateWifi(listOf(ap(-80, 2_000)), fix(9.0, 2_000))
        assertEquals(9.0, t.devices().single().bestFix!!.lat, 0.0)
    }

    @Test
    fun `a stale fix never pins - the 'devices where I have never been' bug`() {
        val t = DeviceTracker(maxFixSkewMs = 15_000)
        // A last-known location from an hour earlier, somewhere else.
        val update = t.updateWifi(listOf(ap(-40, 3_600_000)), fix(51.5, 0))
        assertNull(t.devices().single().bestFix)
        assertNull(update.observations.single().fix)
    }

    @Test
    fun `a cached result heard long before the current fix is not pinned there`() {
        val t = DeviceTracker(maxFixSkewMs = 15_000)
        // Driving: the AP was heard 2 minutes ago, but we're only reading it now.
        t.updateWifi(listOf(ap(-40, 100_000)), fix(1.0, 220_000))
        assertNull(t.devices().single().bestFix)
    }

    @Test
    fun `a coarse network fix never pins`() {
        val t = DeviceTracker(maxPinAccuracyM = 50f)
        t.updateWifi(listOf(ap(-40, 1_000)), fix(1.0, 1_000, accuracy = 1_500f))
        assertNull(t.devices().single().bestFix)
    }

    @Test
    fun `devices expire after the window`() {
        val t = DeviceTracker(expireAfterMs = 1_000)
        t.updateWifi(listOf(ap(-60, 0, bssid = "AA:00:00:00:00:01"), ap(-60, 900, bssid = "AA:00:00:00:00:02")), null)
        t.expire(1_500)
        assertEquals(listOf("wifi:aa:00:00:00:00:02"), t.devices().map { it.key })
    }

    @Test
    fun `bluetooth kinds map to short types`() {
        val t = DeviceTracker()
        fun bt(addr: String, kind: String) = BluetoothDeviceRecord("", addr, -60, null, kind, "Not advertised", "Unknown", 1)
        t.updateBluetooth(listOf(bt("01", "BLE"), bt("02", "Classic"), bt("03", "Classic + BLE")), null)
        assertEquals(listOf("BLE", "BT", "BT+LE"), t.devices().map { it.type })
        assertEquals("Unknown device", t.devices().first().name)
        assertNull(t.devices().first().channel)
    }

    @Test
    fun `replaying recorded sightings rebuilds the device`() {
        val live = DeviceTracker()
        val recorded = live.updateWifi(listOf(ap(-70, 1_000)), fix(1.0, 1_000)).observations +
            live.updateWifi(listOf(ap(-45, 2_000)), fix(2.0, 2_000)).observations
        val replayed = DeviceTracker()
        recorded.forEach(replayed::replay)
        val d = replayed.devices().single()
        assertEquals(listOf(-70, -45), d.history)
        assertEquals(2.0, d.bestFix!!.lat, 0.05)
        assertEquals("[WPA2-PSK-CCMP][ESS]", d.capabilities)
        assertTrue(d.sections.isNotEmpty())
    }
}
