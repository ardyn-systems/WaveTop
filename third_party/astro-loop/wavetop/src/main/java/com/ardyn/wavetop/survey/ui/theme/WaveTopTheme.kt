package com.ardyn.wavetop.survey.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.survey.R

/**
 * Visual tokens copied from Astro Loop's host look:
 * [com.astroloop.game.core.GameConfig] colors, HUD 2px strokes, Exo 2 body,
 * Orbitron display with wide tracking.
 */
object WaveTopPalette {
    val Background = Color(0xFF000011)
    val Hud = Color(0xFFFFFFFF)
    val Dim = Color(0xFFCCCCCC)
    val Muted = Color(0xFF888888)
    val Faint = Color(0xFF444444)
    val Panel = Color(0xFF111111)
    val PanelBorder = Color(0xFFFFFFFF)
    val Label = Color(0xFF88AACC)
    val AccentCyan = Color(0xFF00FFFF)
    val AccentGreen = Color(0xFF00FF00)
    val AccentYellow = Color(0xFFFFFF00)
    val AccentGold = Color(0xFFFFDD44)
    val AccentBlue = Color(0xFF4488FF)
    val HealthBg = Color(0xFF333333)
    val StarFar = Color(0xFF444444)
    val StarMid = Color(0xFF888888)
    val StarNear = Color(0xFFCCCCCC)
}

private val Exo2 = FontFamily(
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_bold, FontWeight.Bold),
)

private val Orbitron = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_bold, FontWeight.Bold),
)

object WaveTopType {
    val display = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.15.em,
        color = WaveTopPalette.Hud,
    )
    val section = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
        color = WaveTopPalette.Label,
    )
    val body = TextStyle(
        fontFamily = Exo2,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = WaveTopPalette.Hud,
    )
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val caption = TextStyle(
        fontFamily = Exo2,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = WaveTopPalette.Dim,
    )
    val tab = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.12.em,
        color = WaveTopPalette.Hud,
    )
}

val LocalWaveTopType = staticCompositionLocalOf { WaveTopType }

@Composable
fun WaveTopTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWaveTopType provides WaveTopType) {
        content()
    }
}
