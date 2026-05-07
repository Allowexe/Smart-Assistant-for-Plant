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
    val tempMin: Float          = 15f,
    val tempMax: Float          = 28f,
    val wateringTip: String     = "Arrosez quand le sol est sec en surface.",
    val lightTip: String        = "Lumière indirecte vive."
)

// ── Données de démonstration ──────────────────────────────────────────
object SampleData {
    val plants = listOf(
        Plant(
            id = "monstera",
            commonName = "Monstera",
            scientificName = "Monstera deliciosa",
            emoji = "🌿",
            humidityMin = 40f, humidityMax = 70f,
            luxMin = 1500f, tempMin = 16f, tempMax = 30f,
            wateringTip = "Arrosez tous les 7–10 jours.",
            lightTip = "Lumière indirecte, évitez le soleil direct."
        ),
        Plant(
            id = "cactus",
            commonName = "Cactus",
            scientificName = "Cactaceae",
            emoji = "🌵",
            humidityMin = 10f, humidityMax = 30f,
            luxMin = 5000f, tempMin = 10f, tempMax = 35f,
            wateringTip = "Arrosez très peu, 1 fois par mois en hiver.",
            lightTip = "Plein soleil indispensable."
        ),
        Plant(
            id = "orchidee",
            commonName = "Orchidée",
            scientificName = "Phalaenopsis",
            emoji = "🌸",
            humidityMin = 50f, humidityMax = 80f,
            luxMin = 1000f, tempMin = 18f, tempMax = 27f,
            wateringTip = "Immersion 15 min toutes les 2 semaines.",
            lightTip = "Lumière tamisée, pas de soleil direct."
        ),
        Plant(
            id = "basilic",
            commonName = "Basilic",
            scientificName = "Ocimum basilicum",
            emoji = "🌱",
            humidityMin = 50f, humidityMax = 75f,
            luxMin = 3000f, tempMin = 18f, tempMax = 30f,
            wateringTip = "Sol toujours légèrement humide.",
            lightTip = "6h de soleil par jour minimum."
        )
    )

    val sensorData = SensorData(
        humidity    = 68f,
        luminosity  = 4200f,
        temperature = 21f
    )
}