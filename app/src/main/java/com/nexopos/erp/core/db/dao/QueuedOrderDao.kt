package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nexopos.erp.core.db.entities.QueuedOrderEntity
import com.nexopos.erp.core.db.entities.QueuedOrderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedOrderDao {

    @Query("SELECT * FROM queued_orders WHERE status = :status ORDER BY created_at")
    fun observeByStatus(status: QueuedOrderStatus): Flow<List<QueuedOrderEntity>>

    @Query("SELECT * FROM queued_orders WHERE status = :status ORDER BY created_at")
    suspend fun getByStatus(status: QueuedOrderStatus): List<QueuedOrderEntity>

    @Query("SELECT * FROM queued_orders ORDER BY created_at DESC")
    fun observeAll(): Flow<List<QueuedOrderEntity>>
    
    /**
     * TASK_MED_004: Pagination support
     */
    @Query("SELECT * FROM queued_orders ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<QueuedOrderEntity>
    
    @Query("SELECT * FROM queued_orders WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPageByStatus(status: QueuedOrderStatus, limit: Int, offset: Int): List<QueuedOrderEntity>
    
    @Query("SELECT COUNT(*) FROM queued_orders")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM queued_orders WHERE status = :status")
    suspend fun getCountByStatus(status: QueuedOrderStatus): Int

    @Query("SELECT * FROM queued_orders ORDER BY created_at DESC")
    suspend fun getAll(): List<QueuedOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: QueuedOrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<QueuedOrderEntity>)

    @Update
    suspend fun update(order: QueuedOrderEntity)

    @Delete
    suspend fun delete(order: QueuedOrderEntity)

    @Query("DELETE FROM queued_orders WHERE status = :status")
    suspend fun deleteByStatus(status: QueuedOrderStatus)

    @Query("SELECT * FROM queued_orders WHERE server_id = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: Long): QueuedOrderEntity?

    @Query("SELECT * FROM queued_orders WHERE client_reference = :clientReference LIMIT 1")
    suspend fun getByClientReference(clientReference: String): QueuedOrderEntity?

    @Query("SELECT MAX(server_id) FROM queued_orders WHERE is_from_server = 1")
    suspend fun getMaxServerIdFromServer(): Long?

    @Query("SELECT * FROM queued_orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QueuedOrderEntity?

    @Query("DELETE FROM queued_orders WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * MED-003: Batch update orders atomically.
     */
    @Transaction
    suspend fun updateAll(orders: List<QueuedOrderEntity>) {
        orders.forEach { update(it) }
    }

    /**
     * MED-003: Upsert order - insert or update if exists by serverId.
     */
    @Transaction
    suspend fun upsertByServerId(order: QueuedOrderEntity) {
        val existing = order.serverId?.let { getByServerId(it) }
        if (existing != null) {
            update(order.copy(id = existing.id))
        } else {
            insert(order)
        }
    }
}
