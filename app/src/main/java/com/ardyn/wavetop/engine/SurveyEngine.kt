package com.ardyn.wavetop.engine

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ardyn.wavetop.drive.DriveEntry
import com.ardyn.wavetop.drive.ExportFormat
import com.ardyn.wavetop.drive.WardriveRecorder
import com.ardyn.wavetop.drive.WardriveService
import com.ardyn.wavetop.drive.WardriveStore
import com.ardyn.wavetop.model.DeviceTracker
import com.ardyn.wavetop.model.GeoFix
import com.ardyn.wavetop.model.ParsedDrive
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.model.TrackerUpdate
import com.ardyn.wavetop.net.LiveState
import com.ardyn.wavetop.net.NetSeerLiveSession
import com.ardyn.wavetop.oui.OuiLookup
import com.ardyn.wavetop.permissions.SurveyPermissions
import com.ardyn.wavetop.prefs.AppSettings
import com.ardyn.wavetop.scan.BluetoothSurveyScanner
import com.ardyn.wavetop.scan.LocationTracker
import com.ardyn.wavetop.scan.WifiSurveyScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What's needed before any radio can scan. */
enum class SurveyAccess { Ready, PermissionDenied, LocationOff }

enum class RadioState { On, Off, Unavailable }

data class SurveyMessage(val timeMs: Long, val text: String, val warning: Boolean = false)

/** A wardrive in progress. */
data class WardriveStatus(
    val name: String,
    val startedMs: Long,
    val observations: Int,
    val wifiDevices: Int,
    val bluetoothDevices: Int,
    val geotagged: Int,
)

data class EngineState(
    val access: SurveyAccess = SurveyAccess.PermissionDenied,
    val wifiRadio: RadioState = RadioState.Unavailable,
    val bluetoothRadio: RadioState = RadioState.Unavailable,
    /** Kismet-style continuous scanning while the screen is visible. */
    val live: Boolean = true,
    val wifiScanning: Boolean = false,
    val bluetoothScanning: Boolean = false,
    /** Android refused the last Wi-Fi scan request (throttling); Wi-Fi rows are cached results. */
    val wifiScanThrottled: Boolean = false,
    val devices: List<TrackedDevice> = emptyList(),
    /** Newest first. */
    val messages: List<SurveyMessage> = emptyList(),
    val fix: GeoFix? = null,
    val wardrive: WardriveStatus? = null,
    /** Set while the running wardrive is being streamed to NetSeer; null when not streaming. */
    val liveStream: LiveState? = null,
    /** Saved wardrives, newest first. */
    val drives: List<DriveEntry> = emptyList(),
)

/**
 * The scanning engine, one per process so it can outlive the survey screen: the screen
 * attaches while it's visible, and a running wardrive keeps it going (from
 * [WardriveService]) with the screen closed or the phone locked.
 *
 * Main thread only.
 */
class SurveyEngine private constructor(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val oui: OuiLookup = try {
        OuiLookup.loadFromGzipTsv(app.assets.open("oui/oui.tsv.gz"))
    } catch (_: Exception) {
        OuiLookup.fromMap(emptyMap())
    }

    private val wifi = WifiSurveyScanner(app, oui)
    private val bluetooth = BluetoothSurveyScanner(app, oui)
    private val location = LocationTracker(app)
    private val tracker = DeviceTracker(expireAfterMs = EXPIRE_MS)
    private val store = WardriveStore(app)

    private val _state = MutableStateFlow(EngineState(live = AppSettings.get(app).current.liveOnOpen))
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var uiAttached = false
    private var receiversOn = false
    private var recorder: WardriveRecorder? = null
    private var liveSession: NetSeerLiveSession? = null
    private var liveJob: Job? = null
    private var wifiScanTimeout: Job? = null
    private var lastWifiScanMs = Long.MIN_VALUE / 2
    private var lastBluetoothScanMs = Long.MIN_VALUE / 2

    init {
        refreshDrives()
    }

    // --- who needs the radios --------------------------------------------------------------

    fun attachUi() {
        uiAttached = true
        update()
    }

    fun detachUi() {
        uiAttached = false
        update()
    }

    /** Permissions, radios or location may have changed (resume, permission dialog, broadcasts). */
    fun recheck() {
        refreshAccess()
        update()
    }

    fun setLive(live: Boolean) {
        if (live == _state.value.live) return
        _state.update { it.copy(live = live) }
        log(if (live) "Live scanning resumed" else "Live scanning paused")
        update()
    }

    // --- wardrive ----------------------------------------------------------------------------

    /** @return false if it couldn't start (already running, no access, or the file couldn't be created). */
    fun startWardrive(name: String, streamLive: Boolean = false): Boolean {
        if (recorder != null) return false
        refreshAccess()
        if (_state.value.access != SurveyAccess.Ready) {
            log("Wardrive not started: scan access is missing", warning = true)
            return false
        }
        val title = name.trim().ifBlank { "Wardrive" }
        recorder = try {
            WardriveRecorder(store.newFile(title), title, System.currentTimeMillis())
        } catch (e: IOException) {
            log("Wardrive not started: ${e.message}", warning = true)
            return false
        }
        publishWardrive()
        refreshDrives()
        log("Wardrive \"$title\" started")
        if (streamLive) startLiveStream()
        try {
            ContextCompat.startForegroundService(app, Intent(app, WardriveService::class.java))
        } catch (e: RuntimeException) {
            // Still records while the screen is open; it just won't survive the app closing.
            log("Background recording unavailable (${e.javaClass.simpleName}); keep WaveTop open", warning = true)
        }
        update()
        return true
    }

    /** Stops the wardrive and saves it. */
    fun stopWardrive() {
        val rec = recorder ?: return
        recorder = null
        liveSession?.stop()
        liveSession = null
        _state.update { it.copy(liveStream = null) }
        try {
            val meta = rec.finish(System.currentTimeMillis())
            val minutes = ((meta.endedMs ?: rec.startedMs) - rec.startedMs) / 60_000
            log(
                "Wardrive \"${meta.name}\" saved · ${minutes} min · " +
                    "${meta.wifiDevices} Wi-Fi, ${meta.bluetoothDevices} BT, ${meta.geotagged} geotagged sightings",
            )
        } catch (e: IOException) {
            log("Wardrive \"${rec.name}\" could not be finalised: ${e.message}", warning = true)
        }
        _state.update { it.copy(wardrive = null) }
        app.stopService(Intent(app, WardriveService::class.java))
        refreshDrives()
        update()
    }

    fun refreshDrives() {
        // The drive being recorded has no footer yet and isn't "saved"; it has its own card.
        val recording = recorder?.file
        scope.launch {
            val list = withContext(Dispatchers.IO) { store.list().filter { it.file != recording } }
            _state.update { it.copy(drives = list) }
        }
    }

    suspend fun loadDrive(entry: DriveEntry): ParsedDrive? = withContext(Dispatchers.IO) { store.load(entry) }

    suspend fun exportDrive(entry: DriveEntry, drive: ParsedDrive, format: ExportFormat): File =
        withContext(Dispatchers.IO) { store.export(entry, drive, format) }

    suspend fun exportAllDrives(): File? = withContext(Dispatchers.IO) {
        store.exportAll(java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date()))
    }

    /** Deletes every saved drive (never the one recording). @return how many. */
    suspend fun deleteAllDrives(): Int {
        val recording = recorder?.file
        val n = withContext(Dispatchers.IO) { store.deleteAll(except = recording) }
        if (n > 0) log("Deleted all $n saved drives")
        refreshDrives()
        return n
    }

    fun deleteDrive(entry: DriveEntry) {
        scope.launch {
            withContext(Dispatchers.IO) { store.delete(entry) }
            log("Deleted wardrive \"${entry.meta.name}\"")
            refreshDrives()
        }
    }

    fun log(text: String, warning: Boolean = false) {
        val msg = SurveyMessage(System.currentTimeMillis(), text, warning)
        _state.update { it.copy(messages = (listOf(msg) + it.messages).take(MAX_MESSAGES)) }
    }

    // --- scanning ----------------------------------------------------------------------------

    /** Brings receivers, the live loop and GPS in line with who currently needs them. */
    private fun update() {
        val wantReceivers = uiAttached || recorder != null
        if (wantReceivers && !receiversOn) {
            wifi.register(onResults = ::onWifiResults, onRadioChanged = ::recheck)
            bluetooth.register(onChange = ::onBluetoothResults, onRadioChanged = ::recheck)
            receiversOn = true
            refreshAccess()
        }
        val s = _state.value
        val shouldScan = wantReceivers && s.access == SurveyAccess.Ready &&
            (recorder != null || (uiAttached && s.live))
        if (shouldScan) {
            if (liveJob?.isActive != true) {
                location.start(::onFix)
                liveJob = scope.launch {
                    while (isActive) {
                        tick()
                        delay(TICK_MS)
                    }
                }
            }
        } else {
            liveJob?.cancel()
            liveJob = null
            location.stop()
            bluetooth.stop()
            wifiScanTimeout?.cancel()
            _state.update { it.copy(wifiScanning = false, bluetoothScanning = false) }
        }
        if (!wantReceivers && receiversOn) {
            wifi.unregister()
            bluetooth.unregister()
            receiversOn = false
        }
    }

    /**
     * Wi-Fi rescans every [WIFI_INTERVAL_MS] — Android 9+ allows a foreground app (or one with a
     * foreground service) 4 scans per 2 minutes. Bluetooth runs a 12 s window every
     * [BLUETOOTH_INTERVAL_MS], under the 5-starts-per-30-s limit on LE scans.
     */
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val s = _state.value
        if (s.wifiRadio == RadioState.On && !s.wifiScanning && now - lastWifiScanMs >= WIFI_INTERVAL_MS) {
            scanWifi()
        }
        if (s.bluetoothRadio == RadioState.On && !bluetooth.isScanning() &&
            now - lastBluetoothScanMs >= BLUETOOTH_INTERVAL_MS
        ) {
            lastBluetoothScanMs = now
            if (bluetooth.start()) _state.update { it.copy(bluetoothScanning = true) }
        }
        publishWardrive()
    }

    private fun scanWifi() {
        lastWifiScanMs = SystemClock.elapsedRealtime()
        val accepted = wifi.requestScan()
        if (!accepted && !_state.value.wifiScanThrottled) {
            log("Android refused a Wi-Fi scan (throttled); showing cached results", warning = true)
        }
        ingestWifi()
        _state.update { it.copy(wifiScanning = accepted, wifiScanThrottled = !accepted) }
        wifiScanTimeout?.cancel()
        if (accepted) {
            // The results broadcast normally lands within a few seconds; don't spin forever if it doesn't.
            wifiScanTimeout = scope.launch {
                delay(WIFI_SCAN_TIMEOUT_MS)
                _state.update { it.copy(wifiScanning = false) }
            }
        }
    }

    private fun onWifiResults() {
        wifiScanTimeout?.cancel()
        ingestWifi()
        _state.update { it.copy(wifiScanning = false, wifiScanThrottled = false) }
    }

    private fun ingestWifi() {
        val update = tracker.updateWifi(wifi.snapshot(System.currentTimeMillis()), location.latest)
        update.newDevices.forEach { d ->
            log("New Wi-Fi AP \"${d.name}\" ${d.mac} ch ${d.channel ?: "?"} · ${d.crypto}")
        }
        record(update)
    }

    private fun onBluetoothResults() {
        val update = tracker.updateBluetooth(bluetooth.snapshot(), location.latest)
        update.newDevices.forEach { d -> log("New ${d.type} device \"${d.name}\" ${d.mac}") }
        record(update)
        _state.update { it.copy(bluetoothScanning = bluetooth.isScanning()) }
    }

    private fun record(update: TrackerUpdate) {
        val now = System.currentTimeMillis()
        recorder?.let { rec ->
            try {
                rec.record(update.observations, now)
            } catch (e: IOException) {
                log("Wardrive write failed: ${e.message}; stopping", warning = true)
                stopWardrive()
            }
        }
        liveSession?.offer(update.observations, _state.value.fix)
        tracker.expire(now)
        bluetooth.forgetBefore(now - EXPIRE_MS)
        _state.update { it.copy(devices = tracker.devices()) }
        publishWardrive()
    }

    // --- live streaming to NetSeer -----------------------------------------------------------

    private fun startLiveStream() {
        val link = AppSettings.get(app).current.netSeer
        if (link == null) {
            log("Live streaming skipped — pair with NetSeer first under Settings → NetSeer", warning = true)
            return
        }
        _state.update { it.copy(liveStream = LiveState.Connecting) }
        // onState arrives on an OkHttp thread; hop back to the engine's main-thread scope.
        liveSession = NetSeerLiveSession(link) { s -> scope.launch { onLiveState(s) } }.also { it.start() }
        log("Streaming this wardrive live to NetSeer (${link.deviceName})")
    }

    private fun onLiveState(s: LiveState) {
        if (liveSession == null) return
        _state.update { it.copy(liveStream = if (s == LiveState.Closed) null else s) }
        if (s == LiveState.Error) log("Live streaming to NetSeer stopped — connection lost", warning = true)
    }

    private fun publishWardrive() {
        val rec = recorder
        val status = rec?.let {
            WardriveStatus(it.name, it.startedMs, it.observations, it.wifiDevices, it.bluetoothDevices, it.geotagged)
        }
        if (status != _state.value.wardrive) _state.update { it.copy(wardrive = status) }
    }

    private fun onFix(fix: GeoFix) {
        val first = _state.value.fix == null
        _state.update { it.copy(fix = fix) }
        if (first) log("Location fix acquired (±${fix.accuracyM.toInt()} m)")
    }

    private fun refreshAccess() {
        val access = when {
            !SurveyPermissions.allGranted(app) -> SurveyAccess.PermissionDenied
            !SurveyPermissions.locationEnabled(app) -> SurveyAccess.LocationOff
            else -> SurveyAccess.Ready
        }
        val wifiRadio = when {
            !wifi.available() -> RadioState.Unavailable
            !wifi.radioOn() -> RadioState.Off
            else -> RadioState.On
        }
        val btRadio = when {
            !bluetooth.available() -> RadioState.Unavailable
            !bluetooth.radioOn() -> RadioState.Off
            else -> RadioState.On
        }
        val before = _state.value
        if (receiversOn && before.access == SurveyAccess.Ready) {
            if (before.wifiRadio != wifiRadio && wifiRadio != RadioState.Unavailable) {
                log("Wi-Fi turned ${if (wifiRadio == RadioState.On) "on" else "off"}", warning = wifiRadio == RadioState.Off)
            }
            if (before.bluetoothRadio != btRadio && btRadio != RadioState.Unavailable) {
                log("Bluetooth turned ${if (btRadio == RadioState.On) "on" else "off"}", warning = btRadio == RadioState.Off)
            }
        }
        _state.update { it.copy(access = access, wifiRadio = wifiRadio, bluetoothRadio = btRadio) }
    }

    companion object {
        private const val TICK_MS = 1_000L
        private const val WIFI_INTERVAL_MS = 35_000L
        private const val BLUETOOTH_INTERVAL_MS = 30_000L
        private const val WIFI_SCAN_TIMEOUT_MS = 15_000L
        private const val EXPIRE_MS = 10 * 60_000L
        private const val MAX_MESSAGES = 200

        @Volatile
        private var instance: SurveyEngine? = null

        fun get(context: Context): SurveyEngine =
            instance ?: synchronized(this) {
                instance ?: SurveyEngine(context.applicationContext as Application).also { instance = it }
            }
    }
}
