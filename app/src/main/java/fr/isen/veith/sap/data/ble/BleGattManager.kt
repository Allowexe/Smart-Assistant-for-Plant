package fr.isen.veith.sap.data.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// ── UUIDs des services et caractéristiques du pot ─────────────────────
// ⚠️ À adapter selon le firmware de ton pot connecté
object PotUuids {
    // Service principal du pot
    val SERVICE         = UUID.fromString("12345678-1234-1234-1234-123456789abc")

    // Caractéristiques capteurs (lecture + notification)
    val HUMIDITY        = UUID.fromString("12345678-1234-1234-1234-123456789ab1")
    val LUMINOSITY      = UUID.fromString("12345678-1234-1234-1234-123456789ab2")
    val TEMPERATURE     = UUID.fromString("12345678-1234-1234-1234-123456789ab3")

    // Caractéristique commandes (écriture)
    val COMMAND         = UUID.fromString("12345678-1234-1234-1234-123456789ab4")

    // Descriptor standard pour activer les notifications
    val CCCD            = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// ── Commandes envoyables au pot ───────────────────────────────────────
enum class PotCommand(val byte: Byte) {
    WATER_NOW(0x01),       // Déclencher l'arrosage
    LED_ON(0x02),          // Allumer la LED
    LED_OFF(0x03),         // Éteindre la LED
    REQUEST_DATA(0x04)     // Demander une lecture immédiate des capteurs
}

// ── Gestionnaire GATT ─────────────────────────────────────────────────
class BleGattManager(private val context: Context) {

    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // ── Connexion ─────────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BleDevice) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btDevice = bluetoothManager.adapter.getRemoteDevice(device.address)

        _connectionState.value = ConnectionState.Connecting

        gatt =
            btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // ── Déconnexion ───────────────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.disconnect()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    // ── Envoi d'une commande ──────────────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: PotCommand): Boolean {
        val g = gatt ?: return false
        val service = g.getService(PotUuids.SERVICE) ?: return false
        val characteristic = service.getCharacteristic(PotUuids.COMMAND) ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                characteristic,
                byteArrayOf(command.byte),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = byteArrayOf(command.byte)
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    // ── Demander une lecture immédiate des capteurs ───────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestSensorData() = sendCommand(PotCommand.REQUEST_DATA)

    // ── Callback GATT ─────────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Lancer la découverte des services après connexion
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    this@BleGattManager.gatt?.close()
                    this@BleGattManager.gatt = null
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val msg = "Erreur GATT: $status"
                _connectionState.value = ConnectionState.Failed(msg)
                _lastError.value = msg
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Failed("Services non trouvés")
                return
            }

            val service = gatt.getService(PotUuids.SERVICE)
            if (service == null) {
                // Le service du pot n'est pas présent → appareil non compatible
                _connectionState.value = ConnectionState.Failed("Ce n'est pas un pot Sap")
                return
            }

            // Activer les notifications sur les 3 capteurs
            enableNotifications(gatt, service, PotUuids.HUMIDITY)
            enableNotifications(gatt, service, PotUuids.LUMINOSITY)
            enableNotifications(gatt, service, PotUuids.TEMPERATURE)

            // Marquer comme connecté avec le vrai BleDevice
            val address = gatt.device.address
            val name    = gatt.device.name ?: "Pot inconnu"
            _connectionState.value = ConnectionState.Connected(
                BleDevice(address = address, name = name, rssi = 0, isSap = true)
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // API 33+
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // API < 33
            handleCharacteristicValue(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }
    }

    // ── Activer les notifications GATT ────────────────────────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        charUuid: UUID
    ) {
        val characteristic = service.getCharacteristic(charUuid) ?: return
        gatt.setCharacteristicNotification(characteristic, true)

        // Écrire le descriptor CCCD pour activer les notifications côté firmware
        val descriptor = characteristic.getDescriptor(PotUuids.CCCD) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ── Parser les valeurs reçues ─────────────────────────────────────
    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return

        // Protocole simple : 4 bytes IEEE 754 float little-endian
        val float = value.toFloat() ?: return

        val current = _sensorData.value ?: SensorData(0f, 0f, 0f)
        _sensorData.value = when (uuid) {
            PotUuids.HUMIDITY    -> current.copy(humidity    = float)
            PotUuids.LUMINOSITY  -> current.copy(luminosity  = float)
            PotUuids.TEMPERATURE -> current.copy(temperature = float)
            else                 -> current
        }
    }

    // ── Extension : ByteArray → Float (little-endian IEEE 754) ───────
    private fun ByteArray.toFloat(): Float? {
        if (size < 4) return null
        val bits = ((this[3].toInt() and 0xFF) shl 24) or
                ((this[2].toInt() and 0xFF) shl 16) or
                ((this[1].toInt() and 0xFF) shl 8)  or
                (this[0].toInt() and 0xFF)
        return java.lang.Float.intBitsToFloat(bits)
    }
}