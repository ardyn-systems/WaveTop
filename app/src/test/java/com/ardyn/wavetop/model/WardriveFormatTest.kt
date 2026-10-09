package com.ardyn.wavetop.model

import com.ardyn.wavetop.net.NetSeerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WardriveFormatTest {
    private fun obs(
        mac: String,
        time: Long,
        rssi: Int,
        lat: Double? = 37.4,
        phy: Phy = Phy.Wifi,
        name: String = "Home, \"Net\"",
    ) = Observation(
        timeMs = time,
        phy = phy,
        mac = mac,
        name = name,
        type = if (phy == Phy.Wifi) "AP" else "BLE",
        crypto = "WPA2 (CCMP)",
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = if (phy == Phy.Wifi) 6 else null,
        frequencyMhz = if (phy == Phy.Wifi) 2437 else null,
        manufacturer = "Acme, Inc.",
        rssi = rssi,
        fix = lat?.let { GeoFix(it, -122.0, 4f, time) },
    )

    private fun file(vararg o: Observation, finished: Boolean = true): String = buildString {
        append(WardriveCsv.header("Downtown, loop", 1_000))
        o.forEach { append(WardriveCsv.row(it)) }
        if (finished) append(WardriveCsv.footer(9_000, o.size, 1, 1, o.count { it.fix != null }))
    }

    @Test
    fun `round trips a drive, including commas and quotes`() {
        val a = obs("AA:BB:CC:00:00:01", 2_000, -50)
        val b = obs("11:22:33:44:55:66", 3_000, -70, lat = null, phy = Phy.Bluetooth, name = "Buds")
        val drive = WardriveCsv.parse(file(a, b).lineSequence())!!
        assertEquals("Downtown, loop", drive.meta.name)
        assertEquals(1_000L, drive.meta.startedMs)
        assertEquals(9_000L, drive.meta.endedMs)
        assertEquals(8_000L, drive.durationMs)
        assertFalse(drive.meta.interrupted)
        assertEquals(listOf(a, b), drive.observations)
    }

    @Test
    fun `an interrupted drive still opens, ending at its last sighting`() {
        val text = file(obs("AA:BB:CC:00:00:01", 2_000, -50), obs("AA:BB:CC:00:00:01", 5_000, -40), finished = false) +
            "5500,wifi,AA:BB" // half-written row from a power cut
        val drive = WardriveCsv.parse(text.lineSequence())!!
        assertTrue(drive.meta.interrupted)
        assertEquals(2, drive.observations.size)
        assertEquals(4_000L, drive.durationMs)
    }

    @Test
    fun `meta comes from the comment lines alone`() {
        val comments = file(obs("AA:BB:CC:00:00:01", 2_000, -50)).lines().filter { it.startsWith("#") }
        val meta = WardriveCsv.parseMeta(comments)!!
        assertEquals(1, meta.observations)
        assertEquals(1, meta.geotagged)
    }

    @Test
    fun `not a wardrive file`() {
        assertNull(WardriveCsv.parse(sequenceOf("BSSID,ESSID", "aa,bb")))
    }

    @Test
    fun `kismet netxml has one network per bssid placed at its strongest sighting`() {
        val drive = WardriveCsv.parse(
            file(
                obs("aa:bb:cc:00:00:01", 2_000, -70, lat = 37.1),
                obs("aa:bb:cc:00:00:01", 3_000, -40, lat = 37.2),
                obs("11:22:33:44:55:66", 3_000, -40, phy = Phy.Bluetooth),
            ).lineSequence(),
        )!!
        val xml = DriveExport.kismetNetxml(drive)
        assertTrue(xml.contains("<detection-run"))
        assertEquals(1, Regex("<wireless-network ").findAll(xml).count())
        assertTrue(xml.contains("<BSSID>AA:BB:CC:00:00:01</BSSID>"))
        assertTrue(xml.contains("<avg-lat>37.2000000</avg-lat>"))
        assertTrue(xml.contains("<max_signal_dbm>-40</max_signal_dbm>"))
        assertTrue("SSID is escaped", xml.contains("Home, &quot;Net&quot;"))
        assertTrue(xml.contains("<manuf>Acme, Inc.</manuf>"))
    }

    @Test
    fun `wigle csv lists only geotagged sightings`() {
        val drive = WardriveCsv.parse(
            file(
                obs("AA:BB:CC:00:00:01", 2_000, -50),
                obs("AA:BB:CC:00:00:02", 2_000, -50, lat = null),
                obs("11:22:33:44:55:66", 3_000, -60, phy = Phy.Bluetooth, name = "Buds"),
            ).lineSequence(),
        )!!
        val lines = DriveExport.wigleCsv(drive, DriveExport.DeviceInfo("WaveTop-1", "Pixel", "15", "x", "google")).trim().lines()
        assertTrue(lines[0].startsWith("WigleWifi-1.4,"))
        assertEquals(2 + 2, lines.size)
        assertTrue(lines[2].startsWith("aa:bb:cc:00:00:01,\"Home, \"\"Net\"\"\",[WPA2-PSK-CCMP][ESS],1970-01-01 00:00:02,6,-50,"))
        assertTrue(lines[3].endsWith(",BLE"))
    }

    @Test
    fun `netseer addresses`() {
        assertEquals("http://192.168.1.20:47331", NetSeerAddress.normalize(" 192.168.1.20 "))
        assertEquals("http://192.168.1.20:8000", NetSeerAddress.normalize("192.168.1.20:8000"))
        assertEquals("https://netseer.lan:47331", NetSeerAddress.normalize("https://netseer.lan/"))
        assertEquals("http://[::1]:47331", NetSeerAddress.normalize("[::1]"))
        assertNotNull(NetSeerAddress.normalize("http://host:1/x/y"))
        assertNull(NetSeerAddress.normalize(""))
        assertNull(NetSeerAddress.normalize("host:99999"))
        assertNull(NetSeerAddress.normalize("two words"))
    }
}
