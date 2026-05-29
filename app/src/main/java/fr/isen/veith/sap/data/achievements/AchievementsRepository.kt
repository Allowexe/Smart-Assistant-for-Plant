package fr.isen.veith.sap.data.achievements

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.isen.veith.sap.data.preferences.dataStore
import fr.isen.veith.sap.domain.model.Achievement
import fr.isen.veith.sap.domain.model.Achievements
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists achievement unlock state + the progress needed to compute it.
 *
 * Two kinds of conditions:
 *  - Count-based (first_pot, first_plant, collection, multi_pot): derived from
 *    the current saved plants / paired pots, evaluated on change.
 *  - Streak-based (watering, sunshine, temperature, green_thumb): a calendar day
 *    counts as "qualified" as soon as one in-range sample lands that day; the
 *    streak is the number of consecutive qualified days. A skipped day resets it.
 *    No historical data exists, so these only progress going forward.
 */
class AchievementsRepository(private val context: Context) {

    // ── Streak targets (consecutive days) ──────────────────────────────
    private object Targets {
        const val WATERING    = 7
        const val SUNSHINE    = 5
        const val TEMPERATURE = 14
        const val GREEN_THUMB  = 30
    }

    private fun unlockedKey(id: String) = longPreferencesKey("ach_${id}_unlocked_at")
    private fun streakKey(id: String)   = intPreferencesKey("ach_${id}_streak")
    private fun lastDayKey(id: String)  = longPreferencesKey("ach_${id}_last_day")
    private val pairedPotsKey           = stringPreferencesKey("ach_paired_pots")

    private fun today(): Long = System.currentTimeMillis() / 86_400_000L

    /** Achievements with persisted unlock state layered onto the static catalog. */
    val achievements: Flow<List<Achievement>> = context.dataStore.data.map { prefs ->
        Achievements.all.map { a ->
            val at = prefs[unlockedKey(a.id)]
            if (at != null) a.copy(isUnlocked = true, unlockedAt = at) else a
        }
    }

    /** Mark an achievement unlocked once; first unlock timestamp wins. */
    private suspend fun unlock(id: String) {
        context.dataStore.edit { prefs ->
            val key = unlockedKey(id)
            if (prefs[key] == null) prefs[key] = System.currentTimeMillis()
        }
    }

    // ── Count-based ─────────────────────────────────────────────────────

    /** Call when the set of pots sending data (or commissioned pots) changes. */
    suspend fun recordPairedPots(potIds: Collection<String>) {
        if (potIds.isEmpty()) return
        var count = 0
        context.dataStore.edit { prefs ->
            val known = prefs[pairedPotsKey]
                ?.split(",")?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            known.addAll(potIds.filter { it.isNotBlank() })
            prefs[pairedPotsKey] = known.joinToString(",")
            count = known.size
        }
        if (count >= 1) unlock("first_pot")
        if (count >= 3) unlock("multi_pot")
    }

    /** Call when saved plants change. */
    suspend fun recordPlants(plants: Collection<Plant>) {
        if (plants.isNotEmpty()) unlock("first_plant")
        val distinctSpecies = plants
            .map { it.scientificName.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        if (distinctSpecies.size >= 5) unlock("collection")
    }

    // ── Streak-based ──────────────────────────────────────────────────

    /** Feed one live sample for the active plant; updates all streak achievements. */
    suspend fun recordHealthSample(data: SensorData, plant: Plant, healthScore: Float) {
        updateStreak("watering",    Targets.WATERING)    { data.humidity    in plant.humidityMin..plant.humidityMax }
        updateStreak("sunshine",    Targets.SUNSHINE)    { data.luminosity  in plant.luxMin..plant.luxMax }
        updateStreak("temperature", Targets.TEMPERATURE) { data.temperature in plant.tempMin..plant.tempMax }
        updateStreak("green_thumb", Targets.GREEN_THUMB)  { healthScore > 90f }
    }

    private suspend inline fun updateStreak(id: String, target: Int, qualifies: () -> Boolean) {
        if (!qualifies()) return
        val d = today()
        var reachedTarget = false
        context.dataStore.edit { prefs ->
            val last   = prefs[lastDayKey(id)] ?: -1L
            val streak = prefs[streakKey(id)]  ?: 0
            val newStreak = when (d) {
                last      -> streak              // already counted today
                last + 1  -> streak + 1          // consecutive day
                else      -> 1                   // first day or gap → restart
            }
            prefs[lastDayKey(id)] = d
            prefs[streakKey(id)]  = newStreak
            if (newStreak >= target) reachedTarget = true
        }
        if (reachedTarget) unlock(id)
    }
}
