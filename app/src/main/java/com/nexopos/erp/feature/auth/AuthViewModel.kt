package com.nexopos.erp.feature.auth

import android.util.Patterns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.R
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.repo.AuthRepository
import com.nexopos.erp.core.repo.AuthState
import com.nexopos.erp.core.repo.UserState as CoreUserState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URL

/**
 * Authentication ViewModel for managing login state and user sessions.
 *
 * Follows the state pattern for UI state management.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    companion object {
        private const val TOKEN_LIFECYCLE_TAG = "TokenLifecycle"
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _authState = authRepository.authState
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = authRepository.currentUser
    val currentUser: StateFlow<CoreUserState?> = _currentUser

    private val _serverUrl = MutableStateFlow<String>("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        // Check for existing session on initialization
        Log.d(TOKEN_LIFECYCLE_TAG, "AuthViewModel init - checking for existing session")
        viewModelScope.launch {
            // Load the saved server URL
            _serverUrl.value = settingsRepository.baseUrlFlow.first()
            authRepository.initializeFromToken()
            Log.d(TOKEN_LIFECYCLE_TAG, "AuthViewModel init - auth state: ${authRepository.authState.value}")
        }
    }

    /**
     * Validate if the given URL is a valid HTTP/HTTPS URL.
     * @return true if valid, false otherwise
     */
    fun isValidUrl(url: String): Boolean {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return false
        
        return try {
            val parsedUrl = URL(trimmedUrl)
            parsedUrl.protocol in listOf("http", "https") && parsedUrl.host.isNotBlank()
        } catch (e: MalformedURLException) {
            false
        }
    }

    /**
     * Update the server URL. This will be saved when login is attempted.
     */
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    /**
     * Attempt to login with email, password, and optional server URL.
     * If serverUrl is provided and valid, it will be saved before login.
     */
    fun login(email: String, password: String, serverUrl: String? = null) {
        Log.d(TOKEN_LIFECYCLE_TAG, "Login initiated - email: $email")
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Email and password are required")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _loginState.value = LoginState.Error("Please enter a valid email address")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            Log.d(TOKEN_LIFECYCLE_TAG, "Login state: Loading")

            // If a new server URL is provided, validate and save it before login
            if (!serverUrl.isNullOrBlank()) {
                if (!isValidUrl(serverUrl)) {
                    _loginState.value = LoginState.Error("Invalid server URL format")
                    Log.d(TOKEN_LIFECYCLE_TAG, "Login state: Error - Invalid server URL")
                    return@launch
                }
                
                try {
                    settingsRepository.setBaseUrl(serverUrl)
                    _serverUrl.value = settingsRepository.baseUrlFlow.first()
                    Log.d(TOKEN_LIFECYCLE_TAG, "Server URL updated: ${_serverUrl.value}")
                } catch (e: IllegalArgumentException) {
                    _loginState.value = LoginState.Error(e.message ?: "Invalid server URL")
                    Log.d(TOKEN_LIFECYCLE_TAG, "Login state: Error - ${e.message}")
                    return@launch
                }
            }

            val result = authRepository.login(email, password)

            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                    Log.d(TOKEN_LIFECYCLE_TAG, "Login state: Success")
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(
                        error.message ?: "Login failed. Please check your credentials."
                    )
                    Log.d(TOKEN_LIFECYCLE_TAG, "Login state: Error - ${error.message}")
                }
            )
        }
    }

    /**
     * Logout the current user.
     */
    fun logout() {
        Log.d(TOKEN_LIFECYCLE_TAG, "Logout initiated from AuthViewModel")
        viewModelScope.launch {
            authRepository.logout()
            _loginState.value = LoginState.Initial
            Log.d(TOKEN_LIFECYCLE_TAG, "Logout complete - login state reset to Initial")
        }
    }

    /**
     * Clear login error state.
     */
    fun clearError() {
        _loginState.value = LoginState.Initial
    }

    /**
     * Check if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }
}

/**
 * Represents the login UI state.
 */
sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

/**
 * User state from authentication.
 */
data class UserState(
    val id: Long,
    val name: String,
    val email: String
)
