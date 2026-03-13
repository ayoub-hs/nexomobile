package com.nexopos.shared.models

import com.squareup.moshi.Json

/**
 * Shared customer-related models used by both Android and Desktop.
 */

data class Customer(
    val id: Long,
    val username: String?,
    val name: String? = null,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String?,
    val group: CustomerGroup? = null,
    @Json(name = "is_default") val isDefault: Boolean? = null
)

/**
 * Group of customers, typically used for pricing rules or discounts.
 */
data class CustomerGroup(
    val id: Long,
    val name: String?,
    @Json(name = "description") val description: String?
)

/**
 * Payment method definition shared across platforms.
 */
data class PaymentMethod(
    val identifier: String,
    val label: String?,
    val selected: Boolean? = null,
    val readonly: Boolean? = null
)
