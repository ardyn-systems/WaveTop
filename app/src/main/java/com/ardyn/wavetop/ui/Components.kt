package com.ardyn.wavetop.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.prefs.ClockStyle
import com.ardyn.wavetop.ui.theme.SectionLabelStyle
import com.ardyn.wavetop.ui.theme.Wt
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- buttons --------------------------------------------------------------------------------

enum class BtnKind { Primary, Default, Ghost, Danger }

/** NetSeer's `.btn`: 34px tall, 8px radius, raised fill; `.primary` is the accent. */
@Composable
fun WtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Default,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Wt.colors
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.heightIn(min = 40.dp)
    when (kind) {
        BtnKind.Primary -> Button(
            onClick, m, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) { content() }
        BtnKind.Danger -> Button(
            onClick, m, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = if (c.isLight) Color.White else c.bg),
        ) { content() }
        BtnKind.Default -> OutlinedButton(
            onClick, m, enabled = enabled, shape = shape, contentPadding = padding,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.lineStrong),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.raised, contentColor = c.text),
        ) { content() }
        BtnKind.Ghost -> TextButton(
            onClick, m, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = c.muted),
        ) { content() }
    }
}

// --- surfaces ---------------------------------------------------------------------------------

/** NetSeer's `.version-card`-style container: raised, 1px line, 12px radius. */
@Composable
fun WtCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Wt.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (highlight) c.accentSoft else c.raised)
            .border(1.dp, if (highlight) c.accent else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

/** Small uppercase heading inside a pane (`.settings-pane h3`). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = SectionLabelStyle,
        color = Wt.colors.muted,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Wt.colors.muted) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** NetSeer's `.switch-row`: title + small explanation, switch on the right, all in a bordered row. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Wt.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = if (enabled) c.text else c.faint)
            if (subtitle != null) Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.bg,
                uncheckedBorderColor = c.lineStrong,
            ),
        )
    }
}

/** A row of mutually exclusive options in one bordered pill group. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Wt.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.line, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Text(
                label(option),
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) c.text else c.muted,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) c.accentSoft else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

/** The "New" pill beside Updates (`.nav-badge`). */
@Composable
fun NavBadge(text: String = "New") {
    val c = Wt.colors
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = c.onAccent,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** The dot on the settings cog when an update is waiting (`.update-dot`). */
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    val c = Wt.colors
    Box(modifier.size(10.dp).clip(CircleShape).background(c.panel).padding(2.dp).clip(CircleShape).background(c.accent))
}

/** One-line outcome under an action: green good, accent news, red trouble. */
@Composable
fun StatusLine(status: TaskStatus, modifier: Modifier = Modifier) {
    val c = Wt.colors
    val (text, color) = when (status) {
        TaskStatus.Idle -> return
        is TaskStatus.Working -> status.what to c.muted
        is TaskStatus.Done -> status.message to c.good
        is TaskStatus.Failed -> status.message to c.danger
    }
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = color, modifier = modifier.padding(top = 10.dp))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Wt.colors
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.raised).border(1.dp, c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

// --- data presentation -----------------------------------------------------------------------

@Composable
fun signalColor(dbm: Int): Color {
    val c = Wt.colors
    return when {
        dbm >= -55 -> c.good
        dbm >= -70 -> c.fair
        else -> c.weak
    }
}

fun signalLabel(dbm: Int) = when {
    dbm >= -55 -> "Strong"
    dbm >= -70 -> "Fair"
    else -> "Weak"
}

/** Map/legend colour by kind of device, like NetSeer's node colours. */
@Composable
fun kindColor(device: TrackedDevice): Color =
    if (device.phy == Phy.Bluetooth) Wt.colors.nodeBluetooth else Wt.colors.nodeAp

fun isOpen(device: TrackedDevice) = device.phy == Phy.Wifi && device.crypto.startsWith("Open")

/** Colour for the security text: open is a warning, legacy is a caution, modern is fine. */
@Composable
fun securityColor(device: TrackedDevice): Color {
    val c = Wt.colors
    if (device.phy == Phy.Bluetooth) return c.muted
    val s = device.crypto
    return when {
        s.startsWith("Open") -> c.danger
        s.startsWith("WEP") || s.startsWith("WPA /") || s == "WPA" || s.startsWith("WPA (") -> c.weak
        s.startsWith("WPA3") || s.startsWith("Enhanced Open") -> c.good
        else -> c.text
    }
}

/** "WPA3 / WPA2 (CCMP)" -> "WPA3/WPA2"; the cipher lives in the details. */
fun shortCrypto(device: TrackedDevice): String {
    if (device.phy == Phy.Bluetooth) return "—"
    return device.crypto
        .replace("Enhanced Open (OWE)", "OWE")
        .replace("-Enterprise", "-E")
        .substringBefore(" (")
        .replace(" / ", "/")
}

/** Signal sparkline: one bar per recent sample, height by dBm. */
@Composable
fun Sparkline(history: List<Int>, modifier: Modifier = Modifier, samples: Int = 12) {
    val recent = history.takeLast(samples)
    val good = Wt.colors.good
    val fair = Wt.colors.fair
    val weak = Wt.colors.weak
    Canvas(modifier) {
        if (recent.isEmpty()) return@Canvas
        val slot = size.width / samples
        val barW = (slot * 0.66f).coerceAtLeast(1f)
        val offset = samples - recent.size
        recent.forEachIndexed { i, dbm ->
            val t = ((dbm + 100).coerceIn(5, 70) / 70f)
            val h = size.height * t
            val color = when {
                dbm >= -55 -> good
                dbm >= -70 -> fair
                else -> weak
            }
            drawRect(color, topLeft = Offset((offset + i) * slot, size.height - h), size = Size(barW, h))
        }
    }
}

// --- time -------------------------------------------------------------------------------------

/** Wall-clock time that ticks every [periodMs]; read it only in the small composable showing it. */
@Composable
fun rememberClock(periodMs: Long): Long {
    val now by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

private fun uses24h(context: Context, style: ClockStyle) = when (style) {
    ClockStyle.H24 -> true
    ClockStyle.H12 -> false
    ClockStyle.Auto -> DateFormat.is24HourFormat(context)
}

/** Clock time respecting Settings → General → Time. */
@Composable
fun timeText(epochMs: Long, style: ClockStyle, seconds: Boolean = true): String {
    val ctx = LocalContext.current
    val pattern = if (uses24h(ctx, style)) {
        if (seconds) "HH:mm:ss" else "HH:mm"
    } else {
        if (seconds) "h:mm:ss a" else "h:mm a"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

@Composable
fun dateTimeText(epochMs: Long, style: ClockStyle): String {
    val day = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
    return "$day · ${timeText(epochMs, style, seconds = false)}"
}

/** 1:05 / 1:02:05 */
fun elapsedText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun agoText(deltaMs: Long): String {
    val s = (deltaMs / 1000).coerceAtLeast(0)
    return when {
        s < 3 -> "just now"
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        else -> "${s / 3600}h ago"
    }
}

/** Devices not heard for a minute are drawn faded, as Kismet and NetSeer do. */
const val STALE_MS = 60_000L
