package com.astroloop.game.splash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoLongPressGateTest {

    @Test
    fun `hold shorter than 10 seconds does not open WaveTop`() {
        val gate = LogoLongPressGate(10_000L)
        gate.press(0L)
        assertFalse(gate.shouldOpenWaveTop(9_999L))
        gate.release()
        assertFalse(gate.shouldOpenWaveTop(20_000L))
    }

    @Test
    fun `continuous 10 second press opens WaveTop once`() {
        val gate = LogoLongPressGate(10_000L)
        gate.press(100L)
        assertTrue(gate.shouldOpenWaveTop(10_100L))
        assertFalse("must not fire twice", gate.shouldOpenWaveTop(11_000L))
    }

    @Test
    fun `release before 10 seconds cancels the hold`() {
        val gate = LogoLongPressGate(10_000L)
        gate.press(0L)
        gate.release()
        gate.press(5_000L)
        assertFalse(gate.shouldOpenWaveTop(14_999L))
        assertTrue(gate.shouldOpenWaveTop(15_000L))
    }
}
