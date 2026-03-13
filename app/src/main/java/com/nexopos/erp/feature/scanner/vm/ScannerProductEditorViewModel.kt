package com.nexopos.erp.feature.scanner.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.AdminProductResponse
import com.nexopos.erp.core.network.CategoryResponse
import com.nexopos.erp.core.network.ContainerType
import com.nexopos.erp.core.network.CreateProductRequest
import com.nexopos.erp.core.network.ProductAdminExpiryRequest
import com.nexopos.erp.core.network.ProductAdminIdentificationRequest
import com.nexopos.erp.core.network.ProductAdminSellingGroupRequest
import com.nexopos.erp.core.network.ProductAdminTaxesRequest
import com.nexopos.erp.core.network.ProductAdminUnitsRequest
import com.nexopos.erp.core.network.ProductAdminVariationRequest
import com.nexopos.erp.core.network.TaxGroupResponse
import com.nexopos.erp.core.network.UnitGroupResponse
import com.nexopos.erp.core.network.UnitResponse
import com.nexopos.erp.core.network.UpdateProductRequest
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.repo.ProductAdminRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerUnitEditorState(
    val rowId: Long,
    val unitQuantityId: Long? = null,
    val unitId: Long? = null,
    val barcode: String = "",
    val salePrice: String = "",
    val wholesalePrice: String = "",
    val cogs: String = "",
    val quantity: String = "",
    val lowQuantity: String = "",
    val stockAlertEnabled: Boolean = false,
    val visible: Boolean = true,
    val isManufactured: Boolean = false,
    val isRawMaterial: Boolean = false,
    val containerTypeId: Long? = null
)

data class ScannerProductEditorState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val productId: Long? = null,
    val name: String = "",
    val barcode: String = "",
    val sku: String = "",
    val status: String = "available",
    val categoryId: Long? = null,
    val description: String = "",
    val barcodeType: String = "code128",
    val stockManagement: String = "enabled",
    val unitGroupId: Long? = null,
    val taxGroupId: Long? = null,
    val taxType: String = "inclusive",
    val accurateTracking: Boolean = false,
    val autoCogs: Boolean = false,
    val onExpiration: String = "prevent_sales",
    val isManufactured: Boolean = false,
    val isRawMaterial: Boolean = false,
    val unitRows: List<ScannerUnitEditorState> = listOf(ScannerUnitEditorState(rowId = 1L)),
    val categories: List<CategoryResponse> = emptyList(),
    val units: List<UnitResponse> = emptyList(),
    val unitGroups: List<UnitGroupResponse> = emptyList(),
    val taxGroups: List<TaxGroupResponse> = emptyList(),
    val containerTypes: List<ContainerType> = emptyList(),
    val error: String? = null,
    val validationError: String? = null,
    val savedProductId: Long? = null
)

data class ScannerUnitEditorDraft(
    val rowId: Long,
    val unitQuantityId: Long? = null,
    val unitId: Long? = null,
    val barcode: String = "",
    val salePrice: String = "",
    val wholesalePrice: String = "",
    val cogs: String = "",
    val quantity: String = "",
    val lowQuantity: String = "",
    val stockAlertEnabled: Boolean = false,
    val visible: Boolean = true,
    val isManufactured: Boolean = false,
    val isRawMaterial: Boolean = false,
    val containerTypeId: Long? = null
)

data class ScannerProductEditorDraft(
    val isEditMode: Boolean = false,
    val productId: Long? = null,
    val name: String = "",
    val barcode: String = "",
    val sku: String = "",
    val status: String = "available",
    val categoryId: Long? = null,
    val description: String = "",
    val barcodeType: String = "code128",
    val stockManagement: String = "enabled",
    val unitGroupId: Long? = null,
    val taxGroupId: Long? = null,
    val taxType: String = "inclusive",
    val accurateTracking: Boolean = false,
    val autoCogs: Boolean = false,
    val onExpiration: String = "prevent_sales",
    val isManufactured: Boolean = false,
    val isRawMaterial: Boolean = false,
    val unitRows: List<ScannerUnitEditorDraft> = emptyList()
)

class ScannerProductEditorViewModel(
    private val repository: ProductAdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ScannerProductEditorState())
    val state: StateFlow<ScannerProductEditorState> = _state.asStateFlow()

    private var initializedKey: String? = null
    private var nextRowId = 2L
    private var initialProduct: AdminProductResponse? = null

    fun initialize(productId: Long?, prefilledBarcode: String?) {
        val key = "${productId ?: "new"}:${prefilledBarcode.orEmpty()}"
        if (initializedKey == key) {
            return
        }
        initializedKey = key
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, validationError = null, savedProductId = null)
            try {
                val metadata = loadMetadata()
                val product = productId?.let { repository.getProduct(it) }
                _state.value = buildInitialState(metadata, product, prefilledBarcode)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load editor data")
                )
            }
        }
    }

    private suspend fun loadMetadata(): EditorMetadata = coroutineScope {
        val categories = async { repository.getCategories() }
        val units = async { repository.getUnits() }
        val unitGroups = async { repository.getUnitGroups() }
        val taxGroups = async { repository.getTaxGroups() }
        val containerTypes = async { repository.getContainerTypes() }

        EditorMetadata(
            categories = categories.await(),
            units = units.await(),
            unitGroups = unitGroups.await(),
            taxGroups = taxGroups.await(),
            containerTypes = containerTypes.await()
        )
    }

    private fun buildInitialState(
        metadata: EditorMetadata,
        product: AdminProductResponse?,
        prefilledBarcode: String?
    ): ScannerProductEditorState {
        if (product == null) {
            initialProduct = null
            return ScannerProductEditorState(
                isLoading = false,
                isEditMode = false,
                barcode = prefilledBarcode.orEmpty(),
                sku = prefilledBarcode.orEmpty(),
                categories = metadata.categories,
                units = metadata.units,
                unitGroups = metadata.unitGroups,
                taxGroups = metadata.taxGroups,
                containerTypes = metadata.containerTypes
            )
        }

        initialProduct = product

        val rows = product.unitQuantities.ifEmpty {
            listOf()
        }.map {
            ScannerUnitEditorState(
                rowId = nextRowId++,
                unitQuantityId = it.id,
                unitId = it.unitId,
                barcode = it.barcode.orEmpty(),
                salePrice = (it.salePriceEdit ?: it.salePrice ?: 0.0).toDisplayString(),
                wholesalePrice = (it.wholesalePriceEdit ?: it.wholesalePrice ?: 0.0).toDisplayString(),
                cogs = (it.cogs ?: 0.0).toDisplayString(),
                quantity = it.quantity.toDisplayString(),
                lowQuantity = (it.lowQuantity ?: 0.0).toDisplayString(),
                stockAlertEnabled = it.stockAlertEnabled,
                visible = it.visible,
                isManufactured = it.isManufactured,
                isRawMaterial = it.isRawMaterial,
                containerTypeId = it.containerLink?.containerTypeId
            )
        }.ifEmpty { listOf(ScannerUnitEditorState(rowId = 1L)) }

        return ScannerProductEditorState(
            isLoading = false,
            isEditMode = true,
            productId = product.id,
            name = product.name,
            barcode = product.barcode.orEmpty(),
            sku = product.sku.orEmpty(),
            status = product.status ?: "available",
            categoryId = product.categoryId,
            description = product.description.orEmpty(),
            barcodeType = product.barcodeType ?: "code128",
            stockManagement = product.stockManagement ?: "enabled",
            unitGroupId = product.unitGroup,
            taxGroupId = product.taxGroupId,
            taxType = product.taxType ?: "inclusive",
            accurateTracking = product.accurateTracking,
            autoCogs = product.autoCogs,
            onExpiration = product.onExpiration ?: "prevent_sales",
            isManufactured = product.isManufactured,
            isRawMaterial = product.isRawMaterial,
            unitRows = rows,
            categories = metadata.categories,
            units = metadata.units,
            unitGroups = metadata.unitGroups,
            taxGroups = metadata.taxGroups,
            containerTypes = metadata.containerTypes
        )
    }

    fun updateName(value: String) = updateState { copy(name = value, validationError = null) }
    fun updateBarcode(value: String) = updateState { copy(barcode = value, validationError = null) }
    fun updateSku(value: String) = updateState { copy(sku = value) }
    fun updateStatus(value: String) = updateState { copy(status = value) }
    fun updateCategory(value: Long?) = updateState { copy(categoryId = value) }
    fun updateDescription(value: String) = updateState { copy(description = value) }
    fun updateBarcodeType(value: String) = updateState { copy(barcodeType = value) }
    fun updateStockManagement(value: String) = updateState { copy(stockManagement = value) }
    fun updateUnitGroup(value: Long?) = updateState { copy(unitGroupId = value) }
    fun updateTaxGroup(value: Long?) = updateState { copy(taxGroupId = value) }
    fun updateTaxType(value: String) = updateState { copy(taxType = value) }
    fun updateAccurateTracking(value: Boolean) = updateState { copy(accurateTracking = value) }
    fun updateAutoCogs(value: Boolean) = updateState { copy(autoCogs = value) }
    fun updateOnExpiration(value: String) = updateState { copy(onExpiration = value) }
    fun updateManufactured(value: Boolean) = updateState { copy(isManufactured = value) }
    fun updateRawMaterial(value: Boolean) = updateState { copy(isRawMaterial = value) }
    fun clearSavedProduct() = updateState { copy(savedProductId = null) }
    fun clearError() = updateState { copy(error = null, validationError = null) }

    fun addUnitRow() {
        updateState {
            copy(unitRows = unitRows + ScannerUnitEditorState(rowId = nextRowId++))
        }
    }

    fun removeUnitRow(rowId: Long) {
        updateState {
            val updatedRows = unitRows.filterNot { it.rowId == rowId }.ifEmpty {
                listOf(ScannerUnitEditorState(rowId = nextRowId++))
            }
            copy(unitRows = updatedRows)
        }
    }

    fun updateUnitRow(rowId: Long, transform: ScannerUnitEditorState.() -> ScannerUnitEditorState) {
        updateState {
            copy(unitRows = unitRows.map { if (it.rowId == rowId) it.transform() else it })
        }
    }

    fun saveProduct() {
        saveProduct(_state.value.toDraft())
    }

    fun saveProduct(draft: ScannerProductEditorDraft) {
        val validationError = validate(draft)
        if (validationError != null) {
            _state.value = _state.value.copy(validationError = validationError)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, validationError = null, error = null)
            try {
                val savedProductId = handleSave(draft)
                _state.value = _state.value.copy(isSaving = false, savedProductId = savedProductId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.onlineOnlyMessage("Failed to save product")
                )
            }
        }
    }

    private suspend fun handleSave(draft: ScannerProductEditorDraft): Long? {
        if (!draft.isEditMode || draft.productId == null) {
            val request = draft.toPayload()
            val saved = repository.createProduct(request)
            initialProduct = saved
            return saved.id
        }

        val initial = initialProduct
        if (initial == null) {
            val request = draft.toPayload()
            val saved = repository.updateProduct(draft.productId, request.toUpdateRequest())
            initialProduct = saved
            return saved.id
        }

        if (requiresFullUpdate(draft, initial)) {
            val request = draft.toPayload()
            val saved = repository.updateProduct(draft.productId, request.toUpdateRequest())
            initialProduct = saved
            return saved.id
        }

        val updates = collectUnitUpdates(draft, initial)
        if (updates.isEmpty()) {
            return draft.productId
        }

        val updatedUnits = initial.unitQuantities.toMutableList()
        updates.forEach { (unitQuantityId, request) ->
            val response = repository.updateUnitQuantity(unitQuantityId, request)
            val index = updatedUnits.indexOfFirst { it.id == response.unitQuantity.id }
            if (index >= 0) {
                updatedUnits[index] = response.unitQuantity
            }
        }

        initialProduct = initial.copy(unitQuantities = updatedUnits)

        return draft.productId
    }

    private fun requiresFullUpdate(draft: ScannerProductEditorDraft, initial: AdminProductResponse): Boolean {
        if (draft.name != initial.name) return true
        if (draft.barcode != initial.barcode.orEmpty()) return true
        if (draft.sku != initial.sku.orEmpty()) return true
        if (draft.status != (initial.status ?: "")) return true
        if (draft.categoryId != initial.categoryId) return true
        if (draft.description != (initial.description ?: "")) return true
        if (draft.stockManagement != (initial.stockManagement ?: "")) return true
        if (draft.taxGroupId != initial.taxGroupId) return true
        if (draft.taxType != (initial.taxType ?: "")) return true
        if (draft.unitGroupId != initial.unitGroup) return true
        if (draft.accurateTracking != initial.accurateTracking) return true
        if (draft.autoCogs != initial.autoCogs) return true
        if (draft.onExpiration != (initial.onExpiration ?: "")) return true
        if (draft.isManufactured != initial.isManufactured) return true
        if (draft.isRawMaterial != initial.isRawMaterial) return true

        val initialUnits = initial.unitQuantities.associateBy { it.id }
        val currentUnitIds = draft.unitRows.mapNotNull { it.unitQuantityId }.toSet()

        if (draft.unitRows.any { it.unitQuantityId == null }) return true
        if (currentUnitIds.size != initialUnits.size) return true
        if (!currentUnitIds.containsAll(initialUnits.keys)) return true

        return draft.unitRows.any { row ->
            val initialUnit = initialUnits[row.unitQuantityId] ?: return@any true
            if (row.unitId != initialUnit.unitId) return@any true
            if (row.barcode != initialUnit.barcode.orEmpty()) return@any true
            val currentQuantity = row.quantity.toDoubleOrNull() ?: 0.0
            if (currentQuantity != initialUnit.quantity) return@any true
            false
        }
    }

    private fun collectUnitUpdates(
        draft: ScannerProductEditorDraft,
        initial: AdminProductResponse
    ): List<Pair<Long, com.nexopos.erp.core.network.UpdateUnitQuantityRequest>> {
        val initialUnits = initial.unitQuantities.associateBy { it.id }
        val updates = mutableListOf<Pair<Long, com.nexopos.erp.core.network.UpdateUnitQuantityRequest>>()

        draft.unitRows.forEach { row ->
            val unitId = row.unitQuantityId ?: return@forEach
            val initialUnit = initialUnits[unitId] ?: return@forEach

            val salePriceEdit = row.salePrice.toDoubleOrNull() ?: 0.0
            val wholesalePriceEdit = row.wholesalePrice.toDoubleOrNull() ?: 0.0
            val cogs = row.cogs.toDoubleOrNull() ?: 0.0
            val lowQuantity = row.lowQuantity.toDoubleOrNull() ?: 0.0

            val initialSalePrice = initialUnit.salePriceEdit ?: initialUnit.salePrice ?: 0.0
            val initialWholesalePrice = initialUnit.wholesalePriceEdit ?: initialUnit.wholesalePrice ?: 0.0
            val initialCogs = initialUnit.cogs ?: 0.0
            val initialLowQuantity = initialUnit.lowQuantity ?: 0.0
            val initialContainerTypeId = initialUnit.containerLink?.containerTypeId

            val containerTypeIdRequest = if (row.containerTypeId != initialContainerTypeId) {
                row.containerTypeId ?: 0L
            } else {
                null
            }

            val request = com.nexopos.erp.core.network.UpdateUnitQuantityRequest(
                salePriceEdit = if (salePriceEdit != initialSalePrice) salePriceEdit else null,
                wholesalePriceEdit = if (wholesalePriceEdit != initialWholesalePrice) wholesalePriceEdit else null,
                cogs = if (cogs != initialCogs) cogs else null,
                lowQuantity = if (lowQuantity != initialLowQuantity) lowQuantity else null,
                stockAlertEnabled = if (row.stockAlertEnabled != initialUnit.stockAlertEnabled) row.stockAlertEnabled else null,
                visible = if (row.visible != initialUnit.visible) row.visible else null,
                isManufactured = if (row.isManufactured != initialUnit.isManufactured) row.isManufactured else null,
                isRawMaterial = if (row.isRawMaterial != initialUnit.isRawMaterial) row.isRawMaterial else null,
                containerTypeId = containerTypeIdRequest
            )

            if (
                request.salePriceEdit != null ||
                request.wholesalePriceEdit != null ||
                request.cogs != null ||
                request.lowQuantity != null ||
                request.stockAlertEnabled != null ||
                request.visible != null ||
                request.isManufactured != null ||
                request.isRawMaterial != null ||
                request.containerTypeId != null
            ) {
                updates.add(unitId to request)
            }
        }

        return updates
    }

    private fun ScannerProductEditorState.toDraft(): ScannerProductEditorDraft {
        return ScannerProductEditorDraft(
            isEditMode = isEditMode,
            productId = productId,
            name = name,
            barcode = barcode,
            sku = sku,
            status = status,
            categoryId = categoryId,
            description = description,
            barcodeType = barcodeType,
            stockManagement = stockManagement,
            unitGroupId = unitGroupId,
            taxGroupId = taxGroupId,
            taxType = taxType,
            accurateTracking = accurateTracking,
            autoCogs = autoCogs,
            onExpiration = onExpiration,
            isManufactured = isManufactured,
            isRawMaterial = isRawMaterial,
            unitRows = unitRows.map { row ->
                ScannerUnitEditorDraft(
                    rowId = row.rowId,
                    unitQuantityId = row.unitQuantityId,
                    unitId = row.unitId,
                    barcode = row.barcode,
                    salePrice = row.salePrice,
                    wholesalePrice = row.wholesalePrice,
                    cogs = row.cogs,
                    quantity = row.quantity,
                    lowQuantity = row.lowQuantity,
                    stockAlertEnabled = row.stockAlertEnabled,
                    visible = row.visible,
                    isManufactured = row.isManufactured,
                    isRawMaterial = row.isRawMaterial,
                    containerTypeId = row.containerTypeId
                )
            }
        )
    }

    private fun validate(state: ScannerProductEditorState): String? {
        if (state.name.isBlank()) return "Product name is required"
        if (!state.isEditMode && state.barcode.isBlank()) return "Barcode is required"
        if (state.categoryId == null) return "Category is required"
        if (state.unitGroupId == null) return "Unit group is required"
        if (state.unitRows.isEmpty()) return "At least one selling unit is required"
        if (state.unitRows.any { it.unitId == null }) return "Each selling unit needs a unit"
        return null
    }

    private fun validate(draft: ScannerProductEditorDraft): String? {
        if (draft.name.isBlank()) return "Product name is required"
        if (!draft.isEditMode && draft.barcode.isBlank()) return "Barcode is required"
        if (draft.categoryId == null) return "Category is required"
        if (draft.unitGroupId == null) return "Unit group is required"
        if (draft.unitRows.isEmpty()) return "At least one selling unit is required"
        if (draft.unitRows.any { it.unitId == null }) return "Each selling unit needs a unit"
        return null
    }

    private fun updateState(transform: ScannerProductEditorState.() -> ScannerProductEditorState) {
        _state.value = _state.value.transform()
    }

    private fun ScannerProductEditorState.toPayload(): CreateProductRequest {
        val variation = ProductAdminVariationRequest(
            units = ProductAdminUnitsRequest(
                unitGroup = requireNotNull(unitGroupId),
                accurateTracking = accurateTracking,
                autoCogs = autoCogs,
                sellingGroup = unitRows.map { row ->
                    ProductAdminSellingGroupRequest(
                        unitId = requireNotNull(row.unitId),
                        barcode = row.barcode.takeIf { it.isNotBlank() },
                        salePrice = row.salePrice.toDoubleOrNull() ?: 0.0,
                        salePriceEdit = row.salePrice.toDoubleOrNull() ?: 0.0,
                        wholesalePrice = row.wholesalePrice.toDoubleOrNull() ?: 0.0,
                        wholesalePriceEdit = row.wholesalePrice.toDoubleOrNull() ?: 0.0,
                        cogs = row.cogs.toDoubleOrNull() ?: 0.0,
                        stockAlertEnabled = row.stockAlertEnabled,
                        lowQuantity = row.lowQuantity.toDoubleOrNull() ?: 0.0,
                        visible = row.visible,
                        quantity = row.quantity.toDoubleOrNull() ?: 0.0,
                        isManufactured = row.isManufactured,
                        isRawMaterial = row.isRawMaterial,
                        containerTypeId = row.containerTypeId
                    )
                }
            ),
            identification = ProductAdminIdentificationRequest(
                categoryId = categoryId,
                barcode = barcode.takeIf { it.isNotBlank() },
                sku = sku.takeIf { it.isNotBlank() },
                barcodeType = barcodeType,
                status = status,
                description = description,
                stockManagement = stockManagement,
                isManufactured = isManufactured,
                isRawMaterial = isRawMaterial
            ),
            expiry = ProductAdminExpiryRequest(
                onExpiration = onExpiration
            ),
            taxes = ProductAdminTaxesRequest(
                taxGroupId = taxGroupId,
                taxType = taxType
            )
        )

        return CreateProductRequest(
            name = name,
            barcode = barcode.takeIf { it.isNotBlank() },
            barcodeType = barcodeType,
            sku = sku.takeIf { it.isNotBlank() },
            status = status,
            categoryId = requireNotNull(categoryId),
            description = description,
            stockManagement = stockManagement,
            taxGroupId = taxGroupId,
            taxType = taxType,
            unitGroup = requireNotNull(unitGroupId),
            accurateTracking = accurateTracking,
            autoCogs = autoCogs,
            onExpiration = onExpiration,
            isManufactured = isManufactured,
            isRawMaterial = isRawMaterial,
            variations = listOf(variation)
        )
    }

    private fun ScannerProductEditorDraft.toPayload(): CreateProductRequest {
        val variation = ProductAdminVariationRequest(
            units = ProductAdminUnitsRequest(
                unitGroup = requireNotNull(unitGroupId),
                accurateTracking = accurateTracking,
                autoCogs = autoCogs,
                sellingGroup = unitRows.map { row ->
                    ProductAdminSellingGroupRequest(
                        unitId = requireNotNull(row.unitId),
                        barcode = row.barcode.takeIf { it.isNotBlank() },
                        salePrice = row.salePrice.toDoubleOrNull() ?: 0.0,
                        salePriceEdit = row.salePrice.toDoubleOrNull() ?: 0.0,
                        wholesalePrice = row.wholesalePrice.toDoubleOrNull() ?: 0.0,
                        wholesalePriceEdit = row.wholesalePrice.toDoubleOrNull() ?: 0.0,
                        cogs = row.cogs.toDoubleOrNull() ?: 0.0,
                        stockAlertEnabled = row.stockAlertEnabled,
                        lowQuantity = row.lowQuantity.toDoubleOrNull() ?: 0.0,
                        visible = row.visible,
                        quantity = row.quantity.toDoubleOrNull() ?: 0.0,
                        isManufactured = row.isManufactured,
                        isRawMaterial = row.isRawMaterial,
                        containerTypeId = row.containerTypeId
                    )
                }
            ),
            identification = ProductAdminIdentificationRequest(
                categoryId = categoryId,
                barcode = barcode.takeIf { it.isNotBlank() },
                sku = sku.takeIf { it.isNotBlank() },
                barcodeType = barcodeType,
                status = status,
                description = description,
                stockManagement = stockManagement,
                isManufactured = isManufactured,
                isRawMaterial = isRawMaterial
            ),
            expiry = ProductAdminExpiryRequest(
                onExpiration = onExpiration
            ),
            taxes = ProductAdminTaxesRequest(
                taxGroupId = taxGroupId,
                taxType = taxType
            )
        )

        return CreateProductRequest(
            name = name,
            barcode = barcode.takeIf { it.isNotBlank() },
            barcodeType = barcodeType,
            sku = sku.takeIf { it.isNotBlank() },
            status = status,
            categoryId = requireNotNull(categoryId),
            description = description,
            stockManagement = stockManagement,
            taxGroupId = taxGroupId,
            taxType = taxType,
            unitGroup = requireNotNull(unitGroupId),
            accurateTracking = accurateTracking,
            autoCogs = autoCogs,
            onExpiration = onExpiration,
            isManufactured = isManufactured,
            isRawMaterial = isRawMaterial,
            variations = listOf(variation)
        )
    }

    private fun CreateProductRequest.toUpdateRequest(): UpdateProductRequest {
        return UpdateProductRequest(
            name = name,
            barcode = barcode,
            barcodeType = barcodeType,
            sku = sku,
            status = status,
            categoryId = categoryId,
            description = description,
            stockManagement = stockManagement,
            taxGroupId = taxGroupId,
            taxType = taxType,
            unitGroup = unitGroup,
            accurateTracking = accurateTracking,
            autoCogs = autoCogs,
            onExpiration = onExpiration,
            isManufactured = isManufactured,
            isRawMaterial = isRawMaterial,
            variations = variations
        )
    }

    private fun Double.toDisplayString(): String {
        return if (this == 0.0) "" else toString()
    }

    private data class EditorMetadata(
        val categories: List<CategoryResponse>,
        val units: List<UnitResponse>,
        val unitGroups: List<UnitGroupResponse>,
        val taxGroups: List<TaxGroupResponse>,
        val containerTypes: List<ContainerType>
    )
}
