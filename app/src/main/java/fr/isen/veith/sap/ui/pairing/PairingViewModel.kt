package fr.isen.veith.sap.ui.pairing

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.R
import fr.isen.veith.sap.data.ble.BleDevice
import fr.isen.veith.sap.data.ble.BleManager
import fr.isen.veith.sap.data.ble.ConnectionState
import fr.isen.veith.sap.data.ble.ScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PairingUiState(
    val devices: List<BleDevice>         = emptyList(),
    val scanState: ScanState             = ScanState.Idle,
    val selectedDevice: BleDevice?       = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val missingPermissions: List<String> = emptyList(),
    val showBluetoothOffBanner: Boolean  = false,
    val errorMessage: String?            = null
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            BleManager.devices.collect { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
        }
        viewModelScope.launch {
            BleManager.scanState.collect { state ->
                _uiState.update { it.copy(scanState = state) }
            }
        }
        viewModelScope.launch {
            BleManager.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }
        viewModelScope.launch {
            BleManager.lastError.collect { error ->
                if (error != null) _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    fun checkAndStartScan() {
        val missing = requiredPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(getApplication(), perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) { _uiState.update { it.copy(missingPermissions = missing) }; return }
        if (!BleManager.isBluetoothEnabled) { _uiState.update { it.copy(showBluetoothOffBanner = true) }; return }
        startScan()
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(missingPermissions = emptyList()) }
        if (granted) startScan()
        else _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.ble_permissions_denied)) }
    }

    fun dismissBluetoothBanner() = _uiState.update { it.copy(showBluetoothOffBanner = false) }
    fun dismissError()           = _uiState.update { it.copy(errorMessage = null) }
    fun selectDevice(device: BleDevice) = _uiState.update { it.copy(selectedDevice = device) }

    fun connectDevice(device: BleDevice) {
        selectDevice(device)
        BleManager.stopScan()
        BleManager.connect(device)
    }
    fun disconnect()             = BleManager.disconnect()

    private fun startScan() {
        BleManager.startScan()
        viewModelScope.launch {
            delay(15_000)
            if (_uiState.value.scanState is ScanState.Scanning) BleManager.stopScan()
        }
    }

    fun stopScan() = BleManager.stopScan()

    fun pairSelectedDevice() {
        val device = _uiState.value.selectedDevice ?: return
        BleManager.stopScan()
        BleManager.connect(device)
    }

    override fun onCleared() {
        super.onCleared()
        try { BleManager.stopScan() } catch (_: Exception) {}
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}