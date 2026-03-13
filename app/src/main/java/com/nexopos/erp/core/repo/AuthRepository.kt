package com.nexopos.erp.core.repo

import android.content.Context
import android.os.Build
import android.util.Log
import com.nexopos.erp.R
import com.nexopos.erp.core.network.LoginRequest
import com.nexopos.erp.core.network.LoginResponse
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.PermissionsResponse
import com.nexopos.erp.core.network.UserResponse
import com.nexopos.erp.core.network.isConnectivityIssue
import com.nexopos.erp.core.prefs.FeatureFlags
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Authentication repository for handling login, logout, and user state.
 * 
 * Provides a clean interface for auth operations with token management.
 * 
 * HIGH-001: Added token expiration checking and 401 error handling.
 */
class AuthRepository(
    private val context: Context,
    private val api: MobileApi,
    private val tokenStorage: SecureTokenStorage,
    private val featureFlags: FeatureFlags
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val TOKEN_LIFECYCLE_TAG = "TokenLifecycle"
        private const val DEVICE_NAME_PREFIX = "NexoPos "
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserState?>(null)
    val currentUser: StateFlow<UserState?> = _currentUser.asStateFlow()

    /**
     * Check if user is currently authenticated.
     */
    val isAuthenticated: Flow<Boolean> = tokenStorage.tokenFlow.map { it.isNotBlank() }
    
    /**
     * Observe encryption status from SecureTokenStorage.
     * HIGH-001: UI can observe this to handle encryption failures.
     */
    val encryptionStatus: StateFlow<SecureTokenStorage.EncryptionStatus> = tokenStorage.encryptionStatus

    /**
     * Login with email and password.
     * HIGH-001: Now stores token with expiration timestamp.
     */
    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            // Check if encryption is available before attempting login
            if (!tokenStorage.isEncryptionAvailable()) {
                val status = tokenStorage.encryptionStatus.value
                val errorMsg = if (status is SecureTokenStorage.EncryptionStatus.Error) {
                    "Secure storage unavailable: ${status.message}"
                } else {
                    "Secure storage not ready"
                }
                Log.e(TAG, errorMsg)
                _authState.value = AuthState.Error(errorMsg)
                return@withContext Result.failure(SecurityException(errorMsg))
            }
            
            val deviceName = "$DEVICE_NAME_PREFIX ${Build.MODEL}"
            val request = LoginRequest(email, password, deviceName)
            Log.d(TOKEN_LIFECYCLE_TAG, "Login request sent - email: $email, device: $deviceName")
            val response = api.login(request)
            
            if (response.success && response.data != null) {
                Log.d(TOKEN_LIFECYCLE_TAG, "Login response received - token length: ${response.data.token.length}, user: ${response.data.user.email}")
                // Save the token securely with expiration
                tokenStorage.setToken(response.data.token)
                _currentUser.value = UserState(
                    id = response.data.user.id,
                    name = response.data.user.name,
                    email = response.data.user.email
                )
                _authState.value = AuthState.Authenticated
                Log.i(TAG, "Login successful for user: ${response.data.user.email}")
                Result.success(response)
            } else {
                _authState.value = AuthState.Error("Login failed")
                Result.failure(Exception(response.data?.let { "Login failed" } ?: "Unknown error"))
            }
        } catch (e: Exception) {
            val message = resolveLoginErrorMessage(e)
            Log.e(TAG, "Login failed", e)
            _authState.value = AuthState.Error(message)
            Result.failure(Exception(message, e))
        }
    }

    /**
     * Logout and clear all authentication data.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TOKEN_LIFECYCLE_TAG, "Logout initiated - clearing token")
        try {
            api.logout()
            clearAuthData()
            Log.i(TAG, "Logout successful")
            Log.d(TOKEN_LIFECYCLE_TAG, "Token cleared on logout")
            Result.success(Unit)
        } catch (e: Exception) {
            // Clear local data even if network call fails
            Log.w(TAG, "Logout network call failed, clearing local data anyway", e)
            clearAuthData()
            Log.d(TOKEN_LIFECYCLE_TAG, "Token cleared on logout (network failed)")
            Result.success(Unit)
        }
    }

    /**
     * Fetch current user information.
     * HIGH-001: Checks token expiration before making API call.
     */
    suspend fun fetchCurrentUser(): Result<UserResponse> = withContext(Dispatchers.IO) {
        try {
            // Check token expiration before API call
            if (tokenStorage.isTokenExpired()) {
                Log.w(TAG, "Token is expired, clearing auth data")
                clearAuthData()
                return@withContext Result.failure(Exception("Token expired"))
            }
            
            val response = api.getCurrentUser()
            if (response.success && response.data != null) {
                _currentUser.value = UserState(
                    id = response.data.id,
                    name = response.data.name,
                    email = response.data.email
                )
                Result.success(response)
            } else {
                Result.failure(Exception("Failed to fetch user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch current user", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch user permissions and update FeatureFlags.
     * HIGH-001: Checks token expiration before making API call.
     */
    suspend fun fetchPermissions(): Result<PermissionsResponse> = withContext(Dispatchers.IO) {
        try {
            // Check token expiration before API call
            if (tokenStorage.isTokenExpired()) {
                Log.w(TAG, "Token is expired, clearing auth data")
                clearAuthData()
                return@withContext Result.failure(Exception("Token expired"))
            }
            
            val response = api.getPermissions()
            if (response.success && response.data != null) {
                // Update FeatureFlags with permissions and roles
                featureFlags.updatePermissionsAndRoles(
                    permissions = response.data.permissions,
                    roles = response.data.roles
                )
                Result.success(response)
            } else {
                Result.failure(Exception("Failed to fetch permissions"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch permissions", e)
            Result.failure(e)
        }
    }

    /**
     * Get the stored authentication token.
     */
    suspend fun getToken(): String = tokenStorage.getToken()

    /**
     * Check if user has a valid token.
     * HIGH-001: Also checks if token is not expired.
     */
    fun hasValidToken(): Boolean {
        val hasToken = tokenStorage.hasCustomToken()
        val isExpired = tokenStorage.isTokenExpired()
        return hasToken && !isExpired
    }
    
    /**
     * HIGH-001: Check if the current token is expired.
     */
    fun isTokenExpired(): Boolean = tokenStorage.isTokenExpired()
    
    /**
     * HIGH-001: Get remaining time until token expires in milliseconds.
     */
    fun getTokenRemainingTimeMs(): Long = tokenStorage.getTokenRemainingTimeMs()

    /**
     * Clear all authentication data.
     */
    private suspend fun clearAuthData() {
        try {
            tokenStorage.clearToken()
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to clear token from secure storage", e)
            // Continue to clear other data even if token clear fails
        }
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
        featureFlags.clear()
    }

    /**
     * Initialize auth state from existing token.
     * HIGH-001: Also checks token expiration.
     */
    suspend fun initializeFromToken() {
        if (hasValidToken()) {
            // Check if token is expired
            if (tokenStorage.isTokenExpired()) {
                Log.i(TAG, "Stored token is expired, clearing auth data")
                clearAuthData()
            } else {
                _authState.value = AuthState.Authenticated
                Log.i(TAG, "Initialized from existing valid token")
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }
    
    /**
     * HIGH-001: Handle session expiration from TokenAuthenticator.
     * Call this when a 401 response is received to update auth state.
     */
    fun handleSessionExpired() {
        Log.w(TAG, "Session expired, updating auth state")
        _currentUser.value = null
        _authState.value = AuthState.SessionExpired
        featureFlags.clear()
    }

    private fun resolveLoginErrorMessage(error: Throwable): String {
        if (error.isConnectivityIssue()) {
            return context.getString(R.string.login_error_network)
        }

        return when (error) {
            is HttpException -> extractHttpErrorMessage(error)
                ?: fallbackLoginMessage(error.code())
            is JsonDataException -> context.getString(R.string.login_error_invalid_credentials)
            else -> error.message ?: context.getString(R.string.login_error_unknown)
        }
    }

    private fun extractHttpErrorMessage(error: HttpException): String? {
        val rawBody = error.response()?.errorBody()?.string()?.trim().orEmpty()
        if (rawBody.isBlank()) {
            return null
        }

        if (rawBody.startsWith("<")) {
            return null
        }

        return try {
            val json = JSONObject(rawBody)
            val errors = json.optJSONObject("errors")
            if (errors != null) {
                val firstKey = errors.keys().asSequence().firstOrNull()
                val firstArray = firstKey?.let { errors.optJSONArray(it) }
                val firstMessage = firstArray?.optString(0)?.trim().orEmpty()
                if (firstMessage.isNotBlank()) {
                    return firstMessage
                }
            }

            json.optString("message").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            rawBody.takeIf { it.isNotBlank() }
        }
    }

    private fun fallbackLoginMessage(code: Int): String {
        return when (code) {
            401, 403, 422 -> context.getString(R.string.login_error_invalid_credentials)
            else -> context.getString(R.string.login_error_unknown)
        }
    }
}

/**
 * Represents the current authentication state.
 * HIGH-001: Added SessionExpired state.
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
    object SessionExpired : AuthState()
}

/**
 * Represents a logged-in user.
 */
data class UserState(
    val id: Long,
    val name: String,
    val email: String
)
