package com.nexopos.erp.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexopos.erp.feature.procurement.ProcurementOrder
import com.nexopos.erp.feature.procurement.ProcurementStatus

/**
 * Room entity for procurement orders.
 * Provides offline caching for procurement data.
 */
@Entity(tableName = "procurements")
data class ProcurementEntity(
    @PrimaryKey
    val id: Long,
    val providerId: Long,
    val providerName: String,
    val status: String,
    val totalAmount: Double,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expectedDelivery: Long?,
    val notes: String?,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert entity to domain model
     */
    fun toDomainModel(): ProcurementOrder {
        return ProcurementOrder(
            id = id,
            providerId = providerId,
            providerName = providerName,
            status = ProcurementStatus.valueOf(status.uppercase()),
            totalAmount = totalAmount,
            currency = currency,
            createdAt = createdAt,
            updatedAt = updatedAt,
            expectedDelivery = expectedDelivery,
            notes = notes
        )
    }

    companion object {
        /**
         * Convert domain model to entity
         */
        fun fromDomainModel(order: ProcurementOrder): ProcurementEntity {
            return ProcurementEntity(
                id = order.id,
                providerId = order.providerId,
                providerName = order.providerName,
                status = order.status.name,
                totalAmount = order.totalAmount,
                currency = order.currency,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
                expectedDelivery = order.expectedDelivery,
                notes = order.notes
            )
        }
    }
}

/**
 * Room entity for procurement line items.
 */
@Entity(tableName = "procurement_items", primaryKeys = ["procurementId", "productId"])
data class ProcurementItemEntity(
    val procurementId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val unitId: Long?,
    val unitName: String?,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
