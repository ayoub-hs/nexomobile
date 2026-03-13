package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached category data for offline access.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: Long,
    
    val name: String,
    
    val description: String?,
    
    @ColumnInfo(name = "products_count")
    val productsCount: Int = 0,
    
    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
