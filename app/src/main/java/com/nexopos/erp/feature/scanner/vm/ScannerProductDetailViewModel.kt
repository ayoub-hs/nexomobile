package com.nexopos.erp.feature.scanner.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.AdminProductResponse
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.repo.ProductAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerProductDetailState(
    val isLoading: Boolean = true,
    val product: AdminProductResponse? = null,
    val categoryName: String? = null,
    val error: String? = null
)

class ScannerProductDetailViewModel(
    private val repository: ProductAdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ScannerProductDetailState())
    val state: StateFlow<ScannerProductDetailState> = _state.asStateFlow()

    private var loadedProductId: Long? = null

    fun loadProduct(productId: Long) {
        if (loadedProductId == productId && _state.value.product != null) {
            return
        }
        loadedProductId = productId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val product = repository.getProduct(productId)
                val categoryName = product.categoryId?.let { categoryId ->
                    repository.getCategories().firstOrNull { it.id == categoryId }?.name
                }
                _state.value = ScannerProductDetailState(
                    isLoading = false,
                    product = product,
                    categoryName = categoryName
                )
            } catch (e: Exception) {
                _state.value = ScannerProductDetailState(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load product")
                )
            }
        }
    }
}
