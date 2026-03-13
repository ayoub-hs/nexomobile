package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.db.PaymentMethods
import com.nexopos.desktop.core.db.QueuedOrders
import com.nexopos.desktop.core.network.*
import com.nexopos.shared.models.CreateOrderRequest
import com.nexopos.shared.models.OrderType
import com.nexopos.shared.models.PaymentMethod
import com.nexopos.shared.repo.OrderRepository as IOrderRepository
import com.nexopos.shared.repo.OrderResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*

/**
 * Desktop Order repository implementation using Exposed SQL framework.
 * Implements shared OrderRepository interface.
 * Handles order submission and queueing for offline support.
 */
class OrderRepository(private val api: NexoApiClient) : IOrderRepository {
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    /**
     * Create a new order on the server.
     * Implements: OrderRepository.createOrder()
     * If offline, queue for later sync.
     */
    override suspend fun createOrder(request: CreateOrderRequest): Result<OrderResponse> = withContext(Dispatchers.IO) {
        try {
            // Try to submit to server
            val result = api.createOrder(request)
            
            if (result.isSuccess) {
                // Convert network response to shared OrderResponse
                val networkResponse = result.getOrNull()
                if (networkResponse != null) {
                    Result.success(OrderResponse(
                        orderId = networkResponse.data?.id ?: 0L,
                        orderCode = networkResponse.data?.code ?: "",
                        total = networkResponse.data?.total ?: 0.0,
                        status = networkResponse.status ?: "success",
                        message = networkResponse.message
                    ))
                } else {
                    queueOrder(request)
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                // Failed - queue for offline sync
                queueOrder(request)
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to submit order"))
            }
        } catch (e: Exception) {
            // Network error - queue for offline sync
            queueOrder(request)
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing order on the server.
     * Implements: OrderRepository.updateOrder()
     */
    override suspend fun updateOrder(orderId: Long, request: CreateOrderRequest): Result<OrderResponse> = withContext(Dispatchers.IO) {
        try {
            val result = api.updateOrder(orderId, request)

            if (result.isSuccess) {
                val networkResponse = result.getOrNull()
                if (networkResponse != null) {
                    Result.success(OrderResponse(
                        orderId = networkResponse.data?.id ?: orderId,
                        orderCode = networkResponse.data?.code ?: "",
                        total = networkResponse.data?.total ?: request.total,
                        status = networkResponse.status ?: "success",
                        message = networkResponse.message
                    ))
                } else {
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to update order"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get available payment methods from local cache.
     * Implements: OrderRepository.getPaymentMethods()
     */
    override suspend fun getPaymentMethods(): Result<List<PaymentMethod>> = withContext(Dispatchers.IO) {
        try {
            val methods = AppDatabase.query {
                PaymentMethods.selectAll().map { row ->
                    PaymentMethod(
                        identifier = row[PaymentMethods.identifier],
                        label = row[PaymentMethods.label] ?: row[PaymentMethods.identifier],
                        selected = row[PaymentMethods.selected],
                        readonly = row[PaymentMethods.readonly]
                    )
                }
            }
            Result.success(methods)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Refresh payment methods from network.
     * Implements: OrderRepository.refreshPaymentMethods()
     */
    override suspend fun refreshPaymentMethods(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = api.getPaymentMethods()
            if (result.isSuccess) {
                val methods = result.getOrNull() ?: emptyList()
                savePaymentMethods(methods)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch payment methods"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get available order types.
     * Implements: OrderRepository.getOrderTypes()
     */
    override suspend fun getOrderTypes(): Result<List<OrderType>> = withContext(Dispatchers.IO) {
        // Desktop returns hardcoded order types for now
        try {
            Result.success(listOf(
                OrderType(identifier = "takeaway", label = "Takeaway", selected = true),
                OrderType(identifier = "delivery", label = "Delivery", selected = false)
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // PRIVATE HELPERS & OFFLINE QUEUE SUPPORT
    // ========================================================================
    
    /**
     * Queue order for later sync (offline support)
     */
    private fun queueOrder(order: com.nexopos.shared.models.CreateOrderRequest) {
        val orderJson = moshi.adapter(com.nexopos.shared.models.CreateOrderRequest::class.java).toJson(order)
        val clientRef = "POS-${System.currentTimeMillis()}"
        
        AppDatabase.query {
            QueuedOrders.insert {
                it[this.orderJson] = orderJson
                it[status] = "pending"
                it[this.clientReference] = clientRef
                it[createdAt] = System.currentTimeMillis()
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Get pending orders count
     */
    fun getPendingOrdersCount(): Int {
        return AppDatabase.query {
            QueuedOrders.selectAll()
                .andWhere { QueuedOrders.status eq "pending" }
                .count()
                .toInt()
        }
    }
    
    /**
     * Retry failed orders
     */
    suspend fun retryFailedOrders(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pendingOrders: List<ResultRow> = AppDatabase.query {
                QueuedOrders.selectAll()
                    .andWhere { QueuedOrders.status eq "pending" }
                    .toList()
            }
            
            var successCount = 0
            
            for (row in pendingOrders) {
                val orderJson = row[QueuedOrders.orderJson]
                val orderId = row[QueuedOrders.id].value
                
                val order = moshi.adapter(com.nexopos.shared.models.CreateOrderRequest::class.java).fromJson(orderJson)
                if (order != null) {
                    val result = api.createOrder(order)
                    if (result.isSuccess) {
                        // Mark as synced
                        AppDatabase.query {
                            QueuedOrders.update({ QueuedOrders.id eq orderId }) {
                                it[status] = "synced"
                                it[serverId] = result.getOrNull()?.data?.id
                                it[serverCode] = result.getOrNull()?.data?.code
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }
                        successCount++
                    }
                }
            }
            
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun savePaymentMethods(methods: List<com.nexopos.desktop.core.network.PaymentMethod>) {
        AppDatabase.query {
            methods.forEach { method ->
                PaymentMethods.replace {
                    it[identifier] = method.identifier
                    it[label] = method.label
                    it[selected] = method.selected
                    it[readonly] = method.readonly
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }
}
