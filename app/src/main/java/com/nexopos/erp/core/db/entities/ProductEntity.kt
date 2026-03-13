package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["barcode"]),
        Index(value = ["sku"]),
        Index(value = ["name"])
    ]
    // Note: Foreign key to CategoryEntity removed to allow products to sync independently
    // category_id is nullable and handled by application logic
)
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val barcode: String?,
    @ColumnInfo(name = "barcode_type") val barcodeType: String?,
    val sku: String?,
    val status: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "unit_quantities_json") val unitQuantitiesJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)
