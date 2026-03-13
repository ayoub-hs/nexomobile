package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_orders")
data class QueuedOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_reference") val clientReference: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    val status: QueuedOrderStatus = QueuedOrderStatus.PENDING,
    val error: String? = null,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "server_code") val serverCode: String? = null,
    @ColumnInfo(name = "payment_status") val paymentStatus: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
    @ColumnInfo(name = "is_from_server") val isFromServer: Boolean = false
)

enum class QueuedOrderStatus {
    PENDING,
    SYNCED,
    FAILED
}
