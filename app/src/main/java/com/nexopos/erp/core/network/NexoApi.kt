package com.nexopos.erp.core.network

import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

interface NexoApi {
    @GET
    suspend fun searchByBarcode(@Url url: String): BarcodeSearchResult

    @POST
    suspend fun searchByTerm(@Url url: String, @Body body: SearchRequest): List<Product>

    @GET
    suspend fun getCustomers(@Url url: String): List<Customer>

    @GET
    suspend fun getAllProducts(@Url url: String): List<Product>

    @GET
    suspend fun getCategories(@Url url: String): List<Category>

    @GET
    suspend fun getCategoryProducts(@Url url: String): List<Product>

    @GET
    suspend fun getProduct(@Url url: String): Product

    @GET
    suspend fun getPaymentFields(@Url url: String): List<PaymentField>

    @POST
    suspend fun createOrder(
        @Url url: String,
        @Body body: CreateOrderRequest
    ): ApiStatusResponse<OrderResponseData>

    @GET
    suspend fun getOrders(@Url url: String): List<ServerOrder>

    /**
     * Mobile-optimized paginated orders endpoint
     * Supports cursor pagination and filtering
     */
    @GET
    suspend fun getMobileOrders(@Url url: String): PaginatedOrdersResponse

    /**
     * Delete an order by ID
     * Returns Response<Unit> since delete may return empty body
     */
    @DELETE
    suspend fun deleteOrder(@Url url: String): Response<Unit>

    /**
     * Update an existing order
     */
    @PUT
    suspend fun updateOrder(
        @Url url: String,
        @Body body: CreateOrderRequest
    ): ApiStatusResponse<OrderResponseData>

    // ============ Inventory API Methods ============

    /**
     * Get products for inventory list
     * Returns a list of products with their unit quantities
     */
    @GET
    suspend fun getProducts(@Url url: String): List<Product>

    /**
     * Create an inventory adjustment
     */
    @POST
    suspend fun createInventoryAdjustment(
        @Url url: String,
        @Body body: InventoryAdjustmentRequest
    ): ApiStatusResponse<Unit>

    // ============ Container API Methods ============

    /**
     * Get all containers
     */
    @GET
    suspend fun getContainers(@Url url: String): List<ContainerDto>

    /**
     * Create a container transaction (deposit/return)
     */
    @POST
    suspend fun createContainerTransaction(
        @Url url: String,
        @Body body: ContainerTransactionRequest
    ): ApiStatusResponse<ContainerTransactionResponse>

    // ============ Procurement API Methods ============

    /**
     * Get all procurement orders
     */
    @GET
    suspend fun getProcurements(@Url url: String): List<ProcurementOrderDto>

    /**
     * Get a single procurement order by ID
     */
    @GET
    suspend fun getProcurement(@Url url: String): ProcurementOrderDto

    /**
     * Create a new procurement order
     */
    @POST
    suspend fun createProcurement(
        @Url url: String,
        @Body body: CreateProcurementRequest
    ): ApiStatusResponse<ProcurementOrderDto>

    /**
     * Update an existing procurement order
     */
    @PUT
    suspend fun updateProcurement(
        @Url url: String,
        @Body body: UpdateProcurementRequest
    ): ApiStatusResponse<ProcurementOrderDto>

    /**
     * Delete a procurement order
     */
    @DELETE
    suspend fun deleteProcurement(@Url url: String): Response<Unit>

    // ============ Order Payment API Methods ============

    /**
     * Add a payment to an existing order
     * @param url Full URL including order ID: /api/orders/{id}/payments
     * @param body Payment details
     */
    @POST
    suspend fun addOrderPayment(
        @Url url: String,
        @Body body: PaymentRequest
    ): PaymentResponse

    // ============ Register API Methods ============

    @Headers("Cache-Control: no-cache, no-store", "Pragma: no-cache")
    @GET
    suspend fun getRegisters(@Url url: String): List<Map<String, Any?>>

    @Headers("Cache-Control: no-cache, no-store", "Pragma: no-cache")
    @GET
    suspend fun getRegister(@Url url: String): Register

    @Headers("Cache-Control: no-cache, no-store", "Pragma: no-cache")
    @GET
    suspend fun getUsedRegister(@Url url: String): Map<String, Any?>

    @POST
    suspend fun syncRegister(@Url url: String): Map<String, Any?>

    @POST
    suspend fun openRegister(
        @Url url: String,
        @Body body: RegisterActionRequest
    ): RegisterResponse

    @POST
    suspend fun closeRegister(
        @Url url: String,
        @Body body: RegisterActionRequest
    ): RegisterResponse

    @POST
    suspend fun cashIn(
        @Url url: String,
        @Body body: RegisterActionRequest
    ): Map<String, Any?>

    @POST
    suspend fun cashOut(
        @Url url: String,
        @Body body: RegisterActionRequest
    ): Map<String, Any?>

    @Headers("Cache-Control: no-cache, no-store", "Pragma: no-cache")
    @GET
    suspend fun getRegisterHistory(@Url url: String): Map<String, Any?>
}
