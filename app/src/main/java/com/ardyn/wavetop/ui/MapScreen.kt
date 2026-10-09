package com.ardyn.wavetop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.engine.EngineState
import com.ardyn.wavetop.model.PhyFilter
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.theme.Wt

@Composable
fun MapScreen(
    devices: List<TrackedDevice>,
    engine: EngineState,
    view: ViewState,
    settings: Settings,
    nowMs: Long,
    onFilter: (PhyFilter) -> Unit,
    onMode: (MapMode) -> Unit,
    onBasemap: (Basemap) -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Toolbar {
            Segmented(MapMode.entries, view.mapMode, { it.label }, onMode)
            Spacer(Modifier.weight(1f))
            if (view.mapMode == MapMode.Street) BasemapMenu(view.basemap, onBasemap)
        }
        Toolbar { PhyFilterControl(view.filter, onFilter) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (view.mapMode) {
                MapMode.Street -> GeoMap(
                    devices = devices,
                    fix = engine.fix,
                    highlightKey = view.highlightKey,
                    focusNonce = view.focusNonce,
                    nowMs = nowMs,
                    basemap = view.basemap,
                    showLabels = settings.mapLabels,
                    onDetails = onSelect,
                    modifier = Modifier.fillMaxSize(),
                )
                MapMode.Radar -> RadarView(
                    devices = devices,
                    highlightKey = view.highlightKey,
                    sweeping = engine.wifiScanning || engine.bluetoothScanning,
                    nowMs = nowMs,
                    showLabels = settings.mapLabels,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** NetSeer's Basemap selector: streets (online, cached once seen) or nothing behind the pins. */
@Composable
fun BasemapMenu(basemap: Basemap, onBasemap: (Basemap) -> Unit) {
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
            Icon(Icons.Outlined.Layers, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(basemap.label, fontSize = 13.sp, color = c.text)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.raised) {
            Basemap.entries.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.label, fontWeight = if (b == basemap) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onBasemap(b)
                    },
                )
            }
        }
    }
}
