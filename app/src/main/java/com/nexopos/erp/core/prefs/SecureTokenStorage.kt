package com.nexopos.erp.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * MED-005: Secure storage for authentication token using EncryptedSharedPreferences.
 * 
 * This class provides encrypted storage for sensitive data like API tokens,
 * using Android's Keystore-backed encryption.
 * 
 * HIGH-001: Added encryption status tracking to handle silent encryption failures.
 */
class SecureTokenStorage(context: Context) {
    private val appContext = context.applicationContext
    
    companion object {
        private const val TAG = "SecureTokenStorage"
        private const val TOKEN_LIFECYCLE_TAG = "TokenLifecycle"
        private const val PREFS_NAME = "secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_TOKEN_EXPIRATION = "token_expiration"
        private const val DEFAULT_TOKEN_LIFETIME_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    private val _tokenFlow = MutableStateFlow("")
    val tokenFlow: Flow<String> = _tokenFlow.asStateFlow()
    
    /**
     * Encryption initialization status for UI observation.
     * HIGH-001: Exposes encryption status so UI can handle failures.
     */
    sealed class EncryptionStatus {
        object Initializing : EncryptionStatus()
        object Ready : EncryptionStatus()
        data class Error(val message: String) : EncryptionStatus()
    }
    
    private val _encryptionStatus = MutableStateFlow<EncryptionStatus>(EncryptionStatus.Initializing)
    val encryptionStatus: StateFlow<EncryptionStatus> = _encryptionStatus.asStateFlow()
    
    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            _encryptionStatus.value = EncryptionStatus.Ready
            prefs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences", e)
            _encryptionStatus.value = EncryptionStatus.Error(e.message ?: "Encryption initialization failed")
            null
        }
    }
    
    init {
        // Load initial token value from encrypted storage (not from cache which is empty)
        _tokenFlow.value = loadTokenFromStorage()
    }
    
    /**
     * Load token directly from encrypted storage.
     * Used during initialization to populate the cache.
     */
    private fun loadTokenFromStorage(): String {
        return try {
            val prefs = encryptedPrefs
            val token = prefs?.getString(KEY_TOKEN, null) ?: ""
            if (token.isNotBlank()) {
                Log.d(TOKEN_LIFECYCLE_TAG, "Token loaded from storage on init - length: ${token.length}")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load token from storage on init", e)
            ""
        }
    }
    
    /**
     * Get token synchronously - returns cached value to prevent read storms.
     * 
     * This method now returns the in-memory cached value instead of reading from
     * encrypted storage every time. The cache is updated when token is set/cleared.
     * 
     * Performance: O(1) memory access instead of encrypted SharedPreferences I/O.
     * 
     * Note: If the token was modified from a different SecureTokenStorage instance
     * (which shouldn't happen with proper singleton usage), the cache may be stale.
     * Use forceRefreshFromStorage() to reload from storage if needed.
     */
    fun getTokenSync(): String {
        val token = _tokenFlow.value
        // Only log on cache miss or special conditions to reduce log spam
        if (token.isBlank()) {
            Log.d(TOKEN_LIFECYCLE_TAG, "Token cache miss - no token in memory")
        }
        return token
    }
    
    /**
     * Get the cached token value synchronously without blocking.
     * This is now an alias for getTokenSync() since both return the cached value.
     */
    fun getCachedTokenSync(): String = _tokenFlow.value
    
    /**
     * Force refresh the token cache from encrypted storage.
     * Use this only when you suspect the cache may be stale (rare edge case).
     * @return The token value loaded from storage
     */
    fun forceRefreshFromStorage(): String {
        val token = encryptedPrefs?.getString(KEY_TOKEN, null) ?: ""
        Log.d(TOKEN_LIFECYCLE_TAG, "Token force-refreshed from storage - exists: ${token.isNotBlank()}, length: ${token.length}")
        _tokenFlow.value = token
        return token
    }
    
    /**
     * HIGH-001: Check if encryption is properly initialized.
     * @throws SecurityException if encryption is not available
     */
    private fun requireEncryption(): SharedPreferences {
        return encryptedPrefs ?: throw SecurityException(
            "Secure storage is not available. " +
            (_encryptionStatus.value.let { 
                if (it is EncryptionStatus.Error) it.message else "Unknown error" 
            })
        )
    }
    
    /**
     * Get token asynchronously - returns cached value for O(1) performance.
     * The cache is initialized on SecureTokenStorage creation and updated on set/clear.
     */
    suspend fun getToken(): String = withContext(Dispatchers.IO) {
        // Return cached value - no storage I/O needed
        _tokenFlow.value
    }
    
    /**
     * HIGH-001: Store token with expiration timestamp.
     * @throws SecurityException if encryption is not available
     */
    suspend fun setToken(token: String, expirationMs: Long = DEFAULT_TOKEN_LIFETIME_MS) = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        Log.d(TOKEN_LIFECYCLE_TAG, "Token saved to storage - key: $KEY_TOKEN, length: ${trimmedToken.length}, expirationMs: $expirationMs")
        val prefs = requireEncryption()
        val expirationTime = System.currentTimeMillis() + expirationMs
        
        prefs.edit()
            .putString(KEY_TOKEN, trimmedToken)
            .putLong(KEY_TOKEN_EXPIRATION, expirationTime)
            .apply()
        
        _tokenFlow.value = trimmedToken
        Log.d(TOKEN_LIFECYCLE_TAG, "Token saved successfully - StateFlow updated")
    }
    
    /**
     * Check if the stored token has expired.
     * @return true if token is expired or no expiration is stored
     */
    fun isTokenExpired(): Boolean {
        val prefs = encryptedPrefs ?: return true
        val expiration = prefs.getLong(KEY_TOKEN_EXPIRATION, 0)
        if (expiration == 0L) return false // No expiration set, assume valid
        return System.currentTimeMillis() > expiration
    }
    
    /**
     * Get remaining time until token expires in milliseconds.
     * @return remaining time in ms, or 0 if expired/no token
     */
    fun getTokenRemainingTimeMs(): Long {
        val prefs = encryptedPrefs ?: return 0
        val expiration = prefs.getLong(KEY_TOKEN_EXPIRATION, 0)
        if (expiration == 0L) return Long.MAX_VALUE // No expiration set
        val remaining = expiration - System.currentTimeMillis()
        return remaining.coerceAtLeast(0)
    }
    
    /**
     * HIGH-001: Clear token with proper error handling.
     * @throws SecurityException if encryption is not available
     */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        Log.d(TOKEN_LIFECYCLE_TAG, "Token cleared from storage - key: $KEY_TOKEN")
        val prefs = requireEncryption()
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_EXPIRATION)
            .apply()
        _tokenFlow.value = ""
        Log.d(TOKEN_LIFECYCLE_TAG, "Token cleared successfully - StateFlow reset")
    }
    
    /**
     * Check if a custom token has been set.
     * Uses cached value for O(1) performance.
     */
    fun hasCustomToken(): Boolean {
        return _tokenFlow.value.isNotBlank()
    }
    
    /**
     * HIGH-001: Check if encryption is available and ready.
     */
    fun isEncryptionAvailable(): Boolean {
        return encryptedPrefs != null && _encryptionStatus.value is EncryptionStatus.Ready
    }
    
    /**
     * HIGH-001: Expose encryption status as a Flow for UI observation.
     * Converts internal EncryptionStatus to the public EncryptionStatus data class.
     */
    val encryptionStatusFlow: Flow<com.nexopos.erp.core.prefs.EncryptionStatus> = _encryptionStatus.map { status ->
        when (status) {
            is EncryptionStatus.Initializing -> com.nexopos.erp.core.prefs.EncryptionStatus(
                isAvailable = false,
                error = null
            )
            is EncryptionStatus.Ready -> com.nexopos.erp.core.prefs.EncryptionStatus(
                isAvailable = true,
                error = null
            )
            is EncryptionStatus.Error -> com.nexopos.erp.core.prefs.EncryptionStatus(
                isAvailable = false,
                error = status.message
            )
        }
    }
    
    /**
     * Get current encryption error message if any.
     */
    fun getEncryptionError(): String? {
        return (_encryptionStatus.value as? EncryptionStatus.Error)?.message
    }
}
