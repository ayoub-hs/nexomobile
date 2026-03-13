package com.nexopos.erp.feature.pricelookup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.AdminProductResponse
import com.nexopos.erp.core.network.UpdateUnitQuantityRequest
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.repo.ProductAdminRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlinx.coroutines.launch

data class UnitPriceDraft(
    val sale: String,
    val wholesale: String,
    val cogs: String
)

data class PriceLookupDetailState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val product: AdminProductResponse? = null,
    val editMode: Boolean = false,
    val drafts: Map<Long, UnitPriceDraft> = emptyMap(),
    val originalDrafts: Map<Long, UnitPriceDraft> = emptyMap()
)

class PriceLookupDetailViewModel(
    private val repository: ProductAdminRepository
) : ViewModel() {
    var state: PriceLookupDetailState by mutableStateOf(PriceLookupDetailState())
        private set

    private var loadedProductId: Long? = null

    fun loadProduct(productId: Long) {
        if (loadedProductId == productId && state.product != null) {
            return
        }
        loadedProductId = productId
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val product = repository.getProduct(productId)
                val drafts = buildDrafts(product)
                state = state.copy(
                    isLoading = false,
                    product = product,
                    drafts = drafts,
                    originalDrafts = drafts,
                    editMode = false
                )
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load product")
                )
            }
        }
    }

    fun toggleEditMode() {
        val product = state.product ?: return
        val nextMode = !state.editMode
        state = state.copy(
            editMode = nextMode,
            drafts = if (nextMode) buildDrafts(product) else state.drafts,
            error = null
        )
    }

    fun cancelEdit() {
        val product = state.product ?: return
        state = state.copy(
            editMode = false,
            drafts = buildDrafts(product),
            error = null
        )
    }

    fun updateDraft(unitId: Long, sale: String? = null, wholesale: String? = null, cogs: String? = null) {
        val current = state.drafts[unitId] ?: UnitPriceDraft("", "", "")
        state = state.copy(
            drafts = state.drafts + (unitId to current.copy(
                sale = sale ?: current.sale,
                wholesale = wholesale ?: current.wholesale,
                cogs = cogs ?: current.cogs
            ))
        )
    }

    fun saveChanges() {
        val product = state.product ?: return
        val drafts = state.drafts
        val originalDrafts = state.originalDrafts
        val units = product.unitQuantities

        viewModelScope.launch {
            state = state.copy(isSaving = true, error = null)
            try {
                var updated = false
                for (unit in units) {
                    val draft = drafts[unit.id] ?: continue
                    val originalDraft = originalDrafts[unit.id]
                    val saleChanged = draft.sale.isNotBlank() &&
                        normalizeNumberInput(draft.sale) != normalizeNumberInput(originalDraft?.sale)
                    val wholesaleChanged = draft.wholesale.isNotBlank() &&
                        normalizeNumberInput(draft.wholesale) != normalizeNumberInput(originalDraft?.wholesale)
                    val cogsChanged = draft.cogs.isNotBlank() &&
                        normalizeNumberInput(draft.cogs) != normalizeNumberInput(originalDraft?.cogs)

                    val saleValue = if (saleChanged) draft.sale.toDoubleOrNullIfNotBlank() else null
                    val wholesaleValue = if (wholesaleChanged) draft.wholesale.toDoubleOrNullIfNotBlank() else null
                    val cogsValue = if (cogsChanged) draft.cogs.toDoubleOrNullIfNotBlank() else null

                    val request = UpdateUnitQuantityRequest(
                        salePriceEdit = saleValue,
                        wholesalePriceEdit = wholesaleValue,
                        cogs = cogsValue
                    )

                    if (request.salePriceEdit != null || request.wholesalePriceEdit != null || request.cogs != null) {
                        repository.updateUnitQuantity(unit.id, request)
                        updated = true
                    }
                }

                if (updated) {
                    val refreshed = repository.getProduct(product.id)
                    val refreshedDrafts = buildDrafts(refreshed)
                    state = state.copy(
                        isSaving = false,
                        product = refreshed,
                        drafts = refreshedDrafts,
                        originalDrafts = refreshedDrafts,
                        editMode = false
                    )
                } else {
                    state = state.copy(isSaving = false, editMode = false)
                }
            } catch (e: Exception) {
                state = state.copy(
                    isSaving = false,
                    error = e.onlineOnlyMessage("Failed to save prices")
                )
            }
        }
    }

    private fun buildDrafts(product: AdminProductResponse): Map<Long, UnitPriceDraft> {
        return product.unitQuantities.associate { unit ->
            unit.id to UnitPriceDraft(
                sale = formatForInput(unit.salePriceEdit ?: unit.salePrice),
                wholesale = formatForInput(unit.wholesalePriceEdit ?: unit.wholesalePrice),
                cogs = formatForInput(unit.cogs)
            )
        }
    }

    private fun formatForInput(value: Double?): String {
        if (value == null) return ""
        val formatted = String.format(Locale.US, "%.3f", value)
        return formatted.trimEnd('0').trimEnd('.').ifBlank { "0" }
    }

    private fun String.toDoubleOrNullIfNotBlank(): Double? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.replace(" ", "").replace(",", ".")
        return normalized.toDoubleOrNull()
    }

    private fun normalizeNumberInput(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim().replace(" ", "").replace(",", ".")
    }
}
