package fr.isen.veith.sap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Sap_prefs")

// ── Enum thème ────────────────────────────────────────────────────────
enum class AppTheme(val label: String) {
    LIGHT("Clair"),
    DARK("Sombre"),
    SYSTEM("Appareil")
}

// ── Enum langue ───────────────────────────────────────────────────────
enum class AppLanguage(val label: String, val code: String) {
    FRENCH("Français", "fr"),
    ENGLISH("English",  "en")
}

// ── Modèle des préférences ────────────────────────────────────────────
data class UserPreferences(
    val theme: AppTheme         = AppTheme.SYSTEM,
    val language: AppLanguage   = AppLanguage.FRENCH,
    val notificationsEnabled: Boolean = true,
    val username: String        = "",
    val email: String           = "",
    val photoUrl: String?       = null
)

// ── Repository DataStore ───────────────────────────────────────────────
class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME         = stringPreferencesKey("theme")
        val LANGUAGE      = stringPreferencesKey("language")
        val NOTIFS        = booleanPreferencesKey("notifications")
        val USERNAME      = stringPreferencesKey("username")
        val EMAIL         = stringPreferencesKey("email")
        val PHOTO_URL     = stringPreferencesKey("photo_url")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            UserPreferences(
                theme    = AppTheme.entries.find { it.name == prefs[Keys.THEME] }
                    ?: AppTheme.SYSTEM,
                language = AppLanguage.entries.find { it.name == prefs[Keys.LANGUAGE] }
                    ?: AppLanguage.FRENCH,
                notificationsEnabled = prefs[Keys.NOTIFS] ?: true,
                username  = prefs[Keys.USERNAME]  ?: "",
                email     = prefs[Keys.EMAIL]     ?: "",
                photoUrl  = prefs[Keys.PHOTO_URL]
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
}