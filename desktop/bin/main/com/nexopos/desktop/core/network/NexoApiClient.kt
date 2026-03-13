package com.nexopos.desktop.core.network

import com.nexopos.desktop.core.prefs.AppSettings
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import com.nexopos.shared.models.RegisterResponse
import com.nexopos.shared.models.RegisterData
import com.nexopos.shared.models.RegistersListResponse
import com.nexopos.shared.models.RegisterHistoryResponse
import com.nexopos.shared.models.RegisterHistoryData
import com.nexopos.shared.models.RegisterHistoryListResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP API Client (matching Android's Retrofit + OkHttp)
 */
class NexoApiClient(private val settings: AppSettings) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)    // Reduced from 30
        .readTimeout(15, TimeUnit.SECONDS)       // Reduced from 30  
        .writeTimeout(15, TimeUnit.SECONDS)      // Reduced from 30
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
            
            if (settings.token.isNotEmpty()) {
                builder.header("Authorization", "Bearer ${settings.token}")
            }
            
            chain.proceed(builder.build())
        }
        .build()
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private inline fun <reified T> getAdapter(): JsonAdapter<T> {
        return moshi.adapter(T::class.java)
    }
    
    suspend fun <T> get(endpoint: String, adapter: JsonAdapter<T>): Result<T> {
        return try {
            if (settings.baseUrl.isEmpty()) {
                println("[NexoApiClient] ERROR: No base URL configured")
                return Result.failure(Exception("Server URL not configured"))
            }
            
            val url = "${settings.baseUrl}/$endpoint"
            println("[NexoApiClient] GET $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: throw Exception("Empty response")
                    println("[NexoApiClient] Response: ${body.take(200)}...")
                    val data = adapter.fromJson(body) ?: throw Exception("Failed to parse response")
                    Result.success(data)
                } else {
                    println("[NexoApiClient] HTTP Error: ${resp.code} ${resp.message}")
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            println("[NexoApiClient] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun <T, R> post(endpoint: String, body: T, bodyAdapter: JsonAdapter<T>, responseAdapter: JsonAdapter<R>): Result<R> {
        return try {
            val url = "${settings.baseUrl}/$endpoint"
            val json = bodyAdapter.toJson(body)
            println("[NexoApiClient] POST $url")
            println("[NexoApiClient] Body: ${json.take(500)}...")
            val requestBody = json.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                println("[NexoApiClient] Response ${resp.code}: ${responseBody.take(500)}")
                
                if (resp.isSuccessful) {
                    val data = responseAdapter.fromJson(responseBody) ?: throw Exception("Failed to parse response")
                    Result.success(data)
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message} - $responseBody"))
                }
            }
        } catch (e: Exception) {
            println("[NexoApiClient] POST error: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Specific API endpoints matching Android app (using /api/mobile/ endpoints)
    
    // Cache bootstrap response to avoid multiple calls
    private var cachedBootstrap: BootstrapSyncResponse? = null
    private var bootstrapCacheTime: Long = 0
    private val CACHE_TTL = 30000L // 30 seconds cache (refresh takes 5+ seconds)
    
    /**
     * Bootstrap sync - get all data at once (products, customers, payment methods)
     * Cached for 5 seconds to avoid multiple calls during refresh
     */
    suspend fun bootstrapSync(forceRefresh: Boolean = false): Result<BootstrapSyncResponse> {
        val now = System.currentTimeMillis()
        
        // Return cached if valid
        if (!forceRefresh && cachedBootstrap != null && (now - bootstrapCacheTime) < CACHE_TTL) {
            println("[NexoApiClient] Using cached bootstrap data")
            return Result.success(cachedBootstrap!!)
        }
        
        val adapter = getAdapter<BootstrapSyncResponse>()
        val result = get("api/mobile/sync/bootstrap", adapter)
        
        if (result.isSuccess) {
            cachedBootstrap = result.getOrNull()
            bootstrapCacheTime = now
        }
        
        return result
    }
    
    fun clearCache() {
        cachedBootstrap = null
        bootstrapCacheTime = 0
    }
    
    suspend fun getCustomers(): Result<List<Customer>> {
        val bootstrap = bootstrapSync()
        return if (bootstrap.isSuccess) {
            val customers = bootstrap.getOrNull()?.customers ?: emptyList()
            println("[NexoApiClient] Got ${customers.size} customers from bootstrap")
            Result.success(customers)
        } else {
            Result.failure(bootstrap.exceptionOrNull() ?: Exception("Failed to fetch customers"))
        }
    }
    
    suspend fun getProducts(): Result<List<Product>> {
        val bootstrap = bootstrapSync()
        return if (bootstrap.isSuccess) {
            val mobileProducts = bootstrap.getOrNull()?.products ?: emptyList()
            println("[NexoApiClient] Got ${mobileProducts.size} products from bootstrap")
            // Convert MobileProduct to Product
            val products = mobileProducts.map { mp ->
                Product(
                    id = mp.id,
                    name = mp.name,
                    barcode = mp.barcode,
                    barcodeType = mp.barcodeType,
                    sku = mp.sku,
                    status = mp.status,
                    categoryId = mp.categoryId,
                    unitQuantities = mp.unitQuantities
                )
            }
            Result.success(products)
        } else {
            Result.failure(bootstrap.exceptionOrNull() ?: Exception("Failed to fetch products"))
        }
    }
    
    suspend fun getPaymentMethods(): Result<List<PaymentMethod>> {
        val bootstrap = bootstrapSync()
        return if (bootstrap.isSuccess) {
            val methods = bootstrap.getOrNull()?.paymentMethods ?: emptyList()
            println("[NexoApiClient] Got ${methods.size} payment methods from bootstrap")
            Result.success(methods)
        } else {
            Result.failure(bootstrap.exceptionOrNull() ?: Exception("Failed to fetch payment methods"))
        }
    }
    
    suspend fun searchByBarcode(barcode: String): Result<BarcodeSearchResult> {
        val adapter = getAdapter<BarcodeSearchResult>()
        return get("api/mobile/products/barcode/$barcode", adapter)
    }
    
    suspend fun createOrder(order: CreateOrderRequest): Result<OrderResponse> {
        val bodyAdapter = getAdapter<CreateOrderRequest>()
        val responseAdapter = getAdapter<OrderResponse>()
        return post("api/orders", order, bodyAdapter, responseAdapter)
    }
    
    /**
     * Fetch orders using the mobile-optimized paginated API
     * @param cursor The cursor for pagination (null for first page)
     * @param limit Number of orders per page
     * @param customerFilter Optional customer name filter
     */
    suspend fun getMobileOrders(
        cursor: Long? = null,
        limit: Int = 20,
        customerFilter: String? = null
    ): Result<PaginatedOrdersResponse> {
        return try {
            val params = mutableListOf<String>()
            params.add("limit=$limit")
            cursor?.let { params.add("cursor=$it") }
            customerFilter?.takeIf { it.isNotBlank() }?.let { params.add("customer=$it") }
            
            val endpoint = if (params.isNotEmpty()) {
                "api/mobile/orders?${params.joinToString("&")}"
            } else {
                "api/mobile/orders"
            }
            
            val adapter = getAdapter<PaginatedOrdersResponse>()
            get(endpoint, adapter)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete an order from the server
     * @param orderId The server order ID to delete
     */
    suspend fun deleteOrder(orderId: Long): Result<Unit> {
        return try {
            val url = "${settings.baseUrl}/api/orders/$orderId"
            println("[NexoApiClient] DELETE $url")
            
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    println("[NexoApiClient] Order deleted successfully")
                    Result.success(Unit)
                } else {
                    println("[NexoApiClient] Delete failed: ${resp.code} ${resp.message}")
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            println("[NexoApiClient] Delete error: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Register management endpoints
    
    suspend fun getRegisters(): Result<List<Register>> {
        return try {
            println("[NexoApiClient] Parsing registers manually...")
            val adapter = getAdapter<List<Map<String, Any>>>()
            val result = get("api/cash-registers", adapter)
            
            result.map { registersList ->
                println("[NexoApiClient] Got ${registersList.size} registers to parse manually")
                registersList.map { registerMap ->
                    parseRegisterFromMap(registerMap)
                }
            }
        } catch (e: Exception) {
            println("[NexoApiClient] Failed to parse registers: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getRegister(id: Int): Result<Register> {
        val adapter = getAdapter<Register>()
        return get("api/cash-registers/$id", adapter)
    }
    
    suspend fun getUsedRegister(): Result<Register?> {
        return try {
            // First try to get the used register
            val adapter = getAdapter<Map<String, Any>>()
            val result = get("api/cash-registers/used", adapter)
            
            result.map { response ->
                if (response["status"] == "success") {
                    val data = response["data"] as? Map<*, *>
                    val registerMap = data?.get("register") as? Map<*, *>
                    if (registerMap != null) {
                        parseRegisterFromMap(registerMap)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            // If 403 or other error, return null (no register in use)
            println("[NexoApiClient] No register in use: ${e.message}")
            Result.success(null)
        }
    }
    
    suspend fun openRegister(registerId: Int, amount: Double, description: String = ""): Result<Register> {
        val body = mapOf(
            "amount" to amount,
            "description" to description
        )
        val bodyAdapter = getAdapter<Map<String, Any>>()
        val responseAdapter = getAdapter<RegisterResponse>()
        val result = post("api/cash-registers/open/$registerId", body, bodyAdapter, responseAdapter)
        
        return result.map { response ->
            response.data.register
        }
    }
    
    suspend fun closeRegister(registerId: Int, amount: Double, description: String = ""): Result<Register> {
        val body = mapOf(
            "amount" to amount,
            "description" to description
        )
        val bodyAdapter = getAdapter<Map<String, Any>>()
        val responseAdapter = getAdapter<RegisterResponse>()
        val result = post("api/cash-registers/close/$registerId", body, bodyAdapter, responseAdapter)
        
        return result.map { response ->
            if (response.status == "success") {
                response.data.register
            } else {
                throw Exception("Failed to close register: ${response.message}")
            }
        }
    }
    
    suspend fun cashIn(registerId: Int, amount: Double, description: String): Result<RegisterHistory> {
        val body = mapOf(
            "amount" to amount,
            "description" to description
        )
        val bodyAdapter = getAdapter<Map<String, Any>>()
        val responseAdapter = getAdapter<Map<String, Any>>()
        val result = post("api/cash-registers/register-cash-in/$registerId", body, bodyAdapter, responseAdapter)
        
        return result.map {
            // Return a dummy RegisterHistory since API doesn't return proper response
            RegisterHistory(
                id = 0,
                registerId = registerId,
                action = "register-cash-in",
                author = 0,
                value = amount,
                description = description,
                createdAt = "",  // Empty string for dummy objects
                updatedAt = ""   // Empty string for dummy objects
            )
        }
    }
    
    suspend fun cashOut(registerId: Int, amount: Double, description: String): Result<RegisterHistory> {
        val body = mapOf(
            "amount" to amount,
            "description" to description
        )
        val bodyAdapter = getAdapter<Map<String, Any>>()
        val responseAdapter = getAdapter<Map<String, Any>>()
        val result = post("api/cash-registers/register-cash-out/$registerId", body, bodyAdapter, responseAdapter)
        
        return result.map {
            // Return a dummy RegisterHistory since API doesn't return proper response
            RegisterHistory(
                id = 0,
                registerId = registerId,
                action = "register-cash-out",
                author = 0,
                value = -amount, // Negative for cash out
                description = description,
                createdAt = "",  // Empty string for dummy objects
                updatedAt = ""   // Empty string for dummy objects
            )
        }
    }
    
    suspend fun getSessionHistory(registerId: Int): Result<List<RegisterHistory>> {
        return try {
            val adapter = getAdapter<Map<String, Any>>()
            val result = get("api/cash-registers/session-history/$registerId", adapter)
            
            result.map { response ->
                val historyList = response["history"] as? List<Map<String, Any>>
                historyList?.map { historyMap ->
                    RegisterHistory(
                        id = (historyMap["id"] as? Number)?.toInt() ?: 0,
                        registerId = (historyMap["register_id"] as? Number)?.toInt() ?: 0,
                        paymentId = (historyMap["payment_id"] as? Number)?.toInt(),
                        paymentTypeId = (historyMap["payment_type_id"] as? Number)?.toInt() ?: 0,
                        orderId = (historyMap["order_id"] as? Number)?.toInt(),
                        action = historyMap["action"] as? String ?: "",
                        author = (historyMap["author"] as? Number)?.toInt() ?: 0,
                        value = (historyMap["value"] as? Number)?.toDouble() ?: 0.0,
                        description = historyMap["description"] as? String,
                        uuid = historyMap["uuid"] as? String,
                        balanceBefore = (historyMap["balance_before"] as? Number)?.toDouble() ?: 0.0,
                        transactionType = historyMap["transaction_type"] as? String,
                        balanceAfter = (historyMap["balance_after"] as? Number)?.toDouble() ?: 0.0,
                        createdAt = historyMap["created_at"] as? String ?: "",
                        updatedAt = historyMap["updated_at"] as? String ?: ""
                    )
                } ?: emptyList()
            }
        } catch (e: Exception) {
            println("[NexoApiClient] Failed to get session history: ${e.message}")
            Result.success(emptyList())
        }
    }
    
    private fun parseRegisterFromMap(map: Map<*, *>): Register {
        return Register(
            id = (map["id"] as? Number)?.toInt() ?: 0,
            name = map["name"] as? String ?: "",
            description = map["description"] as? String,
            status = map["status"] as? String ?: "closed",
            usedBy = (map["used_by"] as? Number)?.toInt(),
            balance = (map["balance"] as? Number)?.toDouble() ?: 0.0,
            author = (map["author"] as? Number)?.toInt() ?: 0,
            uuid = map["uuid"] as? String,
            createdAt = map["created_at"] as? String ?: "",
            updatedAt = map["updated_at"] as? String ?: ""
        )
    }
    
    companion object {
        @Volatile
        private var instance: NexoApiClient? = null
        
        fun getInstance(): NexoApiClient {
            return instance ?: synchronized(this) {
                val settings = AppSettings.getInstance()
                instance ?: NexoApiClient(settings).also { instance = it }
            }
        }
    }
}
