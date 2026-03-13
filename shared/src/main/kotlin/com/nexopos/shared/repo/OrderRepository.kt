package com.nexopos.shared.repo

import com.nexopos.shared.models.CreateOrderRequest
import com.nexopos.shared.models.OrderType
import com.nexopos.shared.models.PaymentMethod

/**
 * Order repository interface for cross-platform order operations.
 * 
 * Platform implementations:
 * - Android: RoomOrderRepository (with offline queue support)
 * - Desktop: ExposedOrderRepository (direct network submission)
 * 
 * Strategy: Network-first for submissions, cache for history
 */
interface OrderRepository {
    
    /**
     * Create a new order on the server.
     * 
     * @param request Order creation request with products, payments, customer, etc.
     * @return Result with order response data or error
     */
    suspend fun createOrder(request: CreateOrderRequest): Result<OrderResponse>
    
    /**
     * Update an existing order on the server.
     * 
     * @param orderId Order ID to update
     * @param request Updated order data
     * @return Result with order response data or error
     */
    suspend fun updateOrder(orderId: Long, request: CreateOrderRequest): Result<OrderResponse>
    
    /**
     * Get available payment methods from local cache or network.
     * 
     * @return Result with list of payment methods or error
     */
    suspend fun getPaymentMethods(): Result<List<PaymentMethod>>
    
    /**
     * Refresh payment methods from network and update local cache.
     * 
     * @return Result indicating success or failure
     */
    suspend fun refreshPaymentMethods(): Result<Unit>
    
    /**
     * Get available order types (takeaway, dine-in, delivery, etc.).
     * 
     * @return Result with list of order types or error
     */
    suspend fun getOrderTypes(): Result<List<OrderType>>
}

/**
 * Order response data returned after successful order creation/update.
 */
data class OrderResponse(
    val orderId: Long,
    val orderCode: String,
    val total: Double,
    val status: String,
    val message: String? = null
)
