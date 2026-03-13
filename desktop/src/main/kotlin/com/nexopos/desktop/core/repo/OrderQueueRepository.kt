package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.db.QueuedOrders
import com.nexopos.desktop.core.network.ServerOrder
import com.nexopos.shared.models.CreateOrderRequest
import com.nexopos.shared.models.OrderProductRequest
import com.nexopos.shared.models.OrderType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.text.SimpleDateFormat
import java.util.*

/**
 * Desktop OrderQueueRepository for managing local queued orders and syncing with server
 * Matches Android's OrderQueueRepository functionality
 */
class OrderQueueRepository {
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val orderAdapter = moshi.adapter(CreateOrderRequest::class.java)
    
    // Observable flow of all orders
    private val _ordersFlow = MutableStateFlow<List<QueuedOrderEntity>>(emptyList())
    val ordersFlow: Flow<List<QueuedOrderEntity>> = _ordersFlow.asStateFlow()
    
    // Debouncing mechanism for refreshFlow calls
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    
    @Volatile
    private var isInitialized = false
    private val initLock = Any()
    
    init {
        // Don't load initial data here - wait for first access
        // This allows migration to complete first
    }
    
    /**
     * Lazy initialization - ensures migration completes before first query
     * Thread-safe with double-checked locking
     * OPTIMIZED: Async initialization on IO dispatcher
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(initLock) {
                if (!isInitialized) {
                    // Launch async initialization on IO thread
                    GlobalScope.launch(Dispatchers.IO) {
                        refreshFlow()
                    }
                    isInitialized = true
                }
            }
        }
    }
    
    /**
     * Get all orders as a flow
     */
    fun observeAll(): Flow<List<QueuedOrderEntity>> {
        ensureInitialized()
        return ordersFlow
    }
    
    /**
     * TASK_MED_004: Paginated query for orders
     * @param limit Number of orders per page
     * @param offset Starting position
     */
    suspend fun getPage(limit: Int, offset: Int): List<QueuedOrderEntity> = withContext(Dispatchers.IO) {
        ensureInitialized()
        AppDatabase.query {
            QueuedOrders.selectAll()
                .orderBy(QueuedOrders.createdAt to SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row -> rowToEntity(row) }
        }
    }
    
    /**
     * TASK_MED_004: Get orders by status with pagination
     */
    suspend fun getPageByStatus(status: QueuedOrderStatus, limit: Int, offset: Int): List<QueuedOrderEntity> = withContext(Dispatchers.IO) {
        ensureInitialized()
        AppDatabase.query {
            QueuedOrders.selectAll()
                .andWhere { QueuedOrders.status eq status.name.lowercase() }
                .orderBy(QueuedOrders.createdAt to SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row -> rowToEntity(row) }
        }
    }
    
    /**
     * Get paginated orders from local database
     * @param offset Starting position (page * limit)
     * @param limit Number of orders to fetch (default 20)
     */
    suspend fun getPaged(offset: Int = 0, limit: Int = 20): Result<List<QueuedOrderEntity>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val orders = AppDatabase.query {
                QueuedOrders.selectAll()
                    .orderBy(QueuedOrders.createdAt to SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .map { row -> rowToEntity(row) }
            }
            Result.success(orders)
        } catch (e: Exception) {
            println("[OrderQueueRepository] getPaged error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * TASK_MED_004: Get total count for pagination logic
     */
    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) {
        ensureInitialized()
        AppDatabase.query {
            QueuedOrders.selectAll().count().toInt()
        }
    }
    
    /**
     * TASK_MED_004: Get count by status
     */
    suspend fun getCountByStatus(status: QueuedOrderStatus): Int = withContext(Dispatchers.IO) {
        ensureInitialized()
        AppDatabase.query {
            QueuedOrders.selectAll()
                .andWhere { QueuedOrders.status eq status.name.lowercase() }
                .count()
                .toInt()
        }
    }
    
    /**
     * Refresh the flow with latest data from database
     * OPTIMIZED: Loads only 20 most recent orders to prevent crashes
     * Use getPaged() for loading more orders
     */
    private suspend fun refreshFlow() = withContext(Dispatchers.IO) {
        try {
            val orders = AppDatabase.query {
                QueuedOrders.selectAll()
                    .orderBy(QueuedOrders.createdAt to SortOrder.DESC)
                    .limit(20) // FIXED: Load only 20 orders to prevent crash
                    .map { row -> rowToEntity(row) }
            }
            withContext(Dispatchers.Main) {
                _ordersFlow.value = orders
            }
            println("[OrderQueueRepository] Loaded ${orders.size} orders into flow")
        } catch (e: Exception) {
            println("[OrderQueueRepository] RefreshFlow error (migration may be in progress): ${e.message}")
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                _ordersFlow.value = emptyList()
            }
            // Still mark as initialized to avoid retry loops
            // Empty list is better than repeated errors
        }
    }
    
    /**
     * Schedule a debounced refresh
     * OPTIMIZED: Prevents excessive database queries by debouncing refresh calls
     */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = refreshScope.launch {
            delay(100) // Debounce 100ms
            refreshFlow()
        }
    }
    
    /**
     * Insert a new queued order
     * ROBUST: Validates order data and uses transaction
     */
    suspend fun insert(order: CreateOrderRequest, status: QueuedOrderStatus = QueuedOrderStatus.PENDING): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validation
            require(order.products.isNotEmpty()) { "Order must have at least one product" }
            require(order.total > 0) { "Order total must be positive" }
            
            val orderJson = orderAdapter.toJson(order)
            val clientRef = "POS-${System.currentTimeMillis()}"
            
            val orderId = AppDatabase.query {
                QueuedOrders.insert {
                    it[this.orderJson] = orderJson
                    it[this.status] = status.name.lowercase()
                    it[this.clientReference] = clientRef
                    it[createdAt] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                }[QueuedOrders.id].value
            }
            
            scheduleRefresh()
            Result.success(orderId)
        } catch (e: Exception) {
            println("[OrderQueueRepository] Insert failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get orders by server IDs
     * Used after syncing to load full order details from local DB
     */
    suspend fun getByServerIds(serverIds: List<Long>): List<QueuedOrderEntity> = withContext(Dispatchers.IO) {
        AppDatabase.query {
            QueuedOrders.selectAll()
                .andWhere { QueuedOrders.serverId inList serverIds }
                .orderBy(QueuedOrders.createdAt to SortOrder.DESC)
                .map { row -> rowToEntity(row) }
        }
    }
    
    /**
     * Update order status
     * ROBUST: Returns success count and uses transaction
     */
    suspend fun updateStatus(orderId: Long, newStatus: QueuedOrderStatus, serverId: Long? = null, serverCode: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val updated = AppDatabase.query {
                QueuedOrders.update({ QueuedOrders.id eq orderId }) {
                    it[status] = newStatus.name.lowercase()
                    it[this.serverId] = serverId
                    it[this.serverCode] = serverCode
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            
            scheduleRefresh()
            Result.success(updated)
        } catch (e: Exception) {
            println("[OrderQueueRepository] Update status failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Replace a queued order payload after editing it in POS.
     */
    suspend fun updateOrder(
        orderId: Long,
        order: CreateOrderRequest,
        status: QueuedOrderStatus = QueuedOrderStatus.PENDING
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val orderJson = orderAdapter.toJson(order)
            val updated = AppDatabase.query {
                QueuedOrders.update({ QueuedOrders.id eq orderId }) {
                    it[this.orderJson] = orderJson
                    it[this.status] = status.name.lowercase()
                    it[updatedAt] = System.currentTimeMillis()
                }
            }

            scheduleRefresh()
            Result.success(updated)
        } catch (e: Exception) {
            println("[OrderQueueRepository] Update order failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Delete order by ID
     * ROBUST: Returns success count
     */
    suspend fun deleteById(orderId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val deleted = AppDatabase.query {
                QueuedOrders.deleteWhere { QueuedOrders.id eq orderId }
            }
            
            scheduleRefresh()
            Result.success(deleted)
        } catch (e: Exception) {
            println("[OrderQueueRepository] Delete failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get order by server ID
     */
    suspend fun getByServerId(serverId: Long): QueuedOrderEntity? = withContext(Dispatchers.IO) {
        ensureInitialized()
        AppDatabase.query {
            QueuedOrders.selectAll()
                .andWhere { QueuedOrders.serverId eq serverId }
                .firstOrNull()
                ?.let { rowToEntity(it) }
        }
    }
    
    /**
     * Sync server orders while keeping phone-created orders untouched.
     * Only updates phone orders if they were updated on the server.
     * ROBUST: Uses transaction for atomic operation
     */
    suspend fun syncServerOrders(serverOrders: List<ServerOrder>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            println("[OrderQueueRepository] Syncing ${serverOrders.size} server orders")
            
            var syncedCount = 0
            
            // TASK_MED_003: Use transaction boundary for atomic sync
            AppDatabase.query {
                for (serverOrder in serverOrders) {
                    try {
                        // First check by serverId
                        var existingOrder = getByServerIdInternal(serverOrder.id)
                        
                        if (existingOrder != null) {
                            // Order exists locally - update if server version is newer
                            val serverUpdatedAt = try {
                                serverOrder.updatedAt?.let { dateFormat.parse(it)?.time } ?: 0L
                            } catch (e: Exception) {
                                0L
                            }
                            
                            if (serverUpdatedAt > existingOrder.updatedAt) {
                                // Server version is newer - update local
                                updateFromServerInternal(existingOrder.id, serverOrder)
                                syncedCount++
                            }
                        } else {
                            // New order from server - insert
                            insertFromServerInternal(serverOrder)
                            syncedCount++
                        }
                    } catch (e: Exception) {
                        println("[OrderQueueRepository] Failed to sync order ${serverOrder.id}: ${e.message}")
                        // Continue with next order
                    }
                }
            }
            
            scheduleRefresh()
            println("[OrderQueueRepository] Successfully synced $syncedCount orders")
            Result.success(syncedCount)
        } catch (e: Exception) {
            println("[OrderQueueRepository] Sync failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Internal method to get by server ID (without suspend)
     */
    private fun getByServerIdInternal(serverId: Long): QueuedOrderEntity? {
        return QueuedOrders.selectAll()
            .andWhere { QueuedOrders.serverId eq serverId }
            .firstOrNull()
            ?.let { rowToEntity(it) }
    }
    
    /**
     * Update existing order from server data (internal, within transaction)
     */
    private fun updateFromServerInternal(localId: Long, serverOrder: ServerOrder) {
        val orderRequest = serverOrderToRequest(serverOrder)
        val orderJson = orderAdapter.toJson(orderRequest)
        
        QueuedOrders.update({ QueuedOrders.id eq localId }) {
            it[this.orderJson] = orderJson
            it[status] = QueuedOrderStatus.SYNCED.name.lowercase()
            it[serverId] = serverOrder.id
            it[serverCode] = serverOrder.code
            it[paymentStatus] = serverOrder.paymentStatus
            it[isFromServer] = true
            it[updatedAt] = System.currentTimeMillis()
        }
    }
    
    /**
     * Insert new order from server (internal, within transaction)
     * BUGFIX: Use server's creation date, not sync time
     */
    private fun insertFromServerInternal(serverOrder: ServerOrder) {
        val orderRequest = serverOrderToRequest(serverOrder)
        val orderJson = orderAdapter.toJson(orderRequest)
        val clientRef = serverOrder.code ?: "SERVER-${serverOrder.id}"
        
        QueuedOrders.insert {
            it[this.orderJson] = orderJson
            it[status] = QueuedOrderStatus.SYNCED.name.lowercase()
            it[serverId] = serverOrder.id
            it[serverCode] = serverOrder.code
            it[this.clientReference] = clientRef
            it[paymentStatus] = serverOrder.paymentStatus
            it[isFromServer] = true
            it[createdAt] = parseServerDate(serverOrder.createdAt)  // Use server date!
            it[updatedAt] = System.currentTimeMillis()  // Sync time is OK for updatedAt
        }
    }
    
    /**
     * Parse server date string to timestamp
     * ROBUST: Handles multiple date formats with fallback
     * FIX: Now handles microseconds format from server: yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'
     */
    private fun parseServerDate(dateStr: String?): Long {
        if (dateStr == null) {
            println("[OrderQueueRepository] WARNING: Null date string, using current time")
            return System.currentTimeMillis()
        }
        
        return try {
            // Try server format with microseconds: "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"
            val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
            serverFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parsed = serverFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
            println("[OrderQueueRepository] Parsed date '$dateStr' -> ${java.util.Date(parsed)}")
            parsed
        } catch (e: Exception) {
            try {
                // Try standard format: "yyyy-MM-dd HH:mm:ss"
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val parsed = format.parse(dateStr)?.time ?: System.currentTimeMillis()
                println("[OrderQueueRepository] Parsed date '$dateStr' -> ${java.util.Date(parsed)}")
                parsed
            } catch (e2: Exception) {
                try {
                    // Try ISO format: "yyyy-MM-dd'T'HH:mm:ss"
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val parsed = isoFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                    println("[OrderQueueRepository] Parsed ISO date '$dateStr' -> ${java.util.Date(parsed)}")
                    parsed
                } catch (e3: Exception) {
                    println("[OrderQueueRepository] FAILED to parse date: '$dateStr'")
                    println("[OrderQueueRepository] Error: ${e3.message}")
                    val fallback = System.currentTimeMillis()
                    println("[OrderQueueRepository] Using fallback: ${java.util.Date(fallback)}")
                    fallback
                }
            }
        }
    }
    
    /**
     * Convert ServerOrder to CreateOrderRequest
     * Made public for OrdersViewModel to build full order details
     */
    fun serverOrderToCreateOrderRequest(serverOrder: ServerOrder): CreateOrderRequest {
        return serverOrderToRequest(serverOrder)
    }
    
    /**
     * Convert ServerOrder to CreateOrderRequest (internal)
     */
    private fun serverOrderToRequest(serverOrder: ServerOrder): CreateOrderRequest {
        val products = serverOrder.products?.map { p ->
            OrderProductRequest(
                productId = p.productId ?: 0L,
                name = p.name,
                quantity = p.quantity,
                unitQuantityId = p.unitQuantityId ?: 0L,
                unitId = p.unitId ?: 0L,
                unitName = p.unitName ?: "",
                unitPrice = p.unitPrice,
                totalPrice = p.totalPrice,
                totalPriceWithTax = p.totalPriceWithTax ?: p.totalPrice,
                taxValue = p.taxValue ?: 0.0,
                discount = p.discount ?: 0.0
            )
        } ?: emptyList()
        
        return CreateOrderRequest(
            customerId = serverOrder.customer?.id,
            totalProducts = products.size,
            products = products,
            payments = serverOrder.payments ?: emptyList(),
            customer = serverOrder.customer,
            type = OrderType(
                identifier = serverOrder.typeIdentifier ?: "takeaway",
                label = serverOrder.typeIdentifier ?: "Takeaway",
                selected = true
            ),
            subtotal = serverOrder.subtotal ?: serverOrder.total,
            total = serverOrder.total,
            tendered = serverOrder.tendered ?: 0.0,
            change = serverOrder.change ?: 0.0,
            discountAmount = serverOrder.discountAmount ?: 0.0,
            discountType = serverOrder.discountType ?: "none",
            discountPercentage = serverOrder.discountPercentage ?: 0.0,
            taxValue = serverOrder.taxValue ?: 0.0,
            paymentStatus = serverOrder.paymentStatus ?: "unpaid"
        )
    }
    
    /**
     * Convert database row to entity
     */
    private fun rowToEntity(row: ResultRow): QueuedOrderEntity {
        return QueuedOrderEntity(
            id = row[QueuedOrders.id].value,
            orderJson = row[QueuedOrders.orderJson],
            status = QueuedOrderStatus.valueOf(row[QueuedOrders.status].uppercase()),
            serverId = row[QueuedOrders.serverId],
            serverCode = row[QueuedOrders.serverCode],
            clientReference = row[QueuedOrders.clientReference],
            paymentStatus = row[QueuedOrders.paymentStatus],
            isFromServer = row[QueuedOrders.isFromServer],
            createdAt = row[QueuedOrders.createdAt],
            updatedAt = row[QueuedOrders.updatedAt]
        )
    }
    
    companion object {
        // Shared Moshi instance for optimal JSON parsing performance
        private val sharedMoshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        private val sharedAdapter = sharedMoshi.adapter(CreateOrderRequest::class.java)
        
        /**
         * Parse order JSON using singleton adapter
         * PERFORMANCE: Avoids creating new Moshi instance for each order
         */
        fun parseOrderJson(json: String): CreateOrderRequest {
            return sharedAdapter.fromJson(json)
                ?: throw IllegalStateException("Failed to parse order JSON")
        }
    }
}

/**
 * Queued order entity (matching Android's QueuedOrderEntity)
 */
data class QueuedOrderEntity(
    val id: Long,
    val orderJson: String,
    val status: QueuedOrderStatus,
    val serverId: Long?,
    val serverCode: String?,
    val clientReference: String,
    val paymentStatus: String?,
    val isFromServer: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Order status enum (matching Android's QueuedOrderStatus)
 */
enum class QueuedOrderStatus {
    PENDING,
    SYNCED,
    FAILED
}

/**
 * Convert QueuedOrderEntity to CreateOrderRequest
 * OPTIMIZED: Uses singleton Moshi instance from companion object
 */
fun QueuedOrderEntity.toRequest(): CreateOrderRequest {
    return OrderQueueRepository.parseOrderJson(orderJson)
}
