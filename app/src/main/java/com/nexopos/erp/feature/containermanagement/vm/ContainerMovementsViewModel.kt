package com.nexopos.erp.feature.containermanagement.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.ContainerMovementItem
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.onlineOnlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI model for container movement history item.
 */
data class MovementUiModel(
    val id: Long,
    val containerTypeName: String,
    val customerName: String?,
    val type: String,
    val quantity: Int,
    val notes: String?,
    val createdAt: String?,
    val createdBy: String?
) {
    val isReceived: Boolean get() = type == "receive" || type == "in"
    val isDispatched: Boolean get() = type == "give" || type == "out"
    val isAdjustment: Boolean get() = type == "adjustment" || type == "adjust" || type == "adjusted"
    val quantityDisplay: String
        get() = when {
            isReceived -> "+${kotlin.math.abs(quantity)}"
            isDispatched -> "-${kotlin.math.abs(quantity)}"
            isAdjustment && quantity >= 0 -> "+${kotlin.math.abs(quantity)}"
            else -> "-${kotlin.math.abs(quantity)}"
        }
}

/**
 * State for the Container Movements screen.
 */
data class ContainerMovementsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val movements: List<MovementUiModel> = emptyList(),
    val containerTypeName: String = "",
    val error: String? = null,
    val hasMore: Boolean = false,
    val offset: Int = 0
)

/**
 * ViewModel for Container Movements screen.
 * Loads and displays movement history for a specific container type.
 */
class ContainerMovementsViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {

    private val _state = MutableStateFlow(ContainerMovementsState())
    val state: StateFlow<ContainerMovementsState> = _state.asStateFlow()

    private var currentTypeId: Long? = null

    /**
     * Load movements for a specific container type.
     *
     * @param typeId The container type ID
     */
    fun loadMovements(typeId: Long) {
        if (currentTypeId != typeId) {
            currentTypeId = typeId
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                offset = 0,
                movements = emptyList()
            )
        } else {
            _state.value = _state.value.copy(isLoading = true, error = null)
        }
        
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    mobileApi.getContainerMovements(
                        containerTypeId = typeId,
                        limit = 50,
                        offset = 0
                    )
                }
                
                if (response.status == "success" && response.data != null) {
                    val movements = response.data.map { mapToMovementUiModel(it) }
                    val containerTypeName = movements.firstOrNull()?.containerTypeName ?: ""
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        movements = movements,
                        containerTypeName = containerTypeName,
                        hasMore = response.meta?.hasMore ?: false,
                        offset = response.meta?.offset ?: 0
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = response.message ?: "Failed to load movements"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load movements")
                )
            }
        }
    }

    /**
     * Refresh movements (pull-to-refresh).
     */
    fun refresh() {
        val typeId = currentTypeId ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    mobileApi.getContainerMovements(
                        containerTypeId = typeId,
                        limit = 50,
                        offset = 0
                    )
                }
                
                if (response.status == "success" && response.data != null) {
                    val movements = response.data.map { mapToMovementUiModel(it) }
                    
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        movements = movements,
                        hasMore = response.meta?.hasMore ?: false,
                        offset = response.meta?.offset ?: 0
                    )
                } else {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = response.message ?: "Failed to refresh movements"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh movements")
                )
            }
        }
    }

    /**
     * Load more movements (pagination).
     */
    fun loadMore() {
        val typeId = currentTypeId ?: return
        if (_state.value.isLoading || !_state.value.hasMore) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            try {
                val newOffset = _state.value.offset + 50
                val response = withContext(Dispatchers.IO) {
                    mobileApi.getContainerMovements(
                        containerTypeId = typeId,
                        limit = 50,
                        offset = newOffset
                    )
                }
                
                if (response.status == "success" && response.data != null) {
                    val newMovements = response.data.map { mapToMovementUiModel(it) }
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        movements = _state.value.movements + newMovements,
                        hasMore = response.meta?.hasMore ?: false,
                        offset = response.meta?.offset ?: newOffset
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = response.message ?: "Failed to load more movements"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load more movements")
                )
            }
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun mapToMovementUiModel(item: ContainerMovementItem): MovementUiModel {
        return MovementUiModel(
            id = item.id,
            containerTypeName = item.containerTypeName,
            customerName = item.customerName,
            type = item.type,
            quantity = item.quantity,
            notes = item.notes,
            createdAt = item.createdAt,
            createdBy = item.createdBy
        )
    }
}
