package com.nexopos.desktop.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Network models matching Android app's API responses.
 * Core entities delegate to shared models via :shared module.
 */

typealias Customer = com.nexopos.shared.models.Customer
typealias CustomerGroup = com.nexopos.shared.models.CustomerGroup
typealias PaymentMethod = com.nexopos.shared.models.PaymentMethod

typealias Product = com.nexopos.shared.models.Product
typealias UnitQuantity = com.nexopos.shared.models.UnitQuantity
typealias UnitDetail = com.nexopos.shared.models.UnitDetail

typealias OrderTypeRequest = com.nexopos.shared.models.OrderType
typealias CreateOrderRequest = com.nexopos.shared.models.CreateOrderRequest
typealias OrderProductRequest = com.nexopos.shared.models.OrderProductRequest
typealias OrderPaymentRequest = com.nexopos.shared.models.OrderPaymentRequest

@JsonClass(generateAdapter = true)
data class BarcodeSearchResult(
    val status: String,
    val message: String?,
    val data: Product?
)

// ============================================================================
// ORDER REQUEST MODELS (matching Android app exactly) - now provided by :shared
// ============================================================================

@JsonClass(generateAdapter = true)
data class OrderResponse(
    val status: String,
    val message: String?,
    val data: OrderData?
)

@JsonClass(generateAdapter = true)
data class OrderData(
    val id: Long?,
    val code: String?,
    val total: Double?,
    val subtotal: Double?,
    val tax_value: Double?,
    val discount: Double?
)

// ============================================================================
// BOOTSTRAP SYNC MODELS (matching Android /api/mobile/sync/bootstrap)
// ============================================================================

@JsonClass(generateAdapter = true)
data class BootstrapSyncResponse(
    val categories: List<MobileCategory>? = null,
    val products: List<MobileProduct>? = null,
    val customers: List<Customer>? = null,
    @Json(name = "payment_methods") val paymentMethods: List<PaymentMethod>? = null,
    @Json(name = "order_types") val orderTypes: List<OrderType>? = null,
    @Json(name = "sync_token") val syncToken: String? = null,
    @Json(name = "server_time") val serverTime: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileCategory(
    val id: Long,
    val name: String,
    val description: String? = null,
    @Json(name = "products_count") val productsCount: Int? = null,
    @Json(name = "display_order") val displayOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class MobileProduct(
    val id: Long,
    val name: String,
    val barcode: String? = null,
    @Json(name = "barcode_type") val barcodeType: String? = null,
    val sku: String? = null,
    val status: String? = null,
    @Json(name = "category_id") val categoryId: Long? = null,
    @Json(name = "unit_quantities") val unitQuantities: List<UnitQuantity>? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "deleted_at") val deletedAt: String? = null
)

typealias OrderType = com.nexopos.shared.models.OrderType

// ============================================================================
// SERVER ORDER MODELS (for order history/management)
// ============================================================================

@JsonClass(generateAdapter = true)
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

@JsonClass(generateAdapter = true)
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
    val discount: Double?
)

/**
 * Paginated response for mobile orders API
 */
@JsonClass(generateAdapter = true)
data class PaginatedOrdersResponse(
    val data: List<ServerOrder>,
    val meta: PaginationMeta
)

@JsonClass(generateAdapter = true)
data class PaginationMeta(
    @Json(name = "has_more") val hasMore: Boolean,
    @Json(name = "next_cursor") val nextCursor: Long?,
    @Json(name = "prev_cursor") val prevCursor: Long?,
    val limit: Int
)

// ============================================================================
// CONTAINER MANAGEMENT MODELS (matching Android /api/mobile/containers/*)
// ============================================================================

@JsonClass(generateAdapter = true)
data class ContainerType(
    val id: Long,
    val name: String,
    val capacity: Double,
    @Json(name = "capacity_unit") val capacityUnit: String,
    @Json(name = "deposit_fee") val depositFee: Double,
    val description: String?,
    @Json(name = "is_active") val isActive: Boolean,
    val inventory: ContainerInventory?
)

@JsonClass(generateAdapter = true)
data class ContainerInventory(
    val id: Long = 0,
    @Json(name = "container_type_id") val containerTypeId: Long = 0,
    @Json(name = "total_quantity") val totalQuantity: Int = 0,
    @Json(name = "available_quantity") val availableQuantity: Int = 0,
    @Json(name = "in_circulation") val inCirculation: Int = 0
)

@JsonClass(generateAdapter = true)
data class ContainerTypesResponse(
    val status: String?,
    val message: String? = null,
    val data: List<ContainerType>
)

@JsonClass(generateAdapter = true)
data class CustomerContainerBalance(
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "customer_name") val customerName: String,
    @Json(name = "container_type_id") val containerTypeId: Int,
    @Json(name = "container_type_name") val containerTypeName: String,
    @Json(name = "quantity_held") val quantityHeld: Int,
    @Json(name = "deposit_total") val depositTotal: Double,
    @Json(name = "last_transaction_at") val lastTransactionAt: String?
)

@JsonClass(generateAdapter = true)
data class CustomerContainerBalancesResponse(
    val status: String?,
    val message: String?,
    val data: List<CustomerContainerBalance>,
    val meta: ContainerBalancesMeta?
)

@JsonClass(generateAdapter = true)
data class ContainerBalancesMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    @Json(name = "has_more") val hasMore: Boolean
)

@JsonClass(generateAdapter = true)
data class ContainerReceiveRequest(
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "container_type_id") val containerTypeId: Long,
    val quantity: Int,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class ContainerMovementItem(
    val id: Long,
    @Json(name = "container_type_id") val containerTypeId: Int,
    @Json(name = "container_type_name") val containerTypeName: String,
    @Json(name = "customer_id") val customerId: Long?,
    @Json(name = "customer_name") val customerName: String?,
    val type: String,
    val quantity: Int,
    val notes: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "created_by") val createdBy: String?
)

@JsonClass(generateAdapter = true)
data class ContainerReceiveResponse(
    val status: String?,
    val message: String?,
    val data: ContainerMovementItem?
)
