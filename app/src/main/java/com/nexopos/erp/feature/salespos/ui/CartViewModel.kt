package com.nexopos.erp.feature.salespos.ui

import android.content.Context
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.R
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.toModel
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.network.ContainerLink
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.OrderPaymentRequest
import com.nexopos.erp.core.network.OrderProductRequest
import com.nexopos.erp.core.network.OrderSummary
import com.nexopos.erp.core.network.OrderType
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.core.print.PrinterType
import com.nexopos.erp.core.repo.CustomerRepository
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
import com.nexopos.erp.print.PrintUtil
import com.nexopos.erp.core.sync.OfflineOrderSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class CartViewModel(
    private val appContext: Context,
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val orderQueueRepository: OrderQueueRepository,
    private val customerRepository: CustomerRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_TAX_RATE = 0.19
        private const val MAX_CART_SIZE = 100
        private const val MAX_QUANTITY = 9999.0
        private const val MAX_PRICE = 999_999.99
    }

    private val productDao = AppDatabase.get(appContext).productDao()
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var lastFailedCount: Int = 0

    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()
    // P1 Fix: Use replay=1 to ensure navigation events are not lost if collector isn't ready.
    // Clear the replay cache after handling to avoid stale events on re-entry.
    private val _events = MutableSharedFlow<CartEvent>(replay = 1)
    val events: SharedFlow<CartEvent> = _events.asSharedFlow()

    init {
        // P1 Fix: Stagger initialization by 500ms to reduce post-login lag
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Stagger after login
            preloadData()
        }
        viewModelScope.launch {
            refreshPrinterStatus()
        }
        viewModelScope.launch {
            orderQueueRepository.observePendingCount().collect { count ->
                _state.update { it.copy(pendingOrderCount = count) }
            }
        }
        viewModelScope.launch {
            orderQueueRepository.observeFailedCount().collect { count ->
                _state.update { it.copy(failedOrderCount = count) }
                if (count > 0 && count != lastFailedCount) {
                    _events.emit(CartEvent.OfflineSyncFailed(count))
                }
                lastFailedCount = count
            }
        }
    }

    fun retryFailedOrders() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                orderQueueRepository.retryFailed()
            }
            OfflineOrderSyncWorker.schedule(appContext)
        }
    }

    fun clearEvents() {
        _events.resetReplayCache()
    }

    private suspend fun preloadData() {
        _state.update { it.copy(isLoading = true, error = null) }
        val customersResult = withContext(Dispatchers.IO) { customerRepository.listCustomers() }
        val paymentMethodsResult = withContext(Dispatchers.IO) { orderRepository.listPaymentMethods() }

        val customers = customersResult.getOrElse {
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    error = it.message ?: appContext.getString(R.string.error_load_customers)
                )
            }
            return
        }

        val targetFirstName = appContext.getString(R.string.customer_default_walk_in_name)

        // P2 Fix: Move customer sorting to IO dispatcher to reduce main thread work
        val orderedCustomers = withContext(Dispatchers.IO) {
            sortCustomersOnBackground(customers, targetFirstName)
        }

        val paymentMethods = paymentMethodsResult.getOrElse {
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    error = it.message ?: appContext.getString(R.string.error_load_payment_methods)
                )
            }
            return
        }

        _state.update { state ->
            state.copy(
                isLoading = false,
                customers = orderedCustomers,
                selectedCustomer = orderedCustomers.firstOrNull { it.firstName?.equals(targetFirstName, ignoreCase = true) == true }
                    ?: orderedCustomers.firstOrNull { it.isDefault == true }
                    ?: orderedCustomers.firstOrNull(),
                paymentMethods = paymentMethods,
                selectedPayment = paymentMethods.firstOrNull(),
                error = null
            )
        }
    }

    fun addProduct(product: Product, quantity: Double = 1.0) {
        // MED-001: Input validation
        if (quantity <= 0.0 || quantity > MAX_QUANTITY) return
        // MED-004: Cart size limit
        if (_state.value.items.size >= MAX_CART_SIZE) {
            viewModelScope.launch { _events.emit(CartEvent.Error(appContext.getString(R.string.error_cart_full))) }
            return
        }
        val selectedUnit = product.unitQuantities?.firstOrNull()
        val salePrice = selectedUnit?.salePrice ?: 0.0
        val wholesalePrice = selectedUnit?.wholesalePriceWithTax
        val initialPrice = when {
            salePrice > 0.0 -> salePrice
            wholesalePrice != null && wholesalePrice > 0.0 -> wholesalePrice
            else -> 0.0
        }
        if (initialPrice <= 0.0 || initialPrice > MAX_PRICE) return
        _state.update { state ->
            val existingIndex = state.items.indexOfFirst {
                it.productId == product.id && it.unitQuantityId == selectedUnit?.id
            }
            val updatedItems = state.items.toMutableList()
            if (existingIndex >= 0) {
                val existing = updatedItems[existingIndex]
                updatedItems[existingIndex] = existing.copy(
                    quantity = existing.quantity + quantity,
                    containerLink = existing.containerLink ?: selectedUnit?.containerLink,
                    hasContainerMetadata = existing.hasContainerMetadata || selectedUnit?.containerLink != null
                )
            } else {
                updatedItems += CartItem(
                    key = UUID.randomUUID().toString(),
                    productId = product.id,
                    name = product.name,
                    quantity = quantity,
                    unitPrice = initialPrice,
                    unitQuantityId = selectedUnit?.id,
                    unitId = selectedUnit?.unitId,
                    unitName = selectedUnit?.unit?.name,
                    salePrice = salePrice.takeIf { it > 0.0 },
                    wholesalePriceWithTax = wholesalePrice?.takeIf { it > 0.0 },
                    useWholesale = salePrice <= 0.0 && (wholesalePrice ?: 0.0) > 0.0,
                    containerLink = selectedUnit?.containerLink,
                    hasContainerMetadata = selectedUnit?.containerLink != null
                )
            }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0,
                error = null
            )
        }
    }
    fun addProduct(
        productId: Long,
        name: String,
        unitQuantityId: Long?,
        unitId: Long?,
        unitName: String?,
        unitPrice: Double,
        quantity: Double,
        salePrice: Double? = null,
        wholesalePriceWithTax: Double? = null,
        useWholesale: Boolean = false,
        isCustomPrice: Boolean = false,
        containerLink: ContainerLink? = null,
        hasContainerMetadata: Boolean = containerLink != null
    ) {
        // MED-001: Input validation
        if (unitPrice < 0.0 || unitPrice > MAX_PRICE) return
        if (quantity <= 0.0 || quantity > MAX_QUANTITY) return
        // MED-004: Cart size limit
        if (_state.value.items.size >= MAX_CART_SIZE) {
            viewModelScope.launch { _events.emit(CartEvent.Error(appContext.getString(R.string.error_cart_full))) }
            return
        }
        _state.update { state ->
            val existingIndex = state.items.indexOfFirst {
                it.productId == productId && it.unitQuantityId == unitQuantityId
            }
            val updatedItems = state.items.toMutableList()
            if (existingIndex >= 0) {
                val existing = updatedItems[existingIndex]
                updatedItems[existingIndex] = existing.copy(
                    quantity = existing.quantity + quantity,
                    containerLink = existing.containerLink ?: containerLink,
                    hasContainerMetadata = existing.hasContainerMetadata || hasContainerMetadata
                )
            } else {
                updatedItems += CartItem(
                    key = UUID.randomUUID().toString(),
                    productId = productId,
                    name = name,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    unitQuantityId = unitQuantityId,
                    unitId = unitId,
                    unitName = unitName,
                    salePrice = salePrice?.takeIf { it > 0.0 },
                    wholesalePriceWithTax = wholesalePriceWithTax?.takeIf { it > 0.0 },
                    useWholesale = useWholesale,
                    isCustomPrice = isCustomPrice,
                    containerLink = containerLink,
                    hasContainerMetadata = hasContainerMetadata
                )
            }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0,
                error = null
            )
        }
    }

    /**
     * Add a custom item to the cart (non-catalog item with custom price).
     */
    fun addCustomItem(
        name: String,
        quantity: Double,
        unitPrice: Double
    ) {
        // Input validation
        if (name.isBlank()) return
        if (unitPrice < 0.0 || unitPrice > MAX_PRICE) return
        if (quantity <= 0.0 || quantity > MAX_QUANTITY) return
        // Cart size limit
        if (_state.value.items.size >= MAX_CART_SIZE) {
            viewModelScope.launch { _events.emit(CartEvent.Error(appContext.getString(R.string.error_cart_full))) }
            return
        }
        _state.update { state ->
            val updatedItems = state.items.toMutableList()
            updatedItems += CartItem(
                key = UUID.randomUUID().toString(),
                productId = -1L, // Custom item marker
                name = name,
                quantity = quantity,
                unitPrice = unitPrice,
                unitQuantityId = null,
                unitId = null,
                unitName = null,
                salePrice = null,
                wholesalePriceWithTax = null,
                useWholesale = false,
                isCustomPrice = true
            )
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0,
                error = null
            )
        }
    }

    fun removeItem(key: String) {
        _state.update { state ->
            val updatedItems = state.items.filterNot { it.key == key }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0
            )
        }
    }

    fun updateQuantity(key: String, quantity: Double) {
        if (quantity <= 0) {
            removeItem(key)
            return
        }
        _state.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.key == key) item.copy(quantity = quantity) else item
            }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0
            )
        }
    }

    fun toggleWholesale(key: String, useWholesale: Boolean) {
        _state.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.key == key) {
                    val targetPrice = if (useWholesale) {
                        item.wholesalePriceWithTax ?: item.unitPrice
                    } else {
                        item.salePrice ?: item.wholesalePriceWithTax ?: item.unitPrice
                    }
                    item.copy(
                        unitPrice = targetPrice,
                        useWholesale = useWholesale,
                        isCustomPrice = false
                    )
                } else item
            }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0
            )
        }
    }

    fun updateItemPrice(key: String, price: Double) {
        if (price <= 0.0) return
        _state.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.key == key) {
                    item.copy(
                        unitPrice = price,
                        isCustomPrice = true,
                        useWholesale = false
                    )
                } else item
            }
            state.copy(
                items = updatedItems,
                tendered = if (state.tenderedOverride) state.tendered else 0.0
            )
        }
    }

    fun updateContainerTracking(key: String, enabled: Boolean, quantityOverride: Int?) {
        _state.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.key == key) {
                    item.copy(
                        containerTrackingEnabled = enabled,
                        containerQuantityOverride = quantityOverride,
                        hasContainerMetadata = item.hasContainerMetadata || item.containerLink != null || enabled || quantityOverride != null
                    )
                } else {
                    item
                }
            }
            state.copy(items = updatedItems, error = null)
        }
    }

    fun setCustomer(customer: Customer) {
        _state.update { it.copy(selectedCustomer = customer) }
    }

    fun setPayment(identifier: String) {
        _state.update { state ->
            val method = state.paymentMethods.firstOrNull { it.identifier == identifier }
            state.copy(selectedPayment = method)
        }
    }

    fun setPrintReceipt(enabled: Boolean) {
        _state.update { it.copy(printReceipt = enabled) }
    }

    fun refreshPrinterStatus() {
        viewModelScope.launch {
            val config = fetchPrinterConfig()
            applyPrinterStatus(config)
        }
    }

    fun testPrinter() {
        viewModelScope.launch {
            val config = fetchPrinterConfig()
            applyPrinterStatus(config)
            val ready = isPrinterConfigured(config)
            if (!ready) {
                _state.update { state ->
                    state.copy(
                        printerMessage = appContext.getString(R.string.printer_message_configure_before_testing),
                        printerMessageError = true
                    )
                }
                return@launch
            }
            _state.update { state ->
                state.copy(isTestingPrinter = true, printerMessage = null)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { PrintUtil.printSampleReceipt(appContext, config) }
            }
            _state.update { state ->
                state.copy(
                    isTestingPrinter = false,
                    printerMessage = result.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.printer_message_test_receipt_sent),
                    printerMessageError = result.isFailure
                )
            }
        }
    }

    fun setTendered(amount: Double) {
        _state.update { state ->
            state.copy(
                tendered = amount.coerceAtLeast(0.0),
                tenderedOverride = true
            )
        }
    }

    fun setDiscountValue(value: Double) {
        _state.update { state ->
            state.copy(discountValue = value.coerceAtLeast(0.0))
        }
    }

    fun setDiscountType(type: DiscountType) {
        _state.update { state ->
            state.copy(discountType = type)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Load an existing order into the cart for editing.
     * This clears the current cart and populates it with the order's items.
     * Looks up unitQuantityId from product database if not present in order data.
     * 
     * @param orderId The local database ID of the order being edited
     * @param request The order request containing products, customer, payments, etc.
     * @param serverOrderId The server ID if this is a synced order (for PUT request)
     */
    fun loadOrderForEdit(orderId: Long, request: CreateOrderRequest, serverOrderId: Long? = null) {
        Log.d("CartViewModel", "Loading order for edit: orderId=$orderId, serverOrderId=$serverOrderId")
        
        viewModelScope.launch {
            // Look up unitQuantityId from product database for each product
            val items = mutableListOf<CartItem>()
            for (product in request.products) {
                Log.d("CartViewModel", "Product: ${product.name}, productId=${product.productId}, unitQuantityId=${product.unitQuantityId}, unitId=${product.unitId}")
                
                // If unitQuantityId is missing, look it up from the product's unit quantities
                val resolvedUnitQuantityId = product.unitQuantityId ?: run {
                    val productId = product.productId ?: return@run null
                    val unitId = product.unitId ?: return@run null
                    
                    withContext(Dispatchers.IO) {
                        val productEntity = productDao.findById(productId)
                        val productModel = productEntity?.toModel()
                        val unitQuantity = productModel?.unitQuantities?.find { it.unitId == unitId }
                        Log.d("CartViewModel", "Looked up unitQuantityId for product $productId, unitId $unitId: ${unitQuantity?.id}")
                        unitQuantity?.id
                    }
                }
                val resolvedContainerLink = resolveContainerLink(product.productId, resolvedUnitQuantityId)
                val hasContainerMetadata =
                    product.containerTrackingEnabled != null ||
                        product.containerQuantityOverride != null ||
                        resolvedContainerLink != null
                
                items += CartItem(
                    key = UUID.randomUUID().toString(),
                    productId = product.productId ?: 0L,
                    name = product.name,
                    quantity = product.quantity,
                    unitPrice = product.unitPrice,
                    unitQuantityId = resolvedUnitQuantityId,
                    unitId = product.unitId,
                    unitName = product.unitName,
                    salePrice = product.unitPrice.takeIf { product.mode != "wholesale" },
                    wholesalePriceWithTax = product.unitPrice.takeIf { product.mode == "wholesale" },
                    useWholesale = product.mode == "wholesale",
                    isCustomPrice = product.mode == "custom" || product.productId == null || product.productId == 0L,
                    containerLink = resolvedContainerLink,
                    containerTrackingEnabled = product.containerTrackingEnabled ?: false,
                    containerQuantityOverride = product.containerQuantityOverride,
                    hasContainerMetadata = hasContainerMetadata
                )
            }

            val discountType = when (request.discountType) {
                "percentage" -> DiscountType.Percent
                else -> DiscountType.Amount
            }
            val discountValue = when (discountType) {
                DiscountType.Percent -> request.discountPercentage
                DiscountType.Amount -> request.discountAmount
            }

            _state.update { state ->
                // Find matching customer from loaded customers
                val customer = request.customer?.let { reqCustomer ->
                    state.customers.find { it.id == reqCustomer.id } ?: reqCustomer
                } ?: request.customerId?.let { customerId ->
                    state.customers.find { it.id == customerId }
                }

                // Find matching payment method
                val payment = request.payments.firstOrNull()?.let { reqPayment ->
                    state.paymentMethods.find { it.identifier == reqPayment.identifier }
                } ?: state.selectedPayment

                state.copy(
                    items = items,
                    discountType = discountType,
                    discountValue = discountValue,
                    tendered = request.tendered.coerceAtLeast(0.0),
                    tenderedOverride = request.tendered > 0.0,
                    selectedCustomer = customer ?: state.selectedCustomer,
                    selectedPayment = payment,
                    editingOrderId = orderId,
                    editingServerOrderId = serverOrderId,
                    error = null
                )
            }
        }
    }

    /**
     * Clear edit mode without clearing the cart.
     */
    fun clearEditMode() {
        _state.update { it.copy(editingOrderId = null, editingServerOrderId = null) }
    }

    /**
     * Check if currently editing an order.
     */
    fun isEditingOrder(): Boolean = _state.value.editingOrderId != null

    private suspend fun resolveContainerLink(productId: Long?, unitQuantityId: Long?): ContainerLink? {
        if (productId == null || productId <= 0L || unitQuantityId == null) {
            return null
        }
        return withContext(Dispatchers.IO) {
            productDao.findById(productId)
                ?.toModel()
                ?.unitQuantities
                ?.firstOrNull { it.id == unitQuantityId }
                ?.containerLink
        }
    }

    suspend fun submitOrder(
        registerId: Int? = null,
        orderType: OrderType = defaultOrderType()
    ): Result<OrderSummary?> {
        return submitOrderInternal(
            orderType = orderType,
            holdTitle = null,
            paymentStatusOverride = null,
            registerId = registerId
        )
    }

    suspend fun submitOrderHold(
        title: String,
        registerId: Int? = null,
        orderType: OrderType = defaultOrderType(),
    ): Result<OrderSummary?> {
        return submitOrderInternal(
            orderType = orderType,
            holdTitle = title,
            paymentStatusOverride = "hold",
            registerId = registerId
        )
    }

    suspend fun submitOrderUnpaid(
        registerId: Int? = null,
        orderType: OrderType = defaultOrderType()
    ): Result<OrderSummary?> {
        return submitOrderInternal(
            orderType = orderType,
            holdTitle = null,
            paymentStatusOverride = "unpaid",
            registerId = registerId
        )
    }

    private suspend fun submitOrderInternal(
        orderType: OrderType,
        holdTitle: String?,
        paymentStatusOverride: String?,
        registerId: Int?
    ): Result<OrderSummary?> {
        val current = state.value
        if (current.items.isEmpty()) {
            return Result.failure(IllegalStateException(appContext.getString(R.string.error_cart_empty)))
        }
        if (current.hasTrackedContainersWithoutCustomer) {
            val message = appContext.getString(R.string.error_select_customer_for_tracked_containers)
            _state.update { it.copy(error = message) }
            return Result.failure(IllegalStateException(message))
        }
        val payment = current.selectedPayment
            ?: return Result.failure(IllegalStateException(appContext.getString(R.string.error_select_payment_method)))
        val customer = current.selectedCustomer

        val products = current.items.map { item ->
            Log.d("CartViewModel", "Submitting item: ${item.name}, productId=${item.productId}, unitQuantityId=${item.unitQuantityId}, unitId=${item.unitId}")
            val isQuickProduct = item.productId == 0L
            val payloadProductId = if (isQuickProduct) 0L else item.productId
            val unitPriceForPayload = item.unitPrice.takeIf { it > 0.0 }
                ?: item.subtotalExcludingTax.let { subtotal ->
                    if (item.quantity > 0.0) subtotal / item.quantity else 0.0
                }
            val priceWithTax = unitPriceForPayload
            val priceWithoutTax = priceWithTax / (1 + DEFAULT_TAX_RATE)
            val unitTaxValue = priceWithTax - priceWithoutTax
            val subtotalExcludingTax = priceWithoutTax * item.quantity
            val totalWithTax = priceWithTax * item.quantity
            val totalTaxValue = unitTaxValue * item.quantity
            val productMode = when {
                item.isCustomPrice -> "custom" // quick products and edited prices
                item.useWholesale && (item.wholesalePriceWithTax ?: 0.0) > 0.0 -> "wholesale"
                else -> "normal"
            }
            OrderProductRequest(
                productId = payloadProductId,
                name = item.name,
                quantity = item.quantity,
                unitQuantityId = item.unitQuantityId,
                unitId = item.unitId ?: if (isQuickProduct) 1L else null,
                unitName = item.unitName ?: if (isQuickProduct) "PCS" else null,
                unitPrice = unitPriceForPayload,
                priceWithTax = priceWithTax,
                priceWithoutTax = priceWithoutTax,
                taxGroupId = if (isQuickProduct) 1L else null,
                taxType = "inclusive",
                taxValue = unitTaxValue,
                saleTaxValue = if (item.useWholesale) null else unitTaxValue,
                wholesaleTaxValue = if (item.useWholesale) unitTaxValue else null,
                totalPrice = totalWithTax,
                totalPriceWithoutTax = subtotalExcludingTax,
                totalPriceWithTax = totalWithTax,
                totalTaxValue = totalTaxValue,
                mode = productMode,
                containerTrackingEnabled = if (item.hasContainerMetadata) item.containerTrackingEnabled else null,
                containerQuantityOverride = if (item.hasContainerMetadata) item.containerQuantityOverride else null
            )
        }

        val deferredPayment = paymentStatusOverride == "hold" || paymentStatusOverride == "unpaid"

        val payments = if (deferredPayment) {
            emptyList()
        } else {
            listOf(
                OrderPaymentRequest(
                    identifier = payment.identifier,
                    value = current.total,
                    label = payment.label,
                    selected = true,
                    readonly = payment.readonly
                )
            )
        }

        val clientReference = UUID.randomUUID().toString()

        val request = CreateOrderRequest(
            title = holdTitle,
            type = orderType,
            customerId = customer?.id,
            customer = customer,
            products = products,
            payments = payments,
            subtotal = current.subtotal,
            total = current.total,
            tendered = if (deferredPayment) 0.0 else current.tendered,
            change = if (deferredPayment) 0.0 else current.change,
            totalProducts = current.items.size,
            discountAmount = current.discountAmount,
            discountType = when (current.discountType) {
                DiscountType.Amount -> "flat"
                DiscountType.Percent -> "percentage"
            },
            discountPercentage = if (current.discountType == DiscountType.Percent) current.discountValue else 0.0,
            taxValue = current.taxTotal,
            productsTaxValue = current.taxTotal,
            paymentStatus = paymentStatusOverride ?: "paid",
            note = null,
            register_id = registerId,
            clientReference = clientReference
        )

        _state.update { it.copy(isSubmitting = true, error = null) }
        
        val editingOrderId = current.editingOrderId
        val editingServerOrderId = current.editingServerOrderId
        val isEditingSyncedOrder = editingServerOrderId != null
        
        // Use PUT for synced orders, POST for new orders
        val result = withContext(Dispatchers.IO) {
            if (isEditingSyncedOrder) {
                orderRepository.updateOrder(editingServerOrderId, request)
            } else {
                orderRepository.createOrder(request)
            }
        }
        result.onFailure { throwable ->
            val action = if (isEditingSyncedOrder) "Update" else "Create"
            Log.e("CartViewModel", "$action order failed", throwable)
            // Log response body for HTTP errors
            if (throwable is retrofit2.HttpException) {
                val errorBody = throwable.response()?.errorBody()?.string()
                Log.e("CartViewModel", "$action order HTTP ${throwable.code()}: $errorBody")
            }
        }

        var summary: OrderSummary? = null
        var offlineQueued = false
        var queueError: Throwable? = null

        val online = isOnline()

        if (online && result.isSuccess) {
            // Convert OrderResponse to OrderSummary
            val orderResponse = result.getOrNull()
            summary = orderResponse?.let {
                OrderSummary(
                    id = it.orderId,
                    code = it.orderCode,
                    total = it.total,
                    totalWithoutTax = null,
                    totalWithTax = null,
                    totalCoupons = null,
                    taxValue = null,
                    paymentStatus = request.paymentStatus,
                    customer = null
                )
            }

            // Persist a synced copy locally for offline order history
            // But NOT for synced order edits - the server already has the updated order
            // and it will be refreshed on next sync
            if (!isEditingSyncedOrder && summary != null) {
                withContext(Dispatchers.IO) {
                    runCatching { 
                        orderQueueRepository.saveSyncedCopy(
                            request = request,
                            serverId = summary.id,
                            serverCode = summary.code,
                            paymentStatus = summary.paymentStatus
                        )
                    }
                }
            }
        } else if (!isEditingSyncedOrder) {
            // Only queue offline for new orders, not for synced order edits
            val queueResult = withContext(Dispatchers.IO) {
                runCatching { orderQueueRepository.enqueue(request) }
            }
            offlineQueued = queueResult.isSuccess
            if (queueResult.isFailure) {
                queueError = queueResult.exceptionOrNull()
                queueError?.let {
                    Log.e("CartViewModel", "Queue order failed", it)
                }
            } else {
                OfflineOrderSyncWorker.schedule(appContext)
            }
        }

        // If editing a local order and submission succeeded, delete the original order
        if (editingOrderId != null && (summary != null || offlineQueued)) {
            withContext(Dispatchers.IO) {
                runCatching { orderQueueRepository.deleteById(editingOrderId) }
            }
        }

        var printError: String? = null
        val onlineSuccessWithoutSummary = online && result.isSuccess && summary == null
        val shouldPrintReceipt = current.printReceipt && (summary != null || offlineQueued || onlineSuccessWithoutSummary)
        Log.d(
            "CartViewModel",
            "PrintFlow: printReceipt=${current.printReceipt}, summary=${summary != null}, " +
                    "offlineQueued=$offlineQueued, onlineSuccessWithoutSummary=$onlineSuccessWithoutSummary, " +
                    "shouldPrintReceipt=$shouldPrintReceipt"
        )
        var printerConfig: PrinterConfig? = null
        if (shouldPrintReceipt) {
            printerConfig = withContext(Dispatchers.IO) { settingsRepository.printerConfigFlow.first() }
            val isConfigured = when (printerConfig.type) {
                PrinterType.Bluetooth -> !printerConfig.macAddress.isNullOrBlank()
                PrinterType.Tcp -> !printerConfig.host.isNullOrBlank()
            }
            if (isConfigured) {
                val printResult = withContext(Dispatchers.IO) {
                    runCatching {
                        PrintUtil.printOrderReceipt(appContext, printerConfig, request, summary)
                    }
                }
                if (printResult.isFailure) {
                    printError = printResult.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.error_receipt_print_failed)
                    Log.e("CartViewModel", "PrintFlow: print failed", printResult.exceptionOrNull())
                } else {
                    Log.d("CartViewModel", "PrintFlow: print successful")
                }
            } else {
                Log.w("CartViewModel", "PrintFlow: printer not configured, skipping print")
            }
        }
        printerConfig?.let { applyPrinterStatus(it) }

        val printerMessage = when {
            printError != null -> printError
            shouldPrintReceipt -> appContext.getString(R.string.printer_message_receipt_sent)
            else -> current.printerMessage
        }
        val printerMessageError = printError != null || current.printerMessageError

        val offlineMessage = appContext.getString(R.string.checkout_offline_order_queued)
        val failureMessage = result.exceptionOrNull()?.message
            ?: queueError?.message
            ?: appContext.getString(R.string.error_order_failed)

        val updatedState = when {
            summary != null -> {
                CartState(
                    lastOrder = summary,
                    error = printError,
                    customers = current.customers,
                    selectedCustomer = current.selectedCustomer,
                    paymentMethods = current.paymentMethods,
                    selectedPayment = current.selectedPayment,
                    printReceipt = current.printReceipt,
                    printerLabel = current.printerLabel,
                    printerReady = current.printerReady,
                    printerMessage = printerMessage,
                    printerMessageError = printerMessageError,
                    pendingOrderCount = current.pendingOrderCount
                )
            }

            onlineSuccessWithoutSummary -> {
                CartState(
                    lastOrder = null,
                    error = printError,
                    customers = current.customers,
                    selectedCustomer = current.selectedCustomer,
                    paymentMethods = current.paymentMethods,
                    selectedPayment = current.selectedPayment,
                    printReceipt = current.printReceipt,
                    printerLabel = current.printerLabel,
                    printerReady = current.printerReady,
                    printerMessage = printerMessage,
                    printerMessageError = printerMessageError,
                    pendingOrderCount = current.pendingOrderCount
                )
            }

            offlineQueued -> {
                CartState(
                    error = offlineMessage,
                    customers = current.customers,
                    selectedCustomer = current.selectedCustomer,
                    paymentMethods = current.paymentMethods,
                    selectedPayment = current.selectedPayment,
                    printReceipt = current.printReceipt,
                    printerLabel = current.printerLabel,
                    printerReady = current.printerReady,
                    printerMessage = printerMessage,
                    printerMessageError = printerMessageError,
                    pendingOrderCount = current.pendingOrderCount
                )
            }

            else -> {
                current.copy(
                    isSubmitting = false,
                    error = failureMessage,
                    printerMessage = printerMessage,
                    printerMessageError = printerMessageError
                )
            }
        }

        _state.value = updatedState.copy(isSubmitting = false)
        if (summary != null || offlineQueued || onlineSuccessWithoutSummary) {
            val event = when {
                summary != null && holdTitle != null -> CartEvent.OrderPlacedOnHold
                summary != null -> CartEvent.OrderCompleted
                offlineQueued -> CartEvent.OrderQueuedOffline
                else -> CartEvent.OrderCompleted
            }
            _events.emit(event)
        }
        return if (summary != null) {
            Result.success(summary)
        } else if (offlineQueued || onlineSuccessWithoutSummary) {
            Result.success(null)
        } else {
            Result.failure(result.exceptionOrNull() ?: queueError ?: IllegalStateException(failureMessage))
        }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun customerDisplayName(customer: Customer): String {
        val fullName = listOfNotNull(customer.firstName, customer.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return when {
            fullName.isNotBlank() -> fullName
            !customer.name.isNullOrBlank() -> customer.name.orEmpty()
            !customer.username.isNullOrBlank() -> customer.username.orEmpty()
            else -> appContext.getString(R.string.customer_fallback_name, customer.id)
        }
    }

    /**
     * Sort customers on IO dispatcher to avoid blocking main thread.
     * Uses simplified display name calculation that doesn't require appContext resources.
     */
    private fun sortCustomersOnBackground(
        customers: List<Customer>,
        targetFirstName: String
    ): List<Customer> {
        return customers.sortedWith(
            compareByDescending<Customer> { it.firstName?.equals(targetFirstName, ignoreCase = true) == true }
                .thenByDescending { it.isDefault == true }
                .thenBy { customerDisplayNameForSorting(it).lowercase() }
        )
    }

    /**
     * Simplified display name for sorting - doesn't require appContext.
     */
    private fun customerDisplayNameForSorting(customer: Customer): String {
        val fullName = listOfNotNull(customer.firstName, customer.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return when {
            fullName.isNotBlank() -> fullName
            !customer.name.isNullOrBlank() -> customer.name.orEmpty()
            !customer.username.isNullOrBlank() -> customer.username.orEmpty()
            else -> "" // Empty fallback for sorting
        }
    }

    private suspend fun fetchPrinterConfig(): PrinterConfig {
        return withContext(Dispatchers.IO) { settingsRepository.printerConfigFlow.first() }
    }

    private fun applyPrinterStatus(config: PrinterConfig) {
        val label = formatPrinterLabel(config)
        val ready = isPrinterConfigured(config)
        _state.update { state ->
            state.copy(printerLabel = label, printerReady = ready)
        }
    }

    private fun isPrinterConfigured(config: PrinterConfig): Boolean {
        return when (config.type) {
            PrinterType.Bluetooth -> !config.macAddress.isNullOrBlank()
            PrinterType.Tcp -> !config.host.isNullOrBlank()
        }
    }

    private fun formatPrinterLabel(config: PrinterConfig): String? {
        return when (config.type) {
            PrinterType.Bluetooth -> config.macAddress?.takeIf { it.isNotBlank() }?.let {
                appContext.getString(R.string.printer_label_bluetooth, it)
            }
            PrinterType.Tcp -> config.host?.takeIf { it.isNotBlank() }?.let { host ->
                if (config.port != 0) {
                    appContext.getString(R.string.printer_label_tcp_with_port, host, config.port)
                } else {
                    appContext.getString(R.string.printer_label_tcp, host)
                }
            }
        }
    }

    private fun defaultOrderType(): OrderType {
        return OrderType(
            identifier = "takeaway",
            label = appContext.getString(R.string.order_type_take_away)
        )
    }
}

sealed interface CartEvent {
    data object OrderCompleted : CartEvent
    data object OrderPlacedOnHold : CartEvent
    data object OrderQueuedOffline : CartEvent
    data class OfflineSyncFailed(val count: Int) : CartEvent
    data class Error(val message: String) : CartEvent
}
