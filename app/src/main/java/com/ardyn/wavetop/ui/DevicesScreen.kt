package com.ardyn.wavetop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.engine.EngineState
import com.ardyn.wavetop.model.DeviceSort
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.PhyFilter
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt

/** The toolbar strip above a view, like NetSeer's `.geo-toolbar`: raised, with a bottom rule. */
@Composable
fun Toolbar(content: @Composable () -> Unit) {
    val c = Wt.colors
    Column {
        Row(
            Modifier.fillMaxWidth().background(c.raised).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
        HorizontalDivider(color = c.line)
    }
}

@Composable
fun PhyFilterControl(filter: PhyFilter, onFilter: (PhyFilter) -> Unit) {
    Segmented(
        PhyFilter.entries,
        filter,
        {
            when (it) {
                PhyFilter.All -> "All"
                PhyFilter.Wifi -> "Wi-Fi"
                PhyFilter.Bluetooth -> "Bluetooth"
            }
        },
        onFilter,
    )
}

@Composable
fun SortMenu(sort: DeviceSort, descending: Boolean, onSort: (DeviceSort) -> Unit) {
    val c = Wt.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, c.line, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Sort, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${sortLabel(sort)} ${if (descending) "↓" else "↑"}", fontSize = 13.sp, color = c.text)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.raised) {
            DeviceSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(sortLabel(s), fontWeight = if (s == sort) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSort(s)
                    },
                )
            }
        }
    }
}

private fun sortLabel(s: DeviceSort) = when (s) {
    DeviceSort.Name -> "Name"
    DeviceSort.Type -> "Type"
    DeviceSort.Crypto -> "Security"
    DeviceSort.Signal -> "Signal"
    DeviceSort.Channel -> "Channel"
    DeviceSort.LastSeen -> "Last seen"
}

@Composable
fun DevicesScreen(
    devices: List<TrackedDevice>,
    engine: EngineState,
    view: ViewState,
    settings: Settings,
    nowMs: Long,
    onFilter: (PhyFilter) -> Unit,
    onSort: (DeviceSort) -> Unit,
    onSelect: (String) -> Unit,
    onDismissWelcome: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Toolbar {
            PhyFilterControl(view.filter, onFilter)
            Spacer(Modifier.weight(1f))
            SortMenu(view.sort, view.sortDescending, onSort)
        }
        // No early return out of this Column: returning from an inline composable lambda breaks
        // Compose's group bookkeeping (it crashed the Channels tab once).
        if (devices.isEmpty()) {
            val scanning = engine.wifiScanning || engine.bluetoothScanning
            Column(Modifier.fillMaxSize()) {
                if (!settings.welcomed) WelcomeCard(onDismissWelcome, Modifier.padding(12.dp))
                EmptyState(
                    icon = Icons.Outlined.Radar,
                    title = if (scanning) "Listening…" else "Nothing heard yet",
                    body = when {
                        scanning -> "Nearby Wi-Fi networks and Bluetooth devices appear here as WaveTop hears them."
                        !engine.live -> "Live scanning is paused. Tap Paused at the top to resume."
                        else -> "Move around a little, or check that Wi-Fi and Bluetooth are on."
                    },
                )
            }
        } else DeviceList(devices, view, settings, nowMs, onSelect, onDismissWelcome)
    }
}

@Composable
private fun DeviceList(
    devices: List<TrackedDevice>,
    view: ViewState,
    settings: Settings,
    nowMs: Long,
    onSelect: (String) -> Unit,
    onDismissWelcome: () -> Unit,
) {
    run {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!settings.welcomed) item(key = "welcome") { WelcomeCard(onDismissWelcome, Modifier.padding(bottom = 4.dp)) }
            items(devices, key = { it.key }) { device ->
                DeviceRow(
                    device = device,
                    selected = device.key == view.selectedKey,
                    stale = nowMs != 0L && nowMs - device.lastSeenMs > STALE_MS,
                    onClick = { onSelect(device.key) },
                )
            }
        }
    }
}

@Composable
fun DeviceRow(device: TrackedDevice, selected: Boolean, stale: Boolean, onClick: () -> Unit) {
    val c = Wt.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accentSoft else c.panel)
            .border(1.dp, if (selected) c.accent else c.line, shape)
            .clickable(onClick = onClick)
            .alpha(if (stale) 0.55f else 1f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(device)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(device.mac)
                    if (device.manufacturer.isNotBlank() && !device.manufacturer.startsWith("Unknown")) append(" · ${device.manufacturer}")
                },
                style = MonoStyle.copy(fontSize = 11.sp),
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sparkline(device.history, Modifier.width(30.dp).height(14.dp), samples = 10)
                Spacer(Modifier.width(6.dp))
                val dbm = device.rssi
                Text(
                    if (dbm != null) "$dbm" else "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (dbm != null) signalColor(dbm) else c.faint,
                )
                Text(" dBm", fontSize = 11.sp, color = c.faint)
            }
            Text(
                buildString {
                    if (device.phy == Phy.Wifi) {
                        append("Ch ${device.channel ?: "?"} · ")
                        append(shortCrypto(device))
                    } else {
                        append(device.type)
                    }
                },
                fontSize = 11.5.sp,
                color = if (device.phy == Phy.Wifi) securityColor(device) else c.muted,
                maxLines = 1,
            )
        }
    }
}

/** Coloured circle with the device's kind, matching the map pins and legend. */
@Composable
fun KindBadge(device: TrackedDevice, size: Int = 36) {
    val color = kindColor(device)
    val open = isOpen(device)
    val c = Wt.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(if (open) 2.dp else 1.dp, if (open) c.danger else color.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (device.phy == Phy.Bluetooth) Icons.Outlined.Bluetooth else Icons.Outlined.Wifi,
            contentDescription = if (device.phy == Phy.Bluetooth) "Bluetooth" else "Wi-Fi",
            tint = color,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/** First run: what WaveTop does, in one card (dismissed for good). */
@Composable
private fun WelcomeCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Wt.colors
    WtCard(modifier.fillMaxWidth()) {
        Text("Welcome to WaveTop", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(6.dp))
        Hint(
            "WaveTop lists the Wi-Fi networks and Bluetooth devices around you, pins them on a map, " +
                "and records surveys you can send to NetSeer. It uses only Android's own scanning, and " +
                "nothing leaves your phone unless you send it. Tap any device for its details; themes, " +
                "updates and NetSeer pairing are under the cog.",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            WtButton("Got it", onDismiss, kind = BtnKind.Primary)
        }
    }
}
