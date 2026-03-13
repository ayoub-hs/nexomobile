package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for queueing stock adjustments when offline.
 * Following the same pattern as QueuedOrderEntity for consistency.
 */
@Entity(tableName = "queued_stock_adjustments")
data class QueuedStockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "unit_quantity_id") val unitQuantityId: Long?,
    @ColumnInfo(name = "adjustment_type") val adjustmentType: String, // "add" or "remove"
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "reference") val reference: String?, // Optional reference for tracking
    @ColumnInfo(name = "payload_json") val payloadJson: String, // Full payload for API
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    val status: QueuedStockAdjustmentStatus = QueuedStockAdjustmentStatus.PENDING,
    val error: String? = null,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null
)

enum class QueuedStockAdjustmentStatus {
    PENDING,
    SYNCED,
    FAILED
}
