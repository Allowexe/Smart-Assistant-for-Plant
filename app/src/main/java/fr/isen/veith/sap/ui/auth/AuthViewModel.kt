package fr.isen.veith.sap.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.R
import fr.isen.veith.sap.data.repository.AuthRepository
import fr.isen.veith.sap.data.repository.AuthResult
import fr.isen.veith.sap.data.repository.FirebaseAuthRepository
import fr.isen.veith.sap.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI State ────────────────────────────────────────────────────────
data class AuthUiState(
    val isLoginTab: Boolean = true,

    val loginEmail: String    = "",
    val loginPassword: String = "",

    val regUsername: String   = "",
    val regEmail: String      = "",
    val regPassword: String   = "",

    val isLoading: Boolean    = false,
    val errorMessage: String? = null,
    val successUser: User?    = null,

    val loginPwdVisible: Boolean = false,
    val regPwdVisible: Boolean   = false
)

// ── ViewModel ───────────────────────────────────────────────────────
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuthRepository = FirebaseAuthRepository()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ── Navigation entre onglets ───────────────────────────────────
    fun selectTab(isLogin: Boolean) {
        _uiState.update { it.copy(isLoginTab = isLogin, errorMessage = null) }
    }

    // ── Champs login ───────────────────────────────────────────────
    fun onLoginEmailChange(value: String)    = _uiState.update { it.copy(loginEmail = value) }
    fun onLoginPasswordChange(value: String) = _uiState.update { it.copy(loginPassword = value) }
    fun toggleLoginPwdVisibility()           = _uiState.update { it.copy(loginPwdVisible = !it.loginPwdVisible) }

    // ── Champs register ───────────────────────────────────────────
    fun onRegUsernameChange(value: String) = _uiState.update { it.copy(regUsername = value) }
    fun onRegEmailChange(value: String)    = _uiState.update { it.copy(regEmail = value) }
    fun onRegPasswordChange(value: String) = _uiState.update { it.copy(regPassword = value) }
    fun toggleRegPwdVisibility()           = _uiState.update { it.copy(regPwdVisible = !it.regPwdVisible) }

    // ── Actions ───────────────────────────────────────────────────
    fun login() {
        val state = _uiState.value
        if (!validateLogin(state)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.login(state.loginEmail.trim(), state.loginPassword)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoading = false, successUser = result.user)
                }
                is AuthResult.Error   -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (!validateRegister(state)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.register(
                state.regUsername.trim(),
                state.regEmail.trim(),
                state.regPassword
            )) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoading = false, successUser = result.user)
                }
                is AuthResult.Error   -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ── Validation ────────────────────────────────────────────────
    private fun validateLogin(state: AuthUiState): Boolean {
        val ctx = getApplication<Application>()
        val error = when {
            state.loginEmail.isBlank()      -> ctx.getString(R.string.error_email_empty)
            !state.loginEmail.contains("@") -> ctx.getString(R.string.error_email_invalid)
            state.loginPassword.length < 6  -> ctx.getString(R.string.error_password_short)
            else                            -> null
        }
        if (error != null) _uiState.update { it.copy(errorMessage = error) }
        return error == null
    }

    private fun validateRegister(state: AuthUiState): Boolean {
        val ctx = getApplication<Application>()
        val error = when {
            state.regUsername.isBlank()     -> ctx.getString(R.string.error_username_empty)
            state.regEmail.isBlank()        -> ctx.getString(R.string.error_email_empty)
            !state.regEmail.contains("@")   -> ctx.getString(R.string.error_email_invalid)
            state.regPassword.length < 6    -> ctx.getString(R.string.error_password_short)
            else                            -> null
        }
        if (error != null) _uiState.update { it.copy(errorMessage = error) }
        return error == null
    }
}