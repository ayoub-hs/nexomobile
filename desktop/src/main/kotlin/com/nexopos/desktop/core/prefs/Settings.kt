package com.nexopos.desktop.core.prefs

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.prefs.Preferences

/**
 * Application settings (matching Android SharedPreferences)
 */
class AppSettings {
    private val prefs: Preferences = Preferences.userNodeForPackage(AppSettings::class.java)

    private val tokenKeyPath: Path by lazy {
        Paths.get(System.getProperty("user.home"), ".nexopos", "desktop.key")
    }

    private val tokenPrefKey = "token"
    private val tokenPrefix = "enc:"
    
    var baseUrl: String
        get() = prefs.get("base_url", "")
        set(value) = prefs.put("base_url", value)
    
    var token: String
        get() {
            val stored = prefs.get(tokenPrefKey, "")
            if (stored.isBlank()) {
                return ""
            }

            if (stored.startsWith(tokenPrefix)) {
                val key = loadKey() ?: run {
                    prefs.remove(tokenPrefKey)
                    return ""
                }
                val decrypted = decryptToken(stored.removePrefix(tokenPrefix), key)
                if (decrypted == null) {
                    prefs.remove(tokenPrefKey)
                    return ""
                }
                return decrypted
            }

            // Legacy plaintext token - attempt migration to encrypted storage
            val key = loadOrCreateKey() ?: run {
                prefs.remove(tokenPrefKey)
                return ""
            }
            val encrypted = encryptToken(stored, key) ?: run {
                prefs.remove(tokenPrefKey)
                return ""
            }
            prefs.put(tokenPrefKey, tokenPrefix + encrypted)
            return stored
        }
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(tokenPrefKey)
                return
            }

            val key = loadOrCreateKey() ?: run {
                prefs.remove(tokenPrefKey)
                return
            }

            val encrypted = encryptToken(trimmed, key) ?: run {
                prefs.remove(tokenPrefKey)
                return
            }

            prefs.put(tokenPrefKey, tokenPrefix + encrypted)
        }
    
    var storeName: String
        get() = prefs.get("store_name", "")
        set(value) = prefs.put("store_name", value)
    
    var printerAddress: String
        get() = prefs.get("printer_address", "")
        set(value) = prefs.put("printer_address", value)
    
    var printerPort: Int
        get() = prefs.getInt("printer_port", 9100)
        set(value) = prefs.putInt("printer_port", value)

    var quickAccessCategoryId: Long
        get() = prefs.getLong("quick_access_category_id", 0L)
        set(value) = prefs.putLong("quick_access_category_id", value)

    private var popularProductClicksRaw: String
        get() = prefs.get("popular_product_clicks", "")
        set(value) = prefs.put("popular_product_clicks", value)

    fun getPopularProductClicks(): Map<Long, Int> {
        return popularProductClicksRaw
            .split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val productId = parts[0].toLongOrNull() ?: return@mapNotNull null
                val count = parts[1].toIntOrNull() ?: return@mapNotNull null
                productId to count
            }
            .toMap()
    }

    fun incrementPopularProductClick(productId: Long) {
        val updated = getPopularProductClicks().toMutableMap()
        updated[productId] = (updated[productId] ?: 0) + 1
        popularProductClicksRaw = updated.entries
            .sortedByDescending { it.value }
            .take(50)
            .joinToString(",") { "${it.key}:${it.value}" }
    }
    
    fun isConfigured(): Boolean {
        return baseUrl.isNotEmpty() && token.isNotEmpty()
    }

    private fun loadKey(): SecretKey? {
        return try {
            if (!Files.exists(tokenKeyPath)) {
                null
            } else {
                val encoded = Files.readString(tokenKeyPath).trim()
                val bytes = Base64.getDecoder().decode(encoded)
                if (bytes.size != 32) return null
                SecretKeySpec(bytes, "AES")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadOrCreateKey(): SecretKey? {
        loadKey()?.let { return it }

        return try {
            Files.createDirectories(tokenKeyPath.parent)
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            val encoded = Base64.getEncoder().encodeToString(keyBytes)
            Files.writeString(tokenKeyPath, encoded)
            try {
                val perms = setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
                Files.setPosixFilePermissions(tokenKeyPath, perms)
            } catch (_: Exception) {
                // Ignore if filesystem doesn't support POSIX permissions
            }
            SecretKeySpec(keyBytes, "AES")
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptToken(plain: String, key: SecretKey): String? {
        return try {
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptToken(payload: String, key: SecretKey): String? {
        return try {
            val decoded = Base64.getDecoder().decode(payload)
            if (decoded.size <= 12) return null
            val iv = decoded.copyOfRange(0, 12)
            val cipherText = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
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
