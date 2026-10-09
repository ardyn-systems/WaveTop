package com.ardyn.wavetop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceViewsTest {
    private fun device(
        name: String,
        rssi: Int?,
        phy: Phy = Phy.Wifi,
        channel: Int? = 6,
        freq: Int? = 2437,
        mac: String = name,
    ) = TrackedDevice(
        key = "${phy.name}:$mac",
        phy = phy,
        name = name,
        mac = mac,
        type = if (phy == Phy.Wifi) "AP" else "BLE",
        crypto = "WPA2 (CCMP)",
        capabilities = "",
        channel = channel,
        frequencyMhz = freq,
        manufacturer = "",
        rssi = rssi,
        history = emptyList(),
        minRssi = rssi,
        maxRssi = rssi,
        firstSeenMs = 0,
        lastSeenMs = 0,
        bestFix = null,
        sections = emptyList(),
    )

    @Test
    fun `signal sorts strongest first and unknown last`() {
        val list = listOf(device("a", -80), device("b", null), device("c", -40))
        val names = DeviceViews.visible(list, PhyFilter.All, DeviceSort.Signal, descending = true).map { it.name }
        assertEquals(listOf("c", "a", "b"), names)
    }

    @Test
    fun `name sort ignores case`() {
        val list = listOf(device("beta", -50), device("Alpha", -50), device("gamma", -50))
        val names = DeviceViews.visible(list, PhyFilter.All, DeviceSort.Name, descending = false).map { it.name }
        assertEquals(listOf("Alpha", "beta", "gamma"), names)
    }

    @Test
    fun `phy filter`() {
        val list = listOf(device("ap", -50), device("buds", -50, phy = Phy.Bluetooth, channel = null, freq = null))
        assertEquals(listOf("buds"), DeviceViews.visible(list, PhyFilter.Bluetooth, DeviceSort.Name, false).map { it.name })
        assertEquals(listOf("ap"), DeviceViews.visible(list, PhyFilter.Wifi, DeviceSort.Name, false).map { it.name })
    }

    @Test
    fun `2g channel bars always show 1 to 13`() {
        val bars = ChannelUsage.bars(
            listOf(device("a", -40, channel = 6), device("b", -70, channel = 6), device("c", -60, channel = 11, freq = 2462)),
            WifiBand.Band2g,
        )
        assertEquals((1..13).toList(), bars.map { it.channel })
        assertEquals(ChannelBar(6, 2, -40), bars.single { it.channel == 6 })
        assertEquals(0, bars.single { it.channel == 1 }.count)
    }

    @Test
    fun `5g bars list only channels in use`() {
        val bars = ChannelUsage.bars(
            listOf(device("a", -40, channel = 36, freq = 5180), device("b", -40, channel = 6)),
            WifiBand.Band5g,
        )
        assertEquals(listOf(36), bars.map { it.channel })
    }

    @Test
    fun `radar puts stronger signals closer in`() {
        assertTrue(RadarMath.radiusFraction(-40) < RadarMath.radiusFraction(-80))
        assertEquals(1f, RadarMath.radiusFraction(-120), 0f)
        assertTrue(RadarMath.radiusFraction(-10) > 0f)
    }

    @Test
    fun `radar angle is stable and in range`() {
        val a = RadarMath.angleDegrees("wifi:aa:bb:cc:dd:ee:ff")
        assertEquals(a, RadarMath.angleDegrees("wifi:aa:bb:cc:dd:ee:ff"), 0f)
        assertTrue(a in 0f..360f)
    }
}
