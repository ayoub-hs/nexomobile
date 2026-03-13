package com.nexopos.desktop.ui.pos

import com.nexopos.desktop.core.network.NexoApiClient
import com.nexopos.desktop.core.repo.OrderQueueRepository
import com.nexopos.desktop.core.repo.QueuedOrderEntity
import com.nexopos.desktop.core.repo.QueuedOrderStatus
import com.nexopos.desktop.core.repo.toRequest
import com.nexopos.shared.models.CreateOrderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Orders list item for UI display
 */
data class OrdersListItem(
    val id: Long,
    val serverId: Long?,
    val serverCode: String?,
    val clientReference: String,
    val createdAt: Long,
    val status: QueuedOrderStatus,
    val total: Double,
    val customerName: String?,
    val paymentStatus: String?,
    val isFromServer: Boolean,
    val request: CreateOrderRequest?  // Nullable for server-loaded orders
) {
    /** Display ID: server code if synced, otherwise local ID */
    val displayId: String
        get() = serverCode ?: serverId?.toString() ?: "#$id"
}

/**
 * Orders screen state
 */
data class OrdersState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isDeleting: Boolean = false,
    val items: List<OrdersListItem> = emptyList(),
    val filteredItems: List<OrdersListItem> = emptyList(),
    val customerFilter: String = "",
    val message: String? = null,
    val messageError: Boolean = false,
    val hasMorePages: Boolean = false,
    val nextCursor: Long? = null,
    val deleteError: String? = null
)

/**
 * Desktop OrdersViewModel
 * Manages order history, filtering, pagination, and deletion
 */
class OrdersViewModel(
    private val queueRepository: OrderQueueRepository,
    private val api: NexoApiClient
) {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()
    
    private var currentServerFilter: String = ""
    
    init {
        println("[OrdersViewModel] Initializing...")
        // OPTIMIZED: Don't observe local flow to prevent duplicates
        // Orders are loaded directly from API with pagination
        // Local flow observation disabled to avoid duplicate orders after submission
        
        // Load from server API with pagination (20 orders)
        refreshFromServer()
    }
    
    /**
     * Set customer filter
     */
    fun setCustomerFilter(filter: String) {
        _state.value = _state.value.copy(customerFilter = filter)
        
        // If filter changed significantly, fetch from server with filter
        if (filter.length >= 2 && filter != currentServerFilter) {
            currentServerFilter = filter
            refreshFromServer(serverFilter = filter)
        } else if (filter.isBlank() && currentServerFilter.isNotBlank()) {
            currentServerFilter = ""
            refreshFromServer()
        } else {
            // Apply local filter only
            val items = _state.value.items
            _state.value = _state.value.copy(
                filteredItems = applyLocalFilter(items, filter)
            )
        }
    }
    
    /**
     * Apply local filter to orders
     */
    private fun applyLocalFilter(items: List<OrdersListItem>, filter: String): List<OrdersListItem> {
        if (filter.isBlank()) return items
        val lowerFilter = filter.lowercase()
        return items.filter { item ->
            item.customerName?.lowercase()?.contains(lowerFilter) == true
        }
    }
    
    /**
     * Refresh orders from server
     * OPTIMIZED: Loads 20 orders at a time with pagination
     */
    fun refreshFromServer(serverFilter: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, isLoading = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    api.getMobileOrders(
                        cursor = null,
                        limit = 20,  // Load 20 orders per page
                        customerFilter = serverFilter
                    )
                }
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    val serverOrders = response?.data ?: emptyList()
                    println("[OrdersViewModel] Fetched ${serverOrders.size} orders from server (hasMore: ${response?.meta?.hasMore})")
                    
                    // FIXED: Sync to local DB FIRST, then load from DB
                    // This ensures orders are available offline for printing/editing
                    val syncResult = withContext(Dispatchers.IO) {
                        queueRepository.syncServerOrders(serverOrders)
                    }
                    println("[OrdersViewModel] Synced ${syncResult.getOrNull() ?: 0} orders to local DB")
                    
                    // Now load from local DB (not from API response)
                    // This ensures we load with full details persisted in DB
                    val items = withContext(Dispatchers.IO) {
                        val serverIds = serverOrders.map { it.id }
                        queueRepository.getByServerIds(serverIds)
                    }.map { entity ->
                        val request = entity.toRequest()
                        val customerName = request.customer?.let { customer ->
                            listOfNotNull(customer.firstName, customer.lastName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { customer.name ?: customer.username }
                        }
                        OrdersListItem(
                            id = entity.id,
                            serverId = entity.serverId,
                            serverCode = entity.serverCode,
                            clientReference = entity.clientReference,
                            createdAt = entity.createdAt,
                            status = entity.status,
                            total = request.total,
                            customerName = customerName,
                            paymentStatus = entity.paymentStatus ?: request.paymentStatus,
                            isFromServer = entity.isFromServer,
                            request = request  // Full details from DB
                        )
                    }
                    
                    val currentFilter = _state.value.customerFilter
                    _state.value = _state.value.copy(
                        isLoading = false,
                        items = items,
                        filteredItems = applyLocalFilter(items, currentFilter),
                        hasMorePages = response?.meta?.hasMore ?: false,
                        nextCursor = response?.meta?.nextCursor
                    )
                    println("[OrdersViewModel] Loaded ${items.size} orders from local DB (after sync)")
                } else {
                    println("[OrdersViewModel] Failed to fetch orders: ${result.exceptionOrNull()?.message}")
                    _state.value = _state.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                println("[OrdersViewModel] Error refreshing orders: ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(isLoading = false)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }
    
    private fun parseDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
            format.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Load more orders (pagination)
     */
    fun loadMoreOrders() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore || !_state.value.hasMorePages) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    api.getMobileOrders(
                        cursor = cursor,
                        limit = 20,
                        customerFilter = currentServerFilter.takeIf { it.isNotBlank() }
                    )
                }
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    val serverOrders = response?.data ?: emptyList()
                    println("[OrdersViewModel] Loaded ${serverOrders.size} more orders")
                    
                    // FIXED: Sync to local DB FIRST, then load from DB
                    val syncResult = withContext(Dispatchers.IO) {
                        queueRepository.syncServerOrders(serverOrders)
                    }
                    println("[OrdersViewModel] Synced ${syncResult.getOrNull() ?: 0} more orders to local DB")
                    
                    // Load from local DB (not from API response)
                    val newItems = withContext(Dispatchers.IO) {
                        val serverIds = serverOrders.map { it.id }
                        queueRepository.getByServerIds(serverIds)
                    }.map { entity ->
                        val request = entity.toRequest()
                        val customerName = request.customer?.let { customer ->
                            listOfNotNull(customer.firstName, customer.lastName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { customer.name ?: customer.username }
                        }
                        OrdersListItem(
                            id = entity.id,
                            serverId = entity.serverId,
                            serverCode = entity.serverCode,
                            clientReference = entity.clientReference,
                            createdAt = entity.createdAt,
                            status = entity.status,
                            total = request.total,
                            customerName = customerName,
                            paymentStatus = entity.paymentStatus ?: request.paymentStatus,
                            isFromServer = entity.isFromServer,
                            request = request  // Full details from DB
                        )
                    }
                    
                    // Append new items to existing list
                    val currentItems = _state.value.items
                    val allItems = currentItems + newItems
                    val currentFilter = _state.value.customerFilter
                    
                    _state.value = _state.value.copy(
                        items = allItems,
                        filteredItems = applyLocalFilter(allItems, currentFilter),
                        hasMorePages = response?.meta?.hasMore ?: false,
                        nextCursor = response?.meta?.nextCursor
                    )
                }
            } catch (e: Exception) {
                println("[OrdersViewModel] Error loading more orders: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }
    
    /**
     * Delete an order. If synced to server, also deletes from server.
     * Local-only orders are deleted immediately.
     */
    fun deleteOrder(item: OrdersListItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, deleteError = null)
            try {
                // If order is synced to server, try to delete from server first
                if (item.serverId != null && item.status == QueuedOrderStatus.SYNCED) {
                    val result = withContext(Dispatchers.IO) {
                        api.deleteOrder(item.serverId)
                    }
                    if (result.isFailure) {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Failed to delete from server"
                        _state.value = _state.value.copy(
                            isDeleting = false,
                            deleteError = errorMsg
                        )
                        return@launch
                    }
                }
                
                // Delete from local database
                val deleteResult = withContext(Dispatchers.IO) {
                    queueRepository.deleteById(item.id)
                }
                
                if (deleteResult.isFailure) {
                    throw deleteResult.exceptionOrNull() ?: Exception("Failed to delete locally")
                }
                
                // FIXED: Remove from UI list immediately
                val currentItems = _state.value.items.toMutableList()
                currentItems.removeIf { it.id == item.id }
                val currentFilter = _state.value.customerFilter
                
                _state.value = _state.value.copy(
                    isDeleting = false,
                    items = currentItems,
                    filteredItems = applyLocalFilter(currentItems, currentFilter),
                    message = "Order deleted successfully",
                    messageError = false
                )
            } catch (e: Exception) {
                println("[OrdersViewModel] Error deleting order: ${e.message}")
                _state.value = _state.value.copy(
                    isDeleting = false,
                    deleteError = e.message ?: "Failed to delete order"
                )
            }
        }
    }
    
    /**
     * Check if an order can be edited.
     * - Pending/failed orders can always be edited locally
     * - Synced orders can be edited if they are "hold" or "unpaid" status
     */
    fun canEditOrder(item: OrdersListItem): Boolean {
        return when (item.status) {
            QueuedOrderStatus.PENDING, QueuedOrderStatus.FAILED -> true
            QueuedOrderStatus.SYNCED -> {
                // Synced orders can be edited if not fully paid
                val paymentStatus = item.paymentStatus?.lowercase()
                paymentStatus == "hold" || paymentStatus == "unpaid" || paymentStatus == "partially_paid"
            }
        }
    }
    
    /**
     * Check if an order can be deleted.
     * All orders can be deleted (local deletion + server deletion if synced).
     */
    fun canDeleteOrder(item: OrdersListItem): Boolean {
        return true // All orders can be deleted
    }
    
    /**
     * Clear message
     */
    fun clearMessage() {
        _state.value = _state.value.copy(message = null, messageError = false)
    }
    
    /**
     * Clear delete error
     */
    fun clearDeleteError() {
        _state.value = _state.value.copy(deleteError = null)
    }
    
    /**
     * Cleanup when view model is no longer needed
     */
    fun onCleared() {
        // Cancel all coroutines
        viewModelScope.launch {
            // Any cleanup needed
        }
    }
}
