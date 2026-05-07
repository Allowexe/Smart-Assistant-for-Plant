package fr.isen.veith.sap.domain.model

// ── Modèle d'un succès ────────────────────────────────────────────────
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long?   = null
)

// ── Tous les succès de l'application ─────────────────────────────────
object Achievements {
    val all = listOf(
        Achievement(
            id = "first_pot",
            title = "Premier pot",
            description = "Appairer son premier pot connecté",
            emoji = "🪴",
            isUnlocked = true,
            unlockedAt = System.currentTimeMillis() - 86400000L * 10
        ),
        Achievement(
            id = "first_plant",
            title = "Botaniste",
            description = "Identifier sa première plante",
            emoji = "🌿",
            isUnlocked = true,
            unlockedAt = System.currentTimeMillis() - 86400000L * 8
        ),
        Achievement(
            id = "watering",
            title = "Arroseur fidèle",
            description = "Arroser sa plante 7 jours de suite",
            emoji = "💧",
            isUnlocked = true,
            unlockedAt = System.currentTimeMillis() - 86400000L * 2
        ),
        Achievement(
            id = "sunshine",
            title = "Chasseur de lumière",
            description = "Maintenir un ensoleillement optimal 5 jours",
            emoji = "☀️",
            isUnlocked = false
        ),
        Achievement(
            id = "collection",
            title = "Collectionneur",
            description = "Identifier 5 plantes différentes",
            emoji = "🌸",
            isUnlocked = false
        ),
        Achievement(
            id = "green_thumb",
            title = "Pouce vert",
            description = "Maintenir une santé > 90% pendant 30 jours",
            emoji = "🏆",
            isUnlocked = false
        ),
        Achievement(
            id = "temperature",
            title = "Thermomètre",
            description = "Garder la température idéale 14 jours",
            emoji = "🌡️",
            isUnlocked = false
        ),
        Achievement(
            id = "multi_pot",
            title = "Jardin connecté",
            description = "Appairer 3 pots ou plus",
            emoji = "🏡",
            isUnlocked = false
        )
    )

    val unlocked get() = all.filter { it.isUnlocked }
    val locked    get() = all.filter { !it.isUnlocked }
}