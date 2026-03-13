package com.nexopos.erp.feature.containermanagement

import com.nexopos.erp.core.prefs.FeatureFlags

/**
 * Container Management Feature
 * 
 * Handles container tracking, deposits, and returns for returnable containers.
 * 
 * Required permissions:
 * - nexopos.read.containers
 * - nexopos.create.containers
 * - nexopos.read.container-transactions
 * - nexopos.create.container-transactions
 * 
 * @see FeatureFlags for permission gating
 */
object ContainerManagementFeature {
    const val PERMISSION_READ = "nexopos.read.containers"
    const val PERMISSION_WRITE = "nexopos.create.containers"
    const val PERMISSION_TRANSACTIONS = "nexopos.read.container-transactions"
    const val PERMISSION_CREATE_TRANSACTION = "nexopos.create.container-transactions"
    
    /**
     * Feature flags configuration for container management feature.
     */
    object FeatureFlags {
        fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_READ)
        }
        
        fun canReadTransactions(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_TRANSACTIONS)
        }
        
        fun canCreateTransaction(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_CREATE_TRANSACTION)
        }
        
        fun canWrite(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
            return flags.hasPermission(PERMISSION_WRITE)
        }
    }
    
    /**
     * Check if container management feature is enabled for the current user.
     */
    @Deprecated("Use FeatureFlags.isEnabled instead")
    fun isEnabled(flags: com.nexopos.erp.core.prefs.FeatureFlags): Boolean {
        return FeatureFlags.isEnabled(flags)
    }
}

/**
 * Container status.
 * Matches backend ContainerInventory status values.
 */
enum class ContainerStatus(val value: String) {
    AVAILABLE("available"),
    RESERVED("reserved"),
    IN_USE("in_use"),
    RETURNED("returned");

    companion object {
        fun fromValue(value: String): ContainerStatus {
            return entries.find { it.value == value } ?: AVAILABLE
        }
    }
}

/**
 * Container transaction type.
 * Matches backend TransactionType values.
 */
enum class TransactionType(val value: String) {
    GIVE("give"),
    RECEIVE("receive"),
    ADJUST("adjust");

    companion object {
        fun fromValue(value: String): TransactionType {
            return entries.find { it.value == value } ?: ADJUST
        }
    }
}

/**
 * Container type data class.
 * Matches backend ContainerType structure.
 */
data class ContainerType(
    val id: Int,
    val name: String,
    val capacity: Double,
    val capacityUnit: String,
    val depositFee: Double,
    val description: String?,
    val isActive: Boolean
)

/**
 * Container data class.
 * Matches backend ContainerInventory structure.
 * Note: Containers do not have barcodes.
 */
data class Container(
    val id: Int,
    val containerTypeId: Int,
    val containerType: ContainerType? = null,
    val quantityOnHand: Int,
    val quantityReserved: Int,
    val availableQuantity: Int  // computed: on_hand - reserved
)

/**
 * Container transaction record.
 * Matches backend ContainerTransaction structure.
 */
data class ContainerTransaction(
    val id: Int,
    val containerTypeId: Int,
    val customerId: Int?,
    val type: TransactionType,
    val quantity: Int,
    val notes: String?,
    val createdAt: String
)
