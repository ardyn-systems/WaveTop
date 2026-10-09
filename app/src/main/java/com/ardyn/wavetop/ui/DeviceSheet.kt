package com.ardyn.wavetop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.model.SurveySection
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    device: TrackedDevice,
    nowMs: Long,
    settings: Settings,
    onDismiss: () -> Unit,
    onShowOnMap: (() -> Unit)?,
) {
    val c = Wt.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = c.panel,
        contentColor = c.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(device, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${device.mac} · ${typeLabel(device)}", style = MonoStyle, color = c.muted)
                }
            }

            if (onShowOnMap != null) {
                Spacer(Modifier.height(14.dp))
                WtButton(
                    if (device.bestFix != null) "Show on map" else "Show on radar",
                    onShowOnMap,
                    Modifier.fillMaxWidth(),
                    kind = BtnKind.Primary,
                    icon = if (device.bestFix != null) Icons.Outlined.Map else Icons.Outlined.Radar,
                )
                if (device.bestFix == null) {
                    Hint(
                        "Not on the street map yet: it hasn't been heard while the phone had an accurate GPS fix.",
                        Modifier.padding(top = 6.dp),
                    )
                }
            }

            SectionLabel("Signal")
            SignalSummary(device)
            Spacer(Modifier.height(10.dp))
            SignalGraph(device.history, Modifier.fillMaxWidth().height(96.dp))

            device.sections.forEach { section ->
                when (section) {
                    is SurveySection.Rssi -> Unit // the graph covers it
                    is SurveySection.Identity -> {
                        SectionLabel("Identity")
                        Fact(section.nameLabel, section.name)
                        Fact(section.addressLabel, section.mac.ifBlank { "—" }, mono = true)
                    }
                    is SurveySection.Encryption -> {
                        SectionLabel("Security")
                        Fact("Encryption", section.type, valueColor = securityColor(device))
                    }
                    is SurveySection.ChannelFrequency -> {
                        SectionLabel("Channel and frequency")
                        Fact("Channel", section.channel)
                        Fact("Frequency", section.frequency)
                        section.extra?.takeIf { it.isNotBlank() }?.let { Fact("PHY", it) }
                    }
                    is SurveySection.Manufacturer -> {
                        SectionLabel("Manufacturer")
                        Fact("Vendor", section.name)
                    }
                }
            }

            SectionLabel("Seen")
            Fact("First", timeText(device.firstSeenMs, settings.clock))
            Fact("Last", "${timeText(device.lastSeenMs, settings.clock)} (${agoText(nowMs - device.lastSeenMs)})")
            device.bestFix?.let { f ->
                Fact(
                    "Strongest at",
                    String.format(Locale.US, "%.5f, %.5f  ±%d m", f.lat, f.lon, f.accuracyM.toInt()),
                    mono = true,
                )
            }
        }
    }
}

private fun typeLabel(d: TrackedDevice) = when (d.type) {
    "AP" -> "Wi-Fi access point"
    "BLE" -> "Bluetooth LE"
    "BT" -> "Bluetooth Classic"
    else -> "Bluetooth Classic + LE"
}

/** NetSeer's `dl.facts` row: muted label column, value beside it. */
@Composable
fun Fact(label: String, value: String, mono: Boolean = false, valueColor: androidx.compose.ui.graphics.Color = Wt.colors.text) {
    val c = Wt.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(110.dp))
        Text(
            value,
            style = if (mono) MonoStyle.copy(fontSize = 13.sp) else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SignalSummary(device: TrackedDevice) {
    val c = Wt.colors
    val now = device.rssi
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            now?.let { "$it dBm" } ?: "Not reported",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = now?.let { signalColor(it) } ?: c.muted,
        )
        if (now != null) Text(signalLabel(now), color = signalColor(now), modifier = Modifier.padding(bottom = 3.dp))
        Spacer(Modifier.weight(1f))
        Text(
            "min ${device.minRssi ?: "—"} · max ${device.maxRssi ?: "—"}",
            style = MonoStyle,
            color = c.muted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** Recent samples on a fixed −100…−30 dBm scale. */
@Composable
private fun SignalGraph(history: List<Int>, modifier: Modifier) {
    val c = Wt.colors
    val shape = RoundedCornerShape(10.dp)
    val last = history.lastOrNull()
    val dot = last?.let { signalColor(it) } ?: c.muted
    Canvas(
        modifier
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.line, shape)
            .padding(8.dp),
    ) {
        fun y(dbm: Int) = size.height * ((-30 - dbm.coerceIn(-100, -30)) / 70f)
        listOf(-50, -70, -90).forEach { g ->
            drawLine(c.line, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1f)
        }
        if (history.size < 2) {
            last?.let { drawCircle(dot, 3.dp.toPx(), Offset(size.width, y(it))) }
            return@Canvas
        }
        val step = size.width / (history.size - 1)
        val path = Path()
        history.forEachIndexed { i, dbm ->
            val p = Offset(i * step, y(dbm))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, c.accent, style = Stroke(2.dp.toPx()))
        drawCircle(dot, 3.5.dp.toPx(), Offset(size.width, y(history.last())))
    }
}
