package com.nexopos.erp.feature.specialcustomer

/**
 * Customer Feature Routes
 * Navigation routes for the Special Customer feature.
 * 
 * Flow: Customer List → Customer Dashboard (with tabs for topups/tickets)
 */
object CustomerRoutes {
    const val CUSTOMERS = "customers/list"
    const val DASHBOARD = "customers/{customerId}"
    
    /**
     * Create route for customer dashboard
     */
    fun dashboard(customerId: Long) = "customers/$customerId"
}

/**
 * Legacy routes for backward compatibility during migration
 * @deprecated Use CustomerRoutes instead
 */
@Deprecated(message = "Use CustomerRoutes instead", replaceWith = ReplaceWith("CustomerRoutes"))
object SpecialCustomerRoutes {
    const val TICKETS = "special-customer-tickets"
    const val TICKET_DETAIL = "special-customer-ticket/{id}"
    const val TOPUPS = "special-customer-topups"
    const val TOPUP_NEW = "special-customer-topup/new"
    const val TOPUP_DETAIL = "special-customer-topup/{id}"
    
    // Customer-specific outstanding tickets with pay from wallet
    const val CUSTOMER_TICKETS = "special-customer/{customerId}/tickets"
    const val CUSTOMER_TICKETS_WITH_NAME = "special-customer/{customerId}/tickets?name={customerName}"
    
    /**
     * Create route for customer outstanding tickets
     */
    fun customerTickets(customerId: Long, customerName: String = ""): String {
        return if (customerName.isNotEmpty()) {
            "special-customer/$customerId/tickets?name=${java.net.URLEncoder.encode(customerName, "UTF-8")}"
        } else {
            "special-customer/$customerId/tickets"
        }
    }
}
