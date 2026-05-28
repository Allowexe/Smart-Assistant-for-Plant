package fr.isen.veith.sap.data.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

// ── UUIDs firmware ST67W6X_BLE_Commissioning ─────────────────────────
object CommissioningUuids {
    val SERVICE    = UUID.fromString("0000ff9a-cc7a-482a-984a-7f2ed5b3e58f")
    val CONTROL    = UUID.fromString("0000fe9b-8e22-4541-9d4c-21edae82ed19") // Write w/ Response
    val CONFIGURE  = UUID.fromString("0000fe9c-8e22-4541-9d4c-21edae82ed19") // Write w/ Response
    val AP_LIST    = UUID.fromString("0000fe9d-8e22-4541-9d4c-21edae82ed19") // Notify
    val MONITORING = UUID.fromString("0000fe9e-8e22-4541-9d4c-21edae82ed19") // Read + Notify
    val CCCD       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// ── Sécurité Wi-Fi (uint32 firmware) ─────────────────────────────────
enum class WifiSecurity(val label: String) {
    OPEN("Ouvert"), WPA_PSK("WPA"), WPA2_PSK("WPA2"),
    WPA_WPA2("WPA/WPA2"), WPA3("WPA3"), WPA2_WPA3("WPA2/WPA3"), UNKNOWN("?");
    companion object {
        fun from(v: Int) = when (v) {
            0 -> OPEN; 2 -> WPA_PSK; 3 -> WPA2_PSK
            4 -> WPA_WPA2; 6 -> WPA3; 7 -> WPA2_WPA3; else -> UNKNOWN
        }
    }
}

data class WifiAP(
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val security: WifiSecurity
)

// ── États du commissioning ────────────────────────────────────────────
sealed class CommissioningState {
    object Idle                : CommissioningState()
    object Connecting          : CommissioningState()
    object DiscoveringServices : CommissioningState()
    object Ready               : CommissioningState()       // prêt, attend action user
    object ScanningWifi        : CommissioningState()       // scan en cours
    object SendingCredentials  : CommissioningState()       // envoi SSID/PWD/connect
    data class Monitoring(val statusText: String) : CommissioningState()
    object Done                : CommissioningState()       // 0x04 reçu, IP obtenue
    data class Error(val message: String) : CommissioningState()
}

class CommissioningGattManager(private val context: Context) {

    private var gatt: BluetoothGatt? = null

    private val _state = MutableStateFlow<CommissioningState>(CommissioningState.Idle)
    val state: StateFlow<CommissioningState> = _state.asStateFlow()

    // Un événement par AP reçu sur la caractéristique AP_LIST
    private val _apEvent = MutableSharedFlow<WifiAP>(extraBufferCapacity = 64)
    val apEvent: SharedFlow<WifiAP> = _apEvent.asSharedFlow()

    // Queue pour sérialiser les Write-with-Response
    private val lock = Any()
    private val writeQueue = ArrayDeque<Pair<UUID, ByteArray>>()
    private var writeBusy = false

    // Queue pour sérialiser les enableNotify (writeDescriptor)
    private val pendingNotifyUuids = ArrayDeque<UUID>()

    // ── API publique ──────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BleDevice) {
        synchronized(lock) { writeQueue.clear(); writeBusy = false }
        _state.value = CommissioningState.Connecting
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        gatt = adapter.getRemoteDevice(device.address)
            .connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startWifiScan() {
        _state.value = CommissioningState.ScanningWifi
        enqueue(CommissioningUuids.CONTROL, byteArrayOf(0x01))
    }

    // Envoie SSID, puis password, puis 0x03 (connect) — chaque écriture attend l'ACK
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCredentialsAndConnect(ssid: String, password: String) {
        _state.value = CommissioningState.SendingCredentials
        enqueue(CommissioningUuids.CONFIGURE, byteArrayOf(0x01) + ssid.toByteArray(Charsets.UTF_8))
        enqueue(CommissioningUuids.CONFIGURE, byteArrayOf(0x02) + password.toByteArray(Charsets.UTF_8))
        enqueue(CommissioningUuids.CONTROL,   byteArrayOf(0x03))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectGatt() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        synchronized(lock) { writeQueue.clear(); writeBusy = false }
        _state.value = CommissioningState.Idle
    }

    // ── Write queue ───────────────────────────────────────────────────

    private fun enqueue(uuid: UUID, value: ByteArray) {
        synchronized(lock) {
            writeQueue.addLast(uuid to value)
            if (!writeBusy) drainQueue()
        }
    }

    // Doit être appelé dans synchronized(lock)
    private fun drainQueue() {
        val next = writeQueue.removeFirstOrNull()
        if (next == null) {
            writeBusy = false
            // 0x03 firmware never emits CONNECTING notif — transition locally once queue empty
            if (_state.value is CommissioningState.SendingCredentials) {
                _state.value = CommissioningState.Monitoring("Connexion en cours…")
            }
            return
        }
        writeBusy = true
        doWrite(next.first, next.second)
    }

    @Suppress("MissingPermission")
    private fun doWrite(charUuid: UUID, value: ByteArray) {
        val g   = gatt ?: return
        val svc = g.getService(CommissioningUuids.SERVICE) ?: return
        val chr = svc.getCharacteristic(charUuid) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(chr, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            chr.value = value
            chr.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(chr)
        }
    }

    // ── Notifications ─────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun enableNextNotify() {
        val uuid = pendingNotifyUuids.removeFirstOrNull() ?: run {
            _state.value = CommissioningState.Ready
            return
        }
        val g    = gatt ?: return
        val svc  = g.getService(CommissioningUuids.SERVICE) ?: return
        val chr  = svc.getCharacteristic(uuid) ?: run { enableNextNotify(); return }
        g.setCharacteristicNotification(chr, true)
        val desc = chr.getDescriptor(CommissioningUuids.CCCD) ?: run { enableNextNotify(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(desc)
        }
    }

    // ── Parsing AP struct (little-endian, voir spec firmware) ─────────

    private fun parseAP(raw: ByteArray): WifiAP? {
        if (raw.size < 9) return null
        val ssidLen = raw[0].toInt() and 0xFF
        val channel = raw[1].toInt() and 0xFF
        val rssi    = ByteBuffer.wrap(raw, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val secVal  = ByteBuffer.wrap(raw, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val ssidEnd = minOf(8 + ssidLen, raw.size)
        val ssid    = if (ssidEnd > 8) String(raw, 8, ssidEnd - 8, Charsets.UTF_8).trimEnd(' ')
                      else ""
        if (ssid.isBlank()) return null
        return WifiAP(ssid = ssid, rssi = rssi, channel = channel, security = WifiSecurity.from(secVal))
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        when (uuid) {
            CommissioningUuids.AP_LIST -> parseAP(value)?.let { _apEvent.tryEmit(it) }
            CommissioningUuids.MONITORING -> handleMonitoring(value)
        }
    }

    private fun handleMonitoring(value: ByteArray) {
        if (value.isEmpty()) return
        when (value[0]) {
            0x04.toByte() -> _state.value = CommissioningState.Done   // idempotent
            0x05.toByte() -> Unit  // ping — ignorer
            0x06.toByte() -> {
                val isTimeout = value.size > 1 && value[1] == 0x01.toByte()
                _state.value = CommissioningState.Error(
                    if (isTimeout) "Timeout — réseau introuvable ou mot de passe incorrect"
                    else "Erreur de connexion Wi-Fi"
                )
            }
        }
    }

    // ── GATT callback ─────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = CommissioningState.Error("Erreur GATT $status")
                gatt.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = CommissioningState.DiscoveringServices
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_state.value !is CommissioningState.Done &&
                        _state.value !is CommissioningState.Idle)
                        _state.value = CommissioningState.Idle
                    gatt.close()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS ||
                gatt.getService(CommissioningUuids.SERVICE) == null) {
                _state.value = CommissioningState.Error("Service commissioning introuvable sur cet appareil")
                return
            }
            // Activer les notifications en séquence : AP_LIST puis MONITORING
            pendingNotifyUuids.clear()
            pendingNotifyUuids.addAll(listOf(CommissioningUuids.AP_LIST, CommissioningUuids.MONITORING))
            enableNextNotify()
        }

        // Chaque writeDescriptor termine → on passe au suivant
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) = enableNextNotify()

        // ACK d'un writeCharacteristic → drainer la queue
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int
        ) {
            synchronized(lock) {
                writeBusy = false
                drainQueue()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray
        ) = handleNotification(char.uuid, value)

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, char: BluetoothGattCharacteristic
        ) = handleNotification(char.uuid, char.value)
    }
}
