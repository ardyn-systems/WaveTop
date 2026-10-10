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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.drive.DriveEntry
import com.ardyn.wavetop.engine.WardriveStatus
import com.ardyn.wavetop.net.LiveState
import com.ardyn.wavetop.model.DeviceSort
import com.ardyn.wavetop.model.DeviceViews
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.PhyFilter
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.theme.Wt

/** Wardrive control plus every saved drive, newest first. */
@Composable
fun DrivesScreen(
    wardrive: WardriveStatus?,
    liveStream: LiveState?,
    drives: List<DriveEntry>,
    settings: Settings,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (DriveEntry) -> Unit,
) {
    val c = Wt.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "control") {
            if (wardrive != null) {
                ActiveDrive(wardrive, liveStream, onStop)
            } else {
                WtCard(Modifier.fillMaxWidth()) {
                    Text("Wardrive", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Hint(
                        "Records every device with the time and GPS position it was heard. It keeps going " +
                            "with the screen off and saves itself when you stop.",
                    )
                    Spacer(Modifier.height(12.dp))
                    WtButton("Start a wardrive", onStart, Modifier.fillMaxWidth(), kind = BtnKind.Primary, icon = Icons.Outlined.FiberManualRecord)
                }
            }
        }
        item(key = "heading") { SectionLabel("Saved drives (${drives.size})", Modifier.padding(start = 4.dp)) }
        if (drives.isEmpty()) {
            item(key = "none") { Hint("Nothing saved yet. Your first wardrive will appear here.", Modifier.padding(start = 4.dp)) }
        }
        items(drives, key = { it.id }) { d -> DriveRow(d, settings, onClick = { onOpen(d) }) }
    }
}

@Composable
private fun ActiveDrive(status: WardriveStatus, liveStream: LiveState?, onStop: () -> Unit) {
    val c = Wt.colors
    val shape = RoundedCornerShape(12.dp)
    val clock = rememberClock(1_000)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.dangerSoft)
            .border(1.dp, c.danger, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(c.danger))
            Spacer(Modifier.width(8.dp))
            Text("Recording", fontWeight = FontWeight.SemiBold, color = c.danger)
            Spacer(Modifier.weight(1f))
            Text(elapsedText(clock - status.startedMs), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
        Spacer(Modifier.height(6.dp))
        Text(status.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Hint("${status.wifiDevices} Wi-Fi · ${status.bluetoothDevices} Bluetooth · ${status.observations} sightings · ${status.geotagged} geotagged")
        if (liveStream != null) {
            Spacer(Modifier.height(8.dp))
            LiveStreamRow(liveStream)
        }
        Spacer(Modifier.height(12.dp))
        WtButton("Stop and save", onStop, Modifier.fillMaxWidth(), kind = BtnKind.Danger, icon = Icons.Outlined.Stop)
    }
}

/** A small status line under the recording card while a wardrive streams to NetSeer. */
@Composable
private fun LiveStreamRow(state: LiveState) {
    val c = Wt.colors
    val (dot, label) = when (state) {
        LiveState.Live -> c.good to "Streaming live to NetSeer"
        LiveState.Connecting -> c.fair to "Connecting to NetSeer…"
        LiveState.Reconnecting -> c.fair to "Reconnecting to NetSeer…"
        LiveState.Error -> c.weak to "Live streaming lost — the drive is still recording"
        LiveState.Idle, LiveState.Closed -> c.muted to "Live streaming stopped"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun DriveRow(entry: DriveEntry, settings: Settings, onClick: () -> Unit) {
    val c = Wt.colors
    val m = entry.meta
    WtCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Route, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (m.interrupted) {
                Text(
                    "Interrupted",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.weak,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, c.weak, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Hint(
            buildString {
                append(dateTimeText(m.startedMs, settings.clock))
                m.endedMs?.let { append(" · ").append(elapsedText(it - m.startedMs)) }
            },
        )
        Hint(
            if (m.observations != null) "${m.wifiDevices} Wi-Fi · ${m.bluetoothDevices} Bluetooth · ${m.geotagged} geotagged sightings"
            else "Open it to count · ${entry.sizeBytes / 1024} KB",
            color = c.faint,
        )
    }
}

/** One saved wardrive, replayed: its numbers, its devices, and where they were heard. */
@Composable
fun DriveDetailScreen(
    open: OpenDrive,
    view: ViewState,
    settings: Settings,
    nowMs: Long,
    onBack: () -> Unit,
    onTab: (DriveTab) -> Unit,
    onFilter: (PhyFilter) -> Unit,
    onSort: (DeviceSort) -> Unit,
    onSelect: (String) -> Unit,
    onBasemap: (Basemap) -> Unit,
    onTime: (Long?) -> Unit,
    onSend: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Wt.colors
    val meta = open.entry.meta
    val drive = open.drive
    val visible = remember(open.devices, view.filter, view.sort, view.sortDescending) {
        DeviceViews.visible(open.devices, view.filter, view.sort, view.sortDescending)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.background(c.raised)) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to drives", tint = c.text) }
                Column(Modifier.weight(1f)) {
                    Text(meta.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint(dateTimeText(meta.startedMs, settings.clock))
                }
                IconButton(onClick = onSend, enabled = drive != null) { Icon(Icons.Outlined.Hub, "Send to NetSeer", tint = c.accent) }
                IconButton(onClick = onShare, enabled = drive != null) { Icon(Icons.Outlined.Share, "Share", tint = c.muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = c.muted) }
            }
            HorizontalDivider(color = c.line)
        }
        when {
            open.loading -> EmptyState(Icons.Outlined.Route, "Opening the drive…", open.entry.file.name)
            drive == null -> EmptyState(Icons.Outlined.Route, "Can't open this drive", open.error ?: "The file couldn't be read.")
            else -> {
                val wifi = open.devices.count { it.phy == Phy.Wifi }
                val bt = open.devices.size - wifi
                val geotagged = drive.observations.count { it.fix != null }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Stat("Duration", elapsedText(drive.durationMs) + if (meta.interrupted) "*" else "", Modifier.weight(1f))
                    Stat("Wi-Fi", "$wifi", Modifier.weight(1f))
                    Stat("Bluetooth", "$bt", Modifier.weight(1f))
                    Stat("Geotagged", "$geotagged", Modifier.weight(1f))
                }
                if (meta.interrupted) Hint("* Interrupted: the drive ended without being stopped, so this is up to its last sighting.", Modifier.padding(horizontal = 14.dp))
                Toolbar {
                    Segmented(DriveTab.entries, open.tab, { it.label }, onTab)
                    Spacer(Modifier.weight(1f))
                    if (open.tab == DriveTab.Map) BasemapMenu(view.basemap, onBasemap) else SortMenu(view.sort, view.sortDescending, onSort)
                }
                Toolbar { PhyFilterControl(view.filter, onFilter) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (open.tab) {
                        DriveTab.Devices -> if (visible.isEmpty()) {
                            EmptyState(Icons.Outlined.Route, "No devices", "Nothing in this drive matches the filter.")
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // A saved drive is history: nothing is "stale", so rows aren't faded.
                                items(visible, key = { it.key }) { d ->
                                    DeviceRow(d, selected = d.key == view.selectedKey, stale = false, onClick = { onSelect(d.key) })
                                }
                            }
                        }
                        DriveTab.Map -> Column(Modifier.fillMaxSize()) {
                            GeoMap(
                                devices = visible,
                                fix = null,
                                highlightKey = view.highlightKey,
                                focusNonce = view.focusNonce,
                                nowMs = nowMs,
                                basemap = view.basemap,
                                showLabels = settings.mapLabels,
                                onDetails = onSelect,
                                live = false,
                                track = open.track,
                                timeMs = open.timeMs,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            TimeBar(meta.startedMs, drive.endMs, open.timeMs, settings, onTime)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Wt.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(c.panel).border(1.dp, c.line, shape).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
        Text(label, fontSize = 11.sp, color = c.muted, maxLines = 1)
    }
}

/** NetSeer's `.geo-timebar`: scrub through the drive; the map shows only what was heard by then. */
@Composable
private fun TimeBar(startMs: Long, endMs: Long, timeMs: Long?, settings: Settings, onTime: (Long?) -> Unit) {
    val c = Wt.colors
    val span = (endMs - startMs).coerceAtLeast(1)
    val at = timeMs ?: endMs
    Column(Modifier.background(c.raised)) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timeText(startMs, settings.clock, seconds = false), fontSize = 11.5.sp, color = c.muted)
            Slider(
                value = (at - startMs).toFloat() / span,
                onValueChange = { f -> onTime(if (f >= 0.999f) null else startMs + (f * span).toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.line,
                ),
            )
            Text(
                if (timeMs == null) "End" else timeText(at, settings.clock),
                fontSize = 11.5.sp,
                color = if (timeMs == null) c.muted else c.text,
                modifier = Modifier.width(76.dp),
            )
        }
    }
}
