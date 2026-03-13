package com.nexopos.erp.core.repo

import android.content.Context
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.entities.QueuedOrderEntity
import com.nexopos.erp.core.db.entities.QueuedOrderStatus
import com.nexopos.erp.core.db.toEntity
import com.nexopos.erp.core.db.toRequest
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.network.OrderProductRequest
import com.nexopos.erp.core.network.OrderType
import com.nexopos.erp.core.network.ServerOrder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class OrderQueueRepository(context: Context) {
    private val appContext = context.applicationContext
    private val orderDao = AppDatabase.get(appContext).queuedOrderDao()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(CreateOrderRequest::class.java)

    fun observePending(): Flow<List<CreateOrderRequest>> {
        return orderDao.observeByStatus(QueuedOrderStatus.PENDING).map { orders ->
            orders.map { it.toRequest() }
        }
    }

    fun observePendingCount(): Flow<Int> {
        return orderDao.observeByStatus(QueuedOrderStatus.PENDING).map { it.size }
    }

    fun observeFailedCount(): Flow<Int> {
        return orderDao.observeByStatus(QueuedOrderStatus.FAILED).map { it.size }
    }

    fun observeAll(): Flow<List<QueuedOrderEntity>> {
        return orderDao.observeAll()
    }

    suspend fun enqueue(request: CreateOrderRequest): Long {
        val reference = request.clientReference ?: UUID.randomUUID().toString()
        val entity = request.toEntity(reference, System.currentTimeMillis())
        return orderDao.insert(entity)
    }

    suspend fun markSynced(entity: QueuedOrderEntity) {
        orderDao.update(
            entity.copy(
                status = QueuedOrderStatus.SYNCED,
                lastAttemptAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markFailed(entity: QueuedOrderEntity, error: String?) {
        orderDao.update(
            entity.copy(
                status = QueuedOrderStatus.FAILED,
                lastAttemptAt = System.currentTimeMillis(),
                attemptCount = entity.attemptCount + 1,
                error = error
            )
        )
    }

    suspend fun markRetry(entity: QueuedOrderEntity, error: String?) {
        orderDao.update(
            entity.copy(
                status = QueuedOrderStatus.PENDING,
                lastAttemptAt = System.currentTimeMillis(),
                attemptCount = entity.attemptCount + 1,
                error = error
            )
        )
    }

    suspend fun retryFailed() {
        val failed = orderDao.getByStatus(QueuedOrderStatus.FAILED)
        if (failed.isEmpty()) return
        // MED-003: Batch update atomically
        val updated = failed.map { it.copy(
            status = QueuedOrderStatus.PENDING,
            lastAttemptAt = System.currentTimeMillis(),
            attemptCount = it.attemptCount + 1,
            error = null
        ) }
        orderDao.updateAll(updated)
    }

    suspend fun nextPendingBatch(limit: Int = 10): List<QueuedOrderEntity> {
        return orderDao.getByStatus(QueuedOrderStatus.PENDING).take(limit)
    }

    suspend fun delete(entity: QueuedOrderEntity) {
        orderDao.delete(entity)
    }

    suspend fun deleteById(id: Long) {
        orderDao.deleteById(id)
    }

    suspend fun getById(id: Long): QueuedOrderEntity? {
        return orderDao.getById(id)
    }

    suspend fun clearSynced() {
        orderDao.deleteByStatus(QueuedOrderStatus.SYNCED)
    }

    /**
     * Save a synced copy of an order after successful server submission.
     * Uses server ID and code to prevent duplicates on refresh.
     */
    suspend fun saveSyncedCopy(
        request: CreateOrderRequest,
        serverId: Long,
        serverCode: String?,
        paymentStatus: String? = null
    ) {
        // Use serverCode as clientReference so sync can find it
        val reference = serverCode ?: request.clientReference ?: UUID.randomUUID().toString()
        val entity = request.toEntity(reference, System.currentTimeMillis()).copy(
            status = QueuedOrderStatus.SYNCED,
            serverId = serverId,
            serverCode = serverCode,
            paymentStatus = paymentStatus
        )
        orderDao.insert(entity)
    }

    suspend fun markSyncedWithServerData(entity: QueuedOrderEntity, serverId: Long, serverCode: String?, paymentStatus: String?) {
        orderDao.update(
            entity.copy(
                status = QueuedOrderStatus.SYNCED,
                lastAttemptAt = System.currentTimeMillis(),
                serverId = serverId,
                serverCode = serverCode,
                paymentStatus = paymentStatus
            )
        )
    }

    suspend fun getByClientReference(clientReference: String): QueuedOrderEntity? {
        return orderDao.getByClientReference(clientReference)
    }

    suspend fun getByServerId(serverId: Long): QueuedOrderEntity? {
        return orderDao.getByServerId(serverId)
    }

    suspend fun getAllOrders(): List<QueuedOrderEntity> {
        return orderDao.getAll()
    }

    /**
     * Sync server orders while keeping phone-created orders untouched.
     * Only updates phone orders if they were updated on the server.
     */
    suspend fun syncServerOrders(serverOrders: List<ServerOrder>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        android.util.Log.d("OrderQueueRepository", "Syncing ${serverOrders.size} server orders")
        
        for (serverOrder in serverOrders) {
            // First check by serverId
            var existingOrder = orderDao.getByServerId(serverOrder.id)
            
            // If not found by serverId, check by serverCode (clientReference)
            // This handles the case where order was created online and saved locally before refresh
            if (existingOrder == null && serverOrder.code != null) {
                existingOrder = orderDao.getByClientReference(serverOrder.code)
                if (existingOrder != null) {
                    android.util.Log.d("OrderQueueRepository", "Found existing order by code: ${serverOrder.code}, updating serverId")
                }
            }
            
            if (existingOrder != null) {
                // Order exists - update it with server data
                val serverUpdatedAt = serverOrder.updatedAt?.let { 
                    runCatching { dateFormat.parse(it)?.time }.getOrNull() 
                }
                
                if (!existingOrder.isFromServer) {
                    // Phone-created order - update with server ID and payment status
                    val localUpdatedAt = existingOrder.updatedAt ?: existingOrder.lastAttemptAt
                    
                    // Always update serverId/serverCode if missing, and payment status if server is newer
                    val shouldUpdatePaymentStatus = serverUpdatedAt != null && 
                        (localUpdatedAt == null || serverUpdatedAt > localUpdatedAt)
                    
                    orderDao.update(
                        existingOrder.copy(
                            serverId = serverOrder.id,
                            serverCode = serverOrder.code,
                            paymentStatus = if (shouldUpdatePaymentStatus) serverOrder.paymentStatus else existingOrder.paymentStatus,
                            updatedAt = serverUpdatedAt ?: existingOrder.updatedAt
                        )
                    )
                } else {
                    // Server-originated order - update with latest server data
                    orderDao.update(
                        existingOrder.copy(
                            serverId = serverOrder.id,
                            serverCode = serverOrder.code,
                            paymentStatus = serverOrder.paymentStatus,
                            updatedAt = serverUpdatedAt,
                            payloadJson = createPayloadFromServerOrder(serverOrder)
                        )
                    )
                }
            } else {
                // New server order - insert it
                android.util.Log.d("OrderQueueRepository", "Inserting new server order: ${serverOrder.id} - ${serverOrder.code}")
                val createdAt = serverOrder.createdAt?.let { 
                    runCatching { dateFormat.parse(it)?.time }.getOrNull() 
                } ?: System.currentTimeMillis()
                
                val updatedAt = serverOrder.updatedAt?.let { 
                    runCatching { dateFormat.parse(it)?.time }.getOrNull() 
                }
                
                val entity = QueuedOrderEntity(
                    clientReference = serverOrder.code ?: "server-${serverOrder.id}",
                    payloadJson = createPayloadFromServerOrder(serverOrder),
                    createdAt = createdAt,
                    status = QueuedOrderStatus.SYNCED,
                    serverId = serverOrder.id,
                    serverCode = serverOrder.code,
                    paymentStatus = serverOrder.paymentStatus,
                    updatedAt = updatedAt,
                    isFromServer = true
                )
                orderDao.insert(entity)
            }
        }
        android.util.Log.d("OrderQueueRepository", "Sync complete")
    }

    private fun createPayloadFromServerOrder(serverOrder: ServerOrder): String {
        val products = serverOrder.products?.map { product ->
            OrderProductRequest(
                productId = product.productId,
                name = product.name,
                quantity = product.quantity,
                unitQuantityId = product.unitQuantityId,
                unitId = product.unitId,
                unitName = product.unitName,
                unitPrice = product.unitPrice,
                totalPrice = product.totalPrice,
                totalPriceWithTax = product.totalPriceWithTax,
                discount = product.discount ?: 0.0,
                containerTrackingEnabled = product.containerTrackingEnabled,
                containerQuantityOverride = product.containerQuantityOverride
            )
        } ?: emptyList()

        val typeIdentifier = serverOrder.typeIdentifier ?: "takeaway"
        val request = CreateOrderRequest(
            type = OrderType(
                identifier = typeIdentifier,
                label = typeIdentifier.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            ),
            customerId = serverOrder.customer?.id,
            customer = serverOrder.customer,
            products = products,
            payments = serverOrder.payments ?: emptyList(),
            subtotal = serverOrder.subtotal ?: serverOrder.total,
            total = serverOrder.total,
            tendered = serverOrder.tendered ?: serverOrder.total,
            change = serverOrder.change ?: 0.0,
            discountAmount = serverOrder.discountAmount ?: 0.0,
            discountType = serverOrder.discountType,
            discountPercentage = serverOrder.discountPercentage ?: 0.0,
            totalProducts = products.size,
            taxValue = serverOrder.taxValue ?: 0.0,
            paymentStatus = serverOrder.paymentStatus ?: "paid",
            clientReference = serverOrder.code
        )
        return requestAdapter.toJson(request)
    }
}
