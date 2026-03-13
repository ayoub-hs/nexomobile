package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nexopos.erp.core.db.entities.QueuedStockAdjustmentEntity
import com.nexopos.erp.core.db.entities.QueuedStockAdjustmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {

    @Query("SELECT * FROM queued_stock_adjustments WHERE status = :status ORDER BY created_at")
    fun observeByStatus(status: QueuedStockAdjustmentStatus): Flow<List<QueuedStockAdjustmentEntity>>

    @Query("SELECT * FROM queued_stock_adjustments WHERE status = :status ORDER BY created_at")
    suspend fun getByStatus(status: QueuedStockAdjustmentStatus): List<QueuedStockAdjustmentEntity>

    @Query("SELECT * FROM queued_stock_adjustments ORDER BY created_at DESC")
    fun observeAll(): Flow<List<QueuedStockAdjustmentEntity>>

    @Query("SELECT * FROM queued_stock_adjustments ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<QueuedStockAdjustmentEntity>

    @Query("SELECT * FROM queued_stock_adjustments WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPageByStatus(status: QueuedStockAdjustmentStatus, limit: Int, offset: Int): List<QueuedStockAdjustmentEntity>

    @Query("SELECT COUNT(*) FROM queued_stock_adjustments")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM queued_stock_adjustments WHERE status = :status")
    suspend fun getCountByStatus(status: QueuedStockAdjustmentStatus): Int

    @Query("SELECT * FROM queued_stock_adjustments ORDER BY created_at DESC")
    suspend fun getAll(): List<QueuedStockAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adjustment: QueuedStockAdjustmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(adjustments: List<QueuedStockAdjustmentEntity>)

    @Update
    suspend fun update(adjustment: QueuedStockAdjustmentEntity)

    @Delete
    suspend fun delete(adjustment: QueuedStockAdjustmentEntity)

    @Query("DELETE FROM queued_stock_adjustments WHERE status = :status")
    suspend fun deleteByStatus(status: QueuedStockAdjustmentStatus)

    @Query("SELECT * FROM queued_stock_adjustments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QueuedStockAdjustmentEntity?

    @Query("SELECT * FROM queued_stock_adjustments WHERE product_id = :productId ORDER BY created_at DESC")
    suspend fun getByProductId(productId: Long): List<QueuedStockAdjustmentEntity>

    @Query("DELETE FROM queued_stock_adjustments WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Get next batch of pending adjustments for sync worker.
     */
    @Query("SELECT * FROM queued_stock_adjustments WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    suspend fun getNextPendingBatch(limit: Int = 10): List<QueuedStockAdjustmentEntity>

    /**
     * Batch update adjustments atomically.
     */
    @Transaction
    suspend fun updateAll(adjustments: List<QueuedStockAdjustmentEntity>) {
        adjustments.forEach { update(it) }
    }

    /**
     * Delete all synced adjustments (for cleanup).
     */
    @Query("DELETE FROM queued_stock_adjustments WHERE status = 'SYNCED'")
    suspend fun deleteSynced(): Int
}
