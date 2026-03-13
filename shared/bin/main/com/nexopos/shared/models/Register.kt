package com.nexopos.shared.models

import com.squareup.moshi.Json

data class Register(
    val id: Int,
    val name: String,
    val description: String? = null,
    val status: String, // "closed", "opened", "disabled"
    @Json(name = "used_by") val usedBy: Int? = null,
    val balance: Double = 0.0,
    val author: Int,
    val uuid: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

data class RegisterHistory(
    val id: Int,
    @Json(name = "register_id") val registerId: Int,
    @Json(name = "payment_id") val paymentId: Int? = null,
    @Json(name = "payment_type_id") val paymentTypeId: Int = 0,
    @Json(name = "order_id") val orderId: Int? = null,
    val action: String, // "register-opening", "register-closing", etc.
    val author: Int,
    val value: Double = 0.0,
    val description: String? = null,
    val uuid: String? = null,
    @Json(name = "balance_before") val balanceBefore: Double = 0.0,
    @Json(name = "transaction_type") val transactionType: String? = null, // "unchanged", "negative", "positive"
    @Json(name = "balance_after") val balanceAfter: Double = 0.0,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)
