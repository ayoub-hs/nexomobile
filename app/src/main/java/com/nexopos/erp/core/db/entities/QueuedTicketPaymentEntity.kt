package com.nexopos.erp.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status of a queued ticket payment.
 */
enum class QueuedPaymentStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

/**
 * Entity for queueing ticket payments when offline.
 * Following the same pattern as QueuedOrderEntity for consistency.
 */
@Entity(tableName = "queued_ticket_payments")
data class QueuedTicketPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * The ID of the ticket being paid
     */
    val ticketId: Long,
    
    /**
     * The ticket code for reference
     */
    val ticketCode: String,
    
    /**
     * Payment amount
     */
    val amount: Double,
    
    /**
     * Payment method identifier
     */
    val paymentMethod: String,
    
    /**
     * Client-generated unique reference for this payment
     */
    val clientReference: String,
    
    /**
     * Current sync status
     */
    val status: String = QueuedPaymentStatus.PENDING.name,
    
    /**
     * Number of sync attempts
     */
    val retryCount: Int = 0,
    
    /**
     * Last error message if sync failed
     */
    val lastError: String? = null,
    
    /**
     * Server-assigned payment ID after successful sync
     */
    val serverPaymentId: Long? = null,
    
    /**
     * Timestamp when this payment was created locally
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp of last sync attempt
     */
    val lastSyncAttemptAt: Long? = null,
    
    /**
     * Timestamp when successfully synced
     */
    val syncedAt: Long? = null
) {
    /**
     * Check if this payment can be retried
     */
    fun canRetry(maxRetries: Int = 3): Boolean {
        return status == QueuedPaymentStatus.FAILED.name && retryCount < maxRetries
    }
    
    /**
     * Check if this payment is pending sync
     */
    fun isPending(): Boolean {
        return status == QueuedPaymentStatus.PENDING.name || status == QueuedPaymentStatus.FAILED.name
    }
}
