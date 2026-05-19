package fr.isen.veith.sap.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.preferences.AppPreferencesRepository
import fr.isen.veith.sap.data.preferences.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferencesRepository(application)

    val theme = prefs.preferences
        .map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)
}
