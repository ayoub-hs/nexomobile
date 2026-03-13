package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey @ColumnInfo(name = "identifier") val identifier: String,
    val label: String?,
    val selected: Boolean?,
    @ColumnInfo(name = "is_readonly") val readonly: Boolean?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
