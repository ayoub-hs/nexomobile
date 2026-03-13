package com.nexopos.erp.feature.orders.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.db.entities.QueuedOrderStatus
import com.nexopos.erp.core.db.toRequest
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.core.print.PrinterType
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
import com.nexopos.erp.print.PrintUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val request: CreateOrderRequest
) {
    /** Display ID: server code if synced, otherwise local ID */
    val displayId: String
        get() = serverCode ?: serverId?.toString() ?: "#$id"
}

data class OrdersState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isDeleting: Boolean = false,
    val items: List<OrdersListItem> = emptyList(),
    val filteredItems: List<OrdersListItem> = emptyList(),
    val customerFilter: String = "",
    val printerMessage: String? = null,
    val printerMessageError: Boolean = false,
    val hasMorePages: Boolean = false,
    val nextCursor: Long? = null,
    val deleteError: String? = null
)

class OrdersViewModel(
    private val appContext: Context,
    private val queueRepository: OrderQueueRepository,
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    private var currentServerFilter: String = ""
    
    // Track recently deleted order server IDs to prevent re-syncing
    private val recentlyDeletedOrderIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            queueRepository.observeAll().collectLatest { entities ->
                val currentFilter = _state.value.customerFilter
                val items = withContext(Dispatchers.Default) {
                    entities.map { entity ->
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
                            request = request
                        )
                    }
                }
                val filteredItems = withContext(Dispatchers.Default) {
                    applyLocalFilter(items, currentFilter)
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = items,
                    filteredItems = filteredItems
                )
                // Auto-refresh from server if local database is empty
                if (entities.isEmpty() && !_state.value.isRefreshing) {
                    refreshFromServer()
                }
            }
        }
    }

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
            val items = _state.value.items
            viewModelScope.launch {
                val filteredItems = withContext(Dispatchers.Default) {
                    applyLocalFilter(items, filter)
                }
                _state.value = _state.value.copy(
                    filteredItems = filteredItems
                )
            }
        }
    }

    private fun applyLocalFilter(items: List<OrdersListItem>, filter: String): List<OrdersListItem> {
        if (filter.isBlank()) return items
        val lowerFilter = filter.lowercase()
        return items.filter { item ->
            item.customerName?.lowercase()?.contains(lowerFilter) == true
        }
    }

    fun refreshFromServer(serverFilter: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    orderRepository.getMobileOrders(
                        cursor = null,
                        limit = 20,
                        customerFilter = serverFilter
                    )
                }
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    val serverOrders = response?.data ?: emptyList()
                    // Filter out recently deleted orders to prevent re-syncing
                    val filteredOrders = serverOrders.filter { order ->
                        !recentlyDeletedOrderIds.contains(order.id)
                    }
                    android.util.Log.d("OrdersViewModel", "Fetched ${serverOrders.size} orders from server, ${filteredOrders.size} after filtering (hasMore: ${response?.meta?.hasMore})")
                    withContext(Dispatchers.IO) {
                        queueRepository.syncServerOrders(filteredOrders)
                    }
                    _state.value = _state.value.copy(
                        hasMorePages = response?.meta?.hasMore ?: false,
                        nextCursor = response?.meta?.nextCursor
                    )
                } else {
                    android.util.Log.e("OrdersViewModel", "Failed to fetch orders: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("OrdersViewModel", "Error refreshing orders", e)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    fun loadMoreOrders() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore || !_state.value.hasMorePages) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    orderRepository.getMobileOrders(
                        cursor = cursor,
                        limit = 20,
                        customerFilter = currentServerFilter.takeIf { it.isNotBlank() }
                    )
                }
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    val serverOrders = response?.data ?: emptyList()
                    // Filter out recently deleted orders
                    val filteredOrders = serverOrders.filter { order ->
                        !recentlyDeletedOrderIds.contains(order.id)
                    }
                    android.util.Log.d("OrdersViewModel", "Loaded ${serverOrders.size} more orders, ${filteredOrders.size} after filtering")
                    withContext(Dispatchers.IO) {
                        queueRepository.syncServerOrders(filteredOrders)
                    }
                    _state.value = _state.value.copy(
                        hasMorePages = response?.meta?.hasMore ?: false,
                        nextCursor = response?.meta?.nextCursor
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("OrdersViewModel", "Error loading more orders", e)
            } finally {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun getOrderById(orderId: Long): OrdersListItem? {
        return _state.value.items.firstOrNull { it.id == orderId }
    }

    fun printOrder(item: OrdersListItem) {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { settingsRepository.printerConfigFlow.first() }
            val ready = isPrinterConfigured(config)
            if (!ready) {
                _state.value = _state.value.copy(
                    printerMessage = "Please configure the printer before printing a receipt.",
                    printerMessageError = true
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { PrintUtil.printOrderReceipt(appContext, config, item.request, null) }
            }
            _state.value = _state.value.copy(
                printerMessage = result.exceptionOrNull()?.message
                    ?: "Receipt sent to printer",
                printerMessageError = result.isFailure
            )
        }
    }

    fun clearPrinterMessage() {
        _state.value = _state.value.copy(printerMessage = null, printerMessageError = false)
    }

    fun clearDeleteError() {
        _state.value = _state.value.copy(deleteError = null)
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
                        orderRepository.deleteOrder(item.serverId)
                    }
                    if (result.isFailure) {
                        val errorMsg = result.exceptionOrNull()?.message 
                            ?: "Failed to delete order from server"
                        _state.value = _state.value.copy(
                            isDeleting = false,
                            deleteError = errorMsg
                        )
                        return@launch
                    }
                }
                
                // Delete from local database
                withContext(Dispatchers.IO) {
                    queueRepository.deleteById(item.id)
                }
                
                // Track this deleted order to prevent re-syncing
                item.serverId?.let { serverId ->
                    recentlyDeletedOrderIds.add(serverId)
                }
                
                _state.value = _state.value.copy(
                    isDeleting = false,
                    printerMessage = "Order deleted successfully",
                    printerMessageError = false
                )
                
                // Refresh from server to ensure the list is up-to-date
                // and the deleted order doesn't reappear
                refreshFromServer()
            } catch (e: Exception) {
                android.util.Log.e("OrdersViewModel", "Error deleting order", e)
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

    private fun isPrinterConfigured(config: PrinterConfig): Boolean {
        return when (config.type) {
            PrinterType.Bluetooth -> !config.macAddress.isNullOrBlank()
            PrinterType.Tcp -> !config.host.isNullOrBlank()
        }
    }
}
