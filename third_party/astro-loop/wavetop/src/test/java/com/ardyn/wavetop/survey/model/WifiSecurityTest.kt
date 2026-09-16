package com.ardyn.wavetop.survey.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSecurityTest {
    @Test
    fun `wpa2 psk`() {
        assertEquals("WPA2 (CCMP)", WifiSecurity.describe("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun `wpa3 sae`() {
        assertEquals("WPA3 (CCMP)", WifiSecurity.describe("[WPA3-SAE-CCMP][ESS]"))
    }

    @Test
    fun `open ess`() {
        assertEquals("Open", WifiSecurity.describe("[ESS]"))
    }

    @Test
    fun `wep`() {
        assertEquals("WEP", WifiSecurity.describe("[WEP][ESS]"))
    }

    @Test
    fun `blank is unknown`() {
        assertEquals("Unknown", WifiSecurity.describe(null))
        assertEquals("Unknown", WifiSecurity.describe(" "))
    }
}
