package fr.isen.veith.sap.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.influxdb.InfluxRepository
import fr.isen.veith.sap.domain.model.EnvironmentData
import fr.isen.veith.sap.domain.model.HistoryPoint
import fr.isen.veith.sap.domain.model.LightSpectrum
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val plantIds: List<String> = emptyList(),
    val selectedPlantId: String = "",
    val environment: EnvironmentData = EnvironmentData(),
    val spectrum: LightSpectrum = LightSpectrum(),
    val history: List<HistoryPoint> = emptyList(),
    val lastUpdatedMs: Long = 0L
)

class DashboardViewModel : ViewModel() {

    private val repo = InfluxRepository()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadPlantIds()
    }

    private fun loadPlantIds() {
        viewModelScope.launch {
            runCatching { repo.fetchPlantIds() }
                .onSuccess { ids ->
                    val first = ids.firstOrNull() ?: ""
                    _uiState.update { it.copy(plantIds = ids, selectedPlantId = first) }
                    if (first.isNotBlank()) startRefreshing()
                    else _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectPlant(plantId: String) {
        _uiState.update { it.copy(selectedPlantId = plantId) }
        startRefreshing()
    }

    fun forceRefresh() = startRefreshing()

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetchAll()
                delay(10_000L)
            }
        }
    }

    private suspend fun fetchAll() {
        val plantId = _uiState.value.selectedPlantId
        if (plantId.isBlank()) return
        _uiState.update { it.copy(isLoading = it.history.isEmpty()) }
        runCatching {
            val env      = repo.fetchEnvironment(plantId)
            val spectrum = repo.fetchSpectrum(plantId)
            val history  = repo.fetchHistory(plantId)
            _uiState.update {
                it.copy(
                    isLoading     = false,
                    error         = null,
                    environment   = env,
                    spectrum      = spectrum,
                    history       = history,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
