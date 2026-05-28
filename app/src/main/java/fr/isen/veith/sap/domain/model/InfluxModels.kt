package fr.isen.veith.sap.domain.model

data class EnvironmentData(
    val soil1: Float = 0f,
    val soil2: Float = 0f,
    val tempShtc3: Float = 0f,
    val tempBmp180: Float = 0f
)

data class LightSpectrum(
    val violet: Float = 0f,
    val indigo: Float = 0f,
    val blue: Float = 0f,
    val cyan: Float = 0f,
    val green: Float = 0f,
    val yellow: Float = 0f,
    val orange: Float = 0f,
    val red: Float = 0f,
    val luminosity: Float = 0f
)

data class HistoryPoint(
    val timeMs: Long,
    val soil1: Float,
    val soil2: Float,
    val tempShtc3: Float,
    val tempBmp180: Float
)