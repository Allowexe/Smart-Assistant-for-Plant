package fr.isen.veith.sap.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.ble.BleDevice
import fr.isen.veith.sap.data.ble.BleRepository
import fr.isen.veith.sap.data.ble.CommissioningState
import fr.isen.veith.sap.data.ble.ScanState
import fr.isen.veith.sap.data.ble.WifiAP
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanPhase {
    SCANNING,     // BLE device list
    CONNECTING,   // GATT connecting
    WIFI_TRIGGER, // ready — waiting for scan or scanning in progress
    WIFI_LIST,    // AP list ready
    CONFIGURE,    // password entry
    SENDING,      // writing credentials
    STATUS,       // monitoring connection
    DONE,
    ERROR
}

data class ScanUiState(
    val phase: ScanPhase           = ScanPhase.SCANNING,
    val selectedDevice: BleDevice? = null,
    val isWifiScanning: Boolean    = false,
    val wifiAPs: List<WifiAP>      = emptyList(),
    val selectedAP: WifiAP?        = null,
    val password: String           = "",
    val passwordVisible: Boolean   = false,
    val statusMessage: String      = "",
    val errorMessage: String       = ""
)

class BleViewModel(app: Application) : AndroidViewModel(app) {

    val repo = BleRepository(app)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    val devices: StateFlow<List<BleDevice>> = repo.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanState: StateFlow<ScanState> = repo.scanState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanState.Idle)

    private var apDebounceJob: Job? = null
    private var wifiTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repo.commissioningState.collect { state ->
                when (state) {
                    is CommissioningState.Connecting,
                    is CommissioningState.DiscoveringServices ->
                        _uiState.update { it.copy(phase = ScanPhase.CONNECTING, isWifiScanning = false) }

                    is CommissioningState.Ready ->
                        _uiState.update { it.copy(phase = ScanPhase.WIFI_TRIGGER, isWifiScanning = false) }

                    is CommissioningState.ScanningWifi ->
                        _uiState.update { it.copy(phase = ScanPhase.WIFI_TRIGGER, isWifiScanning = true, wifiAPs = emptyList()) }

                    is CommissioningState.SendingCredentials ->
                        _uiState.update { it.copy(phase = ScanPhase.SENDING) }

                    is CommissioningState.Monitoring -> {
                        _uiState.update { it.copy(phase = ScanPhase.STATUS, statusMessage = state.statusText) }
                        wifiTimeoutJob?.cancel()
                        wifiTimeoutJob = viewModelScope.launch {
                            delay(30_000)
                            if (_uiState.value.phase == ScanPhase.STATUS) {
                                _uiState.update {
                                    it.copy(phase = ScanPhase.ERROR, errorMessage = "Timeout — aucune réponse du pot (30 s)")
                                }
                            }
                        }
                    }

                    is CommissioningState.Done -> {
                        wifiTimeoutJob?.cancel()
                        _uiState.update { it.copy(phase = ScanPhase.DONE) }
                    }

                    is CommissioningState.Error -> {
                        wifiTimeoutJob?.cancel()
                        _uiState.update { it.copy(phase = ScanPhase.ERROR, errorMessage = state.message) }
                    }

                    else -> Unit
                }
            }
        }

        // Accumulate APs; switch to WIFI_LIST 2 s after the last notification
        viewModelScope.launch {
            repo.apEvent.collect { ap ->
                _uiState.update { s ->
                    s.copy(wifiAPs = (s.wifiAPs + ap).distinctBy { it.ssid })
                }
                apDebounceJob?.cancel()
                apDebounceJob = viewModelScope.launch {
                    delay(2000)
                    _uiState.update { it.copy(phase = ScanPhase.WIFI_LIST, isWifiScanning = false) }
                }
            }
        }
    }

    fun startScan() {
        if (!repo.isBluetoothEnabled) {
            _uiState.update { it.copy(phase = ScanPhase.ERROR, errorMessage = "Bluetooth désactivé") }
            return
        }
        _uiState.update { it.copy(phase = ScanPhase.SCANNING) }
        repo.startScan()
    }

    fun stopScan() = repo.stopScan()

    fun connectDevice(device: BleDevice) {
        stopScan()
        _uiState.update { it.copy(selectedDevice = device, phase = ScanPhase.CONNECTING) }
        repo.connectForCommissioning(device)
    }

    fun triggerWifiScan() = repo.triggerWifiScan()

    fun selectAP(ap: WifiAP) {
        _uiState.update { it.copy(selectedAP = ap, phase = ScanPhase.CONFIGURE) }
    }

    fun updatePassword(v: String) = _uiState.update { it.copy(password = v) }
    fun togglePasswordVisible()   = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun sendCredentials() {
        val s = _uiState.value
        repo.sendCredentials(s.selectedAP?.ssid ?: "", s.password)
    }

    fun disconnect() = repo.disconnect()

    fun reset() {
        apDebounceJob?.cancel()
        wifiTimeoutJob?.cancel()
        disconnect()
        _uiState.value = ScanUiState()
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopScan()
    }
}
