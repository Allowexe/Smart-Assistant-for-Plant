package fr.isen.veith.sap.domain.model

// ── Données capteurs du pot ───────────────────────────────────────────
data class SensorData(
    val humidity: Float,
    val luminosity: Float,
    val temperature: Float,
    val timestamp: Long = System.currentTimeMillis()
)

// ── État de bien-être de la plante (détermine le visage affiché) ──────
enum class PlantMood {
    HAPPY,
    NEUTRAL,
    CONCERNED,
    SAD;

    companion object {
        fun from(data: SensorData, plant: Plant): PlantMood {
            var issues = 0
            if (data.humidity < plant.humidityMin || data.humidity > plant.humidityMax) issues++
            if (data.luminosity < plant.luxMin)     issues++
            if (data.temperature < plant.tempMin || data.temperature > plant.tempMax) issues++
            return when (issues) {
                0    -> HAPPY
                1    -> NEUTRAL
                2    -> CONCERNED
                else -> SAD
            }
        }
    }
}

// ── Plante identifiée ─────────────────────────────────────────────────
data class Plant(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val emoji: String           = "🌿",
    val humidityMin: Float      = 40f,
    val humidityMax: Float      = 70f,
    val luxMin: Float           = 2000f,
    val luxMax: Float           = 50000f,
    val tempMin: Float          = 15f,
    val tempMax: Float          = 28f,
    val wateringTip: String     = "Arrosez quand le sol est sec en surface.",
    val lightTip: String        = "Lumière indirecte vive."
)

object SampleData {
    val sensorData = SensorData(
        humidity    = 68f,
        luminosity  = 4200f,
        temperature = 21f
    )
}