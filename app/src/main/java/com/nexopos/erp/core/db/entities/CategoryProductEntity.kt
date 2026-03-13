package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "category_products",
    primaryKeys = ["category_id", "product_id"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["product_id"])
    ]
)
data class CategoryProductEntity(
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "position") val position: Int
)
