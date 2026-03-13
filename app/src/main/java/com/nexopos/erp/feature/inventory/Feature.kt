package com.nexopos.erp.feature.inventory

import com.nexopos.erp.core.prefs.FeatureFlags

/**
 * Inventory Management Feature
 * 
 * Provides inventory tracking, stock adjustments, and inventory reports.
 * 
 * Required permissions:
 * - products.read
 * - products.write
 * - inventory.read
 * - inventory.adjust
 * 
 * @see FeatureFlags for permission gating
 */
object InventoryFeature {
    const val PERMISSION_READ = "products.read"
    const val PERMISSION_WRITE = "products.write"
    const val PERMISSION_STOCK_ADJUST = "inventory.adjust"
    const val PERMISSION_INVENTORY_READ = "inventory.read"
    
    /**
     * Feature flags configuration for inventory feature.
     */
    object FeatureFlags {
        fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_READ) ||
                   flags.hasPermission(PERMISSION_WRITE) ||
                   flags.hasPermission(PERMISSION_INVENTORY_READ)
        }
        
        fun canAdjustStock(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_STOCK_ADJUST) ||
                   flags.hasPermission(PERMISSION_WRITE)
        }
        
        fun canReadInventory(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_READ) ||
                   flags.hasPermission(PERMISSION_INVENTORY_READ)
        }
    }
    
    /**
     * Check if inventory feature is enabled for the current user.
     */
    @Deprecated("Use FeatureFlags.isEnabled instead")
    fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
        return FeatureFlags.isEnabled(flags)
    }
}

/**
 * Inventory operation types.
 */
enum class InventoryOperationType {
    SET,
    ADD,
    DELETE,
    DEFECTIVE,
    LOST
}

/**
 * Stock status types for filtering.
 */
enum class StockStatus {
    ALL,
    NORMAL,
    LOW_STOCK,
    OUT_OF_STOCK
}

/**
 * Inventory adjustment request.
 */
data class InventoryAdjustment(
    val productId: Long,
    val quantity: Int,
    val operationType: InventoryOperationType,
    val reason: String? = null,
    val reference: String? = null
)

/**
 * Inventory item with stock information.
 */
data class InventoryItem(
    val productId: Long,
    val unitQuantityId: Long?,
    val productName: String,
    val sku: String,
    val categoryId: Long,
    val categoryName: String,
    val stockQuantity: Int,
    val lowStockThreshold: Int,
    val unitName: String,
    val barcode: String? = null,
    val imageUrl: String? = null
) {
    val stockStatus: StockStatus
        get() = when {
            stockQuantity <= 0 -> StockStatus.OUT_OF_STOCK
            stockQuantity <= lowStockThreshold -> StockStatus.LOW_STOCK
            else -> StockStatus.NORMAL
        }
    
    val isLowStock: Boolean
        get() = stockStatus == StockStatus.LOW_STOCK || stockStatus == StockStatus.OUT_OF_STOCK
}
