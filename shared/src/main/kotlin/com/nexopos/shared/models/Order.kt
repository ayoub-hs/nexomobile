package com.nexopos.shared.models

import com.squareup.moshi.Json

/**
 * Shared order request/response models used across platforms.
 */

data class OrderType(
    val identifier: String,
    val label: String?,
    val icon: String? = null,
    val selected: Boolean? = null
)

/**
 * Product line item inside an order.
 */
data class OrderProductRequest(
    @Json(name = "product_id") val productId: Long?,
    val name: String,
    val quantity: Double,
    @Json(name = "unit_quantity_id") val unitQuantityId: Long?,
    @Json(name = "unit_id") val unitId: Long?,
    @Json(name = "unit_name") val unitName: String?,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "price_with_tax") val priceWithTax: Double? = null,
    @Json(name = "price_without_tax") val priceWithoutTax: Double? = null,
    @Json(name = "product_type") val productType: String = "product",
    val discount: Double = 0.0,
    @Json(name = "discount_type") val discountType: String? = null,
    @Json(name = "discount_percentage") val discountPercentage: Double? = null,
    @Json(name = "tax_group_id") val taxGroupId: Long? = null,
    @Json(name = "tax_type") val taxType: String? = null,
    @Json(name = "tax_value") val taxValue: Double? = null,
    @Json(name = "sale_tax_value") val saleTaxValue: Double? = null,
    @Json(name = "wholesale_tax_value") val wholesaleTaxValue: Double? = null,
    val rate: Double = 0.0,
    @Json(name = "total_price") val totalPrice: Double,
    @Json(name = "total_price_without_tax") val totalPriceWithoutTax: Double? = null,
    @Json(name = "total_price_with_tax") val totalPriceWithTax: Double? = null,
    @Json(name = "total_tax_value") val totalTaxValue: Double? = null,
    val mode: String = "normal",
    @Json(name = "container_tracking_enabled") val containerTrackingEnabled: Boolean? = null,
    @Json(name = "container_quantity_override") val containerQuantityOverride: Int? = null
)

/**
 * Payment line item inside an order.
 */
data class OrderPaymentRequest(
    val identifier: String,
    val value: Double,
    val label: String? = null,
    val selected: Boolean? = null,
    val readonly: Boolean? = null
)

/**
 * Create order request sent to the backend.
 */
data class CreateOrderRequest(
    val title: String? = "",
    val type: OrderType,
    @Json(name = "customer_id") val customerId: Long?,
    val customer: Customer?,
    val products: List<OrderProductRequest>,
    val payments: List<OrderPaymentRequest>,
    val subtotal: Double,
    val total: Double,
    val tendered: Double,
    val change: Double,
    @Json(name = "discount") val discountAmount: Double = 0.0,
    @Json(name = "discount_type") val discountType: String? = null,
    @Json(name = "discount_percentage") val discountPercentage: Double = 0.0,
    @Json(name = "total_products") val totalProducts: Int,
    @Json(name = "total_coupons") val totalCoupons: Double = 0.0,
    val coupons: List<Any> = emptyList(),
    val taxes: List<Any> = emptyList(),
    @Json(name = "tax_value") val taxValue: Double = 0.0,
    @Json(name = "products_tax_value") val productsTaxValue: Double = 0.0,
    @Json(name = "tax_group_id") val taxGroupId: Long? = null,
    @Json(name = "tax_type") val taxType: Any? = null,
    val shipping: Double = 0.0,
    @Json(name = "shipping_rate") val shippingRate: Double = 0.0,
    val note: String? = "",
    @Json(name = "note_visibility") val noteVisibility: String = "hidden",
    @Json(name = "payment_status") val paymentStatus: String = "paid",
    val instalments: List<Any> = emptyList(),
    val addresses: Map<String, Any?> = emptyMap(),
    @Json(name = "register_id") val register_id: Int? = null,
    @Json(name = "client_reference") val clientReference: String? = null
)
