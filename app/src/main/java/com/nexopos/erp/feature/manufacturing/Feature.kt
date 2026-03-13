package com.nexopos.erp.feature.manufacturing

import com.nexopos.erp.core.prefs.FeatureFlags

/**
 * Manufacturing Feature
 * 
 * Handles production orders, bill of materials, and manufacturing workflows.
 * 
 * Required permissions:
 * - nexopos.read.manufacturing-orders
 * - nexopos.create.manufacturing-orders
 * - nexopos.read.production-boms
 * - nexopos.create.production-boms
 * 
 * @see FeatureFlags for permission gating
 */
object ManufacturingFeature {
    const val PERMISSION_READ = "nexopos.read.manufacturing-orders"
    const val PERMISSION_CREATE = "nexopos.create.manufacturing-orders"
    const val PERMISSION_BOM = "nexopos.read.production-boms"
    const val PERMISSION_CREATE_BOM = "nexopos.create.production-boms"
    
    /**
     * Feature flags configuration for manufacturing feature.
     */
    object FeatureFlags {
        fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_READ)
        }
        
        fun canCreateProduction(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_CREATE)
        }
        
        fun canReadBom(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_BOM)
        }
        
        fun canCreateBom(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_CREATE_BOM)
        }
    }
    
    /**
     * Check if manufacturing feature is enabled for the current user.
     */
    @Deprecated("Use FeatureFlags.isEnabled instead")
    fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
        return FeatureFlags.isEnabled(flags)
    }
    
    /**
     * Check if user can create production orders.
     */
    @Deprecated("Use FeatureFlags.canCreateProduction instead")
    fun canCreateProduction(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
        return FeatureFlags.canCreateProduction(flags)
    }
}

/**
 * Production order status types.
 * Matches backend ManufacturingOrder status values.
 */
enum class ProductionStatus(val value: String) {
    DRAFT("draft"),
    PLANNED("planned"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    ON_HOLD("on_hold");

    companion object {
        fun fromValue(value: String): ProductionStatus {
            return entries.find { it.value == value } ?: DRAFT
        }
    }
}

/**
 * Product data class (nested in ProductionOrder and BoM).
 * Note: Use com.nexopos.erp.core.network.ProductSummary from MobileApiModels.kt for API responses.
 */
data class ProductDto(
    val id: Int,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val unitId: Int?,
    val sellingPrice: Double?,
    val purchasePrice: Double?,
    val unitQuantities: List<com.nexopos.shared.models.UnitQuantity>? = null
)

/**
 * Type alias for ProductionOrder from MobileApiModels.kt
 * Use com.nexopos.erp.core.network.ProductionOrder for API responses.
 * This alias is provided for backward compatibility.
 */
typealias ProductionOrder = com.nexopos.erp.core.network.ProductionOrder

/**
 * Production Bill of Materials.
 * Note: Use com.nexopos.erp.core.network.ManufacturingBom from MobileApiModels.kt for API responses.
 */
data class ProductionBom(
    val id: Int,
    val productId: Int,
    val product: ProductDto? = null,
    val name: String,
    val description: String?,
    val items: List<BomItem> = emptyList()
)

/**
 * Bill of Materials item.
 * Note: Use com.nexopos.erp.core.network.BomItem from MobileApiModels.kt for API responses.
 */
data class BomItem(
    val id: Int,
    val productId: Int,
    val product: ProductDto? = null,
    val quantity: Double,
    val unitId: Int
)

/**
 * Production request for creating new production orders.
 */
data class ProductionRequest(
    val productId: Int,
    val quantity: Double,
    val unitId: Int,
    val notes: String? = null,
    val materials: List<BoMComponentRequest>? = null
)

/**
 * BoM component request.
 */
data class BoMComponentRequest(
    val productId: Int,
    val quantity: Double
)
