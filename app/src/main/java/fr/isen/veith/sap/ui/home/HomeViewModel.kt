package fr.isen.veith.sap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.domain.model.SampleData
import fr.isen.veith.sap.data.ble.BleManager
import fr.isen.veith.sap.data.ble.ConnectionState
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class HomeUiState(
    val userName: String          = "Jardinier",
    val plants: List<Plant>       = SampleData.plants,
    val selectedPlant: Plant      = SampleData.plants.first(),
    val sensorData: SensorData    = SampleData.sensorData,
    val mood: PlantMood           = PlantMood.HAPPY,
    val isPlantMenuOpen: Boolean  = false,
    val isLoadingSensor: Boolean  = false,
    val isBleConnected: Boolean   = false,
    // Santé globale 0–100
    val healthScore: Float        = 78f
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
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
        viewModelScope.launch {
            BleManager.connectionState.collect { connState ->
                _uiState.update { it.copy(isBleConnected = connState is ConnectionState.Connected) }
            }
        }
        // Simulation en l'absence de connexion BLE réelle (démo)
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                if (!_uiState.value.isBleConnected) refreshSensors()
            }
        }
    }

    fun selectPlant(plant: Plant) {
        _uiState.update {
            val mood = PlantMood.from(it.sensorData, plant)
            it.copy(
                selectedPlant  = plant,
                isPlantMenuOpen = false,
                mood           = mood,
                healthScore    = computeHealth(it.sensorData, plant)
            )
        }
    }

    fun togglePlantMenu() {
        _uiState.update { it.copy(isPlantMenuOpen = !it.isPlantMenuOpen) }
    }

    fun closePlantMenu() {
        _uiState.update { it.copy(isPlantMenuOpen = false) }
    }

    fun setUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
    }

    private fun refreshSensors() {
        // TODO: remplacer par lecture BLE réelle
        _uiState.update { state ->
            // Légère variation aléatoire pour la démo
            val newData = state.sensorData.copy(
                humidity    = (state.sensorData.humidity + (-2..2).randomInt()).coerceIn(0f, 100f),
                luminosity  = (state.sensorData.luminosity + (-200..200).randomInt()).coerceAtLeast(0f),
                temperature = state.sensorData.temperature + (-0.5f..0.5f).randomFloat()
            )
            state.copy(
                sensorData  = newData,
                mood        = PlantMood.from(newData, state.selectedPlant),
                healthScore = computeHealth(newData, state.selectedPlant)
            )
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