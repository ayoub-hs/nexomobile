package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks sync metadata for each data type.
 * Used to determine when delta sync is needed vs full refresh.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    
    @ColumnInfo(name = "sync_token")
    val syncToken: String? = null,
    
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null,
    
    @ColumnInfo(name = "last_server_time")
    val lastServerTime: String? = null,
    
    @ColumnInfo(name = "item_count")
    val itemCount: Int = 0,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.NEVER_SYNCED
) {
    companion object {
        const val KEY_PRODUCTS = "products"
        const val KEY_CUSTOMERS = "customers"
        const val KEY_CATEGORIES = "categories"
        const val KEY_PAYMENT_METHODS = "payment_methods"
        const val KEY_BOOTSTRAP = "bootstrap"
    }
}

enum class SyncStatus {
    NEVER_SYNCED,
    SYNCED,
    SYNCING,
    FAILED,
    STALE
}
