package com.ardyn.wavetop.survey.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RfChannelTest {
    @Test
    fun `2g channel 6`() {
        assertEquals(6, RfChannel.channelNumber(2437))
        assertEquals("2.4 GHz", RfChannel.band(2437))
    }

    @Test
    fun `5g channel 36`() {
        assertEquals(36, RfChannel.channelNumber(5180))
        assertEquals("5 GHz", RfChannel.band(5180))
    }

    @Test
    fun `6g channel 1`() {
        assertEquals(1, RfChannel.channelNumber(5955))
        assertEquals("6 GHz", RfChannel.band(5955))
    }
}
