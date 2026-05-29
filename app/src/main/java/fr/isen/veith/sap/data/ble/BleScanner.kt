package fr.isen.veith.sap.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Modèle d'un appareil BLE détecté ─────────────────────────────────
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isSap: Boolean = false
) {
    val signalBars: Int get() = when {
        rssi >= -55 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        else        -> 1
    }
}

// ── États du scan ─────────────────────────────────────────────────────
sealed class ScanState {
    object Idle        : ScanState()
    object Scanning    : ScanState()
    object Stopped     : ScanState()
    data class Error(val message: String) : ScanState()
}

// ── États de connexion ────────────────────────────────────────────────
sealed class ConnectionState {
    object Disconnected                    : ConnectionState()
    object Connecting                      : ConnectionState()
    data class Connected(val device: BleDevice) : ConnectionState()
    data class Failed(val reason: String)  : ConnectionState()
}

// ── BLE Scanner ───────────────────────────────────────────────────────
class BleScanner(context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _devices    = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _scanState  = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (!name.startsWith("ST")) return
            val device = BleDevice(
                address = result.device.address,
                name    = name,
                rssi    = result.rssi,
                isSap   = true
            )
            _devices.value = (_devices.value
                .filter { it.address != device.address } + device)
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            val msg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED          -> "Scan déjà en cours"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Erreur d'enregistrement"
                SCAN_FAILED_FEATURE_UNSUPPORTED      -> "BLE non supporté"
                else                                 -> "Erreur scan ($errorCode)"
            }
            _scanState.value = ScanState.Error(msg)
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION
    ])
    fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            _scanState.value = ScanState.Error("Bluetooth désactivé")
            return
        }
        _devices.value  = emptyList()
        _scanState.value = ScanState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(emptyList<ScanFilter>(), settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        _scanState.value = ScanState.Stopped
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
}