package com.nexopos.erp.core.network

/**
 * DTOs for Inventory and Container API requests/responses.
 */

// ============ Inventory DTOs ============

/**
 * Request body for inventory adjustment
 */
data class InventoryAdjustmentRequest(
    val product_id: Long,
    val quantity: Int,
    val operation_type: String,  // "addition", "subtraction", "set"
    val reason: String? = null,
    val reference: String? = null
)

// ============ Container DTOs ============

/**
 * Container DTO from API
 */
data class ContainerDto(
    val id: Long?,
    val name: String?,
    val description: String?,
    val barcode: String?,
    val deposit_amount: Double?,
    val quantity_on_hand: Int?,
    val quantity_reserved: Int?,
    val available_quantity: Int?,
    val status: String?,
    val type: String?,  // "returnable", "consumable"
    val created_at: String?,
    val updated_at: String?
)

/**
 * Request body for container transaction (deposit/return)
 */
data class ContainerTransactionRequest(
    val container_id: Long,
    val customer_id: Long?,
    val transaction_type: String,  // "deposit", "return"
    val quantity: Int,
    val reference: String? = null
)

/**
 * Response for container transaction
 */
data class ContainerTransactionResponse(
    val id: Long?,
    val container_id: Long?,
    val customer_id: Long?,
    val transaction_type: String?,
    val quantity: Int?,
    val reference: String?,
    val created_at: String?
)
