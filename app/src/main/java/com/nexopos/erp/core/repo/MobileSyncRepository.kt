package com.nexopos.erp.core.repo

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.entities.SyncMetadataEntity
import com.nexopos.erp.core.db.toEntity
import com.nexopos.erp.core.network.BatchOrderRequest
import com.nexopos.erp.core.network.BatchOrderResponse
import com.nexopos.erp.core.network.BootstrapSyncResponse
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.network.DeltaSyncResponse
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.network.SyncStatusResponse
import com.nexopos.erp.core.prefs.SecureTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Unified repository for mobile-optimized sync operations.
 * 
 * Provides:
 * - Bootstrap sync for initial data load
 * - Delta sync for incremental updates
 * - Batch order submission
 * - Sync status tracking
 */
class MobileSyncRepository(
    context: Context,
    private val tokenStorage: SecureTokenStorage
) {
    private val appContext = context.applicationContext
    private val mobileApi: MobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)
    
    private val db = AppDatabase.get(appContext)
    private val productDao = db.productDao()
    private val categoryDao = db.categoryDao()
    private val customerDao = db.customerDao()
    private val paymentMethodDao = db.paymentMethodDao()

    private val syncMetadataRepo = SyncMetadataRepository(appContext)
    private val bootstrapLock = Any()
    @Volatile
    private var bootstrapInFlight: CompletableDeferred<Result<BootstrapSyncResponse>>? = null

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    companion object {
        private const val TAG = "MobileSyncRepository"
        private const val BOOTSTRAP_PAGE_SIZE = 500
    }

    // ========================================================================
    // SYNC STATE
    // ========================================================================

    sealed class SyncState {
        data object Idle : SyncState()
        data class Syncing(val type: SyncType, val progress: Float = 0f) : SyncState()
        data class Success(val type: SyncType, val itemCount: Int) : SyncState()
        data class Error(val type: SyncType, val message: String) : SyncState()
    }

    enum class SyncType {
        BOOTSTRAP,
        DELTA,
        PRODUCTS,
        CUSTOMERS,
        ORDERS
    }

    // ========================================================================
    // BOOTSTRAP SYNC
    // ========================================================================

    /**
     * Perform full bootstrap sync.
     * Downloads all products, categories, customers, and payment methods.
     * 
     * Call this:
     * - After first login
     * - When user requests full refresh
     * - When local cache is corrupted
     */
    suspend fun bootstrapSync(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<BootstrapSyncResponse> {
        var created = false
        val inFlight = synchronized(bootstrapLock) {
            bootstrapInFlight ?: CompletableDeferred<Result<BootstrapSyncResponse>>().also {
                bootstrapInFlight = it
                created = true
            }
        }

        if (!created) {
            Log.d(TAG, "Bootstrap sync already in progress - awaiting existing run")
            return inFlight.await()
        }

        val result = performBootstrapSync(onProgress)
        inFlight.complete(result)
        synchronized(bootstrapLock) {
            if (bootstrapInFlight === inFlight) {
                bootstrapInFlight = null
            }
        }
        return result
    }

    private suspend fun performBootstrapSync(
        onProgress: (processed: Int, total: Int) -> Unit
    ): Result<BootstrapSyncResponse> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing(SyncType.BOOTSTRAP, 0f)
        syncMetadataRepo.markSyncStarted(SyncMetadataEntity.KEY_BOOTSTRAP)

        runCatching {
            Log.d(TAG, "Starting bootstrap sync")

            val runTimestamp = System.currentTimeMillis()
            var cursor: String? = null
            var response: BootstrapSyncResponse
            var totalExpected = 0
            var processedCategories = 0
            var processedProducts = 0
            var processedCustomers = 0
            var processedPaymentMethods = 0

            do {
                response = mobileApi.bootstrapSync(
                    limit = BOOTSTRAP_PAGE_SIZE,
                    cursor = cursor
                )

                if (totalExpected == 0) {
                    response.meta?.counts?.let { counts ->
                        totalExpected = counts.categories +
                            counts.products +
                            counts.customers +
                            counts.paymentMethods
                    }
                }

                if (response.categories.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(
                        SyncType.BOOTSTRAP,
                        progressForBootstrap(
                            processedCategories + processedProducts + processedCustomers + processedPaymentMethods,
                            totalExpected
                        )
                    )
                    categoryDao.upsertAll(response.categories.map { it.toEntity(runTimestamp) })
                    processedCategories += response.categories.size
                }

                if (response.products.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(
                        SyncType.BOOTSTRAP,
                        progressForBootstrap(
                            processedCategories + processedProducts + processedCustomers + processedPaymentMethods,
                            totalExpected
                        )
                    )
                    productDao.upsertAll(response.products.map { it.toEntity(runTimestamp) })
                    processedProducts += response.products.size
                }

                if (response.customers.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(
                        SyncType.BOOTSTRAP,
                        progressForBootstrap(
                            processedCategories + processedProducts + processedCustomers + processedPaymentMethods,
                            totalExpected
                        )
                    )
                    customerDao.upsertAll(response.customers.map { it.toEntity(runTimestamp) })
                    processedCustomers += response.customers.size
                }

                if (response.paymentMethods.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(
                        SyncType.BOOTSTRAP,
                        progressForBootstrap(
                            processedCategories + processedProducts + processedCustomers + processedPaymentMethods,
                            totalExpected
                        )
                    )
                    paymentMethodDao.upsertAll(response.paymentMethods.map { it.toEntity(runTimestamp) })
                    processedPaymentMethods += response.paymentMethods.size
                }

                val processedItems = processedCategories +
                    processedProducts +
                    processedCustomers +
                    processedPaymentMethods
                val progressTotal = totalExpected.takeIf { it > 0 } ?: processedItems

                onProgress(processedItems, progressTotal)
                cursor = response.nextCursor
            } while (response.hasMore && cursor != null)

            if (response.hasMore && cursor == null) {
                throw IllegalStateException("Bootstrap sync response indicated more pages without a continuation cursor.")
            }

            productDao.deleteNotUpdatedAt(runTimestamp)
            customerDao.deleteNotUpdatedAt(runTimestamp)
            categoryDao.deleteNotUpdatedAt(runTimestamp)
            paymentMethodDao.deleteNotUpdatedAt(runTimestamp)
            productDao.clearAllCategoryProducts()

            val totalItems = processedCategories +
                processedProducts +
                processedCustomers +
                processedPaymentMethods

            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_BOOTSTRAP,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = totalItems
            )
            
            // Also update individual sync metadata
            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_PRODUCTS,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = processedProducts
            )
            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_CATEGORIES,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = processedCategories
            )
            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_CUSTOMERS,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = processedCustomers
            )
            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_PAYMENT_METHODS,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = processedPaymentMethods
            )
            
            onProgress(totalItems, totalItems)
            _syncState.value = SyncState.Success(SyncType.BOOTSTRAP, totalItems)
            Log.d(TAG, "Bootstrap sync complete: $totalItems items")
            
            response
        }.onFailure { e ->
            Log.e(TAG, "Bootstrap sync failed", e)
            syncMetadataRepo.markSyncFailed(SyncMetadataEntity.KEY_BOOTSTRAP)
            _syncState.value = SyncState.Error(SyncType.BOOTSTRAP, e.message ?: "Unknown error")
        }
    }

    private fun progressForBootstrap(processed: Int, total: Int): Float {
        if (total <= 0) {
            return 0f
        }

        return (processed.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
    }

    // ========================================================================
    // DELTA SYNC
    // ========================================================================

    /**
     * Perform incremental delta sync.
     * Only downloads items changed since last sync.
     */
    suspend fun deltaSync(): Result<DeltaSyncResponse> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing(SyncType.DELTA, 0f)

        runCatching {
            val syncToken = syncMetadataRepo.getSyncToken(SyncMetadataEntity.KEY_BOOTSTRAP)
            if (syncToken == null) {
                // No previous sync, need bootstrap
                throw IllegalStateException("No sync token found. Run bootstrap sync first.")
            }

            Log.d(TAG, "Starting delta sync with token $syncToken")
            val now = System.currentTimeMillis()
            var cursor: String? = null
            var response: DeltaSyncResponse
            var totalChanges = 0

            do {
                response = mobileApi.deltaSync(
                    since = syncToken,
                    cursor = cursor
                )

                // Apply product changes
                if (!response.products.isEmpty) {
                    _syncState.value = SyncState.Syncing(SyncType.DELTA, 0.3f)
                    applyProductDelta(response.products.created, response.products.updated, response.products.deletedIds, now)
                }

                // Apply customer changes
                if (!response.customers.isEmpty) {
                    _syncState.value = SyncState.Syncing(SyncType.DELTA, 0.6f)
                    applyCustomerDelta(response.customers.created, response.customers.updated, response.customers.deletedIds, now)
                }

                // Apply category changes
                if (!response.categories.isEmpty) {
                    _syncState.value = SyncState.Syncing(SyncType.DELTA, 0.8f)
                    applyCategoryDelta(response.categories.created, response.categories.updated, response.categories.deletedIds, now)
                }

                // Apply payment method changes
                if (!response.paymentMethods.isEmpty) {
                    _syncState.value = SyncState.Syncing(SyncType.DELTA, 0.9f)
                    applyPaymentMethodDelta(response.paymentMethods.created, response.paymentMethods.updated, response.paymentMethods.deletedIds, response.paymentMethods.deletedIdentifiers, now)
                }

                totalChanges += response.products.totalChanges +
                    response.customers.totalChanges +
                    response.categories.totalChanges +
                    response.paymentMethods.totalChanges

                cursor = response.nextCursor
            } while (response.hasMore && cursor != null)

            if (response.hasMore && cursor == null) {
                throw IllegalStateException("Delta sync response indicated more pages without a continuation cursor.")
            }

            syncMetadataRepo.markSyncCompleted(
                key = SyncMetadataEntity.KEY_BOOTSTRAP,
                syncToken = response.syncToken,
                serverTime = response.serverTime,
                itemCount = totalChanges
            )
            
            _syncState.value = SyncState.Success(SyncType.DELTA, totalChanges)
            Log.d(TAG, "Delta sync complete: $totalChanges changes")
            
            response
        }.onFailure { e ->
            Log.e(TAG, "Delta sync failed", e)
            _syncState.value = SyncState.Error(SyncType.DELTA, e.message ?: "Unknown error")
        }
    }

    private suspend fun applyProductDelta(
        created: List<MobileProduct>,
        updated: List<MobileProduct>,
        deletedIds: List<Long>,
        timestamp: Long
    ) {
        if (created.isNotEmpty()) {
            productDao.upsertAll(created.map { it.toEntity(timestamp) })
        }
        if (updated.isNotEmpty()) {
            productDao.upsertAll(updated.map { it.toEntity(timestamp) })
        }
        if (deletedIds.isNotEmpty()) {
            productDao.deleteByIds(deletedIds)
        }
    }

    private suspend fun applyCustomerDelta(
        created: List<com.nexopos.erp.core.network.Customer>,
        updated: List<com.nexopos.erp.core.network.Customer>,
        deletedIds: List<Long>,
        timestamp: Long
    ) {
        if (created.isNotEmpty()) {
            customerDao.upsertAll(created.map { it.toEntity(timestamp) })
        }
        if (updated.isNotEmpty()) {
            customerDao.upsertAll(updated.map { it.toEntity(timestamp) })
        }
        if (deletedIds.isNotEmpty()) {
            customerDao.deleteByIds(deletedIds)
        }
    }

    private suspend fun applyCategoryDelta(
        created: List<com.nexopos.erp.core.network.MobileCategory>,
        updated: List<com.nexopos.erp.core.network.MobileCategory>,
        deletedIds: List<Long>,
        timestamp: Long
    ) {
        if (created.isNotEmpty()) {
            categoryDao.upsertAll(created.map { it.toEntity(timestamp) })
        }
        if (updated.isNotEmpty()) {
            categoryDao.upsertAll(updated.map { it.toEntity(timestamp) })
        }
        if (deletedIds.isNotEmpty()) {
            categoryDao.deleteByIds(deletedIds)
        }
    }

    private suspend fun applyPaymentMethodDelta(
        created: List<com.nexopos.erp.core.network.PaymentMethod>,
        updated: List<com.nexopos.erp.core.network.PaymentMethod>,
        deletedIds: List<Long>,
        deletedIdentifiers: List<String>,
        timestamp: Long
    ) {
        if (created.isNotEmpty()) {
            paymentMethodDao.upsertAll(created.map { it.toEntity(timestamp) })
        }
        if (updated.isNotEmpty()) {
            paymentMethodDao.upsertAll(updated.map { it.toEntity(timestamp) })
        }
        // Delete payment methods using string identifiers
        if (deletedIdentifiers.isNotEmpty()) {
            paymentMethodDao.deleteByIdentifiers(deletedIdentifiers)
        }
    }

    // ========================================================================
    // SYNC STATUS CHECK
    // ========================================================================

    /**
     * Quick check if sync is needed without fetching data.
     */
    suspend fun checkSyncStatus(): Result<SyncStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            mobileApi.syncStatus()
        }
    }

    /**
     * Determine if we need to sync based on local state.
     */
    suspend fun needsSync(): Boolean {
        return syncMetadataRepo.needsBootstrap() || 
               syncMetadataRepo.hasStaleData(SyncMetadataRepository.DEFAULT_STALE_THRESHOLD_MS)
    }

    // ========================================================================
    // BATCH ORDER SUBMISSION
    // ========================================================================

    /**
     * Submit multiple orders in a single request.
     * More efficient than individual submissions for offline queue.
     */
    suspend fun submitOrderBatch(orders: List<CreateOrderRequest>): Result<BatchOrderResponse> = 
        withContext(Dispatchers.IO) {
            _syncState.value = SyncState.Syncing(SyncType.ORDERS, 0f)
            
            runCatching {
                val request = BatchOrderRequest(orders = orders)
                
                Log.d(TAG, "Submitting batch of ${orders.size} orders")
                val response = mobileApi.submitOrderBatch(request)
                
                _syncState.value = SyncState.Success(SyncType.ORDERS, response.successCount)
                Log.d(TAG, "Batch submit complete: ${response.successCount} success, ${response.failureCount} failed")
                
                response
            }.onFailure { e ->
                Log.e(TAG, "Batch order submission failed", e)
                _syncState.value = SyncState.Error(SyncType.ORDERS, e.message ?: "Unknown error")
            }
        }

    // ========================================================================
    // SMART SYNC
    // ========================================================================

    /**
     * Perform the appropriate sync based on current state.
     * - If never synced: bootstrap
     * - If synced before: delta
     */
    suspend fun smartSync(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (syncMetadataRepo.needsBootstrap()) {
                Log.d(TAG, "Performing bootstrap sync (first time or cache cleared)")
                bootstrapSync(onProgress).getOrThrow()
            } else {
                Log.d(TAG, "Performing delta sync")
                deltaSync().getOrThrow()
            }
            Unit
        }
    }

    /**
     * Force a full refresh, clearing existing data.
     */
    suspend fun forceFullRefresh(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<BootstrapSyncResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Forcing full refresh")
        syncMetadataRepo.clearAll()
        bootstrapSync(onProgress)
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
}
