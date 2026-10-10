package com.ardyn.wavetop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WaveTop's colour tokens. Names and values follow NetSeer's `styles.css` so the two apps read
 * as one family: `bg` behind everything, `panel` for bars and sheets, `raised` for cards and
 * buttons, `line`/`lineStrong` for 1px borders, `accent` for the one thing to press.
 */
@Immutable
data class WtColors(
    val isLight: Boolean,
    val bg: Color,
    val panel: Color,
    val raised: Color,
    val hover: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val canvas: Color,
    val select: Color,
    /** Map and legend colours per device kind (NetSeer's --node-*). */
    val nodeAp: Color,
    val nodeBluetooth: Color,
    val nodeWireless: Color,
    /** Signal strength scale: strong / fair / weak. */
    val good: Color,
    val fair: Color,
    val weak: Color,
)

/**
 * WaveTop's own theme (the logo's colours, and the default) plus the three stock themes shared with
 * NetSeer.
 */
enum class AppTheme(val id: String, val label: String, val blurb: String) {
    WaveTop("wavetop", "WaveTop", "The logo's cyan and orange on black"),
    Terrain("terrain", "Terrain", "Warm amber on dark earth"),
    Blueprint("blueprint", "Blueprint", "White lines on drafting blue"),
    Daylight("daylight", "Daylight", "Light, for bright sun and print");

    val colors: WtColors get() = when (this) {
        WaveTop -> WaveTopColors
        Terrain -> TerrainColors
        Blueprint -> BlueprintColors
        Daylight -> DaylightColors
    }

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: WaveTop
    }
}

// From the logo: near-black, the cyan of "WAVE" and the orange of "TOP". Orange is the one thing to
// press; access points take the orange side and Bluetooth the cyan side, as in the mark.
private val WaveTopColors = WtColors(
    isLight = false,
    bg = Color(0xFF07090D), panel = Color(0xFF0C1016), raised = Color(0xFF121821), hover = Color(0xFF19212C),
    line = Color(0xFF1E2833), lineStrong = Color(0xFF2E3C4C),
    text = Color(0xFFEAF6FF), muted = Color(0xFF9DB2C5), faint = Color(0xFF6B7F92),
    accent = Color(0xFFFF7A2F), accentHover = Color(0xFFFF9350), onAccent = Color(0xFF1C0A00),
    accentSoft = Color(0x26FF7A2F),
    danger = Color(0xFFFF4F5E), dangerSoft = Color(0x26FF4F5E),
    canvas = Color(0xFF090C11), select = Color(0xFF5BE3FF),
    nodeAp = Color(0xFFFF8A3D), nodeBluetooth = Color(0xFF2EC5FF), nodeWireless = Color(0xFFFFB15C),
    good = Color(0xFF4FE0B0), fair = Color(0xFFFFB347), weak = Color(0xFFFF5A4F),
)

private val TerrainColors = WtColors(
    isLight = false,
    bg = Color(0xFF15140F), panel = Color(0xFF1C1A14), raised = Color(0xFF25221A), hover = Color(0xFF2D2A20),
    line = Color(0xFF36312A), lineStrong = Color(0xFF4A4436),
    text = Color(0xFFEFE8D8), muted = Color(0xFFA89F88), faint = Color(0xFF7B735F),
    accent = Color(0xFFE0A84A), accentHover = Color(0xFFEAB866), onAccent = Color(0xFF2A1D05),
    accentSoft = Color(0x24E0A84A),
    danger = Color(0xFFE57A6B), dangerSoft = Color(0x24E57A6B),
    canvas = Color(0xFF171610), select = Color(0xFFF5D38A),
    nodeAp = Color(0xFFE0A84A), nodeBluetooth = Color(0xFF5B9BD5), nodeWireless = Color(0xFFE8C77F),
    good = Color(0xFF9CC27A), fair = Color(0xFFE8C77F), weak = Color(0xFFD98A6A),
)

private val BlueprintColors = WtColors(
    isLight = false,
    bg = Color(0xFF0D2644), panel = Color(0xFF102D50), raised = Color(0xFF14365F), hover = Color(0xFF19406F),
    line = Color(0xFF24507F), lineStrong = Color(0xFF3567A0),
    text = Color(0xFFEAF2FF), muted = Color(0xFFA3BDDF), faint = Color(0xFF7896BD),
    accent = Color(0xFFFFFFFF), accentHover = Color(0xFFDFEAFF), onAccent = Color(0xFF0D2644),
    accentSoft = Color(0x1FFFFFFF),
    danger = Color(0xFFFF9B8F), dangerSoft = Color(0x26FF9B8F),
    canvas = Color(0xFF0F2A4A), select = Color(0xFFFFD479),
    nodeAp = Color(0xFFFFD479), nodeBluetooth = Color(0xFF8FD3FF), nodeWireless = Color(0xFFFFE3A3),
    good = Color(0xFFB9F0C9), fair = Color(0xFFFFE3A3), weak = Color(0xFFFFA3C2),
)

private val DaylightColors = WtColors(
    isLight = true,
    bg = Color(0xFFF4F5F7), panel = Color(0xFFFFFFFF), raised = Color(0xFFF7F8FA), hover = Color(0xFFEEF1F5),
    line = Color(0xFFE1E5EB), lineStrong = Color(0xFFC9D0DA),
    text = Color(0xFF18212F), muted = Color(0xFF5B6676), faint = Color(0xFF8A94A3),
    accent = Color(0xFF2563EB), accentHover = Color(0xFF1D4FD8), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1A2563EB),
    danger = Color(0xFFDC2626), dangerSoft = Color(0x14DC2626),
    canvas = Color(0xFFFBFBFC), select = Color(0xFF2563EB),
    nodeAp = Color(0xFFD97706), nodeBluetooth = Color(0xFF2563EB), nodeWireless = Color(0xFFB45309),
    good = Color(0xFF0D9488), fair = Color(0xFFD97706), weak = Color(0xFFDC2626),
)

val LocalWt = staticCompositionLocalOf { WaveTopColors }

/** Shorthand: `Wt.colors.accent`. */
object Wt {
    val colors: WtColors @Composable get() = LocalWt.current
    val mono = FontFamily.Monospace
}

private fun WtColors.toScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = text,
        tertiary = nodeBluetooth,
        background = bg,
        onBackground = text,
        surface = panel,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        surfaceContainerLowest = bg,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = hover,
        surfaceBright = raised,
        surfaceDim = bg,
        inverseSurface = text,
        inverseOnSurface = bg,
        outline = lineStrong,
        outlineVariant = line,
        error = danger,
        onError = if (isLight) Color.White else bg,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        scrim = Color.Black,
    )
}

// NetSeer's type scale (Segoe UI 14px base) on Android's system sans.
private val WtTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp),
    )
}

/** NetSeer's corner radii: 8 for controls, 10–12 for cards, 999 for pills. */
private val WtShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Monospace for MACs, coordinates and other fixed-width values. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/** Section headings inside panes: small caps-style label, as in NetSeer's settings. */
val SectionLabelStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)

@Composable
fun WaveTopTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = theme.colors
    CompositionLocalProvider(LocalWt provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(),
            typography = WtTypography,
            shapes = WtShapes,
            content = content,
        )
    }
}
