package fr.isen.veith.sap.domain.model

import androidx.annotation.StringRes
import fr.isen.veith.sap.R

data class Achievement(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long?   = null
)

object Achievements {
    val all = listOf(
        Achievement(
            id             = "first_pot",
            titleRes       = R.string.achievement_first_pot_title,
            descriptionRes = R.string.achievement_first_pot_desc,
            emoji          = "🪴",
            isUnlocked     = true,
            unlockedAt     = System.currentTimeMillis() - 86400000L * 10
        ),
        Achievement(
            id             = "first_plant",
            titleRes       = R.string.achievement_botanist_title,
            descriptionRes = R.string.achievement_botanist_desc,
            emoji          = "🌿",
            isUnlocked     = true,
            unlockedAt     = System.currentTimeMillis() - 86400000L * 8
        ),
        Achievement(
            id             = "watering",
            titleRes       = R.string.achievement_watering_title,
            descriptionRes = R.string.achievement_watering_desc,
            emoji          = "💧",
            isUnlocked     = true,
            unlockedAt     = System.currentTimeMillis() - 86400000L * 2
        ),
        Achievement(
            id             = "sunshine",
            titleRes       = R.string.achievement_sunshine_title,
            descriptionRes = R.string.achievement_sunshine_desc,
            emoji          = "☀️",
            isUnlocked     = false
        ),
        Achievement(
            id             = "collection",
            titleRes       = R.string.achievement_collection_title,
            descriptionRes = R.string.achievement_collection_desc,
            emoji          = "🌸",
            isUnlocked     = false
        ),
        Achievement(
            id             = "green_thumb",
            titleRes       = R.string.achievement_green_thumb_title,
            descriptionRes = R.string.achievement_green_thumb_desc,
            emoji          = "🏆",
            isUnlocked     = false
        ),
        Achievement(
            id             = "temperature",
            titleRes       = R.string.achievement_temperature_title,
            descriptionRes = R.string.achievement_temperature_desc,
            emoji          = "🌡️",
            isUnlocked     = false
        ),
        Achievement(
            id             = "multi_pot",
            titleRes       = R.string.achievement_multi_pot_title,
            descriptionRes = R.string.achievement_multi_pot_desc,
            emoji          = "🏡",
            isUnlocked     = false
        )
    )

    val unlocked get() = all.filter { it.isUnlocked }
    val locked    get() = all.filter { !it.isUnlocked }

    fun getById(id: String): Achievement? = all.find { it.id == id }
}
