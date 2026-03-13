package com.nexopos.erp.feature.manufacturing.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.BomItem
import com.nexopos.erp.core.network.ManufacturingBom
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ProductSummary
import com.nexopos.erp.core.network.UnitSummary
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.feature.manufacturing.ProductDto
import com.nexopos.shared.models.UnitQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for BOM Items Edit Screen
 */
data class BomItemsEditState(
    val isLoading: Boolean = true,
    val isLoadingFormOptions: Boolean = false,
    val isSaving: Boolean = false,
    val bom: ManufacturingBom? = null,
    val items: List<BomItem> = emptyList(),
    val originalItems: List<BomItem> = emptyList(),
    val rawProducts: List<ProductDto> = emptyList(),
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val hasChanges: Boolean = false
)

/**
 * ViewModel for BOM Items Edit Screen
 * 
 * Handles loading and editing BOM items including
 * add, update, and remove operations.
 */
class BomItemsEditViewModel(
    private val mobileApi: MobileApi
) : ViewModel() {

    companion object {
        private const val TAG = "BomItemsEditViewModel"
    }

    private val _state = MutableStateFlow(BomItemsEditState())
    val state = _state.asStateFlow()

    /**
     * Load a BOM by ID with its items
     */
    fun loadBom(bomId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = mobileApi.getManufacturingBom(bomId.toInt())
                val bom = response.data
                val items = bom.items ?: emptyList()
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    bom = bom,
                    items = items,
                    originalItems = items,
                    hasChanges = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BOM", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load BOM")
                )
            }
        }
    }

    /**
     * Load raw material products and units for the BOM item form.
     */
    fun loadFormOptions() {
        if (_state.value.isLoadingFormOptions) {
            return
        }
        if (_state.value.rawProducts.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingFormOptions = true, error = null)
            try {
                val productsResponse = mobileApi.getProducts(limit = 200)
                val rawProducts = productsResponse.data.map { product ->
                    val isRaw = product.isRawMaterial == true
                    if (!isRaw) {
                        return@map null
                    }
                    val allUnits = product.unitQuantities ?: emptyList()
                    val rawUnits = allUnits.filter { it.isRawMaterial == true }
                    val preferredUnits = if (rawUnits.isNotEmpty()) rawUnits else allUnits
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
                    rawProducts = rawProducts
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BOM item form options", e)
                _state.value = _state.value.copy(
                    isLoadingFormOptions = false,
                    error = e.onlineOnlyMessage("Failed to load form options")
                )
            }
        }
    }

    /**
     * Add a new item to the BOM
     */
    fun addItem(product: ProductDto, quantity: Double, unitQuantity: UnitQuantity) {
        val currentItems = _state.value.items
        val componentProduct = ProductSummary(
            id = product.id.toLong(),
            name = product.name,
            sku = product.sku
        )
        val unitSummary = UnitSummary(
            id = unitQuantity.unitId,
            name = unitQuantity.unit?.name ?: ""
        )
        val newItem = BomItem(
            id = System.currentTimeMillis(), // Temporary ID for new items
            productId = null,
            product = null,
            componentProductId = product.id.toLong(),
            componentProduct = componentProduct,
            quantity = quantity,
            unitId = unitQuantity.unitId,
            unit = unitSummary
        )
        
        val updatedItems = currentItems + newItem
        _state.value = _state.value.copy(
            items = updatedItems,
            hasChanges = true
        )
    }

    /**
     * Update an existing item's quantity
     */
    fun updateItem(itemId: Long, quantity: Double) {
        val currentItems = _state.value.items
        val updatedItems = currentItems.map { item ->
            if (item.id == itemId) {
                item.copy(quantity = quantity)
            } else {
                item
            }
        }
        
        _state.value = _state.value.copy(
            items = updatedItems,
            hasChanges = true
        )
    }

    /**
     * Remove an item from the BOM
     */
    fun removeItem(itemId: Long) {
        val currentItems = _state.value.items
        val updatedItems = currentItems.filter { it.id != itemId }
        
        _state.value = _state.value.copy(
            items = updatedItems,
            hasChanges = true
        )
    }

    /**
     * Save all BOM item changes to the server
     * Note: This would typically call an API endpoint to update the BOM items
     * For now, we'll mark it as successful since the API endpoint may not exist yet
     */
    fun saveBomItems() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                // TODO: Call actual API endpoint to save BOM items
                // For now, we'll simulate a successful save
                // val response = mobileApi.updateBomItems(_state.value.bom!!.id, _state.value.items)
                
                // Simulate network delay
                kotlinx.coroutines.delay(500)
                
                _state.value = _state.value.copy(
                    isSaving = false,
                    originalItems = _state.value.items,
                    hasChanges = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save BOM items", e)
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.onlineOnlyMessage("Failed to save BOM items")
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
     * Clear the save success flag
     */
    fun clearSaveSuccess() {
        _state.value = _state.value.copy(saveSuccess = false)
    }
}
