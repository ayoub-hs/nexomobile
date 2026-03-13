package com.nexopos.erp.feature.scanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.CategoryResponse
import com.nexopos.erp.core.network.ContainerType
import com.nexopos.erp.core.network.TaxGroupResponse
import com.nexopos.erp.core.network.UnitGroupResponse
import com.nexopos.erp.core.network.UnitResponse
import com.nexopos.erp.feature.scanner.vm.ScannerProductEditorDraft
import com.nexopos.erp.feature.scanner.vm.ScannerProductEditorViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerUnitEditorDraft
import com.nexopos.erp.feature.scanner.vm.ScannerUnitEditorState
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appSpacing
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun ScannerProductEditorScreen(
    productId: Long?,
    barcode: String?,
    viewModel: ScannerProductEditorViewModel,
    onSaved: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var controller by remember { mutableStateOf<ProductEditorController?>(null) }
    var controllerKey by remember { mutableStateOf<String?>(null) }
    var showFullFields by rememberSaveable(state.isEditMode, state.productId) {
        mutableStateOf(!state.isEditMode)
    }

    LaunchedEffect(productId, barcode) {
        viewModel.initialize(productId, barcode)
    }

    LaunchedEffect(state.savedProductId) {
        state.savedProductId?.let(onSaved)
    }

    LaunchedEffect(state.isLoading, state.productId, barcode) {
        if (!state.isLoading) {
            val key = "${state.productId ?: "new"}:${barcode.orEmpty()}"
            if (controllerKey != key) {
                controller = ProductEditorController(state)
                controllerKey = key
            }
        }
    }

    if (state.isLoading || controller == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val form = controller ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(MaterialTheme.appSpacing.m)
            ) {
                if (state.isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.scanner_mode_label),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.s))
                            Text(
                                text = if (showFullFields) {
                                    stringResource(R.string.scanner_mode_full)
                                } else {
                                    stringResource(R.string.scanner_mode_simple)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = showFullFields,
                            onCheckedChange = { showFullFields = it }
                        )
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AppTextField(form.name, { form.name = it }, label = stringResource(R.string.scanner_label_name), singleLine = true)
                SelectorField(
                    label = stringResource(R.string.scanner_label_stock_management),
                    selectedLabel = form.stockManagement,
                    options = listOf("enabled", "disabled"),
                    optionLabel = { it },
                    onSelected = { form.stockManagement = it }
                )
                if (showFullFields) {
                    AppTextField(form.barcode, { form.barcode = it }, label = stringResource(R.string.scanner_label_barcode), singleLine = true)
                    AppTextField(form.sku, { form.sku = it }, label = stringResource(R.string.scanner_label_sku), singleLine = true)
                    SelectorField(
                        label = stringResource(R.string.scanner_label_category_id),
                        selectedLabel = state.categories.firstOrNull { it.id == form.categoryId }?.name ?: "",
                        options = state.categories,
                        optionLabel = { it.name },
                        onSelected = { form.categoryId = it.id }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_barcode_type),
                        selectedLabel = form.barcodeType,
                        options = listOf("code128", "ean13", "ean8", "upca", "upce"),
                        optionLabel = { it },
                        onSelected = { form.barcodeType = it }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_status),
                        selectedLabel = form.status,
                        options = listOf("available", "unavailable"),
                        optionLabel = { it },
                        onSelected = { form.status = it }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_unit_group),
                        selectedLabel = state.unitGroups.firstOrNull { it.id == form.unitGroupId }?.name ?: "",
                        options = state.unitGroups,
                        optionLabel = { it.name },
                        onSelected = { form.unitGroupId = it.id }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_tax_group),
                        selectedLabel = state.taxGroups.firstOrNull { it.id == form.taxGroupId }?.name ?: "",
                        options = state.taxGroups,
                        optionLabel = { it.name },
                        onSelected = { form.taxGroupId = it.id }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_tax_type),
                        selectedLabel = form.taxType,
                        options = listOf("inclusive", "exclusive"),
                        optionLabel = { it },
                        onSelected = { form.taxType = it }
                    )
                    SelectorField(
                        label = stringResource(R.string.scanner_label_on_expiration),
                        selectedLabel = form.onExpiration,
                        options = listOf("prevent_sales", "allow_sales"),
                        optionLabel = { it },
                        onSelected = { form.onExpiration = it }
                    )
                    AppTextField(form.description, { form.description = it }, label = stringResource(R.string.scanner_label_description), minLines = 3)
                    CheckboxRow(stringResource(R.string.scanner_label_accurate_tracking), form.accurateTracking) { form.accurateTracking = it }
                    CheckboxRow(stringResource(R.string.scanner_label_auto_cogs), form.autoCogs) { form.autoCogs = it }
                    CheckboxRow(stringResource(R.string.scanner_label_manufactured), form.isManufactured) { form.isManufactured = it }
                    CheckboxRow(stringResource(R.string.scanner_label_raw_material), form.isRawMaterial) { form.isRawMaterial = it }
                    Text(
                        stringResource(R.string.scanner_truth_table_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(form.unitRows, key = { it.rowId }) { row ->
            UnitRowEditor(
                row = row,
                units = state.units,
                containerTypes = state.containerTypes,
                onRemove = { form.removeUnitRow(row.rowId) }
            )
        }

        item {
            AppButtonSecondary(
                onClick = form::addUnitRow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scanner_add_selling_unit))
            }
        }

        item {
            AppButtonPrimary(
                onClick = { viewModel.saveProduct(form.toDraft()) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) stringResource(R.string.scanner_saving_product) else stringResource(R.string.scanner_save_product))
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun UnitRowEditor(
    row: UnitRowController,
    units: List<UnitResponse>,
    containerTypes: List<ContainerType>,
    onRemove: () -> Unit
) {
    val noneLabel = stringResource(R.string.scanner_none)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scanner_selling_unit_title), style = MaterialTheme.typography.titleMedium)
        SelectorField(
            label = stringResource(R.string.scanner_label_unit),
            selectedLabel = units.firstOrNull { it.id == row.unitId }?.name ?: "",
            options = units,
            optionLabel = { it.name },
            onSelected = { unit -> row.unitId = unit.id }
        )
        AppTextField(row.barcode, { row.barcode = it }, label = stringResource(R.string.scanner_label_unit_barcode), singleLine = true)
        AppTextField(
            row.salePrice,
            { row.salePrice = it },
            label = stringResource(R.string.scanner_label_sale_price),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        AppTextField(
            row.wholesalePrice,
            { row.wholesalePrice = it },
            label = stringResource(R.string.scanner_label_wholesale_price),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        AppTextField(
            row.cogs,
            { row.cogs = it },
            label = stringResource(R.string.scanner_label_cogs),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        AppTextField(
            row.quantity,
            { row.quantity = it },
            label = stringResource(R.string.scanner_label_quantity),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        AppTextField(
            row.lowQuantity,
            { row.lowQuantity = it },
            label = stringResource(R.string.scanner_label_low_quantity_alert),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        SelectorField(
            label = stringResource(R.string.scanner_label_container_type),
            selectedLabel = containerTypes.firstOrNull { it.id == row.containerTypeId }?.name ?: "",
            options = listOf<ContainerType?>(null) + containerTypes,
            optionLabel = { it?.name ?: noneLabel },
            onSelected = { selected -> row.containerTypeId = selected?.id }
        )
        CheckboxRow(stringResource(R.string.scanner_label_stock_alert_enabled), row.stockAlertEnabled) { row.stockAlertEnabled = it }
        CheckboxRow(stringResource(R.string.scanner_label_visible), row.visible) { row.visible = it }
        CheckboxRow(stringResource(R.string.scanner_label_manufactured), row.isManufactured) { row.isManufactured = it }
        CheckboxRow(stringResource(R.string.scanner_label_raw_material), row.isRawMaterial) { row.isRawMaterial = it }
        AppButtonSecondary(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scanner_remove_unit))
        }
    }
}

@Stable
private class ProductEditorController(initial: com.nexopos.erp.feature.scanner.vm.ScannerProductEditorState) {
    var name by mutableStateOf(initial.name)
    var barcode by mutableStateOf(initial.barcode)
    var sku by mutableStateOf(initial.sku)
    var status by mutableStateOf(initial.status)
    var categoryId by mutableStateOf(initial.categoryId)
    var description by mutableStateOf(initial.description)
    var barcodeType by mutableStateOf(initial.barcodeType)
    var stockManagement by mutableStateOf(initial.stockManagement)
    var unitGroupId by mutableStateOf(initial.unitGroupId)
    var taxGroupId by mutableStateOf(initial.taxGroupId)
    var taxType by mutableStateOf(initial.taxType)
    var accurateTracking by mutableStateOf(initial.accurateTracking)
    var autoCogs by mutableStateOf(initial.autoCogs)
    var onExpiration by mutableStateOf(initial.onExpiration)
    var isManufactured by mutableStateOf(initial.isManufactured)
    var isRawMaterial by mutableStateOf(initial.isRawMaterial)

    private var nextRowId by mutableStateOf((initial.unitRows.maxOfOrNull { it.rowId } ?: 0L) + 1L)
    val unitRows = mutableStateListOf<UnitRowController>().apply {
        addAll(initial.unitRows.map(::UnitRowController))
    }

    val isEditMode = initial.isEditMode
    val productId = initial.productId

    fun addUnitRow() {
        unitRows.add(UnitRowController(ScannerUnitEditorState(rowId = nextRowId++)))
    }

    fun removeUnitRow(rowId: Long) {
        unitRows.removeAll { it.rowId == rowId }
        if (unitRows.isEmpty()) {
            addUnitRow()
        }
    }

    fun toDraft(): ScannerProductEditorDraft {
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
            unitRows = unitRows.map { it.toDraft() }
        )
    }
}

@Stable
private class UnitRowController(initial: ScannerUnitEditorState) {
    val rowId = initial.rowId
    val unitQuantityId = initial.unitQuantityId
    var unitId by mutableStateOf(initial.unitId)
    var barcode by mutableStateOf(initial.barcode)
    var salePrice by mutableStateOf(initial.salePrice)
    var wholesalePrice by mutableStateOf(initial.wholesalePrice)
    var cogs by mutableStateOf(initial.cogs)
    var quantity by mutableStateOf(initial.quantity)
    var lowQuantity by mutableStateOf(initial.lowQuantity)
    var stockAlertEnabled by mutableStateOf(initial.stockAlertEnabled)
    var visible by mutableStateOf(initial.visible)
    var isManufactured by mutableStateOf(initial.isManufactured)
    var isRawMaterial by mutableStateOf(initial.isRawMaterial)
    var containerTypeId by mutableStateOf(initial.containerTypeId)

    fun toDraft(): ScannerUnitEditorDraft {
        return ScannerUnitEditorDraft(
            rowId = rowId,
            unitQuantityId = unitQuantityId,
            unitId = unitId,
            barcode = barcode,
            salePrice = salePrice,
            wholesalePrice = wholesalePrice,
            cogs = cogs,
            quantity = quantity,
            lowQuantity = lowQuantity,
            stockAlertEnabled = stockAlertEnabled,
            visible = visible,
            isManufactured = isManufactured,
            isRawMaterial = isRawMaterial,
            containerTypeId = containerTypeId
        )
    }
}

@Composable
private fun <T> SelectorField(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        AppTextField(
            value = selectedLabel,
            onValueChange = {},
            label = label,
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                AppButtonSecondary(onClick = { expanded = true }) {
                    Text(stringResource(R.string.scanner_select))
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}
