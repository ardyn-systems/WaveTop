package com.ardyn.wavetop.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPairingTest {
    @Test
    fun `parses a full payload with host order preserved`() {
        val p = QrPairing.parse("netseer://pair?v=1&code=070889&host=100.101.102.103:47331&host=192.168.1.20:47331&name=My%20PC")!!
        assertEquals("070889", p.code)   // leading zero kept, opaque string
        assertEquals(listOf("100.101.102.103:47331", "192.168.1.20:47331"), p.hosts)  // remote first
        assertEquals("My PC", p.name)    // percent-decoded
    }

    @Test
    fun `literal colon in host and https scheme survive`() {
        val p = QrPairing.parse("netseer://pair?v=1&code=123456&host=https://netseer.example:443")!!
        assertEquals(listOf("https://netseer.example:443"), p.hosts)
    }

    @Test
    fun `name is optional`() {
        val p = QrPairing.parse("netseer://pair?v=1&code=123456&host=10.0.0.5:47331")!!
        assertNull(p.name)
    }

    @Test
    fun `rejects wrong scheme, version, or missing fields`() {
        assertNull(QrPairing.parse("https://example.com"))
        assertNull(QrPairing.parse("netseer://pair?v=2&code=123456&host=10.0.0.5:47331")) // unknown version
        assertNull(QrPairing.parse("netseer://pair?v=1&host=10.0.0.5:47331"))              // no code
        assertNull(QrPairing.parse("netseer://pair?v=1&code=123456"))                      // no host
        assertNull(QrPairing.parse(null))
        assertNull(QrPairing.parse(""))
    }
}
