package com.nexopos.erp.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: Long,
    val username: String?,
    val name: String?,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String?,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "is_default") val isDefault: Boolean?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
