package com.nexopos.erp.feature.containermanagement.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.ContainerAdjustRequest
import com.nexopos.erp.core.network.ContainerAdjustResponse
import com.nexopos.erp.core.network.ContainerType
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.feature.containermanagement.ContainerStatus
import com.nexopos.erp.feature.containermanagement.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Container UI model for display in the container list.
 * Consolidated with Feature.kt Container model - use this for UI display.
 * Note: Containers do not have barcodes (per backend schema).
 */
data class ContainerUiModel(
    val id: Long,
    val containerTypeId: Int,
    val name: String,
    val description: String?,
    val depositAmount: Double,
    val totalQuantity: Int,
    val availableQuantity: Int,
    val inCirculation: Int,
    val status: ContainerStatus,
    val capacity: Double,
    val capacityUnit: String
)

/**
 * State for the Container list screen.
 */
data class ContainerState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val containers: List<ContainerUiModel> = emptyList(),
    val filteredContainers: List<ContainerUiModel> = emptyList(),
    val selectedStatusFilter: ContainerStatus? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val transactionSuccess: Boolean = false
)

/**
 * ViewModel for Container Management feature.
 * Uses MobileApi endpoints for container operations:
 * - GET mobile/containers/types - List container types with inventory
 * - GET mobile/containers/inventory - Get inventory summary
 * - POST mobile/containers/adjust - Adjust container inventory
 */
class ContainerViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {

    private val _state = MutableStateFlow(ContainerState())
    val state: StateFlow<ContainerState> = _state.asStateFlow()

    init {
        loadContainers()
    }

    /**
     * Load all containers from the MobileApi.
     * Uses the getContainerTypes() endpoint which includes inventory data.
     */
    fun loadContainers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    mobileApi.getContainerTypes()
                }
                
                if (response.status == "success") {
                    val mappedContainers = response.data.mapNotNull { containerType ->
                        mapToContainer(containerType)
                    }
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        containers = mappedContainers,
                        filteredContainers = filterContainers(
                            mappedContainers,
                            _state.value.selectedStatusFilter,
                            _state.value.searchQuery
                        )
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load containers"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load containers")
                )
            }
        }
    }

    /**
     * Refresh containers (pull-to-refresh).
     */
    fun refreshContainers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    mobileApi.getContainerTypes()
                }
                
                if (response.status == "success") {
                    val mappedContainers = response.data.mapNotNull { containerType ->
                        mapToContainer(containerType)
                    }
                    
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        containers = mappedContainers,
                        filteredContainers = filterContainers(
                            mappedContainers,
                            _state.value.selectedStatusFilter,
                            _state.value.searchQuery
                        )
                    )
                } else {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = "Failed to refresh containers"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh containers")
                )
            }
        }
    }

    /**
     * Process a container adjustment (give/receive containers).
     * Uses the MobileApi adjustContainer() endpoint.
     *
     * @param containerId The container type ID
     * @param quantity The quantity to adjust
     * @param transactionType The type of transaction (GIVE/RECEIVE/ADJUST)
     * @param notes Optional notes for the adjustment
     */
    fun processTransaction(
        containerId: Long,
        quantity: Int,
        transactionType: TransactionType,
        customerId: Long? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null, transactionSuccess = false)
            
            try {
                // Calculate adjustment based on transaction type
                // GIVE (to customer) = negative adjustment (reduces available)
                // RECEIVE (from customer) = positive adjustment (increases available)
                // ADJUST = use quantity as-is for manual adjustments
                val adjustment = when (transactionType) {
                    TransactionType.GIVE -> -quantity
                    TransactionType.RECEIVE -> quantity
                    TransactionType.ADJUST -> quantity
                }
                
                val reason = buildString {
                    append(when (transactionType) {
                        TransactionType.GIVE -> "Given to customer"
                        TransactionType.RECEIVE -> "Received from customer"
                        TransactionType.ADJUST -> "Manual adjustment"
                    })
                    if (customerId != null) {
                        append(" (Customer ID: $customerId)")
                    }
                    if (!notes.isNullOrBlank()) {
                        append(" - $notes")
                    }
                }
                
                val request = ContainerAdjustRequest(
                    containerTypeId = containerId,
                    adjustment = adjustment,
                    reason = reason
                )
                
                val response: ContainerAdjustResponse = withContext(Dispatchers.IO) {
                    mobileApi.adjustContainer(request)
                }
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        transactionSuccess = true
                    )
                    refreshContainers()
                } else {
                    _state.value = _state.value.copy(
                        error = response.message ?: "Failed to process transaction"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.onlineOnlyMessage("Failed to process transaction")
                )
            }
        }
    }

    /**
     * Set status filter.
     */
    fun setStatusFilter(status: ContainerStatus?) {
        val filtered = filterContainers(
            _state.value.containers,
            status,
            _state.value.searchQuery
        )
        _state.value = _state.value.copy(
            selectedStatusFilter = status,
            filteredContainers = filtered
        )
    }

    /**
     * Set search query filter.
     */
    fun setSearchQuery(query: String) {
        val filtered = filterContainers(
            _state.value.containers,
            _state.value.selectedStatusFilter,
            query
        )
        _state.value = _state.value.copy(
            searchQuery = query,
            filteredContainers = filtered
        )
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clear transaction success state.
     */
    fun clearTransactionSuccess() {
        _state.value = _state.value.copy(transactionSuccess = false)
    }

    /**
     * Map ContainerType from API to UI Container model.
     */
    private fun mapToContainer(containerType: ContainerType): ContainerUiModel {
        val inventory = containerType.inventory
        
        // Determine status based on inventory levels
        // Using existing ContainerStatus enum values
        val status = if (inventory != null) {
            when {
                inventory.availableQuantity == 0 -> ContainerStatus.RESERVED
                inventory.inCirculation > 0 -> ContainerStatus.IN_USE
                else -> ContainerStatus.AVAILABLE
            }
        } else {
            ContainerStatus.AVAILABLE
        }
        
        return ContainerUiModel(
            id = containerType.id,
            containerTypeId = containerType.id.toInt(),
            name = containerType.name,
            description = containerType.description,
            depositAmount = containerType.depositFee,
            totalQuantity = inventory?.totalQuantity ?: 0,
            availableQuantity = inventory?.availableQuantity ?: 0,
            inCirculation = inventory?.inCirculation ?: 0,
            status = status,
            capacity = containerType.capacity,
            capacityUnit = containerType.capacityUnit
        )
    }

    private fun filterContainers(
        containers: List<ContainerUiModel>,
        status: ContainerStatus?,
        query: String
    ): List<ContainerUiModel> {
        var filtered = containers
        
        // Filter by status
        if (status != null) {
            filtered = filtered.filter { it.status == status }
        }
        
        // Filter by search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { container ->
                container.name.contains(query, ignoreCase = true) ||
                container.description?.contains(query, ignoreCase = true) == true
            }
        }
        
        return filtered
    }
}
