package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nexopos.erp.core.db.entities.QueuedPaymentStatus
import com.nexopos.erp.core.db.entities.QueuedTicketPaymentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for queued ticket payments.
 * Provides operations for offline payment queue management.
 */
@Dao
interface QueuedTicketPaymentDao {
    
    /**
     * Observe all queued payments
     */
    @Query("SELECT * FROM queued_ticket_payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QueuedTicketPaymentEntity>>
    
    /**
     * Observe payments by status
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE status = :status ORDER BY createdAt")
    fun observeByStatus(status: QueuedPaymentStatus): Flow<List<QueuedTicketPaymentEntity>>
    
    /**
     * Get payments by status
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE status = :status ORDER BY createdAt")
    suspend fun getByStatus(status: QueuedPaymentStatus): List<QueuedTicketPaymentEntity>
    
    /**
     * Get all queued payments
     */
    @Query("SELECT * FROM queued_ticket_payments ORDER BY createdAt DESC")
    suspend fun getAll(): List<QueuedTicketPaymentEntity>
    
    /**
     * Get a queued payment by ID
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QueuedTicketPaymentEntity?
    
    /**
     * Get queued payments for a specific ticket
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE ticketId = :ticketId ORDER BY createdAt DESC")
    suspend fun getByTicketId(ticketId: Long): List<QueuedTicketPaymentEntity>
    
    /**
     * Get pending payments for a specific ticket
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE ticketId = :ticketId AND status IN ('PENDING', 'FAILED') ORDER BY createdAt")
    suspend fun getPendingByTicketId(ticketId: Long): List<QueuedTicketPaymentEntity>
    
    /**
     * Get a payment by client reference
     */
    @Query("SELECT * FROM queued_ticket_payments WHERE clientReference = :clientReference LIMIT 1")
    suspend fun getByClientReference(clientReference: String): QueuedTicketPaymentEntity?
    
    /**
     * Insert a queued payment
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: QueuedTicketPaymentEntity): Long
    
    /**
     * Insert multiple queued payments
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<QueuedTicketPaymentEntity>)
    
    /**
     * Update a queued payment
     */
    @Update
    suspend fun update(payment: QueuedTicketPaymentEntity)
    
    /**
     * Delete a queued payment
     */
    @Delete
    suspend fun delete(payment: QueuedTicketPaymentEntity)
    
    /**
     * Delete a queued payment by ID
     */
    @Query("DELETE FROM queued_ticket_payments WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Delete all synced payments older than the specified timestamp
     */
    @Query("DELETE FROM queued_ticket_payments WHERE status = 'SYNCED' AND syncedAt < :timestamp")
    suspend fun deleteOldSynced(timestamp: Long)
    
    /**
     * Get count of pending payments
     */
    @Query("SELECT COUNT(*) FROM queued_ticket_payments WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
    
    /**
     * Get count of failed payments
     */
    @Query("SELECT COUNT(*) FROM queued_ticket_payments WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>
    
    /**
     * Get total pending amount
     */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM queued_ticket_payments WHERE status IN ('PENDING', 'SYNCING', 'FAILED')")
    fun getTotalPendingAmount(): Flow<Double>
    
    /**
     * Update payment status to syncing
     */
    @Query("UPDATE queued_ticket_payments SET status = 'SYNCING', lastSyncAttemptAt = :timestamp WHERE id = :id")
    suspend fun markSyncing(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Update payment status to synced
     */
    @Query("UPDATE queued_ticket_payments SET status = 'SYNCED', serverPaymentId = :serverId, syncedAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Update payment status to failed
     */
    @Query("UPDATE queued_ticket_payments SET status = 'FAILED', lastError = :error, retryCount = retryCount + 1, lastSyncAttemptAt = :timestamp WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Reset failed payments to pending for retry
     */
    @Query("UPDATE queued_ticket_payments SET status = 'PENDING', lastError = NULL WHERE status = 'FAILED' AND retryCount < :maxRetries")
    suspend fun resetFailedForRetry(maxRetries: Int = 3)
}
