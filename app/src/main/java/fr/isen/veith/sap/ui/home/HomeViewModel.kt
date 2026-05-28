package fr.isen.veith.sap.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.influxdb.InfluxRepository
import fr.isen.veith.sap.data.preferences.AppPreferencesRepository
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.domain.model.SampleData
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
    val userName: String                    = "Jardinier",
    val plantsPerPot: Map<String, Plant>    = emptyMap(),
    val sensorData: SensorData              = SampleData.sensorData,
    val mood: PlantMood                     = PlantMood.HAPPY,
    val healthScore: Float                  = 0f,
    val availablePotIds: List<String>       = emptyList(),
    val selectedPotId: String               = "",
    val isPotMenuOpen: Boolean              = false,
    val isInfluxLoading: Boolean            = false,
    val influxError: String?                = null
) {
    val activePlant: Plant? get() = plantsPerPot[selectedPotId]
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val influx = InfluxRepository()
    private val prefs  = AppPreferencesRepository(app)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Sync plants map + username from DataStore
        viewModelScope.launch {
            prefs.preferences.collect { p ->
                _uiState.update { state ->
                    val plant = p.savedPlants[state.selectedPotId]
                    state.copy(
                        userName     = p.username.ifBlank { "Jardinier" },
                        plantsPerPot = p.savedPlants,
                        mood         = if (plant != null) PlantMood.from(state.sensorData, plant)
                                       else PlantMood.HAPPY,
                        healthScore  = if (plant != null) computeHealth(state.sensorData, plant)
                                       else 0f
                    )
                }
            }
        }

        // Load available pots from InfluxDB, then start refresh loop
        viewModelScope.launch {
            try {
                val ids = influx.fetchPlantIds()
                if (ids.isNotEmpty()) {
                    _uiState.update { it.copy(availablePotIds = ids, selectedPotId = ids.first()) }
                    startRefreshing()
                }
            } catch (_: Exception) {
                startSimulation()
            }
        }
    }

    fun selectPotId(id: String) {
        _uiState.update { state ->
            val plant = state.plantsPerPot[id]
            state.copy(
                selectedPotId = id,
                isPotMenuOpen = false,
                mood          = if (plant != null) PlantMood.from(state.sensorData, plant) else PlantMood.HAPPY,
                healthScore   = if (plant != null) computeHealth(state.sensorData, plant) else 0f
            )
        }
        startRefreshing()
    }

    fun togglePotMenu()  = _uiState.update { it.copy(isPotMenuOpen = !it.isPotMenuOpen) }
    fun closePotMenu()   = _uiState.update { it.copy(isPotMenuOpen = false) }

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
                val plant = state.activePlant
                state.copy(
                    sensorData      = data,
                    mood            = if (plant != null) PlantMood.from(data, plant) else PlantMood.HAPPY,
                    healthScore     = if (plant != null) computeHealth(data, plant) else 0f,
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
                    val plant = state.activePlant
                    state.copy(
                        sensorData  = d,
                        mood        = if (plant != null) PlantMood.from(d, plant) else PlantMood.HAPPY,
                        healthScore = if (plant != null) computeHealth(d, plant) else 0f
                    )
                }
            }
        }
    }

    private fun computeHealth(data: SensorData, plant: Plant): Float {
        var score = 100f
        val humidMid = (plant.humidityMin + plant.humidityMax) / 2f
        score -= (abs(data.humidity - humidMid) / ((plant.humidityMax - plant.humidityMin) / 2f).coerceAtLeast(1f) * 30f).coerceAtMost(30f)
        val luxMid = (plant.luxMin + plant.luxMax) / 2f
        score -= (abs(data.luminosity - luxMid) / ((plant.luxMax - plant.luxMin) / 2f).coerceAtLeast(1f) * 30f).coerceAtMost(30f)
        val tempMid = (plant.tempMin + plant.tempMax) / 2f
        score -= (abs(data.temperature - tempMid) / ((plant.tempMax - plant.tempMin) / 2f).coerceAtLeast(1f) * 20f).coerceAtMost(20f)
        return score.coerceIn(0f, 100f)
    }

    private fun ClosedRange<Int>.randomInt()   = (start + Math.random() * (endInclusive - start)).toInt().toFloat()
    private fun ClosedRange<Float>.randomFloat() = start + (Math.random() * (endInclusive - start)).toFloat()
}
