package fr.isen.veith.sap.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fr.isen.veith.sap.data.achievements.AchievementsRepository
import fr.isen.veith.sap.data.preferences.AppLanguage
import fr.isen.veith.sap.data.preferences.AppPreferencesRepository
import fr.isen.veith.sap.data.preferences.AppTheme
import fr.isen.veith.sap.domain.model.Achievement
import fr.isen.veith.sap.domain.model.Achievements
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

data class SettingsUiState(
    val username: String              = "",
    val email: String                 = "",
    val photoUrl: String?             = null,
    val theme: AppTheme               = AppTheme.SYSTEM,
    val language: AppLanguage         = AppLanguage.FRENCH,
    val notificationsEnabled: Boolean = true,
    val achievements: List<Achievement> = Achievements.all,
    // Contrôle des dialogs
    val showThemeDialog: Boolean      = false,
    val showLanguageDialog: Boolean   = false,
    val showLogoutDialog: Boolean     = false,
    val isSaved: Boolean              = false   // feedback "Sauvegardé"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo        = AppPreferencesRepository(application)
    private val achievementsRepo = AchievementsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _languageChanged = Channel<Unit>(Channel.BUFFERED)
    val languageChanged: Flow<Unit> = _languageChanged.receiveAsFlow()

    init {
        // Charger les préférences sauvegardées
        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        username             = prefs.username.ifBlank { "Jardinier" },
                        email                = prefs.email,
                        photoUrl             = prefs.photoUrl,
                        theme                = prefs.theme,
                        language             = prefs.language,
                        notificationsEnabled = prefs.notificationsEnabled
                    )
                }
            }
        }

        // Live achievement unlock state
        viewModelScope.launch {
            achievementsRepo.achievements.collect { list ->
                _uiState.update { it.copy(achievements = list) }
            }
        }
    }

    // ── Profil ────────────────────────────────────────────────────────
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onEmailChange(value: String)    = _uiState.update { it.copy(email = value) }

    fun saveProfile() {
        viewModelScope.launch {
            prefsRepo.setUserInfo(
                _uiState.value.username,
                _uiState.value.email,
                _uiState.value.photoUrl
            )
            _uiState.update { it.copy(isSaved = true) }
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(isSaved = false) }
        }
    }

    // ── Thème ─────────────────────────────────────────────────────────
    fun showThemeDialog()  = _uiState.update { it.copy(showThemeDialog = true) }
    fun dismissThemeDialog() = _uiState.update { it.copy(showThemeDialog = false) }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            prefsRepo.setTheme(theme)
            _uiState.update { it.copy(theme = theme, showThemeDialog = false) }
        }
    }

    // ── Langue ────────────────────────────────────────────────────────
    fun showLanguageDialog()   = _uiState.update { it.copy(showLanguageDialog = true) }
    fun dismissLanguageDialog() = _uiState.update { it.copy(showLanguageDialog = false) }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            prefsRepo.setLanguage(lang)
            _uiState.update { it.copy(language = lang, showLanguageDialog = false) }
            _languageChanged.send(Unit)
        }
    }

    // ── Notifications ─────────────────────────────────────────────────
    fun toggleNotifications() {
        val newValue = !_uiState.value.notificationsEnabled
        viewModelScope.launch {
            prefsRepo.setNotifications(newValue)
            _uiState.update { it.copy(notificationsEnabled = newValue) }
        }
    }

    // ── Déconnexion ───────────────────────────────────────────────────
    fun showLogoutDialog()    = _uiState.update { it.copy(showLogoutDialog = true) }
    fun dismissLogoutDialog() = _uiState.update { it.copy(showLogoutDialog = false) }

    fun logout(onDone: () -> Unit) {
        FirebaseAuth.getInstance().signOut()
        onDone()
    }
}