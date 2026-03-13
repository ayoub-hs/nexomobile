package com.nexopos.erp.feature.containermanagement.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.ContainerAdjustRequest
import com.nexopos.erp.core.network.ContainerAdjustResponse
import com.nexopos.erp.core.network.ContainerReceiveRequest
import com.nexopos.erp.core.network.CustomerContainerBalance
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.onlineOnlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI model for container inventory item grouped by type.
 */
data class ContainerInventoryItem(
    val id: Long,
    val name: String,
    val description: String?,
    val depositAmount: Double,
    val totalQuantity: Int,
    val availableQuantity: Int,
    val inCirculation: Int,
    val capacity: Double,
    val capacityUnit: String
)

data class CustomerContainerBalanceItem(
    val customerId: Long,
    val customerName: String,
    val containerTypeId: Long,
    val containerTypeName: String,
    val quantityHeld: Int,
    val depositTotal: Double,
    val lastTransactionAt: String?
)

/**
 * State for the Container Inventory screen.
 */
data class ContainerInventoryState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val inventory: List<ContainerInventoryItem> = emptyList(),
    val filteredInventory: List<ContainerInventoryItem> = emptyList(),
    val balances: List<CustomerContainerBalanceItem> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val receiveSuccess: Boolean = false,
    val adjustSuccess: Boolean = false
)

/**
 * ViewModel for Container Inventory screen.
 * Manages container inventory list and receive container actions.
 */
class ContainerInventoryViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {

    private val _state = MutableStateFlow(ContainerInventoryState())
    val state: StateFlow<ContainerInventoryState> = _state.asStateFlow()

    init {
        loadInventory()
    }

    /**
     * Load container inventory from the API.
     */
    fun loadInventory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val payload = withContext(Dispatchers.IO) {
                    loadInventoryPayload()
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    inventory = payload.inventory,
                    filteredInventory = filterInventory(payload.inventory, _state.value.searchQuery),
                    balances = payload.balances
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load container inventory")
                )
            }
        }
    }

    /**
     * Refresh inventory (pull-to-refresh).
     */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null, receiveSuccess = false, adjustSuccess = false)
            
            try {
                val payload = withContext(Dispatchers.IO) {
                    loadInventoryPayload()
                }

                _state.value = _state.value.copy(
                    isRefreshing = false,
                    inventory = payload.inventory,
                    filteredInventory = filterInventory(payload.inventory, _state.value.searchQuery),
                    balances = payload.balances
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh container inventory")
                )
            }
        }
    }

    /**
     * Receive containers from a customer.
     *
     * @param containerTypeId The container type ID
     * @param customerId The customer ID
     * @param quantity The quantity to receive
     * @param notes Optional notes
     */
    fun receiveContainers(
        containerTypeId: Long,
        customerId: Long?,
        quantity: Int,
        notes: String?
    ) {
        if (customerId == null) {
            _state.value = _state.value.copy(
                error = "Customer is required to receive containers",
                receiveSuccess = false,
                adjustSuccess = false
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(error = null, receiveSuccess = false, adjustSuccess = false)
            
            try {
                val request = ContainerReceiveRequest(
                    customerId = customerId,
                    containerTypeId = containerTypeId,
                    quantity = quantity,
                    note = notes?.takeIf { it.isNotBlank() }
                )
                
                val response = withContext(Dispatchers.IO) {
                    mobileApi.receiveContainers(request)
                }
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(receiveSuccess = true)
                    refresh()
                } else {
                    _state.value = _state.value.copy(
                        error = response.message ?: "Failed to receive containers"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.onlineOnlyMessage("Failed to receive containers")
                )
            }
        }
    }

    fun adjustInventory(
        containerTypeId: Long,
        adjustment: Int,
        reason: String?
    ) {
        if (adjustment == 0) return

        viewModelScope.launch {
            _state.value = _state.value.copy(error = null, receiveSuccess = false, adjustSuccess = false)

            try {
                val request = ContainerAdjustRequest(
                    containerTypeId = containerTypeId,
                    adjustment = adjustment,
                    reason = reason?.takeIf { it.isNotBlank() }
                        ?: if (adjustment < 0) "Container stock decreased" else "Container stock increased"
                )

                val response = withContext(Dispatchers.IO) {
                    mobileApi.adjustContainer(request)
                }

                if (response.status == "success") {
                    val updatedInventory = _state.value.inventory.map { item ->
                        if (item.id == containerTypeId) {
                            item.copy(
                                totalQuantity = response.data.totalQuantity,
                                availableQuantity = response.data.availableQuantity,
                                inCirculation = response.data.inCirculation
                            )
                        } else {
                            item
                        }
                    }

                    _state.value = _state.value.copy(
                        inventory = updatedInventory,
                        filteredInventory = filterInventory(updatedInventory, _state.value.searchQuery),
                        adjustSuccess = true
                    )
                    refresh()
                } else {
                    _state.value = _state.value.copy(
                        error = response.message ?: "Failed to adjust container stock"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.onlineOnlyMessage("Failed to adjust container stock")
                )
            }
        }
    }

    /**
     * Set search query filter.
     */
    fun setSearchQuery(query: String) {
        val filtered = filterInventory(_state.value.inventory, query)
        _state.value = _state.value.copy(
            searchQuery = query,
            filteredInventory = filtered
        )
    }

    fun customerOptions(): List<com.nexopos.erp.feature.containermanagement.ui.CustomerOption> {
        return _state.value.balances
            .distinctBy { it.customerId }
            .map {
                com.nexopos.erp.feature.containermanagement.ui.CustomerOption(
                    id = it.customerId,
                    name = it.customerName
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clear receive success state.
     */
    fun clearReceiveSuccess() {
        _state.value = _state.value.copy(receiveSuccess = false)
    }

    fun clearAdjustSuccess() {
        _state.value = _state.value.copy(adjustSuccess = false)
    }

    private suspend fun loadInventoryPayload(): ContainerInventoryPayload = coroutineScope {
        val typesDeferred = async { mobileApi.getContainerTypes() }
        val balancesDeferred = async { mobileApi.getCustomerContainerBalances(limit = 100, offset = 0) }

        val typesResponse = typesDeferred.await()
        if (typesResponse.status != "success") {
            throw IllegalStateException("Failed to load container inventory")
        }

        val inventoryItems = typesResponse.data.map { mapToInventoryItem(it) }
        val balancesResponse = balancesDeferred.await()
        val balancesItems = if (balancesResponse.status == "success") {
            balancesResponse.data.map(::mapToBalanceItem)
        } else {
            emptyList()
        }

        ContainerInventoryPayload(
            inventory = inventoryItems,
            balances = balancesItems
        )
    }

    private fun mapToInventoryItem(containerType: com.nexopos.erp.core.network.ContainerType): ContainerInventoryItem {
        val inventory = containerType.inventory
        return ContainerInventoryItem(
            id = containerType.id,
            name = containerType.name,
            description = containerType.description,
            depositAmount = containerType.depositFee,
            totalQuantity = inventory?.totalQuantity ?: 0,
            availableQuantity = inventory?.availableQuantity ?: 0,
            inCirculation = inventory?.inCirculation ?: 0,
            capacity = containerType.capacity,
            capacityUnit = containerType.capacityUnit
        )
    }

    private fun mapToBalanceItem(item: CustomerContainerBalance): CustomerContainerBalanceItem {
        return CustomerContainerBalanceItem(
            customerId = item.customerId,
            customerName = item.customerName,
            containerTypeId = item.containerTypeId.toLong(),
            containerTypeName = item.containerTypeName,
            quantityHeld = item.quantityHeld,
            depositTotal = item.depositTotal,
            lastTransactionAt = item.lastTransactionAt
        )
    }

    private fun filterInventory(
        items: List<ContainerInventoryItem>,
        query: String
    ): List<ContainerInventoryItem> {
        if (query.isBlank()) return items
        
        return items.filter { item ->
            item.name.contains(query, ignoreCase = true) ||
            item.description?.contains(query, ignoreCase = true) == true
        }
    }

    private data class ContainerInventoryPayload(
        val inventory: List<ContainerInventoryItem>,
        val balances: List<CustomerContainerBalanceItem>
    )
}
