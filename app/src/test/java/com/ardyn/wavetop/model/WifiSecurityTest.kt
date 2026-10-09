package com.ardyn.wavetop.model

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
    fun `wpa3 as android reports it is not also wpa2`() {
        assertEquals("WPA3 (CCMP)", WifiSecurity.describe("[RSN-SAE-CCMP][ESS][MFPR]"))
    }

    @Test
    fun `wpa2 and wpa3 transition mode`() {
        assertEquals(
            "WPA3 / WPA2 (CCMP)",
            WifiSecurity.describe("[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]"),
        )
    }

    @Test
    fun `wpa2 psk reported under rsn`() {
        assertEquals("WPA2 (CCMP)", WifiSecurity.describe("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
    }

    @Test
    fun `enhanced open is not wpa2`() {
        assertEquals("Enhanced Open (OWE) (CCMP)", WifiSecurity.describe("[RSN-OWE-CCMP][ESS][MFPR]"))
    }

    @Test
    fun `enterprise`() {
        assertEquals("WPA2-Enterprise (CCMP)", WifiSecurity.describe("[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]"))
        assertEquals("WPA3-Enterprise (GCMP-256)", WifiSecurity.describe("[RSN-EAP-SUITE-B-192-GCMP-256][ESS]"))
    }

    @Test
    fun `mixed wpa and wpa2`() {
        assertEquals(
            "WPA2 / WPA (CCMP)",
            WifiSecurity.describe("[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][ESS]"),
        )
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
