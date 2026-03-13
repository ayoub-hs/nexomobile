package com.nexopos.erp.core.repo

import android.content.Context
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.toModel
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.NexoApi
import com.nexopos.erp.core.network.OrderSyncResponse
import com.nexopos.erp.core.network.PaginatedOrdersResponse
import com.nexopos.erp.core.network.PaymentData
import com.nexopos.erp.core.network.PaymentRequest
import com.nexopos.erp.core.network.ServerOrder
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.shared.models.CreateOrderRequest
import com.nexopos.shared.models.OrderType
import com.nexopos.shared.models.PaymentMethod
import com.nexopos.shared.repo.OrderRepository as IOrderRepository
import com.nexopos.shared.repo.OrderResponse
import kotlinx.coroutines.flow.first

/**
 * Android Order repository implementation.
 * Implements shared OrderRepository interface.
 */
class OrderRepository(
    context: Context,
    private val tokenStorage: SecureTokenStorage,
    private val settings: SettingsRepository,
    private val syncRepository: MobileSyncRepository = MobileSyncRepository(context.applicationContext, tokenStorage)
) : IOrderRepository {
    private val appContext = context.applicationContext
    private val api: NexoApi = ServiceLocator.api(appContext, tokenStorage)
    private val mobileApi: MobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)
    private val paymentMethodDao = AppDatabase.get(appContext).paymentMethodDao()

    private suspend fun baseUrl(): String = settings.baseUrlFlow.first()
    
    // ========================================================================
    // SHARED INTERFACE METHODS
    // ========================================================================

    /**
     * Create a new order.
     * Implements: OrderRepository.createOrder()
     */
    override suspend fun createOrder(request: CreateOrderRequest): Result<OrderResponse> = runCatching {
        val url = baseUrl() + "api/orders"
        val response = api.createOrder(url, request)
        OrderResponse(
            orderId = response.data?.order?.id ?: 0L,
            orderCode = response.data?.order?.code ?: "",
            total = response.data?.order?.total ?: 0.0,
            status = response.status ?: "success",
            message = response.message
        )
    }
    
    /**
     * Update an existing order.
     * Implements: OrderRepository.updateOrder()
     */
    override suspend fun updateOrder(orderId: Long, request: CreateOrderRequest): Result<OrderResponse> = runCatching {
        val url = baseUrl() + "api/orders/$orderId"
        val response = api.updateOrder(url, request)
        OrderResponse(
            orderId = response.data?.order?.id ?: orderId,
            orderCode = response.data?.order?.code ?: "",
            total = response.data?.order?.total ?: 0.0,
            status = response.status ?: "success",
            message = response.message
        )
    }
    
    /**
     * Get payment methods.
     * Implements: OrderRepository.getPaymentMethods()
     */
    override suspend fun getPaymentMethods(): Result<List<PaymentMethod>> {
        return listPaymentMethods(forceRefresh = false)
    }
    
    /**
     * Refresh payment methods from network.
     * Implements: OrderRepository.refreshPaymentMethods()
     */
    override suspend fun refreshPaymentMethods(): Result<Unit> {
        return listPaymentMethods(forceRefresh = true).map { Unit }
    }
    
    /**
     * Get available order types.
     * Implements: OrderRepository.getOrderTypes()
     */
    override suspend fun getOrderTypes(): Result<List<OrderType>> {
        // Android returns hardcoded order types for now
        return runCatching {
            listOf(
                OrderType(identifier = "takeaway", label = "Takeaway", selected = true),
                OrderType(identifier = "delivery", label = "Delivery", selected = false)
            )
        }
    }
    
    // ========================================================================
    // ANDROID-SPECIFIC METHODS
    // ========================================================================
    
    suspend fun listPaymentMethods(forceRefresh: Boolean = false): Result<List<PaymentMethod>> = runCatching {
        if (!forceRefresh) {
            val cached = paymentMethodDao.getAll()
            if (cached.isNotEmpty()) {
                return@runCatching cached.map { it.toModel() }
            }
        }

        syncRepository.smartSync().getOrThrow()
        val synced = paymentMethodDao.getAll()
        if (synced.isEmpty()) {
            throw IllegalStateException("No payment methods available after mobile sync.")
        }

        synced.map { it.toModel() }
    }.recoverCatching { throwable ->
        val fallback = paymentMethodDao.getAll()
        if (fallback.isNotEmpty()) fallback.map { it.toModel() } else throw throwable
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
    ): Result<PaginatedOrdersResponse> = runCatching {
        mobileApi.getOrders(
            cursor = cursor,
            limit = limit,
            customer = customerFilter?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Delete an order from the server
     * @param orderId The server ID of the order to delete
     */
    suspend fun deleteOrder(orderId: Long): Result<Unit> = runCatching {
        val url = baseUrl() + "api/orders/$orderId"
        val response = api.deleteOrder(url)
        if (!response.isSuccessful) {
            throw Exception("Delete failed: HTTP ${response.code()}")
        }
    }

    // ========================================================================
    // MOBILE API METHODS - Single Order & Delta Sync
    // ========================================================================

    /**
     * Get a single order by ID with full details.
     * Uses the mobile-optimized endpoint for efficient data retrieval.
     *
     * @param orderId The server ID of the order
     * @return Result containing the full order details
     */
    suspend fun getOrderById(orderId: Long): Result<ServerOrder> = runCatching {
        mobileApi.getOrderById(orderId).data
    }

    /**
     * Delta sync for orders - fetch orders changed since a timestamp.
     * Used for incremental synchronization to avoid fetching all orders.
     *
     * @param since Unix timestamp in seconds for incremental sync
     * @param cursor Optional pagination cursor for large result sets
     * @param limit Maximum number of orders to return (default 50)
     * @return Result containing the sync response with changed orders
     */
    suspend fun syncOrders(
        since: Long,
        cursor: Long? = null,
        limit: Int = 50
    ): Result<OrderSyncResponse> = runCatching {
        mobileApi.syncOrders(since = since, cursor = cursor, limit = limit)
    }

    /**
     * Add a payment to an existing order.
     *
     * @param orderId The server ID of the order
     * @param payment The payment details (identifier, value, etc.)
     * @return Result containing the payment response
     */
    suspend fun addPayment(orderId: Long, payment: PaymentRequest): Result<PaymentData> = runCatching {
        val url = baseUrl() + "api/orders/$orderId/payments"
        val response = api.addOrderPayment(url, payment)
        
        if (response.status == "success") {
            response.data ?: throw Exception("Payment response data is null")
        } else {
            throw Exception(response.message ?: "Payment failed")
        }
    }
}
