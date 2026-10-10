package com.ardyn.wavetop.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ardyn.wavetop.R
import com.ardyn.wavetop.engine.EngineState
import com.ardyn.wavetop.engine.RadioState
import com.ardyn.wavetop.engine.SurveyAccess
import com.ardyn.wavetop.model.DeviceViews
import com.ardyn.wavetop.model.PhyFilter
import com.ardyn.wavetop.permissions.SurveyPermissions
import com.ardyn.wavetop.ui.settings.SettingsScreen
import com.ardyn.wavetop.ui.theme.WaveTopTheme
import com.ardyn.wavetop.ui.theme.Wt
import kotlinx.coroutines.launch
import java.io.File

/** Which modal is open; one at a time. */
private enum class Modal { None, StartWardrive, StopWardrive, NetSeer, Share, DeleteDrive }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    WaveTopTheme(settings.theme) {
        val engine by vm.engineState.collectAsStateWithLifecycle()
        val view by vm.view.collectAsStateWithLifecycle()
        val updates by vm.updates.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var modal by remember { mutableStateOf(Modal.None) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { vm.onPermissionsChanged() }
        // The wardrive notification needs this on Android 13+; recording works either way.
        val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            if (!SurveyPermissions.allGranted(context)) permissionLauncher.launch(SurveyPermissions.required())
            // Scan only while WaveTop is visible (a wardrive keeps the engine going on its own).
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> vm.start()
                    Lifecycle.Event.ON_RESUME -> vm.onPermissionsChanged()
                    Lifecycle.Event.ON_STOP -> vm.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                vm.stop()
            }
        }

        val nowMs = rememberClock(5_000)
        val visible = remember(engine.devices, view.filter, view.sort, view.sortDescending) {
            DeviceViews.visible(engine.devices, view.filter, view.sort, view.sortDescending)
        }
        val inDrive = view.tab == Tab.Drives && view.openDrive != null
        val pool = if (inDrive) view.openDrive?.devices.orEmpty() else engine.devices
        val selected = remember(pool, view.selectedKey) { view.selectedKey?.let { k -> pool.firstOrNull { it.key == k } } }

        BackHandler(enabled = view.settingsPane != null) { vm.backInSettings() }
        BackHandler(enabled = view.settingsPane == null && modal != Modal.None) { modal = Modal.None }
        BackHandler(enabled = view.settingsPane == null && modal == Modal.None && inDrive && selected == null) { vm.closeDrive() }

        fun startWardrive() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            modal = Modal.StartWardrive
        }

        val c = Wt.colors
        Scaffold(
            containerColor = c.bg,
            topBar = {
                TopBar(
                    engine = engine,
                    deviceCount = visible.size,
                    updateWaiting = updates.available != null,
                    onLive = vm::setLive,
                    onWardrive = { if (engine.wardrive == null) startWardrive() else modal = Modal.StopWardrive },
                    onSettings = { vm.openSettings() },
                )
            },
            bottomBar = { BottomNav(view.tab, recording = engine.wardrive != null, onSelect = vm::selectTab) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val needsAccess = view.tab == Tab.Devices || view.tab == Tab.Map || view.tab == Tab.Channels
                when {
                    needsAccess && engine.access == SurveyAccess.PermissionDenied -> EmptyState(
                        icon = Icons.Outlined.Shield,
                        title = "WaveTop needs access to scan",
                        body = "Android treats nearby Wi-Fi and Bluetooth as location data, so WaveTop needs location, " +
                            "nearby-devices and Bluetooth-scan access. Nothing leaves your phone unless you send it.",
                        action = {
                            Row {
                                WtButton("Allow access", { permissionLauncher.launch(SurveyPermissions.required()) }, kind = BtnKind.Primary)
                                Spacer(Modifier.width(8.dp))
                                WtButton("App settings", {
                                    openIntent(
                                        context,
                                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", context.packageName, null)),
                                    )
                                })
                            }
                        },
                    )
                    needsAccess && engine.access == SurveyAccess.LocationOff -> EmptyState(
                        icon = Icons.Outlined.LocationOff,
                        title = "Location is off",
                        body = "Android only hands out Wi-Fi and Bluetooth scan results while location is on.",
                        action = {
                            WtButton("Turn on location", {
                                openIntent(context, Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }, kind = BtnKind.Primary)
                        },
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        if (needsAccess) RadioBanners(engine, view, context)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (view.tab) {
                                Tab.Devices -> DevicesScreen(
                                    devices = visible,
                                    engine = engine,
                                    view = view,
                                    settings = settings,
                                    nowMs = nowMs,
                                    onFilter = vm::setFilter,
                                    onSort = vm::sortBy,
                                    onSelect = vm::select,
                                    onDismissWelcome = vm::dismissWelcome,
                                )
                                Tab.Map -> MapScreen(
                                    devices = visible,
                                    engine = engine,
                                    view = view,
                                    settings = settings,
                                    nowMs = nowMs,
                                    onFilter = vm::setFilter,
                                    onMode = vm::setMapMode,
                                    onBasemap = vm::setBasemap,
                                    onSelect = vm::select,
                                )
                                Tab.Channels -> ChannelsScreen(engine.devices, view.band, vm::setBand)
                                Tab.Drives -> {
                                    val open = view.openDrive
                                    if (open == null) {
                                        DrivesScreen(
                                            wardrive = engine.wardrive,
                                            liveStream = engine.liveStream,
                                            drives = engine.drives,
                                            settings = settings,
                                            onStart = ::startWardrive,
                                            onStop = { modal = Modal.StopWardrive },
                                            onOpen = vm::openDrive,
                                        )
                                    } else {
                                        DriveDetailScreen(
                                            open = open,
                                            view = view,
                                            settings = settings,
                                            nowMs = nowMs,
                                            onBack = vm::closeDrive,
                                            onTab = vm::setDriveTab,
                                            onFilter = vm::setFilter,
                                            onSort = vm::sortBy,
                                            onSelect = vm::select,
                                            onBasemap = vm::setBasemap,
                                            onTime = vm::setDriveTime,
                                            onSend = {
                                                vm.resetNetSeerTask()
                                                modal = Modal.NetSeer
                                            },
                                            onShare = { modal = Modal.Share },
                                            onDelete = { modal = Modal.DeleteDrive },
                                        )
                                    }
                                }
                                Tab.Log -> LogScreen(engine.messages, settings)
                            }
                        }
                    }
                }
            }
        }

        selected?.let { device ->
            DeviceSheet(
                device = device,
                nowMs = nowMs,
                settings = settings,
                onDismiss = { vm.select(null) },
                // A saved drive has no radar, so only pinned devices can be shown there.
                onShowOnMap = if (!inDrive || device.bestFix != null) ({ vm.showOnMap(device) }) else null,
            )
        }

        when (modal) {
            Modal.None -> Unit
            Modal.StartWardrive -> WardriveStartDialog(
                onStart = { name, streamLive ->
                    modal = Modal.None
                    if (vm.startWardrive(name, streamLive)) vm.selectTab(Tab.Drives)
                },
                canStream = settings.netSeer != null,
                onDismiss = { modal = Modal.None },
            )
            Modal.StopWardrive -> ConfirmDialog(
                title = "Stop the wardrive?",
                body = "\"${engine.wardrive?.name}\" will be saved and listed under Drives.",
                confirm = "Stop and save",
                onConfirm = {
                    modal = Modal.None
                    vm.stopWardrive()
                },
                onDismiss = { modal = Modal.None },
            )
            Modal.NetSeer -> SendToNetSeerDialog(
                paired = settings.netSeer,
                task = view.netSeerTask,
                onSend = vm::sendOpenDriveToNetSeer,
                onOpenSettings = {
                    modal = Modal.None
                    vm.openSettings(com.ardyn.wavetop.ui.SettingsPane.NetSeer)
                },
                onDismiss = { modal = Modal.None },
            )
            Modal.Share -> ShareDialog(
                onPick = { format ->
                    modal = Modal.None
                    scope.launch { vm.exportOpenDrive(format)?.let { shareFile(context, it, format.mime) } }
                },
                onDismiss = { modal = Modal.None },
            )
            Modal.DeleteDrive -> view.openDrive?.let { open ->
                ConfirmDialog(
                    title = "Delete this drive?",
                    body = "\"${open.entry.meta.name}\" will be removed from this phone. This can't be undone.",
                    confirm = "Delete",
                    danger = true,
                    onConfirm = {
                        modal = Modal.None
                        vm.deleteDrive(open.entry)
                    },
                    onDismiss = { modal = Modal.None },
                )
            }
        }

        AnimatedVisibility(
            visible = view.settingsPane != null,
            enter = slideInHorizontally { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut(),
        ) {
            SettingsScreen(
                vm = vm,
                view = view,
                settings = settings,
                updates = updates,
                engine = engine,
                onShareFile = { file, mime -> shareFile(context, file, mime) },
            )
        }
    }
}

@Composable
private fun TopBar(
    engine: EngineState,
    deviceCount: Int,
    updateWaiting: Boolean,
    onLive: (Boolean) -> Unit,
    onWardrive: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Wt.colors
    Column(Modifier.background(c.panel).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.brand_mark),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BrandTitle()
                val scanning = buildList {
                    if (engine.wifiScanning) add("Wi-Fi")
                    if (engine.bluetoothScanning) add("Bluetooth")
                }
                Text(
                    buildString {
                        append("$deviceCount device${if (deviceCount == 1) "" else "s"}")
                        when {
                            scanning.isNotEmpty() -> append(" · scanning ${scanning.joinToString(" + ")}")
                            !engine.live && engine.wardrive == null -> append(" · paused")
                        }
                    },
                    fontSize = 11.5.sp,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LivePill(engine.live, onClick = { onLive(!engine.live) })
            Spacer(Modifier.width(6.dp))
            WardrivePill(engine, onWardrive)
            Box {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = c.muted)
                }
                if (updateWaiting) UpdateDot(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Composable
private fun LivePill(live: Boolean, onClick: () -> Unit) {
    val c = Wt.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, if (live) c.good.copy(alpha = 0.6f) else c.lineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) c.good else c.faint))
        Spacer(Modifier.width(6.dp))
        Text(if (live) "Live" else "Paused", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (live) c.text else c.muted)
    }
}

/** Start a wardrive from anywhere; while recording it shows the elapsed time and stops it. */
@Composable
private fun WardrivePill(engine: EngineState, onClick: () -> Unit) {
    val c = Wt.colors
    val shape = RoundedCornerShape(999.dp)
    val status = engine.wardrive
    Row(
        Modifier
            .clip(shape)
            .background(if (status != null) c.dangerSoft else c.raised)
            .border(1.dp, if (status != null) c.danger else c.lineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == null) {
            Icon(Icons.Outlined.FiberManualRecord, null, tint = c.danger, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text("Drive", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        } else {
            val clock = rememberClock(1_000)
            Icon(Icons.Outlined.Stop, null, tint = c.danger, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(elapsedText(clock - status.startedMs), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.danger)
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, recording: Boolean, onSelect: (Tab) -> Unit) {
    val c = Wt.colors
    Column {
        HorizontalDivider(color = c.line)
        NavigationBar(containerColor = c.panel, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = {
                        Box {
                            Icon(
                                when (tab) {
                                    Tab.Devices -> Icons.Outlined.ViewList
                                    Tab.Map -> Icons.Outlined.Map
                                    Tab.Channels -> Icons.Outlined.BarChart
                                    Tab.Drives -> Icons.Outlined.Route
                                    Tab.Log -> Icons.Outlined.Notes
                                },
                                contentDescription = null,
                            )
                            if (tab == Tab.Drives && recording) {
                                Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(c.danger))
                            }
                        }
                    },
                    label = { Text(tab.label, fontSize = 11.5.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.text,
                        indicatorColor = c.accentSoft,
                        unselectedIconColor = c.muted,
                        unselectedTextColor = c.muted,
                    ),
                )
            }
        }
    }
}

/** Slim warnings for a radio that's off or missing, so the other one keeps working. */
@Composable
private fun RadioBanners(engine: EngineState, view: ViewState, context: Context) {
    val showWifi = view.filter != PhyFilter.Bluetooth
    val showBt = view.filter != PhyFilter.Wifi && view.tab != Tab.Channels
    if (showWifi) {
        when (engine.wifiRadio) {
            RadioState.Off -> Banner("Wi-Fi is off. Tap to turn it on.") {
                openIntent(context, Intent(AndroidSettings.ACTION_WIFI_SETTINGS))
            }
            RadioState.Unavailable -> Banner("This phone has no Wi-Fi adapter WaveTop can use.", null)
            RadioState.On -> if (engine.wifiScanThrottled) {
                Banner("Android is limiting Wi-Fi scans, so Wi-Fi rows are a little behind.", null)
            }
        }
    }
    if (showBt) {
        when (engine.bluetoothRadio) {
            RadioState.Off -> Banner("Bluetooth is off. Tap to turn it on.") {
                openIntent(context, Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
            }
            RadioState.Unavailable -> Banner("This phone has no Bluetooth adapter WaveTop can use.", null)
            RadioState.On -> Unit
        }
    }
}

@Composable
private fun Banner(text: String, onClick: (() -> Unit)?) {
    val c = Wt.colors
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = c.text,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.dangerSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

fun openIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}

fun openUrl(context: Context, url: String) = openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** Hands a file to the system share sheet (Drive, email, Files, the WiGLE app…). */
fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.wavetop.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
    } catch (_: ActivityNotFoundException) {
        // Nothing can receive it; the export stays in cache.
    }
}

/** "WaveTop" in the top bar: on the WaveTop theme, "Wave" in the logo's cyan and "Top" in its orange. */
@Composable
private fun BrandTitle() {
    val c = Wt.colors
    val wave = c.brandWave
    val top = c.brandTop
    val style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
    if (wave == null || top == null) {
        Text("WaveTop", style = style, color = c.text)
    } else {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = wave)) { append("Wave") }
                withStyle(SpanStyle(color = top)) { append("Top") }
            },
            style = style,
        )
    }
}
