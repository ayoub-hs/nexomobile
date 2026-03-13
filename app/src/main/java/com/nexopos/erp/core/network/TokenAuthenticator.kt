package com.nexopos.erp.core.network

import android.util.Log
import com.nexopos.erp.core.prefs.SecureTokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * HIGH-001: OkHttp Authenticator to handle 401 Unauthorized responses.
 * 
 * This authenticator intercepts 401 responses and:
 * 1. Clears the stored token
 * 2. Emits a re-login event that the UI can observe
 * 3. Returns null to indicate no retry (let the call fail with 401)
 * 
 * The UI should observe [reloginRequired] flow to navigate to login screen.
 */
class TokenAuthenticator(
    private val tokenStorage: SecureTokenStorage
) : Authenticator {
    
    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val TOKEN_LIFECYCLE_TAG = "TokenLifecycle"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Event emitted when a 401 response is received and token needs to be cleared.
     * UI components should observe this and navigate to login screen.
     */
    sealed class AuthEvent {
        object SessionExpired : AuthEvent()
        object TokenCleared : AuthEvent()
    }
    
    private val _authEvent = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvent: SharedFlow<AuthEvent> = _authEvent.asSharedFlow()
    
    override fun authenticate(route: Route?, response: Response): Request? {
        // Check if this is a 401 response
        if (response.code != 401) {
            return null
        }
        
        Log.w(TAG, "Received 401 Unauthorized response for ${response.request.url}")
        Log.d(TOKEN_LIFECYCLE_TAG, "401 Unauthorized received - endpoint: ${response.request.url.encodedPath}")
        
        // Don't try to authenticate if we've already tried (prevent infinite loop)
        val priorAuthHeader = response.request.header("Authorization")
        if (priorAuthHeader != null) {
            Log.i(TAG, "Request already had Authorization header, clearing token and signaling re-login")
            Log.d(TOKEN_LIFECYCLE_TAG, "Token authentication failed - clearing token due to 401")
            
            // Clear the token asynchronously
            scope.launch {
                try {
                    tokenStorage.clearToken()
                    _authEvent.emit(AuthEvent.SessionExpired)
                    Log.i(TAG, "Token cleared and session expired event emitted")
                    Log.d(TOKEN_LIFECYCLE_TAG, "Token cleared due to session expiration")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear token", e)
                    _authEvent.emit(AuthEvent.TokenCleared)
                }
            }
            
            // Return null to let the call fail with 401
            // The UI should observe authEvent and handle navigation to login
            return null
        }
        
        // No prior auth header, nothing we can do
        return null
    }
    
    /**
     * Check if the current token is expired before making API calls.
     * This is a proactive check to avoid unnecessary 401 responses.
     */
    fun isTokenExpired(): Boolean = tokenStorage.isTokenExpired()
    
    /**
     * Get remaining time until token expires.
     */
    fun getTokenRemainingTimeMs(): Long = tokenStorage.getTokenRemainingTimeMs()
}
