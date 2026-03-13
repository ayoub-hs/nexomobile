package com.nexopos.erp.core.network

import com.squareup.moshi.Json

/**
 * Mobile-optimized API response models.
 * These models support bundled data and delta sync for offline-first architecture.
 */

// ============================================================================
// BOOTSTRAP SYNC - Initial full sync after login
// ============================================================================

/**
 * Response from /api/mobile/sync/bootstrap
 * Contains all data needed for initial app setup.
 */
data class BootstrapSyncResponse(
    val categories: List<MobileCategory>,
    val products: List<MobileProduct>,
    val customers: List<Customer>,
    @Json(name = "payment_methods") val paymentMethods: List<PaymentMethod>,
    @Json(name = "order_types") val orderTypes: List<OrderType>,
    @Json(name = "sync_token") val syncToken: String,
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "has_more") val hasMore: Boolean = false,
    @Json(name = "next_cursor") val nextCursor: String? = null,
    val meta: BootstrapSyncMeta? = null
)

data class BootstrapSyncMeta(
    @Json(name = "execution_time_ms") val executionTimeMs: Long? = null,
    val limit: Int? = null,
    val snapshot: String? = null,
    val counts: BootstrapCounts? = null
)

data class BootstrapCounts(
    val categories: Int = 0,
    val products: Int = 0,
    val customers: Int = 0,
    @Json(name = "payment_methods") val paymentMethods: Int = 0
)

/**
 * Category with embedded products for efficient loading.
 */
data class MobileCategory(
    val id: Long,
    val name: String,
    val description: String?,
    @Json(name = "products_count") val productsCount: Int,
    @Json(name = "display_order") val displayOrder: Int = 0
)

/**
 * Product with all variations and prices bundled.
 * Eliminates N+1 queries for unit quantities.
 */
data class MobileProduct(
    val id: Long,
    val name: String,
    val barcode: String?,
    @Json(name = "barcode_type") val barcodeType: String?,
    val sku: String?,
    val status: String?,
    @Json(name = "category_id") val categoryId: Long?,
    @Json(name = "unit_quantities") val unitQuantities: List<UnitQuantity>,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "deleted_at") val deletedAt: String? = null
)

// ============================================================================
// DELTA SYNC - Incremental updates since last sync
// ============================================================================

/**
 * Response from /api/mobile/sync/delta?since={timestamp}
 * Contains only changed data since the last sync.
 */
data class DeltaSyncResponse(
    val products: DeltaCollection<MobileProduct>,
    val customers: DeltaCollection<Customer>,
    val categories: DeltaCollection<MobileCategory>,
    @Json(name = "payment_methods") val paymentMethods: DeltaCollection<PaymentMethod>,
    @Json(name = "sync_token") val syncToken: String,
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "has_more") val hasMore: Boolean = false,
    @Json(name = "next_cursor") val nextCursor: String? = null
)

/**
 * Collection of items with separate lists for created/updated/deleted.
 */
data class DeltaCollection<T>(
    val created: List<T> = emptyList(),
    val updated: List<T> = emptyList(),
    @Json(name = "deleted_ids") val deletedIds: List<Long> = emptyList(),
    @Json(name = "deleted_identifiers") val deletedIdentifiers: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = created.isEmpty() && updated.isEmpty() && deletedIds.isEmpty() && deletedIdentifiers.isEmpty()
    val totalChanges: Int get() = created.size + updated.size + deletedIds.size + deletedIdentifiers.size
}

// ============================================================================
// CATEGORY PRODUCTS - Bundled category with products
// ============================================================================

/**
 * Response from /api/mobile/categories/{id}/products
 * Returns category info with all products and their variations.
 */
data class CategoryProductsResponse(
    val category: MobileCategory,
    val products: List<MobileProduct>,
    @Json(name = "last_updated") val lastUpdated: String
)

// ============================================================================
// PRODUCT SEARCH - Optimized search response
// ============================================================================

/**
 * Response from /api/mobile/products/search
 * Includes full product details with variations.
 */
data class ProductSearchResponse(
    val results: List<MobileProduct>,
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "search_time_ms") val searchTimeMs: Long? = null
)

// ============================================================================
// ORDER BATCH SYNC - Batch order submission
// ============================================================================

/**
 * Request for /api/mobile/orders/batch
 * Submit multiple offline orders in one request.
 */
data class BatchOrderRequest(
    val orders: List<CreateOrderRequest>
)

/**
 * Response from /api/mobile/orders/batch
 */
data class BatchOrderResponse(
    val results: List<BatchOrderResult>,
    @Json(name = "success_count") val successCount: Int,
    @Json(name = "failure_count") val failureCount: Int
)

data class BatchOrderResult(
    @Json(name = "client_reference") val clientReference: String,
    val success: Boolean,
    val order: OrderSummary?,
    val error: String?
)

// ============================================================================
// SYNC STATUS - Check what needs syncing
// ============================================================================

/**
 * Response from /api/mobile/sync/status
 * Quick check for pending changes without fetching data.
 */
data class SyncStatusResponse(
    @Json(name = "products_updated") val productsUpdated: Boolean,
    @Json(name = "customers_updated") val customersUpdated: Boolean,
    @Json(name = "categories_updated") val categoriesUpdated: Boolean,
    @Json(name = "last_product_update") val lastProductUpdate: String?,
    @Json(name = "last_customer_update") val lastCustomerUpdate: String?,
    @Json(name = "server_time") val serverTime: String
)

// ============================================================================
// REGISTER CONFIG - Register-specific settings
// ============================================================================

/**
 * Response from /api/mobile/register/config
 * Contains register-specific configuration.
 */
data class RegisterConfigResponse(
    @Json(name = "register_id") val registerId: Long?,
    @Json(name = "store_name") val storeName: String?,
    @Json(name = "store_address") val storeAddress: String?,
    @Json(name = "store_phone") val storePhone: String?,
    @Json(name = "currency_symbol") val currencySymbol: String,
    @Json(name = "currency_position") val currencyPosition: String,
    @Json(name = "tax_enabled") val taxEnabled: Boolean,
    @Json(name = "default_tax_rate") val defaultTaxRate: Double?,
    @Json(name = "receipt_header") val receiptHeader: String?,
    @Json(name = "receipt_footer") val receiptFooter: String?
)

// ============================================================================
// MANUFACTURING - Production Orders and BOMs
// ============================================================================

/**
 * Response for production orders list
 */
data class ManufacturingOrdersResponse(
    val data: List<ProductionOrder>,
    val meta: PaginationMeta
)

/**
 * Production Order model
 */
data class ProductionOrder(
    val id: Long,
    val code: String,
    @Json(name = "bom_id") val bomId: Long?,
    val bom: BomSummary?,
    @Json(name = "product_id") val productId: Long?,
    val product: ProductSummary?,
    @Json(name = "unit_id") val unitId: Long?,
    val unit: UnitSummary?,
    val quantity: Double,
    val status: String,
    @Json(name = "started_at") val startedAt: String?,
    @Json(name = "completed_at") val completedAt: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

data class BomSummary(
    val id: Long,
    val name: String
)

data class ProductSummary(
    val id: Long,
    val name: String,
    val sku: String?
)

data class ProviderSummary(
    val id: Long,
    val name: String
)

data class ProvidersResponse(
    val status: String? = null,
    val data: List<ProviderSummary> = emptyList()
)

data class UnitSummary(
    val id: Long,
    val name: String
)

/**
 * Create Production Order request
 */
data class CreateProductionOrderRequest(
    @Json(name = "bom_id") val bomId: Long,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "unit_id") val unitId: Long,
    val quantity: Double
)

/**
 * Response for single production order
 */
data class ProductionOrderResponse(
    val status: String,
    val data: ProductionOrder
)

/**
 * Response for BOMs list
 */
data class ManufacturingBomsResponse(
    val data: List<ManufacturingBom>,
    val meta: PaginationMeta
)

/**
 * Bill of Materials model
 */
data class ManufacturingBom(
    val id: Long,
    val uuid: String,
    val name: String,
    @Json(name = "product_id") val productId: Long?,
    val product: ProductSummary?,
    @Json(name = "unit_id") val unitId: Long?,
    val unit: UnitSummary?,
    val quantity: Double,
    @Json(name = "is_active") val isActive: Boolean,
    val description: String?,
    @Json(name = "items_count") val itemsCount: Int? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val items: List<BomItem>? = null
)

/**
 * BOM Item model
 */
data class BomItem(
    val id: Long,
    @Json(name = "product_id") val productId: Long?,
    val product: ProductSummary?,
    @Json(name = "component_product_id") val componentProductId: Long?,
    @Json(name = "component_product") val componentProduct: ProductSummary?,
    val quantity: Double,
    @Json(name = "unit_id") val unitId: Long?,
    val unit: UnitSummary?
)

/**
 * Response for single BOM
 */
data class BomResponse(
    val status: String,
    val data: ManufacturingBom
)

/**
 * Request to create a manufacturing BOM.
 */
data class CreateBomRequest(
    val name: String,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "unit_id") val unitId: Long,
    val quantity: Double,
    @Json(name = "is_active") val isActive: Boolean = true,
    val description: String? = null
)

// ============================================================================
// CONTAINER MANAGEMENT
// ============================================================================

/**
 * Container Type model
 */
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

/**
 * Container Inventory model
 */
data class ContainerInventory(
    val id: Long = 0,
    @Json(name = "container_type_id") val containerTypeId: Long = 0,
    @Json(name = "total_quantity") val totalQuantity: Int = 0,
    @Json(name = "available_quantity") val availableQuantity: Int = 0,
    @Json(name = "in_circulation") val inCirculation: Int = 0
)

/**
 * Response for container types
 */
data class ContainerTypesResponse(
    val status: String,
    val data: List<ContainerType>
)

/**
 * Response for container inventory
 */
data class ContainerInventoryResponse(
    val status: String,
    val data: List<ContainerInventory>
)

/**
 * Container adjustment request
 */
data class ContainerAdjustRequest(
    @Json(name = "container_type_id") val containerTypeId: Long,
    val adjustment: Int,
    val reason: String
)

/**
 * Container adjustment response
 */
data class ContainerAdjustResponse(
    val status: String,
    val message: String,
    val data: ContainerInventory
)

/**
 * Container receive request
 */
data class ContainerReceiveRequest(
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "container_type_id") val containerTypeId: Long,
    val quantity: Int,
    val note: String? = null
)

// ============================================================================
// SPECIAL CUSTOMER - Outstanding Tickets
// ============================================================================

/**
 * Response for outstanding tickets
 */
data class OutstandingTicketsResponse(
    val status: String,
    val data: List<OutstandingTicket>,
    val meta: TicketMeta
)

/**
 * Outstanding Ticket model
 */
data class OutstandingTicket(
    val id: Long,
    val code: String,
    @Json(name = "customer_id") val customerId: Long,
    val customer: TicketCustomer?,
    val total: Double,
    @Json(name = "paid_amount") val paidAmount: Double,
    @Json(name = "due_amount") val dueAmount: Double,
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "created_at") val createdAt: String
)

data class TicketCustomer(
    val id: Long,
    val name: String,
    val email: String?,
    val phone: String?
)

data class TicketMeta(
    val count: Int
)

/**
 * Response for single outstanding ticket
 */
data class OutstandingTicketResponse(
    val status: String,
    val data: OutstandingTicketDetail
)

/**
 * Outstanding Ticket with full details
 */
data class OutstandingTicketDetail(
    val id: Long,
    val code: String,
    @Json(name = "customer_id") val customerId: Long,
    val customer: TicketCustomer?,
    val total: Double,
    @Json(name = "paid_amount") val paidAmount: Double,
    @Json(name = "due_amount") val dueAmount: Double,
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "created_at") val createdAt: String,
    val payments: List<TicketPayment>?,
    val products: List<TicketProduct>?
)

data class TicketPayment(
    val id: Long,
    val identifier: String,
    val value: Double,
    @Json(name = "created_at") val createdAt: String
)

data class TicketProduct(
    val id: Long,
    val name: String,
    val quantity: Double,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "total_price") val totalPrice: Double
)

/**
 * Pay ticket request
 */
data class PayTicketRequest(
    val amount: Double,
    @Json(name = "payment_method") val paymentMethod: String,
    val reference: String?
)

/**
 * Pay ticket response
 */
data class PayTicketResponse(
    val status: String,
    val message: String,
    val data: PayTicketData
)

data class PayTicketData(
    @Json(name = "order_id") val orderId: Long,
    @Json(name = "amount_paid") val amountPaid: Double,
    @Json(name = "payment_method") val paymentMethod: String
)

/**
 * Pay ticket with method request (for wallet, cash, card, bank transfer)
 * Same format as webapp
 */
data class PayTicketWithMethodRequest(
    @Json(name = "order_id") val orderId: Long,
    @Json(name = "customer_id") val customerId: Long,
    val amount: Double,
    @Json(name = "payment_method") val paymentMethod: String,
    val reference: String? = null
)

// ============================================================================
// CATEGORY MODELS
// ============================================================================

/**
 * Response model for category list endpoint
 */
data class CategoryResponse(
    val id: Long,
    val name: String,
    val slug: String
)

// ============================================================================
// PRODUCT MODELS
// ============================================================================

/**
 * Response model for products list endpoint
 */
data class ProductsResponse(
    val status: String,
    val data: List<Product>,
    val meta: PaginationMeta
)

// ============================================================================
// UNIT MODELS
// ============================================================================

/**
 * Response model for units list endpoint
 */
data class UnitResponse(
    val id: Long,
    val name: String,
    val symbol: String?
)

// ============================================================================
// PROCUREMENT MODELS
// ============================================================================

/**
 * DTO for procurement order from API
 */
data class ProcurementOrderDto(
    val id: Long,
    @Json(name = "provider_id") val providerId: Long,
    @Json(name = "provider_name") val providerName: String?,
    val status: String,
    @Json(name = "total_amount") val totalAmount: Double?,
    val currency: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "expected_delivery") val expectedDelivery: String?,
    val notes: String?,
    val products: List<ProcurementProductDto>? = null
)

/**
 * DTO for procurement product line item
 */
data class ProcurementProductDto(
    val id: Long,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "product_name") val productName: String?,
    val quantity: Double,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "total_price") val totalPrice: Double?,
    @Json(name = "unit_id") val unitId: Long?
)

/**
 * Request to create a new procurement order
 */
data class CreateProcurementRequest(
    @Json(name = "provider_id") val providerId: Long,
    val products: List<ProcurementProductRequestDto>,
    val notes: String? = null,
    @Json(name = "expected_delivery") val expectedDelivery: String? = null,
    val name: String? = null,
    @Json(name = "invoice_reference") val invoiceReference: String? = null,
    @Json(name = "invoice_date") val invoiceDate: String? = null,
    val status: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null
)

/**
 * Request to update an existing procurement order
 */
data class UpdateProcurementRequest(
    val status: String? = null,
    val notes: String? = null,
    @Json(name = "expected_delivery") val expectedDelivery: String? = null,
    val products: List<ProcurementProductRequestDto>? = null,
    val name: String? = null,
    @Json(name = "invoice_reference") val invoiceReference: String? = null,
    @Json(name = "invoice_date") val invoiceDate: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null
)

/**
 * Product item for procurement request
 */
data class ProcurementProductRequestDto(
    @Json(name = "product_id") val productId: Long,
    val quantity: Double,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "unit_id") val unitId: Long? = null,
    @Json(name = "tax_type") val taxType: String? = null
)

/**
 * Response for procurement API calls (used by ProcurementRepository)
 */
data class ProcurementResponse(
    val id: Long,
    @Json(name = "provider_id") val providerId: Long,
    val provider: ProviderSummary?,
    val status: String,
    val total: Double?,
    val currency: String?,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "delivery_date") val deliveryDate: String?,
    @Json(name = "invoice_reference") val invoiceReference: String? = null,
    @Json(name = "invoice_date") val invoiceDate: String? = null,
    val description: String?,
    val products: List<ProcurementProductDto>? = null
)

/**
 * Response wrapper for procurement list
 */
data class ProcurementListResponse(
    val status: String?,
    val data: List<ProcurementResponse>
)

/**
 * Response wrapper for single procurement
 */
data class SingleProcurementResponse(
    val status: String?,
    val data: ProcurementResponse
)

/**
 * Request to receive procurement items (partial or full delivery)
 */
data class ReceiveProcurementRequest(
    val items: List<ReceiveProcurementItem>
)

/**
 * Individual item for receiving procurement products
 */
data class ReceiveProcurementItem(
    @Json(name = "product_id") val productId: Long,
    @Json(name = "received_quantity") val receivedQuantity: Double,
    @Json(name = "unit_id") val unitId: Long? = null
)

/**
 * Extended procurement product DTO with received quantity
 */
data class ProcurementProductDetailDto(
    val id: Long,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "product_name") val productName: String?,
    val quantity: Double,
    @Json(name = "received_quantity") val receivedQuantity: Double?,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "total_price") val totalPrice: Double?,
    @Json(name = "unit_id") val unitId: Long?,
    @Json(name = "unit_name") val unitName: String?
)

// ============================================================================
// ORDER DETAIL & SYNC MODELS
// ============================================================================

/**
 * Response from /api/mobile/orders/{id}
 * Contains full order details including products and payments.
 */
data class OrderDetailResponse(
    val status: String?,
    val data: ServerOrder
)

/**
 * Response from /api/mobile/orders/sync?since={timestamp}
 * Contains orders changed since the provided timestamp.
 */
data class OrderSyncResponse(
    val data: List<ServerOrder>,
    val meta: OrderSyncMeta
)

/**
 * Metadata for order sync response
 */
data class OrderSyncMeta(
    @Json(name = "has_more") val hasMore: Boolean,
    @Json(name = "next_cursor") val nextCursor: Long?,
    @Json(name = "sync_token") val syncToken: String?,
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "total_count") val totalCount: Int
)

// ============================================================================
// PAYMENT MODELS
// ============================================================================

/**
 * Request for adding payment to an existing order
 * Used with POST /api/orders/{id}/payments
 */
data class PaymentRequest(
    val identifier: String,
    val value: Double,
    @Json(name = "payment_date") val paymentDate: String? = null,
    val reference: String? = null
)

/**
 * Response from adding payment to an order
 */
data class PaymentResponse(
    val status: String?,
    val message: String?,
    val data: PaymentData?
)

/**
 * Payment data returned after successful payment
 */
data class PaymentData(
    val id: Long,
    @Json(name = "order_id") val orderId: Long,
    val identifier: String,
    val value: Double,
    @Json(name = "created_at") val createdAt: String?
)

// ============================================================================
// INVENTORY/STOCK ADJUSTMENT MODELS
// ============================================================================

/**
 * Request for adjusting product stock
 */
data class StockAdjustmentRequest(
    @Json(name = "product_id") val productId: Long,
    @Json(name = "unit_quantity_id") val unitQuantityId: Long?,
    @Json(name = "adjustment_type") val adjustmentType: String? = null,
    @Json(name = "operation_type") val operationType: String? = null,
    val quantity: Double,
    val reason: String? = null,
    val reference: String? = null
)

/**
 * Response from stock adjustment
 */
data class StockAdjustmentResponse(
    val status: String?,
    val message: String?,
    val data: StockAdjustmentData?
)

/**
 * Stock adjustment result data
 */
data class StockAdjustmentData(
    val id: Long,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "previous_quantity") val previousQuantity: Double,
    @Json(name = "new_quantity") val newQuantity: Double,
    val adjustment: Double,
    @Json(name = "created_at") val createdAt: String?
)

/**
 * Inventory history item
 */
data class InventoryHistoryItem(
    val id: Long,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "product_name") val productName: String?,
    val operation: String,
    val quantity: Double,
    @Json(name = "unit_name") val unitName: String?,
    val reason: String?,
    val reference: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "created_by") val createdBy: String?
)

/**
 * Response for inventory history
 */
data class InventoryHistoryResponse(
    val data: List<InventoryHistoryItem>,
    val meta: InventoryHistoryMeta?
)

/**
 * Metadata for inventory history pagination
 */
data class InventoryHistoryMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    @Json(name = "has_more") val hasMore: Boolean
)

// ============================================================================
// CONTAINER MANAGEMENT API MODELS
// ============================================================================

/**
 * Customer container balance item
 */
data class CustomerContainerBalance(
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "customer_name") val customerName: String,
    @Json(name = "container_type_id") val containerTypeId: Int,
    @Json(name = "container_type_name") val containerTypeName: String,
    @Json(name = "quantity_held") val quantityHeld: Int,
    @Json(name = "deposit_total") val depositTotal: Double,
    @Json(name = "last_transaction_at") val lastTransactionAt: String?
)

/**
 * Response for customer container balances
 */
data class CustomerContainerBalancesResponse(
    val status: String?,
    val message: String?,
    val data: List<CustomerContainerBalance>,
    val meta: ContainerBalancesMeta?
)

/**
 * Metadata for container balances pagination
 */
data class ContainerBalancesMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    @Json(name = "has_more") val hasMore: Boolean
)

/**
 * Container charge preview item
 */
data class ContainerChargePreviewItem(
    @Json(name = "container_type_id") val containerTypeId: Int,
    @Json(name = "container_type_name") val containerTypeName: String,
    val quantity: Int,
    @Json(name = "deposit_amount") val depositAmount: Double,
    @Json(name = "total_charge") val totalCharge: Double
)

/**
 * Response for container charge preview
 */
data class ContainerChargePreviewResponse(
    val status: String?,
    val message: String?,
    val data: ContainerChargePreviewData?
)

/**
 * Container charge preview data
 */
data class ContainerChargePreviewData(
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "customer_name") val customerName: String,
    val items: List<ContainerChargePreviewItem>,
    @Json(name = "total_charge") val totalCharge: Double,
    @Json(name = "containers_held") val containersHeld: Int
)

/**
 * Request for container charge
 */
data class ContainerChargeRequest(
    @Json(name = "customer_id") val customerId: Long,
    val items: List<ContainerChargeItem>,
    val notes: String? = null
)

/**
 * Container charge item
 */
data class ContainerChargeItem(
    @Json(name = "container_type_id") val containerTypeId: Int,
    val quantity: Int
)

/**
 * Response for container charge
 */
data class ContainerChargeResponse(
    val status: String?,
    val message: String?,
    val data: ContainerChargeResult?
)

/**
 * Container charge result
 */
data class ContainerChargeResult(
    @Json(name = "transaction_id") val transactionId: Long,
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "total_charged") val totalCharged: Double,
    @Json(name = "containers_processed") val containersProcessed: Int,
    @Json(name = "created_at") val createdAt: String
)

/**
 * Container inventory history item
 */
data class ContainerInventoryHistoryItem(
    val id: Long,
    @Json(name = "container_type_id") val containerTypeId: Int,
    @Json(name = "container_type_name") val containerTypeName: String,
    val operation: String,
    val quantity: Int,
    @Json(name = "previous_quantity") val previousQuantity: Int,
    @Json(name = "new_quantity") val newQuantity: Int,
    val reason: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "created_by") val createdBy: String?
)

/**
 * Response for container inventory history
 */
data class ContainerInventoryHistoryResponse(
    val status: String?,
    val message: String?,
    val data: List<ContainerInventoryHistoryItem>,
    val meta: ContainerHistoryMeta?
)

/**
 * Container movement item
 */
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

/**
 * Response for container movements
 */
data class ContainerMovementsResponse(
    val status: String?,
    val message: String?,
    val data: List<ContainerMovementItem>,
    val meta: ContainerHistoryMeta?
)

/**
 * Metadata for container history pagination
 */
data class ContainerHistoryMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    @Json(name = "has_more") val hasMore: Boolean
)

// ============================================================================
// WALLET TOPUP MODELS
// ============================================================================

/**
 * Wallet Topup model
 */
data class WalletTopup(
    val id: Long,
    @Json(name = "customer_id") val customerId: Long,
    @Json(name = "customer_name") val customerName: String? = null,
    val amount: Double,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    val reference: String?,
    @Json(name = "created_at") val createdAt: String
)

/**
 * Response for wallet topups list.
 * Server returns: {"status": "success", "data": {"items": [...], "total": X}}
 */
data class WalletTopupListResponse(
    val status: String?,
    val message: String?,
    val data: WalletTopupListData
) {
    /**
     * Get the actual list of topups
     */
    fun getTopups(): List<WalletTopup> = data.items
}

/**
 * Data wrapper for wallet topups list
 * Server returns "topups" not "items"
 */
data class WalletTopupListData(
    @Json(name = "topups") val items: List<WalletTopup>,
    val total: Int
)

/**
 * Metadata for wallet topup pagination
 */
data class WalletTopupMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    @Json(name = "has_more") val hasMore: Boolean
)

/**
 * Response for single wallet topup
 */
data class WalletTopupResponse(
    val status: String?,
    val message: String?,
    val data: WalletTopup
)

/**
 * Request to create a new wallet topup
 */
data class CreateTopupRequest(
    @Json(name = "customer_id") val customerId: Long,
    val amount: Double,
    val description: String? = null,
    @Json(name = "received_date") val receivedDate: String
)

/**
 * Response for wallet topup creation
 */
data class CreateTopupResponse(
    val status: String?,
    val message: String?,
    val data: CreateTopupData? = null
)

/**
 * Wallet topup creation result
 */
data class CreateTopupData(
    val success: Boolean = false,
    @Json(name = "transaction_id") val transactionId: Long? = null,
    @Json(name = "customer_id") val customerId: Long? = null,
    val amount: Double? = null,
    @Json(name = "new_balance") val newBalance: Double? = null,
    @Json(name = "received_date") val receivedDate: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

/**
 * Response for customer wallet balance
 */
data class CustomerBalanceResponse(
    val status: String?,
    val message: String?,
    val data: CustomerBalance
)

/**
 * Customer wallet balance data
 */
data class CustomerBalance(
    @Json(name = "customer_id") val customerId: Long? = null,
    @Json(name = "customer_name") val customerName: String? = null,
    val balance: Double = 0.0,
    @Json(name = "last_topup_at") val lastTopupAt: String? = null,
    @Json(name = "total_topups") val totalTopups: Int = 0
)

// ============================================================================
// SPECIAL CUSTOMERS LIST - Customers belonging to special customer group
// ============================================================================

/**
 * Response from /api/mobile/special-customer/customers
 * Returns list of customers that belong to the special customer group.
 */
data class SpecialCustomersListResponse(
    val status: String?,
    val message: String?,
    val data: SpecialCustomersPageDto?
)

data class SpecialCustomersPageDto(
    val data: List<SpecialCustomerDto> = emptyList(),
    @Json(name = "current_page") val currentPage: Int? = null,
    @Json(name = "last_page") val lastPage: Int? = null,
    @Json(name = "per_page") val perPage: Int? = null,
    val total: Int? = null
)

/**
 * Special customer DTO with wallet and credit information.
 */
data class SpecialCustomerDto(
    val id: Long,
    val name: String?,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String?,
    @Json(name = "credit_limit") val creditLimit: Double = 0.0,
    @Json(name = "wallet_balance") val walletBalance: Double = 0.0,
    @Json(name = "priority_level") val priorityLevel: Int = 0,
    val notes: String? = null
)
