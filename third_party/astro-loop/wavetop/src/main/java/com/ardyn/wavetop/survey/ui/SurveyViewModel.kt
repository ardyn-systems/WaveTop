package com.ardyn.wavetop.survey.ui

import android.app.Application
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

    fun start() {
        if (running) {
            refreshGate()
            return
        }
        running = true
        wifi.register { publishWifi() }
        bluetooth.register { publishBluetooth() }
        refreshGate()
        if (_state.value.gate == SurveyGate.Ready) {
            scan()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        wifi.unregister()
        bluetooth.unregister()
        _state.update { it.copy(wifiScanning = false, bluetoothScanning = false) }
    }

    fun selectTab(tab: SurveyTab) {
        _state.update { it.copy(tab = tab) }
        refreshGate()
        if (_state.value.gate == SurveyGate.Ready) scan()
    }

    fun onPermissionsChanged() {
        refreshGate()
        if (_state.value.gate == SurveyGate.Ready) scan()
    }

    fun scan() {
        refreshGate()
        if (_state.value.gate != SurveyGate.Ready) return
        viewModelScope.launch {
            when (_state.value.tab) {
                SurveyTab.Wifi -> {
                    _state.update { it.copy(wifiScanning = true) }
                    wifi.requestScan()
                    publishWifi()
                }
                SurveyTab.Bluetooth -> {
                    _state.update { it.copy(bluetoothScanning = true) }
                    bluetooth.start()
                    publishBluetooth()
                }
            }
        }
    }

    private fun refreshGate() {
        val app = getApplication<Application>()
        val gate = when {
            !SurveyPermissions.allGranted(app) -> SurveyGate.PermissionDenied
            !SurveyPermissions.locationEnabled(app) -> SurveyGate.LocationOff
            _state.value.tab == SurveyTab.Wifi && !wifi.available() -> SurveyGate.Unavailable
            _state.value.tab == SurveyTab.Wifi && !wifi.radioOn() -> SurveyGate.RadioOff
            _state.value.tab == SurveyTab.Bluetooth && !bluetooth.available() -> SurveyGate.Unavailable
            _state.value.tab == SurveyTab.Bluetooth && !bluetooth.radioOn() -> SurveyGate.RadioOff
            else -> SurveyGate.Ready
        }
        _state.update { it.copy(gate = gate) }
    }

    private fun publishWifi() {
        refreshGate()
        _state.update {
            it.copy(
                wifiResults = wifi.snapshot(System.currentTimeMillis()),
                wifiScanning = false,
            )
        }
    }

    private fun publishBluetooth() {
        refreshGate()
        _state.update {
            it.copy(bluetoothResults = bluetooth.snapshot())
        }
    }
}
