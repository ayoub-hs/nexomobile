package com.nexopos.erp.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Mobile-optimized API interface.
 * 
 * These endpoints are designed for offline-first mobile apps with:
 * - Bundled responses (products + variations in one call)
 * - Delta sync support (only fetch changes since last sync)
 * - Batch operations (submit multiple orders at once)
 * - Efficient pagination
 * 
 * Base path: /api/mobile/
 */
interface MobileApi {

    // ========================================================================
    // AUTHENTICATION ENDPOINTS
    // ========================================================================

    /**
     * Login endpoint for mobile app
     * Validates credentials and returns a Sanctum token.
     */
    @POST("api/mobile/auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): LoginResponse

    /**
     * Logout endpoint for mobile app
     * Revokes the current authentication token.
     */
    @POST("api/mobile/auth/logout")
    suspend fun logout(): LogoutResponse

    /**
     * Get current user info
     * Returns information about the authenticated user.
     */
    @GET("api/mobile/auth/me")
    suspend fun getCurrentUser(): UserResponse

    /**
     * Get user permissions endpoint
     * Returns the authenticated user's permissions for UI gating.
     */
    @GET("api/mobile/auth/permissions")
    suspend fun getPermissions(): PermissionsResponse

    // ========================================================================
    // SYNC ENDPOINTS
    // ========================================================================

    /**
     * Full bootstrap sync for initial app setup or cache rebuild.
     * Returns all products, categories, customers, and payment methods.
     * 
     * Should be called:
     * - After first login
     * - When user requests full refresh
     * - When local cache is corrupted/cleared
     */
    @GET("api/mobile/sync/bootstrap")
    suspend fun bootstrapSync(
        @Query("limit") limit: Int = 500,
        @Query("cursor") cursor: String? = null
    ): BootstrapSyncResponse

    /**
     * Delta sync for incremental updates.
     * Returns only items changed since the provided sync token.
     * 
     * @param since Sync token from previous sync response
     * @param limit Max items per collection (default 500)
     */
    @GET("api/mobile/sync/delta")
    suspend fun deltaSync(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int = 500,
        @Query("cursor") cursor: String? = null
    ): DeltaSyncResponse

    /**
     * Quick status check to see if sync is needed.
     * Lightweight call that doesn't fetch actual data.
     */
    @GET("api/mobile/sync/status")
    suspend fun syncStatus(): SyncStatusResponse

    // ========================================================================
    // PRODUCT ENDPOINTS
    // ========================================================================

    /**
     * Get products for a specific category with all variations bundled.
     * Replaces the need for separate product detail calls.
     */
    @GET("api/mobile/catalog/category/{id}")
    suspend fun getCategoryProducts(
        @retrofit2.http.Path("id") categoryId: Long
    ): CategoryProductsResponse

    /**
     * Search products with full details including variations.
     * Results include all unit quantities so no follow-up calls needed.
     */
    @POST("api/mobile/catalog/search")
    suspend fun searchProducts(
        @Body body: SearchRequest
    ): ProductSearchResponse

    /**
     * Get a single product with full details.
     * Includes all unit quantities and pricing.
     */
    @GET("api/mobile/catalog/product/{id}")
    suspend fun getProduct(
        @retrofit2.http.Path("id") productId: Long
    ): MobileProduct

    /**
     * Search by barcode with full product details.
     */
    @GET("api/mobile/products/barcode/{barcode}")
    suspend fun searchByBarcode(
        @retrofit2.http.Path("barcode") barcode: String
    ): MobileProduct?

    /**
     * Search by barcode for scanner-admin flows.
     * Returns the richer admin product payload when a product exists.
     */
    @GET("api/mobile/products/barcode/{barcode}")
    suspend fun searchAdminByBarcode(
        @retrofit2.http.Path("barcode") barcode: String
    ): AdminProductResponse?

    @GET("api/mobile/products/barcode/{barcode}")
    suspend fun searchAdminByBarcodeRaw(
        @retrofit2.http.Path("barcode") barcode: String
    ): Response<ResponseBody>

    // ========================================================================
    // ORDER ENDPOINTS
    // ========================================================================

    /**
     * Submit a batch of offline orders.
     * More efficient than individual order submissions.
     */
    @POST("api/mobile/orders/batch")
    suspend fun submitOrderBatch(
        @Body body: BatchOrderRequest
    ): BatchOrderResponse

    /**
     * Get paginated orders.
     */
    @GET("api/mobile/orders")
    suspend fun getOrders(
        @Query("cursor") cursor: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("customer") customer: String? = null,
        @Query("payment_status") paymentStatus: String? = null,
        @Query("since") since: String? = null,
        @Query("direction") direction: String = "before"
    ): PaginatedOrdersResponse

    /**
     * Get a single order by ID.
     * Returns full order details including products and payments.
     */
    @GET("api/mobile/orders/{id}")
    suspend fun getOrderById(
        @Path("id") orderId: Long
    ): OrderDetailResponse

    /**
     * Delta sync for orders.
     * Returns orders changed since the provided timestamp.
     *
     * @param since Unix timestamp in seconds for incremental sync
     * @param cursor Pagination cursor for large result sets
     * @param limit Maximum number of orders to return
     */
    @GET("api/mobile/orders/sync")
    suspend fun syncOrders(
        @Query("since") since: Long,
        @Query("cursor") cursor: Long? = null,
        @Query("limit") limit: Int = 50
    ): OrderSyncResponse

    // ========================================================================
    // CONFIGURATION ENDPOINTS
    // ========================================================================

    /**
     * Get register-specific configuration.
     * Includes store info, currency settings, receipt templates.
     */
    @GET("api/mobile/register/config")
    suspend fun getRegisterConfig(): RegisterConfigResponse

    // ========================================================================
    // MANUFACTURING ENDPOINTS
    // ========================================================================

    /**
     * Get list of production orders
     */
    @GET("api/mobile/manufacturing/orders")
    suspend fun getManufacturingOrders(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: Int? = null,
        @Query("direction") direction: String = "before"
    ): ManufacturingOrdersResponse

    /**
     * Get single production order details
     */
    @GET("api/mobile/manufacturing/orders/{id}")
    suspend fun getManufacturingOrder(
        @retrofit2.http.Path("id") id: Int
    ): ProductionOrderResponse

    /**
     * Create new production order
     */
    @POST("api/mobile/manufacturing/orders")
    suspend fun createManufacturingOrder(
        @Body order: CreateProductionOrderRequest
    ): ProductionOrderResponse

    /**
     * Start production order
     */
    @PUT("api/mobile/manufacturing/orders/{id}/start")
    suspend fun startManufacturing(
        @Path("id") id: Int
    ): ProductionOrderResponse

    /**
     * Complete production order
     */
    @PUT("api/mobile/manufacturing/orders/{id}/complete")
    suspend fun completeManufacturing(
        @Path("id") id: Int
    ): ProductionOrderResponse

    /**
     * Get list of Bill of Materials
     */
    @GET("api/mobile/manufacturing/boms")
    suspend fun getManufacturingBoms(
        @Query("active_only") activeOnly: Boolean = false,
        @Query("limit") limit: Int = 20
    ): ManufacturingBomsResponse

    /**
     * Get single BOM with items
     */
    @GET("api/mobile/manufacturing/boms/{id}")
    suspend fun getManufacturingBom(
        @retrofit2.http.Path("id") id: Int
    ): BomResponse

    /**
     * Create a new BOM
     */
    @POST("api/mobile/manufacturing/boms")
    suspend fun createManufacturingBom(
        @Body request: CreateBomRequest
    ): ApiStatusResponse<ManufacturingBom>

    // ========================================================================
    // CONTAINER MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * Get list of container types
     */
    @GET("api/mobile/containers/types")
    suspend fun getContainerTypes(): ContainerTypesResponse

    /**
     * Get container inventory summary
     */
    @GET("api/mobile/containers/inventory")
    suspend fun getContainerInventory(): ContainerInventoryResponse

    /**
     * Adjust container inventory
     */
    @POST("api/mobile/containers/adjust")
    suspend fun adjustContainer(
        @Body request: ContainerAdjustRequest
    ): ContainerAdjustResponse

    /**
     * Receive returned containers from a customer.
     */
    @POST("api/mobile/containers/receive")
    suspend fun receiveContainers(
        @Body request: ContainerReceiveRequest
    ): ApiStatusResponse<ContainerMovementItem>

    /**
     * Get customer container balances
     */
    @GET("api/mobile/containers/customers/balances")
    suspend fun getCustomerContainerBalances(
        @Query("customer_id") customerId: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): CustomerContainerBalancesResponse

    /**
     * Preview container charge before execution
     */
    @GET("api/mobile/containers/charge/preview/{id}")
    suspend fun previewContainerCharge(
        @Path("id") customerId: Long
    ): ContainerChargePreviewResponse

    /**
     * Execute container charge for a customer
     */
    @POST("api/mobile/containers/charge")
    suspend fun chargeContainers(
        @Body request: ContainerChargeRequest
    ): ContainerChargeResponse

    /**
     * Get container inventory history
     */
    @GET("api/mobile/containers/inventory/history")
    suspend fun getContainerInventoryHistory(
        @Query("container_type_id") containerTypeId: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ContainerInventoryHistoryResponse

    /**
     * Get container movement history
     */
    @GET("api/mobile/containers/movements")
    suspend fun getContainerMovements(
        @Query("customer_id") customerId: Long? = null,
        @Query("container_type_id") containerTypeId: Long? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ContainerMovementsResponse

    // ========================================================================
    // SPECIAL CUSTOMER ENDPOINTS
    // ========================================================================

    /**
     * Get outstanding tickets for special customers
     */
    @GET("api/mobile/special-customer/tickets")
    suspend fun getOutstandingTickets(
        @Query("customer_id") customerId: Int? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): OutstandingTicketsResponse

    /**
     * Get single outstanding ticket details
     */
    @GET("api/mobile/special-customer/tickets/{id}")
    suspend fun getOutstandingTicket(
        @retrofit2.http.Path("id") id: Int
    ): OutstandingTicketResponse

    /**
     * Pay outstanding ticket
     */
    @POST("api/mobile/special-customer/tickets/{id}/pay")
    suspend fun payOutstandingTicket(
        @retrofit2.http.Path("id") id: Int,
        @Body request: PayTicketRequest
    ): PayTicketResponse

    /**
     * Pay an outstanding ticket using the shared pay-with-method endpoint.
     * Wallet payments also use this route with payment_method = "wallet".
     */
    @POST("api/mobile/special-customer/tickets/pay-with-method")
    suspend fun payOutstandingTicketWithMethod(
        @Body request: PayTicketWithMethodRequest
    ): PayTicketResponse

    /**
     * Get wallet topups list
     */
    @GET("api/mobile/special-customer/wallet/topups")
    suspend fun getWalletTopups(
        @Query("customer_id") customerId: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): WalletTopupListResponse

    /**
     * Get single wallet topup details
     */
    @GET("api/mobile/special-customer/wallet/topups/{id}")
    suspend fun getWalletTopup(
        @Path("id") topupId: Long
    ): WalletTopupResponse

    /**
     * Create new wallet topup
     */
    @POST("api/mobile/special-customer/wallet/topup")
    suspend fun createWalletTopup(
        @Body request: CreateTopupRequest
    ): CreateTopupResponse

    /**
     * Get customer wallet balance
     */
    @GET("api/mobile/special-customer/customers/{id}/balance")
    suspend fun getCustomerWalletBalance(
        @Path("id") customerId: Long
    ): CustomerBalanceResponse

    /**
     * Get all special customers (customers belonging to special customer group).
     * Returns list of customers with their wallet balance and credit limit.
     */
    @GET("api/mobile/special-customer/customers")
    suspend fun getSpecialCustomers(
        @Query("per_page") perPage: Int = 100
    ): SpecialCustomersListResponse

    // ========================================================================
    // CATEGORY ENDPOINTS
    // ========================================================================

    /**
     * Get all categories for the search screen
     */
    @GET("api/mobile/categories")
    suspend fun getCategories(): List<CategoryResponse>

    // ========================================================================
    // PRODUCT ENDPOINTS
    // ========================================================================

    /**
     * Get paginated products for inventory and selection flows.
     */
    @GET("api/mobile/products")
    suspend fun getProducts(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: Long? = null,
        @Query("search") search: String? = null
    ): ProductsResponse

    /**
     * Get a single product with admin-edit fields.
     */
    @GET("api/mobile/products/{id}")
    suspend fun getAdminProduct(
        @Path("id") productId: Long
    ): AdminProductResponse

    /**
     * Create a product for scanner-admin flows.
     */
    @POST("api/mobile/products")
    suspend fun createProduct(
        @Body request: CreateProductRequest
    ): AdminProductResponse

    /**
     * Update a product for scanner-admin flows.
     */
    @PUT("api/mobile/products/{id}")
    suspend fun updateProduct(
        @Path("id") productId: Long,
        @Body request: UpdateProductRequest
    ): AdminProductResponse

    /**
     * Update a single unit quantity (partial update).
     */
    @PATCH("api/mobile/unit-quantities/{id}")
    suspend fun updateUnitQuantity(
        @Path("id") unitQuantityId: Long,
        @Body request: UpdateUnitQuantityRequest
    ): UnitQuantityUpdateResponse

    /**
     * Get units list for dropdown selection
     */
    @GET("api/mobile/units")
    suspend fun getUnits(): List<UnitResponse>

    /**
     * Get unit groups list for dropdown selection.
     */
    @GET("api/mobile/unit-groups")
    suspend fun getUnitGroups(): List<UnitGroupResponse>

    /**
     * Get tax groups list for dropdown selection.
     */
    @GET("api/mobile/tax-groups")
    suspend fun getTaxGroups(): List<TaxGroupResponse>

    // ========================================================================
    // INVENTORY ENDPOINTS
    // ========================================================================

    /**
     * Adjust product stock (add or remove quantity)
     */
    @POST("api/mobile/inventory/adjust")
    suspend fun adjustStock(
        @Body request: StockAdjustmentRequest
    ): StockAdjustmentResponse

    /**
     * Get inventory history for a product
     */
    @GET("api/mobile/inventory/history")
    suspend fun getInventoryHistory(
        @Query("product_id") productId: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): InventoryHistoryResponse

    /**
     * Get providers list for procurement forms
     */
    @GET("api/mobile/providers")
    suspend fun getProviders(
        @Query("search") search: String? = null
    ): ProvidersResponse

    // ========================================================================
    // PROCUREMENT ENDPOINTS
    // ========================================================================

    /**
     * Get all procurements
     */
    @GET("api/mobile/procurements")
    suspend fun getProcurements(): ProcurementListResponse

    /**
     * Get single procurement by ID
     */
    @GET("api/mobile/procurements/{id}")
    suspend fun getProcurement(
        @Path("id") id: Long
    ): SingleProcurementResponse

    /**
     * Create new procurement
     */
    @POST("api/mobile/procurements")
    suspend fun createProcurement(
        @Body request: CreateProcurementRequest
    ): SingleProcurementResponse

    /**
     * Update procurement
     */
    @PUT("api/mobile/procurements/{id}")
    suspend fun updateProcurement(
        @Path("id") id: Long,
        @Body request: UpdateProcurementRequest
    ): SingleProcurementResponse

    /**
     * Update procurement status
     */
    @PUT("api/mobile/procurements/{id}/status")
    suspend fun updateProcurementStatus(
        @Path("id") id: Long,
        @Query("status") status: String
    ): SingleProcurementResponse

    /**
     * Delete procurement
     */
    @retrofit2.http.DELETE("api/mobile/procurements/{id}")
    suspend fun deleteProcurement(
        @Path("id") id: Long
    ): Response<Void>

    /**
     * Receive procurement items (partial or full delivery)
     * Updates received quantities for specified products
     */
    @PUT("api/mobile/procurements/{id}/receive")
    suspend fun receiveProcurement(
        @Path("id") id: Long,
        @Body request: ReceiveProcurementRequest
    ): SingleProcurementResponse

    /**
     * Cancel a procurement order
     */
    @PUT("api/mobile/procurements/{id}/cancel")
    suspend fun cancelProcurement(
        @Path("id") id: Long
    ): Response<Void>
}
