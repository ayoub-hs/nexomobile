package com.nexopos.erp.core.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexopos.erp.core.db.toRequest
import com.nexopos.erp.core.network.BatchOrderRequest
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent

/**
 * Worker for syncing offline orders to the server.
 *
 * Uses batch sync for efficiency - submits multiple orders in a single API call
 * instead of individual requests. This is more efficient for users with multiple
 * offline orders and reduces network overhead.
 */
class OfflineOrderSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tokenStorage: SecureTokenStorage by lazy {
        KoinJavaComponent.get(SecureTokenStorage::class.java)
    }
    private val settings: SettingsRepository by lazy {
        KoinJavaComponent.get(SettingsRepository::class.java)
    }
    private val queueRepository = OrderQueueRepository(appContext)
    private val orderRepository = OrderRepository(appContext, tokenStorage, settings)
    private val mobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)

    companion object {
        private const val TAG = "OfflineOrderSyncWorker"
        private const val BATCH_SIZE = 10  // Number of orders per batch
        private const val WORK_NAME = "offline-order-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineOrderSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, // 15 seconds initial backoff
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting offline order batch sync")
        var hasPartialFailure = false
        var totalSynced = 0
        var totalFailed = 0

        while (true) {
            // Fetch a batch of pending orders
            val batch = queueRepository.nextPendingBatch()
            if (batch.isEmpty()) {
                Log.d(TAG, "No more pending orders to sync")
                break
            }

            // Limit batch size to prevent oversized requests
            val ordersToSync = batch.take(BATCH_SIZE)
            Log.d(TAG, "Processing batch of ${ordersToSync.size} orders")

            // Convert to requests for batch API
            val orderRequests = ordersToSync.map { it.toRequest() }
            
            try {
                // Use batch sync API
                val batchRequest = BatchOrderRequest(orders = orderRequests)
                val batchResponse = mobileApi.submitOrderBatch(batchRequest)
                
                Log.d(TAG, "Batch sync completed: ${batchResponse.successCount} success, ${batchResponse.failureCount} failed")
                
                // Process individual results
                batchResponse.results.forEachIndexed { index, result ->
                    val entity = ordersToSync.getOrNull(index) ?: return@forEachIndexed
                    
                    if (result.success) {
                        val serverId = result.order?.id ?: 0L
                        val serverCode = result.order?.code ?: ""
                        val paymentStatus = orderRequests[index].paymentStatus ?: "paid"
                        
                        if (serverId > 0) {
                            queueRepository.markSyncedWithServerData(
                                entity = entity,
                                serverId = serverId,
                                serverCode = serverCode,
                                paymentStatus = paymentStatus
                            )
                        } else {
                            queueRepository.markSynced(entity)
                        }
                        totalSynced++
                        Log.d(TAG, "Order synced successfully: clientRef=${result.clientReference}, serverId=$serverId")
                    } else {
                        val errorMessage = result.error ?: "Unknown error"
                        queueRepository.markRetry(entity, errorMessage)
                        hasPartialFailure = true
                        totalFailed++
                        Log.w(TAG, "Order sync failed: clientRef=${result.clientReference}, error=$errorMessage")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch sync failed, falling back to individual sync", e)
                
                // Fallback to individual sync if batch API fails
                ordersToSync.forEach { entity ->
                    val request = entity.toRequest()
                    val result = orderRepository.createOrder(request)
                    
                    if (result.isSuccess) {
                        val response = result.getOrNull()
                        if (response != null && response.orderId > 0) {
                            val paymentStatus = request.paymentStatus ?: "paid"
                            queueRepository.markSyncedWithServerData(
                                entity = entity,
                                serverId = response.orderId,
                                serverCode = response.orderCode,
                                paymentStatus = paymentStatus
                            )
                        } else {
                            queueRepository.markSynced(entity)
                        }
                        totalSynced++
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message
                        queueRepository.markRetry(entity, errorMessage)
                        hasPartialFailure = true
                        totalFailed++
                    }
                }
            }
        }

        Log.d(TAG, "Sync completed: $totalSynced synced, $totalFailed failed")
        
        if (hasPartialFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
