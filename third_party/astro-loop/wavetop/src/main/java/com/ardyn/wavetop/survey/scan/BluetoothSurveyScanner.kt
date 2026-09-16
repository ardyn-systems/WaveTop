package com.ardyn.wavetop.survey.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.ardyn.wavetop.survey.model.BluetoothDeviceRecord
import com.ardyn.wavetop.survey.oui.OuiLookup
import java.util.concurrent.ConcurrentHashMap

class BluetoothSurveyScanner(
    private val context: Context,
    private val oui: OuiLookup,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val devices = ConcurrentHashMap<String, BluetoothDeviceRecord>()
    private var listener: (() -> Unit)? = null
    private var scanning = false

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                .takeIf { it != Short.MIN_VALUE }?.toInt()
            upsert(device, rssi, kind = "Classic", phy = "BR/EDR")
        }
    }

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val phy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (result.primaryPhy) {
                    BluetoothDevice.PHY_LE_1M -> "LE 1M"
                    BluetoothDevice.PHY_LE_2M -> "LE 2M"
                    BluetoothDevice.PHY_LE_CODED -> "LE Coded"
                    else -> "BLE"
                }
            } else {
                "BLE"
            }
            val advertised = result.scanRecord?.deviceName
            upsert(result.device, result.rssi, kind = "BLE", phy = phy, advertisedName = advertised)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    fun available(): Boolean = adapter != null

    fun radioOn(): Boolean = adapter?.isEnabled == true

    fun snapshot(): List<BluetoothDeviceRecord> =
        devices.values.sortedWith(compareByDescending<BluetoothDeviceRecord> { it.rssiDbm ?: Int.MIN_VALUE })

    fun register(onChange: () -> Unit) {
        listener = onChange
        registerExported(context, classicReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    fun unregister() {
        stop()
        listener = null
        try {
            context.unregisterReceiver(classicReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val bt = adapter ?: return
        if (!bt.isEnabled || scanning) return
        scanning = true
        try {
            bt.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                leCallback,
            )
        } catch (_: SecurityException) {
        }
        try {
            bt.startDiscovery()
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        scanning = false
        val bt = adapter ?: return
        try {
            bt.bluetoothLeScanner?.stopScan(leCallback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun upsert(
        device: BluetoothDevice,
        rssi: Int?,
        kind: String,
        phy: String?,
        advertisedName: String? = null,
    ) {
        val address = device.address ?: return
        val existing = devices[address]
        val name = advertisedName
            ?: try {
                device.name
            } catch (_: SecurityException) {
                null
            }
            ?: existing?.name.orEmpty()
        val mergedKind = when {
            existing == null -> kind
            existing.kind == kind -> kind
            else -> "Classic + BLE"
        }
        devices[address] = BluetoothDeviceRecord(
            name = name.ifBlank { existing?.name.orEmpty() },
            address = address,
            rssiDbm = rssi ?: existing?.rssiDbm,
            phy = phy ?: existing?.phy,
            kind = mergedKind,
            encryption = "Not advertised",
            manufacturer = oui.manufacturerFor(address),
            lastSeenEpochMs = System.currentTimeMillis(),
        )
        listener?.invoke()
    }
}
