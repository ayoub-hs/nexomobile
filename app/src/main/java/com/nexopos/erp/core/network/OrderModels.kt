package com.nexopos.erp.core.network

import com.squareup.moshi.Json
 
typealias Customer = com.nexopos.shared.models.Customer
typealias CustomerGroup = com.nexopos.shared.models.CustomerGroup
typealias PaymentMethod = com.nexopos.shared.models.PaymentMethod
typealias OrderProductRequest = com.nexopos.shared.models.OrderProductRequest
typealias OrderPaymentRequest = com.nexopos.shared.models.OrderPaymentRequest
typealias OrderType = com.nexopos.shared.models.OrderType
typealias CreateOrderRequest = com.nexopos.shared.models.CreateOrderRequest

data class PaymentField(
    val label: String?,
    val name: String,
    val options: List<PaymentOption>? = null,
    val value: String? = null,
    val type: String? = null
)

data class PaymentOption(
    val id: Long? = null,
    val label: String?,
    val identifier: String? = null,
    val value: Any? = null,
    val readonly: Int? = null,
    val selected: Boolean? = null,
    val priority: Int? = null,
    val description: String? = null
)
 
data class ApiStatusResponse<T>(
    val status: String?,
    val message: String?,
    val data: T?
)

data class OrderResponseData(
    val order: OrderSummary?
)

data class OrderSummary(
    val id: Long,
    val code: String?,
    val total: Double,
    @Json(name = "total_without_tax") val totalWithoutTax: Double?,
    @Json(name = "total_with_tax") val totalWithTax: Double?,
    @Json(name = "total_coupons") val totalCoupons: Double?,
    @Json(name = "tax_value") val taxValue: Double?,
    @Json(name = "payment_status") val paymentStatus: String?,
    val customer: Customer?
)

data class ServerOrder(
    val id: Long,
    val code: String?,
    val total: Double,
    val subtotal: Double?,
    @Json(name = "payment_status") val paymentStatus: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    val customer: Customer?,
    val products: List<ServerOrderProduct>?,
    val payments: List<OrderPaymentRequest>?,
    @Json(name = "type") val typeIdentifier: String?,
    val tendered: Double?,
    val change: Double?,
    @Json(name = "discount") val discountAmount: Double?,
    @Json(name = "discount_type") val discountType: String?,
    @Json(name = "discount_percentage") val discountPercentage: Double?,
    @Json(name = "tax_value") val taxValue: Double?
)

data class ServerOrderProduct(
    val id: Long?,
    @Json(name = "product_id") val productId: Long?,
    val name: String,
    val quantity: Double,
    @Json(name = "unit_quantity_id") val unitQuantityId: Long?,
    @Json(name = "unit_id") val unitId: Long?,
    @Json(name = "unit_name") val unitName: String?,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "total_price") val totalPrice: Double,
    @Json(name = "total_price_with_tax") val totalPriceWithTax: Double?,
    @Json(name = "tax_value") val taxValue: Double?,
    val discount: Double?,
    @Json(name = "container_tracking_enabled") val containerTrackingEnabled: Boolean?,
    @Json(name = "container_quantity_override") val containerQuantityOverride: Int?
)

/**
 * Paginated response for mobile orders API
 */
data class PaginatedOrdersResponse(
    val data: List<ServerOrder>,
    val meta: PaginationMeta
)

data class PaginationMeta(
    @Json(name = "has_more") val hasMore: Boolean,
    @Json(name = "next_cursor") val nextCursor: Long?,
    @Json(name = "prev_cursor") val prevCursor: Long?,
    val limit: Int
)
