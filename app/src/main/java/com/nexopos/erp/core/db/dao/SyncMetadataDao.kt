package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nexopos.erp.core.db.entities.SyncMetadataEntity
import com.nexopos.erp.core.db.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    fun observe(key: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAll(): List<SyncMetadataEntity>

    @Query("SELECT * FROM sync_metadata")
    fun observeAll(): Flow<List<SyncMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)

    @Query("UPDATE sync_metadata SET sync_status = :status WHERE `key` = :key")
    suspend fun updateStatus(key: String, status: SyncStatus)

    @Query("UPDATE sync_metadata SET last_sync_at = :timestamp, sync_token = :token, sync_status = :status WHERE `key` = :key")
    suspend fun markSynced(key: String, timestamp: Long, token: String?, status: SyncStatus = SyncStatus.SYNCED)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAll()

    @Query("SELECT last_sync_at FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncTime(key: String): Long?

    @Query("SELECT sync_token FROM sync_metadata WHERE `key` = :key")
    suspend fun getSyncToken(key: String): String?

    /**
     * Check if any sync is stale (older than threshold).
     */
    @Query("SELECT COUNT(*) > 0 FROM sync_metadata WHERE last_sync_at < :threshold OR last_sync_at IS NULL")
    suspend fun hasStaleData(threshold: Long): Boolean
}
