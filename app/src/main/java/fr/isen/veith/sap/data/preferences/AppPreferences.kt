package fr.isen.veith.sap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.isen.veith.sap.domain.model.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Sap_prefs")

enum class AppTheme(val label: String) {
    LIGHT("Clair"),
    DARK("Sombre"),
    SYSTEM("Appareil")
}

enum class AppLanguage(val label: String, val code: String) {
    FRENCH("Français", "fr"),
    ENGLISH("English",  "en")
}

data class UserPreferences(
    val theme: AppTheme               = AppTheme.SYSTEM,
    val language: AppLanguage         = AppLanguage.FRENCH,
    val notificationsEnabled: Boolean = true,
    val username: String              = "",
    val email: String                 = "",
    val photoUrl: String?             = null,
    // potId → Plant  (one plant per commissioned pot)
    val savedPlants: Map<String, Plant> = emptyMap()
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME      = stringPreferencesKey("theme")
        val LANGUAGE   = stringPreferencesKey("language")
        val NOTIFS     = booleanPreferencesKey("notifications")
        val USERNAME   = stringPreferencesKey("username")
        val EMAIL      = stringPreferencesKey("email")
        val PHOTO_URL  = stringPreferencesKey("photo_url")
        // comma-separated list of potIds that have a saved plant
        val PLANT_POTS = stringPreferencesKey("plant_pots")
        // per-pot plant fields: plant_<potId>_id / _common / _sci / _emoji
        fun plantId     (potId: String) = stringPreferencesKey("plant_${potId}_id")
        fun plantCommon (potId: String) = stringPreferencesKey("plant_${potId}_common")
        fun plantSci    (potId: String) = stringPreferencesKey("plant_${potId}_sci")
        fun plantEmoji  (potId: String) = stringPreferencesKey("plant_${potId}_emoji")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val potIds = prefs[Keys.PLANT_POTS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val plants = potIds.associateWith { potId ->
                Plant(
                    id             = prefs[Keys.plantId(potId)]     ?: potId,
                    commonName     = prefs[Keys.plantCommon(potId)] ?: potId,
                    scientificName = prefs[Keys.plantSci(potId)]    ?: potId,
                    emoji          = prefs[Keys.plantEmoji(potId)]  ?: "🌱"
                )
            }

            UserPreferences(
                theme    = AppTheme.entries.find { it.name == prefs[Keys.THEME] }
                    ?: AppTheme.SYSTEM,
                language = AppLanguage.entries.find { it.name == prefs[Keys.LANGUAGE] }
                    ?: AppLanguage.FRENCH,
                notificationsEnabled = prefs[Keys.NOTIFS] ?: true,
                username     = prefs[Keys.USERNAME]  ?: "",
                email        = prefs[Keys.EMAIL]     ?: "",
                photoUrl     = prefs[Keys.PHOTO_URL],
                savedPlants  = plants
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
        LocaleHelper.save(context, language)
    }

    suspend fun setNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFS] = enabled }
    }

    suspend fun setUserInfo(username: String, email: String, photoUrl: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.EMAIL]    = email
            photoUrl?.let { prefs[Keys.PHOTO_URL] = it }
        }
    }

    suspend fun savePlant(potId: String, plant: Plant) {
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.PLANT_POTS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            if (potId !in existing) {
                prefs[Keys.PLANT_POTS] = (existing + potId).joinToString(",")
            }
            prefs[Keys.plantId(potId)]     = plant.id
            prefs[Keys.plantCommon(potId)] = plant.commonName
            prefs[Keys.plantSci(potId)]    = plant.scientificName
            prefs[Keys.plantEmoji(potId)]  = plant.emoji
        }
    }
}
