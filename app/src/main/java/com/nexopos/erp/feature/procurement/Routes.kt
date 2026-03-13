package com.nexopos.erp.feature.procurement

/**
 * Procurement Feature Routes
 * Navigation routes for the Procurement feature.
 * 
 * Flow: Procurement List → New Procurement | Procurement Detail
 */
object ProcurementRoutes {
    const val PROCUREMENTS = "procurements/list"
    const val NEW = "procurements/new"
    const val DETAIL = "procurements/{id}"
    
    /**
     * Create route for procurement detail screen
     */
    fun detail(id: Long) = "procurements/$id"
    
    // Legacy routes for backward compatibility during migration
    @Deprecated("Use PROCUREMENTS instead")
    const val PROCUREMENT_CREATE = "procurements/create"
    
    @Deprecated("Use DETAIL instead")
    const val PROCUREMENT_DETAIL = "procurements/{id}"
}
