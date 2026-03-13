package com.nexopos.desktop.ui.pos

import com.nexopos.desktop.core.network.*
import com.nexopos.desktop.core.repo.*
import com.nexopos.desktop.core.repo.getDefaultUnitQuantity
import com.nexopos.desktop.core.repo.getPrice
import com.nexopos.desktop.core.repo.getUnitQuantity
import com.nexopos.shared.models.Customer
import com.nexopos.shared.models.PaymentMethod
import com.nexopos.shared.models.Product
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import com.nexopos.shared.repo.ProductRepository as IProductRepository
import com.nexopos.shared.repo.CustomerRepository as ICustomerRepository
import com.nexopos.shared.repo.OrderRepository as IOrderRepository
import com.nexopos.shared.repo.RegisterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.Closeable
import java.util.UUID

/**
 * Consolidated UI state to reduce recomposition overhead.
 * All UI state is combined into a single object to prevent multiple StateFlow collections.
 */
data class POSUIState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategory: CategoryEntity? = null,
    val allProducts: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val searchTerm: String = "",
    val customers: List<Customer> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedCustomer: Customer? = null,
    val selectedPaymentMethod: PaymentMethod? = null,
    val cart: List<CartItem> = emptyList(),
    val discountType: DiscountType = DiscountType.Percent,
    val discountValue: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentRegister: Register? = null,
    val registers: List<Register> = emptyList(),
    val registerHistory: List<RegisterHistory> = emptyList()
)

/**
 * POS ViewModel (matching Android app's CartViewModel pattern)
 * Manages POS state and business logic
 *
 * IMPORTANT: Call dispose() when ViewModel is no longer needed to prevent memory leaks.
 */
class POSViewModel(
    private val productRepo: IProductRepository,
    private val customerRepo: ICustomerRepository,
    private val orderRepo: IOrderRepository,
    private val categoryRepo: CategoryRepository,
    private val registerRepo: RegisterRepository
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Single UI state flow for performance optimization
    private val _uiState = MutableStateFlow(POSUIState())
    val uiState: StateFlow<POSUIState> = _uiState.asStateFlow()
    
    // Update the combined UI state whenever individual states change
    private fun updateUIState() {
        _uiState.value = POSUIState(
            categories = _categories.value,
            selectedCategory = _selectedCategory.value,
            allProducts = _allProducts.value,
            filteredProducts = filteredProducts.value,
            searchTerm = _searchTerm.value,
            customers = _customers.value,
            paymentMethods = _paymentMethods.value,
            selectedCustomer = _selectedCustomer.value,
            selectedPaymentMethod = _selectedPaymentMethod.value,
            cart = _cart.value,
            discountType = _discountType.value,
            discountValue = _discountValue.value,
            isLoading = _isLoading.value,
            error = _error.value,
            currentRegister = _currentRegister.value,
            registers = _registers.value,
            registerHistory = _registerHistory.value
        )
    }
    
    // Legacy individual StateFlows (kept for backward compatibility during transition)
    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    val selectedCategory: StateFlow<CategoryEntity?> = _selectedCategory.asStateFlow()

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _paymentMethods = MutableStateFlow<List<PaymentMethod>>(emptyList())
    val paymentMethods: StateFlow<List<PaymentMethod>> = _paymentMethods.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow<PaymentMethod?>(null)
    val selectedPaymentMethod: StateFlow<PaymentMethod?> = _selectedPaymentMethod.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _useWholesalePrice = MutableStateFlow(false)
    val useWholesalePrice: StateFlow<Boolean> = _useWholesalePrice.asStateFlow()

    private val _discountType = MutableStateFlow(DiscountType.Percent)
    val discountType: StateFlow<DiscountType> = _discountType.asStateFlow()

    private val _discountValue = MutableStateFlow(0.0)
    val discountValue: StateFlow<Double> = _discountValue.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Register state
    private val _currentRegister = MutableStateFlow<Register?>(null)
    val currentRegister: StateFlow<Register?> = _currentRegister.asStateFlow()
    
    private val _registers = MutableStateFlow<List<Register>>(emptyList())
    val registers: StateFlow<List<Register>> = _registers.asStateFlow()
    
    private val _registerHistory = MutableStateFlow<List<RegisterHistory>>(emptyList())
    val registerHistory: StateFlow<List<RegisterHistory>> = _registerHistory.asStateFlow()

    // Search term for filtering products
    private val _searchTerm = MutableStateFlow("")
    val searchTerm: StateFlow<String> = _searchTerm.asStateFlow()

    // All products and filtered by category + search
    private val _allProducts = MutableStateFlow<List<Product>>(emptyList())
    
    // Pagination for large product lists
    private val _visibleProducts = MutableStateFlow<List<Product>>(emptyList())
    val visibleProducts: StateFlow<List<Product>> = _visibleProducts.asStateFlow()
    private var currentProductLimit = 50
    
    fun loadMoreProducts() {
        val current = _allProducts.value
        val currentVisible = _visibleProducts.value.size
        if (currentVisible < current.size) {
            currentProductLimit += 50
            updateVisibleProducts(current)
        }
    }
    
    private fun updateVisibleProducts(products: List<Product>) {
        _visibleProducts.value = products.take(currentProductLimit)
    }

    // Optimized filtered products with better performance
    val filteredProducts: StateFlow<List<Product>> = combine(
        _allProducts,
        _searchTerm.debounce(300),  // Debounce search input
        _selectedCategory
    ) { products: List<Product>, term: String, category: CategoryEntity? ->
        // Filter products efficiently
        if (term.isNotBlank()) {
            val lowerTerm = term.lowercase()
            products.filter { product ->
                product.name.lowercase().contains(lowerTerm) ||
                product.barcode?.lowercase()?.contains(lowerTerm) == true ||
                product.sku?.lowercase()?.contains(lowerTerm) == true
            }
        } else if (category != null) {
            products.filter { it.categoryId == category.id }
        } else {
            products
        }
    }.flowOn(Dispatchers.Default)  // Run filtering on background thread
    .stateIn(scope, SharingStarted.Lazily, emptyList())

    init {
        loadData()
        
        // Sync filtered products changes to UI state
        scope.launch {
            filteredProducts.collect {
                updateUIState()
            }
        }
        
        // Sync cart changes to UI state
        scope.launch {
            _cart.collect {
                updateUIState()
            }
        }
        
        // Sync customer/payment selections to UI state
        scope.launch {
            _selectedCustomer.collect {
                updateUIState()
            }
        }
        
        scope.launch {
            _selectedPaymentMethod.collect {
                updateUIState()
            }
        }
    }

    private fun loadData() {
        scope.launch {
            val startTime = System.currentTimeMillis()
            _isLoading.value = true
            println("[POSViewModel] Loading data from local cache (parallel)...")

            // PERFORMANCE FIX: Load all data in parallel with coroutineScope
            // Instead of sequential loading (wait for each), all load simultaneously
            coroutineScope {
                val catsDeferred = async(Dispatchers.IO) {
                    categoryRepo.getAllCategories().firstOrNull()
                }
                val productsDeferred = async(Dispatchers.IO) {
                    productRepo.getAllProducts()
                }
                val customersDeferred = async(Dispatchers.IO) {
                    customerRepo.getAllCustomers()
                }
                val paymentsDeferred = async(Dispatchers.IO) {
                    orderRepo.getPaymentMethods()
                }
                val defaultCustomerDeferred = async(Dispatchers.IO) {
                    customerRepo.getDefaultCustomer()
                }
                // Add registers loading
                val registersDeferred = async(Dispatchers.IO) {
                    try {
                        registerRepo.getRegisters()
                    } catch (e: Exception) {
                        emptyList<Register>()
                    }
                }
                val usedRegisterDeferred = async(Dispatchers.IO) {
                    try {
                        registerRepo.getUsedRegister()
                    } catch (e: Exception) {
                        null
                    }
                }

                // Wait for all results
                val cats = catsDeferred.await()
                val products = productsDeferred.await()
                val customers = customersDeferred.await()
                val paymentMethodsResult = paymentsDeferred.await()
                val defaultCustomer = defaultCustomerDeferred.await()

                // Process categories
                cats?.let { categories ->
                    println("[POSViewModel] ✓ Loaded ${categories.size} categories from local cache")
                    val filteredCats = categories.filter { cat -> cat.name.uppercase().startsWith("ALJAZIRA") }.take(2)
                    _categories.value = if (filteredCats.isNotEmpty()) filteredCats else categories.take(2)
                }

                // Process products (now uses cached Moshi instance - 6500ms faster)
                println("[POSViewModel] ✓ Loaded ${products.size} products from local cache")
                _allProducts.value = products
                updateVisibleProducts(products) // Initialize pagination

                // Process customers
                println("[POSViewModel] ✓ Loaded ${customers.size} customers from cache")
                _customers.value = customers
                if (_selectedCustomer.value == null) {
                    _selectedCustomer.value = defaultCustomer ?: customers.firstOrNull()
                }

                // Process payment methods
                if (paymentMethodsResult.isSuccess) {
                    val methods = paymentMethodsResult.getOrNull() ?: emptyList()
                    println("[POSViewModel] ✓ Loaded ${methods.size} payment methods from cache")
                    _paymentMethods.value = methods
                    if (_selectedPaymentMethod.value == null) {
                        _selectedPaymentMethod.value = methods.firstOrNull { it.selected == true } ?: methods.firstOrNull()
                    }
                }
                
                // Process registers
                val registers = registersDeferred.await()
                val usedRegister = usedRegisterDeferred.await()
                _registers.value = registers
                if (usedRegister != null) {
                    _currentRegister.value = usedRegister
                    // Load history for used register
                    launch {
                        try {
                            val history = registerRepo.getSessionHistory(usedRegister.id)
                            _registerHistory.value = history
                        } catch (e: Exception) {
                            println("[POSViewModel] Failed to load register history: ${e.message}")
                        }
                    }
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            _isLoading.value = false
            updateUIState() // Update combined UI state after loading all data
            println("[POSViewModel] Data loading complete in ${elapsedTime}ms")
        }
    }

    fun selectCategory(category: CategoryEntity?) {
        _selectedCategory.value = category
        _searchTerm.value = "" // Clear search when changing category
        updateUIState()
    }

    fun setSearchTerm(term: String) {
        _searchTerm.value = term
        updateUIState()
    }

    private var hasRefreshed = false

    fun refreshData() {
        // Prevent multiple refreshes
        if (_isLoading.value) return

        scope.launch {
            _isLoading.value = true
            _error.value = null

            println("[POSViewModel] Refreshing data from server (single API call)...")

            try {
                // FIX: Categories refresh forces API call, others use 30-second cached response
                val categoryResult = categoryRepo.refreshCategories() // forceRefresh=true
                println("[POSViewModel] Categories refresh: ${if (categoryResult.isSuccess) "success" else "failed: ${categoryResult.exceptionOrNull()?.message}"}")

                // These use cached bootstrap data (30-second cache)
                val productResult = productRepo.refreshProducts()
                println("[POSViewModel] Products refresh: ${if (productResult.isSuccess) "success" else "failed: ${productResult.exceptionOrNull()?.message}"}")

                val customerResult = customerRepo.refreshCustomers()
                println("[POSViewModel] Customers refresh: ${if (customerResult.isSuccess) "success" else "failed: ${customerResult.exceptionOrNull()?.message}"}")

                val paymentResult = orderRepo.refreshPaymentMethods()
                println("[POSViewModel] Payment methods refresh: ${if (paymentResult.isSuccess) "success" else "failed: ${paymentResult.exceptionOrNull()?.message}"}")

                hasRefreshed = true

                // Reload local data after sync
                loadData()
            } catch (e: Exception) {
                println("[POSViewModel] Refresh error: ${e.message}")
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun refreshDataIfNeeded() {
        if (!hasRefreshed && _allProducts.value.isEmpty()) {
            refreshData()
        }
    }

    fun searchByBarcode(barcode: String, onResult: (Product?) -> Unit) {
        scope.launch {
            val result = productRepo.searchByBarcode(barcode)
            onResult(result.getOrNull())
        }
    }

    fun selectCustomer(customer: Customer) {
        _selectedCustomer.value = customer
        updateUIState()
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _selectedPaymentMethod.value = method
        updateUIState()
    }

    fun setDiscountType(type: DiscountType) {
        _discountType.value = type
        updateUIState()
    }

    fun setDiscountValue(value: Double) {
        _discountValue.value = value
        updateUIState()
    }

    fun setUseWholesalePrice(use: Boolean) {
        _useWholesalePrice.value = use
        updateUIState()
    }

    fun setItemPrice(item: CartItem, newPrice: Double) {
    val currentCart = _cart.value.toMutableList()
    val index = currentCart.indexOfFirst { it.key == item.key }
    if (index != -1) {
        val currentItem = currentCart[index]
        
        // Get original retail price
        val originalProduct = currentItem.product
        val originalUq = originalProduct.getUnitQuantity(currentItem.unitQuantityId ?: 0)
        val originalRetailPrice = originalUq?.effectivePrice ?: currentItem.unitPrice
        
        // Check what price this matches
        val matchesWholesale = currentItem.wholesalePrice != null && 
                               Math.abs(newPrice - currentItem.wholesalePrice) < 0.001
        
        val matchesRetail = Math.abs(newPrice - originalRetailPrice) < 0.001
        
        currentCart[index] = currentItem.copy(
            unitPrice = newPrice,
            isCustomPrice = !matchesWholesale && !matchesRetail, // Only custom if not wholesale OR retail
            isWholesale = matchesWholesale // Set wholesale if price matches wholesale
        )
        _cart.value = currentCart
        updateUIState()
    }
}

    fun toggleItemWholesale(item: CartItem) {
    val currentCart = _cart.value.toMutableList()
    val index = currentCart.indexOfFirst { it.key == item.key }
    if (index != -1) {
        val currentItem = currentCart[index]
        val newWholesaleState = !currentItem.isWholesale
        
        // Get the original product and unit quantity to find the retail price
        val originalProduct = currentItem.product
        val originalUq = originalProduct.getUnitQuantity(currentItem.unitQuantityId ?: 0)
        val originalRetailPrice = originalUq?.effectivePrice ?: currentItem.unitPrice
        
        // When switching FROM wholesale TO retail, use the original retail price
        // When switching FROM retail TO wholesale, use the wholesale price
        val newPrice = if (newWholesaleState && currentItem.wholesalePrice != null) {
            // Switching to wholesale: use wholesale price
            currentItem.wholesalePrice ?: currentItem.unitPrice
        } else {
            // Switching to retail: use original retail price
            originalRetailPrice
        }
        
        // Check if the new price matches expected prices
        val matchesWholesale = newWholesaleState && 
                              currentItem.wholesalePrice != null && 
                              Math.abs(newPrice - currentItem.wholesalePrice) < 0.001
        
        val matchesRetail = !newWholesaleState && 
                           Math.abs(newPrice - originalRetailPrice) < 0.001
        
        // Only mark as custom price if price doesn't match expected price
        val shouldBeCustomPrice = !matchesWholesale && !matchesRetail
        
        currentCart[index] = currentItem.copy(
            isWholesale = newWholesaleState,
            unitPrice = newPrice,
            isCustomPrice = shouldBeCustomPrice
        )
        _cart.value = currentCart
        updateUIState()
    }
}

    /**
     * ROBUST: Validate product before adding to cart
     * Returns validation result to determine if dialog is needed
     */
    sealed class ProductValidation {
        data class ReadyToAdd(val product: Product, val unitQuantity: UnitQuantity) : ProductValidation()
        data class NeedsVariation(val product: Product) : ProductValidation()
        data class NeedsQuantityDialog(val product: Product, val unitQuantity: UnitQuantity) : ProductValidation()
        data class NeedsPrice(val product: Product, val unitQuantity: UnitQuantity) : ProductValidation()
    }

    fun validateProduct(product: Product, unitQuantityId: Long? = null): ProductValidation {
        // Check for variations first
        if (unitQuantityId == null && product.hasVariations()) {
            return ProductValidation.NeedsVariation(product)
        }

        val uq = unitQuantityId?.let { product.getUnitQuantity(it) } ?: product.getDefaultUnitQuantity()

        // Check if we have a valid unit quantity
        if (uq == null) {
            // Fallback unit quantity
            val fallbackUq = UnitQuantity(
                id = 0,
                unitId = 1,
                salePrice = 0.0,
                unit = UnitDetail(id = 1, name = "PCS", identifier = "piece")
            )
            return ProductValidation.NeedsPrice(product, fallbackUq)
        }

        // Check if price is missing (needs price dialog)
        if (uq.effectivePrice <= 0.0) {
            return ProductValidation.NeedsPrice(product, uq)
        }

        // Ready to add - show quantity dialog for user convenience
        return ProductValidation.NeedsQuantityDialog(product, uq)
    }

    /**
     * ROBUST: Add to cart with explicit unit quantity (called after validation/dialog)
     */
    fun confirmAddToCart(
        product: Product,
        unitQuantity: UnitQuantity,
        quantity: Double = 1.0,
        customPrice: Double? = null
    ) {
        if (_cart.value.size >= MAX_CART_SIZE) {
            throw IllegalStateException("Cart full")
        }

        val currentCart = _cart.value.toMutableList()
        val cartKey = "${product.id}_${unitQuantity.id}"

        val existing = currentCart.find { it.key == cartKey }

        if (existing != null) {
            // Update existing item quantity
            val index = currentCart.indexOf(existing)
            currentCart[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            // Add new item
            val retailPrice = customPrice ?: unitQuantity.effectivePrice
            // Use ONLY API-provided wholesale price with tax (server already includes tax)
            // If not available, set to null (no wholesale pricing)
            val wholesalePrice = unitQuantity.wholesalePriceWithTax

            currentCart.add(CartItem(
                key = cartKey,
                product = product,
                quantity = quantity,
                unitPrice = retailPrice,
                wholesalePrice = wholesalePrice,
                isWholesale = false,
                unitQuantityId = unitQuantity.id,
                unitId = unitQuantity.unitId,
                unitName = unitQuantity.unitName,
                isCustomPrice = customPrice != null
            ))
        }

        _cart.value = currentCart
    }

    fun addToCart(product: Product?, unitQuantityId: Long? = null) {
        if (product == null) return
        if (_cart.value.size >= MAX_CART_SIZE) {
            throw IllegalStateException("Cart full")
        }

        val uq = if (unitQuantityId != null) {
            product.getUnitQuantity(unitQuantityId)
        } else {
            product.getDefaultUnitQuantity()
        } ?: return

        val cartKey = "${product.id}_${uq.id}"
        val existingItem = _cart.value.find { it.key == cartKey }

        if (existingItem != null) {
            // Update existing item quantity
            val currentCart = _cart.value.toMutableList()
            val index = currentCart.indexOfFirst { it.key == cartKey }
            currentCart[index] = existingItem.copy(quantity = existingItem.quantity + 1.0)
            _cart.value = currentCart
        } else {
            // Add new item
            val newItem = CartItem(
                key = cartKey,
                product = product,
                quantity = 1.0,
                unitPrice = uq.effectivePrice,
                wholesalePrice = uq.wholesalePriceWithTax,
                isWholesale = false,
                unitQuantityId = uq.id,
                unitId = uq.unitId,
                unitName = uq.unitName,
                isCustomPrice = false
            )
            _cart.value = _cart.value + newItem
        }
        updateUIState()
    }

    fun updateQuantity(cartKey: String, quantity: Double) {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.key == cartKey }

        if (index != -1) {
            if (quantity > 0) {
                currentCart[index] = currentCart[index].copy(quantity = quantity)
            } else {
                currentCart.removeAt(index)
            }
            _cart.value = currentCart
            updateUIState()
        }
    }

    fun removeFromCart(cartKey: String) {
        _cart.value = _cart.value.filter { it.key != cartKey }
        updateUIState()
    }

    fun clearCart() {
        _cart.value = emptyList()
        updateUIState()
    }

    fun increaseQuantity(item: CartItem) {
        updateQuantity(item.key, item.quantity + 1.0)
    }

    fun decreaseQuantity(item: CartItem) {
        if (item.quantity > 1) {
            updateQuantity(item.key, item.quantity - 1.0)
        } else {
            removeFromCart(item.key)
        }
    }

    fun setQuantity(item: CartItem, newQuantity: Double) {
        updateQuantity(item.key, newQuantity)
    }

    /**
     * Add a quick product (custom item with no product ID)
     */
    fun addQuickProduct(name: String, quantity: Double, price: Double) {
        val trimmedName = name.trim()
        // Basic validation: non-empty, length, and simple characters only
        val isNameValid = trimmedName.isNotEmpty() &&
            trimmedName.length <= 255 &&
            trimmedName.all { ch ->
                ch.isLetterOrDigit() || ch.isWhitespace() || ch == '-' || ch == '_'
            }

        val isQuantityValid = quantity > 0.0 && quantity <= 9999.0
        val isPriceValid = price >= 0.0 && price <= 999_999.99

        if (!isNameValid || !isQuantityValid || !isPriceValid) return

        val currentCart = _cart.value.toMutableList()
        val cartKey = "quick_${System.currentTimeMillis()}"

        // Create a dummy product for quick products (manually entered)
        val quickProduct = Product(
            id = 0L,
            name = trimmedName,
            barcode = null,
            barcodeType = null,
            sku = null,
            status = "available",
            categoryId = null,
            unitQuantities = null
        )

        currentCart.add(CartItem(
            key = cartKey,
            product = quickProduct,
            quantity = quantity,
            unitPrice = price,
            wholesalePrice = null,
            isWholesale = false,
            unitQuantityId = null,
            unitId = 1L,
            unitName = "PCS",
            isCustomPrice = true
        ))

        _cart.value = currentCart
    }

    fun submitOrder(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val customer = _selectedCustomer.value
        val paymentMethod = _selectedPaymentMethod.value
        val cartItems = _cart.value
        val currentReg = _currentRegister.value
        
        if (currentReg == null) {
            onError("No cash register selected. Please open a register first.")
            return
        }

        if (customer == null) {
            onError("No customer selected")
            return
        }

        if (paymentMethod == null) {
            onError("No payment method selected")
            return
        }

        if (cartItems.isEmpty()) {
            onError("Cart is empty")
            return
        }

        scope.launch {
            _isLoading.value = true
            println("[POSViewModel] Submitting order with ${cartItems.size} items...")

            val subtotal = cartItems.sumOf { it.total }
            val discountAmount = if (_discountType.value == DiscountType.Percent) {
                subtotal * (_discountValue.value / 100.0)
            } else {
                _discountValue.value
            }.coerceAtMost(subtotal)

            val discountPercentage = if (_discountType.value == DiscountType.Percent) _discountValue.value else 0.0
            val total = subtotal - discountAmount // VAT is inclusive (prices already include VAT)
            // Note: Tax calculation should come from server configuration, not hard-coded
            // For now, keeping existing tax extraction logic but this should be API-driven
            val taxValue = total - (total / 1.19) // Extract VAT from total (19% Tunisia rate)
            val subtotalWithoutTax = total / 1.19

            // Build order products matching Android app format (VAT inclusive)
            val orderProducts = cartItems.map { item ->
                val price = item.effectivePrice
                val lineTotal = price * item.quantity
                // Tax extraction - should ideally come from server config
                val lineTotalWithoutTax = lineTotal / 1.19 // 19% Tunisia VAT
                val lineTax = lineTotal - lineTotalWithoutTax

                OrderProductRequest(
                    productId = item.product.id,
                    name = item.product.name,
                    quantity = item.quantity.toDouble(),
                    unitQuantityId = item.unitQuantityId,
                    unitId = item.unitId,
                    unitName = item.unitName,
                    unitPrice = price,
                    priceWithTax = price, // API provides price with tax
                    priceWithoutTax = price / 1.19, // Extract base price (19% Tunisia VAT)
                    totalPrice = lineTotal,
                    totalPriceWithTax = lineTotal, // Total already includes tax
                    totalPriceWithoutTax = lineTotalWithoutTax,
                    totalTaxValue = lineTax
                )
            }

            // Build customer object for request
            val customerObj = Customer(
                id = customer.id,
                username = customer.username,
                name = customer.name,
                firstName = customer.firstName,
                lastName = customer.lastName,
                email = customer.email,
                phone = customer.phone,
                isDefault = customer.isDefault
            )

            val orderRequest = CreateOrderRequest(
                title = "",
                type = OrderTypeRequest(identifier = "takeaway", label = "Take Away"),
                customerId = customer.id,
                customer = customerObj,
                products = orderProducts,
                payments = listOf(
                    OrderPaymentRequest(
                        identifier = paymentMethod.identifier,
                        value = total,
                        label = paymentMethod.label
                    )
                ),
                subtotal = subtotal,
                total = total,
                tendered = total,
                change = 0.0,
                discountAmount = discountAmount,
                discountType = if (_discountType.value == DiscountType.Percent) "percentage" else "flat",
                discountPercentage = discountPercentage,
                totalProducts = cartItems.sumOf { it.quantity.toInt() },
                taxValue = taxValue,
                productsTaxValue = taxValue,
                register_id = currentReg.id,
                clientReference = UUID.randomUUID().toString()
            )

            println("[POSViewModel] Order request: total=$total, products=${orderProducts.size}")

            try {
                val result = orderRepo.createOrder(orderRequest)

                _isLoading.value = false

                if (result.isSuccess) {
                    println("[POSViewModel] Order submitted successfully!")
                    
                    // CRITICAL: Refresh register data to show updated balance/history
                    refreshRegisterAfterOrder(currentReg.id)
                    
                    clearCart()

                    // OPTIMIZED: Don't reload all data - just reset selections
                    val currentCustomer = _selectedCustomer.value
                    val currentPayment = _selectedPaymentMethod.value

                    // Only reset to default if current is not default
                    if (currentCustomer?.isDefault != true) {
                        val defaultCustomer = customerRepo.getDefaultCustomer() ?: _customers.value.firstOrNull()
                        println("[POSViewModel] Resetting to default customer: ${defaultCustomer?.name}")
                        _selectedCustomer.value = defaultCustomer
                    }

                    if (currentPayment?.selected != true) {
                        val defaultPayment = _paymentMethods.value.firstOrNull { it.selected == true } ?: _paymentMethods.value.firstOrNull()
                        _selectedPaymentMethod.value = defaultPayment
                    }

                    // REMOVED: loadData() call that was causing grid reload
                    onSuccess()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to submit order"
                    println("[POSViewModel] Order failed: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                println("[POSViewModel] Order exception: ${e.message}")
                e.printStackTrace()
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun refreshRegisterAfterOrder(registerId: Int) {
        try {
            println("[POSViewModel] Refreshing register data after order...")
            
            // 1. Refresh the register details
            val updatedRegister = registerRepo.getRegister(registerId)
            _currentRegister.value = updatedRegister
            println("[POSViewModel] Register balance updated: ${updatedRegister.balance} DT")
            
            // 2. Refresh the register history
            val history = registerRepo.getSessionHistory(registerId)
            _registerHistory.value = history
            println("[POSViewModel] Register history refreshed: ${history.size} entries")
            
            // 3. Optional: Refresh the registers list (for dropdown)
            val allRegisters = registerRepo.getRegisters()
            _registers.value = allRegisters
            
        } catch (e: Exception) {
            println("[POSViewModel] Failed to refresh register after order: ${e.message}")
            // Don't fail the order if refresh fails
        }
    }

    fun onCleared() {
        scope.coroutineContext.cancel()
    }

    private fun parseUnitQuantities(json: String): List<UnitQuantity>? {
        return try {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter<List<UnitQuantity>>(
                com.squareup.moshi.Types.newParameterizedType(
                    List::class.java,
                    UnitQuantity::class.java
                )
            )
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dispose of the ViewModel and cancel all coroutines.
     * This prevents memory leaks by cleaning up the coroutine scope.
     */
    override fun close() {
        scope.cancel()
    }
    
    // Add register management functions
    fun openRegister(registerId: Int, amount: Double, description: String = "") {
        scope.launch {
            try {
                val register = registerRepo.openRegister(registerId, amount, description)
                _currentRegister.value = register
                // Load history for the opened register
                val history = registerRepo.getSessionHistory(registerId)
                _registerHistory.value = history
            } catch (e: Exception) {
                _error.value = "Failed to open register: ${e.message}"
            }
        }
    }
    
    fun closeRegister(amount: Double, description: String = "") {
        scope.launch {
            val current = _currentRegister.value
            if (current != null) {
                try {
                    val register = registerRepo.closeRegister(current.id, amount, description)
                    _currentRegister.value = null
                    _registerHistory.value = emptyList()
                    
                    // Refresh the registers list
                    val refreshedRegisters = registerRepo.getRegisters()
                    _registers.value = refreshedRegisters
                    
                } catch (e: Exception) {
                    _error.value = "Failed to close register: ${e.message}"
                }
            }
        }
    }
    
    fun cashIn(amount: Double, description: String = "") {
        scope.launch {
            val current = _currentRegister.value
            if (current != null) {
                try {
                    val history = registerRepo.cashIn(current.id, amount, description)
                    // Add to history and refresh register
                    val updatedHistory = listOf(history) + _registerHistory.value
                    _registerHistory.value = updatedHistory
                    
                    // Refresh current register to get updated balance
                    val updatedRegister = registerRepo.getRegister(current.id)
                    _currentRegister.value = updatedRegister
                    
                } catch (e: Exception) {
                    _error.value = "Failed to cash in: ${e.message}"
                }
            }
        }
    }
    
    fun cashOut(amount: Double, description: String = "") {
        scope.launch {
            val current = _currentRegister.value
            if (current != null) {
                try {
                    val history = registerRepo.cashOut(current.id, amount, description)
                    // Add to history and refresh register
                    val updatedHistory = listOf(history) + _registerHistory.value
                    _registerHistory.value = updatedHistory
                    
                    // Refresh current register to get updated balance
                    val updatedRegister = registerRepo.getRegister(current.id)
                    _currentRegister.value = updatedRegister
                    
                } catch (e: Exception) {
                    _error.value = "Failed to cash out: ${e.message}"
                }
            }
        }
    }

    companion object {
        private const val MAX_CART_SIZE = 100
    }
}

/**
 * Cart item matching Android app's CartItem
 */
data class CartItem(
    val key: String,
    val product: Product,
    val quantity: Double,
    val unitPrice: Double,
    val wholesalePrice: Double? = null,
    val isWholesale: Boolean = false,
    val unitQuantityId: Long? = null,
    val unitId: Long? = null,
    val unitName: String? = null,
    val isCustomPrice: Boolean = false
) {
    val effectivePrice: Double get() = if (isWholesale && wholesalePrice != null) wholesalePrice else unitPrice
    val total: Double get() = effectivePrice * quantity
    val lineTotal: Double get() = total
    val needsPrice: Boolean get() = unitPrice <= 0.0 && !isCustomPrice
}

enum class DiscountType {
    Amount,
    Percent
}