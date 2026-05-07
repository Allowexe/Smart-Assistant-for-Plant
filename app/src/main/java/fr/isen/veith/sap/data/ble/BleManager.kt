package fr.isen.veith.sap.data.ble

import android.annotation.SuppressLint
import android.content.Context
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.flow.StateFlow

/**
 * Point d'accès unique au BLE dans l'application.
 *
 * Initialiser dans Application.onCreate() :
 *   BleManager.init(this)
 *
 * Puis accéder depuis n'importe où :
 *   BleManager.connectionState.value
 *   BleManager.connect(device)
 */
object BleManager {

    @SuppressLint("StaticFieldLeak")
    private lateinit var scanner: BleScanner
    @SuppressLint("StaticFieldLeak")
    private lateinit var gatt: BleGattManager

    fun init(context: Context) {
        scanner = BleScanner(context.applicationContext)
        gatt    = BleGattManager(context.applicationContext)
    }

    // ── Scanner ───────────────────────────────────────────────────────
    val devices: StateFlow<List<BleDevice>>
        get() = scanner.devices

    val scanState: StateFlow<ScanState>
        get() = scanner.scanState

    val isBluetoothEnabled: Boolean
        get() = scanner.isBluetoothEnabled

    @Suppress("MissingPermission")
    fun startScan() = scanner.startScan()

    @Suppress("MissingPermission")
    fun stopScan() = scanner.stopScan()

    // ── Connexion GATT ────────────────────────────────────────────────
    val connectionState: StateFlow<ConnectionState>
        get() = gatt.connectionState

    val sensorData: StateFlow<SensorData?>
        get() = gatt.sensorData

    val lastError: StateFlow<String?>
        get() = gatt.lastError

    @Suppress("MissingPermission")
    fun connect(device: BleDevice) = gatt.connect(device)

    @Suppress("MissingPermission")
    fun disconnect() = gatt.disconnect()

    @Suppress("MissingPermission")
    fun close() = gatt.close()

    @Suppress("MissingPermission")
    fun sendCommand(command: PotCommand) = gatt.sendCommand(command)

    @Suppress("MissingPermission")
    fun requestSensorData() = gatt.requestSensorData()
}