package com.nexopos.erp.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexopos.erp.feature.specialcustomer.TicketStatus

/**
 * Room entity for outstanding tickets (special customer module).
 * Provides offline caching for ticket data.
 */
@Entity(tableName = "outstanding_tickets")
data class OutstandingTicketEntity(
    @PrimaryKey
    val id: Int,
    val code: String,
    val customerId: Int,
    val customerName: String?,
    val customerEmail: String?,
    val customerPhone: String?,
    val total: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val paymentStatus: String,
    val createdAt: String,
    val dueDate: String?,
    val description: String?,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert entity to domain model
     */
    fun toDomainModel(): com.nexopos.erp.feature.specialcustomer.OutstandingTicket {
        return com.nexopos.erp.feature.specialcustomer.OutstandingTicket(
            id = id,
            code = code,
            customerId = customerId,
            customer = customerName?.let {
                com.nexopos.erp.feature.specialcustomer.CustomerDto(
                    id = customerId,
                    name = it,
                    email = customerEmail,
                    phone = customerPhone
                )
            },
            total = total,
            paidAmount = paidAmount,
            dueAmount = dueAmount,
            paymentStatus = TicketStatus.fromValue(paymentStatus),
            createdAt = createdAt,
            dueDate = dueDate,
            description = description
        )
    }

    companion object {
        /**
         * Convert domain model to entity
         */
        fun fromDomainModel(ticket: com.nexopos.erp.feature.specialcustomer.OutstandingTicket): OutstandingTicketEntity {
            return OutstandingTicketEntity(
                id = ticket.id,
                code = ticket.code,
                customerId = ticket.customerId,
                customerName = ticket.customer?.name,
                customerEmail = ticket.customer?.email,
                customerPhone = ticket.customer?.phone,
                total = ticket.total,
                paidAmount = ticket.paidAmount,
                dueAmount = ticket.dueAmount,
                paymentStatus = ticket.paymentStatus.value,
                createdAt = ticket.createdAt,
                dueDate = ticket.dueDate,
                description = ticket.description
            )
        }
    }
}
