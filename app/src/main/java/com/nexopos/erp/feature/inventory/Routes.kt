package com.nexopos.erp.feature.inventory

/**
 * Inventory Feature Routes
 * Navigation routes for the Inventory feature.
 * 
 * Stock adjustments are shown as popup/dialog, not as separate screen.
 */
object InventoryRoutes {
    const val PRODUCTS = "inventory/products"
    // Adjustment uses popup, no separate route
    
    // Legacy routes for backward compatibility during migration
    @Deprecated("Use PRODUCTS instead")
    const val INVENTORY = "inventory"
    
    @Deprecated("Use PRODUCTS with popup instead")
    const val STOCK_ADJUST = "inventory/adjust/{id}"
}
