package com.nexopos.erp.core.repo

import android.content.Context
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.entities.SyncMetadataEntity
import com.nexopos.erp.core.db.entities.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Repository for managing sync metadata.
 * Tracks when each data type was last synced and provides sync tokens for delta sync.
 */
class SyncMetadataRepository(context: Context) {
    private val appContext = context.applicationContext
    private val syncMetadataDao = AppDatabase.get(appContext).syncMetadataDao()

    companion object {
        // Default stale threshold: 24 hours
        val DEFAULT_STALE_THRESHOLD_MS = TimeUnit.HOURS.toMillis(24)
        
        // Quick stale threshold: 1 hour (for frequently changing data)
        val QUICK_STALE_THRESHOLD_MS = TimeUnit.HOURS.toMillis(1)
    }

    suspend fun get(key: String): SyncMetadataEntity? {
        return syncMetadataDao.get(key)
    }

    fun observe(key: String): Flow<SyncMetadataEntity?> {
        return syncMetadataDao.observe(key)
    }

    fun observeAll(): Flow<List<SyncMetadataEntity>> {
        return syncMetadataDao.observeAll()
    }

    suspend fun getLastSyncTime(key: String): Long? {
        return syncMetadataDao.getLastSyncTime(key)
    }

    suspend fun getSyncToken(key: String): String? {
        return syncMetadataDao.getSyncToken(key)
    }

    /**
     * Check if data is stale (needs refresh).
     * @param key The data type key
     * @param thresholdMs How old data can be before it's considered stale
     */
    suspend fun isStale(key: String, thresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS): Boolean {
        val lastSync = syncMetadataDao.getLastSyncTime(key) ?: return true
        val now = System.currentTimeMillis()
        return (now - lastSync) > thresholdMs
    }

    /**
     * Check if any data type has never been synced.
     */
    suspend fun needsBootstrap(): Boolean {
        val bootstrap = syncMetadataDao.get(SyncMetadataEntity.KEY_BOOTSTRAP)
        return bootstrap == null || bootstrap.syncStatus == SyncStatus.NEVER_SYNCED
    }

    /**
     * Mark sync as started.
     */
    suspend fun markSyncStarted(key: String) {
        val existing = syncMetadataDao.get(key)
        if (existing != null) {
            syncMetadataDao.updateStatus(key, SyncStatus.SYNCING)
        } else {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = key,
                    syncStatus = SyncStatus.SYNCING
                )
            )
        }
    }

    /**
     * Mark sync as completed successfully.
     */
    suspend fun markSyncCompleted(
        key: String,
        syncToken: String?,
        serverTime: String?,
        itemCount: Int
    ) {
        val now = System.currentTimeMillis()
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = key,
                syncToken = syncToken,
                lastSyncAt = now,
                lastServerTime = serverTime,
                itemCount = itemCount,
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    /**
     * Mark sync as failed.
     */
    suspend fun markSyncFailed(key: String) {
        syncMetadataDao.updateStatus(key, SyncStatus.FAILED)
    }

    /**
     * Clear all sync metadata (e.g., on logout).
     */
    suspend fun clearAll() {
        syncMetadataDao.deleteAll()
    }

    /**
     * Check if any data needs syncing based on threshold.
     */
    suspend fun hasStaleData(thresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS): Boolean {
        val threshold = System.currentTimeMillis() - thresholdMs
        return syncMetadataDao.hasStaleData(threshold)
    }
}
