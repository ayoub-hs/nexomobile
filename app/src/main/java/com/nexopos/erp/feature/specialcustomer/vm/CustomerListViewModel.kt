package com.nexopos.erp.feature.specialcustomer.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.SpecialCustomerDto
import com.nexopos.erp.core.repo.CustomerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Customer List State for Special Customers
 */
data class CustomerListState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val customers: List<SpecialCustomerDto> = emptyList(),
    val error: String? = null
)

/**
 * Customer List ViewModel for Special Customers
 * 
 * Manages the list of special customers (customers belonging to special customer group)
 * for the Special Customer List screen.
 */
class CustomerListViewModel(
    private val customerRepository: CustomerRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CustomerListViewModel"
    }
    
    private val _state = MutableStateFlow(CustomerListState())
    val state: StateFlow<CustomerListState> = _state.asStateFlow()
    
    init {
        loadSpecialCustomers()
    }
    
    /**
     * Load special customers from repository
     */
    private fun loadSpecialCustomers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val result = customerRepository.getSpecialCustomers()
                
                result.fold(
                    onSuccess = { customers ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            customers = customers
                        )
                        Log.d(TAG, "Loaded ${customers.size} special customers")
                    },
                    onFailure = { throwable ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to load special customers"
                        )
                        Log.e(TAG, "Failed to load special customers", throwable)
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load special customers"
                )
                Log.e(TAG, "Failed to load special customers", e)
            }
        }
    }
    
    /**
     * Refresh special customers from network
     */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            
            try {
                val result = customerRepository.getSpecialCustomers()
                
                result.fold(
                    onSuccess = { customers ->
                        _state.value = _state.value.copy(
                            isRefreshing = false,
                            customers = customers
                        )
                        Log.d(TAG, "Refreshed ${customers.size} special customers")
                    },
                    onFailure = { throwable ->
                        _state.value = _state.value.copy(
                            isRefreshing = false,
                            error = throwable.message ?: "Failed to refresh special customers"
                        )
                        Log.e(TAG, "Failed to refresh special customers", throwable)
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.message ?: "Failed to refresh special customers"
                )
                Log.e(TAG, "Failed to refresh special customers", e)
            }
        }
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
