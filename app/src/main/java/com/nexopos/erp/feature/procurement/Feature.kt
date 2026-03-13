package com.nexopos.erp.feature.procurement

import com.nexopos.erp.core.prefs.FeatureFlags

/**
 * Procurement Feature
 * 
 * Handles purchase orders, supplier management, and procurement workflows.
 * 
 * Required permissions:
 * - procurements.read
 * - procurements.create
 * - procurements.update
 * - providers.read
 * 
 * @see FeatureFlags for permission gating
 */
object ProcurementFeature {
    const val PERMISSION_READ = "procurements.read"
    const val PERMISSION_CREATE = "procurements.create"
    const val PERMISSION_APPROVE = "procurements.approve"
    const val PERMISSION_PROVIDERS = "providers.read"
    
    /**
     * Check if procurement feature is enabled for the current user.
     */
    fun isEnabled(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_READ)
    }
    
    /**
     * Check if user can create procurement orders.
     */
    fun canCreate(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_CREATE)
    }
    
    /**
     * Check if user can approve procurement orders.
     */
    fun canApprove(flags: FeatureFlags): Boolean {
        return flags.hasPermission(PERMISSION_APPROVE)
    }
}

/**
 * Procurement status types.
 */
enum class ProcurementStatus {
    DRAFT,
    PENDING,
    DELIVERED,
    STOCKED,
    APPROVED,
    ORDERED,
    PARTIAL,
    COMPLETED,
    CANCELLED
}

/**
 * Procurement order data class.
 */
data class ProcurementOrder(
    val id: Long,
    val providerId: Long,
    val providerName: String,
    val status: ProcurementStatus,
    val totalAmount: Double,
    val currency: String,
    val paymentStatus: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expectedDelivery: Long? = null,
    val notes: String? = null,
    val products: List<ProcurementLineItem> = emptyList(),
    val invoiceReference: String? = null,
    val invoiceDate: Long? = null
)

/**
 * Procurement line item for product details.
 */
data class ProcurementLineItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val receivedQuantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val unitId: Long?,
    val unitName: String?
) {
    /**
     * Calculate the remaining quantity to receive.
     */
    val remainingQuantity: Double
        get() = quantity - receivedQuantity
    
    /**
     * Check if this item is fully received.
     */
    val isFullyReceived: Boolean
        get() = receivedQuantity >= quantity
    
    /**
     * Check if this item is partially received.
     */
    val isPartiallyReceived: Boolean
        get() = receivedQuantity > 0 && receivedQuantity < quantity
}
