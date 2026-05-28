package fr.isen.veith.sap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.influxdb.InfluxRepository
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.domain.model.SampleData
import fr.isen.veith.sap.data.ble.BleManager
import fr.isen.veith.sap.data.ble.ConnectionState
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class HomeUiState(
    val userName: String              = "Jardinier",
    val plants: List<Plant>           = SampleData.plants,
    val selectedPlant: Plant          = SampleData.plants.first(),
    val sensorData: SensorData        = SampleData.sensorData,
    val mood: PlantMood               = PlantMood.HAPPY,
    val isPlantMenuOpen: Boolean      = false,
    val healthScore: Float            = 78f,
    // Pot selector (InfluxDB)
    val availablePotIds: List<String> = emptyList(),
    val selectedPotId: String         = "",
    val isPotMenuOpen: Boolean        = false,
    val isInfluxLoading: Boolean      = false,
    val influxError: String?          = null
)

class HomeViewModel : ViewModel() {

    private val influx = InfluxRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Load available pots from InfluxDB, then start refresh loop
        viewModelScope.launch {
            try {
                val ids = influx.fetchPlantIds()
                if (ids.isNotEmpty()) {
                    _uiState.update { it.copy(availablePotIds = ids, selectedPotId = ids.first()) }
                    startRefreshing()
                }
            } catch (_: Exception) {
                // No InfluxDB connection — fall back to simulated data
                startSimulation()
            }
        }

        // Mirror live BLE sensor data if connected
        viewModelScope.launch {
            BleManager.sensorData.collect { bleData ->
                if (bleData != null) {
                    _uiState.update { state ->
                        state.copy(
                            sensorData  = bleData,
                            mood        = PlantMood.from(bleData, state.selectedPlant),
                            healthScore = computeHealth(bleData, state.selectedPlant)
                        )
                    }
                }
            }
        }
    }

    fun selectPotId(id: String) {
        _uiState.update { it.copy(selectedPotId = id, isPotMenuOpen = false) }
        startRefreshing()
    }

    fun togglePotMenu()  = _uiState.update { it.copy(isPotMenuOpen = !it.isPotMenuOpen) }
    fun closePotMenu()   = _uiState.update { it.copy(isPotMenuOpen = false) }

    fun selectPlant(plant: Plant) {
        _uiState.update {
            it.copy(
                selectedPlant   = plant,
                isPlantMenuOpen = false,
                mood            = PlantMood.from(it.sensorData, plant),
                healthScore     = computeHealth(it.sensorData, plant)
            )
        }
    }

    fun togglePlantMenu() = _uiState.update { it.copy(isPlantMenuOpen = !it.isPlantMenuOpen) }
    fun closePlantMenu()  = _uiState.update { it.copy(isPlantMenuOpen = false) }
    fun setUserName(name: String) = _uiState.update { it.copy(userName = name) }

    private fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetchFromInflux()
                delay(10_000)
            }
        }
    }

    private suspend fun fetchFromInflux() {
        val potId = _uiState.value.selectedPotId
        if (potId.isBlank()) return
        _uiState.update { it.copy(isInfluxLoading = true) }
        try {
            val env      = influx.fetchEnvironment(potId)
            val spectrum = influx.fetchSpectrum(potId)
            val data = SensorData(
                humidity    = (env.soil1 + env.soil2) / 2f,
                temperature = env.tempShtc3,
                luminosity  = spectrum.luminosity
            )
            _uiState.update { state ->
                state.copy(
                    sensorData      = data,
                    mood            = PlantMood.from(data, state.selectedPlant),
                    healthScore     = computeHealth(data, state.selectedPlant),
                    isInfluxLoading = false,
                    influxError     = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isInfluxLoading = false, influxError = e.message) }
        }
    }

    private fun startSimulation() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                _uiState.update { state ->
                    val d = state.sensorData.copy(
                        humidity    = (state.sensorData.humidity + (-2..2).randomInt()).coerceIn(0f, 100f),
                        luminosity  = (state.sensorData.luminosity + (-200..200).randomInt()).coerceAtLeast(0f),
                        temperature = state.sensorData.temperature + (-0.5f..0.5f).randomFloat()
                    )
                    state.copy(
                        sensorData  = d,
                        mood        = PlantMood.from(d, state.selectedPlant),
                        healthScore = computeHealth(d, state.selectedPlant)
                    )
                }
            }
        }
    }

    private fun computeHealth(data: SensorData, plant: Plant): Float {
        var score = 100f
        val humidMid = (plant.humidityMin + plant.humidityMax) / 2f
        score -= (abs(data.humidity - humidMid) / humidMid * 30f).coerceAtMost(30f)
        if (data.luminosity < plant.luxMin)
            score -= ((plant.luxMin - data.luminosity) / plant.luxMin * 30f).coerceAtMost(30f)
        val tempMid = (plant.tempMin + plant.tempMax) / 2f
        score -= (abs(data.temperature - tempMid) / tempMid * 20f).coerceAtMost(20f)
        return score.coerceIn(0f, 100f)
    }

    private fun ClosedRange<Int>.randomInt() = (start + Math.random() * (endInclusive - start)).toInt().toFloat()
    private fun ClosedRange<Float>.randomFloat() = start + (Math.random() * (endInclusive - start)).toFloat()
}
