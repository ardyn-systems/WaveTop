package com.ardyn.wavetop.oui

import org.junit.Assert.assertEquals
import org.junit.Test

class OuiLookupTest {
    private val lookup = OuiLookup.fromMap(
        mapOf(
            0x00000C to "Cisco Systems, Inc",
            0x001A11 to "Google, Inc.",
        ),
    )

    @Test
    fun `matches 24-bit OUI`() {
        assertEquals("Cisco Systems, Inc", lookup.manufacturerFor("00:00:0C:11:22:33"))
        assertEquals("Google, Inc.", lookup.manufacturerFor("00-1A-11-AA-BB-CC"))
    }

    @Test
    fun `unknown when no match`() {
        assertEquals("Unknown", lookup.manufacturerFor("00:11:22:33:44:55"))
    }

    @Test
    fun `randomized locally administered mac is unknown`() {
        assertEquals("Unknown (randomized MAC)", lookup.manufacturerFor("02:00:00:00:00:01"))
    }
}
