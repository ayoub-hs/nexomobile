package com.nexopos.erp.feature.manufacturing.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.BomItem
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ProductionOrder
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.feature.manufacturing.ProductionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for Production Order Edit Screen
 */
data class ProductionOrderEditState(
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val order: ProductionOrder? = null,
    val bomItems: List<BomItem> = emptyList(),
    val error: String? = null,
    val statusUpdated: Boolean = false
)

/**
 * ViewModel for Production Order Edit Screen
 * 
 * Handles loading and updating production orders including
 * state transitions (start/complete production).
 */
class ProductionOrderEditViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {

    companion object {
        private const val TAG = "ProductionOrderEditVM"
    }

    private val _state = MutableStateFlow(ProductionOrderEditState())
    val state = _state.asStateFlow()

    /**
     * Load a production order by ID
     */
    fun loadOrder(orderId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = mobileApi.getManufacturingOrder(orderId.toInt())
                val order = response.data
                
                // Load BOM items if BOM is linked
                val bomItems = if (order.bomId != null) {
                    try {
                        val bomResponse = mobileApi.getManufacturingBom(order.bomId.toInt())
                        bomResponse.data.items ?: emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load BOM items", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    order = order,
                    bomItems = bomItems
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load production order", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load production order")
                )
            }
        }
    }

    /**
     * Start production (transition from DRAFT/PLANNED to IN_PROGRESS)
     */
    fun startProduction() {
        val orderId = _state.value.order?.id ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isUpdating = true, error = null)
            try {
                val response = mobileApi.startManufacturing(orderId.toInt())
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        order = response.data,
                        statusUpdated = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        error = "Failed to start production"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start production", e)
                _state.value = _state.value.copy(
                    isUpdating = false,
                    error = e.onlineOnlyMessage("Failed to start production")
                )
            }
        }
    }

    /**
     * Complete production (transition from IN_PROGRESS to COMPLETED)
     */
    fun completeProduction() {
        val orderId = _state.value.order?.id ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isUpdating = true, error = null)
            try {
                val response = mobileApi.completeManufacturing(orderId.toInt())
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        order = response.data,
                        statusUpdated = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        error = "Failed to complete production"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete production", e)
                _state.value = _state.value.copy(
                    isUpdating = false,
                    error = e.onlineOnlyMessage("Failed to complete production")
                )
            }
        }
    }

    /**
     * Clear the error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clear the status updated flag
     */
    fun clearStatusUpdated() {
        _state.value = _state.value.copy(statusUpdated = false)
    }
}
