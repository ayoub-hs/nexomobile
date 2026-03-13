package com.nexopos.erp.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.core.print.PrinterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.MalformedURLException
import java.net.URL

private const val DATASTORE_NAME = "app_prefs"

val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

class SettingsRepository(
    context: Context,
    private val secureTokenStorage: SecureTokenStorage
) {
    private val appContext = context.applicationContext
    
    companion object Keys {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        @Deprecated("Use SecureTokenStorage instead")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_PRINTER_TYPE = stringPreferencesKey("printer_type")
        val KEY_PRINTER_MAC = stringPreferencesKey("printer_mac")
        val KEY_PRINTER_HOST = stringPreferencesKey("printer_host")
        val KEY_PRINTER_PORT = intPreferencesKey("printer_port")
        val KEY_PRINTER_WIDTH = intPreferencesKey("printer_width")
        val KEY_PRINTER_CUT = booleanPreferencesKey("printer_cut")
        val KEY_PRINTER_LOGO = stringPreferencesKey("printer_logo")
        val KEY_STORE_NAME = stringPreferencesKey("store_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        const val DEFAULT_BASE_URL = "http://192.168.1.120:10080/"
        const val DEFAULT_TOKEN = ""
        const val DEFAULT_PRINTER_TYPE = "bluetooth"
        const val DEFAULT_PRINTER_HOST = ""
        const val DEFAULT_PRINTER_MAC = ""
        const val DEFAULT_PRINTER_PORT = 9100
        const val DEFAULT_PRINTER_WIDTH = 384
        const val DEFAULT_PRINTER_CUT = true
        const val DEFAULT_PRINTER_LOGO = ""
        const val DEFAULT_STORE_NAME = ""
        const val DEFAULT_THEME_MODE = "system"
    }

    val baseUrlFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        (prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL).ensureEndsWithSlash()
    }

    /**
     * MED-005: Token is now stored in EncryptedSharedPreferences.
     */
    val tokenFlow: Flow<String> = secureTokenStorage.tokenFlow
    
    /**
     * Get the cached token value synchronously without blocking.
     * Safe to call from OkHttp interceptors to avoid ANR.
     */
    fun getCachedTokenSync(): String = secureTokenStorage.getCachedTokenSync()

    val storeNameFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
         prefs[KEY_STORE_NAME] ?: DEFAULT_STORE_NAME
    }

    val printerConfigFlow: Flow<PrinterConfig> = appContext.dataStore.data.map { prefs ->
        val typeStr = prefs[KEY_PRINTER_TYPE] ?: DEFAULT_PRINTER_TYPE
        val type = if (typeStr.equals("bluetooth", true)) PrinterType.Bluetooth else PrinterType.Tcp
        PrinterConfig(
            type = type,
            macAddress = prefs[KEY_PRINTER_MAC] ?: DEFAULT_PRINTER_MAC,
            host = prefs[KEY_PRINTER_HOST] ?: DEFAULT_PRINTER_HOST,
            port = prefs[KEY_PRINTER_PORT] ?: DEFAULT_PRINTER_PORT,
            paperWidthDots = prefs[KEY_PRINTER_WIDTH] ?: DEFAULT_PRINTER_WIDTH,
            cut = prefs[KEY_PRINTER_CUT] ?: DEFAULT_PRINTER_CUT,
            logoUri = prefs[KEY_PRINTER_LOGO]?.takeIf { it.isNotBlank() }
        )
    }
    
    /**
     * Theme mode preference flow.
     * Values: "system", "light", "dark"
     */
    val themeModeFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
    }

    suspend fun setBaseUrl(url: String) {
        val trimmedUrl = url.trim().ensureEndsWithSlash()

        // Validate URL format
        val parsedUrl = try {
            URL(trimmedUrl)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Invalid URL format: $url", e)
        }

        // In release builds, reject HTTP URLs for security (except for private/local networks)
        if (!isDebugBuild() && trimmedUrl.startsWith("http://", ignoreCase = true)) {
            val host = parsedUrl.host
            if (!isPrivateOrLocalNetwork(host)) {
                throw IllegalArgumentException("HTTP URLs are not allowed in release builds for public addresses. Please use HTTPS.")
            }
        }

        appContext.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = trimmedUrl
        }

        // PERF-001: Update cached URL and clear API instances only if URL changed
        ServiceLocator.updateCachedBaseUrl(trimmedUrl)
    }

    /**
     * Check if the host is a private or local network address.
     * These are safe to use with HTTP since they're not exposed to the public internet.
     */
    private fun isPrivateOrLocalNetwork(host: String?): Boolean {
        if (host == null) return false
        
        // Localhost variants
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        
        // Private IPv4 ranges (RFC 1918)
        // 10.0.0.0/8
        if (host.startsWith("10.")) return true
        
        // 172.16.0.0/12 (172.16.x.x - 172.31.x.x)
        if (host.startsWith("172.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull() ?: return false
                if (second in 16..31) return true
            }
        }
        
        // 192.168.0.0/16
        if (host.startsWith("192.168.")) return true
        
        // Local link addresses (169.254.0.0/16)
        if (host.startsWith("169.254.")) return true
        
        // .local mDNS domain
        if (host.endsWith(".local")) return true
        
        // .internal domain (often used for internal services)
        if (host.endsWith(".internal")) return true
        
        return false
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val clazz = Class.forName("com.nexopos.erp.BuildConfig")
            val field = clazz.getField("DEBUG")
            field.getBoolean(null)
        } catch (ignored: Throwable) {
            false
        }
    }

    /**
     * MED-005: Token is now stored in EncryptedSharedPreferences.
     * PERF-001: No need to clear API cache - token is read from memory cache.
     */
    suspend fun setToken(token: String) {
        secureTokenStorage.setToken(token)
        // Note: No need to clear API cache - auth interceptor reads token from memory
    }

    suspend fun setStoreName(storeName: String) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_STORE_NAME] = storeName.trim()
        }
    }

    suspend fun setPrinterConfig(config: PrinterConfig) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_PRINTER_TYPE] = when (config.type) {
                PrinterType.Bluetooth -> "bluetooth"
                PrinterType.Tcp -> "tcp"
            }
            config.macAddress?.let { prefs[KEY_PRINTER_MAC] = it }
            config.host?.let { prefs[KEY_PRINTER_HOST] = it }
            prefs[KEY_PRINTER_PORT] = config.port
            prefs[KEY_PRINTER_WIDTH] = config.paperWidthDots
            prefs[KEY_PRINTER_CUT] = config.cut
            prefs[KEY_PRINTER_LOGO] = config.logoUri ?: ""
        }
    }
    
    /**
     * Set theme mode preference.
     * @param mode One of "system", "light", "dark"
     */
    suspend fun setThemeMode(mode: String) {
        val validMode = when (mode.lowercase()) {
            "light", "dark", "system" -> mode.lowercase()
            else -> DEFAULT_THEME_MODE
        }
        appContext.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = validMode
        }
    }
    
    /**
     * Get encryption status from SecureTokenStorage.
     * @return true if encryption is working properly, false otherwise
     */
    fun isEncryptionAvailable(): Boolean {
        return secureTokenStorage.isEncryptionAvailable()
    }
    
    /**
     * Flow that emits encryption status changes.
     */
    val encryptionStatusFlow: Flow<EncryptionStatus> = secureTokenStorage.encryptionStatusFlow
}

/**
 * Encryption status data class
 */
data class EncryptionStatus(
    val isAvailable: Boolean,
    val error: String? = null
)

private fun String.ensureEndsWithSlash(): String = if (endsWith('/')) this else "$this/"
