package com.ardyn.wavetop.scan

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
import android.os.Handler
import android.os.Looper
import com.ardyn.wavetop.model.BluetoothDeviceRecord
import com.ardyn.wavetop.oui.OuiLookup

/**
 * One scan is a bounded window: LE scan + classic discovery for [SCAN_WINDOW_MS], then both
 * stop and [onChange] fires with [isScanning] false. Advertisements arrive many times a second
 * per device, so result changes are coalesced and published at most every [PUBLISH_INTERVAL_MS].
 *
 * All callbacks (receiver, LE scan, timers) run on the main thread.
 */
class BluetoothSurveyScanner(
    private val context: Context,
    private val oui: OuiLookup,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val devices = HashMap<String, BluetoothDeviceRecord>()
    private val handler = Handler(Looper.getMainLooper())
    private var listener: (() -> Unit)? = null
    private var onRadioChanged: (() -> Unit)? = null
    private var scanning = false
    private var publishPending = false

    private val publish = Runnable {
        publishPending = false
        listener?.invoke()
    }
    private val endWindow = Runnable {
        stop()
        listener?.invoke()
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                if (!radioOn()) stop()
                onRadioChanged?.invoke()
                return
            }
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

    fun isScanning(): Boolean = scanning

    fun snapshot(): List<BluetoothDeviceRecord> =
        devices.values.sortedByDescending { it.rssiDbm ?: Int.MIN_VALUE }

    /** Forgets devices last heard before [cutoffEpochMs]; rotating BLE addresses otherwise pile up. */
    fun forgetBefore(cutoffEpochMs: Long) {
        devices.values.removeAll { it.lastSeenEpochMs < cutoffEpochMs }
    }

    /**
     * [onChange] fires with coalesced results and when a scan window ends;
     * [onRadioChanged] when Bluetooth is switched on or off.
     */
    fun register(onChange: () -> Unit, onRadioChanged: () -> Unit) {
        listener = onChange
        this.onRadioChanged = onRadioChanged
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerExported(context, classicReceiver, filter)
    }

    fun unregister() {
        stop()
        listener = null
        onRadioChanged = null
        handler.removeCallbacks(publish)
        publishPending = false
        try {
            context.unregisterReceiver(classicReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    /** Starts one scan window. Returns false if the radio is off or a window is already open. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val bt = adapter ?: return false
        if (!bt.isEnabled || scanning) return false
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
        handler.postDelayed(endWindow, SCAN_WINDOW_MS)
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(endWindow)
        if (!scanning) return
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
        // device.name is a binder call; only ask when nothing better is known yet.
        val name = advertisedName?.takeIf { it.isNotBlank() }
            ?: existing?.name?.takeIf { it.isNotBlank() }
            ?: try {
                device.name
            } catch (_: SecurityException) {
                null
            }
            ?: ""
        val mergedKind = when {
            existing == null -> kind
            existing.kind == kind -> kind
            else -> "Classic + BLE"
        }
        devices[address] = BluetoothDeviceRecord(
            name = name,
            address = address,
            rssiDbm = rssi ?: existing?.rssiDbm,
            phy = phy ?: existing?.phy,
            kind = mergedKind,
            encryption = "Not advertised",
            manufacturer = existing?.manufacturer ?: oui.manufacturerFor(address),
            lastSeenEpochMs = System.currentTimeMillis(),
        )
        if (!publishPending) {
            publishPending = true
            handler.postDelayed(publish, PUBLISH_INTERVAL_MS)
        }
    }

    companion object {
        /** About one classic inquiry cycle; also caps how long LOW_LATENCY LE scanning runs. */
        const val SCAN_WINDOW_MS = 12_000L
        const val PUBLISH_INTERVAL_MS = 500L
    }
}
