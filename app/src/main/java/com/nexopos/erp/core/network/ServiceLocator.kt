package com.nexopos.erp.core.network

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service locator for network dependencies.
 * 
 * HIGH-001: Added TokenAuthenticator for 401 response handling.
 * PERF-001: Fixed main thread blocking by using cached base URL.
 */
object ServiceLocator {
    private const val TAG = "ServiceLocator"
    private const val TOKEN_LIFECYCLE_TAG = "TokenLifecycle"
    private const val DEFAULT_BASE_URL = "http://192.168.1.120:10080/"
    
    // Cache repositories and clients to prevent memory overhead
    @Volatile
    private var settingsRepo: SettingsRepository? = null
    @Volatile
    private var tokenAuthenticator: TokenAuthenticator? = null
    @Volatile
    private var apiInstance: NexoApi? = null
    @Volatile
    private var mobileApiInstance: MobileApi? = null
    // PERF-001: Cache base URL to avoid runBlocking on main thread
    @Volatile
    private var cachedBaseUrl: String? = null

    private fun getSettingsRepository(context: Context, tokenStorage: SecureTokenStorage): SettingsRepository {
        return settingsRepo ?: synchronized(this) {
            settingsRepo ?: SettingsRepository(context.applicationContext, tokenStorage).also {
                settingsRepo = it
            }
        }
    }
    
    /**
     * PERF-001: Get base URL without blocking. Uses cached value if available.
     * Falls back to default if not yet cached.
     */
    private fun getBaseUrl(context: Context, tokenStorage: SecureTokenStorage): String {
        // Return cached value if available
        cachedBaseUrl?.let { return it }
        
        // Try to get from settings repo (may block, but only once)
        return try {
            val url = runBlocking { 
                getSettingsRepository(context, tokenStorage).baseUrlFlow.first() 
            }
            cachedBaseUrl = url
            url
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get base URL, using default", e)
            DEFAULT_BASE_URL
        }
    }
    
    /**
     * PERF-001: Update cached base URL without clearing API instances.
     * Call this when base URL changes to update the cache.
     */
    fun updateCachedBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith('/')) url else "$url/"
        if (cachedBaseUrl != normalizedUrl) {
            cachedBaseUrl = normalizedUrl
            // Clear API instances so they get recreated with new URL
            synchronized(this) {
                apiInstance = null
                mobileApiInstance = null
            }
        }
    }
    
    /**
     * HIGH-001: Get or create TokenAuthenticator instance.
     * Returns null if TokenAuthenticator cannot be created (e.g., encryption unavailable).
     * Now accepts SecureTokenStorage as parameter to use shared Koin-managed instance.
     */
    fun getTokenAuthenticator(context: Context, tokenStorage: SecureTokenStorage): TokenAuthenticator? {
        if (tokenAuthenticator != null) return tokenAuthenticator
        
        return synchronized(this) {
            tokenAuthenticator ?: try {
                TokenAuthenticator(tokenStorage).also {
                    tokenAuthenticator = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create TokenAuthenticator", e)
                null
            }
        }
    }

    private fun authInterceptor(storage: SecureTokenStorage) = Interceptor { chain ->
        // Get token from cache (O(1) memory access, no I/O)
        val token = storage.getTokenSync()
        val request = chain.request().newBuilder().apply {
            addHeader("Accept", "application/json")
            if (token.isNotBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        chain.proceed(request)
    }

    private fun client(context: Context, storage: SecureTokenStorage): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 10L * 1024L * 1024L)

        // Certificate pinning for production hosts
        val certificatePinner = CertificatePinner.Builder()
            .add("jazicloud.dedyn.io", "sha256/5GBJELMTtLfQ0gIfnrTSW01+PGgSTS2VjSD65cft0L0=")
            .build()

        val builder = OkHttpClient.Builder()
            .cache(cache)
            .certificatePinner(certificatePinner)
            .addInterceptor(authInterceptor(storage))
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (!request.method.equals("GET", ignoreCase = true)) {
                    return@addNetworkInterceptor response
                }

                val requestCacheControl = request.header("Cache-Control")?.lowercase().orEmpty()
                val bypassCache = requestCacheControl.contains("no-cache") ||
                    requestCacheControl.contains("no-store") ||
                    request.url.encodedPath.contains("/api/cash-registers")

                if (bypassCache) {
                    return@addNetworkInterceptor response
                }

                val cacheControl = CacheControl.Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build()
                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .build()
            }
            .apply {
                // Only log in debug builds to save battery and improve performance
                if (isDebugBuild()) {
                    val logger = HttpLoggingInterceptor { message ->
                        // Redact sensitive headers in logs
                        val redactedMessage = message
                            .replace(Regex("Authorization: Bearer [^\\s\\r\\n]+"), "Authorization: Bearer [REDACTED]")
                            .replace(Regex("Cookie: [^\\r\\n]+"), "Cookie: [REDACTED]")
                            .replace(Regex("Set-Cookie: [^\\r\\n]+"), "Set-Cookie: [REDACTED]")
                        println(redactedMessage)
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(logger)
                }
            }
            // Add connection pooling for better resource management
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            // Add reasonable timeouts
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
        
        // HIGH-001: Add TokenAuthenticator for 401 response handling
        val authenticator = getTokenAuthenticator(context, storage)
        if (authenticator != null) {
            builder.authenticator(authenticator)
            Log.i(TAG, "TokenAuthenticator added to OkHttpClient")
        } else {
            Log.w(TAG, "TokenAuthenticator not available, 401 responses will not be handled automatically")
        }
        
        return builder.build()
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

    private fun getRetrofit(context: Context, tokenStorage: SecureTokenStorage): Retrofit {
        // PERF-001: Use cached base URL to avoid blocking
        val baseUrl = getBaseUrl(context, tokenStorage)
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client(context, tokenStorage))
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
    }

    fun api(context: Context, tokenStorage: SecureTokenStorage): NexoApi {
        return apiInstance ?: synchronized(this) {
            apiInstance ?: getRetrofit(context, tokenStorage).create(NexoApi::class.java).also {
                apiInstance = it
            }
        }
    }

    fun mobileApi(context: Context, tokenStorage: SecureTokenStorage): MobileApi {
        return mobileApiInstance ?: synchronized(this) {
            mobileApiInstance ?: getRetrofit(context, tokenStorage).create(MobileApi::class.java).also {
                mobileApiInstance = it
            }
        }
    }

    /**
     * Clear cached API instances. Call when base URL changes.
     * Note: Token changes don't require cache clear since we use cached token.
     */
    fun clearApiCache() {
        synchronized(this) {
            apiInstance = null
            mobileApiInstance = null
        }
    }
    
    /**
     * HIGH-001: Clear all cached instances including TokenAuthenticator.
     * Call during logout to ensure clean state.
     */
    fun clearAllCache() {
        synchronized(this) {
            apiInstance = null
            mobileApiInstance = null
            tokenAuthenticator = null
            cachedBaseUrl = null
        }
    }
}
