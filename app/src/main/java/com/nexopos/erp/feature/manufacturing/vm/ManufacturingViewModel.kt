package com.nexopos.erp.feature.manufacturing.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.CreateProductionOrderRequest
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ProductionOrder
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.feature.manufacturing.ProductDto
import com.nexopos.erp.feature.manufacturing.ProductionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ManufacturingState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val isLoadingFormOptions: Boolean = false,
    val isCreatingBom: Boolean = false,
    val orders: List<ProductionOrder> = emptyList(),
    val filteredOrders: List<ProductionOrder> = emptyList(),
    val selectedStatusFilter: ProductionStatus? = null,
    val products: List<ProductDto> = emptyList(),
    val error: String? = null,
    val createSuccess: Boolean = false,
    val bomCreateSuccess: Boolean = false,
    // BOM-related state
    val boms: List<com.nexopos.erp.core.network.ManufacturingBom> = emptyList(),
    val isLoadingBoms: Boolean = false,
    val isRefreshingBoms: Boolean = false
)

class ManufacturingViewModel(
    private val mobileApi: MobileApi,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ManufacturingViewModel"
    }

    private val _state = MutableStateFlow(ManufacturingState())
    val state = _state.asStateFlow()

    /**
     * Load production orders from the API.
     * Fetches orders and updates state with results.
     */
    fun loadProductionOrders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = mobileApi.getManufacturingOrders()
                _state.value = _state.value.copy(
                    isLoading = false,
                    orders = response.data,
                    filteredOrders = response.data
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load production orders", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load production orders")
                )
            }
        }
    }

    /**
     * Refresh production orders (pull-to-refresh).
     * Same as loadProductionOrders but with isRefreshing flag.
     */
    fun refreshProductionOrders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val response = mobileApi.getManufacturingOrders()
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    orders = response.data,
                    filteredOrders = applyStatusFilter(response.data, _state.value.selectedStatusFilter)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh production orders", e)
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh production orders")
                )
            }
        }
    }

    /**
     * Create a new production order via API using a BOM.
     */
    fun createProductionOrder(
        bomId: Long,
        quantity: Double,
        unitId: Long? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true, error = null)
            try {
                val bom = _state.value.boms.firstOrNull { it.id == bomId }
                val productId = bom?.productId ?: bom?.product?.id
                val resolvedUnitId = unitId ?: bom?.unitId ?: bom?.unit?.id

                if (productId == null || resolvedUnitId == null) {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        error = "Unable to resolve product/unit for selected BOM"
                    )
                    return@launch
                }

                val request = CreateProductionOrderRequest(
                    bomId = bomId,
                    productId = productId,
                    unitId = resolvedUnitId,
                    quantity = quantity
                )
                
                val response = mobileApi.createManufacturingOrder(request)
                
                if (response.status == "success") {
                    // Add the new order to the list
                    val updatedOrders = _state.value.orders + response.data
                    _state.value = _state.value.copy(
                        isCreating = false,
                        createSuccess = true,
                        orders = updatedOrders,
                        filteredOrders = applyStatusFilter(updatedOrders, _state.value.selectedStatusFilter)
                    )
                } else {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        error = "Failed to create production order"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create production order", e)
                _state.value = _state.value.copy(
                    isCreating = false,
                    error = e.onlineOnlyMessage("Failed to create production order")
                )
            }
        }
    }

    /**
     * Update production order status (start or complete).
     * Calls the appropriate API endpoint based on the new status.
     */
    fun updateProductionStatus(orderId: Long, newStatus: ProductionStatus) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            try {
                val response = when (newStatus) {
                    ProductionStatus.IN_PROGRESS -> {
                        mobileApi.startManufacturing(orderId.toInt())
                    }
                    ProductionStatus.COMPLETED -> {
                        mobileApi.completeManufacturing(orderId.toInt())
                    }
                    else -> {
                        // For other statuses, we don't have API endpoints yet
                        Log.w(TAG, "No API endpoint for status: $newStatus")
                        return@launch
                    }
                }
                
                if (response.status == "success") {
                    // Update the order in the list
                    val updatedOrders = _state.value.orders.map { order ->
                        if (order.id == orderId) {
                            response.data
                        } else {
                            order
                        }
                    }
                    _state.value = _state.value.copy(
                        orders = updatedOrders,
                        filteredOrders = applyStatusFilter(updatedOrders, _state.value.selectedStatusFilter)
                    )
                } else {
                    _state.value = _state.value.copy(
                        error = "Failed to update production status"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update production status", e)
                _state.value = _state.value.copy(
                    error = e.onlineOnlyMessage("Failed to update production status")
                )
            }
        }
    }

    /**
     * Set status filter for the orders list.
     */
    fun setStatusFilter(status: ProductionStatus?) {
        val filtered = applyStatusFilter(_state.value.orders, status)
        _state.value = _state.value.copy(
            selectedStatusFilter = status,
            filteredOrders = filtered
        )
    }

    /**
     * Clear the create success flag.
     */
    fun clearCreateSuccess() {
        _state.value = _state.value.copy(createSuccess = false)
    }

    fun clearBomCreateSuccess() {
        _state.value = _state.value.copy(bomCreateSuccess = false)
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Load BOMs from the API.
     */
    fun loadBoms() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBoms = true, error = null)
            try {
                Log.d(TAG, "Loading BOMs from API")
                val response = mobileApi.getManufacturingBoms(limit = 200)
                Log.d(TAG, "BOMs loaded: ${response.data.size}")
                _state.value = _state.value.copy(
                    isLoadingBoms = false,
                    boms = response.data
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BOMs", e)
                _state.value = _state.value.copy(
                    isLoadingBoms = false,
                    error = e.onlineOnlyMessage("Failed to load BOMs")
                )
            }
        }
    }

    /**
     * Refresh BOMs (pull-to-refresh).
     */
    fun refreshBoms() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshingBoms = true, error = null)
            try {
                Log.d(TAG, "Refreshing BOMs from API")
                val response = mobileApi.getManufacturingBoms(limit = 200)
                Log.d(TAG, "BOMs refreshed: ${response.data.size}")
                _state.value = _state.value.copy(
                    isRefreshingBoms = false,
                    boms = response.data
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh BOMs", e)
                _state.value = _state.value.copy(
                    isRefreshingBoms = false,
                    error = e.onlineOnlyMessage("Failed to refresh BOMs")
                )
            }
        }
    }

    /**
     * Load products and units for manufacturing forms.
     */
    fun loadFormOptions() {
        val current = _state.value
        if (current.isLoadingFormOptions) {
            return
        }
        if (current.products.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingFormOptions = true, error = null)
            try {
                val productsResponse = mobileApi.getProducts(limit = 200)
                val products = productsResponse.data.map { product ->
                    val isManufacturable = product.isManufactured == true
                    if (!isManufacturable) {
                        return@map null
                    }
                    val allUnits = product.unitQuantities ?: emptyList()
                    val manufacturedUnits = allUnits.filter { it.isManufactured == true }
                    val preferredUnits = if (manufacturedUnits.isNotEmpty()) manufacturedUnits else allUnits
                    val unitQuantity = preferredUnits.firstOrNull()
                    ProductDto(
                        id = product.id.toInt(),
                        name = product.name,
                        sku = product.sku,
                        barcode = product.barcode,
                        unitId = unitQuantity?.unitId?.toInt(),
                        sellingPrice = unitQuantity?.salePrice,
                        purchasePrice = null,
                        unitQuantities = allUnits
                    )
                }.filterNotNull()
                _state.value = _state.value.copy(
                    isLoadingFormOptions = false,
                    products = products
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load manufacturing form options", e)
                _state.value = _state.value.copy(
                    isLoadingFormOptions = false,
                    error = e.onlineOnlyMessage("Failed to load form options")
                )
            }
        }
    }

    /**
     * Create a new BOM for mobile.
     */
    fun createBom(
        name: String,
        productId: Long,
        unitId: Long,
        quantity: Double,
        isActive: Boolean,
        description: String?
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreatingBom = true, error = null)
            try {
                val response = mobileApi.createManufacturingBom(
                    com.nexopos.erp.core.network.CreateBomRequest(
                        name = name,
                        productId = productId,
                        unitId = unitId,
                        quantity = quantity,
                        isActive = isActive,
                        description = description
                    )
                )
                val createdBom = response.data
                if (response.status == "success" && createdBom != null) {
                    val updated = listOf(createdBom) + _state.value.boms
                    _state.value = _state.value.copy(
                        isCreatingBom = false,
                        boms = updated,
                        bomCreateSuccess = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isCreatingBom = false,
                        error = response.message ?: "Failed to create BOM"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create BOM", e)
                _state.value = _state.value.copy(
                    isCreatingBom = false,
                    error = e.onlineOnlyMessage("Failed to create BOM")
                )
            }
        }
    }

    /**
     * Helper function to apply status filter to orders list.
     */
    private fun applyStatusFilter(
        orders: List<ProductionOrder>,
        status: ProductionStatus?
    ): List<ProductionOrder> {
        return if (status == null) {
            orders
        } else {
            orders.filter { ProductionStatus.fromValue(it.status) == status }
        }
    }
}
