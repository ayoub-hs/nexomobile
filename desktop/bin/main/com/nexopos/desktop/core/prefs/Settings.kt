package com.nexopos.desktop.core.prefs

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import java.util.prefs.Preferences

/**
 * Application settings (matching Android SharedPreferences)
 */
class AppSettings {
    private val prefs: Preferences = Preferences.userNodeForPackage(AppSettings::class.java)
    
    var baseUrl: String
        get() = prefs.get("base_url", "")
        set(value) = prefs.put("base_url", value)
    
    var token: String
        get() = prefs.get("token", "")
        set(value) = prefs.put("token", value)
    
    var storeName: String
        get() = prefs.get("store_name", "")
        set(value) = prefs.put("store_name", value)
    
    var printerAddress: String
        get() = prefs.get("printer_address", "")
        set(value) = prefs.put("printer_address", value)
    
    var printerPort: Int
        get() = prefs.getInt("printer_port", 9100)
        set(value) = prefs.putInt("printer_port", value)
    
    fun isConfigured(): Boolean {
        return baseUrl.isNotEmpty() && token.isNotEmpty()
    }
    
    companion object {
        @Volatile
        private var instance: AppSettings? = null
        
        fun getInstance(): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings().also { instance = it }
            }
        }
    }
}
