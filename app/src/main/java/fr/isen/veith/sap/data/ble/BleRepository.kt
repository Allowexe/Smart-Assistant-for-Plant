package fr.isen.veith.sap.data.ble

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BleRepository(context: Context) {

    private val scanner       = BleScanner(context.applicationContext)
    private val commissioning = CommissioningGattManager(context.applicationContext)

    val devices: StateFlow<List<BleDevice>>               = scanner.devices
    val scanState: StateFlow<ScanState>                   = scanner.scanState
    val commissioningState: StateFlow<CommissioningState> = commissioning.state
    val apEvent: SharedFlow<WifiAP>                       = commissioning.apEvent
    val isBluetoothEnabled: Boolean get()                 = scanner.isBluetoothEnabled

    @Suppress("MissingPermission")
    fun startScan() = scanner.startScan()

    @Suppress("MissingPermission")
    fun stopScan() = scanner.stopScan()

    @Suppress("MissingPermission")
    fun connectForCommissioning(device: BleDevice) {
        scanner.stopScan()
        commissioning.connect(device)
    }

    @Suppress("MissingPermission")
    fun triggerWifiScan() = commissioning.startWifiScan()

    @Suppress("MissingPermission")
    fun sendCredentials(ssid: String, password: String) =
        commissioning.sendCredentialsAndConnect(ssid, password)

    @Suppress("MissingPermission")
    fun disconnect() = commissioning.disconnectGatt()
}
