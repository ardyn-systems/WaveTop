package com.astroloop.game.splash

/**
 * Hidden 10-second continuous press on the Astro Loop logo/splash
 * opens WaveTop in-process. Finger-up or cancel before 10s is a miss.
 */
class LogoLongPressGate(private val holdDurationMs: Long = 10_000L) {
    private var pressedAtMs: Long? = null
    private var opened: Boolean = false

    fun press(nowMs: Long) {
        if (pressedAtMs == null) pressedAtMs = nowMs
    }

    fun release() {
        pressedAtMs = null
    }

    fun isHolding(): Boolean = pressedAtMs != null

    fun hasOpened(): Boolean = opened

    fun reset() {
        pressedAtMs = null
        opened = false
    }

    fun remainingMs(nowMs: Long): Long {
        val start = pressedAtMs ?: return holdDurationMs
        return (holdDurationMs - (nowMs - start)).coerceAtLeast(0L)
    }

    fun shouldOpenWaveTop(nowMs: Long): Boolean {
        val start = pressedAtMs ?: return false
        if (opened) return false
        if (nowMs - start >= holdDurationMs) {
            opened = true
            return true
        }
        return false
    }
}
