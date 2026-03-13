package com.nexopos.erp.feature.inventory.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.network.ProductsResponse
import com.nexopos.erp.core.network.StockAdjustmentRequest
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.feature.inventory.InventoryItem
import com.nexopos.erp.feature.inventory.InventoryOperationType
import com.nexopos.erp.feature.inventory.StockStatus
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the Inventory list screen.
 */
data class InventoryState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isAdjusting: Boolean = false,
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val selectedStockFilter: StockStatus = StockStatus.ALL,
    val searchQuery: String = "",
    val hasMore: Boolean = true,
    val nextCursor: Long? = null,
    val error: String? = null,
    val adjustSuccess: Boolean = false
)

/**
 * ViewModel for Inventory feature.
 */
class InventoryViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {
    private companion object {
        const val PAGE_SIZE = 40
    }

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    init {
        loadInventory()
    }

    /**
     * Load all inventory items from the API.
     */
    fun loadInventory() {
        loadPage(refresh = false, reset = true)
    }

    /**
     * Refresh inventory (pull-to-refresh).
     */
    fun refreshInventory() {
        loadPage(refresh = true, reset = true)
    }

    fun loadMoreInventory() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) {
            return
        }
        loadPage(refresh = false, reset = false)
    }

    private fun loadPage(
        refresh: Boolean,
        reset: Boolean,
        queryOverride: String? = null
    ) {
        viewModelScope.launch {
            val current = _state.value
            val requestQuery = queryOverride ?: current.searchQuery
            _state.value = when {
                refresh -> current.copy(isRefreshing = true, error = null)
                reset -> current.copy(
                    isLoading = true,
                    error = null,
                    hasMore = true,
                    nextCursor = null,
                    searchQuery = requestQuery
                )
                else -> current.copy(isLoadingMore = true, error = null)
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    fetchProductsPage(
                        cursor = if (reset) null else current.nextCursor,
                        query = requestQuery
                    )
                }
                val incomingItems = withContext(Default) {
                    response.data.map(::toInventoryItem)
                }
                val mergedItems = if (reset) {
                    incomingItems
                } else {
                    (current.items + incomingItems).distinctBy { it.productId }
                }
                val filteredItems = withContext(Default) {
                    filterItems(
                        items = mergedItems,
                        status = current.selectedStockFilter,
                        query = requestQuery
                    )
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    items = mergedItems,
                    filteredItems = filteredItems,
                    hasMore = response.meta.hasMore,
                    nextCursor = response.meta.nextCursor,
                    searchQuery = requestQuery
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = e.onlineOnlyMessage("Failed to refresh inventory")
                )
            }
        }
    }

    /**
     * Create a stock adjustment.
     */
    fun createStockAdjustment(
        productId: Long,
        unitQuantityId: Long?,
        quantity: Int,
        operationType: InventoryOperationType,
        reason: String? = null,
        reference: String? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isAdjusting = true, error = null, adjustSuccess = false)
            
            try {
                val request = StockAdjustmentRequest(
                    productId = productId,
                    unitQuantityId = unitQuantityId,
                    adjustmentType = mapLegacyAdjustmentType(operationType),
                    operationType = mapOperationType(operationType),
                    quantity = quantity.toDouble(),
                    reason = reason,
                    reference = reference
                )
                
                val response = withContext(Dispatchers.IO) {
                    mobileApi.adjustStock(request)
                }
                
                if (response.status == "success" || response.data != null) {
                    val updatedItems = response.data?.let { applyStockAdjustmentResult(_state.value.items, it) }
                        ?: _state.value.items
                    _state.value = _state.value.copy(
                        isAdjusting = false,
                        adjustSuccess = true,
                        items = updatedItems,
                        filteredItems = filterItems(
                            items = updatedItems,
                            status = _state.value.selectedStockFilter,
                            query = _state.value.searchQuery
                        )
                    )
                    refreshInventory()
                } else {
                    _state.value = _state.value.copy(
                        isAdjusting = false,
                        error = response.message ?: "Failed to create adjustment"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAdjusting = false,
                    error = e.onlineOnlyMessage("Failed to create adjustment")
                )
            }
        }
    }

    /**
     * Set stock status filter.
     */
    fun setStockFilter(status: StockStatus) {
        viewModelScope.launch {
            val current = _state.value
            val filtered = withContext(Default) {
                filterItems(current.items, status, current.searchQuery)
            }
            _state.value = current.copy(
                selectedStockFilter = status,
                filteredItems = filtered
            )
        }
    }

    /**
     * Set search query filter.
     */
    fun setSearchQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery == _state.value.searchQuery) {
            return
        }

        loadPage(
            refresh = false,
            reset = true,
            queryOverride = normalizedQuery
        )
    }

    /**
     * Clear adjust success state.
     */
    fun clearAdjustSuccess() {
        _state.value = _state.value.copy(adjustSuccess = false)
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Map inventory adjustment operations to the same action strings used by the web app.
     */
    private fun mapOperationType(operationType: InventoryOperationType): String {
        return when (operationType) {
            InventoryOperationType.SET -> "set"
            InventoryOperationType.ADD -> "added"
            InventoryOperationType.DELETE -> "deleted"
            InventoryOperationType.DEFECTIVE -> "defective"
            InventoryOperationType.LOST -> "lost"
        }
    }

    private fun mapLegacyAdjustmentType(operationType: InventoryOperationType): String? {
        return when (operationType) {
            InventoryOperationType.SET -> null
            InventoryOperationType.ADD -> "add"
            InventoryOperationType.DELETE -> "remove"
            InventoryOperationType.DEFECTIVE -> "remove"
            InventoryOperationType.LOST -> "remove"
        }
    }

    private suspend fun fetchProductsPage(cursor: Long?, query: String): ProductsResponse {
        return mobileApi.getProducts(
            limit = PAGE_SIZE,
            cursor = cursor,
            search = query.takeIf { it.isNotBlank() }
        )
    }

    private fun applyStockAdjustmentResult(
        items: List<InventoryItem>,
        result: com.nexopos.erp.core.network.StockAdjustmentData
    ): List<InventoryItem> {
        return items.map { item ->
            if (item.productId != result.productId) {
                item
            } else {
                val nextQuantity = result.newQuantity.toInt()
                item.copy(
                    stockQuantity = nextQuantity
                )
            }
        }
    }

    private fun toInventoryItem(product: Product): InventoryItem {
        val unitQuantity = product.unitQuantities?.firstOrNull()
        val stockQuantity = product.stockQuantity?.toInt()
            ?: unitQuantity?.quantity?.toInt()
            ?: 0
        val lowStockThreshold = product.lowStockThreshold ?: 10

        return InventoryItem(
            productId = product.id,
            unitQuantityId = unitQuantity?.id,
            productName = product.name.ifBlank {
                product.sku?.takeIf { it.isNotBlank() }
                    ?: product.barcode?.takeIf { it.isNotBlank() }
                    ?: "Product #${product.id}"
            },
            sku = product.sku ?: "",
            categoryId = product.categoryId ?: 0L,
            categoryName = "",
            stockQuantity = stockQuantity,
            lowStockThreshold = lowStockThreshold,
            unitName = unitQuantity?.unitName ?: "pcs",
            barcode = product.barcode,
            imageUrl = null
        )
    }

    private fun filterItems(
        items: List<InventoryItem>,
        status: StockStatus,
        query: String
    ): List<InventoryItem> {
        var filtered = items
        
        // Filter by stock status
        filtered = when (status) {
            StockStatus.ALL -> filtered
            StockStatus.NORMAL -> filtered.filter { it.stockStatus == StockStatus.NORMAL }
            StockStatus.LOW_STOCK -> filtered.filter { it.stockStatus == StockStatus.LOW_STOCK }
            StockStatus.OUT_OF_STOCK -> filtered.filter { it.stockStatus == StockStatus.OUT_OF_STOCK }
        }
        
        // Filter by search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { item ->
                item.productName.contains(query, ignoreCase = true) ||
                item.sku.contains(query, ignoreCase = true) ||
                item.barcode?.contains(query, ignoreCase = true) == true
            }
        }
        
        return filtered
    }
}
