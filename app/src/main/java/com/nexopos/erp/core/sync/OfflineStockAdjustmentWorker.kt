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
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.dao.StockAdjustmentDao
import com.nexopos.erp.core.db.entities.QueuedStockAdjustmentStatus
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.network.StockAdjustmentRequest
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit

/**
 * Worker for syncing offline stock adjustments to the server.
 *
 * Follows the same pattern as OfflineOrderSyncWorker for consistency.
 * Processes queued stock adjustments when network becomes available.
 */
class OfflineStockAdjustmentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tokenStorage: SecureTokenStorage by lazy {
        KoinJavaComponent.get(SecureTokenStorage::class.java)
    }
    private val mobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)
    private val stockAdjustmentDao: StockAdjustmentDao = AppDatabase.get(appContext).stockAdjustmentDao()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    companion object {
        private const val TAG = "OfflineStockAdjustment"
        private const val WORK_NAME = "offline-stock-adjustment-sync"
        private const val BATCH_SIZE = 10
        private const val MAX_RETRIES = 3

        /**
         * Schedule the worker to sync pending stock adjustments.
         * Uses KEEP policy to avoid duplicate scheduling.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineStockAdjustmentWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, // 15 seconds initial backoff
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Force immediate sync (useful when network just became available)
         */
        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineStockAdjustmentWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting offline stock adjustment sync")
        var hasPartialFailure = false
        var totalSynced = 0
        var totalFailed = 0

        while (true) {
            // Fetch a batch of pending adjustments
            val batch = stockAdjustmentDao.getNextPendingBatch(BATCH_SIZE)
            if (batch.isEmpty()) {
                Log.d(TAG, "No more pending stock adjustments to sync")
                break
            }

            Log.d(TAG, "Processing batch of ${batch.size} stock adjustments")

            batch.forEach { entity ->
                try {
                    // Parse the payload and create request
                    val request = parseRequest(entity.payloadJson)
                    
                    // Attempt to sync with server
                    val response = mobileApi.adjustStock(request)
                    
                    if (response.status == "success" || response.data != null) {
                        // Mark as synced
                        stockAdjustmentDao.update(
                            entity.copy(
                                status = QueuedStockAdjustmentStatus.SYNCED,
                                serverId = response.data?.id,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        totalSynced++
                        Log.d(TAG, "Stock adjustment synced successfully: id=${entity.id}, productId=${entity.productId}")
                    } else {
                        // Server returned error
                        val errorMessage = response.message ?: "Unknown server error"
                        markFailed(entity, errorMessage)
                        hasPartialFailure = true
                        totalFailed++
                        Log.w(TAG, "Stock adjustment sync failed: id=${entity.id}, error=$errorMessage")
                    }
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Network error"
                    Log.e(TAG, "Exception syncing stock adjustment: id=${entity.id}", e)
                    
                    if (entity.attemptCount >= MAX_RETRIES - 1) {
                        markFailed(entity, errorMessage)
                        totalFailed++
                    } else {
                        // Increment attempt count for retry
                        stockAdjustmentDao.update(
                            entity.copy(
                                attemptCount = entity.attemptCount + 1,
                                lastAttemptAt = System.currentTimeMillis(),
                                error = errorMessage
                            )
                        )
                    }
                    hasPartialFailure = true
                }
            }
        }

        Log.d(TAG, "Sync completed: $totalSynced synced, $totalFailed failed")

        return@withContext if (hasPartialFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun markFailed(entity: com.nexopos.erp.core.db.entities.QueuedStockAdjustmentEntity, error: String) {
        stockAdjustmentDao.update(
            entity.copy(
                status = QueuedStockAdjustmentStatus.FAILED,
                error = error,
                lastAttemptAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun parseRequest(payloadJson: String): StockAdjustmentRequest {
        val adapter = moshi.adapter(StockAdjustmentRequest::class.java)
        return adapter.fromJson(payloadJson) ?: throw IllegalArgumentException("Failed to parse stock adjustment request")
    }
}
