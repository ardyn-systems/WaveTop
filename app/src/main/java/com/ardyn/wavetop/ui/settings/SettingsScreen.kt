package com.ardyn.wavetop.ui.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.outlined.QrCodeScanner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.BuildConfig
import com.ardyn.wavetop.R
import com.ardyn.wavetop.engine.EngineState
import com.ardyn.wavetop.prefs.ClockStyle
import com.ardyn.wavetop.prefs.NetSeerRoute
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.AppViewModel
import com.ardyn.wavetop.ui.BtnKind
import com.ardyn.wavetop.ui.ConfirmDialog
import com.ardyn.wavetop.ui.Fact
import com.ardyn.wavetop.ui.Hint
import com.ardyn.wavetop.ui.NavBadge
import com.ardyn.wavetop.ui.Segmented
import com.ardyn.wavetop.ui.SectionLabel
import com.ardyn.wavetop.ui.SettingsPane
import com.ardyn.wavetop.ui.StatusLine
import com.ardyn.wavetop.ui.SwitchRow
import com.ardyn.wavetop.ui.TaskStatus
import com.ardyn.wavetop.ui.ViewState
import com.ardyn.wavetop.ui.WtButton
import com.ardyn.wavetop.ui.WtCard
import com.ardyn.wavetop.ui.WtTextField
import com.ardyn.wavetop.ui.dateTimeText
import com.ardyn.wavetop.ui.openIntent
import com.ardyn.wavetop.ui.openUrl
import com.ardyn.wavetop.ui.theme.AppTheme
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt
import com.ardyn.wavetop.ui.timeText
import com.ardyn.wavetop.update.Release
import com.ardyn.wavetop.update.ReleaseKind
import com.ardyn.wavetop.update.Releases
import com.ardyn.wavetop.update.UpdatePhase
import com.ardyn.wavetop.update.UpdateText
import com.ardyn.wavetop.update.UpdaterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val REPO_URL = "https://github.com/${BuildConfig.GITHUB_REPO}"

/**
 * Settings, laid out like NetSeer's settings dialog: a section list beside the open section on
 * wide screens; on a phone the list comes first and a section fills the screen.
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    view: ViewState,
    settings: Settings,
    updates: UpdaterState,
    engine: EngineState,
    onShareFile: (File, String) -> Unit,
) {
    val c = Wt.colors
    val pane = view.settingsPane ?: SettingsPane.General
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            // .settings-head
            Column(Modifier.background(c.panel).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide && !view.settingsList) {
                        IconButton(onClick = vm::backInSettings) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.text) }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        if (wide || view.settingsList) "Settings" else pane.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::closeSettings) { Icon(Icons.Outlined.Close, "Close settings", tint = c.muted) }
                }
                HorizontalDivider(color = c.line)
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.width(200.dp).fillMaxHeight().background(c.bg).padding(8.dp)) {
                        SettingsNav(pane, updates.available != null, compact = true, onPick = vm::showSettingsPane)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                    PaneBody(pane, vm, view, settings, updates, engine, onShareFile, Modifier.weight(1f))
                }
            } else if (view.settingsList) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp).navigationBarsPadding()) {
                    SettingsNav(pane, updates.available != null, compact = false, onPick = vm::showSettingsPane)
                }
            } else {
                PaneBody(pane, vm, view, settings, updates, engine, onShareFile, Modifier.weight(1f))
            }
        }
    }
}

/** `.settings-nav`: one row per section; Updates wears a "New" badge when one is waiting. */
@Composable
private fun SettingsNav(selected: SettingsPane, updateWaiting: Boolean, compact: Boolean, onPick: (SettingsPane) -> Unit) {
    val c = Wt.colors
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp)) {
        SettingsPane.entries.forEach { p ->
            val active = compact && p == selected
            val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (active) c.accentSoft else if (compact) c.bg else c.panel)
                    .then(if (compact) Modifier else Modifier.border(1.dp, c.line, shape))
                    .clickable { onPick(p) }
                    .padding(horizontal = 12.dp, vertical = if (compact) 9.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(paneIcon(p), null, tint = if (active || !compact) c.accent else c.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, color = if (active || !compact) c.text else c.muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    if (!compact) Hint(paneBlurb(p))
                }
                if (p == SettingsPane.Updates && updateWaiting) NavBadge()
            }
        }
    }
}

private fun paneIcon(p: SettingsPane): ImageVector = when (p) {
    SettingsPane.General -> Icons.Outlined.Palette
    SettingsPane.NetSeer -> Icons.Outlined.Hub
    SettingsPane.Data -> Icons.Outlined.Folder
    SettingsPane.Updates -> Icons.Outlined.SystemUpdate
    SettingsPane.Help -> Icons.AutoMirrored.Outlined.HelpOutline
    SettingsPane.About -> Icons.Outlined.Info
}

private fun paneBlurb(p: SettingsPane) = when (p) {
    SettingsPane.General -> "Theme, time, map and scanning"
    SettingsPane.NetSeer -> "Pair with NetSeer and send surveys to it"
    SettingsPane.Data -> "Back up or clear your surveys"
    SettingsPane.Updates -> "Check for, install or reinstall versions"
    SettingsPane.Help -> "User guide and reporting a problem"
    SettingsPane.About -> "Version, source and credits"
}

@Composable
private fun PaneBody(
    pane: SettingsPane,
    vm: AppViewModel,
    view: ViewState,
    settings: Settings,
    updates: UpdaterState,
    engine: EngineState,
    onShareFile: (File, String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            // Shrink above the keyboard so the focused field (the pairing code) scrolls into view.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        when (pane) {
            SettingsPane.General -> GeneralPane(vm, settings)
            SettingsPane.NetSeer -> NetSeerPane(vm, view, settings)
            SettingsPane.Data -> DataPane(vm, view, engine, onShareFile)
            SettingsPane.Updates -> UpdatesPane(vm, settings, updates)
            SettingsPane.Help -> HelpPane()
            SettingsPane.About -> AboutPane()
        }
    }
}

// --- General -------------------------------------------------------------------------------

@Composable
private fun GeneralPane(vm: AppViewModel, settings: Settings) {
    val c = Wt.colors
    SectionLabel("Theme")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { theme ->
            val t = theme.colors
            val active = theme == settings.theme
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (active) c.accentSoft else c.panel)
                    .border(if (active) 2.dp else 1.dp, if (active) c.accent else c.line, shape)
                    .clickable { vm.updateSettings { it.copy(theme = theme) } }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // .theme-swatch: the theme's own colours side by side. WaveTop's shows the logo's
                // cyan beside its orange; its raised grey would hide the cyan entirely.
                val second = if (theme == AppTheme.WaveTop) t.nodeBluetooth else t.raised
                Row(Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(6.dp))) {
                    listOf(t.bg, second, t.accent, t.text).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(theme.label, fontWeight = FontWeight.SemiBold, color = c.text)
                    Hint(theme.blurb)
                }
                if (active) Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
            }
        }
    }

    SectionLabel("Time")
    Segmented(ClockStyle.entries, settings.clock, { it.label }, { s -> vm.updateSettings { it.copy(clock = s) } })
    Hint("Now: ${timeText(System.currentTimeMillis(), settings.clock)}", Modifier.padding(top = 8.dp))

    SectionLabel("Map")
    SwitchRow(
        "Name the strongest devices",
        "Labels beside the six strongest pins on the street map and radar",
        settings.mapLabels,
        { on -> vm.updateSettings { it.copy(mapLabels = on) } },
    )

    SectionLabel("Scanning")
    SwitchRow(
        "Scan when WaveTop opens",
        "Start live scanning straight away. Off: open paused and tap Paused to start.",
        settings.liveOnOpen,
        { on -> vm.updateSettings { it.copy(liveOnOpen = on) } },
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        "Keep the screen on while driving",
        "While a survey records and WaveTop is open. Bluetooth LE scanning pauses on some phones when the screen is off.",
        settings.keepScreenOnWhileDriving,
        { on -> vm.updateSettings { it.copy(keepScreenOnWhileDriving = on) } },
    )
}

// --- NetSeer -------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class) // BringIntoViewRequester
@Composable
private fun NetSeerPane(vm: AppViewModel, view: ViewState, settings: Settings) {
    val c = Wt.colors
    var code by rememberSaveable { mutableStateOf("") }
    Hint(
        "Pair WaveTop with NetSeer once, then send any survey to it with one tap: NetSeer maps the Wi-Fi " +
            "and Bluetooth it heard and estimates where each device is. Pairing uses a code NetSeer shows; " +
            "you can remove this phone from NetSeer at any time.",
        Modifier.padding(top = 12.dp),
    )

    val link = settings.netSeer
    if (link != null) {
        SectionLabel("Paired")
        WtCard(Modifier.fillMaxWidth(), highlight = true) {
            Text("Paired as ${link.deviceName}", fontWeight = FontWeight.SemiBold, color = c.text)
            Hint("${link.baseUrl} · since ${dateTimeText(link.pairedAtMs, settings.clock)}")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WtButton("Test", vm::testNetSeer)
                WtButton("Unpair", vm::unpairNetSeer, kind = BtnKind.Ghost)
            }
        }
        StatusLine(view.netSeerTask)
        Hint("To send a survey: Surveys → open a survey → the NetSeer button.", Modifier.padding(top = 10.dp))
        return
    }

    // Primary path: scan the QR NetSeer shows — it carries the address and the code, so there's
    // nothing to type. The scanner (ZXing) asks for the camera itself the first time.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.pairFromQr(it) }
    }
    Spacer(Modifier.height(4.dp))
    WtButton(
        "Scan QR code",
        {
            scanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Point at NetSeer's pairing QR")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                },
            )
        },
        Modifier.fillMaxWidth(),
        kind = BtnKind.Primary,
        icon = Icons.Outlined.QrCodeScanner,
        enabled = view.netSeerTask !is TaskStatus.Working,
    )
    Hint(
        "In NetSeer: Settings → Integrations → Pair a device. Point the camera at the QR it shows " +
            "(good for five minutes). Reaches NetSeer over Wi-Fi or a private network like Tailscale.",
        Modifier.padding(top = 8.dp),
    )
    StatusLine(view.netSeerTask)

    // Fallbacks for anyone who can't scan: plugged-in USB, or a typed address — both with the code.
    var showManual by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(6.dp))
    ExpandRow("Pair another way", showManual) { showManual = !showManual }
    if (showManual) {
        Spacer(Modifier.height(6.dp))
        Segmented(
            NetSeerRoute.entries,
            settings.netSeerRoute,
            { if (it == NetSeerRoute.Usb) "USB cable" else "Wi-Fi / network" },
            vm::setNetSeerRoute,
        )
        Spacer(Modifier.height(10.dp))
        when (settings.netSeerRoute) {
            NetSeerRoute.Usb -> {
                Hint(
                    "Plug the phone into the computer running NetSeer with USB debugging on, and allow the " +
                        "computer when asked. NetSeer links it by itself, then shows a code — type it below. " +
                        "NetSeer stays private to that computer.",
                )
                var showAdb by rememberSaveable { mutableStateOf(false) }
                ExpandRow("Older NetSeer (manual adb)", showAdb) { showAdb = !showAdb }
                if (showAdb) {
                    Hint(
                        "Run this in PowerShell each time you plug in, with NetSeer's port (next to \"This " +
                            "NetSeer\") as the last number; add -s <serial> if more than one device is connected:",
                        Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "& \"\$env:LOCALAPPDATA\\Android\\Sdk\\platform-tools\\adb.exe\" reverse tcp:47331 tcp:47331",
                        style = MonoStyle.copy(fontSize = 13.sp),
                        color = c.text,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.raised)
                            .border(1.dp, c.line, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    )
                }
            }
            NetSeerRoute.Network -> {
                WtTextField(
                    settings.netSeerHost,
                    vm::setNetSeerHost,
                    "NetSeer's address",
                    placeholder = "192.168.1.20",
                    keyboardType = KeyboardType.Uri,
                    mono = true,
                )
                Hint(
                    "In NetSeer, turn on Settings → Integrations → Allow devices on my network; it shows " +
                        "the address to type here. Port 47331 is assumed unless you add one.",
                    Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        WtButton("Test connection", vm::testNetSeer)
        Spacer(Modifier.height(12.dp))
        // Keep the code field and the Pair button above the keyboard together, not just the field.
        val pairRow = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        Column(Modifier.bringIntoViewRequester(pairRow)) {
            WtTextField(
                code,
                { code = it.filter(Char::isDigit).take(6) },
                "Pairing code",
                Modifier.onFocusChanged { focus ->
                    if (focus.isFocused) scope.launch { delay(350); pairRow.bringIntoView() }
                },
                placeholder = "123456",
                keyboardType = KeyboardType.NumberPassword,
                mono = true,
                onDone = { vm.pairNetSeer(code) },
            )
            Spacer(Modifier.height(10.dp))
            WtButton("Pair", { vm.pairNetSeer(code) }, kind = BtnKind.Default, enabled = view.netSeerTask !is TaskStatus.Working)
        }
    }
}

/** A quiet clickable header that expands/collapses the content below it. */
@Composable
private fun ExpandRow(label: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = Wt.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.muted)
        Spacer(Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = c.muted)
    }
}

// --- Your data -----------------------------------------------------------------------------

@Composable
private fun DataPane(vm: AppViewModel, view: ViewState, engine: EngineState, onShareFile: (File, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    val size = engine.surveys.sumOf { it.sizeBytes }
    SectionLabel("Saved on this phone")
    Hint(
        "${engine.surveys.size} saved survey${if (engine.surveys.size == 1) "" else "s"} · ${"%.1f".format(size / 1_048_576.0)} MB. " +
            "Surveys stay on this phone only. Back them up before uninstalling WaveTop or moving to another phone.",
    )
    Spacer(Modifier.height(10.dp))
    WtButton("Back up all surveys (.zip)", {
        scope.launch { vm.exportAllSurveys()?.let { onShareFile(it, "application/zip") } }
    }, enabled = engine.surveys.isNotEmpty())

    SectionLabel("Start over")
    Hint("Deleting surveys can't be undone. Resetting settings keeps your surveys and unpairs NetSeer.")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WtButton("Delete all surveys…", { confirm = "surveys" }, kind = BtnKind.Danger, enabled = engine.surveys.isNotEmpty())
        WtButton("Reset settings…", { confirm = "settings" })
    }
    StatusLine(view.dataTask)

    when (confirm) {
        "surveys" -> ConfirmDialog(
            title = "Delete every survey?",
            body = "All ${engine.surveys.size} saved surveys will be removed from this phone. Back them up first if you might want them.",
            confirm = "Delete all",
            danger = true,
            onConfirm = {
                confirm = null
                vm.deleteAllSurveys()
            },
            onDismiss = { confirm = null },
        )
        "settings" -> ConfirmDialog(
            title = "Reset settings?",
            body = "Theme, time, map and update preferences go back to their defaults, and NetSeer is unpaired. Surveys are kept.",
            confirm = "Reset",
            onConfirm = {
                confirm = null
                vm.resetSettings()
            },
            onDismiss = { confirm = null },
        )
    }
}

// --- Updates -------------------------------------------------------------------------------

@Composable
private fun UpdatesPane(vm: AppViewModel, settings: Settings, updates: UpdaterState) {
    val c = Wt.colors
    val context = LocalContext.current
    val updater = vm.updater
    val phase = updates.phase
    val busy = phase !is UpdatePhase.Idle && phase !is UpdatePhase.NeedsInstallPermission

    // .version-card
    WtCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_mark), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("WaveTop ${updater.installedVersion}", fontWeight = FontWeight.Bold, color = c.text)
                Hint(
                    when {
                        updates.checkedAtMs > 0 -> "Checked ${timeText(updates.checkedAtMs, settings.clock, seconds = false)}"
                        settings.lastUpdateCheckMs > 0 -> "Last checked ${dateTimeText(settings.lastUpdateCheckMs, settings.clock)}"
                        else -> "Not checked yet"
                    },
                )
            }
            WtButton(if (phase is UpdatePhase.Checking) "Checking…" else "Check", { updater.check() }, enabled = !busy)
        }
    }

    when (phase) {
        is UpdatePhase.Downloading -> Progress("Downloading ${phase.release.version}… ${phase.percent}%", phase.percent / 100f)
        is UpdatePhase.Verifying -> Progress("Checking the download against GitHub's checksum…", null)
        is UpdatePhase.Installing -> Progress("Handing it to Android to install…", null)
        is UpdatePhase.NeedsInstallPermission -> WtCard(Modifier.fillMaxWidth().padding(top = 10.dp), highlight = true) {
            Text("One-time permission", fontWeight = FontWeight.SemiBold, color = c.text)
            Hint(
                "Android asks before an app installs updates to itself. Turn on \"Allow from this source\" for " +
                    "WaveTop, come back, and tap Update again.",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WtButton("Open Android settings", { openIntent(context, updater.installPermissionIntent()) }, kind = BtnKind.Primary)
                WtButton("Not now", updater::cancelPermissionPrompt, kind = BtnKind.Ghost)
            }
        }
        else -> Unit
    }
    updates.message?.let {
        Text(
            it,
            color = if (updates.messageIsError) c.danger else if (updates.available != null) c.accent else c.good,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    // Android refused the in-app install (usually Play Protect): the browser route still works.
    val manual = updates.manualInstall
    val manualApk = manual?.apk
    if (manual != null && manualApk != null) {
        WtButton(
            "Download WaveTop ${manual.version} from GitHub",
            { openUrl(context, manualApk.url) },
            Modifier.padding(top = 10.dp),
            kind = BtnKind.Primary,
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
        )
    }

    Spacer(Modifier.height(12.dp))
    SwitchRow(
        "Check when WaveTop opens",
        "Asks GitHub about new releases at most once a day. Nothing about you or your surveys is sent.",
        settings.autoCheckUpdates,
        { on -> vm.updateSettings { it.copy(autoCheckUpdates = on) } },
    )

    if (updater.isDebugBuild) {
        Hint(
            "This is a debug build. Updating installs the release version of WaveTop beside it as a separate app.",
            Modifier.padding(top = 10.dp),
        )
    }

    SectionLabel("Versions")
    if (updates.releases.isEmpty()) {
        Hint("Tap Check to list the versions on GitHub.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            updates.releases.forEach { r -> ReleaseRow(r, updater.installedVersion, busy, settings, onInstall = { updater.install(r) }, onOpen = { openUrl(context, r.htmlUrl) }) }
        }
    }
    Hint(
        "Update and Reinstall download the APK, check it against the release's SHA256SUMS, and hand it to " +
            "Android to install; your surveys stay put. Android can't install an older version over a newer " +
            "one, so going back means uninstalling first (back up your surveys under Your data).",
        Modifier.padding(top = 10.dp),
    )
    Row(Modifier.padding(top = 8.dp)) {
        WtButton("All releases on GitHub", { openUrl(context, updater.releasesPageUrl) }, kind = BtnKind.Ghost, icon = Icons.AutoMirrored.Outlined.OpenInNew)
    }
}

@Composable
private fun Progress(text: String, fraction: Float?) {
    val c = Wt.colors
    Column(Modifier.padding(top = 12.dp)) {
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
        }
        Hint(text, Modifier.padding(top = 6.dp))
    }
}

/** `.release-list > li`: name, a Newer/Installed tag, date, and the one action that makes sense. */
@Composable
private fun ReleaseRow(
    release: Release,
    installed: String,
    busy: Boolean,
    settings: Settings,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = Wt.colors
    val kind = Releases.kind(release, installed)
    WtCard(Modifier.fillMaxWidth(), highlight = kind == ReleaseKind.Current) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(release.name, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    val (tag, tagColor) = when (kind) {
                        ReleaseKind.Newer -> "Newer" to c.accent
                        ReleaseKind.Current -> "Installed" to c.muted
                        ReleaseKind.Older -> "Older" to c.faint
                    }
                    Text(
                        tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tagColor,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, tagColor, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    if (release.prerelease) {
                        Spacer(Modifier.width(4.dp))
                        Text("Pre-release", fontSize = 11.sp, color = c.weak)
                    }
                }
                Hint(release.publishedAt.take(10) + (release.apk?.let { " · ${"%.1f".format(it.size / 1_048_576.0)} MB" } ?: " · no APK"))
            }
            Spacer(Modifier.width(8.dp))
            when {
                release.apk == null -> WtButton("Open", onOpen, kind = BtnKind.Ghost)
                kind == ReleaseKind.Newer -> WtButton("Update", onInstall, kind = BtnKind.Primary, enabled = !busy)
                kind == ReleaseKind.Current -> WtButton("Reinstall", onInstall, enabled = !busy)
                else -> WtButton("View", onOpen, kind = BtnKind.Ghost)
            }
        }
        val notes = remember(release.notes) { UpdateText.plainNotes(release.notes) }
        if (notes.isNotBlank() && kind != ReleaseKind.Older) {
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// --- Help & About --------------------------------------------------------------------------

@Composable
private fun HelpPane() {
    val context = LocalContext.current
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HelpCard("User guide", "Every screen and feature, with screenshots") { openUrl(context, "$REPO_URL/blob/main/docs/user-guide.md") }
        HelpCard("Release notes", "What changed in each version") { openUrl(context, "$REPO_URL/releases") }
        HelpCard("Report a problem", "Opens a new issue on GitHub") { openUrl(context, "$REPO_URL/issues/new") }
    }
    Hint(
        "When you report a problem, include the version shown under About. Don't attach surveys from places " +
            "you need to keep private.",
        Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun HelpCard(title: String, body: String, onClick: () -> Unit) {
    val c = Wt.colors
    WtCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = c.text)
                Hint(body)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AboutPane() {
    val c = Wt.colors
    val context = LocalContext.current
    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.brand_mark), null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("WaveTop", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = c.text)
            Text(context.getString(R.string.app_motto), color = c.muted)
        }
    }
    Spacer(Modifier.height(14.dp))
    Fact("Version", BuildConfig.VERSION_NAME)
    Fact("Build", if (BuildConfig.DEBUG) "Debug" else "Release")
    Fact("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("Source", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(110.dp))
        Text(
            REPO_URL.removePrefix("https://"),
            color = c.accent,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { openUrl(context, REPO_URL) },
        )
    }
    Hint(
        "WaveTop surveys the Wi-Fi networks and Bluetooth devices around you using Android's own scanning, " +
            "pins them on a map, and records surveys you can share or send to NetSeer. Everything stays on " +
            "this phone unless you send it.",
        Modifier.padding(top = 12.dp),
    )
    SectionLabel("Built with")
    Credit("Jetpack Compose", "Apache-2.0")
    Credit("osmdroid", "Apache-2.0")
    Credit("Map data © OpenStreetMap contributors", "ODbL")
    Credit("IEEE OUI registry", "Public data")
    Hint("© Ardyn Systems. All rights reserved.", Modifier.padding(top = 14.dp))
}

@Composable
private fun Credit(name: String, license: String) {
    val c = Wt.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(name, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(license, color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}
