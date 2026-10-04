package com.ardyn.wavetop.survey.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ardyn.wavetop.survey.model.BluetoothDeviceRecord
import com.ardyn.wavetop.survey.model.SurveyGate
import com.ardyn.wavetop.survey.model.SurveyTab
import com.ardyn.wavetop.survey.model.WifiApRecord
import com.ardyn.wavetop.survey.oui.OuiLookup
import com.ardyn.wavetop.survey.permissions.SurveyPermissions
import com.ardyn.wavetop.survey.scan.BluetoothSurveyScanner
import com.ardyn.wavetop.survey.scan.WifiSurveyScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveyUiState(
    val tab: SurveyTab = SurveyTab.Wifi,
    val gate: SurveyGate = SurveyGate.PermissionDenied,
    val wifiScanning: Boolean = false,
    val bluetoothScanning: Boolean = false,
    /** Android refused the last Wi-Fi scan request (throttling); the list shows cached results. */
    val wifiScanThrottled: Boolean = false,
    val wifiResults: List<WifiApRecord> = emptyList(),
    val bluetoothResults: List<BluetoothDeviceRecord> = emptyList(),
)

class SurveyViewModel(application: Application) : AndroidViewModel(application) {
    private val oui: OuiLookup = try {
        OuiLookup.loadFromGzipTsv(application.assets.open("oui/oui.tsv.gz"))
    } catch (_: Exception) {
        OuiLookup.fromMap(emptyMap())
    }

    private val wifi = WifiSurveyScanner(application, oui)
    private val bluetooth = BluetoothSurveyScanner(application, oui)

    private val _state = MutableStateFlow(SurveyUiState())
    val state: StateFlow<SurveyUiState> = _state.asStateFlow()
    private var running = false
    private var lastWifiScanMs = Long.MIN_VALUE / 2
    private var lastBluetoothScanMs = Long.MIN_VALUE / 2
    private var wifiScanTimeout: Job? = null

    /** Screen visible: listen for results and refresh the current tab. */
    fun start() {
        if (running) return
        running = true
        wifi.register(onResults = ::onWifiResults, onRadioChanged = ::recheckGate)
        bluetooth.register(::publishBluetooth)
        refreshGate()
        autoScan()
    }

    /** Screen hidden: stop the radios and receivers so nothing scans in the background. */
    fun stop() {
        if (!running) return
        running = false
        wifi.unregister()
        bluetooth.unregister()
        wifiScanTimeout?.cancel()
        _state.update { it.copy(wifiScanning = false, bluetoothScanning = false) }
    }

    fun selectTab(tab: SurveyTab) {
        if (tab == _state.value.tab) return
        // The LE scan is the expensive one; don't leave it running behind the Wi-Fi tab.
        if (tab != SurveyTab.Bluetooth) {
            bluetooth.stop()
            publishBluetooth()
        }
        _state.update { it.copy(tab = tab) }
        refreshGate()
        autoScan()
    }

    /** On resume and after the permission dialog: permissions, radios or location may have changed. */
    fun onPermissionsChanged() = recheckGate()

    /** The SCAN button: always asks for a fresh scan. */
    fun scan() {
        refreshGate()
        if (_state.value.gate != SurveyGate.Ready) return
        when (_state.value.tab) {
            SurveyTab.Wifi -> scanWifi()
            SurveyTab.Bluetooth -> scanBluetooth()
        }
    }

    override fun onCleared() {
        stop()
    }

    /**
     * Scans triggered by opening the screen, switching tabs, or the gate opening — not by the
     * user. Skipped when the tab was scanned recently: every Wi-Fi request spends part of
     * Android's 4-per-2-minutes allowance, and restarting LE scans too often gets them refused.
     */
    private fun autoScan() {
        if (!running || _state.value.gate != SurveyGate.Ready) return
        val now = SystemClock.elapsedRealtime()
        when (_state.value.tab) {
            SurveyTab.Wifi ->
                if (now - lastWifiScanMs >= AUTO_RESCAN_MS) scanWifi() else publishWifiResults()
            SurveyTab.Bluetooth ->
                if (now - lastBluetoothScanMs >= AUTO_RESCAN_MS) scanBluetooth() else publishBluetooth()
        }
    }

    private fun recheckGate() {
        val wasReady = _state.value.gate == SurveyGate.Ready
        refreshGate()
        if (_state.value.gate != SurveyGate.Ready) {
            wifiScanTimeout?.cancel()
            bluetooth.stop()
            _state.update { it.copy(wifiScanning = false, bluetoothScanning = false) }
        } else if (!wasReady) {
            autoScan()
        }
    }

    private fun scanWifi() {
        lastWifiScanMs = SystemClock.elapsedRealtime()
        val accepted = wifi.requestScan()
        // Show what the platform already has while the new scan runs.
        publishWifiResults()
        _state.update { it.copy(wifiScanning = accepted, wifiScanThrottled = !accepted) }
        wifiScanTimeout?.cancel()
        if (accepted) {
            // The results broadcast normally lands within a few seconds; don't spin forever if it doesn't.
            wifiScanTimeout = viewModelScope.launch {
                delay(WIFI_SCAN_TIMEOUT_MS)
                _state.update { it.copy(wifiScanning = false) }
            }
        }
    }

    private fun onWifiResults() {
        wifiScanTimeout?.cancel()
        publishWifiResults()
        _state.update { it.copy(wifiScanning = false, wifiScanThrottled = false) }
    }

    private fun scanBluetooth() {
        lastBluetoothScanMs = SystemClock.elapsedRealtime()
        bluetooth.start()
        publishBluetooth()
    }

    private fun refreshGate() {
        val app = getApplication<Application>()
        val tab = _state.value.tab
        val gate = when {
            !SurveyPermissions.allGranted(app) -> SurveyGate.PermissionDenied
            !SurveyPermissions.locationEnabled(app) -> SurveyGate.LocationOff
            tab == SurveyTab.Wifi && !wifi.available() -> SurveyGate.Unavailable
            tab == SurveyTab.Wifi && !wifi.radioOn() -> SurveyGate.RadioOff
            tab == SurveyTab.Bluetooth && !bluetooth.available() -> SurveyGate.Unavailable
            tab == SurveyTab.Bluetooth && !bluetooth.radioOn() -> SurveyGate.RadioOff
            else -> SurveyGate.Ready
        }
        _state.update { it.copy(gate = gate) }
    }

    private fun publishWifiResults() {
        _state.update { it.copy(wifiResults = wifi.snapshot(System.currentTimeMillis())) }
    }

    private fun publishBluetooth() {
        _state.update {
            it.copy(
                bluetoothResults = bluetooth.snapshot(),
                bluetoothScanning = bluetooth.isScanning(),
            )
        }
    }

    private companion object {
        const val AUTO_RESCAN_MS = 30_000L
        const val WIFI_SCAN_TIMEOUT_MS = 15_000L
    }
}
