package com.astroloop.game.splash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.astroloop.game.R
import com.astroloop.game.core.GameConfig
import com.astroloop.game.render.FontManager
import kotlin.math.min

/**
 * Astro Loop logo/splash. A continuous 10s press opens WaveTop; otherwise
 * the game continues after a short idle delay or a shorter tap/release.
 */
class SplashLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onContinueToGame: (() -> Unit)? = null
    var onOpenWaveTop: (() -> Unit)? = null

    private val gate = LogoLongPressGate(HOLD_MS)
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private val logo: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GameConfig.COLOR_HUD
        textAlign = Paint.Align.CENTER
        typeface = FontManager.getDisplayBold()
        letterSpacing = 0.15f
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val idleAdvance = Runnable { finishToGame() }
    private val holdComplete = Runnable {
        if (gate.shouldOpenWaveTop(now())) {
            finished = true
            handler.removeCallbacks(idleAdvance)
            onOpenWaveTop?.invoke()
        }
    }

    init {
        setBackgroundColor(GameConfig.COLOR_BACKGROUND)
        isClickable = true
        scheduleIdle()
    }

    fun onHostResumed() {
        if (finished || gate.isHolding() || gate.hasOpened()) return
        scheduleIdle()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(idleAdvance)
                handler.removeCallbacks(holdComplete)
                gate.press(now())
                handler.postDelayed(holdComplete, HOLD_MS)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdComplete)
                val opened = gate.hasOpened()
                gate.release()
                if (!opened) finishToGame()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStars(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val logoSize = (min(width, height) * 0.42f).toInt().coerceAtLeast(120)
        logo?.let {
            val left = (cx - logoSize / 2f).toInt()
            val top = (cy - logoSize / 2f - height * 0.06f).toInt()
            it.setBounds(left, top, left + logoSize, top + logoSize)
            it.draw(canvas)
        }
        titlePaint.textSize = (width * 0.07f).coerceIn(28f, 56f)
        canvas.drawText("ASTRO LOOP", cx, cy + logoSize * 0.42f, titlePaint)
    }

    private fun drawStars(canvas: Canvas) {
        var seed = 42
        fun next(): Int {
            seed = seed * 1_103_515_245 + 12_345
            return seed
        }
        repeat(80) {
            val x = ((next() ushr 8) and 0x7fff) / 32767f * width
            val y = ((next() ushr 8) and 0x7fff) / 32767f * height
            starPaint.color = when (it % 3) {
                0 -> GameConfig.COLOR_STAR_FAR
                1 -> GameConfig.COLOR_STAR_MID
                else -> GameConfig.COLOR_STAR_NEAR
            }
            canvas.drawCircle(x, y, if (it % 3 == 2) 2.2f else 1.4f, starPaint)
        }
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleAdvance)
        handler.postDelayed(idleAdvance, IDLE_MS)
    }

    private fun finishToGame() {
        if (finished || gate.hasOpened()) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        onContinueToGame?.invoke()
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    companion object {
        const val HOLD_MS = 10_000L
        const val IDLE_MS = 3_000L
    }
}
