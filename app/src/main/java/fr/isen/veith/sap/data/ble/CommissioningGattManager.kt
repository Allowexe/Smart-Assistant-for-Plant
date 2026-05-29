package fr.isen.veith.sap.data.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        // Fermer l'ancienne instance GATT avant d'en créer une nouvelle
        // (évite les instances orphelines qui interceptent les callbacks BLE)
        gatt?.close()
        gatt = null
        synchronized(lock) { writeQueue.clear(); writeBusy = false }
        _state.value = CommissioningState.Connecting
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val btDevice = adapter.getRemoteDevice(device.address)
        val mainHandler = Handler(Looper.getMainLooper())
        // Workaround Samsung BLE bug : sans Handler(mainLooper), onCharacteristicChanged
        // n'est jamais appelé sur les Galaxy (callbacks sur Binder thread ignorés)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btDevice.connectGatt(context, false, gattCallback,
                BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, mainHandler)
        } else {
            btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
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
        val notifOk = g.setCharacteristicNotification(chr, true)
        val props = chr.properties
        val isIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        val cccdVal = if (isIndicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                      else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        Log.d("SAP_BLE", "setCharacteristicNotification $uuid → $notifOk props=0x${props.toString(16)} cccd=${if (isIndicate) "INDICATE" else "NOTIFY"}")
        val desc = chr.getDescriptor(CommissioningUuids.CCCD) ?: run { enableNextNotify(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(desc, cccdVal)
        } else {
            @Suppress("DEPRECATION")
            desc.value = cccdVal
            @Suppress("DEPRECATION")
            g.writeDescriptor(desc)
        }
    }

    // ── Parsing AP struct (little-endian, voir spec firmware) ─────────

    // Format réel ST67W6X (44 bytes par AP) :
    // [0x20:1B][channel:1B][rssi:2B LE int16][security:4B LE uint32][SSID:32B null-padded][extra:4B]
    private fun parseAP(raw: ByteArray): WifiAP? {
        if (raw.size < 9) return null
        val channel = raw[1].toInt() and 0xFF
        val rssi    = ByteBuffer.wrap(raw, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val secVal  = ByteBuffer.wrap(raw, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val ssidEnd = minOf(40, raw.size)
        if (ssidEnd <= 8) return null
        val ssidBytes = raw.sliceArray(8 until ssidEnd)
        val nullIdx = ssidBytes.indexOfFirst { it == 0.toByte() }
        val ssidLen = if (nullIdx >= 0) nullIdx else ssidBytes.size
        if (ssidLen == 0) return null
        val ssid = String(ssidBytes, 0, ssidLen, Charsets.UTF_8).trim()
        if (ssid.isBlank()) return null
        return WifiAP(ssid = ssid, rssi = rssi, channel = channel, security = WifiSecurity.from(secVal))
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        Log.d("SAP_BLE", "Notif uuid=$uuid size=${value.size} bytes=${value.joinToString(" ") { "%02X".format(it) }}")
        when (uuid) {
            CommissioningUuids.AP_LIST -> {
                val ap = parseAP(value)
                if (ap != null) {
                    Log.d("SAP_BLE", "AP: ssid=${ap.ssid} rssi=${ap.rssi} ch=${ap.channel} sec=${ap.security}")
                    _apEvent.tryEmit(ap)
                } else {
                    Log.w("SAP_BLE", "AP parse FAILED: ${value.size}B ${value.joinToString(" ") { "%02X".format(it) }}")
                }
            }
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
            Log.d("SAP_BLE", "onConnectionStateChange status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = CommissioningState.Error("Erreur GATT $status")
                gatt.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = CommissioningState.DiscoveringServices
                    try {
                        val refresh = gatt.javaClass.getMethod("refresh")
                        refresh.invoke(gatt)
                        Log.d("SAP_BLE", "GATT cache refreshed")
                    } catch (e: Exception) {
                        Log.w("SAP_BLE", "gatt.refresh() indisponible: ${e.message}")
                    }
                    // Négocier le MTU avant la découverte — certains stacks BLE ignorent
                    // les notifications si le MTU par défaut (23 bytes) n'est pas négocié
                    gatt.requestMtu(512)
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
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("SAP_BLE", "onMtuChanged mtu=$mtu status=$status")
            gatt.discoverServices()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("SAP_BLE", "onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS ||
                gatt.getService(CommissioningUuids.SERVICE) == null) {
                val uuids = gatt.services.map { it.uuid }
                Log.e("SAP_BLE", "Service introuvable. Services dispo: $uuids")
                _state.value = CommissioningState.Error("Service commissioning introuvable sur cet appareil")
                return
            }
            Log.d("SAP_BLE", "Service trouvé. Activation notifications AP_LIST + MONITORING")
            pendingNotifyUuids.clear()
            pendingNotifyUuids.addAll(listOf(CommissioningUuids.AP_LIST, CommissioningUuids.MONITORING))
            enableNextNotify()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            Log.d("SAP_BLE", "onDescriptorWrite status=$status char=${descriptor.characteristic.uuid}")
            enableNextNotify()
        }

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
        ) {
            Log.d("SAP_BLE", "onCharacteristicChanged (API33+) uuid=${char.uuid}")
            handleNotification(char.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, char: BluetoothGattCharacteristic
        ) {
            Log.d("SAP_BLE", "onCharacteristicChanged (deprecated) uuid=${char.uuid}")
            handleNotification(char.uuid, char.value)
        }
    }
}
