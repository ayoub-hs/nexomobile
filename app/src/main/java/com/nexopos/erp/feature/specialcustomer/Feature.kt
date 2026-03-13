package com.nexopos.erp.feature.specialcustomer

import com.nexopos.erp.core.prefs.FeatureFlags

/**
 * Special Customer Feature
 * 
 * Handles special customer management and outstanding tickets.
 * Outstanding tickets are based on core Order with payment_status filter.
 * 
 * Required permissions:
 * - nexopos.read.customers
 * - nexopos.read.outstanding-tickets
 * - nexopos.create.outstanding-tickets
 * 
 * @see FeatureFlags for permission gating
 */
object SpecialCustomerFeature {
    const val PERMISSION_READ = "nexopos.read.customers"
    const val PERMISSION_OUTSTANDING = "nexopos.read.outstanding-tickets"
    const val PERMISSION_CREATE = "nexopos.create.outstanding-tickets"
    
    /**
     * Check if special customer feature is enabled for the current user.
     */
    fun isEnabled(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_READ)
    }
    
    /**
     * Check if user can view outstanding tickets.
     */
    fun canViewOutstandingTickets(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_OUTSTANDING)
    }
    
    /**
     * Check if user can create outstanding tickets.
     */
    fun canCreateTicket(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_CREATE)
    }
}

/**
 * Outstanding ticket status.
 * Based on core Order payment_status.
 */
enum class TicketStatus(val value: String) {
    UNPAID("unpaid"),
    PARTIALLY_PAID("partially_paid"),
    PAID("paid");

    companion object {
        fun fromValue(value: String): TicketStatus {
            return entries.find { it.value == value } ?: UNPAID
        }
    }
}

/**
 * Customer data class for nested customer info.
 */
data class CustomerDto(
    val id: Int,
    val name: String,
    val email: String?,
    val phone: String?
)

/**
 * Outstanding ticket data class.
 * Based on core Order with payment_status filter for unpaid/partially paid orders.
 */
data class OutstandingTicket(
    val id: Int,
    val code: String,
    val customerId: Int,
    val customer: CustomerDto? = null,
    val total: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val paymentStatus: TicketStatus,  // "unpaid" or "partially_paid"
    val createdAt: String,
    val dueDate: String?,
    val description: String? = null
)

/**
 * Special customer data class.
 */
data class SpecialCustomer(
    val id: Int,
    val name: String,
    val email: String?,
    val phone: String?,
    val priorityLevel: Int,
    val creditLimit: Double,
    val currentBalance: Double,
    val notes: String? = null
)
