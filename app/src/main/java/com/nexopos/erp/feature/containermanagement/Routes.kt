package com.nexopos.erp.feature.containermanagement

/**
 * Container Management Feature Routes
 * Navigation routes for the Container Management feature.
 * 
 * Flow: Container Inventory → Container Movements (by type)
 */
object ContainerRoutes {
    const val INVENTORY = "containers/inventory"
    const val MOVEMENTS = "containers/movements/{typeId}"
    
    /**
     * Create route for container movements by type
     */
    fun movements(typeId: Long) = "containers/movements/$typeId"
    
    // Legacy routes for backward compatibility during migration
    @Deprecated("Use INVENTORY instead")
    const val CONTAINERS = "containers"
    
    @Deprecated("Use MOVEMENTS instead")
    const val CONTAINER_TRACKING = "containers/track/{id}"
    
    @Deprecated("Use INVENTORY with popup instead")
    const val CONTAINER_TRANSACTIONS = "containers/transactions"
}
