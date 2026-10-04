package com.ardyn.wavetop.survey.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ardyn.wavetop.survey.model.SurveyGate
import com.ardyn.wavetop.survey.model.SurveySection
import com.ardyn.wavetop.survey.model.SurveyTab
import com.ardyn.wavetop.survey.permissions.SurveyPermissions
import com.ardyn.wavetop.survey.ui.theme.LocalWaveTopType
import com.ardyn.wavetop.survey.ui.theme.WaveTopPalette
import com.ardyn.wavetop.survey.ui.theme.WaveTopTheme
import androidx.compose.foundation.text.BasicText
import kotlin.math.roundToInt

@Composable
fun WaveTopSurveyApp(viewModel: SurveyViewModel = viewModel()) {
    WaveTopTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.onPermissionsChanged() }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            if (!SurveyPermissions.allGranted(context)) {
                permissionLauncher.launch(SurveyPermissions.required())
            }
            // Scan only while the screen is visible. Adding the observer replays the events up
            // to the current state, so ON_START also covers the first composition.
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.start()
                    Lifecycle.Event.ON_RESUME -> viewModel.onPermissionsChanged()
                    Lifecycle.Event.ON_STOP -> viewModel.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stop()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(WaveTopPalette.Background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Starfield(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                Header()
                TabBar(
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
                if (state.tab == SurveyTab.Wifi && state.wifiScanThrottled && state.gate == SurveyGate.Ready) {
                    BasicText(
                        "Android is limiting Wi-Fi scans. Showing the most recent results.",
                        style = LocalWaveTopType.current.caption.copy(color = WaveTopPalette.AccentYellow),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val scanning = if (state.tab == SurveyTab.Wifi) state.wifiScanning else state.bluetoothScanning
                    val emptyWifi = state.tab == SurveyTab.Wifi && state.wifiResults.isEmpty()
                    val emptyBt = state.tab == SurveyTab.Bluetooth && state.bluetoothResults.isEmpty()
                    when {
                        state.gate != SurveyGate.Ready -> GatePanel(
                            gate = state.gate,
                            tab = state.tab,
                            onGrant = { permissionLauncher.launch(SurveyPermissions.required()) },
                            onOpenSettings = {
                                val intent = when (state.gate) {
                                    SurveyGate.RadioOff -> if (state.tab == SurveyTab.Wifi) {
                                        Intent(Settings.ACTION_WIFI_SETTINGS)
                                    } else {
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    }
                                    SurveyGate.LocationOff -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                }
                                context.startActivity(intent)
                            },
                        )
                        emptyWifi || emptyBt -> EmptyResults(scanning)
                        state.tab == SurveyTab.Wifi -> ResultList(
                            keys = state.wifiResults.map { it.bssid },
                            sections = state.wifiResults.map { it.sections() },
                            footers = state.wifiResults.map { lastSeen(it.lastSeenEpochMs) },
                        )
                        else -> ResultList(
                            keys = state.bluetoothResults.map { it.address },
                            sections = state.bluetoothResults.map { it.sections() },
                            footers = state.bluetoothResults.map { lastSeen(it.lastSeenEpochMs) },
                        )
                    }
                }
                ScanBar(
                    scanning = if (state.tab == SurveyTab.Wifi) state.wifiScanning else state.bluetoothScanning,
                    enabled = state.gate == SurveyGate.Ready,
                    onScan = viewModel::scan,
                    onBack = { (context as? ComponentActivity)?.finish() },
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val type = LocalWaveTopType.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("WAVETOP", style = type.display)
        Spacer(Modifier.height(6.dp))
        BasicText("SURVEY", style = type.section.copy(fontSize = 12.sp))
    }
}

@Composable
private fun TabBar(selected: SurveyTab, onSelect: (SurveyTab) -> Unit) {
    val type = LocalWaveTopType.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SurveyTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    tab.name.uppercase(),
                    style = type.tab.copy(
                        color = if (active) WaveTopPalette.Hud else WaveTopPalette.Muted,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (active) WaveTopPalette.AccentCyan else WaveTopPalette.Faint),
                )
            }
        }
    }
}

@Composable
private fun ResultList(
    keys: List<String>,
    sections: List<List<SurveySection>>,
    footers: List<String>,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(keys.size, key = { keys[it] }) { index ->
            SurveyRecordCard(sections[index], footers[index])
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun SurveyRecordCard(sections: List<SurveySection>, footer: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(WaveTopPalette.Panel, RectangleShape)
            .border(2.dp, WaveTopPalette.Faint, RectangleShape)
            .padding(14.dp),
    ) {
        sections.forEachIndexed { i, section ->
            if (i > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WaveTopPalette.Faint.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(10.dp))
            }
            SectionBlock(section)
        }
        Spacer(Modifier.height(10.dp))
        val type = LocalWaveTopType.current
        BasicText(footer, style = type.caption.copy(color = WaveTopPalette.Muted))
    }
}

@Composable
fun SectionBlock(section: SurveySection) {
    when (section) {
        is SurveySection.Rssi -> RssiSection(section)
        is SurveySection.Identity -> IdentitySection(section)
        is SurveySection.Encryption -> LabeledSection("ENCRYPTION", section.type)
        is SurveySection.ChannelFrequency -> ChannelSection(section)
        is SurveySection.Manufacturer -> LabeledSection("MANUFACTURER", section.name)
    }
}

@Composable
private fun RssiSection(section: SurveySection.Rssi) {
    val type = LocalWaveTopType.current
    val dbm = section.dbm
    BasicText("RSSI", style = type.section)
    Spacer(Modifier.height(6.dp))
    if (dbm == null) {
        BasicText("Not reported", style = type.body.copy(color = WaveTopPalette.Muted))
        return
    }
    val quality = rssiQuality(dbm)
    val fill = rssiColor(dbm)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText("$dbm dBm", style = type.bodyBold.copy(color = fill))
        Spacer(Modifier.width(10.dp))
        BasicText(quality, style = type.caption.copy(color = fill))
    }
    Spacer(Modifier.height(8.dp))
    RssiBar(dbm)
}

@Composable
private fun RssiBar(dbm: Int) {
    val t = ((dbm + 100).coerceIn(0, 70) / 70f)
    val fill = rssiColor(dbm)
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        drawRect(WaveTopPalette.HealthBg, size = size)
        drawRect(fill, size = Size(size.width * t, size.height))
        drawRect(WaveTopPalette.Hud, size = size, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun IdentitySection(section: SurveySection.Identity) {
    val type = LocalWaveTopType.current
    BasicText("SSID AND MAC ADDRESS", style = type.section)
    Spacer(Modifier.height(6.dp))
    KeyValue(section.nameLabel, section.name, type.bodyBold)
    Spacer(Modifier.height(4.dp))
    KeyValue(section.addressLabel, section.mac.ifBlank { "—" }, type.body)
}

@Composable
private fun ChannelSection(section: SurveySection.ChannelFrequency) {
    val type = LocalWaveTopType.current
    BasicText("CHANNEL AND FREQUENCY", style = type.section)
    Spacer(Modifier.height(6.dp))
    BasicText(section.channel, style = type.bodyBold)
    Spacer(Modifier.height(2.dp))
    BasicText(section.frequency, style = type.body.copy(color = WaveTopPalette.Dim))
    if (!section.extra.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        BasicText(section.extra, style = type.caption.copy(color = WaveTopPalette.AccentCyan))
    }
}

@Composable
private fun LabeledSection(title: String, value: String) {
    val type = LocalWaveTopType.current
    BasicText(title, style = type.section)
    Spacer(Modifier.height(6.dp))
    BasicText(value, style = type.bodyBold)
}

@Composable
private fun KeyValue(label: String, value: String, valueStyle: TextStyle) {
    val type = LocalWaveTopType.current
    Row(Modifier.fillMaxWidth()) {
        BasicText(
            label,
            style = type.caption.copy(color = WaveTopPalette.Label),
            modifier = Modifier.width(52.dp),
        )
        BasicText(
            value,
            style = valueStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GatePanel(
    gate: SurveyGate,
    tab: SurveyTab,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val type = LocalWaveTopType.current
    val radio = if (tab == SurveyTab.Wifi) "Wi-Fi" else "Bluetooth"
    val (title, body, action, actionLabel) = when (gate) {
        SurveyGate.PermissionDenied -> Quad(
            "PERMISSION DENIED",
            "Location, nearby Wi-Fi, and Bluetooth scan access are required to list networks and devices.",
            onGrant,
            "GRANT ACCESS",
        )
        SurveyGate.RadioOff -> Quad(
            "$radio OFF",
            "Turn $radio on to survey nearby signals.",
            onOpenSettings,
            "OPEN SETTINGS",
        )
        SurveyGate.LocationOff -> Quad(
            "LOCATION OFF",
            "Android only returns scan results while location is enabled.",
            onOpenSettings,
            "OPEN SETTINGS",
        )
        SurveyGate.Unavailable -> Quad(
            "RADIO UNAVAILABLE",
            "This device does not expose a $radio adapter to public scan APIs.",
            onOpenSettings,
            "OPEN SETTINGS",
        )
        SurveyGate.Ready -> return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(title, style = type.section.copy(color = WaveTopPalette.AccentYellow))
        Spacer(Modifier.height(14.dp))
        BasicText(body, style = type.body.copy(color = WaveTopPalette.Dim))
        Spacer(Modifier.height(22.dp))
        HudButton(actionLabel, action)
    }
}

@Composable
private fun EmptyResults(scanning: Boolean) {
    val type = LocalWaveTopType.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            if (scanning) "SCANNING" else "NO RESULTS",
            style = type.section.copy(
                color = if (scanning) WaveTopPalette.AccentCyan else WaveTopPalette.AccentYellow,
            ),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            if (scanning) {
                "Listening with public Android scan APIs."
            } else {
                "Nothing nearby yet. Scan again."
            },
            style = type.body.copy(color = WaveTopPalette.Dim),
        )
    }
}

@Composable
private fun ScanBar(scanning: Boolean, enabled: Boolean, onScan: () -> Unit, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudButton("BACK", onBack, Modifier.weight(1f), dim = true)
        HudButton(
            if (scanning) "SCANNING…" else "SCAN",
            onScan,
            Modifier.weight(1f),
            enabled = enabled && !scanning,
        )
    }
}

@Composable
private fun HudButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dim: Boolean = false,
) {
    val type = LocalWaveTopType.current
    val border = when {
        !enabled -> WaveTopPalette.Faint
        dim -> WaveTopPalette.Muted
        else -> WaveTopPalette.AccentCyan
    }
    Box(
        modifier
            .border(2.dp, border, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = type.tab.copy(
                color = if (enabled) WaveTopPalette.Hud else WaveTopPalette.Faint,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun Starfield(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        var seed = 42
        fun next(): Int {
            seed = seed * 1103515245 + 12345
            return seed
        }
        repeat(70) {
            val x = ((next() ushr 8).and(0x7fff) / 32767f) * w
            val y = ((next() ushr 8).and(0x7fff) / 32767f) * h
            val layer = it % 3
            val color = when (layer) {
                0 -> WaveTopPalette.StarFar
                1 -> WaveTopPalette.StarMid
                else -> WaveTopPalette.StarNear
            }
            val r = if (layer == 2) 1.6f else 1.1f
            drawCircle(color, r, Offset(x, y))
        }
    }
}

private fun rssiColor(dbm: Int) = when {
    dbm >= -55 -> WaveTopPalette.AccentGreen
    dbm >= -70 -> WaveTopPalette.AccentYellow
    else -> WaveTopPalette.AccentGold
}

private fun rssiQuality(dbm: Int) = when {
    dbm >= -55 -> "STRONG"
    dbm >= -70 -> "FAIR"
    else -> "WEAK"
}

private fun lastSeen(epochMs: Long): String {
    val delta = ((System.currentTimeMillis() - epochMs) / 1000f).roundToInt().coerceAtLeast(0)
    return when {
        delta < 3 -> "Last seen just now"
        delta < 60 -> "Last seen ${delta}s ago"
        else -> "Last seen ${delta / 60}m ago"
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
