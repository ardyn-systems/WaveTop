package com.ardyn.wavetop.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ardyn.wavetop.survey.SurveyEntry
import com.ardyn.wavetop.survey.ExportFormat
import com.ardyn.wavetop.engine.EngineState
import com.ardyn.wavetop.engine.SurveyEngine
import com.ardyn.wavetop.model.DeviceSort
import com.ardyn.wavetop.model.DeviceTracker
import com.ardyn.wavetop.model.GeoFix
import com.ardyn.wavetop.model.ParsedSurvey
import com.ardyn.wavetop.model.PhyFilter
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.model.WifiBand
import com.ardyn.wavetop.net.NetSeerAddress
import com.ardyn.wavetop.net.NetSeerClient
import com.ardyn.wavetop.net.LiveSerializer
import com.ardyn.wavetop.net.QrPairing
import com.ardyn.wavetop.prefs.AppSettings
import com.ardyn.wavetop.prefs.NetSeerRoute
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.update.Updater
import com.ardyn.wavetop.update.UpdaterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val label: String) {
    Devices("Devices"),
    Map("Map"),
    Channels("Channels"),
    Surveys("Surveys"),
    Log("Log"),
}

enum class MapMode(val label: String) { Street("Street"), Radar("Radar") }

/** NetSeer's geo map offers streets or nothing behind the pins; so does WaveTop. */
enum class Basemap(val label: String) { Streets("Streets"), None("No basemap") }

enum class SurveyTab(val label: String) { Devices("Devices"), Map("Map") }

enum class SettingsPane(val label: String) {
    General("General"),
    NetSeer("NetSeer"),
    Data("Your data"),
    Updates("Updates"),
    Help("Help"),
    About("About"),
}

/** A saved survey opened from the Surveys tab, replayed into devices for the table and map. */
data class OpenSurvey(
    val entry: SurveyEntry,
    val survey: ParsedSurvey? = null,
    val devices: List<TrackedDevice> = emptyList(),
    /** Where the phone was at each geotagged sighting, in time order: the route driven. */
    val track: List<GeoFix> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val tab: SurveyTab = SurveyTab.Devices,
    /** Map time slider (epoch ms); null shows the whole survey. */
    val timeMs: Long? = null,
)

sealed interface TaskStatus {
    data object Idle : TaskStatus
    data class Working(val what: String) : TaskStatus
    data class Done(val message: String) : TaskStatus
    data class Failed(val message: String) : TaskStatus
}

/** Screen-only state; scan data lives in [EngineState], preferences in [Settings]. */
data class ViewState(
    val tab: Tab = Tab.Devices,
    val filter: PhyFilter = PhyFilter.All,
    val sort: DeviceSort = DeviceSort.Signal,
    val sortDescending: Boolean = true,
    val mapMode: MapMode = MapMode.Street,
    val basemap: Basemap = Basemap.Streets,
    val band: WifiBand = WifiBand.Band2g,
    /** Device whose details sheet is open. */
    val selectedKey: String? = null,
    /** Device ringed on the maps ("Show on map"). */
    val highlightKey: String? = null,
    /** Bumped to make the street map fly to [highlightKey]. */
    val focusNonce: Int = 0,
    val openSurvey: OpenSurvey? = null,
    /** Settings is open on this pane (null = closed). On phones the list shows first: [settingsList]. */
    val settingsPane: SettingsPane? = null,
    val settingsList: Boolean = true,
    /** Pairing with NetSeer, testing it, or sending a survey. */
    val netSeerTask: TaskStatus = TaskStatus.Idle,
    /** Your data: export/delete-all results. */
    val dataTask: TaskStatus = TaskStatus.Idle,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val engine = SurveyEngine.get(application)
    val engineState: StateFlow<EngineState> = engine.state
    private val appSettings = AppSettings.get(application)
    val settings: StateFlow<Settings> = appSettings.state
    val updater = Updater.get(application)
    val updates: StateFlow<UpdaterState> = updater.state

    private val _view = MutableStateFlow(ViewState())
    val view: StateFlow<ViewState> = _view.asStateFlow()

    private var attached = false
    private var netSeerJob: Job? = null

    // --- lifecycle -------------------------------------------------------------------------

    fun start() {
        if (attached) return
        attached = true
        engine.attachUi()
        engine.refreshSurveys()
    }

    fun stop() {
        if (!attached) return
        attached = false
        engine.detachUi()
    }

    override fun onCleared() {
        stop()
    }

    fun onPermissionsChanged() = engine.recheck()

    // --- navigation ----------------------------------------------------------------------------

    fun selectTab(tab: Tab) = _view.update { it.copy(tab = tab) }

    fun openSettings(pane: SettingsPane? = null) = _view.update {
        it.copy(settingsPane = pane ?: SettingsPane.General, settingsList = pane == null)
    }

    fun showSettingsPane(pane: SettingsPane) = _view.update { it.copy(settingsPane = pane, settingsList = false) }

    fun backInSettings() = _view.update {
        if (!it.settingsList) it.copy(settingsList = true) else it.copy(settingsPane = null)
    }

    fun closeSettings() = _view.update { it.copy(settingsPane = null, settingsList = true) }

    // --- live view ---------------------------------------------------------------------------

    fun setLive(live: Boolean) = engine.setLive(live)

    fun setFilter(filter: PhyFilter) = _view.update { it.copy(filter = filter) }

    /** Tapping the active column flips direction; a new column starts in its natural direction. */
    fun sortBy(sort: DeviceSort) = _view.update {
        if (it.sort == sort) it.copy(sortDescending = !it.sortDescending)
        else it.copy(sort = sort, sortDescending = sort.defaultDescending)
    }

    fun setMapMode(mode: MapMode) = _view.update { it.copy(mapMode = mode) }

    fun setBasemap(basemap: Basemap) = _view.update { it.copy(basemap = basemap) }

    fun setBand(band: WifiBand) = _view.update { it.copy(band = band) }

    fun select(key: String?) = _view.update { it.copy(selectedKey = key, highlightKey = key ?: it.highlightKey) }

    /**
     * Closes the details sheet and shows the device on a map: the street map if it has been
     * pinned, otherwise the radar. Works for the live list and for an open survey.
     */
    fun showOnMap(device: TrackedDevice) = _view.update { v ->
        val base = v.copy(selectedKey = null, highlightKey = device.key, focusNonce = v.focusNonce + 1)
        if (v.tab == Tab.Surveys && v.openSurvey != null) {
            base.copy(openSurvey = v.openSurvey.copy(tab = SurveyTab.Map, timeMs = null))
        } else {
            base.copy(
                tab = Tab.Map,
                mapMode = if (device.bestFix != null) MapMode.Street else MapMode.Radar,
                // Make sure the filter doesn't hide what we're about to show.
                filter = if (v.filter.matches(device)) v.filter else PhyFilter.All,
            )
        }
    }

    // --- survey ----------------------------------------------------------------------------

    fun startSurvey(name: String, streamLive: Boolean): Boolean = engine.startSurvey(name, streamLive)

    fun stopSurvey() = engine.stopSurvey()

    fun openSurvey(entry: SurveyEntry) {
        _view.update { it.copy(openSurvey = OpenSurvey(entry), selectedKey = null, highlightKey = null) }
        viewModelScope.launch {
            val survey = engine.loadSurvey(entry)
            // Replay every sighting so the survey reads like a live survey: history, min/max, best pin.
            val replayed = survey?.let {
                withContext(Dispatchers.Default) {
                    val tracker = DeviceTracker(historySize = 60, expireAfterMs = Long.MAX_VALUE / 4)
                    it.observations.sortedBy { o -> o.timeMs }.forEach(tracker::replay)
                    val track = it.observations.mapNotNull { o -> o.fix }.sortedBy { f -> f.timeMs }
                        .distinctBy { f -> f.lat to f.lon }
                    tracker.devices() to track
                }
            }
            _view.update { v ->
                if (v.openSurvey?.entry?.id != entry.id) return@update v
                if (survey == null || replayed == null) {
                    v.copy(openSurvey = v.openSurvey.copy(loading = false, error = "This file couldn't be read."))
                } else {
                    v.copy(
                        openSurvey = v.openSurvey.copy(
                            survey = survey,
                            devices = replayed.first,
                            track = replayed.second,
                            loading = false,
                        ),
                    )
                }
            }
        }
    }

    fun closeSurvey() = _view.update { it.copy(openSurvey = null, selectedKey = null, highlightKey = null) }

    fun setSurveyTab(tab: SurveyTab) = _view.update { v -> v.copy(openSurvey = v.openSurvey?.copy(tab = tab)) }

    fun setSurveyTime(timeMs: Long?) = _view.update { v -> v.copy(openSurvey = v.openSurvey?.copy(timeMs = timeMs)) }

    fun deleteSurvey(entry: SurveyEntry) {
        engine.deleteSurvey(entry)
        if (_view.value.openSurvey?.entry?.id == entry.id) closeSurvey()
    }

    /** Writes an export of the open survey to the share cache. */
    suspend fun exportOpenSurvey(format: ExportFormat): File? {
        val open = _view.value.openSurvey ?: return null
        val survey = open.survey ?: return null
        return runCatching { engine.exportSurvey(open.entry, survey, format) }
            .onFailure { engine.log("Export failed: ${it.message}", warning = true) }
            .getOrNull()
    }

    // --- settings ------------------------------------------------------------------------------

    fun updateSettings(change: (Settings) -> Settings) = appSettings.update(change)

    fun dismissWelcome() = appSettings.update { it.copy(welcomed = true) }

    /** Your data → export every saved survey as one zip, for backup or moving phones. */
    suspend fun exportAllSurveys(): File? {
        _view.update { it.copy(dataTask = TaskStatus.Working("Packing your surveys…")) }
        val file = runCatching { engine.exportAllSurveys() }.getOrElse {
            _view.update { v -> v.copy(dataTask = TaskStatus.Failed("Couldn't pack the surveys: ${it.message}")) }
            return null
        }
        _view.update {
            it.copy(dataTask = if (file == null) TaskStatus.Failed("There are no saved surveys yet.") else TaskStatus.Idle)
        }
        return file
    }

    fun deleteAllSurveys() {
        viewModelScope.launch {
            val n = engine.deleteAllSurveys()
            closeSurvey()
            _view.update { it.copy(dataTask = TaskStatus.Done("Deleted $n saved survey${if (n == 1) "" else "s"}.")) }
        }
    }

    fun resetSettings() {
        appSettings.reset()
        _view.update { it.copy(dataTask = TaskStatus.Done("Settings are back to their defaults.")) }
    }

    // --- NetSeer ------------------------------------------------------------------------------

    /** The address the chosen route points at, or null if the typed host isn't usable. */
    fun netSeerBaseUrl(s: Settings = settings.value): String? = when (s.netSeerRoute) {
        NetSeerRoute.Usb -> NetSeerAddress.USB_BASE_URL
        NetSeerRoute.Network -> NetSeerAddress.normalize(s.netSeerHost)
    }

    fun setNetSeerRoute(route: NetSeerRoute) {
        appSettings.update { it.copy(netSeerRoute = route) }
        setNetSeerTask(TaskStatus.Idle)
    }

    fun setNetSeerHost(host: String) {
        appSettings.update { it.copy(netSeerHost = host) }
        setNetSeerTask(TaskStatus.Idle)
    }

    fun resetNetSeerTask() = setNetSeerTask(TaskStatus.Idle)

    /** Is NetSeer there? (`GET /api/v1/info`, no token needed.) */
    fun testNetSeer() {
        val url = netSeerBaseUrl() ?: return setNetSeerTask(TaskStatus.Failed("Enter NetSeer's address, e.g. 192.168.1.20"))
        runNetSeer("Looking for NetSeer at $url…") {
            NetSeerClient.info(url)
                .onSuccess { setNetSeerTask(TaskStatus.Done("Found ${it.product} ${it.version} at $url.")) }
                .onFailure { setNetSeerTask(TaskStatus.Failed(it.message ?: "Not reachable")) }
        }
    }

    /** Trade the 6-digit code from NetSeer's Settings → Integrations for a device token. */
    fun pairNetSeer(code: String) {
        val url = netSeerBaseUrl() ?: return setNetSeerTask(TaskStatus.Failed("Enter NetSeer's address, e.g. 192.168.1.20"))
        if (code.isBlank()) return setNetSeerTask(TaskStatus.Failed("Type the pairing code NetSeer shows."))
        runNetSeer("Pairing with NetSeer…") {
            NetSeerClient.pair(url, code, deviceName())
                .onSuccess { link ->
                    appSettings.update { it.copy(netSeer = link) }
                    engine.log("Paired with NetSeer at $url")
                    setNetSeerTask(TaskStatus.Done("Paired. Surveys can now go straight to NetSeer."))
                }
                .onFailure { setNetSeerTask(TaskStatus.Failed(it.message ?: "Pairing failed")) }
        }
    }

    /**
     * Pairs from a scanned NetSeer QR: tries each address it carries (remote/Tailscale first, then
     * LAN), uses the first that answers, and pairs with the code baked into the QR — no typing.
     */
    fun pairFromQr(raw: String) {
        val payload = QrPairing.parse(raw)
            ?: return setNetSeerTask(TaskStatus.Failed("That isn't a NetSeer pairing QR. In NetSeer: Settings → Integrations → Pair a device."))
        runNetSeer("Pairing with ${payload.name ?: "NetSeer"}…") {
            var lastError: String? = null
            for (host in payload.hosts) {
                val url = NetSeerAddress.normalize(host) ?: continue
                if (NetSeerClient.info(url).isFailure) {
                    lastError = "Couldn't reach NetSeer at $host."
                    continue
                }
                NetSeerClient.pair(url, payload.code, deviceName())
                    .onSuccess { link ->
                        appSettings.update { it.copy(netSeer = link, netSeerRoute = NetSeerRoute.Network, netSeerHost = host) }
                        engine.log("Paired with NetSeer at $url (QR)")
                        setNetSeerTask(TaskStatus.Done("Paired with ${payload.name ?: "NetSeer"}. Surveys can now go straight to NetSeer."))
                    }
                    .onFailure { setNetSeerTask(TaskStatus.Failed(it.message ?: "Pairing failed")) }
                return@runNetSeer
            }
            setNetSeerTask(TaskStatus.Failed(lastError ?: "Couldn't reach NetSeer at the scanned address."))
        }
    }

    /** Forget the token here; NetSeer's Settings → Integrations can revoke it there too. */
    fun unpairNetSeer() {
        appSettings.update { it.copy(netSeer = null) }
        setNetSeerTask(TaskStatus.Done("Unpaired. Also remove this phone in NetSeer → Settings → Integrations."))
    }

    /**
     * Pushes the open survey to the paired NetSeer as a WiGLE CSV: Wi-Fi *and* Bluetooth with the
     * GPS position of every sighting, which NetSeer maps and turns into location estimates.
     */
    fun sendOpenSurveyToNetSeer() {
        val open = _view.value.openSurvey ?: return
        val survey = open.survey ?: return
        val link = settings.value.netSeer
            ?: return setNetSeerTask(TaskStatus.Failed("Pair with NetSeer first: Settings → NetSeer."))
        if (survey.observations.none { it.fix != null }) {
            return setNetSeerTask(TaskStatus.Failed("Nothing in this survey has a GPS position, so there's nothing to map."))
        }
        val name = open.entry.meta.name
        runNetSeer("Sending \"$name\" to NetSeer…") {
            // Prefer observation deltas: NetSeer then rebuilds the driven route, not just the pins.
            // Fall back to a WiGLE CSV on older NetSeer (no observations endpoint) — devices, no route.
            val batch = LiveSerializer.batch(survey.observations)
            if (batch != null) {
                val result = NetSeerClient.pushObservations(link, batch, name)
                if (result.exceptionOrNull() !== NetSeerClient.EndpointMissing) {
                    result
                        .onSuccess { setNetSeerTask(TaskStatus.Done(it)); engine.log("Sent \"$name\" to NetSeer") }
                        .onFailure { setNetSeerTask(TaskStatus.Failed(it.message ?: "Send failed")) }
                    return@runNetSeer
                }
            }
            val file = runCatching { engine.exportSurvey(open.entry, survey, ExportFormat.WigleCsv) }.getOrElse {
                setNetSeerTask(TaskStatus.Failed("Couldn't build the export: ${it.message}"))
                return@runNetSeer
            }
            NetSeerClient.pushCapture(link, file, name, format = "csv")
                .onSuccess {
                    setNetSeerTask(TaskStatus.Done(it))
                    engine.log("Sent \"$name\" to NetSeer")
                }
                .onFailure { setNetSeerTask(TaskStatus.Failed(it.message ?: "Send failed")) }
        }
    }

    private fun runNetSeer(what: String, block: suspend () -> Unit) {
        netSeerJob?.cancel()
        netSeerJob = viewModelScope.launch {
            setNetSeerTask(TaskStatus.Working(what))
            block()
        }
    }

    private fun setNetSeerTask(status: TaskStatus) = _view.update { it.copy(netSeerTask = status) }

    /** How this phone appears in NetSeer's paired-devices list. */
    private fun deviceName(): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        return "WaveTop on $model"
    }
}
